package com.college.laballocation.timetableimport;

import java.util.List;

/**
 * The output of {@link PdfTextExtractor} - plain extracted text, one entry
 * per non-blank line, in page order. Carries no timetable semantics at all
 * (PART 14 of the phase brief: the extractor never resolves subject/faculty/
 * lab identity) - that is {@link TimetableParser}'s job, operating on this
 * record, not on PDFBox types directly.
 */
public record ExtractedPdf(int pageCount, List<String> lines) {

    public boolean isEmpty() {
        return lines.isEmpty();
    }
}
