package io.bdrc.iiif;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.event.IIOReadWarningListener;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.w3c.dom.NodeList;

import de.digitalcollections.iiif.hymir.model.exception.UnsupportedFormatException;

public class ImageTest {

    public ImageTest() {
        // TODO Auto-generated constructor stub
    }

    public static class MyIIOReadWarningListener implements IIOReadWarningListener {

        @Override
        public void warningOccurred(ImageReader arg0, String arg1) {
            System.out.println(arg1);
        }

    }

    public static void readAndWriteTwelveMonkeys(String filename) throws IOException, UnsupportedFormatException {
        InputStream is = ImageTest.class.getClassLoader().getResourceAsStream(filename);
        ImageInputStream iis = ImageIO.createImageInputStream(is);
        Iterator<ImageReader> itr = ImageIO.getImageReaders(iis);
        ImageReader r = itr.next();
        r = itr.next();
        System.out.println("using reader: " + r.toString());

        r.setInput(iis);

        IIOMetadata meta = r.getImageMetadata(0);
        IIOMetadata meta_stream = r.getStreamMetadata();
        printIcc(meta);

        ImageTypeSpecifier its = r.getRawImageType(0);
        ImageReadParam p = r.getDefaultReadParam();
        p.setDestinationType(its);

        BufferedImage bi = r.read(0, p);

        // original pixel 100,100 is (127, 109, 89), while when the image
        // is transformed into sRGB, it is (135,109,87), which is the case here
        int pixel100 = bi.getRGB(100, 100);
        System.out.println("(" + ((pixel100 & 0xff0000) >> 16) + "," + ((pixel100 & 0xff00) >> 8) + "," + (pixel100 & 0xff) + ")");
        // pixel100 = ((Raster) bi.getRaster()).getRGB(100, 100, pixel100);

        // color space is RGB (not sRGB), not sure if it's relevant
        System.out.println("is ColorSpace of reader ImageType RGB? " + (its.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_RGB));
        System.out.println("is ColorSpace of bufferedImage RGB? " + (bi.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_RGB));

        // nor do I when I try to get a writer for the output of the read:
        Iterator<ImageWriter> itw2 = ImageIO.getImageWriters(new ImageTypeSpecifier(bi), "jpeg");
        ImageWriter iw2 = itw2.next();
        if (iw2 == null) {
            System.out.println("no writer for the image type of the buffered image");
        } else {
            System.out.println("using writer: " + iw2.toString());
            ImageWriteParam wp = iw2.getDefaultWriteParam();
            // wp.setDestinationType(its);
            ImageOutputStream out = ImageIO.createImageOutputStream(new File("test-regularjpg.jpg"));
            iw2.setOutput(out);
            iw2.write(meta_stream, new IIOImage(bi, null, meta), wp);
        }

        is.close();
        iis.close();
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static void printIcc(IIOMetadata meta) {
        if (meta == null)
            return;
        String[] names = meta.getMetadataFormatNames();
        // Print image metadata
        System.out.println("image icc:");
        for (String s : names) {
            IIOMetadataNode inode = (IIOMetadataNode) meta.getAsTree(s);
            NodeList app2iccl = inode.getElementsByTagName("app2ICC");
            if (app2iccl.getLength() > 0) {
                IIOMetadataNode app2icc = (IIOMetadataNode) app2iccl.item(0);
                ICC_Profile icc = (ICC_Profile) app2icc.getUserObject();
                byte[] iccData = icc.getData();
                System.out.println("   length: " + iccData.length + " bytes (Adobe RGB is 560 bytes and starts with 0000 0230)");
                System.out.println("   bytes:  " + bytesToHex(iccData).substring(0, 8) + "...");
            }
        }
    }

    public static void main(String[] args) throws IOException, UnsupportedFormatException {
        readAndWriteTwelveMonkeys("ORIGINAL_S3.jpg");
    }

}