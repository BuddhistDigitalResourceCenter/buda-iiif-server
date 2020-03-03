package io.bdrc.iiif;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

public class PDFBugDemo {

    /*
     * static void testIText() throws IOException { // generate a one page PDF with
     * itext from src/test/resources/default.tif // output pdf in /tmp/testitext.pdf
     * PdfWriter writer = new PdfWriter("testPdfItext.pdf", new
     * WriterProperties().setPdfVersion(PdfVersion.PDF_2_0)); PdfDocument
     * pdfDocument = new PdfDocument(writer); Document document = new
     * Document(pdfDocument); // This is the way to avoid image cropping (for some
     * reasons default margins are // not set to zero) document.setMargins(0, 0, 0,
     * 0); FileInputStream in = new FileInputStream("src/test/resources/test.tif");
     * byte[] img = IOUtils.toByteArray(in); long deb = System.currentTimeMillis();
     * Image image = new Image(ImageDataFactory.create(img));
     * pdfDocument.addNewPage(new PageSize(image.getImageWidth(),
     * image.getImageHeight())); document.add(image);
     * System.out.println("Itext Took " + (System.currentTimeMillis() - deb) +
     * " ms to write the image to page"); document.close(); writer.close(); }
     */

    static void testApachePDFBox() throws IOException {
        PDDocument doc = new PDDocument();
        File img = new File("src/test/resources/test.tif");
        long deb = System.currentTimeMillis();
        BufferedImage bImg = ImageIO.read(img);
        PDPage page = new PDPage(new PDRectangle(bImg.getWidth(), bImg.getHeight()));
        doc.addPage(page);
        PDImageXObject pdImage = LosslessFactory.createFromImage(doc, bImg);
        PDPageContentStream contents = new PDPageContentStream(doc, page);
        contents.drawImage(pdImage, 0, 0);
        contents.close();
        System.out.println("PdfBox took " + (System.currentTimeMillis() - deb) + " ms to write the image to page");
        doc.save(new File("testPdfBox.pdf"));
        doc.close();
    }

    public static void main(String[] args) throws IOException {
        // PDFBugDemo.testIText();
        PDFBugDemo.testApachePDFBox();
    }

}
