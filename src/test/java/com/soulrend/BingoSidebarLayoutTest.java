package com.soulrend;

import java.awt.Color;
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
}
