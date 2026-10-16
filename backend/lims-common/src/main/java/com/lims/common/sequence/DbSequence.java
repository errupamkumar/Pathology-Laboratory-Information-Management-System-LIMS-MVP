package com.lims.common.sequence;

import jakarta.persistence.*;

/**
 * Persistent database-backed sequence counter.
 * Replaces volatile in-memory AtomicLong that resets on restart.
 */
@Entity
@Table(name = "db_sequences")
public class DbSequence {

    @Id
    @Column(name = "sequence_name", length = 100)
    private String sequenceName;

    @Column(name = "current_value", nullable = false)
    private Long currentValue = 0L;

    @Column(name = "prefix", nullable = false, length = 50)
    private String prefix = "";

    @Column(name = "increment_by", nullable = false)
    private Integer incrementBy = 1;

    public DbSequence() {}

    public String getSequenceName() { return sequenceName; }
    public void setSequenceName(String sequenceName) { this.sequenceName = sequenceName; }

    public Long getCurrentValue() { return currentValue; }
    public void setCurrentValue(Long currentValue) { this.currentValue = currentValue; }

    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }

    public Integer getIncrementBy() { return incrementBy; }
    public void setIncrementBy(Integer incrementBy) { this.incrementBy = incrementBy; }
}
