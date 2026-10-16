package com.lims.common.sequence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Year;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SequenceGeneratorServiceTest {

    @Mock
    DbSequenceRepository repository;

    @InjectMocks
    SequenceGeneratorService service;

    @Test
    void nextUhidUsesPersistedCounterValue() {
        when(repository.incrementSequence(SequenceGeneratorService.UHID_SEQUENCE)).thenReturn(1);
        when(repository.findById(SequenceGeneratorService.UHID_SEQUENCE)).thenReturn(Optional.of(sequence(1001L)));

        assertThat(service.nextUhid()).isEqualTo("UHID-" + Year.now().getValue() + "-1001");
    }

    @Test
    void valuesBeyond9999DoNotWrapAround() {
        // The previous implementation used value % 10000, which re-issued UHID-xxxx-0001 after 10,000 patients
        assertThat(SequenceGeneratorService.format("ACC", 10_001L)).endsWith("-10001");
        assertThat(SequenceGeneratorService.format("ACC", 1L)).endsWith("-0001");
    }

    @Test
    void missingSequenceRowFailsLoudly() {
        when(repository.incrementSequence(SequenceGeneratorService.ACCESSION_SEQUENCE)).thenReturn(0);

        assertThatThrownBy(() -> service.nextAccessionNumber())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACCESSION_SEQ");
    }

    private static DbSequence sequence(long value) {
        DbSequence sequence = new DbSequence();
        sequence.setCurrentValue(value);
        return sequence;
    }
}
