package com.setuppicker;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws the picker: as a popup while it has been opened with the hotkey, otherwise beside the bank
 * whenever the bank is open.
 */
@Singleton
public class SetupPickerOverlay extends Overlay
{
	private static final int PALETTE_WIDTH = 240;

	private final Client client;
	private final SetupPickerConfig config;
	private final PickerModel model;
	private final SetupIcons icons;
	private final PickerStyle style;

	// Layout of the last rendered frame, read by the input listeners. Null while the picker isn't showing.
	private volatile PickerLayout layout;
	private volatile String status = "";
	private volatile Point mouse;
	// Where the bank and the canvas were in the last rendered frame that had the list beside the bank
	private volatile Rectangle bankBounds;
	private volatile Rectangle canvas;
	// While the list beside the bank is being dragged: where its corner is from the bank's. Else null.
	private volatile Point dragOffset;

	@Inject
	SetupPickerOverlay(Client client, SetupPickerConfig config, PickerModel model, ItemManager itemManager,
		PickerStyle style)
	{
		this.client = client;
		this.config = config;
		this.model = model;
		this.icons = new SetupIcons(itemManager);
		this.style = style;
		// Positions itself, in canvas coordinates
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	public PickerLayout getLayout()
	{
		return layout;
	}

	/**
	 * Message to show in place of the list when there are no setups.
	 */
	public void setStatus(String status)
	{
		this.status = status;
	}

	public void clearLayout()
	{
		layout = null;
		mouse = null;
		dragOffset = null;
	}

	/**
	 * Show the list beside the bank with its top left corner here on the canvas, while it is being dragged.
	 */
	public void dragTo(Point corner)
	{
		final Rectangle bank = bankBounds;
		if (bank != null)
		{
			// kept as the place without a frame, which is what is saved
			dragOffset = PickerLayout.offsetFor(bank, corner, config.width(), style.getFrame());
		}
	}

	/**
	 * Where the list has been dragged to, from the bank's top left corner and kept on the canvas, for saving.
	 * Null if it hasn't been.
	 */
	public Point getDragOffset()
	{
		final Point offset = dragOffset;
		final Rectangle bank = bankBounds;
		final Rectangle area = canvas;
		if (offset == null || bank == null || area == null)
		{
			return null;
		}
		return PickerLayout.keepOnCanvas(bank, area, offset, config.width(), style.getFrame());
	}

	/**
	 * Go back to showing the list where it is saved as being.
	 */
	public void endDrag()
	{
		dragOffset = null;
	}

	/**
	 * @param mouse where the mouse is over the picker, which is kept from the game (see PickerInput), or null
	 *              when it's elsewhere
	 */
	public void setMouse(Point mouse)
	{
		this.mouse = mouse;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		style.refresh();
		final PickerLayout.Frame frame = style.getFrame();
		final PickerModel.View view = model.view();
		final int rowHeight = config.showIcons() ? PickerLayout.ROW_HEIGHT_ICONS : PickerLayout.ROW_HEIGHT_TEXT;
		final PickerLayout newLayout;
		// which side of the picker its box of notes goes on, given the choice
		boolean notesOnLeft = false;
		if (view.isPaletteOpen())
		{
			newLayout = PickerLayout.computePalette(new Rectangle(client.getCanvasWidth(), client.getCanvasHeight()),
				PALETTE_WIDTH, view.getRows().size(), rowHeight, config.popupRows(), frame);
		}
		else
		{
			final Widget bank = config.showBesideBank() ? client.getWidget(InterfaceID.Bankmain.UNIVERSE) : null;
			if (bank == null || bank.isHidden())
			{
				layout = null;
				return null;
			}
			final Rectangle area = new Rectangle(client.getCanvasWidth(), client.getCanvasHeight());
			final Point dragged = dragOffset;
			final Point offset = dragged != null ? dragged : config.bankOffset();
			if (offset != null)
			{
				newLayout = PickerLayout.computeMoved(bank.getBounds(), area, offset, config.width(), config.collapsed(),
					view.getRows().size(), rowHeight, config.bankRows(), frame);
			}
			else
			{
				newLayout = PickerLayout.compute(bank.getBounds(), area.width,
					config.side() == SetupPickerConfig.Side.LEFT, config.width(), config.collapsed(), config.verticalWhenCollapsed(),
					view.getRows().size(), rowHeight, config.bankRows(), frame);
			}
			bankBounds = bank.getBounds();
			canvas = area;
			// away from the bank, rather than over it
			notesOnLeft = newLayout.getBounds().x < bank.getBounds().x;
		}
		// clamps the scroll position to what now fits, so take the view afterwards
		model.setVisibleRows(newLayout.getVisibleRows());
		layout = newLayout;

		// over the picker the game doesn't know where the mouse is; anywhere else it does
		final Point hidden = mouse;
		final net.runelite.api.Point seen = client.getMouseCanvasPosition();
		// unless the picker has since moved out from under it
		final Point hover = hidden != null && newLayout.getBounds().contains(hidden) ? hidden : (seen == null ? null : new Point(seen.getX(), seen.getY()));
		final String title = config.source() == SetupPickerConfig.Source.BANK_TAGS ? "Bank tags" : "Setups";
		final PickerPainter painter = new PickerPainter(style.getTheme(), title, style.getSkin());
		final PickerModel.View clamped = model.view();
		painter.paint(graphics, newLayout, clamped,
			hover,
			config.showIcons() ? icons::get : null,
			FontManager.getRunescapeSmallFont(), status);
		painter.paintNotes(graphics, newLayout, clamped, hover, FontManager.getRunescapeSmallFont(),
			new Rectangle(client.getCanvasWidth(), client.getCanvasHeight()), notesOnLeft);
		return null;
	}
}
