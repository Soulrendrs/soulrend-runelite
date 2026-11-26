package com.soulrend;

import java.awt.Color;
import java.awt.Container;
import javax.swing.JScrollPane;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BingoSidebarLayoutTest {
    @Test
    public void cardHeightTracksChildrenAddedAfterConstruction() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var card = SoulrendSidebarPanel.bingoCard(Color.GREEN);
            int emptyHeight = card.getMaximumSize().height;
            card.add(Ui.left(new JLabel("Title")));
            card.add(Ui.left(new JLabel("Team · 2 / 5")));
            var details = Ui.left(new JButton("Details"));
            card.add(details);
            int fullHeight = card.getPreferredSize().height;
            assertTrue(fullHeight > emptyHeight);
            assertEquals(fullHeight, card.getMaximumSize().height);

            card.setSize(200, fullHeight);
            card.doLayout();
            assertEquals(0.0, card.getAlignmentX(), 0.0);
            assertTrue(details.getHeight() > 0);
            assertTrue(details.getY() + details.getHeight() <= fullHeight);

            var observed = Ui.left(new JLabel("Observed +2 KC"));
            card.add(observed);
            assertTrue(card.getMaximumSize().height > fullHeight);
        });
    }

    @Test
    public void lastObjectiveScrollsIntoViewInTheWrappedSidebar() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var data = new SoulrendSidebarDataSource(() -> null, null);
            var sidebar = new SoulrendSidebarPanel(data, null, null, null, null, null, null);
            var list = Ui.column();
            JButton last = null;
            for (int i = 0; i < 12; i++) {
                var card = SoulrendSidebarPanel.bingoCard(Color.GREEN);
                card.add(SoulrendSidebarPanel.boardText(
                    "Objective with a long name that wraps " + i, Ui.BOLD, Color.WHITE));
                card.add(Ui.left(new JLabel("Team · 2 / 5")));
                last = Ui.left(new JButton("Details"));
                card.add(last);
                list.add(card);
            }
            sidebar.content.add(list);

            var wrapped = sidebar.getWrappedPanel();
            wrapped.setSize(wrapped.getPreferredSize().width, 380);
            layout(wrapped);
            var scroll = (JScrollPane) wrapped.getComponent(0);
            var viewport = scroll.getViewport();
            assertTrue(viewport.getViewSize().height > viewport.getExtentSize().height);
            var row = SwingUtilities.convertRectangle(last.getParent(), last.getBounds(), viewport.getView());
            assertTrue(row.height > 0);
            scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum());
            assertTrue(viewport.getViewRect().contains(row));
        });
    }

    @Test
    public void rendersManualAndMixedQuantityPayloads() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (String type : new String[]{"manual", "runelite"}) {
                var tile = new com.google.gson.Gson().fromJson(
                    "{\"status\":\"open\",\"credited\":false,\"objective_type\":\"" + type
                        + "\",\"scope\":\"team\",\"value\":\"2\",\"target\":\"5\",\"value_amount\":2,\"target_amount\":5}",
                    BingoApiClient.BoardTile.class);
                var card = SoulrendSidebarPanel.bingoCard(Color.GREEN);
                assertEquals("In progress", SoulrendSidebarPanel.bingoStatus(tile));
                SoulrendSidebarPanel.addBingoProgress(card, tile,
                    SoulrendSidebarPanel.bingoStatus(tile), "Team", Color.GREEN);
                assertEquals(3, card.getComponentCount());
                assertTrue(((JLabel) card.getComponent(1)).getText().contains("2 / 5"));
                var meter = (javax.swing.JProgressBar) card.getComponent(2);
                assertEquals(40, meter.getValue());
                card.setSize(200, card.getPreferredSize().height);
                layout(card);
                assertTrue(meter.getHeight() > 0);
                assertTrue(meter.getY() + meter.getHeight() <= card.getHeight());
                tile.credited = true;
                tile.status = "pending";
                assertEquals("Pending approval", SoulrendSidebarPanel.bingoStatus(tile));
                tile.status = "approved";
                assertEquals("Completed", SoulrendSidebarPanel.bingoStatus(tile));
            }
        });
    }

    private static void layout(Container container) {
        container.doLayout();
        for (var child : container.getComponents()) {
            if (child instanceof Container) layout((Container) child);
        }
    }
}
