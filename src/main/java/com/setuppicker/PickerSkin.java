package com.setuppicker;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.gameval.SpriteID;

/**
 * Draws the picker's chrome from the game's own interface sprites, so that it follows whatever Resource Packs or
 * Interface Styles has made of them: the bank's riveted steel frame and background, the stone outline of the
 * game's input boxes, and the game's scrollbar. Has no dependency on the game client so it can be rendered in tests.
 * <p>
 * Every sprite is used trimmed to its visible pixels and placed flush against the edge it belongs to. The cache
 * keeps an edge piece as a strip at an offset in a larger box, while a resource pack supplies it as an image of
 * the whole box; trimmed, both are the same strip.
 */
public final class PickerSkin
{
	/**
	 * Where the sprites come from.
	 */
	public interface Sprites
	{
		/**
		 * The visible pixels of a sprite as {@link #trim} leaves them, {@link #EMPTY} for one that is all
		 * see-through, or null when it can't be had.
		 */
		BufferedImage get(int id);
	}

	/**
	 * A sprite with nothing to show, which takes up no room and draws nothing.
	 */
	public static final BufferedImage EMPTY = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

	private static final int BACKGROUND = SpriteID.TRADEBACKING;
	private static final int FIELD = SpriteID.TRADEBACKING_DARK;
	private static final int TOP_LEFT = SpriteID.Steelborder.TOP_LEFT;
	private static final int TOP_RIGHT = SpriteID.Steelborder.TOP_RIGHT;
	private static final int BOTTOM_LEFT = SpriteID.Steelborder.BOTTOM_LEFT;
	private static final int BOTTOM_RIGHT = SpriteID.Steelborder.BOTTOM_RIGHT;
	private static final int TOP = SpriteID.Steelborder2.EDGE_TOP;
	private static final int RIGHT = SpriteID.Steelborder2.EDGE_RIGHT;
	private static final int BOTTOM = SpriteID.Miscgraphics.IRON_RIVETS_HORIZONTAL;
	private static final int LEFT = SpriteID.Miscgraphics.IRON_RIVETS_VERTICAL;
	private static final int DIVIDER = SpriteID.SteelborderDivider._0;
	private static final int ARROW_UP = SpriteID.ScrollbarV2.ARROW_UP;
	private static final int ARROW_DOWN = SpriteID.ScrollbarV2.ARROW_DOWN;
	private static final int THUMB_TOP = SpriteID.ScrollbarDraggerV2.TOP;
	private static final int THUMB_MIDDLE = SpriteID.ScrollbarDraggerV2.MIDDLE;
	private static final int THUMB_BOTTOM = SpriteID.ScrollbarDraggerV2.BOTTOM;
	private static final int TRACK = SpriteID.ScrollbarDraggerV2.TRACK;
	// The stone outline of a box, as round the equipment screen's buttons: corners, then edges, in the order
	// top left, top right, bottom left, bottom right, left, top, right, bottom. The focused box's is the
	// lighter one those buttons have under the mouse.
	private static final int[] FIELD_EDGES = {
		SpriteID.V2StoneButton.TOP_LEFT, SpriteID.V2StoneButton.TOP_RIGHT, SpriteID.V2StoneButton.BOTTOM_LEFT,
		SpriteID.V2StoneButton.BOTTOM_RIGHT, SpriteID.V2StoneButton.LEFT, SpriteID.V2StoneButton.TOP,
		SpriteID.V2StoneButton.RIGHT, SpriteID.V2StoneButton.BOTTOM,
	};
	private static final int[] FOCUSED_FIELD_EDGES = {
		SpriteID.V2StoneButtonIn.A_TOP_LEFT, SpriteID.V2StoneButtonIn.A_TOP_RIGHT, SpriteID.V2StoneButtonIn.A_BOTTOM_LEFT,
		SpriteID.V2StoneButtonIn.A_BOTTOM_RIGHT, SpriteID.V2StoneButtonIn.A_LEFT, SpriteID.V2StoneButtonIn.A_TOP,
		SpriteID.V2StoneButtonIn.A_RIGHT, SpriteID.V2StoneButtonIn.A_BOTTOM,
	};
	private static final int[] SPRITES = {
		BACKGROUND, FIELD, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, TOP, RIGHT, BOTTOM, LEFT, DIVIDER,
		ARROW_UP, ARROW_DOWN, THUMB_TOP, THUMB_MIDDLE, THUMB_BOTTOM, TRACK,
	};

	// How far the field's darker backing is inset from its outline's outer edge
	private static final int FIELD_INSET = 2;
	// Frames of a few sizes are kept, for the popup, the list beside the bank and their boxes of notes
	private static final int FRAMES_KEPT = 6;

	private final Sprites sprites;
	private final Map<Long, BufferedImage> frames = new LinkedHashMap<Long, BufferedImage>(16, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, BufferedImage> eldest)
		{
			return size() > FRAMES_KEPT;
		}
	};

	public PickerSkin(Sprites sprites)
	{
		this.sprites = sprites;
	}

	/**
	 * Whether every sprite this draws with can be had. If one can't, the picker is better drawn in its colors.
	 * Every sprite is asked for, rather than stopping at the first that can't be had, so that the game is asked to
	 * load all of them at once.
	 */
	public boolean isComplete()
	{
		boolean complete = true;
		for (int id : SPRITES)
		{
			complete &= sprites.get(id) != null;
		}
		for (int i = 0; i < FIELD_EDGES.length; i++)
		{
			complete &= sprites.get(FIELD_EDGES[i]) != null;
			complete &= sprites.get(FOCUSED_FIELD_EDGES[i]) != null;
		}
		return complete;
	}

	/**
	 * The average color of the background, for the colors that are worked out from the background's, or the
	 * color theme's own background when the background is see-through.
	 */
	public Color getBackground()
	{
		return average(sprites.get(BACKGROUND), PickerTheme.DEFAULT_BACKGROUND);
	}

	/**
	 * The average color of the darker backing of input boxes.
	 */
	public Color getField()
	{
		return average(sprites.get(FIELD), PickerTheme.DEFAULT_HEADER);
	}

	/**
	 * Draws the background and the frame around it.
	 *
	 * @param divider how far down the bounds to put a divider across, under a title, or 0 for none
	 */
	public void paintFrame(Graphics2D g, Rectangle bounds, int divider)
	{
		// Composed once for each size, since it's the same every frame
		final long key = (long) bounds.width << 40 | (long) bounds.height << 20 | divider;
		BufferedImage frame = frames.get(key);
		if (frame == null)
		{
			frame = composeFrame(bounds.width, bounds.height, divider);
			frames.put(key, frame);
		}
		g.drawImage(frame, bounds.x, bounds.y, null);
	}

	/**
	 * The bank's steel frame laid out round a box of this size, on its background.
	 */
	private BufferedImage composeFrame(int width, int height, int divider)
	{
		final BufferedImage frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = frame.createGraphics();
		try
		{
			tile(g, BACKGROUND, 0, 0, width, height);
			final int left = width(LEFT);
			final int right = width(RIGHT);
			if (divider > 0)
			{
				tile(g, DIVIDER, left, divider, width - left - right, height(DIVIDER));
			}

			final int bottom = height(BOTTOM);
			tile(g, TOP, width(TOP_LEFT), 0, width - width(TOP_LEFT) - width(TOP_RIGHT), height(TOP));
			tile(g, BOTTOM, width(BOTTOM_LEFT), height - bottom, width - width(BOTTOM_LEFT) - width(BOTTOM_RIGHT), bottom);
			tile(g, LEFT, 0, height(TOP_LEFT), left, height - height(TOP_LEFT) - height(BOTTOM_LEFT));
			tile(g, RIGHT, width - right, height(TOP_RIGHT), right, height - height(TOP_RIGHT) - height(BOTTOM_RIGHT));

			// Each corner kept to its own quarter, so that on a box too small for them they meet rather than overlap
			final int midX = width / 2;
			final int midY = height / 2;
			corner(g, TOP_LEFT, 0, 0, new Rectangle(0, 0, midX, midY));
			corner(g, TOP_RIGHT, width - width(TOP_RIGHT), 0, new Rectangle(midX, 0, width - midX, midY));
			corner(g, BOTTOM_LEFT, 0, height - height(BOTTOM_LEFT), new Rectangle(0, midY, midX, height - midY));
			corner(g, BOTTOM_RIGHT, width - width(BOTTOM_RIGHT), height - height(BOTTOM_RIGHT),
				new Rectangle(midX, midY, width - midX, height - midY));
		}
		finally
		{
			g.dispose();
		}
		return frame;
	}

	private void corner(Graphics2D g, int id, int x, int y, Rectangle quarter)
	{
		final Shape clip = g.getClip();
		g.clip(quarter);
		draw(g, id, x, y);
		g.setClip(clip);
	}

	/**
	 * Draws an input box: the darker backing inside a stone outline.
	 */
	public void paintField(Graphics2D g, Rectangle box, boolean focused)
	{
		tile(g, FIELD, box.x + FIELD_INSET, box.y + FIELD_INSET, box.width - 2 * FIELD_INSET, box.height - 2 * FIELD_INSET);
		final int[] edges = focused ? FOCUSED_FIELD_EDGES : FIELD_EDGES;
		final int topLeft = edges[0];
		final int topRight = edges[1];
		final int bottomLeft = edges[2];
		final int bottomRight = edges[3];
		final int right = width(edges[6]);
		final int bottom = height(edges[7]);
		final int x = box.x;
		final int y = box.y;
		final int x2 = box.x + box.width;
		final int y2 = box.y + box.height;
		tile(g, edges[4], x, y + height(topLeft), width(edges[4]), box.height - height(topLeft) - height(bottomLeft));
		tile(g, edges[5], x + width(topLeft), y, box.width - width(topLeft) - width(topRight), height(edges[5]));
		tile(g, edges[6], x2 - right, y + height(topRight), right, box.height - height(topRight) - height(bottomRight));
		tile(g, edges[7], x + width(bottomLeft), y2 - bottom, box.width - width(bottomLeft) - width(bottomRight), bottom);
		draw(g, topLeft, x, y);
		draw(g, topRight, x2 - width(topRight), y);
		draw(g, bottomLeft, x, y2 - height(bottomLeft));
		draw(g, bottomRight, x2 - width(bottomRight), y2 - height(bottomRight));
	}

	/**
	 * Draws the game's scrollbar.
	 *
	 * @param track the part the thumb moves along
	 * @param up    the arrow above it, or null for none
	 * @param down  the arrow below it, or null for none
	 */
	public void paintScrollbar(Graphics2D g, Rectangle track, Rectangle thumb, Rectangle up, Rectangle down)
	{
		tile(g, TRACK, track.x, track.y, track.width, track.height);
		if (up != null)
		{
			centre(g, ARROW_UP, up);
		}
		if (down != null)
		{
			centre(g, ARROW_DOWN, down);
		}
		final int top = height(THUMB_TOP);
		final int bottom = height(THUMB_BOTTOM);
		tile(g, THUMB_MIDDLE, thumb.x, thumb.y + top, thumb.width, thumb.height - top - bottom);
		tile(g, THUMB_TOP, thumb.x, thumb.y, thumb.width, top);
		tile(g, THUMB_BOTTOM, thumb.x, thumb.y + thumb.height - bottom, thumb.width, bottom);
	}

	/**
	 * How wide a sprite is, which for one with nothing to show is nothing.
	 */
	private int width(int id)
	{
		final BufferedImage sprite = sprites.get(id);
		return sprite == EMPTY ? 0 : sprite.getWidth();
	}

	private int height(int id)
	{
		final BufferedImage sprite = sprites.get(id);
		return sprite == EMPTY ? 0 : sprite.getHeight();
	}

	private void draw(Graphics2D g, int id, int x, int y)
	{
		final BufferedImage sprite = sprites.get(id);
		if (sprite != EMPTY)
		{
			g.drawImage(sprite, x, y, null);
		}
	}

	/**
	 * Draws a sprite in the middle of a box, pixel for pixel, as a pack's smaller arrow is meant to be.
	 */
	private void centre(Graphics2D g, int id, Rectangle box)
	{
		draw(g, id, box.x + (box.width - width(id)) / 2, box.y + (box.height - height(id)) / 2);
	}

	/**
	 * Repeats a sprite across an area from its top left corner, cut off at the area's edges, as the game tiles one.
	 */
	private void tile(Graphics2D g, int id, int x, int y, int width, int height)
	{
		final BufferedImage sprite = sprites.get(id);
		if (sprite == EMPTY || width <= 0 || height <= 0)
		{
			return;
		}
		for (int tileY = y; tileY < y + height; tileY += sprite.getHeight())
		{
			final int h = Math.min(sprite.getHeight(), y + height - tileY);
			for (int tileX = x; tileX < x + width; tileX += sprite.getWidth())
			{
				final int w = Math.min(sprite.getWidth(), x + width - tileX);
				g.drawImage(sprite, tileX, tileY, tileX + w, tileY + h, 0, 0, w, h, null);
			}
		}
	}

	/**
	 * The part of an image that isn't see-through, or {@link #EMPTY} for an image that all is. A pack can leave a
	 * sprite blank on purpose, such as a scrollbar with no track, and then there is simply nothing to draw.
	 */
	static BufferedImage trim(BufferedImage image)
	{
		int left = image.getWidth();
		int top = image.getHeight();
		int right = -1;
		int bottom = -1;
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				if (image.getRGB(x, y) >>> 24 != 0)
				{
					left = Math.min(left, x);
					right = Math.max(right, x);
					top = Math.min(top, y);
					bottom = Math.max(bottom, y);
				}
			}
		}
		if (right < 0)
		{
			return EMPTY;
		}
		final BufferedImage trimmed = new BufferedImage(right - left + 1, bottom - top + 1, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = trimmed.createGraphics();
		g.drawImage(image, -left, -top, null);
		g.dispose();
		return trimmed;
	}

	/**
	 * The average color of an image's visible pixels, or the fallback when it has none.
	 */
	private static Color average(BufferedImage image, Color fallback)
	{
		long red = 0;
		long green = 0;
		long blue = 0;
		long count = 0;
		for (int y = 0; image != EMPTY && y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				final int pixel = image.getRGB(x, y);
				if (pixel >>> 24 != 0)
				{
					red += pixel >> 16 & 0xff;
					green += pixel >> 8 & 0xff;
					blue += pixel & 0xff;
					count++;
				}
			}
		}
		return count == 0 ? fallback : new Color((int) (red / count), (int) (green / count), (int) (blue / count));
	}
}
