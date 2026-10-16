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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessioningServiceTest {

    @Mock SpecimenSampleRepository repository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks AccessioningService service;

    @Test
    void orderCreatedIsIdempotentOnRedelivery() {
        OrderCreatedEvent event = event("ACC-2026-5001-S01", "ACC-2026-5001-S02");
        when(repository.existsByBarcode("ACC-2026-5001-S01")).thenReturn(true);
        when(repository.existsByBarcode("ACC-2026-5001-S02")).thenReturn(false);

        service.onOrderCreated(event);

        verify(repository, times(1)).save(argThat(s -> s.getBarcode().equals("ACC-2026-5001-S02")));
    }

    @Test
    void receiveMarksSampleReceivedAndPublishesEvent() {
        SpecimenSample sample = sample(SampleStatus.COLLECTED);
        when(repository.findByBarcode(sample.getBarcode())).thenReturn(Optional.of(sample));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccessioningDtos.SpecimenSampleDto dto = service.receiveSample(sample.getBarcode());

        assertThat(dto.status()).isEqualTo(SampleStatus.RECEIVED);
        assertThat(dto.receivedAt()).isNotNull();
        verify(eventPublisher).publishEvent(any(SampleAccessionedEvent.class));
    }

    @Test
    void repeatScanKeepsOriginalReceiptTime() {
        SpecimenSample sample = sample(SampleStatus.RECEIVED);
        Instant original = Instant.parse("2026-01-01T10:00:00Z");
        sample.setReceivedAt(original);
        when(repository.findByBarcode(sample.getBarcode())).thenReturn(Optional.of(sample));

        assertThat(service.receiveSample(sample.getBarcode()).receivedAt()).isEqualTo(original);
        verify(repository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void rejectedSampleCannotBeReceived() {
        SpecimenSample sample = sample(SampleStatus.REJECTED);
        when(repository.findByBarcode(sample.getBarcode())).thenReturn(Optional.of(sample));

        assertThatThrownBy(() -> service.receiveSample(sample.getBarcode())).isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void rejectionIsNotOverwritten() {
        SpecimenSample sample = sample(SampleStatus.REJECTED);
        when(repository.findByBarcode(sample.getBarcode())).thenReturn(Optional.of(sample));

        assertThatThrownBy(() -> service.rejectSample(sample.getBarcode(), RejectionReason.CLOTTED, null))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void rejectRecordsReasonAndTrimsNotes() {
        SpecimenSample sample = sample(SampleStatus.COLLECTED);
        when(repository.findByBarcode(sample.getBarcode())).thenReturn(Optional.of(sample));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccessioningDtos.SpecimenSampleDto dto = service.rejectSample(sample.getBarcode(), RejectionReason.HEMOLYZED, "  gross hemolysis ");

        assertThat(dto.status()).isEqualTo(SampleStatus.REJECTED);
        assertThat(dto.rejectionNotes()).isEqualTo("gross hemolysis");
    }

    @Test
    void unknownBarcodeIsNotFound() {
        when(repository.findByBarcode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSampleByBarcode("NOPE")).isInstanceOf(ResourceNotFoundException.class);
    }

    private static SpecimenSample sample(SampleStatus status) {
        SpecimenSample sample = new SpecimenSample(UUID.randomUUID(), UUID.randomUUID(), "ACC-2026-5001",
                "ACC-2026-5001-S01", "EDTA (Lavender)", "Whole Blood");
        sample.setStatus(status);
        return sample;
    }

    private static OrderCreatedEvent event(String... barcodes) {
        List<OrderCreatedEvent.SampleBarcodeItemDto> samples = java.util.Arrays.stream(barcodes)
                .map(b -> new OrderCreatedEvent.SampleBarcodeItemDto(b, "EDTA", "Whole Blood"))
                .toList();
        return new OrderCreatedEvent(UUID.randomUUID(), "ACC-2026-5001", UUID.randomUUID(), "UHID-2026-1001",
                "Sarah Jenkins", "+91 98112 34567", "FEMALE", 44, BigDecimal.TEN, BigDecimal.TEN, "Self",
                List.of(), samples, null);
    }
}
