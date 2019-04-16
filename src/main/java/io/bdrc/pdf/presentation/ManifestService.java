package io.bdrc.pdf.presentation;

import static io.bdrc.pdf.presentation.AppConstants.BDR;
import static io.bdrc.pdf.presentation.AppConstants.BDR_len;
import static io.bdrc.pdf.presentation.AppConstants.GENERIC_APP_ERROR_CODE;
import static io.bdrc.pdf.presentation.AppConstants.IIIFPresPrefix;
import static io.bdrc.pdf.presentation.AppConstants.NO_ACCESS_ERROR_CODE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.digitalcollections.iiif.model.ImageContent;
import de.digitalcollections.iiif.model.PropertyValue;
import de.digitalcollections.iiif.model.enums.ViewingDirection;
import de.digitalcollections.iiif.model.image.ImageApiProfile;
import de.digitalcollections.iiif.model.image.ImageService;
import de.digitalcollections.iiif.model.sharedcanvas.Canvas;
import de.digitalcollections.iiif.model.sharedcanvas.Manifest;
import de.digitalcollections.iiif.model.sharedcanvas.Sequence;
import io.bdrc.pdf.presentation.exceptions.BDRCAPIException;
import io.bdrc.pdf.presentation.models.AccessType;
import io.bdrc.pdf.presentation.models.Identifier;
import io.bdrc.pdf.presentation.models.ImageInfo;
import io.bdrc.pdf.presentation.models.VolumeInfo;

public class ManifestService {

    private static final Logger logger = LoggerFactory.getLogger(ManifestService.class);

    public static final Map<String, Locale> locales = new HashMap<>();
    public static final PropertyValue attribution = new PropertyValue();
    static {
        attribution.addValue(getLocaleFor("en"), "Buddhist Digital Resource Center");
        attribution.addValue(getLocaleFor("bo"), "ནང་བསྟན་དཔེ་ཚོགས་ལྟེ་གནས།");
        attribution.addValue(getLocaleFor("zh"), "佛教数字资源中心(BDRC)");
    }

    public static Locale getLocaleFor(String lt) {
        return locales.computeIfAbsent(lt, x -> Locale.forLanguageTag(lt));
    }

    public static String getLabelForImage(final int imageIndex) {
        if (imageIndex < 2)
            return "tbrc-" + (imageIndex + 1);
        return "p. " + (imageIndex - 1);
    }

    public static String getImageServiceUrl(final String filename, final Identifier id) {
        return "http://iiif.bdrc.io/" + id.getVolumeId() + "::" + filename;
    }

    public static Sequence getSequenceFrom(final Identifier id, final List<ImageInfo> imageInfoList) throws BDRCAPIException {
        final Sequence mainSeq = new Sequence(IIIFPresPrefix + id.getId() + "/sequence/main");
        final int imageTotal = imageInfoList.size();
        // in identifiers, pages go from 1, not 0, we do a translation for Java list
        // indexes
        final int beginIndex = (id.getBPageNum() == null) ? 0 : id.getBPageNum() - 1;
        if (beginIndex > imageTotal) {
            throw new BDRCAPIException(404, GENERIC_APP_ERROR_CODE, "you asked a manifest for an image number that is greater than the total number of images");
        }
        final Integer ePageNum = id.getEPageNum();
        int endIndex = imageTotal - 1;
        if (ePageNum != null) {
            if (ePageNum > imageTotal - 1) {
                throw new BDRCAPIException(404, GENERIC_APP_ERROR_CODE, "you asked a manifest for an image number that is greater than the total number of images");
            }
            endIndex = ePageNum - 1;
        }
        for (int i = beginIndex; i <= endIndex; i++) {
            final ImageInfo imageInfo = imageInfoList.get(i);
            final String label = getLabelForImage(i);
            final String canvasUri = IIIFPresPrefix + id.getId() + "/canvas/" + (i + 1);
            final Canvas canvas = new Canvas(canvasUri, label);
            canvas.setWidth(imageInfo.width);
            canvas.setHeight(imageInfo.height);
            final String imageServiceUrl = getImageServiceUrl(imageInfo.filename, id);
            // canvas.addIIIFImage(imageServiceUrl, ImageApiProfile.LEVEL_ONE);
            ImageService imgServ = new ImageService(imageServiceUrl, ImageApiProfile.LEVEL_ZERO);
            ImageContent img = new ImageContent(imgServ);
            img.setWidth(imageInfo.width);
            img.setHeight(imageInfo.height);
            canvas.addImage(img);
            mainSeq.addCanvas(canvas);
            if (i == beginIndex) {
                try {
                    mainSeq.setStartCanvas(new URI(canvasUri));
                } catch (URISyntaxException e) { // completely stupid but necessary
                    throw new BDRCAPIException(500, GENERIC_APP_ERROR_CODE, e);
                }
            }
        }
        return mainSeq;
    }

    public static Manifest getManifestForIdentifier(final Identifier id, final VolumeInfo vi) throws BDRCAPIException {
        if (id.getType() != Identifier.MANIFEST_ID || id.getSubType() != Identifier.MANIFEST_ID_VOLUMEID) {
            throw new BDRCAPIException(404, GENERIC_APP_ERROR_CODE, "you cannot access this type of manifest yet");
        }
        if (vi.access != AccessType.OPEN) {
            throw new BDRCAPIException(403, NO_ACCESS_ERROR_CODE, "you cannot access this volume");
        }
        if (!vi.workId.startsWith(BDR)) {
            throw new BDRCAPIException(403, NO_ACCESS_ERROR_CODE, "you can only access BDRC volumes through this API");
        }
        String workLocalId = vi.workId.substring(BDR_len);
        logger.info("building manifest for ID {}", id.getId());
        List<ImageInfo> imageInfoList = ImageInfoListService.getImageInfoList(workLocalId, vi.imageGroup);
        final Manifest manifest = new Manifest(IIIFPresPrefix + id.getId() + "/manifest", "BUDA Manifest");
        manifest.setAttribution(attribution);
        manifest.addLicense("https://creativecommons.org/publicdomain/mark/1.0/");
        manifest.addLogo("https://eroux.fr/logo.png");
        manifest.addLabel(id.getVolumeId());
        final Sequence mainSeq = getSequenceFrom(id, imageInfoList);
        mainSeq.setViewingDirection(ViewingDirection.TOP_TO_BOTTOM);
        manifest.addSequence(mainSeq);
        return manifest;
    }

}