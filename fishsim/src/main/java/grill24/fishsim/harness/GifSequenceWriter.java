package grill24.fishsim.harness;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;

/**
 * Minimal looping animated-GIF writer on top of the JDK's built-in GIF {@link ImageWriter} — no
 * external dependency (docs/fish-sim-engine-handoff.md open decision: simplest thing that works).
 */
public final class GifSequenceWriter implements Closeable {

    private final ImageWriter writer;
    private final IIOMetadata metadata;

    /**
     * @param output      stream the GIF is written to (caller closes)
     * @param imageType   {@code BufferedImage.getType()} of every frame
     * @param delayMillis per-frame delay
     */
    public GifSequenceWriter(ImageOutputStream output, int imageType, int delayMillis) throws IOException {
        writer = ImageIO.getImageWritersBySuffix("gif").next();
        ImageWriteParam params = writer.getDefaultWriteParam();
        metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromBufferedImageType(imageType), params);

        String formatName = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(formatName);

        IIOMetadataNode gce = childNode(root, "GraphicControlExtension");
        gce.setAttribute("disposalMethod", "none");
        gce.setAttribute("userInputFlag", "FALSE");
        gce.setAttribute("transparentColorFlag", "FALSE");
        gce.setAttribute("delayTime", Integer.toString(Math.max(delayMillis / 10, 1)));
        gce.setAttribute("transparentColorIndex", "0");

        // Netscape application extension = loop forever.
        IIOMetadataNode appExtensions = childNode(root, "ApplicationExtensions");
        IIOMetadataNode app = new IIOMetadataNode("ApplicationExtension");
        app.setAttribute("applicationID", "NETSCAPE");
        app.setAttribute("authenticationCode", "2.0");
        app.setUserObject(new byte[]{0x1, 0x0, 0x0});
        appExtensions.appendChild(app);

        metadata.setFromTree(formatName, root);
        writer.setOutput(output);
        writer.prepareWriteSequence(null);
    }

    private static IIOMetadataNode childNode(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equalsIgnoreCase(name)) {
                return (IIOMetadataNode) root.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }

    public void writeFrame(BufferedImage frame) throws IOException {
        writer.writeToSequence(new IIOImage(frame, null, metadata), writer.getDefaultWriteParam());
    }

    @Override
    public void close() throws IOException {
        writer.endWriteSequence();
        writer.dispose();
    }
}
