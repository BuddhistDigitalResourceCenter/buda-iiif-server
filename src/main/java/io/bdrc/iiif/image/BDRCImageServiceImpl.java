package io.bdrc.iiif.image;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.apache.commons.imaging.ColorTools;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.Imaging;
import org.apache.jena.atlas.logging.Log;
import org.imgscalr.Scalr;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.google.common.collect.Streams;
import com.luciad.imageio.webp.WebPWriteParam;
import com.sun.media.jai.codec.ImageCodec;
import com.sun.media.jai.codec.ImageEncodeParam;
import com.sun.media.jai.codec.ImageEncoder;
import com.sun.media.jai.codec.PNGEncodeParam;

import de.digitalcollections.turbojpeg.imageio.TurboJpegImageReadParam;
import de.digitalcollections.turbojpeg.imageio.TurboJpegImageReader;
import io.bdrc.iiif.core.Application;
import io.bdrc.iiif.exceptions.IIIFException;
import io.bdrc.iiif.exceptions.InvalidParametersException;
import io.bdrc.iiif.exceptions.UnsupportedFormatException;
import io.bdrc.iiif.model.ImageApiProfile;
import io.bdrc.iiif.model.ImageApiProfile.Format;
import io.bdrc.iiif.model.ImageApiProfile.Quality;
import io.bdrc.iiif.model.ImageApiSelector;
import io.bdrc.iiif.model.ImageService;
import io.bdrc.iiif.model.RegionRequest;
import io.bdrc.iiif.model.Size;
import io.bdrc.iiif.model.SizeRequest;
import io.bdrc.iiif.model.TileInfo;
import io.bdrc.iiif.resolver.IdentifierInfo;
import io.bdrc.iiif.resolver.ImageS3Service;

@Service
@Primary
public class BDRCImageServiceImpl {

    @Value("${custom.iiif.image.maxWidth:65500}")
    private static int maxWidth;

    @Value("${custom.iiif.image.maxHeight:65500}")
    private static int maxHeight;

    /** Update ImageService based on the image **/
    private static void enrichInfo(ImageReader reader, ImageService info) throws IOException {

        ImageApiProfile profile = new ImageApiProfile();
        profile.addFeature(ImageApiProfile.Feature.BASE_URI_REDIRECT, ImageApiProfile.Feature.CORS, ImageApiProfile.Feature.JSONLD_MEDIA_TYPE,
                ImageApiProfile.Feature.PROFILE_LINK_HEADER, ImageApiProfile.Feature.CANONICAL_LINK_HEADER, ImageApiProfile.Feature.REGION_BY_PCT,
                ImageApiProfile.Feature.REGION_BY_PX, ImageApiProfile.Feature.REGION_SQUARE, ImageApiProfile.Feature.ROTATION_BY_90S,
                ImageApiProfile.Feature.MIRRORING, ImageApiProfile.Feature.SIZE_BY_CONFINED_WH, ImageApiProfile.Feature.SIZE_BY_DISTORTED_WH,
                ImageApiProfile.Feature.SIZE_BY_H, ImageApiProfile.Feature.SIZE_BY_PCT, ImageApiProfile.Feature.SIZE_BY_W,
                ImageApiProfile.Feature.SIZE_BY_WH);

        // Indicate to the client if we cannot deliver full resolution versions of the
        // image
        if (reader.getHeight(0) > maxHeight || reader.getWidth(0) > maxWidth) {
            profile.setMaxWidth(maxWidth);
            if (maxHeight != maxWidth) {
                profile.setMaxHeight(maxHeight);
            }
        }
        info.addProfile(ImageApiProfile.LEVEL_ONE, profile);

        info.setWidth(reader.getWidth(0));
        info.setHeight(reader.getHeight(0));

        // Check if multiple resolutions are supported
        int numImages = reader.getNumImages(true);
        if (numImages > 1) {
            for (int i = 0; i < numImages; i++) {
                int width = reader.getWidth(i);
                int height = reader.getHeight(i);
                if (width > 1 && height > 1 && width <= maxWidth && height <= maxHeight) {
                    info.addSize(new Size(reader.getWidth(i), reader.getHeight(i)));
                }
            }
        }
        // Check if tiling is supported
        if (reader.isImageTiled(0)) {
            int width = reader.getTileWidth(0);
            TileInfo tileInfo = new TileInfo(width);
            for (int i = 0; i < numImages; i++) {
                int scaledWidth = reader.getTileWidth(i);
                tileInfo.addScaleFactor(width / scaledWidth);
            }
            info.addTile(tileInfo);
        } else if (reader instanceof TurboJpegImageReader) {
            // Cropping aligned to MCUs is faster, and MCUs are either 4, 8 or 16 pixels, so
            // if we stick to multiples
            // of 16 for width/height, we are safe.
            if (reader.getWidth(0) >= 512 && reader.getHeight(0) >= 512) {
                TileInfo ti = new TileInfo(512);
                // Scale factors for JPEGs are not always integral, so we hardcode them
                ti.addScaleFactor(1, 2, 4, 8, 16);
                info.addTile(ti);
            }
            if (reader.getWidth(0) >= 1024 && reader.getHeight(0) >= 1024) {
                TileInfo ti = new TileInfo(1024);
                ti.addScaleFactor(1, 2, 4, 8, 16);
                info.addTile(ti);
            }
        }
        // BDRC default behavior : this way the Universal Viewer gets the image in one
        // request only
        // which is BDRC basic use case
        TileInfo tile = new TileInfo(reader.getWidth(0));
        tile.setHeight(reader.getHeight(0));
        tile.addScaleFactor(1, 2, 4, 8);
        info.addTile(tile);
    }

    /**
     * Try to obtain a {@link ImageReader} for a given identifier
     * 
     * @throws IIIFException
     * @throws ResourceNotFoundException
     * @throws ImageReadException
     **/
    private static ImageReader_ICC getReader(String identifier) throws UnsupportedFormatException, IOException, IIIFException {
        long deb = System.currentTimeMillis();
        ICC_Profile icc = null;
        final String s3key;
        final ImageS3Service service;
        if (identifier.startsWith("static::")) {
            s3key = identifier.substring(8);
            service = ImageS3Service.InstanceStatic;
        } else {
            IdentifierInfo idf = new IdentifierInfo(identifier);
            s3key = ImageS3Service.getKey(idf);
            service = ImageS3Service.InstanceArchive;
        }
        byte[] bytes = null;
        try {
            bytes = service.getAsync(s3key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IIIFException(404, 5000, e);
        }

        ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes));
        ImageReader reader = Streams.stream(ImageIO.getImageReaders(iis)).findFirst().orElseThrow(UnsupportedFormatException::new);
        reader.setInput(iis);
        if (reader.getClass().equals(TurboJpegImageReader.class)) {
            try {
                icc = Imaging.getICCProfile(bytes);
            } catch (ImageReadException e) {
                e.printStackTrace();
            }
        }
        Application.logPerf("S3 object IIIS READER >> {}", reader);
        Application.logPerf("Image service return reader at {} ms {}", System.currentTimeMillis() - deb, identifier);
        return new ImageReader_ICC(reader, icc);
    }

    public static ImageReader_ICC readImageInfo(String identifier, ImageService info, ImageReader_ICC imgReader)
            throws IOException, IIIFException, UnsupportedFormatException {
        try {
            imgReader = getReader(identifier);
            enrichInfo(imgReader.getReader(), info);
        } catch (IIIFException e) {
            Log.error("Could not get Image Info", e.getMessage());
            throw new IIIFException(e);
        }
        return imgReader;
    }

    /**
     * Determine parameters for image reading based on the IIIF selector and a given
     * scaling factor
     **/
    private static ImageReadParam getReadParam(ImageReader reader, ImageApiSelector selector, double decodeScaleFactor)
            throws IOException, InvalidParametersException {
        ImageReadParam readParam = reader.getDefaultReadParam();
        Application.logPerf("Entering ReadParam with ImageReadParam {}", readParam);
        Dimension nativeDimensions = new Dimension(reader.getWidth(0), reader.getHeight(0));
        Rectangle targetRegion;
        try {
            targetRegion = selector.getRegion().resolve(nativeDimensions);
        } catch (IIIFException e) {
            Log.error("Could not get image ReadParam", e.getMessage());
            throw new InvalidParametersException(e);
        }
        // IIIF regions are always relative to the native size, while ImageIO regions
        // are always relative to the decoded
        // image size, hence the conversion
        Rectangle decodeRegion = new Rectangle((int) Math.ceil(targetRegion.getX() * decodeScaleFactor),
                (int) Math.ceil(targetRegion.getY() * decodeScaleFactor), (int) Math.ceil(targetRegion.getWidth() * decodeScaleFactor),
                (int) Math.ceil(targetRegion.getHeight() * decodeScaleFactor));
        readParam.setSourceRegion(decodeRegion);
        // TurboJpegImageReader can rotate during decoding
        if (selector.getRotation().getRotation() != 0 && reader instanceof TurboJpegImageReader) {
            ((TurboJpegImageReadParam) readParam).setRotationDegree((int) selector.getRotation().getRotation());
        }
        return readParam;
    }

    private static DecodedImage readImage(String identifier, ImageApiSelector selector, ImageApiProfile profile, ImageReader_ICC imgReader)
            throws IOException, UnsupportedFormatException, InvalidParametersException, ImageReadException {
        long deb = System.currentTimeMillis();
        Application.logPerf("Entering readImage for creating DecodedImage");
        if ((selector.getRotation().getRotation() % 90) != 0) {
            Log.error("Rotation is not a multiple of 90 degrees for selector " + selector.toString(), "");
            throw new UnsupportedOperationException("Can only rotate by multiples of 90 degrees.");
        }
        Dimension nativeDimensions = new Dimension(imgReader.getReader().getWidth(0), imgReader.getReader().getHeight(0));
        Rectangle targetRegion;
        try {
            targetRegion = selector.getRegion().resolve(nativeDimensions);
        } catch (IIIFException e) {
            Log.error("Could not resolve selector region :" + selector.getRegion(), e.getMessage());
            throw new InvalidParametersException(e);
        }
        Dimension croppedDimensions = new Dimension(targetRegion.width, targetRegion.height);
        Dimension targetSize;
        try {
            targetSize = selector.getSize().resolve(croppedDimensions, profile);
        } catch (IIIFException e) {
            Log.error("Could not resolve selector size :" + selector.getSize(), e.getMessage());
            throw new InvalidParametersException(e);
        }

        // Determine the closest resolution to the target that can be decoded directly
        double targetScaleFactor = targetSize.width / targetRegion.getWidth();
        double decodeScaleFactor = 1.0;
        int imageIndex = 0;
        for (int idx = 0; idx < imgReader.getReader().getNumImages(true); idx++) {
            double factor = (double) imgReader.getReader().getWidth(idx) / nativeDimensions.width;
            if (factor < targetScaleFactor) {
                continue;
            }
            if (Math.abs(targetScaleFactor - factor) < Math.abs(targetScaleFactor - decodeScaleFactor)) {
                decodeScaleFactor = factor;
                imageIndex = idx;
            }
        }
        ImageReadParam readParam = getReadParam(imgReader.getReader(), selector, decodeScaleFactor);
        int rotation = (int) selector.getRotation().getRotation();
        if (readParam instanceof TurboJpegImageReadParam && ((TurboJpegImageReadParam) readParam).getRotationDegree() != 0) {
            if (rotation == 90 || rotation == 270) {
                int w = targetSize.width;
                targetSize.width = targetSize.height;
                targetSize.height = w;
            }
            rotation = 0;
        }
        Application.logPerf("Done readingImage computing DecodedImage after {} ms", System.currentTimeMillis() - deb);
        if (imgReader.getIcc() == null) {
            return new DecodedImage(imgReader.getReader().read(imageIndex, readParam), targetSize, rotation);
        } else {
            BufferedImage bi = imgReader.getReader().read(imageIndex);
            bi = new ColorTools().relabelColorSpace(bi, imgReader.getIcc());
            return new DecodedImage(bi, targetSize, rotation);
        }
    }

    /** Apply transformations to an decoded image **/
    private static BufferedImage transformImage(Format format, BufferedImage inputImage, Dimension targetSize, int rotation, boolean mirror,
            ImageApiProfile.Quality quality) {
        BufferedImage img = inputImage;
        int inType = img.getType();
        boolean needsAdditionalScaling = !new Dimension(img.getWidth(), img.getHeight()).equals(targetSize);
        if (needsAdditionalScaling) {
            img = Scalr.resize(img, Scalr.Method.BALANCED, Scalr.Mode.FIT_EXACT, targetSize.width, targetSize.height);
        }

        if (rotation != 0) {
            Scalr.Rotation rot = null;
            switch (rotation) {
            case 90:
                rot = Scalr.Rotation.CW_90;
                break;
            case 180:
                rot = Scalr.Rotation.CW_180;
                break;
            case 270:
                rot = Scalr.Rotation.CW_270;
                break;
            }
            img = Scalr.rotate(img, rot);
        }
        if (mirror) {
            img = Scalr.rotate(img, Scalr.Rotation.FLIP_HORZ);
        }
        // Quality
        int outType;
        switch (quality) {
        case GRAY:
            outType = BufferedImage.TYPE_BYTE_GRAY;
            break;
        case BITONAL:
            outType = BufferedImage.TYPE_BYTE_BINARY;
            break;
        case COLOR:
            outType = BufferedImage.TYPE_3BYTE_BGR;
            break;
        case DEFAULT:
            outType = BufferedImage.TYPE_3BYTE_BGR;
            Application.logPerf("Transform image DEFAULT quality >>" + quality + " OutType: " + outType + " format " + format);
            break;
        default:
            outType = inType;
        }
        if (outType != img.getType()) {
            BufferedImage newImg = new BufferedImage(img.getWidth(), img.getHeight(), outType);
            Graphics2D g2d = newImg.createGraphics();
            g2d.drawImage(img, 0, 0, null);
            img = newImg;
            g2d.dispose();
        }
        return img;
    }

    public static boolean formatDiffer(final String identifier, final ImageApiSelector selector) {
        final Format outputF = selector.getFormat();
        final String lastFour = identifier.substring(identifier.length() - 4).toLowerCase();
        if (outputF == Format.JPG && (lastFour.equals(".jpg") || lastFour.equals("jpeg")))
            return false;
        if (outputF == Format.PNG && lastFour.equals(".png"))
            return false;
        if (outputF == Format.TIF && (lastFour.equals(".tif") || lastFour.equals("tiff")))
            return false;
        return true;
    }

    private static final RegionRequest fullRegionRequest = new RegionRequest();
    private static final SizeRequest fullSizeRequest = new SizeRequest();
    private static final SizeRequest maxSizeRequest = new SizeRequest(true);

    // here we return a boolean telling us if the requested image is different from
    // the original image
    // on S3
    public static boolean requestDiffersFromOriginal(final String identifier, final ImageApiSelector selector) {
        if (formatDiffer(identifier, selector))
            return true;
        if (selector.getQuality() != Quality.DEFAULT) // TODO: this could be improved but we can keep that for later
            return true;
        if (selector.getRotation().getRotation() != 0.)
            return true;
        if (!selector.getRegion().equals(fullRegionRequest)) // TODO: same here, could be improved by reading the
                                                             // dimensions of the image
            return true;
        if (!selector.getSize().equals(fullSizeRequest) && !selector.getSize().equals(maxSizeRequest))
            return true;
        return false;
    }

    public static void processImage(String identifier, ImageApiSelector selector, ImageApiProfile profile, OutputStream os, ImageReader_ICC imgReader,
            String uri)
            throws InvalidParametersException, UnsupportedOperationException, UnsupportedFormatException, IOException, ImageReadException {
        long deb = System.currentTimeMillis();
        try {
            Application.logPerf("Entering Processimage.... with reader {} ", imgReader);
            DecodedImage img = readImage(identifier, selector, profile, imgReader);
            Application.logPerf("Done readingImage DecodedImage created");
            BufferedImage outImg = transformImage(selector.getFormat(), img.img, img.targetSize, img.rotation, selector.getRotation().isMirror(),
                    selector.getQuality());
            ImageWriter writer = null;
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType(selector.getFormat().getMimeType().getTypeName());
            while (writers.hasNext()) {
                ImageWriter w = writers.next();
                Application.logPerf("FOUND REGISTERED WRITER in list {}", w);
                writer = w;
            }
            if (writer == null) {
                throw new UnsupportedFormatException(selector.getFormat().getMimeType().getTypeName());
            }
            switch (selector.getFormat()) {

            case PNG:
                Application.logPerf("USING JAI PNG for {} ", identifier);
                ImageEncodeParam param = PNGEncodeParam.getDefaultEncodeParam(outImg);
                String format = "PNG";
                ImageEncoder encoder = ImageCodec.createImageEncoder(format, os, param);
                encoder.encode(outImg);
                os.flush();
                break;

            case JPG:
                Iterator<ImageWriter> it1 = ImageIO.getImageWritersByMIMEType("image/jpeg");
                ImageWriter wtr = null;
                while (it1.hasNext()) {
                    ImageWriter w = it1.next();
                    if (w.getClass().getName().equals("com.twelvemonkeys.imageio.plugins.jpeg.JPEGImageWriter")) {
                        wtr = w;
                    }
                    Application.logPerf("Should use WRITERS for JPEG >> {} with quality {}", wtr, outImg.getType());
                    Application.logPerf("WRITERS---> in list {}", w.getClass().getName());
                }
                if (outImg.getType() != BufferedImage.TYPE_BYTE_GRAY) {
                    wtr = ImageIO.getImageWritersByMIMEType("image/jpeg").next();
                }
                Application.logPerf("USING JPEG WRITER {} for {}", wtr, identifier);
                Application.logPerf("use jpg writer vendor {}, version {}", wtr.getOriginatingProvider().getVendorName(),
                        wtr.getOriginatingProvider().getVersion());
                // This setting, using 0.75f as compression quality produces the same
                // as the default setting, with no writeParam --> writer.write(outImg)

                ImageWriteParam jpgWriteParam = wtr.getDefaultWriteParam();
                jpgWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                jpgWriteParam.setCompressionQuality(0.75f);

                ImageOutputStream is = ImageIO.createImageOutputStream(os);
                wtr.setOutput(is);
                wtr.write(null, new IIOImage(outImg, null, null), jpgWriteParam);
                wtr.dispose();
                is.flush();
                break;

            case WEBP:
                writer = ImageIO.getImageWritersByMIMEType("image/webp").next();
                Application.logPerf("Using WRITER for WEBP >>" + writer);
                ImageWriteParam pr = writer.getDefaultWriteParam();
                WebPWriteParam writeParam = (WebPWriteParam) pr;
                writeParam.setCompressionMode(WebPWriteParam.MODE_DEFAULT);
                ImageOutputStream iss = ImageIO.createImageOutputStream(os);
                writer.setOutput(iss);
                writer.write(null, new IIOImage(outImg, null, null), writeParam);
                writer.dispose();
                iss.flush();
                break;

            default:
                Application.logPerf("USING NON NULL WRITER {}", writer);
                ImageOutputStream ios = ImageIO.createImageOutputStream(os);
                writer.setOutput(ios);
                writer.write(outImg);
                writer.dispose();
                ios.flush();
            }
            Application.logPerf("Done with Processimage.... in {} ms", System.currentTimeMillis() - deb);
        } catch (InvalidParametersException | UnsupportedOperationException | UnsupportedFormatException | IOException e) {
            Log.error("Error while processing image", e.getMessage());
            throw e;
        }
    }

    public static Instant getImageModificationDate(String identifier) throws IIIFException {
        try {
            return Instant.ofEpochMilli(-1);
        } catch (Exception e) {
            Log.error("Could not get Image modification date from resource for identifier {}", identifier);
            throw new IIIFException("Could not get Image modification date from resource for identifier " + identifier);
        }
    }
}