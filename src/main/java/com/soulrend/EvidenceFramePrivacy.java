package com.soulrend;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

/** A screenshot-only redaction; never writes into the original rendered frame. */
final class EvidenceFramePrivacy {
    private EvidenceFramePrivacy() {}

    static BufferedImage redact(BufferedImage frame, List<Rectangle> masks, int canvasWidth, int canvasHeight) {
        if (frame == null || canvasWidth <= 0 || canvasHeight <= 0 || masks == null
            || masks.stream().anyMatch(r -> r == null)) return null;
        var copy = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = copy.createGraphics();
        try {
            g.drawImage(frame, 0, 0, null);
            g.setColor(Color.BLACK);
            double sx = frame.getWidth() / (double) canvasWidth;
            double sy = frame.getHeight() / (double) canvasHeight;
            for (Rectangle r : masks) {
                if (r.width <= 0 || r.height <= 0) return null;
                int x = (int) Math.floor(r.x * sx), y = (int) Math.floor(r.y * sy);
                int right = (int) Math.ceil((r.x + (double) r.width) * sx);
                int bottom = (int) Math.ceil((r.y + (double) r.height) * sy);
                g.fillRect(x, y, right - x, bottom - y);
            }
        } finally {
            g.dispose();
        }
        return copy;
    }
}
