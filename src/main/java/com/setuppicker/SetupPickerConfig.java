package com.setuppicker;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup(SetupPickerConfig.GROUP)
public interface SetupPickerConfig extends Config
{
	String GROUP = "setuppicker";
	String KEY_COLLAPSED = "collapsed";
	String KEY_RECENT = "recentSetups";
	String KEY_SEEN_VERSION = "lastSeenVersion";
	String KEY_SIDE = "side";
	String KEY_BANK_OFFSET = "bankOffset";

	enum Side
	{
		LEFT,
		RIGHT
	}

	enum Source
	{
		INVENTORY_SETUPS("Inventory Setups", true, false),
		BANK_TAGS("Bank Tags", false, true),
		BOTH("Both", true, true);

		private final String name;
		private final boolean setups;
		private final boolean bankTags;

		Source(String name, boolean setups, boolean bankTags)
		{
			this.name = name;
			this.setups = setups;
			this.bankTags = bankTags;
		}

		boolean hasSetups()
		{
			return setups;
		}

		boolean hasBankTags()
		{
			return bankTags;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	@ConfigSection(
		name = "Beside the bank",
		description = "The list shown next to the bank interface",
		position = 20
	)
	String bankSection = "bank";

	@ConfigItem(
		keyName = "source",
		name = "List",
		description = "What to list: your setups from Inventory Setups, your tag tabs from Bank Tags (opened along with"
			+ " their layout, if they have one), or both. With both, each is marked with where it's from, and"
			+ " bank tags go under a heading of their own when the list is grouped by section.",
		position = 0
	)
	default Source source()
	{
		return Source.INVENTORY_SETUPS;
	}

	@ConfigItem(
		keyName = "hotkey",
		name = "Open hotkey",
		description = "Opens the setup list anywhere in game. Only the key is used; the modifier comes from the next setting."
			+ " Type to filter, Up/Down to move, Enter to open the setup (or close it, if it's the one already open),"
			+ " Esc to cancel.",
		position = 1
	)
	default Keybind openKey()
	{
		return new Keybind(KeyEvent.VK_K, 0);
	}

	@ConfigItem(
		keyName = "requireModifier",
		name = "Require Ctrl/Cmd",
		description = "Require the platform modifier (Cmd on Mac, Ctrl on Windows) together with the open hotkey."
			+ " Follows the Platform Keys plugin's key profile when that plugin is on.",
		position = 2
	)
	default boolean requireModifier()
	{
		return true;
	}

	@ConfigItem(
		keyName = "startOnOpenSetup",
		name = "Start on open setup",
		description = "When a setup is already open, the popup opens with it selected, so pressing Enter closes it."
			+ " (A bank tag that's already open is left open.) When off, the selection always starts at the top.",
		position = 3
	)
	default boolean startOnOpenSetup()
	{
		return true;
	}

	@Range(min = 3, max = 25)
	@ConfigItem(
		keyName = "popupRows",
		name = "Popup rows",
		description = "How many setups the popup shows before it scrolls",
		position = 4
	)
	default int popupRows()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "showIcons",
		name = "Show icons",
		description = "Show each setup's icon next to its name",
		position = 5
	)
	default boolean showIcons()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showNotes",
		name = "Show notes",
		description = "Mark the setups that have notes in Inventory Setups, and show the notes of the one that is"
			+ " selected or under the mouse in a box beside the list",
		position = 6
	)
	default boolean showNotes()
	{
		return false;
	}

	@ConfigItem(
		keyName = "alphabetical",
		name = "Sort alphabetically",
		description = "List setups by name. When off, they keep the order of the Inventory Setups side panel's list,"
			+ " and bank tags that of their tabs.",
		position = 7
	)
	default boolean alphabetical()
	{
		return true;
	}

	@ConfigItem(
		keyName = "favoritesFirst",
		name = "Favorites first",
		description = "List favorited setups before the rest",
		position = 8
	)
	default boolean favoritesFirst()
	{
		return true;
	}

	@Range(max = 10)
	@ConfigItem(
		keyName = "recentCount",
		name = "Recently used",
		description = "How many of the setups you opened most recently to list at the top, latest first and marked"
			+ " with a clock. With Group by section on they get a Recent heading. 0 turns this off.",
		position = 9
	)
	default int recentCount()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "fuzzySearch",
		name = "Fuzzy search",
		description = "When a search finds nothing as typed, also match names that are a typo away (\"vorkahh\") or that"
			+ " have the typed letters in order (\"vkdh\")",
		position = 10
	)
	default boolean fuzzySearch()
	{
		return true;
	}

	@ConfigItem(
		keyName = "groupBySection",
		name = "Group by section",
		description = "List setups under the sections you put them in in Inventory Setups, with the rest under Unassigned."
			+ " Typing a section's name lists its setups. Bank tags have no sections, so they are all listed"
			+ " under Bank tags.",
		position = 11
	)
	default boolean groupBySection()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sectionPages",
		name = "Sections as pages",
		description = "With Group by section on, list just the sections, and open one to see its setups on a page of"
			+ " their own. Click the section's name at the top, or press Left or Backspace, to go back. Typing in"
			+ " the list of sections still searches every setup.",
		position = 12
	)
	default boolean sectionPages()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showBesideBank",
		name = "Show beside bank",
		description = "Also show the setup list next to the bank while it is open",
		section = bankSection,
		position = 0
	)
	default boolean showBesideBank()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSpellbook",
		name = "Show spellbook",
		description = "Say under the bank's bottom right corner which spellbook the open setup is for, in red when it isn't the one"
			+ " you are on. Only for setups that filter the bank, and that have a spellbook set in Inventory Setups.",
		section = bankSection,
		position = 5
	)
	default boolean showSpellbook()
	{
		return false;
	}

	@ConfigItem(
		keyName = KEY_SIDE,
		name = "Bank side",
		description = "Which side of the bank the list sits on. Falls back to the other side when there is no room."
			+ " The list can also be dragged anywhere by its title bar; right-clicking the title bar, or changing this setting, puts it back beside the bank.",
		section = bankSection,
		position = 1
	)
	default Side side()
	{
		return Side.LEFT;
	}

	@Range(min = 110, max = 300)
	@ConfigItem(
		keyName = "width",
		name = "Width",
		description = "Width of the list beside the bank in pixels",
		section = bankSection,
		position = 2
	)
	default int width()
	{
		return 160;
	}

	@Range(max = 40)
	@ConfigItem(
		keyName = "bankRows",
		name = "Max rows",
		description = "The most rows the list beside the bank shows before it scrolls, to keep it from reaching down over"
			+ " the inventory on a small client. 0 lets it run as far down as the bank does.",
		section = bankSection,
		position = 3
	)
	default int bankRows()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "verticalWhenCollapsed",
		name = "Vertical when collapsed",
		description = "Show the collapsed list as a narrow upright tab against the bank. When off, it collapses to its title bar.",
		section = bankSection,
		position = 4
	)
	default boolean verticalWhenCollapsed()
	{
		return false;
	}

	@ConfigSection(
		name = "Colors",
		description = "Colors of the popup and the list beside the bank, for matching a resource pack",
		position = 30,
		closedByDefault = true
	)
	String colorSection = "colors";

	@ConfigItem(
		keyName = "matchResourcePack",
		name = "Match resource pack",
		description = "Draw the lists' background, frame, search box and scrollbar with the game's own interface sprites,"
			+ " so that they follow your resource pack or interface style. The background, title bar and border colors"
			+ " below are then not used.",
		section = colorSection,
		position = 0
	)
	default boolean matchResourcePack()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		keyName = "backgroundColor",
		name = "Background",
		description = "Background of the list",
		section = colorSection,
		position = 1
	)
	default Color backgroundColor()
	{
		return PickerTheme.DEFAULT_BACKGROUND;
	}

	@ConfigItem(
		keyName = "headerColor",
		name = "Title bar",
		description = "Background of the title bar",
		section = colorSection,
		position = 2
	)
	default Color headerColor()
	{
		return PickerTheme.DEFAULT_HEADER;
	}

	@ConfigItem(
		keyName = "borderColor",
		name = "Border",
		description = "The frame, the search box outline and section dividers",
		section = colorSection,
		position = 3
	)
	default Color borderColor()
	{
		return PickerTheme.DEFAULT_BORDER;
	}

	@ConfigItem(
		keyName = "accentColor",
		name = "Accent",
		description = "The title, the keyboard selection, the focused search box and the open setup",
		section = colorSection,
		position = 4
	)
	default Color accentColor()
	{
		return PickerTheme.DEFAULT_ACCENT;
	}

	@ConfigItem(
		keyName = "textColor",
		name = "Text",
		description = "Setup names that have no color of their own in Inventory Setups",
		section = colorSection,
		position = 5
	)
	default Color textColor()
	{
		return PickerTheme.DEFAULT_TEXT;
	}

	@ConfigItem(
		keyName = KEY_COLLAPSED,
		name = "",
		description = "",
		hidden = true
	)
	default boolean collapsed()
	{
		return false;
	}

	@ConfigItem(
		keyName = KEY_SEEN_VERSION,
		name = "",
		description = "",
		hidden = true
	)
	default String lastSeenVersion()
	{
		return "";
	}

	/**
	 * Where the list beside the bank has been dragged to, from the bank's top left corner. Null while it is
	 * still at the bank's side.
	 */
	@ConfigItem(
		keyName = KEY_BANK_OFFSET,
		name = "",
		description = "",
		hidden = true
	)
	default Point bankOffset()
	{
		return null;
	}
}
