package com.setuppicker;

import com.google.inject.Binder;
import com.google.inject.Provides;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Inventory Setups Picker",
	description = "Hotkey search for your Inventory Setups and Bank Tag Layouts: open any setup or bank tag by typing its name",
	tags = {"inventory", "setups", "loadout", "preset", "gear", "bank", "tags", "tag", "layout", "layouts", "hotkey", "search", "quick", "switch", "picker"}
)
// for its BankTagsService
@PluginDependency(BankTagsPlugin.class)
public class SetupPickerPlugin extends Plugin
{
	private static final String INVENTORY_SETUPS_PLUGIN_NAME = "Inventory Setups";
	private static final String BANK_TAGS_PLUGIN_NAME = "Bank Tags";
	// The latest version with something new to tell of, and what is said about it, once, at the next login.
	// A release with nothing to announce leaves both as they are.
	static final String NEWS_VERSION = "1.3.0";
	static final String NEWS = "Inventory Setups Picker " + NEWS_VERSION + ": drag the list beside the bank by its title"
		+ " bar to put it anywhere (right-click the title bar to put it back), and limit its height with the new Max rows setting.";

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private ChatboxKeyGuard chatboxKeyGuard;

	@Inject
	private SetupPickerConfig config;

	@Inject
	private SetupRepository repository;

	@Inject
	private PickerModel model;

	@Inject
	private SetupPickerOverlay overlay;

	@Inject
	private PickerInput input;

	@Inject
	private SpellbookOverlay spellbookOverlay;

	@Inject
	private PickerStyle style;

	private final AtomicBoolean refreshQueued = new AtomicBoolean();
	private final RecentTagTracker recentTagTracker = new RecentTagTracker();
	// The names of the bank tags listed, by their standardized form. Client thread only.
	private Map<String, String> bankTagNames = Collections.emptyMap();

	@Override
	public void configure(Binder binder)
	{
		binder.bind(PickerModel.class).asEagerSingleton();
	}

	@Provides
	SetupPickerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SetupPickerConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		overlayManager.add(spellbookOverlay);
		mouseManager.registerMouseListener(input);
		mouseManager.registerMouseWheelListener(input);
		keyManager.registerKeyListener(input);

		queueRefresh();
		// Inventory Setups only announces the active setup when it changes, so ask once for the current one
		clientThread.invokeLater(() ->
		{
			model.setActiveSetup(repository.queryActiveSetup());
			recentTagTracker.reset();
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlayManager.remove(spellbookOverlay);
		mouseManager.unregisterMouseListener(input);
		mouseManager.unregisterMouseWheelListener(input);
		keyManager.unregisterKeyListener(input);
		clientThread.invoke(chatboxKeyGuard::restore);
		overlay.clearLayout();
		model.resetSearch();
	}

	@Subscribe
	public void onPluginMessage(PluginMessage message)
	{
		if (!SetupRepository.NAMESPACE.equals(message.getNamespace()))
		{
			return;
		}
		if (SetupRepository.MSG_SETUPS_CHANGED.equals(message.getName()))
		{
			queueRefresh();
		}
		else if (SetupRepository.MSG_ACTIVE_SETUP_CHANGED.equals(message.getName()))
		{
			final Object active = message.getData().get(SetupRepository.DATA_ACTIVE_SETUP);
			final String name = active instanceof String ? (String) active : "";
			model.setActiveSetup(name);
			if (!name.isEmpty() && config.source().hasSetups())
			{
				// This message also comes for every edit to the open setup, which leaves the list as it is
				markUsed(name);
			}
		}
	}

	/**
	 * @param key the key of the setup or bank tag that has just been opened
	 */
	private void markUsed(String key)
	{
		if (config.recentCount() <= 0)
		{
			return;
		}
		// Saving it is a config change of this plugin's, which reloads the list in its new order
		final List<String> recent = repository.loadRecent();
		final List<String> updated = SetupRepository.markUsed(recent, key);
		if (updated != recent)
		{
			repository.saveRecent(updated);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		// a color, the resource pack setting, or something that reskins the game's interfaces
		style.onConfigChanged(event.getGroup(), event.getKey());

		// Favoriting, recoloring or changing a setup's icon rewrites its saved json without a setups-changed message
		// and neither does anything done to a section
		final boolean setupEdited = SetupRepository.CONFIG_GROUP.equals(event.getGroup())
			&& (event.getKey().startsWith(SetupRepository.CONFIG_KEY_SETUP_PREFIX)
			|| SetupRepository.CONFIG_KEY_SECTIONS.equals(event.getKey()));
		// creating, deleting, renaming or reordering tag tabs, or changing a tab's icon
		final boolean bankTagEdited = SetupRepository.BANK_TAGS_CONFIG_GROUP.equals(event.getGroup())
			&& (SetupRepository.BANK_TAGS_TABS_KEY.equals(event.getKey())
			|| event.getKey().startsWith(SetupRepository.BANK_TAGS_ICON_PREFIX));
		if (SetupPickerConfig.GROUP.equals(event.getGroup()) && SetupPickerConfig.KEY_SIDE.equals(event.getKey()))
		{
			// choosing a side puts a list that has been dragged elsewhere back beside the bank
			setBankOffset(null);
		}
		if (setupEdited || bankTagEdited || SetupPickerConfig.GROUP.equals(event.getGroup()))
		{
			queueRefresh();
		}
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		style.invalidate();
		clientThread.invokeLater(recentTagTracker::reset);
		queueRefresh();
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		final String name = event.getPlugin().getName();
		if (INVENTORY_SETUPS_PLUGIN_NAME.equals(name) || BANK_TAGS_PLUGIN_NAME.equals(name))
		{
			queueRefresh();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			queueRefresh();
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		// the popup is independent of the bank, so only a search in the list beside it ends here
		if (event.getGroupId() == InterfaceID.BANKMAIN && !model.view().isPaletteOpen())
		{
			model.resetSearch();
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		chatboxKeyGuard.sync(model.view().isSearchFocused());
		input.tick();

		// Bank Tags has no message for its open tag changing, so keep an eye on it
		// Under the name it's listed by, as Bank Tags doesn't mind how a tag's name is capitalized. One that isn't
		// listed counts as none: Inventory Setups opens a tag of its own to filter the bank.
		final String bankTag = bankTagNames.getOrDefault(Text.standardize(repository.queryActiveBankTag()), "");
		model.setActiveBankTag(bankTag);
		if (recentTagTracker.observe(bankTag))
		{
			markUsed(SetupEntry.bankTagKey(bankTag));
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// Nothing is drawn outside the game, so don't leave the keyboard captured by an invisible popup
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			recentTagTracker.reset();
			model.resetSearch();
		}
		else if (!NEWS_VERSION.equals(config.lastSeenVersion()))
		{
			// This state comes again after every loading screen, so it's marked as seen before anything else
			configManager.setConfiguration(SetupPickerConfig.GROUP, SetupPickerConfig.KEY_SEEN_VERSION, NEWS_VERSION);
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append(NEWS).build())
				.build());
		}
	}

	/**
	 * Open the setup, or close it if it's the one already open. A bank tag is opened either way, which for the
	 * one already open changes nothing, the same as clicking its tab.
	 */
	void toggleSetup(SetupEntry setup)
	{
		if (!setup.isBankTag() && model.view().isActive(setup))
		{
			clientThread.invoke(() -> repository.close(setup.getName()));
		}
		else
		{
			openSetup(setup);
		}
	}

	void openSetup(SetupEntry setup)
	{
		if (setup.isBankTag())
		{
			// the next client tick sees it open, and counts it as recently used
			clientThread.invoke(() -> repository.openBankTag(setup.getName()));
		}
		else
		{
			clientThread.invoke(() -> repository.open(setup.getName()));
		}
	}

	void setCollapsed(boolean collapsed)
	{
		configManager.setConfiguration(SetupPickerConfig.GROUP, SetupPickerConfig.KEY_COLLAPSED, collapsed);
	}

	/**
	 * @param offset where the list beside the bank has been dragged to, from the bank's top left corner, or null
	 *               to put it back at the bank's side
	 */
	void setBankOffset(Point offset)
	{
		if (offset == null)
		{
			configManager.unsetConfiguration(SetupPickerConfig.GROUP, SetupPickerConfig.KEY_BANK_OFFSET);
		}
		else
		{
			configManager.setConfiguration(SetupPickerConfig.GROUP, SetupPickerConfig.KEY_BANK_OFFSET, offset);
		}
	}

	// Several triggers tend to fire together (e.g. every setup's config key on a profile switch), so
	// collapse them into one reload on the client thread.
	private void queueRefresh()
	{
		if (refreshQueued.compareAndSet(false, true))
		{
			clientThread.invokeLater(() ->
			{
				refreshQueued.set(false);
				final List<String> used = repository.loadRecent();
				final List<String> recent = used.subList(0, Math.max(0, Math.min(config.recentCount(), used.size())));
				final SetupPickerConfig.Source source = config.source();
				final boolean setupsOn = isPluginEnabled(INVENTORY_SETUPS_PLUGIN_NAME);
				final boolean bankTagsOn = isPluginEnabled(BANK_TAGS_PLUGIN_NAME);
				final List<SetupEntry> setups = new ArrayList<>();
				// whatever is listed, the open setup's spellbook can be shown on the bank
				final Map<String, Integer> spellbooks = new HashMap<>();
				for (SetupEntry setup : repository.loadSetups(config.alphabetical(), config.favoritesFirst()))
				{
					if (source.hasSetups())
					{
						setups.add(setup);
					}
					if (setup.getSpellbook() != SetupEntry.NO_SPELLBOOK)
					{
						spellbooks.put(setup.getName(), setup.getSpellbook());
					}
				}
				spellbookOverlay.setSpellbooks(spellbooks);
				final Map<String, String> tagNames = new HashMap<>();
				// its tabs stay saved while Bank Tags is off, but can't be opened
				if (source.hasBankTags() && bankTagsOn)
				{
					for (SetupEntry tag : repository.loadBankTags())
					{
						setups.add(tag);
						tagNames.put(Text.standardize(tag.getName()), tag.getName());
					}
					SetupRepository.sort(setups, config.alphabetical(), config.favoritesFirst());
				}
				bankTagNames = tagNames;
				final List<SetupSection> sections = config.groupBySection() && source.hasSetups()
					? repository.loadSections() : Collections.emptyList();
				model.setSectionPages(config.sectionPages());
				model.setFuzzySearch(config.fuzzySearch());
				model.setShowNotes(config.showNotes());
				model.setStartOnActiveSetup(config.startOnOpenSetup());
				model.setRows(SetupRepository.group(setups, sections, config.alphabetical(), config.favoritesFirst(), recent), setups.size());
				overlay.setStatus(status(source, setupsOn, bankTagsOn));
			});
		}
	}

	/**
	 * What to say in place of an empty list.
	 */
	static String status(SetupPickerConfig.Source source, boolean setupsOn, boolean bankTagsOn)
	{
		switch (source)
		{
			case BANK_TAGS:
				return bankTagsOn ? "No bank tags yet" : "Bank Tags is off";
			case BOTH:
				return setupsOn || bankTagsOn ? "Nothing to list yet" : "Both plugins are off";
			default:
				return setupsOn ? "No setups yet" : "Inventory Setups is off";
		}
	}

	private boolean isPluginEnabled(String name)
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (name.equals(plugin.getName()))
			{
				return pluginManager.isPluginEnabled(plugin);
			}
		}
		return false;
	}
}
