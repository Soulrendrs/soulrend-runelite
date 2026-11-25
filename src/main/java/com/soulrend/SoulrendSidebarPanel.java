package com.soulrend;

import static javax.swing.BorderFactory.*;
import static java.util.Collections.*;
import static javax.swing.SwingUtilities.invokeLater;
import static net.runelite.client.ui.ColorScheme.*;
import com.soulrend.SoulrendSidebarDataSource.*;
import lombok.Setter;
import com.google.gson.JsonObject;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.*;
import javax.swing.*;
import javax.swing.Timer;
import java.awt.event.*;
import javax.swing.plaf.basic.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.*;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.game.*;

import static com.soulrend.Ui.*;
import static com.soulrend.GlyphIcon.Shape.*;
import static java.awt.Color.WHITE;
import static com.soulrend.SoulrendSidebarPanel.Tab.*;
import static javax.swing.JOptionPane.*;
import static java.awt.BorderLayout.*;
import static com.soulrend.BannerSoundService.Kind.*;
import net.runelite.client.config.ConfigManager;
import java.util.function.*;

/**
 * The side panel. What it holds natively is what only this client has: signing in, the account line,
 * the clan picker, the tab strip, the Lookup search box, settings and banner sounds, and the starting
 * shot. Everything to look at — the Clan tab's events and boards, You, what Lookup found — is a view
 * the site builds and {@link UiView} draws. An auto-refresh runs only while the panel is open.
 *
 * <p><b>Threading:</b> all Swing mutation stays on the EDT; the config re-read and the views' fetches
 * run off it and hand back via invokeLater. {@link Singleton} — one toolbar panel.</p>
 */
@Slf4j
@Singleton
public class SoulrendSidebarPanel extends PluginPanel {
	/** Auto-refresh cadence while the panel is open. Mirrors the plugin's other polls. */
	static final int POLL_INTERVAL_MS = 15_000;
	/** While the live push is up, one poll per this many ticks (5 minutes). */
	static final int PUSH_SAFETY_TICKS = 20;
	/** Client property on the start-proof checks panel: what it shows now (renderStartProofChecks). */
	static final String START_PROOF_SHOWN = "soulrend.startProofShown";


	// Soulrend theme for the interactive widgets: flat dark surfaces with the gold/orange accent the
	// rest of the sidebar (title, bars, links) already uses — default Swing chrome sticks out badly.
	static final Color WIDGET_BG = DARKER_GRAY_COLOR;
	static final Color WIDGET_BORDER = new Color(0x47_47_47);
	// Buttons sit a step above the panel, not sunk below it, so they read as something to press.
	static final Color BUTTON_BG = new Color(0x3a_3a_3a);

	/** Wrap width for the connect-status line so a long notice wraps instead of clipping (tooltip carries full text). */
	static final int STATUS_WRAP_PX = PANEL_WIDTH - 40;


	/** Clip rows on the home page before the rest collapse into a "+N more" line. */
	static final int BANNER_CLIPS_SHOWN = 8;




	final SoulrendSidebarDataSource data;

	// ---- Tabs: Clan · You · Lookup ----------------------------------------------------------------
	// Clan is everything the panel always was — `content` below, untouched. You and Lookup are views
	// the site builds (UiView), and only exist once the site has answered: until then the strip hides
	// and the Clan tab says it can't reach the site, with the sound lists under it (renderEmpty).
	enum Tab { CLAN, FEED, NOTIFICATIONS, YOU, LOOKUP, SETTINGS }
	Tab tab = CLAN;
	/** Whether the strip is showing, i.e. signed in to a site that serves player cards. */
	boolean tabsOn;
	// Holds ONLY the selected tab's panel. A CardLayout sized every tab to the tallest one — usually
	// Clan mid-event — and BoxLayout spread that extra height through the You card: log bars drifted
	// apart and the grids were squeezed until their last row clipped.
	final JPanel tabBody = new JPanel(new BorderLayout());
	final JPanel tabStrip = new JPanel(new GridBagLayout());
	final Map<Tab, JLabel> tabLabels = new EnumMap<>(Tab.class);
	final JPanel lookupPanel = column();
	/** Lookup is for a player or a clan; this says which the search box means. */
	boolean lookupClans;
	/** Players | Clans, redrawn when the mode changes (styleLookupModes). */
	final JPanel lookupModes = column();
	Boolean lookupModesShown;
	/** The site-built views: the Clan tab, the You tab, and what Lookup found. */
	final UiView clanView;
	final UiView youView;
	final UiView lookupView;
	/** The Feed tab: the clan's and the board's live lists, and the board's chat (view 'feed'). */
	final UiView feedView;
	final UiView[] views;
	/** Whose card You holds. */
	String youRsn;
	/** The Clan tab has asked for its view since it was last reset (sign-in, a clan switch). */
	boolean clanShown;
	/** When the Clan tab last asked the site, so a burst of repaints asks once. */
	long clanAskedAt;
	/** ⚙: syncing and this client's own settings — profile sync, roster sync for admins, sounds. */
	final JPanel settingsPanel = column();
	final SoulrendNotificationPanel notificationPanel;
	final JPanel lookupRecentRow = column();
	final JTextField lookupSearch = new JTextField();
	/** Fills in names as you type in {@link #lookupSearch}; built with the tab bodies. */
	LookupSuggestions lookupCompleter;
	final JLabel lookupClear = new JLabel(new GlyphIcon(CLOSE, 10, MUTED));
	final JPanel lookupBox = new JPanel(new BorderLayout(6, 0));
	/** Newest first, at most five, this session only. */
	final LinkedList<String> recentLookups = new LinkedList<>();
	/** The clan dropdown is wanted by the clan logic AND only shown on the Clan tab. */
	boolean pickerWanted;

	// Header controls (persistent across state changes).
	// Which clan the plugin is pointed at. One Soulrend serves every clan and a person can hold seats in
	// several, so this is a real choice rather than a filter over data we already have: picking here
	// re-addresses everything the plugin does — board, stat pushes, submissions, notifications.
	final JComboBox<ClanChoice> clanPicker = new JComboBox<>();
	final JButton refreshButton = new JButton("Refresh");
	final JButton inboxButton = new JButton(new GlyphIcon(BELL, 15));
	long nextNotificationCountAt;

	// Device sign-in (home-native, DeviceSignIn): shown whenever there is no Account Token yet — the
	// main way in, so nobody has to copy a token out of the site.
	final BingoApiClient api;
	final ConfigManager configManager;
	/** RuneLite's shared client-lifetime scheduler — paces the sign-in flow's approval polls. */
	final ScheduledExecutorService executor;
	final JButton signInButton = new JButton("Connect to Soulrend");
	final JLabel signInStatus = small("", MUTED);
	final JPanel signInRow = new JPanel(new BorderLayout(0, 2));
	final JLabel signInCode = new JLabel("", SwingConstants.CENTER);
	/** Opens the account menu: Sign out. */
	final JLabel accountMenuLink = new JLabel(new GlyphIcon(ELLIPSIS, 14));
	/**
	 * Between {@link #onActivate} and {@link #onDeactivate}. Off screen, a refresh is skipped outright:
	 * XP bumps, config changes and logins all ask for one, and drawing a panel nobody can see is EDT
	 * time for nothing — opening it refreshes anyway.
	 */
	boolean onScreen;
	boolean signInInFlight;
	volatile DeviceSignIn signIn;
	volatile String connectedWebsiteUser;
	volatile String connectedDeviceName;
	long nextSessionProbeAt;
	boolean probingSession;

	// The logged-in character's row (SoulrendSidebarDataSource.AccountRow) — one quiet line, because almost
	// everyone reading it is the owner: "Tracking X · Not you? ×", "Not tracking X here · Track again",
	// or "X is someone else's · It's mine?". The way out for a sibling or a visitor; × hides the
	// tracking line for that character for good.
	final JLabel accountLabel = small("", MUTED);
	/** "Tracking" + the tracked name in its Supporter colours (ChatFlair), swapped as the look changes. */
	final JPanel accountLine = new JPanel();
	JLabel accountName;
	final JLabel accountLink = small("", BRAND_ORANGE);
	final JLabel accountStatus = small("", MUTED);
	final JPanel accountRowPanel = new JPanel(new BorderLayout(4, 2));
	/** On a seasonal world: whether its progress is shared with the clan, and a switch for it. */
	final JPanel seasonRow = new JPanel(new BorderLayout(6, 0));
	final JLabel seasonLabel = small("Sharing season progress", MUTED);
	final JLabel seasonLink = new JLabel("Turn off");
	AccountRow shownAccountRow;
	boolean accountActionInFlight;

	final JPanel content = column();

	// Only re-renders while the panel is visible; started on activate, stopped on deactivate.
	final Timer autoRefresh;

	/** Re-checks the starting-shot card's requirements once a second while it's up; null with no card. */
	final Timer startProofTimer = new Timer(1000, e -> tickStartProof());
	Runnable startProofTick;

	// Guards against ActionEvents fired while we rebuild the combo model.
	boolean rebuildingPicker;

	/**
	 * The executor for fetches somebody clicked and is now watching a spinner for — off RuneLite's
	 * single shared thread (see {@link BingoApiClient#interactiveExecutor}).
	 */
	Executor interactive() {
		return api != null ? api.interactiveExecutor() : executor;
	}

	@Inject
	SoulrendSidebarPanel(SoulrendSidebarDataSource dataSource, BingoApiClient apiClient,
		ConfigManager configManager, ScheduledExecutorService executor,
		ItemManager itemManager, SpriteManager spriteManager, SkillIconManager skillIconManager) {
		super(true); // wrap in RuneLite's scroll pane so a long nearest-tiles list scrolls
		this.data = dataSource;
		this.api = apiClient;
		this.notificationPanel = new SoulrendNotificationPanel(this, apiClient);
		this.configManager = configManager;
		this.executor = executor;
		// Item and boss icons for the player card: the managers are null in tests that build the panel by hand.
		Supplier<UiView> view = () -> new UiView(uiHost(), itemManager, spriteManager, skillIconManager);
		views = new UiView[]{clanView = view.get(), youView = view.get(), lookupView = view.get(), feedView = view.get()};

		setLayout(new BorderLayout());
		setBorder(createEmptyBorder(BORDER_OFFSET, BORDER_OFFSET, BORDER_OFFSET, BORDER_OFFSET));
		setBackground(DARK_GRAY_COLOR);

		add(buildHeader(), NORTH);
		SlimScrollBarUI.applyTo(getWrappedPanel());

		buildTabBodies();
		tabBody.setBackground(DARK_GRAY_COLOR);
		tabBody.add(content, NORTH);
		add(tabBody, CENTER);

		autoRefresh = new Timer(POLL_INTERVAL_MS, e -> {
			// With the live push up, a push asks for the board when it moves (boardChanged) and "2m ago"
			// times tick locally (UiView), so the timer is only a safety net: every 5 minutes.
			refreshNotificationCountIfDue();
			refreshDeviceSessionIfDue();
			if (!typingInSearch() && (!pushLive.getAsBoolean() || ++quietTicks % PUSH_SAFETY_TICKS == 0)) refresh(); // the view redraws only if the site's answer changed
		});
		autoRefresh.setCoalesce(true);

		renderLoading();
	}

	JPanel buildHeader() {
		var header = new JPanel(new BorderLayout(0, 8));
		header.setBackground(DARK_GRAY_COLOR);
		header.setBorder(createEmptyBorder(0, 0, 8, 0));

		var title = label("Soulrend", BOLD, BRAND_ORANGE);

		styleFlatButton(refreshButton, WHITE);
		refreshButton.addActionListener(e -> refresh(true));
		// As wide as "Refreshing…" from the start, so the header doesn't shift while it works.
		refreshing(true);
		refreshButton.setPreferredSize(refreshButton.getPreferredSize());
		refreshing(false);

		var titleRow = left(new JPanel(new BorderLayout(6, 0)));
		titleRow.setBackground(DARK_GRAY_COLOR);
		titleRow.add(title, CENTER);
		styleFlatButton(inboxButton, WHITE);
		inboxButton.setBorder(createEmptyBorder(5, 6, 5, 6));
		inboxButton.setToolTipText("Notifications");
		inboxButton.addActionListener(e -> selectTab(tab == NOTIFICATIONS ? CLAN : NOTIFICATIONS));
		var headerActions = new JPanel(new BorderLayout(4, 0));
		headerActions.setBackground(DARK_GRAY_COLOR);
		headerActions.add(inboxButton, WEST);
		headerActions.add(refreshButton, EAST);
		titleRow.add(headerActions, EAST);
		capH(titleRow);

		// Sign-in affordance — visible whenever there is no token (see refreshSignInRow).
		styleFlatButton(signInButton, BRAND_ORANGE);
		signInButton.addActionListener(e -> {
			if (showConfirmDialog(this, "Connecting sends your IP address, installation metadata and, after approval, linked character and bingo activity to soulrend.events, a third-party server not controlled or verified by RuneLite developers. Optional screenshot uploads require a separate setting. Continue?", "Connect to Soulrend", YES_NO_OPTION) == YES_OPTION) startSignIn();
		});
		signInStatus.setVisible(false);
		signInRow.setBackground(DARK_GRAY_COLOR);
		signInRow.add(signInButton, NORTH);
		signInCode.setFont(new Font(Font.MONOSPACED, Font.BOLD, 22));
		signInCode.setForeground(WHITE);
		signInCode.setBackground(DARKER_GRAY_COLOR);
		signInCode.setOpaque(true);
		signInCode.setBorder(createEmptyBorder(8, 6, 8, 6));
		signInCode.putClientProperty("html.disable", Boolean.TRUE);
		signInCode.setVisible(false);
		signInRow.add(signInCode, CENTER);
		signInRow.add(signInStatus, SOUTH);
		left(capH(signInRow)).setVisible(false);

		onClick(accountLink, this::onAccountButton);
		accountStatus.setVisible(false);
		styleLink(accountMenuLink, "Account: Sign out", () -> showAccountMenu(accountMenuLink));
		// One strip for who you are here: the character being tracked on the left, and everything
		// about the sign-in itself behind the ⋯ menu on the right. BorderLayout, not FlowLayout: every
		// label is centred on the same line, where FlowLayout sat the link on its own baseline.
		var accountActions = new JPanel(new BorderLayout(8, 0));
		accountActions.setBackground(DARKER_GRAY_COLOR);
		accountActions.add(accountLink, CENTER);
		accountActions.add(accountMenuLink, EAST);
		accountRowPanel.setBackground(DARKER_GRAY_COLOR);
		accountRowPanel.setBorder(createEmptyBorder(6, 8, 6, 8));
		accountLine.setLayout(new BoxLayout(accountLine, BoxLayout.X_AXIS));
		accountLine.setOpaque(false);
		accountLine.add(accountLabel);
		accountRowPanel.add(accountLine, CENTER);
		accountRowPanel.add(accountActions, EAST);
		accountRowPanel.add(accountStatus, SOUTH);
		left(accountRowPanel).setVisible(false);

		styleLink(seasonLink, "Your seasonal progress (relics, areas, tasks, with a screenshot) goes to your clan."
			+ " Your clan chooses what gets posted on the Soulrend website.", () -> data.setShareSeasonal(!data.shareSeasonal()));
		seasonRow.setBackground(DARKER_GRAY_COLOR);
		seasonRow.setBorder(createEmptyBorder(4, 8, 4, 8));
		seasonRow.add(seasonLabel, CENTER);
		seasonRow.add(seasonLink, EAST);
		left(capH(seasonRow)).setVisible(false);

		var top = column();
		addAll(top, titleRow, gap(6), signInRow, accountRowPanel, seasonRow);
		header.add(top, NORTH);

		// Clan filter — selecting a clan re-renders from the held snapshot (no refetch).
		styleCombo(clanPicker);
		clanPicker.setRenderer(new ClanChoiceRenderer());
		clanPicker.setFocusable(false);
		clanPicker.addActionListener(e -> {
			if (rebuildingPicker) return;
			var sel = (ClanChoice) clanPicker.getSelectedItem();
			if (sel == null || sel.slug.equals(data.chosenClan())) return;
			switchClan(sel.slug);
		});
		var south = new JPanel(new BorderLayout(0, 8));
		south.setBackground(DARK_GRAY_COLOR);
		south.add(buildTabStrip(), NORTH);
		south.add(clanPicker, SOUTH);
		header.add(south, SOUTH);

		return header;
	}

	/**
	 * Address another clan. Not a filter over a snapshot we already hold — the other clan's board lives
	 * on the site, so this hands the choice to the plugin (which persists it and re-addresses the
	 * client) and shows the loading state until the answer comes back.
	 *
	 * <p>No refresh() here on purpose. The source renders from the config the plugin holds, and that
	 * config is still the old clan's until the refetch lands — refreshing now would repaint the clan
	 * they just switched away from. The plugin repaints once it has the new answer.</p>
	 */
	void switchClan(String slug) {
		clanShown = false; // another clan's events are a different list — start at the top
		data.chooseClan(slug);
		renderLoading();
	}

	// ---- Tabs ---------------------------------------------------------------------------------------

	JPanel buildTabStrip() {
		tabStrip.setBackground(DARK_GRAY_COLOR);
		var gc = new GridBagConstraints();
		gc.fill = GridBagConstraints.BOTH;
		gc.gridy = 0;
		for (var t : Tab.values()) {
			JLabel label;
			if (t == NOTIFICATIONS) {
				label = new JLabel(new GlyphIcon(BELL, 13), SwingConstants.CENTER);
				label.setToolTipText("Notifications");
				label.setPreferredSize(new Dimension(34, label.getPreferredSize().height));
				gc.weightx = 0;
			} else if (t == SETTINGS) {
				// An icon, not a fourth word: three named tabs and a narrow gear keep the strip calm.
				label = new JLabel(new GlyphIcon(GEAR, 13), SwingConstants.CENTER);
				label.setToolTipText("Syncing and settings");
				label.setPreferredSize(new Dimension(34, label.getPreferredSize().height));
				gc.weightx = 0;
			} else {
				label = new ClipLabel(t.name().charAt(0) + t.name().substring(1).toLowerCase(), SwingConstants.CENTER);
				label.setFont(SMALL);
				gc.weightx = 1;
			}
			tabLabels.put(t, onClick(label, () -> {
				if (tabsOn) selectTab(t);
			}));
			gc.gridx = t.ordinal();
			tabStrip.add(label, gc);
		}
		styleTabs();
		tabStrip.setVisible(false);
		return tabStrip;
	}

	/** A creator space is not a clan: its tab says "Space". Follows the config on every refresh. EDT. */
	void relabelClanTab() {
		var label = tabLabels.get(CLAN);
		if (label == null) return;
		var space = data.activeIsSpace();
		var text = space ? "Space" : "Clan";
		if (!text.equals(label.getText())) {
			label.setText(text);
			label.setToolTipText(space ? "This creator's space and events" : null);
		}
	}

	/** Selected: white with an orange underline. The rest: grey over a hairline; all greyed while signed out. */
	void styleTabs() {
		for (var e : tabLabels.entrySet()) {
			var on = tabsOn && e.getKey() == tab;
			e.getValue().setForeground(on ? WHITE : tabsOn ? MUTED : MEDIUM_GRAY_COLOR);
			e.getValue().setBorder(createCompoundBorder(
				createMatteBorder(0, 0, on ? 2 : 1, 0, on ? BRAND_ORANGE : MEDIUM_GRAY_COLOR),
				createEmptyBorder(4, 0, on ? 3 : 4, 0)));
		}
	}

	void buildTabBodies() {

		// Our own field rather than RuneLite's IconTextField: that one draws typed text grey and a
		// light-grey clear button with a red cross, neither of which can be restyled from outside.
		styleSearch(lookupSearch);
		lookupSearch.setBorder(createEmptyBorder());
		lookupSearch.setToolTipText("A player's name — press Enter");
		lookupSearch.addActionListener(e -> {
			if (lookupClans) {
				lookupView.show("clans", singletonMap("q", lookupSearch.getText().trim()), "Searching…");
			} else showLookup(lookupSearch.getText());
		});
		onEdit(lookupSearch, () -> lookupClear.setVisible(!lookupSearch.getText().isEmpty()));
		// Players only: clan names aren't in any list the game keeps.
		lookupCompleter = new LookupSuggestions(lookupSearch, () -> !lookupClans,
			() -> new ArrayList<>(recentLookups), data::myRsn, data::lookupNames);
		lookupClear.setVisible(false);
		lookupClear.setToolTipText("Clear");
		onClick(lookupClear, () -> {
			lookupSearch.setText("");
			lookupSearch.requestFocusInWindow();
			sayLookupHint();
		});
		lookupBox.setBackground(DARKER_GRAY_COLOR);
		setBoxLine(MEDIUM_GRAY_COLOR);
		lookupBox.add(small("Name", MUTED), WEST);
		lookupBox.add(lookupSearch, CENTER);
		lookupBox.add(lookupClear, EAST);
		capH(left(lookupBox));
		// Focus shows as the underline turning orange.
		lookupSearch.addFocusListener(new FocusAdapter() {
			public void focusGained(FocusEvent e) {
				setBoxLine(BRAND_ORANGE);
				lookupCompleter.refresh();
			}

			public void focusLost(FocusEvent e) {
				setBoxLine(MEDIUM_GRAY_COLOR);
			}
		});

		styleLookupModes();
		addAll(lookupPanel, lookupModes, gap(6), lookupBox, gap(6), lookupRecentRow, lookupView);
		lookupView.say(LOOKUP_HINT);
	}

	static final String LOOKUP_HINT = "Search a name — or right-click anyone in game and pick Soulrend.";

	void setBoxLine(Color c) {
		lookupBox.setBorder(createCompoundBorder(
			createMatteBorder(0, 0, 1, 0, c),
			createEmptyBorder(6, 8, 6, 8)));
	}

	/**
	 * Signed in to a site that builds the panel's cards — the condition for the strip to show at all, and
	 * for a lookup to open in the panel (false ⇒ the caller opens the browser instead).
	 */
	boolean playerCardsAvailable() {
		return !api.needsSignIn() && data.supportsPluginUi();
	}

	/** Re-checked on every render: the config's answer brings the tabs, and sign-out takes them away. */
	void updateTabStrip() {
		relabelClanTab();
		styleLookupModes();
		var on = playerCardsAvailable();
		// Signed out the strip still shows, greyed: there is more here once you sign in.
		tabStrip.setVisible(on || api.needsSignIn());
		if (on != tabsOn) {
			tabsOn = on;
			if (!on && tab != CLAN) selectTab(CLAN);
			styleTabs();
		}
		applyPicker();
	}

	void setPickerWanted(boolean wanted) {
		pickerWanted = wanted;
		applyPicker();
	}

	void applyPicker() {
		clanPicker.setVisible(pickerWanted && tab == CLAN);
	}

	void selectTab(Tab next) {
		tab = next;
		feedRead();
		data.feedOnScreen(onScreen && next == FEED);
		tabBody.removeAll();
		tabBody.add(next == CLAN ? content : next == FEED ? feedView : next == YOU ? youView : next == LOOKUP ? lookupPanel : next == NOTIFICATIONS ? notificationPanel : settingsPanel,
			NORTH);
		tabBody.revalidate();
		styleTabs();
		applyPicker();
		if (next == YOU) {
			renderYou(true);
		} else if (next == FEED) {
			renderFeed();
		} else if (next == NOTIFICATIONS) notificationPanel.refresh(true);
		else if (next == SETTINGS) renderSettings();
		redraw(this);
	}

	/** Look a character up in the Lookup tab — the right-click "Soulrend" entry lands here. */
	void showLookup(String rawName) {
		invokeLater(() -> {
			var name = rawName == null ? "" : rawName.replace(' ', ' ').trim();
			if (tab != LOOKUP) selectTab(LOOKUP);
			if (name.isEmpty()) return;
			if (lookupClans) setLookupMode(false);
			lookupSearch.setText(name);
			remember(name);
			lookupView.show("player", singletonMap("rsn", name), "Looking up " + name + "…");
		});
	}

	/** {@code fetch} on the interactive executor, then {@code then} with its answer on the EDT. */
	/**
	 * The last answer for each request (clan, character, live board, view and args), drawn the moment it's asked
	 * for again while the fresh one is on its way — going back, or between tabs, is instant. Anything
	 * sent to the site (a claim, a pin) may change every view, so it empties. EDT only.
	 */
	final Map<String, JsonObject> uiCache = BingoApiClient.capped(40, true);

	<T> void async(Supplier<T> fetch, Consumer<T> then) {
		interactive().execute(() -> {
			var result = fetch.get();
			invokeLater(() -> then.accept(result));
		});
	}

	/**
	 * Where the site-built views ({@link UiView}) fetch, post and send their clicks: the interactive
	 * executor for the calls, the browser for the site's own pages, Lookup for a name or a clan.
	 */
	UiView.Host uiHost() {
		return new UiView.Host() {
			public void load(String view, Map<String, String> args, Consumer<JsonObject> done) {
				var clan = data.activeClan();
				var sent = withPins(args);
				// The live board's id too: "event 0" (the board page's tiles, teams) means whichever board is live,
				// so an answer kept for one board must never be drawn once another one is.
				var key = clan + '|' + api.getCurrentRsn() + '|' + data.activeEventId() + '|' + view + '|' + new TreeMap<>(sent);
				var seen = uiCache.get(key);
				if (seen != null) done.accept(seen);
				async(() -> api.fetchUi(view, sent, clan), v -> {
					if (v != null) uiCache.put(key, v);
					if (v != null || seen == null) done.accept(v); // a failed fetch leaves the remembered answer up
				});
			}

			public void pin(int event, int tile, boolean on) {
				uiCache.clear();
				if (tile <= 0) {
					data.unpinAll(event);
				} else data.setTileTracked(event, tile, on);
			}

			public void switchClan(String slug) {
				SoulrendSidebarPanel.this.switchClan(slug);
			}

			public void openTab(String name, Map<String, String> args) {
				// Only a tab this panel is showing.
				var feed = tabLabels.get(FEED);
				if (!"feed".equals(name) || feed == null || !feed.isShowing()) return;
				feedArgs = args == null ? emptyMap() : args;
				selectTab(FEED);
			}

			public long count(int tile) {
				return data.tileCount(tile);
			}

			public String myName() {
				return data.myRsn();
			}

			public void gameFocus() {
				data.gameFocus();
			}

			public JComponent nativePart(String name) {
				var proof = "start-proof".equals(name) ? data.startProof() : null;
				return proof == null ? null : buildStartProofCard(proof);
			}

			public void post(String act, Map<String, String> args, Map<String, String> fields, Consumer<JsonObject> done) {
				uiCache.clear();
				async(() -> api.postUi(act, args, fields), done);
			}

			public void site(String path) {
				LinkBrowser.browse(api.siteLink(path));
			}

			public void image(String path, int w, int h, boolean motion, Consumer<Icon> done) {
				// A picture already in hand lands now, not a frame later: a redraw doesn't blink it out and back.
				api.thumb(path, w, h, motion && GradientLabel.animate, img -> {
					if (SwingUtilities.isEventDispatchThread()) done.accept(img);
					else invokeLater(() -> done.accept(img));
				});
			}

			public void lookup(String rsn) {
				showLookup(rsn);
			}

			public void clan(String slug) {
				if (tab != LOOKUP) selectTab(LOOKUP);
				setLookupMode(true);
				lookupView.show("clan", singletonMap("slug", slug), "Loading the clan…");
			}
		};
	}

	/**
	 * A view's args plus what only this client knows: whether the member picked this clan (rather than
	 * Auto), and whether it can pin a tracker on the board here (in game, tracker on) and which tiles
	 * it already has.
	 */
	Map<String, String> withPins(Map<String, String> args) {
		var given = parseIntOr(args.get("event"), 0);
		var event = given > 0 ? given : data.activeEventId();
		var out = new HashMap<>(args);
		out.put("chosen", data.chosenClan().isEmpty() ? "" : "1");
		if (data.canTrackTiles(event, "1".equals(args.get("pre")))) {
			out.put("track", "1");
			var pins = new StringJoiner(",");
			for (var id : data.pinnedTiles(event)) pins.add("" + id);
			out.put("pins", pins.toString());
		}
		return out;
	}

	static int parseIntOr(String s, int fallback) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	void remember(String name) {
		recentLookups.removeIf(n -> Rsn.same(n, name));
		recentLookups.addFirst(name);
		while (recentLookups.size() > 5) recentLookups.removeLast();
		lookupRecentRow.removeAll();
		if (recentLookups.size() > 1) {
			// Recent names as chips: a grey outline, one click looks them up again.
			var wrap = left(new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4)));
			wrap.setBackground(DARK_GRAY_COLOR);
			for (var n : recentLookups.subList(1, recentLookups.size())) {
				var chip = new Round(new BorderLayout(), 3, null, MEDIUM_GRAY_COLOR, null);
				chip.setBorder(createEmptyBorder(2, 6, 2, 6));
				chip.add(small(plainText(n), LIGHT_GRAY_COLOR));
				chip.setToolTipText(plainText("Look up " + n));
				var slot = new JPanel(new BorderLayout());
				slot.setOpaque(false);
				slot.setBorder(createEmptyBorder(0, 0, 0, 4));
				slot.add(onClick(chip, () -> showLookup(n)));
				wrap.add(slot);
			}
			wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, wrap.getPreferredSize().height * 2));
			addAll(lookupRecentRow, wrap, gap(6));
		}
		lookupRecentRow.revalidate();
	}

	/**
	 * The Feed tab. Opening it asks again (the list is live from there on, push rows grow it), so a
	 * row missed while the socket was down is back the next time anyone looks.
	 */
	void renderFeed() {
		if (!data.supportsPluginUi()) { // the Feed tab's live lists: same condition as the tabs
			feedView.say("Sign in to see the live feed.");
			return;
		}
		feedView.show("feed", feedArgs, "Loading the feed…");
		feedArgs = emptyMap(); // a board's list once; the tab itself opens on its default after
	}

	/** The list the next Feed-tab open shows (a board's feed or chat from its buttons); empty = the default. */
	Map<String, String> feedArgs = emptyMap();

	void feedRead() {
		var l = tabLabels.get(FEED);
		if (onScreen && tab == FEED && l != null) l.setText("Feed");
	}

	/** Push rows for the Feed tab's lists (SoulrendPlugin.applyPush), from any thread. */
	void feedPush(JsonObject m) {
		invokeLater(() -> feedView.feedPush(m));
	}

	/**
	 * Your own card and goals. Opening the tab fetches them again; a refresh only notices a change of
	 * character, so the card isn't rebuilt (and its open section reset) every few seconds.
	 */
	void renderYou(boolean refetch) {
		var me = data.myRsn();
		if (me == null) {
			youRsn = null;
			youView.say("Log in to a character to see your card.");
			return;
		}
		var same = Rsn.same(me, youRsn);
		if (refetch || !same) {
			youRsn = me;
			youView.show("you", emptyMap(), same ? null : "Loading your card…");
		}
	}

	void setLookupMode(boolean clans) {
		lookupClans = clans;
		styleLookupModes();
		sayLookupHint();
	}

	void sayLookupHint() {
		lookupView.say(lookupClans ? "Search a clan by name." : LOOKUP_HINT);
	}

	void styleLookupModes() {
		if (Boolean.valueOf(lookupClans).equals(lookupModesShown)) return;
		lookupModesShown = lookupClans;
		lookupModes.removeAll();
		lookupModes.add(UiView.segments(Arrays.asList("Players", "Clans"), lookupClans ? "Clans" : "Players",
			m -> setLookupMode("Clans".equals(m))));
		redraw(lookupModes);
	}

	/**
	 * ⚙ — everything that's syncing or a setting rather than something to look at, as cards: Syncing,
	 * Banner sounds, then the site's settings as rows with ↗ (they open the browser). No gold: nothing
	 * here is urgent.
	 */
	void renderSettings() {
		var actions = data.actionsFor();
		// Asked on every refresh, but what it shows only moves with these: rebuild only when one does.
		var key = settingsKey(actions);
		if (key.equals(settingsShown)) return;
		settingsShown = key;
		settingsPanel.removeAll();
		var sync = buildPanelActions(actions);
		if (sync.getComponentCount() > 0) addCard(sync);
		addCard(buildBannerSounds());
		addAll(settingsPanel, bold("On the Soulrend website"), gap(6));
		siteRow("Discord posts & screenshot privacy", "What you post to your clan's Discord", "/settings/announcements");
		siteRow("All your Soulrend settings", "Profile, characters, name colours and more", "/settings");
		siteRow("Guides", "The side panel, events, proofs", "/guide");
		siteRow("What's new", "Everything new on Soulrend, for your roles", "/whats-new");
		redraw(settingsPanel);
	}

	/** A section as a card: the darker rounded surface, 10px in. */
	void addCard(JPanel inner) {
		darken(inner);
		var card = left(new Round(new BorderLayout(), 6, DARKER_GRAY_COLOR, null, null));
		card.setBorder(createEmptyBorder(10, 10, 10, 10));
		card.add(inner);
		addAll(settingsPanel, capH(card), gap(10));
	}

	/** Panel-grey grounds inside a card take the card's colour. */
	static void darken(Component c) {
		if (c instanceof JPanel && !(c instanceof Round) && DARK_GRAY_COLOR.equals(c.getBackground())) c.setBackground(DARKER_GRAY_COLOR);
		if (c instanceof Container) for (var k : ((Container) c).getComponents()) darken(k);
	}

	/** A page of the site as a raised row: its name, ↗, and what's there under it. */
	void siteRow(String name, String sub, String path) {
		settingsPanel.add(linkRow(UiView.RAISED, small(name, WHITE), note(sub, 160), sub, () -> LinkBrowser.browse(api.siteLink(path))));
		settingsPanel.add(gap(4));
	}

	static Round linkRow(Color fill, JLabel name, JLabel sub, String tip, Runnable open) {
		var row = left(new Round(new BorderLayout(4, 2), 4, fill, null, WIDGET_BORDER));
		row.setBorder(createEmptyBorder(7, 8, 7, 8));
		row.add(name, CENTER);
		row.add(small("↗", MUTED), EAST);
		if (sub != null) row.add(sub, SOUTH);
		row.setToolTipText(tip);
		return capH(onClick(row, open));
	}


	/** What the settings tab last drew from (settingsKey), so an unchanged refresh skips the rebuild. */
	String settingsShown;

	String settingsKey(PanelActions actions) {
		var k = new StringBuilder()
			.append(actions.canSyncRoster).append('|').append(actions.rosterNote);
		for (var clip : data.sounds(BANNER)) k.append('|').append(clip).append(data.bannerSoundOn(clip) ? '+' : '-');
		return k.append("|m").append(data.sounds(MISSION).size()).toString();
	}

	// ---- Lifecycle (Activatable) — poll only while the panel is on screen -------------------------

	public void onActivate() {
		onScreen = true;
		refreshNotificationCountIfDue();
		refreshDeviceSessionIfDue();
		feedRead();
		data.feedOnScreen(tab == FEED);
		refresh();
		autoRefresh.start();
		for (var v : views) v.resume();
		startProofTimer.start();
		observedGainsTimer.start();
	}

	public void onDeactivate() {
        generation++;
        reading = false;
        nextRead = 0;
		onScreen = false;
		data.feedOnScreen(false);
		autoRefresh.stop();
		for (var v : views) v.stop();
		startProofTimer.stop();
		observedGainsTimer.stop();
	}

	// ---- Device sign-in (DeviceSignIn) ------------------------------------------------------

	/**
	 * Show the Sign-in button whenever there is no Account Token — and say where it will connect.
	 *
	 * The destination is stated BEFORE the click: nothing is sent to it until then, and pressing the
	 * button is the member explicitly choosing to connect.
	 */
	void refreshSignInRow() {
		inboxButton.setEnabled(!api.needsSignIn());
		if (signInInFlight) return;
		var show = api.needsSignIn();
		signInRow.setVisible(show);
		if (show && !signInStatus.isVisible()) setSignInStatus("Connects to " + SetupNudge.SITE_HOST + ".");
		redraw(signInRow);
	}

	/** Stop a sign-in that is still polling — the plugin is shutting down. */
	void cancelSignIn() {
		var flow = signIn;
		if (flow != null) flow.cancel();
	}

	/** Detect true remote revocation without treating temporary network failures as logout.
	 * Only invoked on the visible sidebar; network work stays off the EDT. */
	void refreshDeviceSessionIfDue() {
		if (!onScreen || signInInFlight || api.needsSignIn() || api.accountPaused || probingSession) return;
		long now = System.currentTimeMillis();
		if (now < nextSessionProbeAt) return;
		nextSessionProbeAt = now + 60_000L;
		String token = api.playerToken;
		probingSession = true;
		interactive().execute(() -> {
			BingoApiClient.SessionProbe probe = api.probeSession(token);
			invokeLater(() -> {
				probingSession = false;
				if (!Objects.equals(token, api.playerToken) || !onScreen) return;
				if (probe != null && !probe.authorized) {
					connectedWebsiteUser = null;
					connectedDeviceName = null;
					setToken("");
					setSignInStatus("Session expired or revoked. Connect again.");
					refreshSignInRow();
					refreshAccountRow();
				} else if (probe != null && probe.identity != null &&
					probe.identity.username != null) {
					String user = probe.identity.username;
					String name = probe.identity.device != null ? probe.identity.device.name : null;
					if (!Objects.equals(connectedWebsiteUser, user) ||
						!Objects.equals(connectedDeviceName, name)) {
						connectedWebsiteUser = user;
						connectedDeviceName = name;
						refreshAccountRow();
					}
				}
			});
		});
	}

	/** Account-wide unread badge, polled at most once per minute while the sidebar is visible. */
	void refreshNotificationCountIfDue() {
		if (!onScreen || api.needsSignIn() || api.accountPaused) return;
		long now = System.currentTimeMillis();
		if (now < nextNotificationCountAt) return;
		nextNotificationCountAt = now + 60_000L;
		async(api::notificationCount, result -> {
			if (onScreen && !api.needsSignIn() && result != null)
				notificationPanel.updateCount(result.unread_count);
		});
	}

	void notificationCountChanged(int unread) {
		var badge = unread > 99 ? "99+" : String.valueOf(unread);
		var tip = unread > 0 ? "Notifications: " + badge + " unread" : "Notifications";
		inboxButton.setToolTipText(tip);
		var label = tabLabels.get(NOTIFICATIONS);
		if (label != null) label.setToolTipText(tip);
	}

	/** Run the device sign-in (async, every step on the shared executor); on success store the token —
	 * onConfigChanged does the rest. Callbacks arrive off the EDT, so they marshal via invokeLater. */
	void startSignIn() {
		if (signInInFlight) return;
		signInInFlight = true;
		signInButton.setEnabled(false);
		setSignInStatus("Starting…");

		signIn = new DeviceSignIn(api, executor);
		signIn.run(
			line -> invokeLater(() -> setSignInStatus(line)),
			code -> invokeLater(() -> showSignInCode(code)),
			result -> invokeLater(() -> {
				signInInFlight = false;
				signInButton.setEnabled(true);
				showSignInCode("");
				if (result.outcome == DeviceSignIn.Outcome.SIGNED_IN) {
					// Storing the token fires the plugin's onConfigChanged → client reconfigure,
					// identity stamp + greet, and a sidebar refresh.
					setToken(result.token);

					// Read it straight back. A signed-in-but-empty config is the one failure mode a
					// member can't diagnose: the site says they approved, the panel keeps asking them
					// to sign in, and nothing explains why. It happens when the config write doesn't
					// take — a synced RuneLite profile clobbered by a second client, most often — and
					// it MUST NOT look like the sign-in itself failed. Explain what happened.
					var stored = configManager.getConfiguration("soulrendbingo", "playerToken");
					if (stored == null || stored.isEmpty()) {
						log.warn("Token did not persist");
						setSignInStatus("Connected, but RuneLite did not save your session. Reconnect or check RuneLite profile settings.");
					} else {
						setSignInStatus("");
						// Verify the connected website user without putting credentials in the UI.
						interactive().execute(() -> {
							var identity = api.identityForToken(result.token);
							if (identity != null && identity.username != null)
								invokeLater(() -> {
									if (result.token.equals(configManager.getConfiguration("soulrendbingo", "playerToken"))) {
										connectedWebsiteUser = identity.username;
										connectedDeviceName = identity.device != null ? identity.device.name : null;
										refreshAccountRow();
									}
								});
						});
					}
				}
				refreshSignInRow();
				refresh();
			}));
	}

	/** A small orange link that runs {@code action}. */
	static JLabel styleLink(JLabel link, String tooltip, Runnable action) {
		link.setFont(SMALL);
		link.setForeground(BRAND_ORANGE);
		link.setToolTipText(tooltip);
		return onClick(link, action);
	}

	void setToken(String token) {
		configManager.setConfiguration("soulrendbingo", "playerToken", token);
	}

	void showAccountMenu(Component anchor) {
		var menu = new JPopupMenu();
		item(menu, "Sign out…", null, this::signOut);
		menu.show(anchor, 0, anchor.getHeight());
	}

	static void item(JPopupMenu menu, String text, String tip, Runnable action) {
		var item = new JMenuItem(text);
		item.setToolTipText(tip);
		item.addActionListener(e -> action.run());
		menu.add(item);
	}

	void signOut() {
		if (showConfirmDialog(this, "Sign this computer out of Soulrend? Tracking stops until you sign in again.",
			"Soulrend", OK_CANCEL_OPTION, PLAIN_MESSAGE) != OK_OPTION) return;
		if (api != null) api.revokeToken();
		connectedWebsiteUser = null;
		connectedDeviceName = null;
		// Clearing the token fires onConfigChanged, which drops the session state (configureApiClient).
		setToken("");
		refreshSignInRow();
		refresh();
	}

	private void showSignInCode(String code) {
		signInCode.setText(code);
		signInCode.setVisible(!code.isEmpty());
		capH(signInRow).revalidate();
	}

	/** Instructions and errors shown below the approval code. */
	void setSignInStatus(String text) {
		var show = setStatus(signInStatus, text, signInRow);
		signInStatus.setToolTipText(show ? text : null);
		signInRow.repaint();
	}

	// ---- The logged-in character's account row -----------------------------------------------------

	void refreshAccountRow() {
		seasonalChanged();
		var row = data.accountRow();
		var changed = shownAccountRow == null || row == null
			|| shownAccountRow.state != row.state || !shownAccountRow.rsn.equals(row.rsn);
		shownAccountRow = row;
		// Shown whenever this computer is signed in — it is also where Sign out lives.
		accountRowPanel.setVisible(!api.needsSignIn());
		if (BingoApiClient.CANONICAL_SITE.equals(BingoApiClient.site()) &&
			(row == null || row.state == AccountRow.State.TRACKING)) {
			String website = connectedWebsiteUser == null ? "Soulrend account connected"
				: "Soulrend account: <b>" + esc(connectedWebsiteUser) + "</b>";
			String device = connectedDeviceName == null ? "RuneLite device" : connectedDeviceName;
			setAccountLabel(website, device + " is authorized to use your Soulrend website account.", false);
			accountLink.setVisible(false);
			if (changed && !accountActionInFlight) setAccountStatus("");
			redraw(capH(accountRowPanel));
			return;
		}
		if (row == null) {
			setAccountLabel(connectedWebsiteUser == null ? "Completing Soulrend connection…" :
				"Connected as <b>" + esc(connectedWebsiteUser) + "</b>" +
				(connectedDeviceName == null ? "" : " · " + esc(connectedDeviceName)),
				"Connected to Soulrend. Bingo tracking and board sync are not available yet.", false);
			accountLink.setVisible(false);
			setAccountStatus("");
			accountRowPanel.repaint();
			return;
		}
		if (changed && !accountActionInFlight) setAccountStatus("");
		var name = esc(row.rsn);
		// Everyday state (TRACKING): nothing to decide, so the action lives in the Account menu.
		String link = null;
		switch (row.state) {
			case PAUSED:
				setAccountLabel("Not tracking <b>" + name + "</b> on this computer", row.rsn + " is marked Not my account here — nothing is sent for it.", true);
				link = "Track again";
				break;
			case OTHER:
				setAccountLabel("<b>" + name + "</b> is linked to another Soulrend account", row.rsn + " is linked to another Soulrend account, so nothing is tracked for you.", true);
				link = "It's mine?";
				break;
			default:
				setAccountLabel("Tracking&nbsp;", "Tracking " + row.rsn + " for your Soulrend account.", false);
				showAccountName(row.rsn);
		}
		if (link != null) accountLink.setText(link);
		accountLink.setVisible(link != null);
		accountLink.setEnabled(!accountActionInFlight);
		redraw(capH(accountRowPanel));
	}

	void onAccountButton() {
		var row = shownAccountRow;
		if (row == null || accountActionInFlight) return;
		AccountRow.Action action;
		String question;
		switch (row.state) {
			case PAUSED:
				action = AccountRow.Action.TRACK_AGAIN;
				question = null; // undoing is harmless — no confirm
				break;
			case OTHER:
				action = AccountRow.Action.DISPUTE;
				question = "Ask Soulrend staff to move " + row.rsn + " to your Soulrend account?\n\n"
					+ "It's linked to someone else right now. Staff will check and move it if it's yours.";
				break;
			default:
				action = AccountRow.Action.NOT_MINE;
				question = "Stop tracking " + row.rsn + " on this computer?\n\n"
					+ "Use this if someone else plays it here — a sibling, a friend. It's unlinked from your "
					+ "Soulrend account and nothing is sent while it's logged in. Your other accounts aren't affected, "
					+ "and you can undo it from this panel.";
				break;
		}
		if (question != null && showConfirmDialog(this, question, "Soulrend",
			OK_CANCEL_OPTION, QUESTION_MESSAGE) != OK_OPTION) {
			return;
		}
		accountActionInFlight = true;
		accountLink.setEnabled(false);
		setAccountStatus("Working…");
		data.accountAction(action, outcome -> invokeLater(() -> {
			accountActionInFlight = false;
			refreshAccountRow();
			setAccountStatus(outcome);
			refresh();
		}));
	}

	/** The Supporter list moved: the tracked name's colours may have. Any thread. */
	void flairChanged() {
		invokeLater(this::refreshAccountRow);
	}

	/**
	 * The tracked name after "Tracking": bold, in its Supporter look when it has one (a gradient, moving
	 * for a Patron, with the star), else plain white.
	 */
	void showAccountName(String rsn) {
		var e = data.flairOf(rsn);
		// The panel is a dark surface: the owner's own dark look as-is, else the adapted main one.
		var own = e == null || e.d == null || ChatPalette.nameColor(e.d.c) == null ? null : e.d;
		var source = own != null ? own.g : e == null ? null : e.g;
		var stops = new ArrayList<Color>();
		if (source != null) for (var g : source) {
			if (stops.size() < 4) stops.add(nameColor(own != null, g));
		}
		// The tenure badge (any tier) the chat shows; else the old Patron-only star.
		var tenure = e == null || e.t == null || e.t < 1 || e.t > SoulrendPlugin.TENURE_STEPS ? 0 : e.t;
		var star = e != null && (tenure > 0 || Boolean.TRUE.equals(e.s));
		var l = stops.size() >= 2 ? new GradientLabel(stops.toArray(new Color[0]), Boolean.TRUE.equals(e == null ? null : e.s)) : new JLabel();
		l.setText(rsn);
		l.setFont(accountLabel.getFont().deriveFont(Font.BOLD));
		l.setForeground(nameColor(own != null, own != null ? own.c : e == null ? null : e.c));
		l.setToolTipText(accountLabel.getToolTipText());
		if (star) {
			l.setIconTextGap(2);
			if (tenure > 0) l.setIcon(new ImageIcon(SoulrendPlugin.tenureSprite(tenure, (stops.isEmpty() ? l.getForeground() : stops.get(0)).getRGB() & 0xffffff, -1, 16)));
			else {
				l.setIcon(new GlyphIcon(STAR, 10));
				if (l instanceof GradientLabel) ((GradientLabel) l).setTintIcon(true);
			}
		}
		accountName = l;
		accountLine.add(l);
		accountLine.revalidate();
	}

	/** A flair colour on the panel's dark surface: the owner's own dark look as-is, else the adapted main one. */
	static Color nameColor(boolean own, String c) {
		return own ? teamColor("#" + ChatPalette.nameColor(c), WHITE) : ChatPalette.readableColor(c, false, WHITE);
	}

	/** Room the label leaves for the link (and ⋯) to its right. */
	static final int ACCOUNT_LINK_ROOM_PX = 72;

	/**
	 * The everyday "Tracking X" stays one quiet line (a long name is clipped; the tooltip has it all).
	 * The rare states that ask for a decision wrap instead, so they're read in full.
	 */
	void setAccountLabel(String html, String tooltip, boolean wrap) {
		if (accountName != null) {
			accountLine.remove(accountName);
			accountName = null;
		}
		accountLabel.setText(wrap
			? wrapHtml(html, STATUS_WRAP_PX - ACCOUNT_LINK_ROOM_PX)
			: "<html><nobr>" + html + "</nobr></html>");
		accountLabel.setToolTipText(tooltip);
	}

	void setAccountStatus(String text) {
		setStatus(accountStatus, text, accountRowPanel);
	}

	/**
	 * A status line under a control, wrapped as HTML so a long line breaks instead of being clipped at
	 * the edge of a narrow sidebar, and hidden while empty. The row holding it is re-capped on every
	 * change: capped once at construction, while the line was hidden, it could never grow to fit it.
	 */
	static boolean setStatus(JLabel status, String text, JPanel row) {
		var plain = text == null ? "" : text;
		var show = !plain.isEmpty();
		status.setText(show ? wrapHtml(esc(plain), STATUS_WRAP_PX) : "");
		status.setVisible(show);
		capH(row).revalidate();
		return show;
	}


	// ---- Refresh flow -----------------------------------------------------------------------------

	/**
	 * Forget everything on screen, because it belongs to a site or an account we are no longer using.
	 *
	 * <p>Called the moment the token changes. Without it the panel kept rendering the
	 * previous clan's events, board and roster until a fetch against the NEW credentials succeeded —
	 * which is indefinitely when the new ones are wrong, so the member sits looking at a clan they
	 * just left and reasonably concludes the change didn't take.
	 */
	void clearForCredentialChange() {
		clanShown = false;
		rebuildingPicker = true;
		clanPicker.removeAllItems();
		rebuildingPicker = false;
		setPickerWanted(false); // no config for the new credentials yet, so no clans to choose between
		setSignInStatus("");
		refreshSignInRow();
		renderLoading();
	}

	/** Ask again: the clan picker and sign-in state from the config the plugin holds, the tab from the site. */
	void refresh() {
		refresh(false);
	}

	/** EDT: show or update the seasonal-sharing switch (seasonal world, setting changed, config polled). */
	void seasonalChanged() {
		var show = data.seasonalSharingOffered();
		if (show) {
			var on = data.shareSeasonal();
			seasonLabel.setText(on ? "Sharing season progress" : "Season progress not shared");
			seasonLink.setText(on ? "Turn off" : "Turn on");
		}
		if (seasonRow.isVisible() != show) seasonRow.setVisible(show);
		seasonRow.revalidate();
	}

	/**
	 * The plugin's config re-read happens off the EDT (so a fetch never holds the panel), then the
	 * picker and strip redraw from it and the Clan tab asks the site. A burst of repaints — config,
	 * hello, a login — asks the site once; a click on Refresh always asks.
	 */
	int selectedEvent;
    long nextRead;
    int retrySeconds = 30;
    String panelToken, panelCharacter;
    long generation;
    boolean reading;
    private boolean caReminderDismissed;
    private final Map<Integer, Icon> objectiveItems = BingoApiClient.capped(64, true);
    String lastBingoRenderKey;
    private final Map<JLabel, BingoApiClient.BoardTile> observedRows = new LinkedHashMap<>();
    private final Timer observedGainsTimer = new Timer(1000, e -> refreshObservedGains());

    /** Prefer the active bingo, but retain an explicit choice while it is eligible. */
    static BingoApiClient.EventSummary selectBingoEvent(List<BingoApiClient.EventSummary> events, int selected) {
        if (events == null || events.isEmpty()) return null;
        for (var event : events) if (event.id == selected) return event;
        for (var event : events) if ("active".equals(event.status)) return event;
        return events.get(0);
    }

    void readBingos(boolean manual) {
        clanPicker.setVisible(false);
        tabStrip.setVisible(false);
        String token = api.playerToken;
        String character = data.myRsn();
        boolean tokenChanged = !Objects.equals(token, panelToken);
        if (tokenChanged || !Objects.equals(character, panelCharacter)) {
            generation++;
            reading = false;
            selectedEvent = 0;
            caReminderDismissed = false;
            nextRead = 0;
            panelToken = token;
            panelCharacter = character;
            lastBingoRenderKey = null;
            observedRows.clear();
            if (tokenChanged) {
                connectedWebsiteUser = null;
                connectedDeviceName = null;
            }
            renderLoading();
        }
        refreshSignInRow();
        refreshAccountRow();
        if (api.needsSignIn()) { setContent(firstRunCard()); return; }
        if (character == null) {
            setContent(boardText("Enter the game to see bingos for your RuneScape character."));
            return;
        }
        if (api.isAccountPaused()) { setContent(boardText("Paused for this character")); return; }
        if (reading || (!manual && System.currentTimeMillis() < nextRead)) return;
        long version = ++generation;
        int selected = selectedEvent;
        reading = true;
        interactive().execute(() -> {
            var list = api.bingoEvents(token, character);
            var event = selectBingoEvent(list == null ? null : list.events, selected);
            var board = event == null ? null : api.bingoRead("/" + event.id + "/board", token, character, BingoApiClient.TeamBoard.class);
            invokeLater(() -> {
                if (generation != version) return;
                reading = false;
                if (!onScreen || !Objects.equals(token, api.playerToken) || !Objects.equals(character, data.myRsn()) || api.isAccountPaused()) return;
                var rows = column();
                var account = new JPanel(new BorderLayout(4, 0));
                account.setOpaque(false);
                var characterLabel = small("Playing as " + character, BINGO_TEXT);
                characterLabel.setToolTipText(character);
                account.add(characterLabel, CENTER);
                var sounds = flatButton("", "Bingo notification sounds", BINGO_TEXT, () ->
                    showMessageDialog(this, buildBingoSoundControls(), "Bingo notification sounds", PLAIN_MESSAGE));
                sounds.setIcon(new GlyphIcon(GEAR, 13));
                sounds.getAccessibleContext().setAccessibleName("Bingo notification sounds");
                account.add(sounds, EAST);
                rows.add(left(capH(account)));
                boolean failed = list == null || list.events == null || (event != null && board == null);
                retrySeconds = failed ? Math.min(240, retrySeconds * 2) : 30;
                nextRead = System.currentTimeMillis() + retrySeconds * 1000L;
                int resolvedEvent = event == null ? 0 : event.id;
                if (!failed && selectedEvent != resolvedEvent) {
                    caReminderDismissed = false;
                }
                selectedEvent = resolvedEvent;
                // Keep the existing Swing cards in place when polling returns unchanged data.
                // The key includes the selected board and its authorized server projection.
                String renderKey = api.gson.toJson(list) + "|" + selectedEvent + "|" + api.gson.toJson(board);
                if (!manual && Objects.equals(renderKey, lastBingoRenderKey)) return;
                lastBingoRenderKey = renderKey;
                observedRows.clear();
                if (list == null || list.events == null) {
                    rows.add(boardText(api.lastBingoHttpStatus == 401
                        ? "Soulrend session expired or revoked. Sign out and connect again."
                        : "Unable to load bingos — check your connection or reconnect to Soulrend"));
                }
                else {
                    connectedWebsiteUser = list.username;
                    refreshAccountRow();
                    if (list.events.isEmpty()) {
                        rows.add(boardText(list.rename_check_available
                            ? "Character not verified yet. If you changed your name, approve it on Wise Old Man. Soulrend will check again automatically."
                            : "No eligible bingos for this character."));
                    }
                    for (var e : list.events) {
                        if (e.id == selectedEvent) {
                            var hero = bingoCard(BINGO_GOLD);
                            hero.add(boardText("active".equals(e.status) ? "LIVE BINGO" : "UPCOMING BINGO", SMALL, BINGO_GOLD));
                            hero.add(boardText(e.name, BOLD, WHITE));
                            if (e.team != null) hero.add(boardText(e.team, SMALL, BINGO_TEXT));
                            rows.add(hero);
                        } else {
                            rows.add(fullWidthButton(Ui.plainText(e.name),
                                "active".equals(e.status) ? "Switch to live bingo" : "View upcoming bingo", () -> {
                                    if (reading) return;
                                    selectedEvent = e.id;
                                    caReminderDismissed = false;
                                    observedRows.clear();
                                    renderLoading();
                                    readBingos(true);
                                }));
                        }
                    }
                    if (event != null) {
                        rows.add(Box.createVerticalStrut(6));
                        if (board == null) rows.add(boardText("Board unavailable"));
                        else {
                            if (board.progress != null || board.captain != null) {
                                var overview = bingoCard(BINGO_PURPLE);
                                if (board.progress != null) {
                                    var progress = board.progress;
                                    overview.add(boardText("My Objectives", BOLD, WHITE));
                                    if (progress.main_total != null && progress.main_completed != null
                                        && progress.main_total > 0) {
                                        overview.add(boardText(progress.main_completed + " / " + progress.main_total
                                            + " main objectives cleared", SMALL, BINGO_TEXT));
                                        overview.add(bingoMeter(progress.main_completed, progress.main_total, BINGO_GREEN));
                                    } else {
                                        overview.add(boardText(progress.completed + " cleared · "
                                            + progress.visible + " listed", SMALL, BINGO_TEXT));
                                    }
                                    overview.add(boardText(Math.max(0, progress.visible - progress.completed)
                                        + " listed, not cleared", SMALL, BINGO_TEXT));
                                }
                                if (board.captain != null) overview.add(boardText("Captain: " + board.captain, SMALL, BINGO_TEXT));
                                rows.add(overview);
                            }
                            if (board.tiles != null) {
                                var available = board.tiles.stream()
                                    .filter(SoulrendSidebarPanel::visibleBingoTile)
                                    .collect(java.util.stream.Collectors.toList());
                                rows.add(gap(8));
                                if (available.isEmpty()) rows.add(boardText("No objectives available yet"));
                                if (available.stream().anyMatch(SoulrendSidebarPanel::visibleCombatAchievement)) {
                                    rows.add(combatAchievementReminder(available.stream()
                                        .anyMatch(tile -> visibleCombatAchievement(tile) && tile.allow_repeat)));
                                }
                                boolean linked = "active".equals(event.status) && character != null
                                    && event.characters != null && event.characters.stream().anyMatch(rsn -> Rsn.same(rsn, character));
                                for (var tile : available) {
                                    rows.add(Box.createVerticalStrut(4));
                                    var accent = tile.credited ? BINGO_GREEN : "pending".equals(tile.status) ? BINGO_GOLD
                                        : "rejected".equals(tile.status) ? BINGO_RED : BINGO_PURPLE;
                                    var card = bingoCard(accent);
                                    card.add(objectiveHeading(tile));
                                    String status = tile.credited ? "Completed" : "pending".equals(tile.status) ? "Pending approval"
                                        : "rejected".equals(tile.status) ? "Rejected"
                                        : tile.value_amount != null && tile.value_amount > 0 ? "In progress" : "Available";
                                    card.add(boardText(status, SMALL, accent));
                                    String scope = "solo".equals(tile.scope) ? "Solo" : "Team";
                                    card.add(boardText(tile.target == null ? scope
                                        : scope + " · " + tile.value + " / " + tile.target, SMALL, BINGO_TEXT));
                                    if (tile.target != null) {
                                        if (tile.value_amount != null && tile.target_amount != null) {
                                            card.add(bingoMeter(tile.value_amount, tile.target_amount, accent));
                                        }
                                    }
                                    card.add(left(flatButton("Details", tile.name, BINGO_TEXT,
                                        () -> showObjectiveDetails(tile, status, scope))));
                                    rows.add(card);
                                    if (linked && tile.metric != null && ("skill_xp".equals(tile.objective_type)
                                        || "boss_kc".equals(tile.objective_type))) {
                                        JLabel observed = left(small("", MUTED));
                                        observed.setBorder(createEmptyBorder(2, 8, 2, 8));
                                        observed.setToolTipText("Observed on this client this session. Official credit comes from Wise Old Man.");
                                        observed.setVisible(false);
                                        observedRows.put(observed, tile);
                                        card.add(observed);
                                    }
                                }
                            }
                        }
                        rows.add(gap(10));
                        rows.add(fullWidthButton("Open event on website", "Open the full event, standings and administration on Soulrend",
                            () -> LinkBrowser.browse(BingoApiClient.CANONICAL_SITE + "/events/" + event.id + "/")));
                    }
                }
                refreshObservedGains();
                setContent(rows);
            });
        });
    }

    private static boolean visibleBingoTile(BingoApiClient.BoardTile tile) {
        return tile.name != null && !"hidden".equals(tile.status) && !"locked".equals(tile.status);
    }

    static boolean visibleCombatAchievement(BingoApiClient.BoardTile tile) {
        return visibleBingoTile(tile) && "ca".equals(tile.objective_type);
    }

    static final String CA_REPEAT_GUIDANCE = "Already completed this Combat Achievement? "
        + "In OSRS, open Settings (wrench), search for Repeat completion and enable the "
        + "Combat Achievement notifications. Attempt the task again while Soulrend is connected.\n\n"
        + "The objective must allow repeats and the game must send a completion message. "
        + "Some tasks may send no repeat message. Only new attempts can count, and the website handles approval.";

    private JPanel combatAchievementReminder(boolean allowsRepeats) {
        var reminder = bingoCard(BINGO_GOLD);
        var info = flatButton("Combat Achievement info", "Open OSRS repeat-completion instructions", BINGO_GOLD,
            () -> showBingoDetails("Combat Achievements", CA_REPEAT_GUIDANCE));
        info.setIcon(new GlyphIcon(STAR, 12, BINGO_GOLD));
        reminder.add(left(info));
        var setting = boardText("Repeat notifications: Not verified", SMALL, BINGO_GOLD);
        setting.setVisible(!caReminderDismissed);
        reminder.add(setting);
        var dismiss = new JButton("Got it");
        styleFlatButton(dismiss, BINGO_TEXT);
        dismiss.addActionListener(e -> {
            caReminderDismissed = true;
            setting.setVisible(false);
            dismiss.setVisible(false);
            redraw(reminder);
        });
        dismiss.setVisible(!caReminderDismissed);
        reminder.add(left(dismiss));
        if (allowsRepeats) info.setToolTipText("A visible objective allows repeat completions. Open OSRS setup instructions.");
        return reminder;
    }

    private JComponent objectiveHeading(BingoApiClient.BoardTile tile) {
        var header = new JPanel(new BorderLayout(5, 0));
        header.setOpaque(false);
        var icon = new JLabel(new GlyphIcon(tile.credited ? CHECK : STAR, 15, BINGO_GOLD));
        icon.setPreferredSize(new Dimension(27, 25));
        header.add(icon, WEST);
        if (tile.item_id > 0 && clanView.items != null) {
            var cached = objectiveItems.get(tile.item_id);
            if (cached != null) icon.setIcon(cached);
            else {
                var item = clanView.items.getImage(tile.item_id);
                item.onLoaded(() -> invokeLater(() -> {
                    var scaled = objectiveItems.computeIfAbsent(tile.item_id, id ->
                        new ImageIcon(item.getScaledInstance(24, -1, Image.SCALE_SMOOTH)));
                    icon.setIcon(scaled);
                }));
            }
        } else if ("skill_xp".equals(tile.objective_type) && tile.metric != null && clanView.skills != null) {
            try {
                var skill = net.runelite.api.Skill.valueOf(tile.metric.toUpperCase(Locale.ROOT));
                icon.setIcon(new ImageIcon(clanView.skills.getSkillImage(skill, true)));
            } catch (IllegalArgumentException ignored) {
                // Unknown metrics retain the default icon.
            }
        } else if (BingoApiClient.isBingoIconPath(tile.icon_path)) {
            uiHost().image(tile.icon_path, 24, 24, false, loaded -> {
                if (loaded != null) icon.setIcon(loaded);
            });
        }
        var title = new ClipLabel(wrapHtml(esc(fontSafe(tile.name)), STATUS_WRAP_PX - 44));
        title.setFont(BOLD);
        title.setForeground(WHITE);
        title.setVerticalAlignment(SwingConstants.CENTER);
        title.setToolTipText(wrapHtml(esc(tile.name), STATUS_WRAP_PX));
        header.add(title, CENTER);
        return left(capH(header));
    }

    private void showObjectiveDetails(BingoApiClient.BoardTile tile, String status, String scope) {
        String text = status + " · " + scope;
        if (tile.target != null) text += "\n" + tile.value + " / " + tile.target;
        if (tile.description != null && !tile.description.isBlank()) text += "\n\n" + tile.description;
        if (visibleCombatAchievement(tile)) {
            text += tile.allow_repeat ? "\n\nThis objective allows repeat completions."
                : "\n\nThis objective requires a first-time completion.";
        }
        showBingoDetails(tile.name, text);
    }

    private void showBingoDetails(String title, String text) {
        var details = new JTextArea(text, 7, 25);
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setCaretPosition(0);
        showMessageDialog(this, new JScrollPane(details), title, INFORMATION_MESSAGE);
    }

    private static final Color BINGO_PURPLE = new Color(0xa981ed);
    private static final Color BINGO_GOLD = new Color(0xd9ae65);
    private static final Color BINGO_GREEN = new Color(0xa0e7bc);
    private static final Color BINGO_RED = new Color(0xf09192);
    private static final Color BINGO_TEXT = new Color(0xd7caeb);

    static JPanel bingoCard(Color accent) {
        // Rows and session-progress labels are added after construction. Measure the card
        // when its parent lays it out, so a once-empty card cannot clip its own contents.
        var card = new JPanel() {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(true);
        card.setBackground(new Color(0x20172c));
        card.setBorder(createCompoundBorder(createMatteBorder(1, 3, 1, 1, accent),
            createEmptyBorder(7, 8, 7, 8)));
        return left(card);
    }

    private static JProgressBar bingoMeter(long value, long target, Color accent) {
        var meter = new JProgressBar(0, 100);
        meter.setValue(target > 0 ? (int) Math.min(100, Math.max(0, 100.0 * value / target)) : 0);
        meter.setForeground(accent);
        meter.setBackground(new Color(0x392a4a));
        meter.setBorderPainted(false);
        meter.setPreferredSize(new Dimension(0, 5));
        meter.getAccessibleContext().setAccessibleName(value + " of " + target);
        return left(capH(meter));
    }

    private void refreshObservedGains() {
        if (tab != CLAN || observedRows.isEmpty()) return;
        String token = api.playerToken;
        String character = data.myRsn();
        var gains = Objects.equals(token, panelToken) && Objects.equals(character, panelCharacter) && !api.isAccountPaused()
            ? data.sessionGains(character, token) : Map.<String, Long>of();
        observedRows.forEach((label, tile) -> {
            long gain = gains.getOrDefault(tile.objective_type + ":" + tile.metric, 0L);
            String text = gain > 0 ? "Observed: +" + gain + ("skill_xp".equals(tile.objective_type) ? " XP" : " KC") + " this session" : "";
            if (!Objects.equals(text, label.getText())) label.setText(text);
            if (label.isVisible() != (gain > 0)) label.setVisible(gain > 0);
        });
    }

    private static JLabel boardText(String text) {
        return boardText(text, SMALL, MUTED);
    }

    static JLabel boardText(String text, Font font, Color color) {
        var label = new ClipLabel(wrapHtml(esc(fontSafe(text)), STATUS_WRAP_PX));
        label.setFont(font);
        label.setForeground(color);
        return left(label);
    }

    void refresh(boolean manual) {
        if (onScreen && BingoApiClient.CANONICAL_SITE.equals(BingoApiClient.site())) {
            readBingos(manual);
            return;
        }
		if (!onScreen) return;
		refreshAccountRow();
		if (manual) {
			api.clearThumbs();
			for (var v : views) v.invalidateDrawn();
			// A refresh with nothing new changes nothing on screen: the button says it's working.
			refreshing(true);
		}
		interactive().execute(() -> {
			data.refreshConfig(manual);
			invokeLater(() -> {
				if (manual) refreshing(false);
				rebuildClanPicker();
				updateTabStrip();
				refreshSignInRow();
				refreshAccountRow();
				if (tab == YOU) {
					renderYou(false); // a refresh can change what You shows: the character, the card
					// Same character: ask the site again in place, or a new banner/avatar only showed
					// after leaving the tab and coming back.
					if (manual) youView.refresh();
				} else if (tab == LOOKUP && manual) {
					lookupView.refresh();
				} else if (tab == SETTINGS) {
					renderSettings(); // admin rights and the sound list can change under it
				} else if (tab == FEED && manual && !feedView.refresh()) {
					// The Feed asks the site again in place (its scope, pane and filter kept): blanking it to
					// "Loading the feed…" first collapsed the tab, then it sprang back.
					renderFeed();
				}
				showClan(manual);
			});
		});
	}

	void refreshing(boolean on) {
		refreshButton.setEnabled(!on);
		refreshButton.setText(on ? "Refreshing…" : "Refresh");
	}

	/** The Clan tab: the sign-in card, a line, or the site's view of the clan. EDT. */
	void showClan(boolean manual) {
		if (api.needsSignIn() || !data.supportsPluginUi()) {
			clanShown = false;
			if (data.awaitingFirstConfig()) {
				renderLoading();
			} else renderEmpty();
			return;
		}
		var now = System.currentTimeMillis();
		if (!clanShown) {
			clanShown = true;
			clanAskedAt = now;
			setContent(clanView);
			clanView.show("home", emptyMap(), "Loading progress…");
		} else if (manual || now - clanAskedAt >= MIN_ASK_GAP_MS && !typingInSearch()) {
			clanAskedAt = now;
			clanView.refresh();
		}
	}

	/** The least time between the Clan tab's own asks, Refresh aside. */
	static final long MIN_ASK_GAP_MS = 5_000L;

	/** Somebody is in the tiles or Teams search: don't ask again under their caret. */
	boolean typingInSearch() {
		var w = SwingUtilities.getWindowAncestor(clanView);
		var f = w == null ? null : w.getFocusOwner();
		return f instanceof JTextField && SwingUtilities.isDescendingFrom(f, clanView);
	}

	/** The site confirmed a tile done: ask now, so a tile's detail doesn't say "Open" after the chat said done. */
	void tileCompleted() {
		// The whole page: a race's road and "Up next" are on it, not in a slot a plain refresh would pick.
		askAgainNow(true);
		refresh();
	}

	/**
	 * EDT: the open Clan view asks the site again right away, past the 5s gap and the search-box guard:
	 * a reload keeps what's typed, and after a completion or our own proof a detail left saying "Open"
	 * reads as broken. Remembered answers are dropped first so none is redrawn on the way.
	 */
	void askAgainNow(boolean whole) {
		uiCache.clear();
		clanAskedAt = System.currentTimeMillis();
		if (onScreen && clanShown) clanView.refresh(whole);
	}

	@Setter BooleanSupplier pushLive = () -> false;
	int quietTicks;

	/** The live push says the board moved: ask now. */
	void boardChanged() {
		// Every remembered view may hold the old numbers: never redraw one of those first.
		invokeLater(() -> {
			askAgainNow(false);
			refresh();
		});
	}

	final AtomicBoolean liveQueued = new AtomicBoolean();
	/** After counts alone moved, the rest of the view (who's on what, the feed) is asked for this much later. */
	static final int COUNTS_CATCH_UP_MS = 15_000;
	final Timer countsCatchUp = new Timer(COUNTS_CATCH_UP_MS, e -> boardChanged());

	/** Counts moved here (own progress, the push): the Clan tab's live ones redraw, at most once per frame. Any thread. */
	void liveCountsMoved() {
		if (liveQueued.compareAndSet(false, true)) {
			invokeLater(() -> {
				liveQueued.set(false);
				clanView.tickLive();
			});
		}
	}

	/** The push carried counts only: they're on screen already; the rest catches up once, a little later. */
	void countsChanged() {
		invokeLater(() -> {
			uiCache.clear(); // see boardChanged
			if (!countsCatchUp.isRunning()) {
				countsCatchUp.setRepeats(false);
				countsCatchUp.start();
			}
		});
	}

	void tickStartProof() {
		if (startProofTick != null) startProofTick.run();
	}

	/**
	 * Repopulate the clan dropdown, suppressing the selection-event storm a setModel provokes.
	 *
	 * <p>Fed from the site's list of this person's seats, not from the boards we happen to have loaded:
	 * the point of the control is to reach a clan whose board we have NOT loaded. Hidden below two
	 * entries, because one option that cannot be changed is not a choice.</p>
	 *
	 * <p>There used to be a rule here that landed a guest on their own clan instead. It is gone
	 * deliberately, not lost: the server applies the stronger version of it when it resolves a clanless
	 * request — a live event first, then a real membership before any guest seat — and two copies of a
	 * default that can disagree is worse than one copy that decides.</p>
	 */
	void rebuildClanPicker() {
		var choices = ClanChoice.of(data.clans());
		rebuildingPicker = true;
		try {
			var model = new DefaultComboBoxModel<ClanChoice>();
			var chosen = data.chosenClan();
			ClanChoice toSelect = null;
			for (var c : choices) {
				model.addElement(c);
				if (c.slug.equals(chosen)) toSelect = c;
			}
			clanPicker.setModel(model);
			// Nothing chosen — the site is deciding, which is the Auto row.
			clanPicker.setSelectedItem(toSelect != null ? toSelect : choices.isEmpty() ? null : choices.get(0));
			setPickerWanted(choices.size() > 2);
		} finally {
			rebuildingPicker = false;
		}
	}

	/**
	 * One row of the clan dropdown: a clan to narrow to, or all of them.
	 *
	 * "All clans" is the DEFAULT and it is a real row, not an implied initial state, because it is
	 * also the only way back. A member who narrows to a clan that later goes quiet would otherwise be
	 * stuck watching a dead board while their live one runs somewhere else, with nothing in the UI
	 * admitting it.
	 *
	 * On "All clans" the site decides which board the plugin addresses — live event first — and the
	 * rest appear under "Also live". Choosing a clan narrows to it and moves where submissions file,
	 * which is a bigger deal than a filter usually is; the rows say so.
	 */
	static final class ClanChoice {
		/** Clan slug, or "" for Auto. */
		final String slug;
		final String label;
		/** Second line — the board running there, or what this seat is. Never null. */
		final String detail;

		ClanChoice(String slug, String label, String detail) {
			this.slug = slug == null ? "" : slug;
			this.label = label == null ? "" : label;
			this.detail = detail == null ? "" : detail;
		}

		/** Auto first, then the clans in the order the site sent them (newest seat first). */
		static List<ClanChoice> of(List<PluginConfigResponse.ClanRef> clans) {
			var out = new ArrayList<ClanChoice>();
			if (clans == null || clans.isEmpty()) return out;
			var anySpace = false;
			var anyClan = false;
			for (var c : clans) {
				if (c == null) continue;
				anySpace |= c.space;
				anyClan |= !c.space;
				if (c.slug != null && !c.slug.isEmpty()) out.add(new ClanChoice(c.slug, c.name == null || c.name.isEmpty() ? c.slug : c.name, detailOf(c)));
			}
			// The Auto row, first, names what it covers: clans, spaces, or both.
			var all = anySpace && anyClan ? "All clans & spaces" : anySpace ? "All spaces" : "All clans";
			out.add(0, new ClanChoice("", all, "Everything you're playing"));
			return out;
		}

		/** What is happening in this clan, in one short line — the reason to pick it or not. */
		static String detailOf(PluginConfigResponse.ClanRef c) {
			// A space says so up front: in a list of clans and spaces, the name alone doesn't tell them apart.
			var tag = c.space ? "Space \u00b7 " : "guest".equals(c.kind) ? "Guest \u00b7 " : "";
			if (c.live == null || c.live.eventName == null || c.live.eventName.isEmpty()) return tag + (tag.isEmpty() ? "Nothing live" : "nothing live");
			// A competition has a leaderboard rather than a board to fill, so "0/0" would be a lie
			// dressed as progress. The name is the whole answer there.
			var head = c.live.isWeekly() || c.live.tilesTotal <= 0
				? c.live.eventName
				: c.live.eventName + "  " + c.live.tilesComplete + "/" + c.live.tilesTotal;
			// One line names ONE of them. Say how many were not named rather than letting an arbitrary
			// pick stand in for the whole clan — the number is why you would open the clan at all.
			int more = c.liveCount - 1;
			return (c.space ? "Space \u00b7 " : "") + (more > 0 ? head + "  +" + more + " more" : head);
		}

		public String toString() {
			return label;
		}
	}


	// ---- Render states ----------------------------------------------------------------------------

	void renderLoading() {
		// The picker STAYS. Switching clan renders this state on purpose, and yanking the control the
		// member is holding — then putting it back a second later — reads as the click having failed.
		var loading = new ClipLabel("Loading progress…", SwingConstants.CENTER);
		loading.setForeground(MUTED);
		loading.setBorder(createEmptyBorder(24, 0, 0, 0));
		setContent(loading);
	}

	void renderEmpty() {
		// TWO DIFFERENT NOTHINGS, and they were being told the same story. "Link a clan on your Soulrend
		// site" is advice for somebody who HAS a site and an account; a fresh install has neither, and
		// reading it as their first contact with the plugin leaves them looking for a clan page nobody
		// has given them the address of. Signed out is the state to answer first, because it is the
		// state every new install starts in.
		if (api.needsSignIn()) {
			setContent(firstRunCard());
			return;
		}
		// Signed in, but no config answered: the site can't be reached yet. Say that rather than
		// "no clans", and keep the roster button and the sound lists, since the tab strip is hidden here.
		var empty = new PluginErrorPanel();
		empty.setContent("Can't show your clan here", "Soulrend can't be reached right now. It'll keep trying.");
		renderSettings();
		var stack = new JPanel();
		stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
		stack.setOpaque(false);
		addAll(stack, empty, settingsPanel);
		setContent(stack);
	}

	/**
	 * What a fresh install sees: what this is, the one button that connects it, and where that goes.
	 *
	 * <p>The sign-in button itself lives in the header (it is shown in exactly this state), so this
	 * points at it rather than repeating it. What it adds is the half a stranger cannot guess — that
	 * the account is free, that it is theirs rather than their clan's, and that a person with no clan
	 * still gets something out of it. The link is the only address here, and it is the same one
	 * pressing Sign in would write.</p>
	 */
	JPanel firstRunCard() {
		// A column that opens with a small gold section label (the panel's header style) and the gap under it.
		var card = column();
		addAll(card, small("New here?", BRAND_ORANGE), gap(6));
		card.setBorder(createEmptyBorder(8, 0, 0, 0));
		card.add(note("Connect your Soulrend account to see your bingo events, team progress and notifications in RuneLite."));
		card.add(gap(6));
		card.add(note("You don't need a clan: your profile is your own. If you run one, starting it"
			+ " there is free too."));
		var site = styleLink(new ClipLabel(BingoApiClient.site().replaceFirst("^https?://", "")), "Open Soulrend in your browser",
			() -> LinkBrowser.browse(BingoApiClient.site()));
		site.setIcon(new GlyphIcon(EXTERNAL, 11));
		site.setHorizontalTextPosition(SwingConstants.LEADING);
		site.setBorder(createEmptyBorder(6, 0, 0, 0));
		card.add(site);

		return card;
	}

	/** A wrapped grey paragraph at the panel's width — long copy clips rather than wraps without it. */
	static JLabel note(String text) {
		var label = new ClipLabel(wrapHtml(esc(text), STATUS_WRAP_PX));
		label.setFont(SMALL);
		label.setForeground(MUTED);
		return label;
	}

	/**

	// ---- Events list (a clan running more than one thing) ------------------------------------------

	/**
	 * Roster sync and profile sync, when this account can do them here.
	 *
	 * <p>A roster is scraped from the clan channel you're standing in, so the button is absent for
	 * any clan but your own — an admin elsewhere still can't see a roster they aren't in — and it
	 * says why when the clan channel is unavailable.
	 */
	JPanel buildPanelActions(PanelActions actions) {
		var panel = column();
		// Nothing this account can do here: no empty card either.
		if (!actions.canSyncRoster && actions.rosterNote == null) return panel;
		panel.add(cardTitle("Syncing"));
		if (actions.canSyncRoster) {
			addAll(panel, fullWidthButton("Sync clan roster", "Push the in-game clan member list to the site", data::syncRoster), gap(4), note("Clan admins: push the in-game member list to the site", 165));
		} else if (actions.rosterNote != null) panel.add(note(actions.rosterNote, 165));
		return panel;
	}

	/** Muted small text that wraps at {@code widthPt} rather than clipping. */
	static JLabel note(String text, int widthPt) {
		var l = new JLabel(wrapHtml(esc(text), widthPt));
		l.setFont(SMALL);
		l.setForeground(MUTED);
		return left(l);
	}

	/** A card's white bold title and the gap under it. */
	static JPanel cardTitle(String text) {
		var p = column();
		addAll(p, left(bold(text)), gap(6));
		return capH(p);
	}

	/** Small white bold text. */
	static JLabel bold(String text) {
		return label(text, SMALL.deriveFont(Font.BOLD), WHITE);
	}

	/**
	 * Banner clips, which are files in a folder on THIS machine.
	 *
	 * <p>Nothing here is per account or per clan — swapping character or clan doesn't change which
	 * .wav files are on your disk — so the list is the same wherever you are in the panel. A lit
	 * swatch is in the play cycle, a hollow one is muted, and clicking the row flips it.
	 */
	private JPanel buildBingoSoundControls() {
		var panel = column();
		// Read the same typed configuration as BannerSoundService and RuneLite's settings.
		SoulrendConfig settings = configManager == null ? new SoulrendConfig() {}
			: configManager.getConfig(SoulrendConfig.class);
		var volume = new JSlider(0, 100, Math.max(0, Math.min(100, settings.bannerSoundVolume())));
		volume.setOpaque(false);
		volume.getAccessibleContext().setAccessibleName("Bingo notification volume");
		var volumeLabel = small("Volume " + volume.getValue() + "%", WHITE);
		volumeLabel.setLabelFor(volume);
		volume.addChangeListener(event -> {
			int value = volume.getValue();
			volumeLabel.setText("Volume " + value + "%");
			if (!volume.getValueIsAdjusting() && configManager != null) {
				configManager.setConfiguration("soulrendbingo", "bannerSoundVolume", value);
			}
		});
		panel.add(volumeLabel);
		panel.add(capH(volume));
		panel.add(note("Mute individual cues in RuneLite's Soulrend plugin settings.", 158));
		panel.add(gap(5));
		panel.add(fullWidth(flatButton("Preview: My progress", "Play Retro Game Notification at my configured volume",
			WHITE, () -> data.previewBingoCue(BannerSoundService.BingoCue.SELF_PROGRESS))));
		panel.add(gap(3));
		panel.add(fullWidth(flatButton("Preview: Teammate progress", "Play Happy Bell at my configured volume",
			WHITE, () -> data.previewBingoCue(BannerSoundService.BingoCue.TEAM_PROGRESS))));
		panel.add(gap(3));
		panel.add(fullWidth(flatButton("Preview: BINGOOOOO!", "Play Small Win at my configured volume",
			WHITE, () -> data.previewBingoCue(BannerSoundService.BingoCue.COMPLETION))));
		panel.add(gap(3));
		panel.add(fullWidth(flatButton("Preview: Team bingo complete", "Play the team victory sound at my configured volume",
			WHITE, () -> data.previewBingoCue(BannerSoundService.BingoCue.TEAM_BINGO_COMPLETE))));
		return capH(panel);
	}

	JPanel buildBannerSounds() {
		var clips = data.sounds(BANNER);
		var panel = column();
		panel.add(cardTitle("Bingo notification sounds"));
		panel.add(buildBingoSoundControls());
		panel.add(gap(8));
		panel.add(cardTitle("Custom banner sounds"));

		if (clips.isEmpty()) {
			addAll(panel, note("None added — use Add clip, or the banner folder.", 165), gap(4));
		} else {
			for (var clip : clips.subList(0, Math.min(clips.size(), BANNER_CLIPS_SHOWN))) {
				addAll(panel, buildClipRow(clip, data.bannerSoundOn(clip)), gap(2));
			}
			if (clips.size() > BANNER_CLIPS_SHOWN) {
				var more = small("+" + (clips.size() - BANNER_CLIPS_SHOWN) + " more in the folder", MUTED);
				more.setBorder(createEmptyBorder(2, CLIP_TEXT_INSET, 0, 0));
				panel.add(more);
			}
			panel.add(gap(4));
		}

		// Two buttons on one line, equal halves.
		panel.add(buttons(new GridLayout(1, 2, 4, 0),
			flatButton("Add clip", "Pick .wav files to copy into the banner sounds folder", WHITE,
				() -> data.importSounds(BANNER)),
			flatButton("Copy folder", "Copy the sounds folder's path — paste it into your file manager to rename or delete clips",
				WHITE, data::copyBannerSoundsPath)));

		// MISSIONS, stated rather than configured. A mission dropping should not sound like a tile
		// being finished, and what it sounds like is decided by whether this folder has anything in
		// it — so the panel says which of the two is happening, and the button is how you change it.
		panel.add(gap(8));
		var missions = data.sounds(MISSION).size();
		panel.add(small(missions == 0
			? "Missions: a game sound (no clip added)"
			: "Missions: " + missions + (missions == 1 ? " clip" : " clips"), MUTED));
		panel.add(gap(4));
		panel.add(fullWidth(flatButton(missions == 0 ? "Add mission clip" : "Add mission clips",
			"Pick .wav files to play when a mission drops, instead of the game sound used by default",
			WHITE, () -> data.importSounds(MISSION))));
		return panel;
	}

	/** How far a clip's name sits from the panel edge — the swatch's width plus its gap. */
	static final int CLIP_TEXT_INSET = 14;

	/**
	 * One clip: a lit swatch, its name, and a click that mutes or unmutes it.
	 *
	 * <p>Colour alone carried this before — a green name meant playing, a grey one meant muted — which
	 * is invisible if you don't already know the rule, and unreadable if you can't separate the two
	 * greens. The swatch says on/off the way the in-game list does, the row lights under the pointer
	 * so it's clearly a control, and the tooltip names the action rather than the state.
	 */
	JPanel buildClipRow(String clip, boolean on) {
		var display = BannerSoundService.displayName(clip);

		var row = left(new JPanel(new BorderLayout(6, 0)));
		row.setBorder(createEmptyBorder(2, 0, 2, 4));
		row.setToolTipText(plainText(display) + (on ? " — click to mute" : " — muted; click to unmute"));

		var swatch = new JPanel();
		var size = new Dimension(8, 8); // one for all three: the getters hand out copies
		swatch.setPreferredSize(size);
		swatch.setMinimumSize(size);
		swatch.setMaximumSize(size);
		swatch.setBackground(on ? PROGRESS_COMPLETE_COLOR : DARK_GRAY_COLOR);
		swatch.setBorder(createLineBorder(on
			? PROGRESS_COMPLETE_COLOR : WIDGET_BORDER));
		// Centre the 8px square against the text line rather than stretching it down the row.
		var swatchBox = new JPanel(new GridBagLayout());
		swatchBox.setOpaque(false);
		swatchBox.setPreferredSize(new Dimension(CLIP_TEXT_INSET - 6, 12));
		swatchBox.add(swatch);

		row.add(swatchBox, WEST);
		row.add(small(display.length() <= 26 ? display : display.substring(0, 25).trim() + "…", on ? TEXT_COLOR : MUTED), CENTER);
		hoverable(capH(row), DARKER_GRAY_COLOR, () -> {
			data.toggleBannerSound(clip);
			renderSettings();
		});
		return row;
	}

	// ---- Component builders -----------------------------------------------------------------------

	/**
	 * The starting-shot prompt: where to stand, this account's keyword, and the one button that
	 * captures the frame, burns the proof banner onto it and files it. Rendered only while a shot is
	 * actually owed — {@link SoulrendSidebarDataSource#startProof()} returns null the moment one is filed.
	 */
	JPanel buildStartProofCard(PluginConfigResponse.StartProof proof) {
		var card = block();
		card.setBorder(createCompoundBorder(
			createLineBorder(BRAND_ORANGE),
			createEmptyBorder(6, 8, 8, 8)));
		card.add(small("Starting shot needed", BRAND_ORANGE));

		if (proof.location != null && !proof.location.isEmpty()) {
			var where = small("Go to " + proof.location, WHITE);
			where.setToolTipText(plainText(proof.location));
			addAll(card, gap(4), where);
		}

		if (proof.keyword != null && !proof.keyword.isEmpty()) {
			addAll(card, gap(2), small("Keyword: " + proof.keyword, MUTED));
		}

		// The requirements the button checks — session age, position — shown live, so a player can
		// see each one go green instead of finding out from a refusal in chat. Ticks every second
		// while the card is on screen, on the panel's own second tick; dropped once the card is replaced.
		var checks = column();
		checks.setOpaque(false);
		renderStartProofChecks(checks, proof);
		if (checks.getComponentCount() > 0) {
			addAll(card, gap(4), checks);
			startProofTick = () -> {
				if (checks.isDisplayable()) {
					renderStartProofChecks(checks, proof);
				} else startProofTick = null;
			};
		}

		if ("rejected".equals(proof.status)) {
			addAll(card, gap(2), small("Your last shot was rejected — take another.", PROGRESS_ERROR_COLOR));
		}

		// WHY it matters, which the card never said. A player who reads "starting shot needed" and
		// carries on has no way to know their drops are landing in a review queue meanwhile.
		addAll(card, gap(4), small("Drops you send now are held for review until this is filed.", PROGRESS_ERROR_COLOR));

		var left = StartProofRules.describeWindow(proof, System.currentTimeMillis());
		if (left != null) {
			var expires = small("Asked for another " + left + ", then it lapses", WHITE);
			expires.setToolTipText("Six hours in, the game has logged everyone out anyway, so the shot stops being asked for.");
			addAll(card, gap(2), expires);
		}

		if (proof.spot != null && data.autoStartShot()) {
			addAll(card, gap(2), small("Taken and sent automatically once both are green.", LIGHT_GRAY_COLOR));

		}

		var take = new JButton("Take starting shot");
		styleFlatButton(take, BRAND_ORANGE);
		take.addActionListener(e -> {
			take.setEnabled(false);
			take.setText("Sending...");
			// The capture itself hops to the next rendered frame and then to a worker; the panel just
			// asks for it. The button goes away entirely on the next poll, once the site agrees.
			data.captureStartProof();
			// ...unless the capture refused it — standing in the wrong place, or a session too old to
			// have flushed the hiscores. That answer arrives in chat, so the button has to come back
			// rather than sit on "Sending..." until the next poll redraws the card.
			UiView.once(4000, ev -> {
				take.setText("Take starting shot");
				take.setEnabled(true);
			});
		});
		addAll(card, gap(6), take);

		if (proof.spot != null) {
			card.add(gap(4));
			card.add(fullWidthButton("Route me there", "Draws the way there with the Shortest Path plugin, if you have it",
				data::routeToStartSpot));
		}

		return card;
	}

	void renderStartProofChecks(JPanel into, PluginConfigResponse.StartProof proof) {
		var list = data.startProofChecks(proof);
		// Ticked every second, but the checks only move when you log in or walk to the spot: rebuild
		// only when what they say changes.
		var said = new StringBuilder();
		for (var c : list) said.append(c.state).append('|').append(c.text).append('\n');
		var key = said.toString();
		if (key.equals(into.getClientProperty(START_PROOF_SHOWN))) return;
		into.putClientProperty(START_PROOF_SHOWN, key);
		into.removeAll();
		for (var c : list) {
			var ok = c.state == StartProofRules.Check.State.OK;
			var color = ok ? PROGRESS_COMPLETE_COLOR : c.state == StartProofRules.Check.State.BAD ? PROGRESS_ERROR_COLOR : LIGHT_GRAY_COLOR;
			var shape = ok ? CHECK : CLOSE;
			var row = small(c.text, color);
			row.setIcon(new GlyphIcon(shape, 9, color));
			row.setIconTextGap(4);
			row.setToolTipText("Hiscores only save when you log out, so a fresh session is what sets your starting totals.");
			into.add(row);
		}
		redraw(into);
	}

	// ---- Tiles and Teams pages --------------------------------------------------------------------

	/** Install one row listener on the panel and every label nested inside it. */
	static void addMouseListenerToTree(Component component, MouseAdapter listener) {
		component.addMouseListener(listener);
		if (component instanceof Container) for (var child : ((Container) component).getComponents()) addMouseListenerToTree(child, listener);
	}

	static void addAll(Container into, Component... parts) {
		for (var c : parts) into.add(c);
	}

	static void redraw(JComponent c) {
		c.revalidate();
		c.repaint();
	}

	/** Swap the single content component and repaint. */
	void setContent(Component component) {
		invokeLater(() -> {
			if (content.getComponentCount() == 1 && content.getComponent(0) == component) return;
			content.removeAll();
			if (component instanceof JComponent) ((JComponent) component).setAlignmentX(LEFT_ALIGNMENT);
			content.add(component);
			redraw(content);
		});
	}

	// ---- Soulrend-themed widget chrome ---------------------------------------------------------------

	/** A themed action button, wired to one thing it does. */

	static JButton flatButton(String label, String tooltip, Color foreground, Runnable onClick) {
		var b = new JButton(label);
		styleFlatButton(b, foreground);
		b.setToolTipText(tooltip);
		b.addActionListener(e -> onClick.run());
		return b;
	}


	/** An orange {@link #flatButton} holding a whole row, so a lone action doesn't sit in half a line. */
	static JPanel fullWidthButton(String label, String tooltip, Runnable onClick) {
		return fullWidth(flatButton(label, tooltip, BRAND_ORANGE, onClick));
	}

	static JPanel fullWidth(JButton button) {
		return buttons(new GridLayout(1, 1), button);
	}

	/**
	 * Buttons in a grid row. The height cap matters: a grid inside a vertical BoxLayout will happily
	 * stretch to whatever space is left, which turns a pair of buttons into a pair of slabs.
	 */
	static JPanel buttons(GridLayout layout, JButton... buttons) {
		var row = left(new JPanel(layout));
		row.setBackground(DARK_GRAY_COLOR);
		for (var b : buttons) row.add(b);
		return capH(row);
	}

	static void styleFlatButton(JButton b, Color foreground) {
		b.setFocusPainted(false);
		b.setForeground(foreground);
		b.setBackground(BUTTON_BG);
		b.setFont(SMALL);
		b.setBorder(createEmptyBorder(5, 10, 5, 10));
		// A raised row, like the site's: a rounded fill and no outline, painted once for every button.
		b.setContentAreaFilled(false);
		b.setOpaque(false);
		b.setUI(new BasicButtonUI() {
			public void paint(Graphics g, JComponent c) {
				var g2 = smooth(g);
				g2.setColor(c.getBackground());
				g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 8, 8);
				g2.dispose();
				super.paint(g, c);
			}
		});
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.addMouseListener(new MouseAdapter() {
			public void mouseEntered(MouseEvent e) {
				if (b.isEnabled()) b.setBackground(WIDGET_BORDER); // the hover: a step lighter
			}

			public void mouseExited(MouseEvent e) {
				b.setBackground(BUTTON_BG);
			}
		});
	}

	/** Flat combo for the clan selector and tile-status filter: dark field, gold arrow — default Swing sticks out. */
	static void styleCombo(JComboBox<?> combo) {
		combo.setBackground(WIDGET_BG);
		combo.setForeground(WHITE);
		combo.setFont(SMALL);
		combo.setBorder(createLineBorder(WIDGET_BORDER));
		combo.setUI(new BasicComboBoxUI() {
			protected JButton createArrowButton() {
				var arrow = new BasicArrowButton(
					SwingConstants.SOUTH, WIDGET_BG, WIDGET_BG, BRAND_ORANGE, WIDGET_BG);
				arrow.setBorder(createEmptyBorder());
				return arrow;
			}
		});
	}

	/**
	 * Two real labels, not one HTML one.
	 *
	 * The second line was <code>&lt;font size='-2'&gt;</code> inside a label already set to
	 * RuneScape's small font. That font is a bitmap face: asked to render smaller still it does not
	 * get daintier, it gets illegible — and the size the browser-ish HTML renderer picks is not one
	 * the face was drawn at. The rest of this panel never shrinks type to mean "secondary"; it keeps
	 * the same small font and drops the colour to {@link #MUTED}. This does the same.
	 *
	 * Building the row out of components also means no markup, which means no clan name can smuggle
	 * any: {@link #plainText} was guarding a string we were about to concatenate into HTML ourselves,
	 * which is the one case it does not guard.
	 */
	static final class ClanChoiceRenderer implements ListCellRenderer<ClanChoice> {
		final JPanel row = new JPanel(new BorderLayout(0, 1));
		final JLabel name = new ClipLabel();
		final JLabel detail = new ClipLabel();

		ClanChoiceRenderer() {
			name.setFont(SMALL);
			detail.setFont(SMALL);
			row.setOpaque(true);
			row.setBorder(createEmptyBorder(4, 6, 4, 6));
			row.add(name, NORTH);
			row.add(detail, SOUTH);
		}

		public Component getListCellRendererComponent(JList<? extends ClanChoice> list, ClanChoice value,
			int index, boolean isSelected, boolean cellHasFocus) {
			// index >= 0 → a popup row; -1 → the closed field. Dark rows, gold-tinted hover.
			var hover = isSelected && index >= 0;
			row.setBackground(hover ? DARK_GRAY_HOVER_COLOR : WIDGET_BG);
			name.setForeground(hover ? BRAND_ORANGE : WHITE);
			detail.setForeground(MUTED);

			name.setText(value == null ? "" : plainText(value.label));
			// The closed field shows the name alone — a second line there would resize the header on
			// every refresh, and the detail is what you read while choosing, not after.
			var showDetail = index >= 0 && value != null && !value.detail.isEmpty();
			detail.setText(showDetail ? plainText(value.detail) : "");
			detail.setVisible(showDetail);
			return row;
		}
	}
}
