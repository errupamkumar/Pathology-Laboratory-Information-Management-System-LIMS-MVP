package com.lims.accessioning.service;

import com.lims.accessioning.dto.AccessioningDtos;
import com.lims.accessioning.entity.SpecimenSample;
import com.lims.accessioning.repository.SpecimenSampleRepository;
import com.lims.common.enums.RejectionReason;
import com.lims.common.enums.SampleStatus;
import com.lims.common.event.OrderCreatedEvent;
import com.lims.common.event.SampleAccessionedEvent;
import com.lims.common.exception.InvalidOperationException;
import com.lims.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AccessioningService {

    private static final Logger log = LoggerFactory.getLogger(AccessioningService.class);

    private final SpecimenSampleRepository sampleRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AccessioningService(SpecimenSampleRepository sampleRepository,
                               ApplicationEventPublisher eventPublisher) {
        this.sampleRepository = sampleRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Runs asynchronously after the order transaction commits, in its own transaction. The event
     * is persisted in the publication registry, so a failure here is retried rather than lost;
     * the barcode check makes redelivery idempotent.
     */
    @ApplicationModuleListener
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Accessioning module received OrderCreatedEvent for {}", event.accessionNumber());
        for (OrderCreatedEvent.SampleBarcodeItemDto sampleDto : event.samples()) {
            if (sampleRepository.existsByBarcode(sampleDto.barcode())) {
                log.info("Specimen sample {} already initialised; skipping duplicate delivery", sampleDto.barcode());
                continue;
            }
            SpecimenSample sample = new SpecimenSample(
                    UUID.randomUUID(),
                    event.orderId(),
                    event.accessionNumber(),
                    sampleDto.barcode(),
                    sampleDto.containerType(),
                    sampleDto.specimenType()
            );
            sampleRepository.save(sample);
            log.info("Initialized specimen sample barcode: {} for tube type: {}", sample.getBarcode(), sample.getContainerType());
        }
    }

    public AccessioningDtos.SpecimenSampleDto receiveSample(String barcode) {
        SpecimenSample sample = findByBarcodeOrThrow(barcode);

        if (sample.getStatus() == SampleStatus.REJECTED) {
            throw new InvalidOperationException("Cannot receive sample " + barcode + " as it was previously REJECTED: " + sample.getRejectionReason());
        }
        if (sample.getStatus() == SampleStatus.RECEIVED) {
            // Barcode scanners frequently double-scan; keep the original receipt timestamp
            log.info("Specimen sample {} already received at {}; ignoring repeat scan", sample.getBarcode(), sample.getReceivedAt());
            return mapToDto(sample);
        }

        sample.setStatus(SampleStatus.RECEIVED);
        // DATETIME(6) precision, so the response matches what is persisted and later re-read
        sample.setReceivedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        SpecimenSample saved = sampleRepository.save(sample);

        log.info("Specimen sample {} received in laboratory.", saved.getBarcode());

        eventPublisher.publishEvent(new SampleAccessionedEvent(
                saved.getId(),
                saved.getOrderId(),
                saved.getAccessionNumber(),
                saved.getBarcode(),
                SampleStatus.RECEIVED,
                null,
                null,
                Instant.now()
        ));

        return mapToDto(saved);
    }

    public AccessioningDtos.SpecimenSampleDto rejectSample(String barcode, RejectionReason reason, String notes) {
        SpecimenSample sample = findByBarcodeOrThrow(barcode);

        if (sample.getStatus() == SampleStatus.REJECTED) {
            throw new InvalidOperationException("Sample " + barcode + " is already REJECTED (" + sample.getRejectionReason() + ")");
        }

        String cleanNotes = notes != null && !notes.isBlank() ? notes.trim() : null;
        sample.setStatus(SampleStatus.REJECTED);
        sample.setRejectionReason(reason);
        sample.setRejectionNotes(cleanNotes);
        SpecimenSample saved = sampleRepository.save(sample);

        log.warn("ALERT: Specimen sample {} REJECTED! Reason: {}", saved.getBarcode(), reason);

        eventPublisher.publishEvent(new SampleAccessionedEvent(
                saved.getId(),
                saved.getOrderId(),
                saved.getAccessionNumber(),
                saved.getBarcode(),
                SampleStatus.REJECTED,
                reason.name(),
                cleanNotes,
                Instant.now()
        ));

        return mapToDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<AccessioningDtos.SpecimenSampleDto> getSamples(SampleStatus status, Pageable pageable) {
        Page<SpecimenSample> page = (status != null)
                ? sampleRepository.findByStatus(status, pageable)
                : sampleRepository.findAll(pageable);
        return page.map(this::mapToDto);
    }

    @Transactional(readOnly = true)
    public AccessioningDtos.SpecimenSampleDto getSampleByBarcode(String barcode) {
        return mapToDto(findByBarcodeOrThrow(barcode));
    }

    @Transactional(readOnly = true)
    public List<AccessioningDtos.SpecimenSampleDto> getSamplesByOrderId(UUID orderId) {
        return sampleRepository.findByOrderId(orderId).stream().map(this::mapToDto).toList();
    }

    private SpecimenSample findByBarcodeOrThrow(String barcode) {
        return sampleRepository.findByBarcode(barcode.trim())
                .orElseThrow(() -> new ResourceNotFoundException("SpecimenSample", barcode));
    }

    private AccessioningDtos.SpecimenSampleDto mapToDto(SpecimenSample s) {
        return new AccessioningDtos.SpecimenSampleDto(
                s.getId(),
                s.getOrderId(),
                s.getAccessionNumber(),
                s.getBarcode(),
                s.getContainerType(),
                s.getSpecimenType(),
                s.getStatus(),
                s.getRejectionReason(),
                s.getRejectionNotes(),
                s.getCollectedAt(),
                s.getReceivedAt(),
                s.getCreatedAt()
        );
    }
}
