package io.bdrc.archives;

import static io.bdrc.pdf.presentation.AppConstants.GENERIC_APP_ERROR_CODE;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.s3.AmazonS3;
import com.itextpdf.text.BadElementException;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfWriter;

import de.digitalcollections.iiif.myhymir.ServerCache;
import de.digitalcollections.iiif.myhymir.backend.impl.repository.S3ResourceRepositoryImpl;
import io.bdrc.iiif.resolver.IdentifierInfo;
import io.bdrc.pdf.presentation.exceptions.BDRCAPIException;

public class ArchiveBuilder {

    public static final String IIIF="IIIF";
    public static final String IIIF_ZIP="IIIF_ZIP";
    public final static String PDF_TYPE = "pdf";
    public final static String ZIP_TYPE = "zip";

    public final static Logger log=LoggerFactory.getLogger(ArchiveBuilder.class.getName());

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void buildPdf(Iterator<String> idList,IdentifierInfo inf,String output)
                            throws BDRCAPIException {
        ExecutorService service=Executors.newFixedThreadPool(50);
        AmazonS3 s3=S3ResourceRepositoryImpl.getClientInstance();
        TreeMap<Integer,Future<?>> t_map=new TreeMap<>();
        int i = 1;
        while(idList.hasNext()) {
            final String id = inf.getVolumeId()+"::"+idList.next();
            ArchiveImageProducer tmp=null;
            tmp = new ArchiveImageProducer(s3, id,PDF_TYPE);
            Future<?> fut=service.submit((Callable)tmp);
            t_map.put(i,fut);
            i += 1;
        }
        ServerCache.addToCache("pdfjobs",output,false);
        Document document = new Document();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PdfWriter writer=null;
        try {
            writer = PdfWriter.getInstance(document, stream);
        } catch (DocumentException e) {
            throw new BDRCAPIException(500, GENERIC_APP_ERROR_CODE, e);
        }
        writer.open();
        document.open();
        for(int k=1;k<=t_map.keySet().size();k++) {
            Future<?> tmp=t_map.get(k);
            Image img=null;
            try {
                img = (Image)tmp.get();
                if(img==null) {
                  //Trying to insert image indicating that original image is missing
                    try {
                        img=ArchiveImageProducer.getMissingImage("Page "+k +" couldn't be found");
                        document.setPageSize(new Rectangle(img.getWidth(),img.getHeight()));
                        document.newPage();
                        document.add(img);
                    } catch (BadElementException | IOException e) {
                        // We don't interrupt the pdf generation process
                        e.printStackTrace();
                    }
                }
                document.setPageSize(new Rectangle(img.getWidth(),img.getHeight()));
                document.newPage();
                document.add(img);
            }
            catch (DocumentException | ExecutionException | InterruptedException e) {
                throw new BDRCAPIException(500, GENERIC_APP_ERROR_CODE, e);
            }
        }
        document.close();
        ServerCache.addToCache(IIIF,output.substring(4), stream.toByteArray());
        writer.close();
        ServerCache.addToCache("pdfjobs",output,true);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void buildZip(Iterator<String> idList,IdentifierInfo inf,String output) throws BDRCAPIException {
        ExecutorService service=Executors.newFixedThreadPool(50);
        AmazonS3 s3=S3ResourceRepositoryImpl.getClientInstance();
        TreeMap<Integer,Future<?>> t_map=new TreeMap<>();
        TreeMap<Integer,String> images=new TreeMap<>();
        int i = 1;
        while(idList.hasNext()) {
            String img=idList.next();
            final String id = inf.getVolumeId()+"::"+img;
            ArchiveImageProducer tmp=null;
            tmp = new ArchiveImageProducer(s3, id,ZIP_TYPE);
            Future<?> fut=service.submit((Callable)tmp);
            t_map.put(i,fut);
            images.put(i,img);
            i += 1;
        }
        ServerCache.addToCache("zipjobs",output, false);
        ByteArrayOutputStream baos=new ByteArrayOutputStream();
        ZipOutputStream zipOut= new ZipOutputStream(baos);
        try {
            for(int k=1;k<=t_map.keySet().size();k++) {
                Future<?> tmp=t_map.get(k);
                byte[] img=null;
                img = (byte[])tmp.get();
                if(img==null) {
                    //Trying to insert image indicating that original image is missing
                    try {
                        img=ArchiveImageProducer.getMissingImage("Page "+k +" couldn't be found").getRawData();
                    } catch (BadElementException | IOException e) {
                        // We don't interrupt the pdf generation process
                        e.printStackTrace();
                    }
                }
                ZipEntry zipEntry = new ZipEntry(images.get(k));
                zipOut.putNextEntry(zipEntry);
                zipOut.write(img);
                zipOut.closeEntry();
            }
            zipOut.close();
        }
        catch (IOException | ExecutionException | InterruptedException e) {
            throw new BDRCAPIException(500, GENERIC_APP_ERROR_CODE, e);
        }
        ServerCache.addToCache(IIIF_ZIP,output.substring(3),baos.toByteArray());
        ServerCache.addToCache("zipjobs", output, true);
    }

    public static boolean isPdfDone(String id) {
        log.debug("IS PDF DONE job "+id);
        if(ServerCache.getObjectFromCache("pdfjobs",id)==null) {
            log.debug("IS PDF DONE null in cache for "+id);
            return false;
        }
        log.debug("IS PDF DONE returns from cache value for "+id+ ">>"+(boolean)ServerCache.getObjectFromCache("pdfjobs",id));
        return (boolean)ServerCache.getObjectFromCache("pdfjobs",id);
    }

    public static boolean isZipDone(String id) {
        log.debug("IS ZIP DONE job "+id);
        if(ServerCache.getObjectFromCache("zipjobs",id)==null) {
            log.debug("IS ZIP DONE null in cache for "+id);
            return false;
        }
        log.debug("IS ZIP DONE returns from cache value for "+id+ ">>"+(boolean)ServerCache.getObjectFromCache("zipjobs",id));
        return (boolean)ServerCache.getObjectFromCache("zipjobs",id);
    }
}
