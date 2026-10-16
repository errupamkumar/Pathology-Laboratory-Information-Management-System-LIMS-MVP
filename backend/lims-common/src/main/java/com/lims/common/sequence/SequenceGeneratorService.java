package com.lims.common.sequence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

/**
 * Thread-safe, restart-persistent sequence generator using database-backed counters.
 * <p>
 * Each call runs in its own short transaction: the {@code UPDATE ... SET current_value = current_value + n}
 * takes a row lock that serialises concurrent callers and is released on commit, so the business
 * transaction never holds the sequence lock. As with native DB sequences, a rolled-back business
 * transaction leaves a gap rather than a duplicate.
 */
@Service
public class SequenceGeneratorService {

    public static final String UHID_SEQUENCE = "UHID_SEQ";
    public static final String ACCESSION_SEQUENCE = "ACCESSION_SEQ";

    private static final Logger log = LoggerFactory.getLogger(SequenceGeneratorService.class);

    private final DbSequenceRepository dbSequenceRepository;

    public SequenceGeneratorService(DbSequenceRepository dbSequenceRepository) {
        this.dbSequenceRepository = dbSequenceRepository;
    }

    /**
     * Next UHID in format {@code UHID-{YEAR}-{SEQ}} (zero-padded to at least 4 digits).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextUhid() {
        return format("UHID", next(UHID_SEQUENCE));
    }

    /**
     * Next accession number in format {@code ACC-{YEAR}-{SEQ}} (zero-padded to at least 4 digits).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextAccessionNumber() {
        return format("ACC", next(ACCESSION_SEQUENCE));
    }

    private long next(String sequenceName) {
        int updated = dbSequenceRepository.incrementSequence(sequenceName);
        if (updated != 1) {
            throw new IllegalStateException("Sequence '" + sequenceName + "' not found in db_sequences table");
        }
        long value = dbSequenceRepository.findById(sequenceName)
                .map(DbSequence::getCurrentValue)
                .orElseThrow(() -> new IllegalStateException("Sequence '" + sequenceName + "' not found in db_sequences table"));
        log.debug("Allocated {} -> {}", sequenceName, value);
        return value;
    }

    /**
     * No modulo: the value only ever grows, so identifiers stay unique once the counter passes 9999.
     */
    static String format(String prefix, long value) {
        return String.format("%s-%d-%04d", prefix, Year.now().getValue(), value);
    }
}
