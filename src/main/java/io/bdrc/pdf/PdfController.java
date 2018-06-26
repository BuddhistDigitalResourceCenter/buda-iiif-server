package io.bdrc.pdf;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.text.StrSubstitutor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.digitalcollections.iiif.myhymir.ServerCache;
import io.bdrc.iiif.resolver.IdentifierInfo;
import io.bdrc.pdf.presentation.ItemInfoService;
import io.bdrc.pdf.presentation.VolumeInfoService;
import io.bdrc.pdf.presentation.exceptions.BDRCAPIException;
import io.bdrc.pdf.presentation.models.AccessType;
import io.bdrc.pdf.presentation.models.Identifier;
import io.bdrc.pdf.presentation.models.ItemInfo;
import io.bdrc.pdf.presentation.models.ItemInfo.VolumeInfoSmall;
import io.bdrc.pdf.presentation.models.VolumeInfo;

@Controller
@RequestMapping("/pdfdownload/")
public class PdfController {
    
    public static PdfServiceRegistry registry = PdfServiceRegistry.getInstance();

    @RequestMapping(value = "{id}", method = {RequestMethod.GET,RequestMethod.HEAD})
    public ResponseEntity<String> getPdfLink(@PathVariable String id,HttpServletRequest request) throws Exception {
        String format=request.getHeader("Accept");
        boolean json=format.contains("application/json");
        String output =null;
        Identifier idf=new Identifier(id,Identifier.MANIFEST_ID);        
        HttpHeaders headers = new HttpHeaders();        
        HashMap<String,String> map=new HashMap<>();
        StrSubstitutor s=null;
        String html="";
        AccessType access=null;
        int subType=idf.getSubType();        
        switch(subType) {
            //Case work item   
            case 4:                
                ItemInfo item=ItemInfoService.fetchLdsVolumeInfo(idf.getItemId());
                access=item.getAccess(); 
                if(access.equals(AccessType.OPEN)) {
                    if(json) {
                        map=getJsonVolumePdfLinks(item);
                        ObjectMapper mapper=new ObjectMapper();
                        html=mapper.writerWithDefaultPrettyPrinter().writeValueAsString(map);
                    }else {
                        map.put("links", getVolumeDownLoadLinks(item,idf));
                        html=getTemplate("volumes.tpl"); 
                        s=new StrSubstitutor(map);
                        html=s.replace(html);
                    }
                }
                break;
            //Case volume imageRange
            case 5:
            case 6:
                int bPage=idf.getBPageNum().intValue();
                int ePage=idf.getEPageNum().intValue();
                VolumeInfo vi = VolumeInfoService.getVolumeInfo(idf.getVolumeId()); 
                access=vi.getAccess();
                if(access.equals(AccessType.OPEN)) {
                    Iterator<String> idIterator = vi.getImageListIterator(bPage, ePage);
                    output = idf.getVolumeId()+":"+bPage+"-"+ePage+".pdf";
                    Object pdf_cached =ServerCache.getObjectFromCache(output);
                    if(pdf_cached==null) {
                        // Build pdf since the pdf file doesn't exist yet
                        PdfBuilder.buildPdf(idIterator,new IdentifierInfo(idf.getVolumeId()),output);                        
                    }
                    // Create template and serve html link
                    map.put("links", "/pdfdownload/file/"+output);
                    if(json) {
                        ObjectMapper mapper=new ObjectMapper();                    
                        html=mapper.writerWithDefaultPrettyPrinter().writeValueAsString(map);
                    }else {
                        html=getTemplate("downloadPdf.tpl");
                        map.put("pdf", output);
                        s=new StrSubstitutor(map);
                        html=s.replace(html);
                    }
                }
                break;
        }
        if(!access.equals(AccessType.OPEN)) {            
            ResponseEntity<String> response = new ResponseEntity<String>("Unauthorized Access", headers, HttpStatus.UNAUTHORIZED);
            return response;
        }
        if(json) {
            headers.setContentType(MediaType.parseMediaType("application/json"));
        }else {
            headers.setContentType(MediaType.parseMediaType("text/html"));
        }
        ResponseEntity<String> response = new ResponseEntity<String>(html, headers, HttpStatus.OK);
        return response;
          
    }
    
    @RequestMapping(value = "file/{pdf}",
            method = {RequestMethod.GET,RequestMethod.HEAD})
    public ResponseEntity<ByteArrayResource> downloadPdf(@PathVariable String pdf) throws Exception {
        
        byte[] array=(byte[])ServerCache.getObjectFromCache(pdf+".pdf");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/pdf"));
        headers.setContentDispositionFormData("attachment", pdf+".pdf");
        // Remove pdf reference in registry after download
        registry.removePdfService(pdf+".pdf");
        ResponseEntity<ByteArrayResource> response = new ResponseEntity<ByteArrayResource>(
                new ByteArrayResource(array), headers, HttpStatus.OK);        
        return response;
    }

    public static String getTemplate(String template) {
        InputStream stream = PdfController.class.getClassLoader().getResourceAsStream("templates/"+template);
        BufferedReader buffer = new BufferedReader(new InputStreamReader(stream));
        StringBuffer sb=new StringBuffer();
        try {
            String line=buffer.readLine();
            while(line!=null) {
                sb.append(line+System.lineSeparator());
                line=buffer.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();         
        }
        return sb.toString();
    }
    
    public String getVolumeDownLoadLinks(ItemInfo item,Identifier idf) throws BDRCAPIException {
        String links="";
        List<VolumeInfoSmall> vlist=item.getVolumes();
        for(VolumeInfoSmall vis:vlist) {
            VolumeInfo vi = VolumeInfoService.getVolumeInfo(vis.getPrefixedId());
            links=links+"<a href=\"/pdfdownload/v:"+vis.getPrefixedId()+"::1-"+vi.totalPages+"\">Vol."+vis.getVolumeNumber()+" ("+vi.totalPages+" pages) - "+vis.getPrefixedId()+"</a><br/>";
        }
        return links;
    }
    
    public HashMap<String,String> getJsonVolumePdfLinks(ItemInfo item) throws BDRCAPIException {
        HashMap<String,String> map=new HashMap<>();        
        List<VolumeInfoSmall> vlist=item.getVolumes();
        for(VolumeInfoSmall vis:vlist) {
            VolumeInfo vi = VolumeInfoService.getVolumeInfo(vis.getPrefixedId());
            map.put(vis.getPrefixedId(), "/pdfdownload/v:"+vis.getPrefixedId()+"::1-"+vi.totalPages);
            
        }
        return map;
    }
    
    
}