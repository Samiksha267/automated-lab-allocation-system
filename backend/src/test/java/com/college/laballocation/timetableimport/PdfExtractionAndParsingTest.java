package com.college.laballocation.timetableimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.college.laballocation.common.ApiException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

/**
 * Proves real PDF byte extraction (PART 50/51 - "do not test only mocked
 * strings if the production extractor reads actual PDFs"), not just
 * hand-written {@code ExtractedPdf}/string fixtures. Fixtures are generated
 * deterministically by this test itself via PDFBox's own writer API rather
 * than committed as static binary files under {@code src/test/resources} -
 * a deliberate choice (docs/18-PDF-IMPORT.md, "Test Fixtures"): identical
 * proof of genuine PDF-byte round-tripping, no binary blobs in version
 * control, no risk of ever accidentally committing a real institutional
 * timetable PDF.
 */
class PdfExtractionAndParsingTest {

    private final PdfTextExtractor extractor = new PdfTextExtractor();
    private final TimetableParser parser = new TimetableParser();

    private static byte[] pdfWithLines(List<String> lines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                stream.beginText();
                stream.newLineAtOffset(50, 750);
                for (String line : lines) {
                    stream.showText(line);
                    stream.newLineAtOffset(0, -15);
                }
                stream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] emptyPdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.A4));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void extractsRealPdfTextAndParsesItIntoTimetableRows() throws IOException {
        byte[] pdf = pdfWithLines(List.of(
                "College of Engineering - Timetable",
                "MONDAY | 09:00 | 11:00 | BDA | Dr. S. Sharma | B-204 | A | A1",
                "TUESDAY | 14:00 | 16:00 | CNS | Dr. R. Iyer | C-101 | B | B1"));

        ExtractedPdf extracted = extractor.extract(pdf);
        assertThat(extracted.pageCount()).isEqualTo(1);
        assertThat(extracted.lines()).isNotEmpty();

        List<ParsedTimetableRow> rows = parser.parse(extracted);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).rawSubject()).isEqualTo("BDA");
        assertThat(rows.get(1).rawSubject()).isEqualTo("CNS");
    }

    @Test
    void emptyPdfExtractsToNoLines() throws IOException {
        ExtractedPdf extracted = extractor.extract(emptyPdf());

        assertThat(extracted.isEmpty()).isTrue();
    }

    @Test
    void nonPdfBytesAreRejectedCleanlyNotWithARawStackTrace() {
        byte[] notAPdf = "this is definitely not a PDF file".getBytes();

        assertThatThrownBy(() -> extractor.extract(notAPdf))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("UNSUPPORTED_PDF"));
    }

    @Test
    void multiPageDocumentReportsCorrectPageCount() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.A4));
            document.addPage(new PDPage(PDRectangle.A4));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);

            ExtractedPdf extracted = extractor.extract(out.toByteArray());
            assertThat(extracted.pageCount()).isEqualTo(2);
        }
    }
}
