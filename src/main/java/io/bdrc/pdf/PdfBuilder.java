package io.bdrc.pdf;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.security.NoSuchAlgorithmException;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.amazonaws.services.s3.AmazonS3;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfWriter;

import de.digitalcollections.iiif.myhymir.backend.impl.repository.S3ResourceRepositoryImpl;
import io.bdrc.iiif.resolver.IdentifierInfo;

public class PdfBuilder {
    
    public static void buildPdf(TreeMap<Integer,String> idList,
                                IdentifierInfo inf,
                                String output) throws NoSuchAlgorithmException, FileNotFoundException, DocumentException {
        
        ExecutorService service=Executors.newFixedThreadPool(50);
        AmazonS3 s3=S3ResourceRepositoryImpl.getClientInstance();        
        TreeMap<Integer,PdfImageProducer> p_map=new TreeMap<>();
        TreeMap<Integer,Future<?>> t_map=new TreeMap<>();
        
        for(Entry<Integer,String> e:idList.entrySet()) {
            PdfImageProducer tmp=new PdfImageProducer(s3,e.getValue(), inf);
            p_map.put(e.getKey(),tmp);
            Future<?> fut=service.submit(tmp);
            t_map.put(e.getKey(),fut);
        }
        Document document = new Document();
        FileOutputStream fos = new FileOutputStream(output);        
        PdfWriter writer = PdfWriter.getInstance(document, fos);
        writer.open();
        document.open();
        for(int k=1;k<=t_map.keySet().size();k++) {
            Future<?> tmp=t_map.get(k);
            while(!tmp.isDone()) {
                
            };
            Image i=p_map.get(k).getImg();
            if(i!=null) {
                document.setPageSize(new Rectangle(i.getWidth(),i.getHeight()));
                document.newPage();
                document.add(i);
            }
        }
        document.close();
        writer.close();
    }

}