package com.college.laballocation.timetableimport;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Transforms {@link ExtractedPdf} lines into {@link ParsedTimetableRow}s
 * (PART 15). This project deliberately does not attempt a universal PDF
 * table-layout parser (PART 4) - only one documented, narrow format is
 * supported: one timetable session per line, 8 pipe-delimited columns, in a
 * fixed order:
 *
 * <pre>DAY | START | END | SUBJECT_CODE | FACULTY_NAME | LAB_CODE | DIVISION_CODE | BATCH_CODE</pre>
 *
 * <p>{@code BATCH_CODE} may be empty (a division-wide session - trailing
 * empty field still required, e.g. {@code "...| A |"}). A line that does not
 * split into exactly 8 pipe-delimited fields is not a parse error for the
 * whole import - it is silently skipped as non-timetable text (a title,
 * header, or footer line a real institutional PDF commonly contains), never
 * silently guessed at. If literally zero lines match this shape, the caller
 * (`TimetableImportService`) treats the whole import as
 * {@code NO_TIMETABLE_ROWS_FOUND}. See docs/18-PDF-IMPORT.md for the full
 * documented format and its limitations (no OCR, no AM/PM times, no
 * multi-row-per-cell merged layouts).
 */
@Component
class TimetableParser {

    private static final int EXPECTED_COLUMNS = 8;

    List<ParsedTimetableRow> parse(ExtractedPdf extracted) {
        List<ParsedTimetableRow> rows = new ArrayList<>();
        for (int i = 0; i < extracted.lines().size(); i++) {
            String line = extracted.lines().get(i);
            String[] parts = line.split("\\|", -1);
            if (parts.length != EXPECTED_COLUMNS) {
                continue;
            }
            rows.add(new ParsedTimetableRow(
                    i + 1,
                    parts[0].trim(),
                    parts[1].trim(),
                    parts[2].trim(),
                    parts[3].trim(),
                    parts[4].trim(),
                    parts[5].trim(),
                    parts[6].trim(),
                    parts[7].trim()));
        }
        return rows;
    }
}
