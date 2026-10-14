package com.setuppicker;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.IndexDataBase;
import net.runelite.api.SpritePixels;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * The game's own interface sprites, for drawing the lists in the game's chrome.
 * <p>
 * Sprites are looked up the way the game looks them up when it draws an interface: the client's sprite overrides
 * first, then the game cache. Overrides are how the Resource Packs plugin and RuneLite's Interface Styles reskin
 * interfaces, so going through them keeps the lists matching the bank beside them. The overrides are only ever
 * read, never written.
 * <p>
 * Packs change while the game runs. Resource Packs applies its sprites late, after a download and again on login,
 * and announces nothing, so {@link #refresh()} compares the override behind every sprite it holds with the one now
 * in place and starts again on any difference. The bank's separator color is read from the bank itself, since a
 * color pack recolors those lines rather than a sprite.
 * <p>
 * Client thread only, which is where overlays render.
 */
@Singleton
public class GameSprites implements PickerSkin.Sprites
{
	// The bank's own separator lines, as the game draws them without a color pack
	private static final Color DEFAULT_LINE = new Color(0x2e2b23);

	// Config groups whose changes can reskin interfaces: Resource Packs' and Interface Styles'
	private static final Set<String> THEME_GROUPS = new HashSet<>(Arrays.asList("resourcepacks", "interfaceStyles"));

	// The keys in RuneLite's own config group that turn those plugins on and off. The rest of that group is
	// written all the time (every overlay moved, the window resized), so only these mean a reload.
	private static final String RUNELITE_GROUP = "runelite";
	private static final Set<String> THEME_PLUGIN_KEYS = new HashSet<>(Arrays.asList("resourcepacksplugin", "interfacestylesplugin"));

	private final Client client;

	// The sprites asked for, and the override each came from, or null for the cache
	private final Map<Integer, BufferedImage> sprites = new HashMap<>();
	private final Map<Integer, SpritePixels> sources = new HashMap<>();
	// The sprites that couldn't be had, and the client tick they were last asked for in
	private final Map<Integer, Integer> missing = new HashMap<>();

	// Bumped whenever the sprites are thrown away, so anything made from them can be made again
	private int version;
	// Set from any thread when a theme setting changes, and acted on by the next refresh
	private volatile boolean invalidated;
	// The client tick of the last refresh, so that it is done once a tick however often it's asked for
	private int refreshedCycle = -1;
	private Color line = DEFAULT_LINE;

	@Inject
	GameSprites(Client client)
	{
		this.client = client;
	}

	/**
	 * The pixels of a sprite, trimmed to the part of it that isn't see-through, or null when neither the overrides
	 * nor the cache have it. One that is all see-through is {@link PickerSkin#EMPTY}, not missing: packs leave sprites
	 * blank on purpose, such as a scrollbar with no track.
	 * <p>
	 * The cache may not have loaded a sprite yet when it is first asked for; it is asked again each client tick until
	 * it has, so the game's own sprites are drawn as soon as they can be.
	 */
	@Override
	public BufferedImage get(int id)
	{
		final BufferedImage sprite = sprites.get(id);
		if (sprite != null)
		{
			return sprite;
		}
		final Integer missedIn = missing.get(id);
		if (missedIn != null && missedIn == refreshedCycle)
		{
			return null;
		}

		final SpritePixels override = override(id);
		final SpritePixels pixels = override != null ? override : fromCache(id);
		if (pixels == null)
		{
			missing.put(id, refreshedCycle);
			return null;
		}
		final BufferedImage image = pixels.getWidth() <= 0 || pixels.getHeight() <= 0 ? PickerSkin.EMPTY : PickerSkin.trim(pixels.toBufferedImage());
		missing.remove(id);
		sprites.put(id, image);
		sources.put(id, override);
		return image;
	}

	private SpritePixels override(int id)
	{
		final Map<Integer, SpritePixels> overrides = client.getSpriteOverrides();
		return overrides == null ? null : overrides.get(id);
	}

	private SpritePixels fromCache(int id)
	{
		final IndexDataBase index = client.getIndexSprites();
		if (index == null)
		{
			return null;
		}
		final SpritePixels[] frames = client.getSprites(index, id, 0);
		return frames == null || frames.length == 0 ? null : frames[0];
	}

	/**
	 * The client tick the sprites were last brought up to date in.
	 */
	public int getCycle()
	{
		return refreshedCycle;
	}

	/**
	 * Changes whenever the sprites are replaced.
	 */
	public int getVersion()
	{
		return version;
	}

	/**
	 * The color of the bank's separator lines.
	 */
	public Color getLine()
	{
		return line;
	}

	/**
	 * Brings the sprites up to date with the current theme. Call at the start of every render that uses them; the
	 * work is done at most once a client tick. Comparing each sprite's override with the one in place is the only
	 * way to notice a pack being applied, and is a few map lookups.
	 */
	public void refresh()
	{
		final int cycle = client.getGameCycle();
		if (cycle == refreshedCycle)
		{
			return;
		}
		refreshedCycle = cycle;

		if (invalidated || overridesChanged())
		{
			invalidated = false;
			sprites.clear();
			sources.clear();
			missing.clear();
			version++;
		}

		// the bank's line under its tabs, while the bank is open, and the last one seen while it isn't
		final Widget tabsLine = client.getWidget(InterfaceID.Bankmain.TABS_LINE0);
		if (tabsLine != null && !tabsLine.isHidden() && (tabsLine.getTextColor() & 0xffffff) != (line.getRGB() & 0xffffff))
		{
			line = new Color(tabsLine.getTextColor() & 0xffffff);
		}
	}

	private boolean overridesChanged()
	{
		final Map<Integer, SpritePixels> overrides = client.getSpriteOverrides();
		for (Map.Entry<Integer, SpritePixels> source : sources.entrySet())
		{
			if ((overrides == null ? null : overrides.get(source.getKey())) != source.getValue())
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Notes a config change that might have reskinned the game's interfaces, so that the next refresh loads every
	 * sprite again. Safe to call from any thread.
	 */
	public void onConfigChanged(String group, String key)
	{
		if (THEME_GROUPS.contains(group) || RUNELITE_GROUP.equals(group) && THEME_PLUGIN_KEYS.contains(key))
		{
			invalidated = true;
		}
	}
}
