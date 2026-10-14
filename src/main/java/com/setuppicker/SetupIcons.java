package com.setuppicker;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.SpritePixels;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * The setups' icons at the size the lists draw them. Item sprites are 36x32 and the lists show them at half that.
 * Drawn straight from the sprite, one pixel of every two by two is kept whatever it is, so a thin part such as a
 * staff's shaft or a bow's string is kept where it happens to fall on that pixel and lost where it falls on the
 * outline instead. Each icon is halved once here, still pixel for pixel, but keeping the item's own colours
 * wherever it has any, and then given back the game's outline and shadow at the small size.
 * <p>
 * Client thread only, which is where overlays render and item sprites finish loading.
 */
public class SetupIcons
{
	// The outline and shadow the game draws round an item's sprite, as ItemManager has it draw them
	private static final int OUTLINE = 0xff000001;
	private static final int SHADOW = 0xff000000 | SpritePixels.DEFAULT_SHADOW_COLOR;

	private final ItemManager itemManager;
	private final Map<Integer, BufferedImage> icons = new HashMap<>();

	SetupIcons(ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	/**
	 * The icon of an item at the lists' size, or null when there is none. Until the item's sprite has loaded,
	 * the sprite itself.
	 */
	public BufferedImage get(int itemId)
	{
		final BufferedImage icon = icons.get(itemId);
		if (icon != null)
		{
			return icon;
		}
		final AsyncBufferedImage sprite = itemManager.getImage(itemId);
		if (sprite == null)
		{
			return null;
		}
		// in the meantime the sprite stands in for it, so that it is only asked for once
		icons.put(itemId, sprite);
		sprite.onLoaded(() -> icons.put(itemId, halve(sprite)));
		return sprite;
	}

	/**
	 * Halves an item sprite without blending: each pixel of the result is one of the two by two it stands for.
	 * Of those it is the item's most vivid one, so that thin coloured parts survive, and only where there is none
	 * of the item is it left empty. The outline and shadow are then drawn round the result as the game draws them
	 * round a sprite, a pixel wide at the new size.
	 * <p>
	 * The item can reach the sprite's edge once halved, so the result has a pixel's margin all round for its outline
	 * and shadow: it is two pixels wider and taller than half the sprite, with the halved sprite in the middle.
	 */
	static BufferedImage halve(BufferedImage sprite)
	{
		final int width = sprite.getWidth() / 2 + 2;
		final int height = sprite.getHeight() / 2 + 2;
		final int[] source = sprite.getRGB(0, 0, sprite.getWidth(), sprite.getHeight(), null, 0, sprite.getWidth());
		final int[] item = new int[width * height];
		for (int y = 1; y < height - 1; y++)
		{
			for (int x = 1; x < width - 1; x++)
			{
				int best = 0;
				for (int dy = 0; dy < 2; dy++)
				{
					for (int dx = 0; dx < 2; dx++)
					{
						final int pixel = source[(2 * (y - 1) + dy) * sprite.getWidth() + 2 * (x - 1) + dx];
						if (isItem(pixel) && (best == 0 || vividness(pixel) > vividness(best)))
						{
							best = pixel;
						}
					}
				}
				item[y * width + x] = best;
			}
		}

		// the outline round the item, then the shadow down and to the right of both, as SpritePixels draws them
		final int[] outlined = item.clone();
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				if (item[y * width + x] == 0 && (x > 0 && item[y * width + x - 1] != 0 || x < width - 1 && item[y * width + x + 1] != 0
					|| y > 0 && item[(y - 1) * width + x] != 0 || y < height - 1 && item[(y + 1) * width + x] != 0))
				{
					outlined[y * width + x] = OUTLINE;
				}
			}
		}
		final int[] shadowed = outlined.clone();
		for (int y = 1; y < height; y++)
		{
			for (int x = 1; x < width; x++)
			{
				if (outlined[y * width + x] == 0 && outlined[(y - 1) * width + x - 1] != 0)
				{
					shadowed[y * width + x] = SHADOW;
				}
			}
		}
		final BufferedImage halved = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		halved.setRGB(0, 0, width, height, shadowed, 0, width);
		return halved;
	}

	/**
	 * Whether a pixel is part of the item itself, not its outline, its shadow or nothing.
	 */
	private static boolean isItem(int pixel)
	{
		return pixel >>> 24 != 0 && pixel != OUTLINE && (pixel | 0xff000000) != SHADOW;
	}

	/**
	 * How strongly coloured a pixel is, with brightness to choose between greys: what makes a pixel stand out as
	 * the detail of a small picture.
	 */
	private static int vividness(int pixel)
	{
		final int red = pixel >> 16 & 0xff;
		final int green = pixel >> 8 & 0xff;
		final int blue = pixel & 0xff;
		final int max = Math.max(red, Math.max(green, blue));
		final int min = Math.min(red, Math.min(green, blue));
		return (max - min) * 4 + max;
	}
}
