package com.lims.server.config;

import com.lims.catalog.dto.TestCatalogDto;
import com.lims.catalog.service.CatalogService;
import com.lims.common.enums.Gender;
import com.lims.common.security.AppUser;
import com.lims.common.security.AppUserRepository;
import com.lims.common.security.Roles;
import com.lims.patient.dto.OrderResponseDto;
import com.lims.patient.dto.RegisterPatientAndBookOrderRequest;
import com.lims.patient.service.PatientRegistrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Demo/training data: one staff account per role and a sample order. Controlled by
 * {@code lims.seed.demo-data} (env {@code LIMS_SEED_DEMO_DATA}); disabled in the prod profile.
 */
@Component
@Order(2)
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    record DemoUser(String username, String fullName, String password, String role) {}

    static final List<DemoUser> DEMO_USERS = List.of(
            new DemoUser("pathologist", "Dr. Rajesh Sharma, MD", "Pathologist@123", Roles.PATHOLOGIST),
            new DemoUser("technician", "Priya Nair", "Technician@123", Roles.TECHNICIAN),
            new DemoUser("reception", "Anita Desai", "Reception@123", Roles.RECEPTIONIST)
    );

    private final PatientRegistrationService patientRegistrationService;
    private final CatalogService catalogService;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean seedDemoData;

    public DataInitializer(PatientRegistrationService patientRegistrationService,
                           CatalogService catalogService,
                           AppUserRepository appUserRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${lims.seed.demo-data:true}") boolean seedDemoData) {
        this.patientRegistrationService = patientRegistrationService;
        this.catalogService = catalogService;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedDemoData = seedDemoData;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedDemoData) {
            return;
        }
        seedDemoStaff();
        seedDemoOrder();
    }

    private void seedDemoStaff() {
        for (DemoUser demo : DEMO_USERS) {
            if (appUserRepository.existsByUsername(demo.username())) {
                continue;
            }
            appUserRepository.save(new AppUser(UUID.randomUUID(), demo.username(), demo.username() + "@apexpathlab.com",
                    passwordEncoder.encode(demo.password()), demo.fullName(), Set.of(Roles.authority(demo.role()))));
            log.info("Seeded demo {} account '{}'", demo.role(), demo.username());
        }
    }

    private void seedDemoOrder() {
        if (patientRegistrationService.hasAnyPatients()) {
            return;
        }
        log.info("Database is fresh. Seeding demo patient and diagnostic order...");

        List<TestCatalogDto> tests = catalogService.getAllActiveTests();
        if (tests.isEmpty()) {
            log.warn("No catalog tests found. Flyway scripts might be running or catalog is empty.");
            return;
        }

        List<TestCatalogDto> selected = tests.stream()
                .filter(t -> "CBC".equalsIgnoreCase(t.testCode()) || "GLU_F".equalsIgnoreCase(t.testCode()))
                .toList();
        if (selected.isEmpty()) {
            selected = List.of(tests.getFirst());
        }
        BigDecimal total = selected.stream().map(TestCatalogDto::price).reduce(BigDecimal.ZERO, BigDecimal::add);

        RegisterPatientAndBookOrderRequest request = new RegisterPatientAndBookOrderRequest(
                null,
                null,
                "Sarah",
                "Jenkins",
                LocalDate.of(1982, 5, 14),
                Gender.FEMALE,
                "+91 98112 34567",
                "sarah.jenkins@example.com",
                selected.stream().map(TestCatalogDto::id).toList(),
                BigDecimal.ZERO,
                total,
                "Dr. Robert Sterling, MD (Cardiology)"
        );

        OrderResponseDto order = patientRegistrationService.registerAndBookOrder(request);
        log.info("Initialized demo order {} for patient UHID {}", order.accessionNumber(), order.patient().uhid());
    }
}
