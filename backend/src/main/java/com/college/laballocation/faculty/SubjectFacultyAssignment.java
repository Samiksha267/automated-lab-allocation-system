package com.college.laballocation.faculty;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.academic.Batch;
import com.college.laballocation.academic.Division;
import com.college.laballocation.subject.Subject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * faculty + subject + division + batch(nullable) + academic term.
 *
 * <p><b>Null-batch semantics (precise, not ambiguous):</b> {@code batchId == null}
 * means this assignment covers the <em>whole division</em> for that
 * subject/term - it is not "unknown batch" or "any batch." A batch-specific
 * row ({@code batchId} set) always takes precedence over a division-level row
 * for the same subject/division/term when resolving "who teaches this
 * session" (see {@link FacultyAssignmentResolutionService}).
 *
 * <p>Database uniqueness (two partial indexes, `uq_sfa_batch_scoped` /
 * `uq_sfa_division_scoped` - see V4 migration) prevents two *active* rows
 * from covering the same batch-level or division-level scope at once, so
 * ambiguous "which faculty teaches this" data cannot silently exist
 * (docs/04-DATABASE-DESIGN.md §3, PART 14 of the phase brief).
 */
@Entity
@Table(name = "subject_faculty_assignment")
public class SubjectFacultyAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "division_id", nullable = false)
    private Division division;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_term_id", nullable = false)
    private AcademicTerm academicTerm;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SubjectFacultyAssignment() {}

    public SubjectFacultyAssignment(
            Subject subject, Faculty faculty, Division division, Batch batch, AcademicTerm academicTerm) {
        this.subject = subject;
        this.faculty = faculty;
        this.division = division;
        this.batch = batch;
        this.academicTerm = academicTerm;
        this.active = true;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Subject getSubject() {
        return subject;
    }

    public Faculty getFaculty() {
        return faculty;
    }

    public Division getDivision() {
        return division;
    }

    public Batch getBatch() {
        return batch;
    }

    public AcademicTerm getAcademicTerm() {
        return academicTerm;
    }

    public boolean isActive() {
        return active;
    }
}
