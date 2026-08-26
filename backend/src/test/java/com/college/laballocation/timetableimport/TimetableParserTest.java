package com.college.laballocation.timetableimport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TimetableParserTest {

    private final TimetableParser parser = new TimetableParser();

    @Test
    void parsesAWellFormedLineIntoItsEightColumns() {
        ExtractedPdf extracted = new ExtractedPdf(1, List.of("MONDAY | 09:00 | 11:00 | BDA | Dr. S. Sharma | B-204 | A | A1"));

        List<ParsedTimetableRow> rows = parser.parse(extracted);

        assertThat(rows).hasSize(1);
        ParsedTimetableRow row = rows.get(0);
        assertThat(row.rawDay()).isEqualTo("MONDAY");
        assertThat(row.rawStartTime()).isEqualTo("09:00");
        assertThat(row.rawEndTime()).isEqualTo("11:00");
        assertThat(row.rawSubject()).isEqualTo("BDA");
        assertThat(row.rawFaculty()).isEqualTo("Dr. S. Sharma");
        assertThat(row.rawLab()).isEqualTo("B-204");
        assertThat(row.rawDivision()).isEqualTo("A");
        assertThat(row.rawBatch()).isEqualTo("A1");
    }

    @Test
    void blankTrailingBatchColumnMeansADivisionWideSession() {
        ExtractedPdf extracted = new ExtractedPdf(1, List.of("TUESDAY | 14:00 | 16:00 | CNS | Dr. R. Iyer | C-101 | B |"));

        List<ParsedTimetableRow> rows = parser.parse(extracted);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).rawBatch()).isEmpty();
    }

    @Test
    void nonTimetableLinesAreSilentlySkippedNotTreatedAsErrors() {
        ExtractedPdf extracted = new ExtractedPdf(
                1,
                List.of(
                        "College of Engineering - Odd Semester Timetable 2026-27",
                        "MONDAY | 09:00 | 11:00 | BDA | Dr. S. Sharma | B-204 | A | A1",
                        "Page 1 of 3"));

        List<ParsedTimetableRow> rows = parser.parse(extracted);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).rawSubject()).isEqualTo("BDA");
    }

    @Test
    void multiplePagesAndMultipleRowsAllParse() {
        ExtractedPdf extracted = new ExtractedPdf(
                2,
                List.of(
                        "MONDAY | 09:00 | 11:00 | BDA | Dr. S. Sharma | B-204 | A | A1",
                        "TUESDAY | 14:00 | 16:00 | CNS | Dr. R. Iyer | C-101 | B | B1",
                        "WEDNESDAY | 10:00 | 12:00 | DSA | Dr. K. Rao | A-101 | A | A2"));

        List<ParsedTimetableRow> rows = parser.parse(extracted);

        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(ParsedTimetableRow::rawDay).containsExactly("MONDAY", "TUESDAY", "WEDNESDAY");
    }

    @Test
    void noParseableLinesProducesAnEmptyResultNeverAnException() {
        ExtractedPdf extracted = new ExtractedPdf(1, List.of("This document contains no pipe-delimited rows at all."));

        List<ParsedTimetableRow> rows = parser.parse(extracted);

        assertThat(rows).isEmpty();
    }
}
