package com.setuppicker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Says under the bank which spellbook the open setup is for, in red when it isn't the one the player is on.
 */
@Singleton
public class SpellbookOverlay extends Overlay
{
	// What Inventory Setups saves for a setup's spellbook, which for these is also the game's own numbering
	private static final String[] SPELLBOOKS = {"Standard", "Ancient", "Lunar", "Arceuus"};

	private static final Color RIGHT = new Color(190, 180, 160);
	private static final Color WRONG = new Color(255, 70, 70);

	// Under the bank's bottom right corner
	private static final int INSET = 6;
	private static final int GAP = 3;
	private static final int PADDING_X = 5;
	private static final int PADDING_Y = 3;

	private final Client client;
	private final SetupPickerConfig config;
	private final PickerModel model;
	private final PickerStyle style;

	// Each setup's spellbook by its name, for the ones that have one
	private volatile Map<String, Integer> spellbooks = Collections.emptyMap();

	@Inject
	SpellbookOverlay(Client client, SetupPickerConfig config, PickerModel model, PickerStyle style)
	{
		this.client = client;
		this.config = config;
		this.model = model;
		this.style = style;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	public void setSpellbooks(Map<String, Integer> spellbooks)
	{
		this.spellbooks = spellbooks;
	}

	/**
	 * What to say about a setup's spellbook, or null for nothing: when it has none, or one this doesn't know.
	 *
	 * @param wanted  the setup's spellbook, as Inventory Setups numbers them
	 * @param current the spellbook the player is on
	 */
	static String label(int wanted, int current)
	{
		if (wanted < 0 || wanted >= SPELLBOOKS.length)
		{
			return null;
		}
		return wanted == current ? SPELLBOOKS[wanted] + " spellbook" : "Needs " + SPELLBOOKS[wanted] + " spellbook";
	}

	/**
	 * The color, a little more see-through.
	 */
	private static Color muted(Color color)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() * 3 / 4);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showSpellbook())
		{
			return null;
		}
		final Integer wanted = spellbooks.get(model.view().getActiveSetup());
		final Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		if (wanted == null || bank == null || bank.isHidden())
		{
			return null;
		}
		final int current = client.getVarbitValue(VarbitID.SPELLBOOK);
		final String label = label(wanted, current);
		if (label == null)
		{
			return null;
		}

		PickerPainter.prepare(graphics);
		graphics.setFont(FontManager.getRunescapeSmallFont());
		final FontMetrics fm = graphics.getFontMetrics();
		final Rectangle bounds = bank.getBounds();
		final int width = fm.stringWidth(label) + 2 * PADDING_X;
		final int height = fm.getAscent() + fm.getDescent() + 2 * PADDING_Y;
		final Rectangle box = new Rectangle(bounds.x + bounds.width - INSET - width, bounds.y + bounds.height + GAP, width, height);
		if (box.y + box.height > client.getCanvasHeight())
		{
			// no room under the bank: just inside its bottom edge instead
			box.y = bounds.y + bounds.height - INSET - height;
		}

		// the picker's own background, toned down, so that the text can be read over whatever is behind it
		style.refresh();
		final PickerTheme theme = style.getTheme();
		graphics.setColor(muted(theme.getBackground()));
		graphics.fill(box);
		graphics.setColor(muted(theme.getBorder()));
		graphics.drawRect(box.x, box.y, box.width - 1, box.height - 1);

		final int x = box.x + PADDING_X;
		final int baseline = box.y + PADDING_Y + fm.getAscent();
		graphics.setColor(Color.BLACK);
		graphics.drawString(label, x + 1, baseline + 1);
		graphics.setColor(wanted == current ? RIGHT : WRONG);
		graphics.drawString(label, x, baseline);
		return null;
	}
}
