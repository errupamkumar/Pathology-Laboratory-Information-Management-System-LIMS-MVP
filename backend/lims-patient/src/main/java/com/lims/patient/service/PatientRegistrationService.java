package com.lims.patient.service;

import com.lims.catalog.dto.TestCatalogDto;
import com.lims.catalog.service.CatalogService;
import com.lims.common.event.OrderCreatedEvent;
import com.lims.common.exception.DuplicateResourceException;
import com.lims.common.exception.InvalidOperationException;
import com.lims.common.exception.ResourceNotFoundException;
import com.lims.common.sequence.SequenceGeneratorService;
import com.lims.patient.dto.*;
import com.lims.patient.entity.DiagnosticOrder;
import com.lims.patient.entity.OrderItem;
import com.lims.patient.entity.Patient;
import com.lims.patient.repository.DiagnosticOrderRepository;
import com.lims.patient.repository.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Service
@Transactional
public class PatientRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(PatientRegistrationService.class);

    /**
     * Bounded retries when a freshly allocated identifier already exists (e.g. rows created by
     * the legacy in-memory counter before sequences were persisted).
     */
    static final int MAX_ID_ALLOCATION_ATTEMPTS = 25;

    private final PatientRepository patientRepository;
    private final DiagnosticOrderRepository diagnosticOrderRepository;
    private final CatalogService catalogService;
    private final SequenceGeneratorService sequenceGenerator;
    private final ApplicationEventPublisher eventPublisher;

    public PatientRegistrationService(PatientRepository patientRepository,
                                      DiagnosticOrderRepository diagnosticOrderRepository,
                                      CatalogService catalogService,
                                      SequenceGeneratorService sequenceGenerator,
                                      ApplicationEventPublisher eventPublisher) {
        this.patientRepository = patientRepository;
        this.diagnosticOrderRepository = diagnosticOrderRepository;
        this.catalogService = catalogService;
        this.sequenceGenerator = sequenceGenerator;
        this.eventPublisher = eventPublisher;
    }

    public PatientResponseDto registerPatient(CreatePatientRequest request) {
        if (patientRepository.existsByPhone(request.phone().trim())) {
            throw new DuplicateResourceException("A patient is already registered with phone number: " + request.phone());
        }

        Patient patient = new Patient(
                UUID.randomUUID(),
                allocateUhid(),
                request.firstName().trim(),
                request.lastName().trim(),
                request.dob(),
                request.gender(),
                request.phone().trim(),
                trimToNull(request.email())
        );

        Patient saved = patientRepository.save(patient);
        log.info("Registered new patient with UHID: {}", saved.getUhid());
        return mapToPatientDto(saved);
    }

    @Transactional(readOnly = true)
    public Optional<PatientResponseDto> findPatientByPhone(String phone) {
        return patientRepository.findByPhone(phone.trim()).map(this::mapToPatientDto);
    }

    @Transactional(readOnly = true)
    public PatientResponseDto getPatientById(UUID id) {
        return patientRepository.findById(id)
                .map(this::mapToPatientDto)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }

    @Transactional(readOnly = true)
    public Page<PatientResponseDto> searchPatients(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return patientRepository.findAll(pageable).map(this::mapToPatientDto);
        }
        String q = query.trim();
        return patientRepository
                .findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrPhoneContaining(q, q, q, pageable)
                .map(this::mapToPatientDto);
    }

    @Transactional(readOnly = true)
    public boolean hasAnyPatients() {
        return patientRepository.count() > 0;
    }

    public OrderResponseDto registerAndBookOrder(RegisterPatientAndBookOrderRequest request) {
        Patient patient = resolveOrRegisterPatient(request);

        // De-duplicate so the same test cannot be billed twice on one order
        Set<UUID> testIds = new LinkedHashSet<>(request.testCatalogIds());
        if (testIds.isEmpty()) {
            throw new InvalidOperationException("At least one test must be selected to create an order");
        }

        BigDecimal grossTotal = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();
        List<OrderCreatedEvent.OrderTestItemDto> eventItems = new ArrayList<>();
        Map<String, String> requiredContainers = new LinkedHashMap<>(); // containerType -> specimenType

        for (UUID testId : testIds) {
            TestCatalogDto catalog = catalogService.getTestById(testId);
            if (!Boolean.TRUE.equals(catalog.isActive())) {
                throw new InvalidOperationException("Test " + catalog.testCode() + " is inactive and cannot be booked");
            }
            grossTotal = grossTotal.add(catalog.price());

            items.add(new OrderItem(UUID.randomUUID(), catalog.id(), catalog.testCode(), catalog.testName(), catalog.price()));
            eventItems.add(new OrderCreatedEvent.OrderTestItemDto(
                    catalog.id(), catalog.testCode(), catalog.testName(), catalog.price(), catalog.containerType()));
            requiredContainers.putIfAbsent(catalog.containerType(), catalog.specimenType());
        }

        BigDecimal discount = request.discountAmount() != null ? request.discountAmount() : BigDecimal.ZERO;
        if (discount.compareTo(grossTotal) > 0) {
            throw new InvalidOperationException("Discount (" + discount + ") cannot exceed the gross bill (" + grossTotal + ")");
        }
        BigDecimal netTotal = grossTotal.subtract(discount);
        BigDecimal paid = request.paidAmount();
        if (paid.compareTo(netTotal) > 0) {
            throw new InvalidOperationException("Paid amount (" + paid + ") cannot exceed the net payable (" + netTotal + ")");
        }

        String accessionNumber = allocateAccessionNumber();
        String referringDoctor = trimToNull(request.referringDoctor());
        DiagnosticOrder order = new DiagnosticOrder(
                UUID.randomUUID(),
                accessionNumber,
                patient,
                netTotal,
                paid,
                referringDoctor != null ? referringDoctor : "Self / Walk-in"
        );
        items.forEach(order::addItem);

        DiagnosticOrder savedOrder = diagnosticOrderRepository.save(order);
        log.info("Created DiagnosticOrder {} with {} items for patient {}",
                savedOrder.getAccessionNumber(), items.size(), patient.getUhid());

        List<OrderCreatedEvent.SampleBarcodeItemDto> sampleDtos = new ArrayList<>();
        int sampleIdx = 1;
        for (Map.Entry<String, String> entry : requiredContainers.entrySet()) {
            String sampleBarcode = String.format("%s-S%02d", accessionNumber, sampleIdx++);
            sampleDtos.add(new OrderCreatedEvent.SampleBarcodeItemDto(sampleBarcode, entry.getKey(), entry.getValue()));
        }

        // Persisted to the event publication registry in this transaction; listeners run after commit
        eventPublisher.publishEvent(new OrderCreatedEvent(
                savedOrder.getId(),
                savedOrder.getAccessionNumber(),
                patient.getId(),
                patient.getUhid(),
                patient.getFullName(),
                patient.getPhone(),
                patient.getGender().name(),
                patient.getAge(),
                savedOrder.getTotalAmount(),
                savedOrder.getPaidAmount(),
                savedOrder.getReferringDoctor(),
                eventItems,
                sampleDtos,
                null
        ));
        log.info("Published OrderCreatedEvent for {}", savedOrder.getAccessionNumber());

        return mapToOrderDto(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponseDto getOrderByAccession(String accessionNumber) {
        return diagnosticOrderRepository.findByAccessionNumberWithDetails(accessionNumber)
                .map(this::mapToOrderDto)
                .orElseThrow(() -> new ResourceNotFoundException("DiagnosticOrder", accessionNumber));
    }

    @Transactional(readOnly = true)
    public OrderResponseDto getOrderById(UUID id) {
        return diagnosticOrderRepository.findByIdWithDetails(id)
                .map(this::mapToOrderDto)
                .orElseThrow(() -> new ResourceNotFoundException("DiagnosticOrder", id));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponseDto> getOrders(Pageable pageable) {
        return diagnosticOrderRepository.findAllWithPatient(pageable).map(this::mapToOrderDto);
    }

    private Patient resolveOrRegisterPatient(RegisterPatientAndBookOrderRequest request) {
        if (request.patientId() != null) {
            return patientRepository.findById(request.patientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Patient", request.patientId()));
        }
        if (request.uhid() != null && !request.uhid().isBlank()) {
            return patientRepository.findByUhid(request.uhid().trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Patient UHID", request.uhid()));
        }
        if (!request.isPatientIdentified()) {
            throw new InvalidOperationException("Patient demographics (firstName, lastName, dob, gender, phone) are required for new registration");
        }

        Optional<Patient> existingByPhone = patientRepository.findByPhone(request.phone().trim());
        if (existingByPhone.isPresent()) {
            log.info("Patient with matching phone already exists. Reusing UHID {}", existingByPhone.get().getUhid());
            return existingByPhone.get();
        }

        Patient patient = new Patient(
                UUID.randomUUID(),
                allocateUhid(),
                request.firstName().trim(),
                request.lastName().trim(),
                request.dob(),
                request.gender(),
                request.phone().trim(),
                trimToNull(request.email())
        );
        return patientRepository.save(patient);
    }

    private String allocateUhid() {
        return allocateUnique(sequenceGenerator::nextUhid, patientRepository::existsByUhid, "UHID");
    }

    private String allocateAccessionNumber() {
        return allocateUnique(sequenceGenerator::nextAccessionNumber, diagnosticOrderRepository::existsByAccessionNumber, "accession number");
    }

    static String allocateUnique(Supplier<String> generator, Predicate<String> exists, String label) {
        for (int attempt = 1; attempt <= MAX_ID_ALLOCATION_ATTEMPTS; attempt++) {
            String candidate = generator.get();
            if (!exists.test(candidate)) {
                return candidate;
            }
            log.warn("Allocated {} {} already exists (attempt {}); advancing sequence", label, candidate, attempt);
        }
        throw new IllegalStateException("Unable to allocate a unique " + label + " after "
                + MAX_ID_ALLOCATION_ATTEMPTS + " attempts; db_sequences may need reseeding");
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public PatientResponseDto mapToPatientDto(Patient p) {
        return new PatientResponseDto(
                p.getId(),
                p.getUhid(),
                p.getFirstName(),
                p.getLastName(),
                p.getFullName(),
                p.getDob(),
                p.getAge(),
                p.getGender(),
                p.getPhone(),
                p.getEmail(),
                p.getCreatedAt()
        );
    }

    public OrderResponseDto mapToOrderDto(DiagnosticOrder o) {
        BigDecimal balance = o.getTotalAmount().subtract(o.getPaidAmount()).max(BigDecimal.ZERO);
        List<OrderResponseDto.OrderItemDto> items = o.getItems().stream()
                .map(i -> new OrderResponseDto.OrderItemDto(
                        i.getId(),
                        i.getTestCatalogId(),
                        i.getTestCode(),
                        i.getTestName(),
                        i.getPrice()
                )).toList();

        return new OrderResponseDto(
                o.getId(),
                o.getAccessionNumber(),
                mapToPatientDto(o.getPatient()),
                o.getStatus(),
                o.getTotalAmount(),
                o.getPaidAmount(),
                balance,
                o.getReferringDoctor(),
                o.getCreatedAt(),
                items
        );
    }
}
