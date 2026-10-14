package com.setuppicker;

import java.awt.Color;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * What the lists are drawn in: the colors of the Colors settings or, with Match resource pack on, the game's own
 * interface sprites. Shared by the lists and the spellbook line, and only worked out again when something it
 * depends on changes.
 * <p>
 * Client thread only, but for {@link #onConfigChanged}.
 */
@Singleton
public class PickerStyle
{
	// While the game's sprites can't all be had they are tried for again every client tick at first, as they load
	// at startup, and then only every so often, for one the game may never have
	private static final long STARTUP_MILLIS = 5000;
	private static final long RETRY_MILLIS = 2000;

	private final SetupPickerConfig config;
	private final GameSprites sprites;

	// Set from any thread when a setting changes, and acted on by the next refresh
	private volatile boolean stale = true;
	private PickerTheme theme;
	// read by the mouse too, for the room the list's frame takes while it is dragged
	private volatile PickerSkin skin;
	// what the skin was made from
	private int spritesVersion;
	private Color line;
	// the client tick the game's sprites were last found incomplete in, when they were first, and when to try next
	private int incompleteCycle = -1;
	private long incompleteSince;
	private long retryAt;

	@Inject
	PickerStyle(SetupPickerConfig config, GameSprites sprites)
	{
		this.config = config;
		this.sprites = sprites;
	}

	/**
	 * Brings the style up to date with the settings and the game's sprites. Call at the start of each render.
	 */
	public void refresh()
	{
		final boolean game = config.matchResourcePack();
		if (game)
		{
			sprites.refresh();
		}
		final boolean current = !game
			|| skin != null && spritesVersion == sprites.getVersion() && line.equals(sprites.getLine())
			|| skin == null && spritesVersion == sprites.getVersion()
			&& (sprites.getCycle() == incompleteCycle || System.currentTimeMillis() < retryAt);
		if (!stale && current)
		{
			return;
		}
		stale = false;

		PickerSkin made = null;
		if (game)
		{
			spritesVersion = sprites.getVersion();
			line = sprites.getLine();
			made = new PickerSkin(sprites);
			if (made.isComplete())
			{
				incompleteSince = 0;
			}
			else
			{
				// in colors meanwhile, which only happens while the cache is still loading them, or if it never has them
				made = null;
				incompleteCycle = sprites.getCycle();
				final long now = System.currentTimeMillis();
				if (incompleteSince == 0)
				{
					incompleteSince = now;
				}
				retryAt = now - incompleteSince < STARTUP_MILLIS ? 0 : now + RETRY_MILLIS;
			}
		}
		else
		{
			// so that turning it on again starts with quick tries
			incompleteSince = 0;
		}
		skin = made;
		theme = made == null
			? new PickerTheme(config.backgroundColor(), config.headerColor(), config.borderColor(), config.accentColor(), config.textColor())
			// the text colors are still the settings' own, worked out against the game's background, and dividing
			// lines are the color of the bank's
			: new PickerTheme(made.getBackground(), made.getField(), line, config.accentColor(), config.textColor());
	}

	public PickerTheme getTheme()
	{
		return theme;
	}

	/**
	 * The game's sprites to draw with, or null to draw in colors.
	 */
	public PickerSkin getSkin()
	{
		return skin;
	}

	/**
	 * How much room the chrome being drawn takes.
	 */
	public PickerLayout.Frame getFrame()
	{
		return skin != null ? PickerLayout.Frame.GAME : PickerLayout.Frame.FLAT;
	}

	/**
	 * Notes a setting that changes the style, of this plugin's or of what reskins the game's interfaces. Safe to
	 * call from any thread.
	 */
	public void onConfigChanged(String group, String key)
	{
		if (SetupPickerConfig.GROUP.equals(group))
		{
			stale = true;
		}
		sprites.onConfigChanged(group, key);
	}

	/**
	 * Notes that every setting may have changed, as on switching profiles.
	 */
	public void invalidate()
	{
		stale = true;
	}
}
