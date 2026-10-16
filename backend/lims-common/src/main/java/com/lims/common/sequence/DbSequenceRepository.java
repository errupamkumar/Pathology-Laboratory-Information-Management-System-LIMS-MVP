package com.lims.common.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DbSequenceRepository extends JpaRepository<DbSequence, String> {

    /**
     * Atomically increments a named sequence. The row lock taken here is held until the
     * surrounding (REQUIRES_NEW) transaction commits, serialising concurrent allocators.
     * The persistence context is cleared so the follow-up read sees the incremented value.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE db_sequences SET current_value = current_value + increment_by WHERE sequence_name = :name", nativeQuery = true)
    int incrementSequence(@Param("name") String sequenceName);
}
