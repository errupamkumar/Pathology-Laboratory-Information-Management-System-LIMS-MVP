package com.lims.server;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end workflow against a real MySQL 8 (Flyway V1-V4, outbox, audit trail, RBAC).
 * Runs in the Maven integration-test phase; skipped automatically when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=mysql",
        "lims.rate-limit.login-requests-per-minute=1000",
        "lims.seed.demo-data=true"
})
class LimsWorkflowIT {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36");

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;

    static String admin, reception, technician, pathologist;

    @BeforeAll
    static void noop() {
        // tokens are obtained lazily in tokens() because @Autowired fields are not available in static context
    }

    @Test
    void securedEndpointsRequireAuthenticationAndRole() {
        tokens();
        assertThat(rest.getForEntity("/api/v1/orders", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<JsonNode> denied = exchange(HttpMethod.POST, "/api/v1/orders/book", technician, Map.of());
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(denied.getBody().get("correlationId")).isNotNull();
    }

    @Test
    void fullClinicalWorkflowOnMySql() {
        tokens();
        JsonNode catalog = exchange(HttpMethod.GET, "/api/v1/catalog/tests", reception, null).getBody();
        JsonNode cbc = null;
        for (JsonNode test : catalog) {
            if ("CBC".equals(test.get("testCode").asText())) cbc = test;
        }
        assertThat(cbc).isNotNull();
        JsonNode hb = cbc.get("parameters").get(0);

        // 1. Reception books an order
        ResponseEntity<JsonNode> booked = exchange(HttpMethod.POST, "/api/v1/orders/book", reception, Map.of(
                "firstName", "Ravi", "lastName", "Kumar", "dob", "1985-03-02", "gender", "MALE",
                "phone", "+91 90000 11111", "testCatalogIds", List.of(cbc.get("id").asText()),
                "discountAmount", 0, "paidAmount", 450));
        assertThat(booked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String orderId = booked.getBody().get("id").asText();

        // 2. Outbox delivers OrderCreatedEvent after commit -> samples appear
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(exchange(HttpMethod.GET, "/api/v1/accessioning/samples/order/" + orderId, technician, null).getBody())
                        .hasSize(1));
        String barcode = exchange(HttpMethod.GET, "/api/v1/accessioning/samples/order/" + orderId, technician, null)
                .getBody().get(0).get("barcode").asText();

        // 3. Technician receives the sample and enters a panic value
        assertThat(exchange(HttpMethod.POST, "/api/v1/accessioning/scan-receive", technician, Map.of("barcode", barcode))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> results = exchange(HttpMethod.POST, "/api/v1/diagnostics/results/batch", technician, Map.of(
                "orderId", orderId,
                "results", List.of(Map.of("parameterId", hb.get("id").asText(), "parameterName", hb.get("parameterName").asText(),
                        "unit", hb.get("unit").asText(), "observedValue", "5.1"))));
        assertThat(results.getBody().get(0).get("isCritical").asBoolean()).isTrue();

        // 4. Pathologist signs off; results are then locked
        ResponseEntity<JsonNode> signed = exchange(HttpMethod.POST, "/api/v1/diagnostics/authorize", pathologist, Map.of(
                "orderId", orderId, "pathologistName", "Dr. Rajesh Sharma, MD", "licenseNumber", "MCI-64921-PATH"));
        assertThat(signed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(signed.getBody().get("authorizedBy").asText()).isEqualTo("pathologist");
        assertThat(exchange(HttpMethod.POST, "/api/v1/diagnostics/results/batch", technician, Map.of(
                "orderId", orderId,
                "results", List.of(Map.of("parameterId", hb.get("id").asText(), "parameterName", "Hb", "observedValue", "13"))))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 5. Public verification works without a token
        String token = signed.getBody().get("verificationToken").asText();
        assertThat(rest.getForEntity("/api/v1/diagnostics/verify/" + token, JsonNode.class).getBody().get("valid").asBoolean()).isTrue();

        // 6. Audit trail captured the booking with the real user, in MySQL's JSON column
        Integer audited = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_name = 'DiagnosticOrder' AND entity_id = ? AND changed_by = 'reception'",
                Integer.class, orderId);
        assertThat(audited).isPositive();

        // 7. Every event publication eventually completes
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_publication WHERE completion_date IS NULL", Integer.class))
                        .isZero());
    }

    private void tokens() {
        if (admin != null) return;
        admin = login("admin", "admin123");
        reception = login("reception", "Reception@123");
        technician = login("technician", "Technician@123");
        pathologist = login("pathologist", "Pathologist@123");
    }

    private String login(String username, String password) {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/v1/auth/login",
                Map.of("username", username, "password", password), JsonNode.class);
        assertThat(response.getStatusCode()).as("login %s", username).isEqualTo(HttpStatus.OK);
        return response.getBody().get("accessToken").asText();
    }

    private ResponseEntity<JsonNode> exchange(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }
}
