package ai.motoria.ai.integration;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Extracts the text of each page with Apache PDFBox. When the PDF is a scan (little or no text), the pages are
 * rendered as PNG so a vision-capable model can read them (design D4).
 */
@ApplicationScoped
public class PdfTextExtractor {

    private static final float IMAGE_DPI = 110f;

    public PdfContent extract(byte[] pdf, int maxPages, int minTextChars) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            int pages = Math.min(document.getNumberOfPages(), maxPages);
            PDFTextStripper stripper = new PDFTextStripper();
            List<String> texts = new ArrayList<>(pages);
            int totalChars = 0;
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                texts.add(text);
                totalChars += text.trim().length();
            }
            List<String> images = new ArrayList<>();
            if (totalChars < minTextChars) {
                PDFRenderer renderer = new PDFRenderer(document);
                for (int index = 0; index < pages; index++) {
                    BufferedImage image = renderer.renderImageWithDPI(index, IMAGE_DPI, ImageType.RGB);
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    ImageIO.write(image, "png", output);
                    images.add(Base64.getEncoder().encodeToString(output.toByteArray()));
                }
            }
            return new PdfContent(pages, texts, images);
        } catch (IOException exception) {
            throw new UncheckedIOException("The PDF could not be read", exception);
        }
    }
}
