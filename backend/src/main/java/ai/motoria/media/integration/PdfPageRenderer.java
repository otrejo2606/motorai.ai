package ai.motoria.media.integration;

import ai.motoria.common.exception.BusinessRuleException;
import ai.motoria.media.exception.MediaErrorCodes;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders PDF pages to PNG with Apache PDFBox so the analyst sees images, not the PDF (design D5).
 */
@ApplicationScoped
public class PdfPageRenderer {

    public List<RenderedPage> render(byte[] pdf, int maxPages, float dpi) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = Math.min(document.getNumberOfPages(), maxPages);
            List<RenderedPage> rendered = new ArrayList<>(pages);
            for (int index = 0; index < pages; index++) {
                BufferedImage image = renderer.renderImageWithDPI(index, dpi, ImageType.RGB);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageIO.write(image, "png", output);
                rendered.add(new RenderedPage(index + 1, output.toByteArray(), image.getWidth(), image.getHeight()));
            }
            return rendered;
        } catch (IOException exception) {
            throw new BusinessRuleException(MediaErrorCodes.MEDIA_PDF_UNREADABLE, "The PDF could not be read");
        }
    }
}
