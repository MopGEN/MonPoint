package com.monpoint.reportes.pdf;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/** Lee un PDF generado en memoria y devuelve el texto de cada página, para comprobar su contenido. */
public final class TextoPdf {

    private TextoPdf() {
    }

    public static List<String> paginas(byte[] pdf) {
        try (PdfDocument documento = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            List<String> paginas = new ArrayList<>();
            for (int i = 1; i <= documento.getNumberOfPages(); i++) {
                paginas.add(PdfTextExtractor.getTextFromPage(documento.getPage(i)));
            }
            return paginas;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static String de(byte[] pdf) {
        return String.join("\n", paginas(pdf));
    }

    public static String titulo(byte[] pdf) {
        try (PdfDocument documento = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            return documento.getDocumentInfo().getTitle();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
