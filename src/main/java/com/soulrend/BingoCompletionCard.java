package com.soulrend;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import net.runelite.client.ui.FontManager;

/** Soulrend's gold-rune, aubergine bingo card in the game's own bitmap fonts. */
final class BingoCompletionCard {
    static final int WIDTH = 292;
    static final int HEIGHT = 105;
    private static final Color GOLD = new Color(0xd9ae65);
    private static final Color PURPLE = new Color(0xa981ed);
    private static final Color MINT = new Color(0xa0e7bc);
    private static final Color PARCHMENT = new Color(0xfff7e9);
    private static final Color LAVENDER = new Color(0xd7caeb);

    private BingoCompletionCard() {}

    static void paint(Graphics2D graphics, BingoBannerQueue.Card card, long elapsed) {
        var g = (Graphics2D) graphics.create();
        try {
            float opening = Math.min(1f, (elapsed + 30) / 180f);
            g.setComposite(AlphaComposite.SrcOver.derive(opening));
            g.translate(0, Math.round(-4 * (1 - opening)));
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0x100b16));
            g.fillRoundRect(2, 3, WIDTH, HEIGHT, 16, 16);
            g.setColor(new Color(0x20172c));
            g.fillRoundRect(0, 0, WIDTH, HEIGHT, 16, 16);
            g.setColor(card.completed ? GOLD : PURPLE);
            g.drawRoundRect(0, 0, WIDTH - 1, HEIGHT - 1, 16, 16);
            g.setColor(new Color(0x543d69));
            g.drawLine(13, 47, WIDTH - 13, 47);
            var icon = card.icon;
            if (icon != null) {
                var image = (Graphics2D) g.create(10, 7, 41, 36);
                try {
                    double scale = Math.min(32.0 / icon.getIconWidth(), 32.0 / icon.getIconHeight());
                    if (scale > 0 && Double.isFinite(scale)) {
                        image.translate((41 - icon.getIconWidth() * scale) / 2, (36 - icon.getIconHeight() * scale) / 2);
                        image.scale(scale, scale);
                        icon.paintIcon(null, image, 0, 0);
                    }
                } finally {
                    image.dispose();
                }
            } else {
                g.setColor(GOLD);
                g.fillPolygon(new int[]{28, 36, 28, 20}, new int[]{18, 26, 34, 26}, 4);
            }
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.setFont(FontManager.getRunescapeSmallFont());
            line(g, "SOULREND BINGO", 56, 10, GOLD);
            g.setFont(FontManager.getRunescapeBoldFont());
            line(g, card.completed ? "BINGOOOOO!" : "Tile progress", 56, 25, card.completed ? MINT : PARCHMENT);
            line(g, card.objective, 13, 53, PARCHMENT);
            g.setFont(FontManager.getRunescapeSmallFont());
            line(g, card.detail, 13, 73, LAVENDER);
            if (card.team != null) line(g, card.team, 13, 88, LAVENDER);
        } finally {
            g.dispose();
        }
    }

    private static void line(Graphics2D g, String value, int x, int y, Color color) {
        g.setFont(Ui.fit(g.getFont(), value));
        var metrics = g.getFontMetrics();
        var lines = BingoClogBannerOverlay.wrap(value, metrics, WIDTH - x - 12, 1);
        if (lines.isEmpty()) return;
        g.setColor(color);
        g.drawString(lines.get(0), x, y + metrics.getAscent());
    }
}
