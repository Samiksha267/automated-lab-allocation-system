package com.college.laballocation.timetableimport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

/**
 * Phase 25 opt-in benchmark for the pure, JPA/Spring-free PDF pipeline stages ({@link PdfTextExtractor},
 * {@link TimetableParser}, {@link TimetableNormalizer}) - named {@code *Benchmark}, not {@code *Test}, so
 * an ordinary {@code mvn test} never runs it (Surefire's default include is <code>**&#47;*Test.java</code>).
 * Run explicitly via {@code mvn test -Dtest=PdfImportBenchmark}.
 *
 * <p>Mapping/validation/approval (Phase 19's DB-backed stages, {@code TimetableMappingService}/the
 * constraint engine/{@code TimetableImportService.approve}) are benchmarked separately, live, against
 * the real Dockerized backend + PostgreSQL (docs/16-PERFORMANCE-BENCHMARKS.md) - they cannot run
 * standalone here for the same reason every other DB-backed component can't (no Testcontainers Docker
 * access inside this sandbox's Maven build).
 */
class PdfImportBenchmark {

    private final PdfTextExtractor extractor = new PdfTextExtractor();
    private final TimetableParser parser = new TimetableParser();

    private static byte[] pdfWithLines(List<String> lines, int linesPerPage) throws IOException {
        try (PDDocument document = new PDDocument()) {
            int pages = (int) Math.ceil(lines.size() / (double) linesPerPage);
            for (int p = 0; p < pages; p++) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                List<String> pageLines = lines.subList(p * linesPerPage, Math.min(lines.size(), (p + 1) * linesPerPage));
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                    stream.beginText();
                    stream.newLineAtOffset(50, 750);
                    for (String line : pageLines) {
                        stream.showText(line);
                        stream.newLineAtOffset(0, -15);
                    }
                    stream.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static List<String> timetableRows(int count) {
        String[] days = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"};
        List<String> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String day = days[i % days.length];
            int hour = 9 + (i % 8);
            lines.add(String.format(
                    "%s | %02d:00 | %02d:00 | SUB%d | Dr. Faculty%d | LAB-%d | DIV%d | B%d",
                    day, hour, hour + 1, i % 30, i % 15, i % 15, i % 20, i % 3));
        }
        return lines;
    }

    private record Timing(double medianMs, double p95Ms, double minMs, double maxMs) {
        static Timing of(List<Long> nanos) {
            List<Long> sorted = new ArrayList<>(nanos);
            sorted.sort(Long::compareTo);
            long median = sorted.get(sorted.size() / 2);
            long p95 = sorted.get((int) Math.min(sorted.size() - 1, Math.ceil(sorted.size() * 0.95) - 1));
            return new Timing(median / 1e6, p95 / 1e6, sorted.get(0) / 1e6, sorted.get(sorted.size() - 1) / 1e6);
        }

        String describe() {
            return String.format("median=%.3fms p95=%.3fms min=%.3fms max=%.3fms", medianMs, p95Ms, minMs, maxMs);
        }
    }

    private Timing time(int warmup, int measured, Runnable op) {
        for (int i = 0; i < warmup; i++) {
            op.run();
        }
        List<Long> samples = new ArrayList<>(measured);
        for (int i = 0; i < measured; i++) {
            long start = System.nanoTime();
            op.run();
            samples.add(System.nanoTime() - start);
        }
        return Timing.of(samples);
    }

    /** PART 31 - extraction time by page count, using timetable-shaped rows (~35 rows/page at 15px line spacing on A4). */
    @Test
    void extractionByPageCount() throws IOException {
        for (int pages : new int[] {1, 5, 20}) {
            int rowsPerPage = 35;
            byte[] pdf = pdfWithLines(timetableRows(pages * rowsPerPage), rowsPerPage);
            Timing t = time(5, 20, () -> extractor.extract(pdf));
            var extracted = extractor.extract(pdf);
            System.out.println("[BENCHMARK] PDF extraction pages=" + pages + " (extracted pageCount=" + extracted.pageCount()
                    + ", lines=" + extracted.lines().size() + ", bytes=" + pdf.length + "): " + t.describe());
        }
    }

    /** PART 32 - parsing (and normalization, called directly since it's the pure downstream step) by row count. */
    @Test
    void parsingAndNormalizationByRowCount() throws IOException {
        for (int rows : new int[] {10, 100, 500}) {
            int rowsPerPage = 35;
            byte[] pdf = pdfWithLines(timetableRows(rows), rowsPerPage);
            var extracted = extractor.extract(pdf);

            Timing parseTiming = time(5, 20, () -> parser.parse(extracted));
            List<ParsedTimetableRow> parsed = parser.parse(extracted);

            Timing normalizeTiming = time(5, 20, () -> {
                for (ParsedTimetableRow row : parsed) {
                    TimetableNormalizer.normalizeDay(row.rawDay());
                    TimetableNormalizer.normalizeTime(row.rawStartTime());
                    TimetableNormalizer.normalizeTime(row.rawEndTime());
                    TimetableNormalizer.normalizeToken(row.rawSubject());
                    TimetableNormalizer.normalizeToken(row.rawFaculty());
                    TimetableNormalizer.normalizeToken(row.rawLab());
                    TimetableNormalizer.normalizeToken(row.rawDivision());
                    TimetableNormalizer.normalizeToken(row.rawBatch());
                }
            });

            long successfulDayNormalizations =
                    parsed.stream().filter(r -> TimetableNormalizer.normalizeDay(r.rawDay()) != null).count();

            System.out.println("[BENCHMARK] PDF parsing rows=" + rows + " (parsed=" + parsed.size() + "): " + parseTiming.describe());
            System.out.println("[BENCHMARK] PDF normalization rows=" + rows + " (successful day-normalizations=" + successfulDayNormalizations
                    + "/" + parsed.size() + "): " + normalizeTiming.describe());
        }
    }

    /**
     * Writes two real PDF fixtures to {@code target/} so a live HTTP benchmark script (outside this JVM,
     * against the real Dockerized backend + PostgreSQL) can upload/map/validate/approve them through the
     * actual production pipeline - {@code TimetableMappingService} and the constraint engine are
     * JPA-repository-backed and cannot run standalone in this sandbox (see class javadoc). Run explicitly:
     * {@code mvn test -Dtest=PdfImportBenchmark#writeLiveBenchmarkFixtures}.
     */
    @Test
    void writeLiveBenchmarkFixtures() throws IOException {
        int rowsPerPage = 35;
        // "Mostly valid": real seeded codes (DevAcademicSeeder/DevLabSeeder), MONDAY only (both FAC-BDA and
        // FAC-CNS have a Monday availability window), each row on a distinct hour/lab so no row
        // double-books another row's lab, faculty, or division/batch within the same import.
        List<String> validLines = new ArrayList<>();
        String[] bdaLabs = {"B-301", "C-202"}; // both stock Cloudera (established in earlier live verification)
        String[] cnsLabs = {"B-201", "C-304"};
        int[] bdaHours = {14, 15}; // within FAC-BDA's 14:00-17:00 Monday window, avoiding its already-booked 09:00-11:00 slot
        int[] cnsHours = {9, 10}; // within FAC-CNS's 09:00-13:00 Monday window
        for (int i = 0; i < bdaHours.length; i++) {
            validLines.add(String.format("MONDAY | %02d:00 | %02d:00 | BDA | Faculty BDA | %s | A | A1", bdaHours[i], bdaHours[i] + 1, bdaLabs[i]));
        }
        for (int i = 0; i < cnsHours.length; i++) {
            validLines.add(String.format("MONDAY | %02d:00 | %02d:00 | CNS | Faculty CNS | %s | A | A2", cnsHours[i], cnsHours[i] + 1, cnsLabs[i]));
        }
        Files.write(Path.of("target/bench-mostly-valid.pdf"), pdfWithLines(validLines, rowsPerPage));

        // "High-conflict": unknown lab codes and an unresolvable subject/division/batch combination -
        // every row should fail mapping with a real, documented error (UNKNOWN_LAB / UNRESOLVED_ACADEMIC_ASSIGNMENT).
        List<String> conflictLines = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            conflictLines.add(String.format("TUESDAY | 10:00 | 11:00 | BDA | Faculty BDA | NO-SUCH-LAB-%d | A | A1", i));
        }
        Files.write(Path.of("target/bench-high-conflict.pdf"), pdfWithLines(conflictLines, rowsPerPage));

        System.out.println("[BENCHMARK] Wrote target/bench-mostly-valid.pdf (" + validLines.size() + " rows) and target/bench-high-conflict.pdf ("
                + conflictLines.size() + " rows) for live upload benchmarking.");
    }
}
