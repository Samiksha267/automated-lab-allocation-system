package com.college.laballocation.timetableimport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

/**
 * Phase 29 demo-only fixture generator - produces a real, deterministic, byte-for-byte-genuine PDF
 * (via PDFBox's own writer API, the same approach {@code PdfExtractionAndParsingTest}/{@code PdfImportBenchmark}
 * already use, not a hand-edited or externally-sourced file) containing exactly one row with a real,
 * correctable problem: BDA assigned to a lab without Cloudera - the project's own headline
 * SOFTWARE_MISMATCH example (docs/17-DEMO-SCENARIOS.md, Scenario 12).
 *
 * <p>Named {@code *Generator}, not {@code *Test}, so an ordinary {@code mvn test} never runs it. Run
 * explicitly before a demo: {@code mvn test -Dtest=DemoPdfFixtureGenerator}. Writes
 * {@code target/demo-timetable.pdf} - regenerated on demand rather than committed as a binary file,
 * matching this project's established PDF-fixture convention (docs/18-PDF-IMPORT.md, "Test Fixtures").
 */
class DemoPdfFixtureGenerator {

    @Test
    void writeDemoFixture() throws IOException {
        // The importer always maps "MONDAY" to the academic term's actual first Monday (2026-07-20) -
        // a fixed calendar date, not something the PDF text itself can choose. 11:00-12:00 is the one
        // hour within Faculty BDA's real seeded MONDAY availability (09:00-12:00, 14:00-17:00) this
        // project's own accumulated live-verification history hasn't already booked on that date - so
        // the row's only real problem is the lab, exactly what this demo needs to isolate. B-101 is a
        // real seeded lab that does NOT carry Cloudera - the row fails SOFTWARE_MISMATCH alone.
        List<String> lines = List.of(
                "College of Engineering - Timetable Import (Demo)",
                "MONDAY | 11:00 | 12:00 | BDA | Faculty BDA | B-101 | A | A1");

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
            Files.write(Path.of("target/demo-timetable.pdf"), out.toByteArray());
        }

        System.out.println("[DEMO] Wrote target/demo-timetable.pdf - 1 row, BDA/A1/Faculty BDA/B-101 (no Cloudera) Monday 11:00-12:00.");
    }
}
