package com.soulrend;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Locale;
import net.runelite.client.ui.FontManager;

/** Lightweight native-font cards; evidence is never presented as confirmed progress. */
final class BingoCompletionCard {
    static final int WIDTH = 320;
    static final int HEIGHT = 116;
    private static final Color GOLD = new Color(0xf0ca79);
    private static final Color PURPLE = new Color(0xb59af4);
    private static final Color BLUE = new Color(0x92c5f5);
    private static final Color MINT = new Color(0xa0e7bc);
    private static final Color PARCHMENT = new Color(0xfff7e9);
    private static final Color LAVENDER = new Color(0xd7caeb);
    private static final Color TRACK = new Color(0x43354f);
    private static final Color EVIDENCE = new Color(0xb6aebf);

    private BingoCompletionCard() {}

    static Color accent(BingoBannerQueue.Card card) {
        return card.evidence ? EVIDENCE : card.teamFinished() || card.unlocked() ? MINT
            : card.completed ? GOLD : card.self ? PURPLE : BLUE;
    }

    static String heading(BingoBannerQueue.Card card) {
        return card.teamFinished() ? "TEAM BINGO COMPLETE!" : card.unlocked() ? "Objective unlocked" : card.evidence ? "Evidence captured" : card.completed ? "BINGOOOOO!"
            : card.self ? "Objective progress" : "Teammate progress";
    }

    static String progressLabel(BingoBannerQueue.Card card) {
        if (!card.hasProgress()) return "";
        return String.format(Locale.ROOT, "%,d / %,d%s", card.value, card.target, BingoApiClient.unitSuffix(card.unit));
    }

    static void paint(Graphics2D graphics, BingoBannerQueue.Card card, long elapsed) {
        paint(graphics, card, elapsed, true);
    }

    static void paint(Graphics2D graphics, BingoBannerQueue.Card card, long elapsed, boolean animations) {
        var g = (Graphics2D) graphics.create();
        try {
            float opening = animations ? Math.max(0f, Math.min(1f, (elapsed + 30) / 180f)) : 1f;
            g.setComposite(AlphaComposite.SrcOver.derive(opening));
            if (animations) {
                double scale = 0.96 + 0.04 * opening;
                g.translate(WIDTH * (1 - scale) / 2, HEIGHT * (1 - scale) / 2);
                g.scale(scale, scale);
            }
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            var accent = accent(card);
            g.setColor(new Color(0x100b16));
            g.fillRoundRect(2, 3, WIDTH - 2, HEIGHT - 3, 14, 14);
            g.setPaint(new GradientPaint(0, 0, card.teamFinished() ? new Color(0x204332) : card.completed ? new Color(0x382937) : new Color(0x292038),
                0, HEIGHT, new Color(0x18121f)));
            g.fillRoundRect(1, 1, WIDTH - 3, HEIGHT - 4, 14, 14);
            g.setColor(accent);
            g.drawRoundRect(1, 1, WIDTH - 3, HEIGHT - 4, 14, 14);
            if (card.completed) {
                g.setColor(new Color(0x8f7042));
                g.drawRoundRect(4, 4, WIDTH - 9, HEIGHT - 10, 11, 11);
                if (animations) {
                    for (int i = 0; i < (card.teamFinished() ? 12 : 8); i++) {
                        int x = i % 2 == 0 ? 9 + (i / 2) * 4 : WIDTH - 10 - (i / 2) * 4;
                        int y = 12 + (int) ((Math.max(0, elapsed) / 30 + i * 13) % 86);
                        g.setColor(new Color(240, 202, 121, 90 + (int) ((elapsed / 10 + i * 19) % 100)));
                        g.fillPolygon(new int[]{x, x + 2, x, x - 2}, new int[]{y - 3, y, y + 3, y}, 4);
                    }
                }
            }
            g.setColor(new Color(0x34283e));
            g.fillRoundRect(14, 14, 43, 43, 8, 8);
            g.setColor(accent);
            g.drawRoundRect(14, 14, 43, 43, 8, 8);
            drawIcon(g, card, accent);

            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.setFont(FontManager.getRunescapeSmallFont());
            line(g, "SOULREND BINGO", 67, 9, WIDTH - 83, accent);
            g.setFont(FontManager.getRunescapeBoldFont());
            line(g, heading(card), 67, 23, WIDTH - 83, card.completed || card.unlocked() ? accent : PARCHMENT);
            String gain = !card.completed && !card.evidence && card.quantity > 0
                ? String.format(Locale.ROOT, "+%,d", card.quantity) : "";
            int gainWidth = gain.isEmpty() ? 0 : Math.min(100, g.getFontMetrics().stringWidth(gain) + 12);
            line(g, card.objective, 67, 42, WIDTH - 83 - gainWidth, PARCHMENT);
            if (!gain.isEmpty()) line(g, gain, WIDTH - 15 - gainWidth, 42, gainWidth, accent);

            g.setColor(new Color(0x564462));
            g.drawLine(15, 64, WIDTH - 16, 64);
            g.setFont(FontManager.getRunescapeSmallFont());
            if (card.hasProgress()) {
                String count = progressLabel(card);
                int countWidth = g.getFontMetrics().stringWidth(count);
                String status = card.completed ? "Completed"
                    : "solo".equals(card.scope) ? "Solo best" : "Team";
                int available = WIDTH - 31;
                if (countWidth + g.getFontMetrics().stringWidth(status) + 10 <= available) {
                    line(g, status, 15, 69, available - countWidth - 10, card.completed ? MINT : LAVENDER);
                    line(g, count, WIDTH - 16 - countWidth, 69, countWidth + 1, PARCHMENT);
                } else {
                    line(g, count, 15, 69, available, PARCHMENT);
                }
                g.setColor(TRACK);
                g.fillRoundRect(15, 86, WIDTH - 31, 6, 6, 6);
                double fraction = card.fraction();
                if (animations && !card.completed) fraction *= Math.min(1.0, Math.max(0, elapsed) / 220.0);
                int filled = (int) Math.round((WIDTH - 31) * fraction);
                if (filled > 0) {
                    g.setColor(card.completed ? MINT : accent);
                    g.fillRoundRect(15, 86, filled, 6, 6, 6);
                }
                if (!card.self && card.contributor != null && !card.contributor.isBlank())
                    line(g, "By: " + card.contributor, 15, 96, WIDTH - 31, LAVENDER);
            } else {
                line(g, card.detail, 15, 72, WIDTH - 31, card.completed || card.unlocked() ? MINT : LAVENDER);
                if ((card.evidence || card.teamFinished()) && card.team != null)
                    line(g, card.team, 15, 91, WIDTH - 31, LAVENDER);
            }
        } finally {
            g.dispose();
        }
    }

    private static void drawIcon(Graphics2D g, BingoBannerQueue.Card card, Color accent) {
        var icon = card.icon;
        if (icon != null && icon.getIconWidth() > 0 && icon.getIconHeight() > 0) {
            var image = (Graphics2D) g.create(15, 15, 41, 41);
            try {
                double scale = Math.min(34.0 / icon.getIconWidth(), 34.0 / icon.getIconHeight());
                image.translate((41 - icon.getIconWidth() * scale) / 2, (41 - icon.getIconHeight() * scale) / 2);
                image.scale(scale, scale);
                icon.paintIcon(null, image, 0, 0);
            } finally {
                image.dispose();
            }
        } else if (card.teamFinished()) {
            g.setColor(GOLD);
            g.fillRoundRect(25, 22, 20, 15, 4, 4);
            g.drawRoundRect(19, 23, 10, 12, 6, 6);
            g.drawRoundRect(41, 23, 10, 12, 6, 6);
            g.fillRect(33, 36, 4, 9);
            g.fillRoundRect(27, 45, 16, 4, 2, 2);
        } else {
            g.setColor(accent);
            g.fillPolygon(new int[]{35, 43, 35, 27}, new int[]{24, 35, 46, 35}, 4);
        }
    }

    private static void line(Graphics2D g, String value, int x, int y, int width, Color color) {
        if (value == null || value.isBlank() || width <= 0) return;
        var font = g.getFont();
        try {
            g.setFont(Ui.fit(font, value));
            var metrics = g.getFontMetrics();
            var lines = BingoClogBannerOverlay.wrap(value, metrics, width, 1);
            if (lines.isEmpty()) return;
            g.setColor(color);
            g.drawString(lines.get(0), x, y + metrics.getAscent());
        } finally {
            g.setFont(font);
        }
    }
}
