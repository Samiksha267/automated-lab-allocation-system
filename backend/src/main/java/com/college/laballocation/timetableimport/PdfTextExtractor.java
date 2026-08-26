package com.college.laballocation.timetableimport;

import com.college.laballocation.common.ApiException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * PDF text extraction, isolated from timetable parsing (PART 14) - opens
 * the document, reads every page's text, and hands back plain lines. Never
 * resolves academic identity, never renders/executes anything beyond text
 * extraction (PART 45: no embedded JavaScript/attachments/macros are ever
 * invoked - {@link PDFTextStripper} only reads glyph-to-text mappings).
 *
 * <p>Uses Apache PDFBox {@code Loader.loadPDF(byte[])} directly on the
 * uploaded bytes (never writes the upload to a filesystem path derived from
 * the client-supplied filename, PART 45/46 - the only "identity" this
 * system ever trusts for the file is its own computed SHA-256 hash).
 */
@Component
class PdfTextExtractor {

    ExtractedPdf extract(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.isEncrypted()) {
                throw new ApiException(
                        "UNSUPPORTED_PDF", HttpStatus.UNPROCESSABLE_ENTITY, "Encrypted/password-protected PDFs are not supported.");
            }
            String text = new PDFTextStripper().getText(document);
            List<String> lines = Arrays.stream(text.split("\\r?\\n"))
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .toList();
            return new ExtractedPdf(document.getNumberOfPages(), lines);
        } catch (IOException e) {
            // Deliberately never surfaces the raw PDFBox stack trace to the client (PART 43) -
            // logged at the caller (TimetableImportService) with the import id for operators.
            throw new ApiException("UNSUPPORTED_PDF", HttpStatus.UNPROCESSABLE_ENTITY, "The uploaded file could not be read as a PDF.");
        }
    }
}
