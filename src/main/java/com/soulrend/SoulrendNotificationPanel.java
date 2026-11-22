package com.soulrend;

import java.awt.*;
import java.time.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.util.LinkBrowser;

import static net.runelite.client.ui.ColorScheme.*;

/** Private Soulrend inbox; all I/O runs on the sidebar's interactive executor, never the game thread. */
final class SoulrendNotificationPanel extends JPanel {
    private final SoulrendSidebarPanel owner;
    private final BingoApiClient api;
    private final JLabel heading = new JLabel("Notifications");
    private final JLabel message = new JLabel("Open the inbox to load notifications.");
    private final JPanel rows = new JPanel();
    private final JButton older = new JButton("Show older");
    private final JButton readAll = new JButton("Mark all read");
    private final Map<String, JButton> filters = new LinkedHashMap<>();
    private String selectedTab = "all";
    private String nextCursor;
    private long generation;

    SoulrendNotificationPanel(SoulrendSidebarPanel owner, BingoApiClient api) {
        this.owner = owner;
        this.api = api;
        setLayout(new BorderLayout(0, 10));
        setBackground(DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(8, 0, 6, 0));

        var top = new JPanel(new BorderLayout(0, 8));
        top.setBackground(DARK_GRAY_COLOR);
        heading.setForeground(Color.WHITE);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 15f));
        top.add(heading, BorderLayout.NORTH);

        var tabs = new JPanel(new GridLayout(1, 3, 4, 0));
        tabs.setBackground(DARK_GRAY_COLOR);
        for (var tab : List.of("all", "unread", "mentions")) {
            var button = new JButton(tab.substring(0, 1).toUpperCase(Locale.ROOT) + tab.substring(1));
            button.addActionListener(e -> {
                selectedTab = tab;
                styleTabs();
                refresh(true);
            });
            filters.put(tab, button);
            tabs.add(button);
        }
        top.add(tabs, BorderLayout.CENTER);
        readAll.addActionListener(e -> {
            readAll.setEnabled(false);
            owner.async(() -> api.markNotificationRead(null), result -> {
                readAll.setEnabled(true);
                if (result == null) {
                    setMessage("Couldn't update the inbox. Check your connection.");
                } else {
                    updateCount(result.unread_count);
                    refresh(true);
                }
            });
        });
        top.add(readAll, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setBackground(DARK_GRAY_COLOR);
        var list = new JPanel(new BorderLayout(0, 8));
        list.setBackground(DARK_GRAY_COLOR);
        list.add(rows, BorderLayout.NORTH);
        var footer = new JPanel(new BorderLayout());
        footer.setBackground(DARK_GRAY_COLOR);
        footer.add(message, BorderLayout.NORTH);
        message.setForeground(LIGHT_GRAY_COLOR);
        older.setVisible(false);
        older.addActionListener(e -> refresh(false));
        footer.add(older, BorderLayout.SOUTH);
        list.add(footer, BorderLayout.SOUTH);
        add(list, BorderLayout.CENTER);
        styleTabs();
    }

    private void styleTabs() {
        filters.forEach((name, button) -> {
            button.setForeground(name.equals(selectedTab) ? Color.WHITE : LIGHT_GRAY_COLOR);
            button.setBackground(name.equals(selectedTab) ? new Color(0x65468c) : DARKER_GRAY_COLOR);
            button.setOpaque(true);
        });
    }

    private void setMessage(String text) {
        message.setText(text);
        message.setVisible(text != null && !text.isEmpty());
    }

    /** Once on entry; the shared sidebar timer makes no notification network calls while closed. */
    void refresh(boolean firstPage) {
        if (api.needsSignIn()) {
            ++generation;
            rows.removeAll();
            nextCursor = null;
            older.setVisible(false);
            setMessage("Connect your Soulrend account to view notifications.");
            revalidate();
            repaint();
            return;
        }
        if (!firstPage && nextCursor == null) return;
        final long request = ++generation;
        final String tab = selectedTab;
        final String cursor = firstPage ? null : nextCursor;
        if (firstPage) {
            rows.removeAll();
            nextCursor = null;
            setMessage("Loading notifications…");
        } else {
            older.setEnabled(false);
        }
        owner.async(() -> api.readNotifications(tab, cursor), response -> {
            if (request != generation || !selectedTab.equals(tab)) return;
            older.setEnabled(true);
            if (response == null) {
                setMessage("Notifications are unavailable. Select a tab to retry.");
                older.setVisible(cursor != null);
                revalidate();
                repaint();
                return;
            }
            updateCount(response.unread_count);
            if (response.items != null) for (var item : response.items) rows.add(itemRow(item));
            nextCursor = response.next_cursor;
            older.setVisible(nextCursor != null && !nextCursor.isEmpty());
            if (rows.getComponentCount() == 0)
                setMessage("mentions".equals(tab) ? "No mentions yet." :
                    "unread".equals(tab) ? "No unread notifications." : "No notifications yet.");
            else setMessage("");
            revalidate();
            repaint();
        });
    }

    void updateCount(int unread) {
        heading.setText(unread > 0 ? "Notifications · " + (unread > 99 ? "99+" : unread) + " unread" : "Notifications");
        owner.notificationCountChanged(unread);
    }

    private JPanel itemRow(BingoApiClient.NotificationItem item) {
        var row = new JPanel(new BorderLayout(4, 5));
        row.setBackground(item.read_at == null ? new Color(0x32283e) : DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(9, 10, 9, 10));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        var title = new JTextArea(item.title == null ? "Notification" : item.title);
        title.setEditable(false);
        title.setOpaque(false);
        title.setLineWrap(true);
        title.setWrapStyleWord(true);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setForeground(Color.WHITE);
        title.setFocusable(false);
        row.add(title, BorderLayout.NORTH);

        var details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setOpaque(false);
        if (item.description != null && !item.description.isEmpty()) {
            var description = new JTextArea(item.description);
            description.setLineWrap(true);
            description.setWrapStyleWord(true);
            description.setEditable(false);
            description.setFocusable(false);
            description.setOpaque(false);
            description.setForeground(LIGHT_GRAY_COLOR);
            description.setAlignmentX(Component.LEFT_ALIGNMENT);
            details.add(description);
        }
        var timestamp = new JLabel(relative(item.created_at));
        timestamp.setForeground(LIGHT_GRAY_COLOR);
        details.add(timestamp);
        row.add(details, BorderLayout.CENTER);

        var controls = new JPanel(new GridLayout(2, 1, 0, 4));
        controls.setOpaque(false);
        var open = new JButton("Open");
        open.addActionListener(e -> {
            var destination = safeDestination(item.url);
            if (destination == null) return;
            if (item.read_at == null) {
                owner.async(() -> api.markNotificationRead(item.id), result -> {
                    if (result != null) {
                        updateCount(result.unread_count);
                        refresh(true);
                    }
                    // Website rechecks current permissions; a notification is never an access grant.
                    LinkBrowser.browse(destination);
                });
            } else LinkBrowser.browse(destination);
        });
        controls.add(open);
        var mark = new JButton("Read");
        mark.setToolTipText("Mark this notification as read");
        mark.setEnabled(item.read_at == null);
        mark.addActionListener(e -> {
            mark.setEnabled(false);
            owner.async(() -> api.markNotificationRead(item.id), result -> {
                if (result == null) {
                    mark.setEnabled(true);
                    setMessage("Couldn't save read state; try again.");
                } else {
                    updateCount(result.unread_count);
                    refresh(true);
                }
            });
        });
        controls.add(mark);
        row.add(controls, BorderLayout.EAST);
        var spacer = new JPanel();
        spacer.setOpaque(false);
        spacer.setPreferredSize(new Dimension(1, 7));
        var wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(DARK_GRAY_COLOR);
        wrapper.add(row, BorderLayout.CENTER);
        wrapper.add(spacer, BorderLayout.SOUTH);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        return wrapper;
    }

    static String safeDestination(String path) {
        if (path == null || !path.startsWith("/") || path.startsWith("//")
            || path.indexOf('\\') >= 0 || path.indexOf('\n') >= 0 || path.indexOf('\r') >= 0)
            return null;
        return BingoApiClient.CANONICAL_SITE + path;
    }

    static String relative(String date) {
        try {
            long minutes = Math.max(0, Duration.between(Instant.parse(date), Instant.now()).toMinutes());
            if (minutes < 1) return "Just now";
            if (minutes < 60) return minutes + "m ago";
            if (minutes < 1440) return minutes / 60 + "h ago";
            return minutes / 1440 + "d ago";
        } catch (Exception e) {
            return "";
        }
    }
}
