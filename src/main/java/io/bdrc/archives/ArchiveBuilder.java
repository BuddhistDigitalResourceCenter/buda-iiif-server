package io.bdrc.archives;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdfwriter.COSWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.s3.AmazonS3;

import de.digitalcollections.iiif.myhymir.Application;
import de.digitalcollections.iiif.myhymir.ServerCache;
import de.digitalcollections.iiif.myhymir.backend.impl.repository.S3ResourceRepositoryImpl;
import io.bdrc.iiif.exceptions.IIIFException;
import io.bdrc.iiif.resolver.IdentifierInfo;

public class ArchiveBuilder {

    public static final String IIIF = "IIIF";
    public static final String IIIF_ZIP = "IIIF_ZIP";
    public final static String PDF_TYPE = "pdf";
    public final static String ZIP_TYPE = "zip";

    public final static Logger log = LoggerFactory.getLogger(ArchiveBuilder.class.getName());

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void buildPdf(Iterator<String> idList, IdentifierInfo inf, String output, String origin) throws IIIFException, IOException {
        long deb = System.currentTimeMillis();
        try {
            Application.logPerf("Starting building pdf {}", inf.volumeId);
            ExecutorService service = Executors.newFixedThreadPool(50);
            AmazonS3 s3 = S3ResourceRepositoryImpl.getClientInstance();
            Application.logPerf("S3 client obtained in building pdf {} after {} ", inf.volumeId, System.currentTimeMillis() - deb);
            TreeMap<Integer, Future<?>> t_map = new TreeMap<>();
            int i = 1;
            while (idList.hasNext()) {
                final String id = inf.volumeId + "::" + idList.next();
                ArchiveImageProducer tmp = null;
                tmp = new ArchiveImageProducer(s3, id, PDF_TYPE, origin);
                Future<?> fut = service.submit((Callable) tmp);
                t_map.put(i, fut);
                i += 1;
            }
            ServerCache.addToCache("pdfjobs", output, false);
            PDDocument doc = new PDDocument();

            doc.setDocumentInformation(ArchiveInfo.getInstance(inf).getDocInformation());
            Application.logPerf("building pdf writer and document opened {} after {}", inf.volumeId, System.currentTimeMillis() - deb);
            for (int k = 1; k <= t_map.keySet().size(); k++) {
                Future<?> tmp = t_map.get(k);

                BufferedImage bImg = (BufferedImage) tmp.get();
                if (bImg == null) {
                    // Trying to insert image indicating that original image is missing
                    try {
                        bImg = ArchiveImageProducer.getBufferedMissingImage("Page " + k + " couldn't be found");
                    } catch (Exception e) {
                        // We don't interrupt the pdf generation process
                        log.error("Could not get Buffered Missing image from producer for page {} of volume {}", k, inf.volumeId);
                    }
                }
                PDPage page = new PDPage(new PDRectangle(bImg.getWidth(), bImg.getHeight()));
                doc.addPage(page);
                PDImageXObject pdImage = LosslessFactory.createFromImage(doc, bImg);
                PDPageContentStream contents = new PDPageContentStream(doc, page);
                contents.drawImage(pdImage, 0, 0);
                contents.close();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                COSWriter cw = new COSWriter(baos);
                cw.write(doc);
                Application.logPerf("pdf document finished and closed for {} after {}", inf.volumeId, System.currentTimeMillis() - deb);
                ServerCache.addToCache(IIIF, output.substring(4), baos.toByteArray());
                cw.close();
                doc.close();
                ServerCache.addToCache("pdfjobs", output, true);
            }
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error while building pdf for identifier info " + inf.toString(), "");
            throw new IIIFException(500, IIIFException.GENERIC_APP_ERROR_CODE, e);
        }

    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void buildZip(Iterator<String> idList, IdentifierInfo inf, String output, String origin) throws IIIFException {
        try {
            long deb = System.currentTimeMillis();
            Application.logPerf("Starting building zip {}", inf.volumeId);
            ExecutorService service = Executors.newFixedThreadPool(50);
            AmazonS3 s3 = S3ResourceRepositoryImpl.getClientInstance();
            Application.logPerf("S3 client obtained in building pdf {} after {} ", inf.volumeId, System.currentTimeMillis() - deb);
            TreeMap<Integer, Future<?>> t_map = new TreeMap<>();
            TreeMap<Integer, String> images = new TreeMap<>();
            int i = 1;
            while (idList.hasNext()) {
                String img = idList.next();
                final String id = inf.volumeId + "::" + img;
                ArchiveImageProducer tmp = null;
                tmp = new ArchiveImageProducer(s3, id, ZIP_TYPE, origin);
                Future<?> fut = service.submit((Callable) tmp);
                t_map.put(i, fut);
                images.put(i, img);
                i += 1;
            }
            ServerCache.addToCache("zipjobs", output, false);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zipOut = new ZipOutputStream(baos);
            Application.logPerf("building zip stream opened {} after {}", inf.volumeId, System.currentTimeMillis() - deb);

            for (int k = 1; k <= t_map.keySet().size(); k++) {
                Future<?> tmp = t_map.get(k);
                byte[] img = null;
                img = (byte[]) tmp.get();
                if (img == null) {
                    // Trying to insert image indicating that original image is missing
                    try {
                        BufferedImage bImg = ArchiveImageProducer.getBufferedMissingImage("Page " + k + " couldn't be found");
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        ImageIO.write(bImg, "png", out);
                        img = out.toByteArray();
                    } catch (IOException e) {
                        // We don't interrupt the pdf generation process
                        log.error("Could not get Buffered Missing image from producer for page {} of volume {}", k, inf.volumeId);
                    }
                }
                ZipEntry zipEntry = new ZipEntry(images.get(k));
                zipOut.putNextEntry(zipEntry);
                zipOut.write(img);
                zipOut.closeEntry();
            }
            zipOut.close();
            Application.logPerf("zip document finished and closed for {} after {}", inf.volumeId, System.currentTimeMillis() - deb);
            ServerCache.addToCache(IIIF_ZIP, output.substring(3), baos.toByteArray());
            ServerCache.addToCache("zipjobs", output, true);
        } catch (IOException | ExecutionException | InterruptedException e) {
            log.error("Error while building zip archives ", e.getMessage());
            throw new IIIFException(500, IIIFException.GENERIC_APP_ERROR_CODE, e);
        }
    }

    public static boolean isPdfDone(String id) {
        log.debug("IS PDF DONE job " + id);
        if (ServerCache.getObjectFromCache("pdfjobs", id) == null) {
            log.debug("IS PDF DONE null in cache for " + id);
            return false;
        }
        log.debug("IS PDF DONE returns from cache value for " + id + ">>" + (boolean) ServerCache.getObjectFromCache("pdfjobs", id));
        return (boolean) ServerCache.getObjectFromCache("pdfjobs", id);
    }

    public static boolean isZipDone(String id) {
        log.debug("IS ZIP DONE job " + id);
        if (ServerCache.getObjectFromCache("zipjobs", id) == null) {
            log.debug("IS ZIP DONE null in cache for " + id);
            return false;
        }
        log.debug("IS ZIP DONE returns from cache value for " + id + ">>" + (boolean) ServerCache.getObjectFromCache("zipjobs", id));
        return (boolean) ServerCache.getObjectFromCache("zipjobs", id);
    }
}
