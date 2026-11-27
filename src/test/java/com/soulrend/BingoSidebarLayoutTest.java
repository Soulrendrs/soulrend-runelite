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

    @Test
    public void advancedProgressShowsUnitsAndLongTargets() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var tile = new BingoApiClient.BoardTile();
            tile.status = "open";
            tile.scope = "team";
            tile.objective_type = "loot_value";
            tile.unit = "GP";
            tile.value = "5,000,000,000";
            tile.target = "10,000,000,000";
            tile.value_amount = 5_000_000_000L;
            tile.target_amount = 10_000_000_000L;
            var card = SoulrendSidebarPanel.bingoCard(Color.GREEN);
            SoulrendSidebarPanel.addBingoProgress(card, tile, "In progress", "Team", Color.GREEN);
            assertTrue(((JLabel) card.getComponent(1)).getText().contains("10,000,000,000 GP"));
            assertEquals(50, ((javax.swing.JProgressBar) card.getComponent(2)).getValue());
            assertEquals(" sets", BingoApiClient.unitSuffix("sets"));
            assertEquals(" unique items", BingoApiClient.unitSuffix("unique items"));
            assertEquals("", BingoApiClient.unitSuffix("<html>invalid"));
            assertEquals("", BingoApiClient.unitSuffix(null));
        });
    }

    @Test
    public void personalProgressDoesNotReplaceTeamOrSoloBest() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var gson = new com.google.gson.Gson();
            var tile = gson.fromJson("{\"scope\":\"solo\",\"value\":\"7\",\"target\":\"10\","
                + "\"value_amount\":7,\"target_amount\":10,\"personal_amount\":2,\"unit\":\"items\"}",
                BingoApiClient.BoardTile.class);
            var card = SoulrendSidebarPanel.bingoCard(Color.GREEN);
            SoulrendSidebarPanel.addBingoProgress(card, tile, "In progress", "Solo", Color.GREEN);
            assertEquals(4, card.getComponentCount());
            assertTrue(((JLabel) card.getComponent(1)).getText().contains("Solo best"));
            assertTrue(((JLabel) card.getComponent(3)).getText().contains("You · 2 items"));
            card.setSize(200, card.getPreferredSize().height);
            layout(card);
            var personal = card.getComponent(3);
            assertTrue(personal.getHeight() > 0);
            assertTrue(personal.getY() + personal.getHeight() <= card.getHeight());
            tile.personal_amount = null;
            var older = SoulrendSidebarPanel.bingoCard(Color.GREEN);
            SoulrendSidebarPanel.addBingoProgress(older, tile, "In progress", "Solo", Color.GREEN);
            assertEquals(3, older.getComponentCount());
        });
    }

    @Test
    public void collaborativeProgressRemainsIncompleteAtFullQuantity() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var tile = new com.google.gson.Gson().fromJson(
                "{\"scope\":\"team\",\"status\":\"open\",\"value\":\"10\",\"target\":\"10\","
                + "\"value_amount\":10,\"target_amount\":10,\"personal_amount\":10,"
                + "\"collaboration\":{\"minimum_players\":2,\"qualifying_players\":1,\"minimum_each\":3}}",
                BingoApiClient.BoardTile.class);
            var card = SoulrendSidebarPanel.bingoCard(Color.GREEN);
            SoulrendSidebarPanel.addBingoProgress(card, tile, SoulrendSidebarPanel.bingoStatus(tile), "Team", Color.GREEN);
            assertEquals("In progress", SoulrendSidebarPanel.bingoStatus(tile));
            assertEquals(5, card.getComponentCount());
            assertEquals(100, ((javax.swing.JProgressBar) card.getComponent(2)).getValue());
            assertTrue(((JLabel) card.getComponent(3)).getText().contains("1 / 2 teammates"));
            card.setSize(200, card.getPreferredSize().height);
            layout(card);
            var row = card.getComponent(3);
            assertTrue(row.getHeight() > 0);
            assertTrue(row.getY() + row.getHeight() <= card.getHeight());
            tile.status = "hidden";
            assertNull(SoulrendSidebarPanel.collaborationProgressText(tile));
            tile.status = "open";
            tile.collaboration.minimum_players = 0;
            assertNull(SoulrendSidebarPanel.collaborationProgressText(tile));
            tile.collaboration = null;
            assertNull(SoulrendSidebarPanel.collaborationProgressText(tile));
        });
    }

    @Test
    public void scrollRestorationClampsWhenObjectivesDisappear() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var viewport = new javax.swing.JViewport();
            viewport.setExtentSize(new java.awt.Dimension(200, 300));
            viewport.setView(new javax.swing.JPanel());
            viewport.setViewSize(new java.awt.Dimension(200, 1000));
            SoulrendSidebarPanel.restoreBingoScroll(viewport, new java.awt.Point(0, 420));
            assertEquals(420, viewport.getViewPosition().y);
            viewport.setViewSize(new java.awt.Dimension(200, 400));
            SoulrendSidebarPanel.restoreBingoScroll(viewport, new java.awt.Point(0, 420));
            assertEquals(100, viewport.getViewPosition().y);
        });
    }

    private static void layout(Container container) {
        container.doLayout();
        for (var child : container.getComponents()) {
            if (child instanceof Container) layout((Container) child);
        }
    }
}
