package com.lims.catalog.controller;

import com.lims.catalog.dto.TestCatalogDto;
import com.lims.catalog.service.CatalogService;
import com.lims.common.enums.Gender;
import com.lims.common.security.Roles;
import com.lims.common.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Reference data. Responses carry {@code Cache-Control: private, no-cache} so browsers revalidate
 * with the ETag added by the shallow ETag filter and receive 304s when nothing changed.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/catalog")
@PreAuthorize(Roles.CAN_VIEW_CLINICAL)
@Tag(name = "Catalog Management", description = "Test directory, parameters and clinical reference interval APIs")
public class CatalogController {

    private static final CacheControl REVALIDATE = CacheControl.noCache().cachePrivate();

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/tests")
    @Operation(summary = "List all active laboratory tests", description = "Returns active catalog tests with pricing, container types and parameters")
    public ResponseEntity<List<TestCatalogDto>> getAllTests() {
        return ResponseEntity.ok().cacheControl(REVALIDATE).body(catalogService.getAllActiveTests());
    }

    @GetMapping("/tests/{id}")
    @Operation(summary = "Get test catalog details by ID")
    public ResponseEntity<TestCatalogDto> getTestById(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(REVALIDATE).body(catalogService.getTestById(id));
    }

    @GetMapping("/tests/code/{code}")
    @Operation(summary = "Get test catalog details by test code (e.g. CBC, LIPID)")
    public ResponseEntity<TestCatalogDto> getTestByCode(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_\\-]{1,50}$", message = "Invalid test code") String code) {
        return ResponseEntity.ok().cacheControl(REVALIDATE).body(catalogService.getTestByCode(code));
    }

    @GetMapping("/parameters/{parameterId}/reference-range")
    @Operation(summary = "Get matching normal and panic reference range for given age and gender")
    public ResponseEntity<TestCatalogDto.ReferenceRangeDto> getReferenceRange(
            @PathVariable UUID parameterId,
            @RequestParam @Min(value = 0, message = "Age must be 0 or more") @Max(value = 150, message = "Age must be 150 or less") int age,
            @RequestParam Gender gender) {
        return catalogService.getReferenceRangeForParameter(parameterId, age, gender)
                .map(range -> ResponseEntity.ok().cacheControl(REVALIDATE).body(range))
                .orElse(ResponseEntity.noContent().build());
    }
}
