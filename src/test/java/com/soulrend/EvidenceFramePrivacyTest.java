package com.soulrend;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class EvidenceFramePrivacyTest {
    @Test public void onlyScreenshotCopyIsRedactedAtScaledCoordinates() {
        var frame = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        var g = frame.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, 100, 100); g.dispose();
        var copy = EvidenceFramePrivacy.redact(frame, List.of(new Rectangle(5, 10, 10, 10)), 50, 50);
        assertNotNull(copy);
        assertEquals(Color.WHITE.getRGB(), frame.getRGB(12, 22));
        assertEquals(Color.BLACK.getRGB(), copy.getRGB(12, 22));
        assertEquals(Color.WHITE.getRGB(), copy.getRGB(50, 50));
    }
    @Test public void invalidCanvasOrBoundsDoNotProduceEvidence() {
        var frame = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        assertNull(EvidenceFramePrivacy.redact(frame, List.of(), 0, 100));
        assertNull(EvidenceFramePrivacy.redact(frame, List.of(new Rectangle()), 100, 100));
    }
}
