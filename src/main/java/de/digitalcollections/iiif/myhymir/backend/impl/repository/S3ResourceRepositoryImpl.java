package de.digitalcollections.iiif.myhymir.backend.impl.repository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.util.Properties;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.w3c.dom.Document;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;

import de.digitalcollections.core.backend.api.resource.ResourceRepository;
import de.digitalcollections.core.backend.impl.file.repository.resource.util.S3ResourcePersistenceTypeHandler;
import de.digitalcollections.core.model.api.MimeType;
import de.digitalcollections.core.model.api.resource.Resource;
import de.digitalcollections.core.model.api.resource.enums.ResourcePersistenceType;
import de.digitalcollections.core.model.api.resource.exceptions.ResourceIOException;
import de.digitalcollections.core.model.impl.resource.S3Resource;
import de.digitalcollections.iiif.hymir.model.exception.ResourceNotFoundException;
import de.digitalcollections.iiif.myhymir.Application;
import de.digitalcollections.iiif.myhymir.ServerCache;
import io.bdrc.pdf.presentation.exceptions.BDRCAPIException;

/**
 * A ResourceRepository implementation to use with Amazon S3 services
 *
 * @author @marcagate - Buddhist Digital Resource Center
 *
 */
@Primary
@Repository
public class S3ResourceRepositoryImpl implements ResourceRepository<Resource> {

	private static final Logger log = LoggerFactory.getLogger(S3ResourceRepositoryImpl.class);
	public static final String IIIF_IMG = "IIIF_IMG";

	@Autowired
	S3ResourcePersistenceTypeHandler spt;

	private static final ClientConfiguration config = new ClientConfiguration().withConnectionTimeout(300000)
			.withMaxConnections(50).withMaxErrorRetry(100).withSocketTimeout(300000);
	private static String S3_BUCKET;
	private static AmazonS3ClientBuilder clientBuilder;

	public static void initWithProps(Properties p) {
		S3_BUCKET = p.getProperty("s3bucket");
		clientBuilder = AmazonS3ClientBuilder.standard().withRegion(p.getProperty("awsRegion"))
				.withClientConfiguration(config);
	}

	@Override
	public S3Resource create(String key, ResourcePersistenceType resourcePersistenceType, MimeType mimeType)
			throws ResourceIOException, ResourceNotFoundException {
		S3Resource resource = new S3Resource();
		resource.setId(key);
		if (mimeType != null) {
			if (mimeType.getExtensions() != null && !mimeType.getExtensions().isEmpty()) {
				resource.setFilenameExtension(mimeType.getExtensions().get(0));
			}
			resource.setMimeType(mimeType);
		}
		if (ResourcePersistenceType.REFERENCED.equals(resourcePersistenceType)) {
			resource.setReadonly(true);
		}
		if (ResourcePersistenceType.MANAGED.equals(resourcePersistenceType)) {
			resource.setUuid(UUID.fromString(key));
		}
		resource.setIdentifier(spt.getIdentifier(key));
		return resource;
	}

	@Override
	public S3Resource find(String key, ResourcePersistenceType resourcePersistenceType, MimeType mimeType)
			throws ResourceIOException, ResourceNotFoundException {
		S3Resource resource = create(key, resourcePersistenceType, mimeType);
		return resource;
	}

	/**
	 * @return a client to interact with S3 bucket
	 */
	public static synchronized AmazonS3 getClientInstance() {
		return clientBuilder.build();
	}

	public InputStream getInputStream(S3Resource r) throws ResourceNotFoundException, IOException {
		Application.perf.debug("getting S3 client " + r.getIdentifier());
		String identifier = r.getId();
		S3Object obj = null;
		final AmazonS3 s3 = S3ResourceRepositoryImpl.getClientInstance();
		try {
			final GetObjectRequest request = new GetObjectRequest(S3_BUCKET, r.getIdentifier());
			obj = s3.getObject(request);
			Application.perf.debug("S3 object size is " + obj.getObjectMetadata().getContentLength());
		} catch (AmazonS3Exception e) {
			log.error(">>>>>>>> S3 client failed for identifier {} >> {}", identifier, e.getStatusCode());
			throw new ResourceNotFoundException();
		}
		byte[] imgbytes = IOUtils.toByteArray(obj.getObjectContent());
		try {
			ServerCache.addToCache(IIIF_IMG, identifier, imgbytes);
		} catch (BDRCAPIException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// final InputStream stream = obj.getObjectContent();
		InputStream stream = new ByteArrayInputStream((byte[]) ServerCache.getObjectFromCache(IIIF_IMG, identifier));
		Application.perf.debug("S3 stream {} returned for {}", stream, identifier);
		return stream;
	}

	@Override
	public InputStream getInputStream(Resource r) throws ResourceNotFoundException {
		try {
			return getInputStream((S3Resource) r);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public byte[] getBytes(Resource r) throws ResourceIOException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public InputStream getInputStream(URI uri) throws ResourceIOException, ResourceNotFoundException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void delete(Resource r) throws ResourceIOException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public Reader getReader(Resource r) throws ResourceIOException, ResourceNotFoundException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void write(Resource rsrc, String string) throws ResourceIOException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void write(Resource rsrc, InputStream in) throws ResourceIOException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void assertDocument(Resource arg0) throws ResourceIOException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public Document getDocument(Resource arg0) throws ResourceIOException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

}