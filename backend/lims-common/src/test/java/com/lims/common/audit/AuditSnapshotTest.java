package com.lims.common.audit;

import com.lims.common.entity.BaseEntity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditSnapshotTest {

    static class Parent extends BaseEntity {
        Parent(UUID id) { super(id); }
    }

    @Auditable
    static class Child extends BaseEntity {
        String name = "Hemoglobin";
        BigDecimal value = new BigDecimal("13.4");
        @ManyToOne Parent parent;
        @OneToMany List<Parent> siblings = List.of(new Parent(UUID.randomUUID()));
        @Transient String scratch = "ignored";

        Child(Parent parent) { this.parent = parent; }
    }

    @Test
    void copiesScalarsRecordsAssociationsByIdAndSkipsCollections() {
        UUID parentId = UUID.randomUUID();
        Child child = new Child(new Parent(parentId));

        Map<String, Object> snapshot = AuditSnapshot.of(child);

        assertThat(snapshot)
                .containsEntry("name", "Hemoglobin")
                .containsEntry("value", new BigDecimal("13.4"))
                .containsEntry("parentId", parentId)
                .containsEntry("id", child.getId())
                .doesNotContainKeys("parent", "siblings", "scratch");
    }
}
