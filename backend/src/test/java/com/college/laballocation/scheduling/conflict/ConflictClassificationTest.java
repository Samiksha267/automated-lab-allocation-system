package com.college.laballocation.scheduling.conflict;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConflictClassificationTest {

    @Test
    void temporalCodesAreClassifiedAsTemporal() {
        assertThat(ConflictClassification.categoryOf("LAB_CONFLICT")).isEqualTo(ConflictCategory.TEMPORAL);
        assertThat(ConflictClassification.categoryOf("FACULTY_CONFLICT")).isEqualTo(ConflictCategory.TEMPORAL);
        assertThat(ConflictClassification.categoryOf("FACULTY_UNAVAILABLE")).isEqualTo(ConflictCategory.TEMPORAL);
        assertThat(ConflictClassification.categoryOf("BATCH_CONFLICT")).isEqualTo(ConflictCategory.TEMPORAL);
        assertThat(ConflictClassification.categoryOf("DIVISION_CONFLICT")).isEqualTo(ConflictCategory.TEMPORAL);
        assertThat(ConflictClassification.categoryOf("LAB_UNAVAILABLE")).isEqualTo(ConflictCategory.TEMPORAL);
    }

    @Test
    void structuralCodesAreClassifiedAsStructural() {
        assertThat(ConflictClassification.categoryOf("CAPACITY_VIOLATION")).isEqualTo(ConflictCategory.STRUCTURAL);
        assertThat(ConflictClassification.categoryOf("SOFTWARE_MISMATCH")).isEqualTo(ConflictCategory.STRUCTURAL);
        assertThat(ConflictClassification.categoryOf("EQUIPMENT_MISMATCH")).isEqualTo(ConflictCategory.STRUCTURAL);
        assertThat(ConflictClassification.categoryOf("LAB_TYPE_MISMATCH")).isEqualTo(ConflictCategory.STRUCTURAL);
        assertThat(ConflictClassification.categoryOf("INVALID_ACADEMIC_RELATIONSHIP")).isEqualTo(ConflictCategory.STRUCTURAL);
        assertThat(ConflictClassification.categoryOf("FORBIDDEN_DIVISION_ACCESS")).isEqualTo(ConflictCategory.STRUCTURAL);
        assertThat(ConflictClassification.categoryOf("CR_ASSIGNMENT_NOT_FOUND")).isEqualTo(ConflictCategory.STRUCTURAL);
    }

    @Test
    void unrecognizedCodeDefaultsToStructural() {
        assertThat(ConflictClassification.categoryOf("SOMETHING_NEW")).isEqualTo(ConflictCategory.STRUCTURAL);
    }
}
