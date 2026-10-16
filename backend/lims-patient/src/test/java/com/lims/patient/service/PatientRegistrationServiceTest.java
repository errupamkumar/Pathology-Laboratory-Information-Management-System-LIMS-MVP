package com.lims.patient.service;

import com.lims.catalog.dto.TestCatalogDto;
import com.lims.catalog.service.CatalogService;
import com.lims.common.enums.Gender;
import com.lims.common.event.OrderCreatedEvent;
import com.lims.common.exception.DuplicateResourceException;
import com.lims.common.exception.InvalidOperationException;
import com.lims.common.sequence.SequenceGeneratorService;
import com.lims.patient.dto.CreatePatientRequest;
import com.lims.patient.dto.OrderResponseDto;
import com.lims.patient.dto.RegisterPatientAndBookOrderRequest;
import com.lims.patient.entity.DiagnosticOrder;
import com.lims.patient.entity.Patient;
import com.lims.patient.repository.DiagnosticOrderRepository;
import com.lims.patient.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientRegistrationServiceTest {

    @Mock PatientRepository patientRepository;
    @Mock DiagnosticOrderRepository orderRepository;
    @Mock CatalogService catalogService;
    @Mock SequenceGeneratorService sequenceGenerator;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks PatientRegistrationService service;

    final TestCatalogDto cbc = test("CBC", "450.00", "EDTA (Lavender)", true);
    final TestCatalogDto lipid = test("LIPID", "850.00", "Plain (Red/Gold SST)", true);

    @BeforeEach
    void stubPersistence() {
        lenient().when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(orderRepository.save(any(DiagnosticOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(sequenceGenerator.nextUhid()).thenReturn("UHID-2026-1001");
        lenient().when(sequenceGenerator.nextAccessionNumber()).thenReturn("ACC-2026-5001");
        lenient().when(catalogService.getTestById(cbc.id())).thenReturn(cbc);
        lenient().when(catalogService.getTestById(lipid.id())).thenReturn(lipid);
    }

    @Test
    void booksNewPatientDeduplicatesTestsAndPublishesEventPerTube() {
        when(patientRepository.findByPhone("+91 98765 43210")).thenReturn(Optional.empty());

        OrderResponseDto order = service.registerAndBookOrder(request(List.of(cbc.id(), cbc.id(), lipid.id()), "0", "1300"));

        assertThat(order.accessionNumber()).isEqualTo("ACC-2026-5001");
        assertThat(order.patient().uhid()).isEqualTo("UHID-2026-1001");
        assertThat(order.items()).hasSize(2);
        assertThat(order.totalAmount()).isEqualByComparingTo("1300");
        assertThat(order.balanceAmount()).isEqualByComparingTo("0");

        ArgumentCaptor<OrderCreatedEvent> event = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().samples()).extracting(OrderCreatedEvent.SampleBarcodeItemDto::barcode)
                .containsExactly("ACC-2026-5001-S01", "ACC-2026-5001-S02");
    }

    @Test
    void reusesExistingPatientMatchedByPhone() {
        Patient existing = patient("UHID-2026-0042");
        when(patientRepository.findByPhone("+91 98765 43210")).thenReturn(Optional.of(existing));

        OrderResponseDto order = service.registerAndBookOrder(request(List.of(cbc.id()), "0", "450"));

        assertThat(order.patient().uhid()).isEqualTo("UHID-2026-0042");
        verify(sequenceGenerator, never()).nextUhid();
    }

    @Test
    void rejectsDiscountGreaterThanGrossBill() {
        when(patientRepository.findByPhone(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerAndBookOrder(request(List.of(cbc.id()), "500", "0")))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Discount");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void rejectsOverpayment() {
        when(patientRepository.findByPhone(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerAndBookOrder(request(List.of(cbc.id()), "50", "450")))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Paid amount");
    }

    @Test
    void rejectsInactiveTests() {
        TestCatalogDto retired = test("OLD", "100.00", "Plain", false);
        when(catalogService.getTestById(retired.id())).thenReturn(retired);
        when(patientRepository.findByPhone(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerAndBookOrder(request(List.of(retired.id()), "0", "0")))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void registerPatientRejectsDuplicatePhone() {
        when(patientRepository.existsByPhone("+91 98765 43210")).thenReturn(true);

        assertThatThrownBy(() -> service.registerPatient(new CreatePatientRequest(
                "Rahul", "Verma", LocalDate.of(1990, 1, 1), Gender.MALE, "+91 98765 43210", null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void allocationSkipsIdentifiersThatAlreadyExist() {
        Iterator<String> ids = List.of("UHID-2026-1001", "UHID-2026-1002", "UHID-2026-1003").iterator();
        Set<String> taken = Set.of("UHID-2026-1001", "UHID-2026-1002");

        assertThat(PatientRegistrationService.allocateUnique(ids::next, taken::contains, "UHID")).isEqualTo("UHID-2026-1003");
    }

    @Test
    void allocationGivesUpAfterBoundedAttempts() {
        assertThatThrownBy(() -> PatientRegistrationService.allocateUnique(() -> "DUP", id -> true, "UHID"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static RegisterPatientAndBookOrderRequest request(List<UUID> tests, String discount, String paid) {
        return new RegisterPatientAndBookOrderRequest(null, null, "Rahul", "Verma", LocalDate.of(1988, 4, 12),
                Gender.MALE, "+91 98765 43210", null, tests, new BigDecimal(discount), new BigDecimal(paid), "Dr. A. Sharma");
    }

    private static Patient patient(String uhid) {
        return new Patient(UUID.randomUUID(), uhid, "Rahul", "Verma", LocalDate.of(1988, 4, 12),
                Gender.MALE, "+91 98765 43210", null);
    }

    private static TestCatalogDto test(String code, String price, String container, boolean active) {
        return new TestCatalogDto(UUID.randomUUID(), code, code + " test", "DEPT", "Serum", container,
                new BigDecimal(price), 60, active, List.of());
    }
}
