package de.digitalcollections.iiif.myhymir.backend.impl.repository;

import de.digitalcollections.core.backend.api.resource.ResourceRepository;
import de.digitalcollections.core.model.api.MimeType;
import de.digitalcollections.core.model.api.resource.Resource;
import de.digitalcollections.core.model.api.resource.enums.ResourcePersistenceType;
import de.digitalcollections.core.model.api.resource.exceptions.ResourceIOException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

@Primary
@Repository
public class S3ResourceRepositoryImpl implements ResourceRepository<Resource> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(S3ResourceRepositoryImpl.class);
    
    static Properties props = new Properties();
    static Properties local=new Properties();
    
    static final String S3_ENDPOINT;
    static final String S3_REGION;
    static final String S3_ACCESS_KEY_ID;
    static final String S3_SECRET_KEY;
    static final String S3_MAX_CONNECTIONS;
    
    static {
        loadS3Props();
        S3_ENDPOINT=local.getProperty("S3_ENDPOINT");
        S3_REGION=local.getProperty("S3_REGION");
        S3_ACCESS_KEY_ID=local.getProperty("S3_ACCESS_KEY_ID");
        S3_SECRET_KEY=local.getProperty("S3_SECRET_KEY");
        S3_MAX_CONNECTIONS=local.getProperty("S3_MAX_CONNECTIONS");
    }

    @Override
    public Resource create(String string, ResourcePersistenceType rpt, MimeType mt) throws ResourceIOException {
      throw new UnsupportedOperationException("Not supported yet."); 
      //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void delete(Resource r) throws ResourceIOException {
      throw new UnsupportedOperationException("Not supported yet."); 
      //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Resource find(String string, ResourcePersistenceType rpt, MimeType mt) throws ResourceIOException {
      throw new UnsupportedOperationException("Not supported yet."); 
      //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public byte[] getBytes(Resource r) throws ResourceIOException {
      throw new UnsupportedOperationException("Not supported yet."); 
      //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public InputStream getInputStream(URI uri) throws ResourceIOException {
      throw new UnsupportedOperationException("Not supported yet."); 
      //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public InputStream getInputStream(Resource r) throws ResourceIOException {
      throw new UnsupportedOperationException("Not supported yet."); 
      //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Reader getReader(Resource r) throws ResourceIOException {
      throw new UnsupportedOperationException("Not supported yet."); 
      //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void write(Resource rsrc, String string) throws ResourceIOException {
      throw new UnsupportedOperationException("Not supported yet."); 
      //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void write(Resource rsrc, InputStream in) throws ResourceIOException {
      throw new UnsupportedOperationException("Not supported yet."); 
      //To change body of generated methods, choose Tools | Templates.
    }
  
    /**
     * Loads properties for S3
     * local.properties contains both "real" s3 properties files path (external to the project)
     * and properties keys.
     * s3.properties contains the actual s3 properties under keys defined in local props
     */
    private static void loadS3Props() {        
        try {
          InputStream in = S3ResourceRepositoryImpl.class.getClassLoader().getResourceAsStream("s3repo.properties");          
          local.load(in);          
          in.close();          
          FileReader fr= new FileReader(new File(local.getProperty("s3propsPath")));          
          props.load(fr);
          fr.close();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }      
    }
    
    /**
     * @return a client to interact with S3 bucket
     */
    private static synchronized AmazonS3 getClientInstance() {
        
        BasicAWSCredentials bac=new BasicAWSCredentials(props.getProperty(S3_ACCESS_KEY_ID),props.getProperty(S3_SECRET_KEY));
        ClientConfiguration ccfg = new ClientConfiguration();
        ccfg.setMaxConnections(Integer.parseInt(props.getProperty(S3_MAX_CONNECTIONS)));
        
        return AmazonS3ClientBuilder.standard()
        .withEndpointConfiguration(new EndpointConfiguration(props.getProperty(S3_ENDPOINT),props.getProperty(S3_REGION)))
        .withCredentials(new AWSStaticCredentialsProvider(bac))
        .withClientConfiguration(ccfg)        
        .build();
    }

}