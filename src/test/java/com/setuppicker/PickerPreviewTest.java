package com.setuppicker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;
import javax.imageio.ImageIO;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.ui.FontManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Renders both forms of the picker to build/preview.png, for eyeballing the look without logging in.
 */
public class PickerPreviewTest
{
	private static final int WIDTH = 765;
	private static final int HEIGHT = 503;

	private static List<SetupEntry> setups()
	{
		final List<SetupEntry> setups = new ArrayList<>();
		setups.add(new SetupEntry("Vorkath (dhcb)", true, null, 1));
		setups.add(new SetupEntry("Zulrah", true, new Color(80, 200, 120), 2));
		setups.add(new SetupEntry("Vardorvis", false, null, 3));
		setups.add(new SetupEntry("Tombs of Amascut 300 invocation", false, new Color(230, 190, 90), 4));
		setups.add(new SetupEntry("Barrows", false, null, 5));
		setups.add(new SetupEntry("Slayer melee", false, null, 6));
		setups.add(new SetupEntry("Slayer range", false, null, 7));
		setups.add(new SetupEntry("Slayer burst", false, null, 8));
		for (int i = 0; i < 20; i++)
		{
			setups.add(new SetupEntry("Clue step " + i, false, null, 9 + i));
		}
		return setups;
	}

	// stand-in for item sprites, with the outline the game draws round them, halved as the overlay halves them
	private static final IntFunction<BufferedImage> ICONS = id ->
	{
		final BufferedImage image = new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = image.createGraphics();
		g.setColor(new Color(0x000001));
		g.fillOval(5, 3, 26, 26);
		g.setColor(Color.getHSBColor(id * 0.13f, 0.6f, 0.9f));
		g.fillOval(6, 4, 24, 24);
		g.dispose();
		return SetupIcons.halve(image);
	};

	@Test
	public void iconsKeepTheItemsColorsAndGetTheirOutlineBack()
	{
		final int outline = 0xff000001;
		final int shadow = 0xff302020;
		// 8x6, so 4x3 halved, and 6x5 with the margin round it
		final BufferedImage sprite = new BufferedImage(8, 6, BufferedImage.TYPE_INT_ARGB);
		// block (0, 0), at the sprite's corner: the outline, the shadow, a dull grey and a red; the red wins
		sprite.setRGB(0, 0, outline);
		sprite.setRGB(1, 0, shadow);
		sprite.setRGB(0, 1, 0xff808080);
		sprite.setRGB(1, 1, 0xffcc2020);
		// block (1, 0): only outline and shadow, which never win, so it stays empty but for the outline round the red
		sprite.setRGB(2, 0, outline);
		sprite.setRGB(3, 1, shadow);
		// block (3, 2), at the opposite corner: a single green pixel among nothing
		sprite.setRGB(7, 5, 0xff20a040);

		final BufferedImage icon = SetupIcons.halve(sprite);
		assertEquals(4 + 2, icon.getWidth());
		assertEquals(3 + 2, icon.getHeight());
		// each block in the middle, a pixel in from the margin: pixels of the sprite's own, not blends
		assertEquals(0xffcc2020, icon.getRGB(1, 1));
		assertEquals(0xff20a040, icon.getRGB(4, 3));
		// the outline all round each, even at the sprite's edges, which the margin leaves room for
		for (Point p : Arrays.asList(new Point(0, 1), new Point(1, 0), new Point(2, 1), new Point(1, 2),
			new Point(3, 3), new Point(5, 3), new Point(4, 2), new Point(4, 4)))
		{
			assertEquals(p.toString(), outline, icon.getRGB(p.x, p.y));
		}
		// the shadow down and to the right of the item and its outline
		assertEquals(shadow, icon.getRGB(2, 2));
		assertEquals(shadow, icon.getRGB(5, 4));
		// and nothing where there is none of the item near
		assertEquals(0, icon.getRGB(0, 0));
		assertEquals(0, icon.getRGB(4, 1));
		assertEquals(0, icon.getRGB(0, 4));
	}

	@Test
	public void renderPreview() throws Exception
	{
		final BufferedImage image = new BufferedImage(WIDTH * 2, HEIGHT, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = image.createGraphics();
		g.setColor(new Color(60, 90, 60));
		g.fillRect(0, 0, WIDTH * 2, HEIGHT);

		// Left: the hotkey popup, mid-search
		final PickerModel palette = new PickerModel();
		palette.setSetups(setups());
		palette.setActiveSetup("Slayer melee");
		palette.openPalette();
		for (char c : "sl".toCharArray())
		{
			palette.typeChar(c);
		}
		final PickerLayout paletteLayout = PickerLayout.computePalette(new Rectangle(WIDTH, HEIGHT), 240,
			palette.view().getRows().size(), PickerLayout.ROW_HEIGHT_ICONS, 10);
		palette.setVisibleRows(paletteLayout.getVisibleRows());
		palette.moveSelection(1);
		new PickerPainter(PickerTheme.DEFAULT).paint(g, paletteLayout, palette.view(), null, ICONS, FontManager.getRunescapeSmallFont(), "");
		assertTrue(paletteLayout.isPalette());
		assertEquals(3, paletteLayout.getVisibleRows());

		// Below it: the same popup recolored, as someone matching a resource pack might
		final PickerTheme recolored = new PickerTheme(new Color(20, 28, 44, 240), new Color(34, 48, 74),
			new Color(70, 92, 130), new Color(110, 190, 255), new Color(225, 232, 245));
		g.translate(0, 180);
		new PickerPainter(recolored).paint(g, paletteLayout, palette.view(), null, ICONS, FontManager.getRunescapeSmallFont(), "");
		g.translate(0, -180);

		// Right: docked beside a stand-in bank, scrolled, with the mouse over a row
		g.translate(WIDTH, 0);
		final Rectangle bank = new Rectangle(220, 60, 488, 300);
		g.setColor(new Color(73, 64, 52));
		g.fill(bank);
		final PickerModel docked = new PickerModel();
		final List<SetupSection> sections = Arrays.asList(
			new SetupSection("Bossing", new Color(120, 170, 255), Arrays.asList("Vorkath (dhcb)", "Zulrah", "Vardorvis")),
			new SetupSection("Slayer", null, Arrays.asList("Slayer melee", "Slayer range", "Slayer burst", "Vorkath (dhcb)")));
		docked.setRows(SetupRepository.group(setups(), sections, false, true, Arrays.asList("Barrows", "Zulrah")), setups().size());
		docked.setActiveSetup("Zulrah");
		final PickerLayout dockedLayout = PickerLayout.compute(bank, WIDTH, true, 160, false, true,
			docked.view().getRows().size(), PickerLayout.ROW_HEIGHT_ICONS, 0);
		docked.setVisibleRows(dockedLayout.getVisibleRows());
		docked.scrollBy(1);
		final Rectangle hovered = dockedLayout.getRow(3);
		new PickerPainter(PickerTheme.DEFAULT).paint(g, dockedLayout, docked.view(), new Point(hovered.x + 20, hovered.y + 5), ICONS,
			FontManager.getRunescapeSmallFont(), "");

		// Collapsed, on the bank's other side here so both can be seen: a narrow upright tab
		final PickerLayout collapsed = PickerLayout.compute(bank, WIDTH, false, 160, true, true,
			docked.view().getRows().size(), PickerLayout.ROW_HEIGHT_ICONS, 0);
		new PickerPainter(PickerTheme.DEFAULT).paint(g, collapsed, docked.view(), null, ICONS, FontManager.getRunescapeSmallFont(), "");
		g.dispose();

		assertTrue(collapsed.isCollapsed());
		assertEquals(bank.x + bank.width + PickerLayout.GAP, collapsed.getBounds().x);
		assertEquals(bank.y, collapsed.getBounds().y);
		assertTrue(collapsed.getBounds().height > collapsed.getBounds().width);
		assertEquals(collapsed.getBounds(), collapsed.getHeader());

		// left of the bank, same top, and no taller than it
		assertEquals(bank.x - PickerLayout.GAP, dockedLayout.getBounds().x + dockedLayout.getBounds().width);
		assertEquals(bank.y, dockedLayout.getBounds().y);
		assertTrue(dockedLayout.getBounds().height <= bank.height);
		assertEquals(3, dockedLayout.rowAt(new Point(hovered.x + 20, hovered.y + 5)));

		final File out = new File("build/preview.png");
		out.getParentFile().mkdirs();
		ImageIO.write(image, "png", out);
	}

	@Test
	public void renderMixedPreview() throws Exception
	{
		final BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = image.createGraphics();
		g.setColor(new Color(60, 90, 60));
		g.fillRect(0, 0, WIDTH, HEIGHT);

		// Inventory setups and bank tags together: each row is marked with which it is
		final List<SetupEntry> entries = new ArrayList<>(setups().subList(0, 5));
		entries.add(SetupEntry.bankTag("Clues", 20));
		entries.add(SetupEntry.bankTag("Zulrah", 21));
		final List<SetupSection> sections = Arrays.asList(
			new SetupSection("Bossing", new Color(120, 170, 255), Arrays.asList("Vorkath (dhcb)", "Zulrah", "Vardorvis")));
		final PickerModel model = new PickerModel();
		model.setRows(SetupRepository.group(entries, sections, false, true, Arrays.asList(SetupEntry.bankTagKey("Clues"))), entries.size());
		model.setActiveBankTag("Zulrah");
		final Rectangle bank = new Rectangle(220, 60, 488, 300);
		g.setColor(new Color(73, 64, 52));
		g.fill(bank);
		final PickerLayout layout = PickerLayout.compute(bank, WIDTH, true, 160, false, true,
			model.view().getRows().size(), PickerLayout.ROW_HEIGHT_ICONS, 0);
		model.setVisibleRows(layout.getVisibleRows());
		new PickerPainter(PickerTheme.DEFAULT).paint(g, layout, model.view(), null, ICONS, FontManager.getRunescapeSmallFont(), "");
		g.dispose();

		assertTrue(model.view().isMixed());
		final File out = new File("build/preview-mixed.png");
		out.getParentFile().mkdirs();
		ImageIO.write(image, "png", out);
	}

	// The frame's corners and the stone outlines', each top left, top right, bottom left and bottom right
	private static final int[][] CORNERS = {
		{SpriteID.Steelborder.TOP_LEFT, SpriteID.Steelborder.TOP_RIGHT, SpriteID.Steelborder.BOTTOM_LEFT, SpriteID.Steelborder.BOTTOM_RIGHT},
		{SpriteID.V2StoneButton.TOP_LEFT, SpriteID.V2StoneButton.TOP_RIGHT, SpriteID.V2StoneButton.BOTTOM_LEFT, SpriteID.V2StoneButton.BOTTOM_RIGHT},
		{SpriteID.V2StoneButtonIn.A_TOP_LEFT, SpriteID.V2StoneButtonIn.A_TOP_RIGHT, SpriteID.V2StoneButtonIn.A_BOTTOM_LEFT,
			SpriteID.V2StoneButtonIn.A_BOTTOM_RIGHT},
	};

	/**
	 * Stand-ins for the game's interface sprites, at their sizes in the cache, for checking the layout of the
	 * game's frame. The real ones only come from the game.
	 */
	private static final PickerSkin.Sprites GAME_SPRITES = id ->
	{
		final Dimension size;
		final Color color;
		switch (id)
		{
			case SpriteID.TRADEBACKING:
			case SpriteID.TRADEBACKING_DARK:
				size = new Dimension(88, 60);
				color = id == SpriteID.TRADEBACKING ? new Color(72, 62, 51) : new Color(52, 44, 35);
				break;
			case SpriteID.Steelborder.TOP_LEFT:
			case SpriteID.Steelborder.TOP_RIGHT:
			case SpriteID.Steelborder.BOTTOM_LEFT:
			case SpriteID.Steelborder.BOTTOM_RIGHT:
				size = new Dimension(25, 30);
				color = new Color(30, 30, 28);
				break;
			case SpriteID.Steelborder2.EDGE_RIGHT:
			case SpriteID.Miscgraphics.IRON_RIVETS_VERTICAL:
				size = new Dimension(6, 36);
				color = new Color(45, 45, 42);
				break;
			case SpriteID.Steelborder2.EDGE_TOP:
			case SpriteID.Miscgraphics.IRON_RIVETS_HORIZONTAL:
			case SpriteID.SteelborderDivider._0:
				size = new Dimension(36, 6);
				color = new Color(45, 45, 42);
				break;
			case SpriteID.ScrollbarV2.ARROW_UP:
			case SpriteID.ScrollbarV2.ARROW_DOWN:
				size = new Dimension(16, 16);
				color = new Color(110, 100, 80);
				break;
			case SpriteID.ScrollbarDraggerV2.TOP:
			case SpriteID.ScrollbarDraggerV2.MIDDLE:
			case SpriteID.ScrollbarDraggerV2.BOTTOM:
			case SpriteID.ScrollbarDraggerV2.TRACK:
				size = new Dimension(16, 5);
				color = id == SpriteID.ScrollbarDraggerV2.TRACK ? new Color(40, 34, 26) : new Color(110, 100, 80);
				break;
			case SpriteID.V2StoneButton.LEFT:
			case SpriteID.V2StoneButton.RIGHT:
			case SpriteID.V2StoneButtonIn.A_LEFT:
			case SpriteID.V2StoneButtonIn.A_RIGHT:
				size = new Dimension(3, 9);
				color = new Color(95, 88, 72);
				break;
			case SpriteID.V2StoneButton.TOP:
			case SpriteID.V2StoneButton.BOTTOM:
			case SpriteID.V2StoneButtonIn.A_TOP:
			case SpriteID.V2StoneButtonIn.A_BOTTOM:
				size = new Dimension(9, 3);
				color = new Color(95, 88, 72);
				break;
			default:
				// the stone outline's corners
				size = new Dimension(9, 9);
				color = new Color(95, 88, 72);
		}
		final BufferedImage sprite = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = sprite.createGraphics();
		g.setColor(color);
		boolean corner = false;
		for (int[] set : CORNERS)
		{
			for (int i = 0; i < set.length; i++)
			{
				if (set[i] == id)
				{
					// an L, as the real corners are, with see-through inside it
					final int thickness = set == CORNERS[0] ? 6 : 3;
					g.fillRect(i % 2 == 0 ? 0 : size.width - thickness, 0, thickness, size.height);
					g.fillRect(0, i < 2 ? 0 : size.height - thickness, size.width, thickness);
					corner = true;
				}
			}
		}
		if (!corner)
		{
			g.fillRect(0, 0, size.width, size.height);
		}
		g.dispose();
		return sprite;
	};

	@Test
	public void renderGamePreview() throws Exception
	{
		final BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = image.createGraphics();
		g.setColor(new Color(60, 90, 60));
		g.fillRect(0, 0, WIDTH, HEIGHT);

		// The list beside the bank drawn with the game's sprites, scrolled, with notes shown for the hovered setup
		final PickerSkin skin = new PickerSkin(GAME_SPRITES);
		assertTrue(skin.isComplete());
		final PickerTheme theme = new PickerTheme(skin.getBackground(), skin.getField(), new Color(0x2e2b23),
			PickerTheme.DEFAULT_ACCENT, PickerTheme.DEFAULT_TEXT);
		final List<SetupEntry> entries = new ArrayList<>(setups());
		entries.set(2, new SetupEntry("Vardorvis", false, null, 3, "Bring a stamina potion"));
		final PickerModel model = new PickerModel();
		model.setShowNotes(true);
		model.setSetups(entries);
		model.setActiveSetup("Zulrah");
		final Rectangle bank = new Rectangle(220, 60, 488, 300);
		g.setColor(new Color(73, 64, 52));
		g.fill(bank);
		final PickerLayout layout = PickerLayout.compute(bank, WIDTH, true, 160, false, true,
			model.view().getRows().size(), PickerLayout.ROW_HEIGHT_ICONS, 0, PickerLayout.Frame.GAME);
		model.setVisibleRows(layout.getVisibleRows());
		final Point mouse = new Point(layout.getRow(2).x + 20, layout.getRow(2).y + 5);
		final PickerPainter painter = new PickerPainter(theme, "Setups", skin);
		painter.paint(g, layout, model.view(), mouse, ICONS, FontManager.getRunescapeSmallFont(), "");
		painter.paintNotes(g, layout, model.view(), mouse, FontManager.getRunescapeSmallFont(), new Rectangle(WIDTH, HEIGHT), true);
		g.dispose();

		// the frame's background shows inside it, and the bank is left alone
		assertEquals(skin.getBackground().getRGB(), image.getRGB(layout.getTitle().x + layout.getTitle().width - 30, layout.getTitle().y + 1));
		assertEquals(new Color(73, 64, 52).getRGB(), image.getRGB(bank.x + 5, bank.y + 5));
		final File out = new File("build/preview-game.png");
		out.getParentFile().mkdirs();
		ImageIO.write(image, "png", out);
	}

	@Test
	public void gameFrameMakesRoomForItself()
	{
		final Rectangle bank = new Rectangle(220, 60, 488, 300);
		final PickerLayout flat = PickerLayout.compute(bank, WIDTH, true, 160, false, true, 30, 20, 5);
		final PickerLayout game = PickerLayout.compute(bank, WIDTH, true, 160, false, true, 30, 20, 5, PickerLayout.Frame.GAME);
		// the frame goes round what's inside, which is as wide as in colors, and the list is still beside the bank
		assertEquals(flat.getBounds().width + 2 * 6, game.getBounds().width);
		assertEquals(flat.getList().width, game.getList().width);
		assertEquals(flat.getSearch().width, game.getSearch().width);
		assertEquals(flat.getTitle().width, game.getTitle().width);
		assertEquals(bank.x - PickerLayout.GAP, game.getBounds().x + game.getBounds().width);
		assertEquals(game.getBounds().x + 6 + PickerLayout.PADDING, game.getList().x);
		assertEquals(flat.getBounds().height + 3 * 6, game.getBounds().height);
		assertEquals(game.getTitle().y + game.getTitle().height - game.getBounds().y, game.getDivider());
		assertTrue(game.getHeader().contains(game.getTitle()));
		// flat, the title bar is all of what can be taken hold of
		assertEquals(flat.getHeader(), flat.getTitle());
		assertEquals(0, flat.getDivider());

		// the game's scrollbar has its arrows, and its thumb runs between them
		final Rectangle bar = game.getScrollbar(30);
		final Rectangle up = game.getScrollUp(30);
		final Rectangle down = game.getScrollDown(30);
		final Rectangle track = game.getScrollTrack(30);
		assertEquals(16, bar.width);
		assertEquals(bar.y, up.y);
		assertEquals(up.y + up.height, track.y);
		assertEquals(down.y, track.y + track.height);
		assertEquals(bar.y + bar.height, down.y + down.height);
		for (int scroll = 0; scroll <= 25; scroll++)
		{
			final Rectangle thumb = game.getScrollThumb(30, scroll);
			assertTrue(track.contains(thumb));
			assertEquals(scroll, game.scrollAt(30, thumb.y));
		}
		// the flat one has none, nor has one too short for them
		assertNull(flat.getScrollUp(30));
		assertEquals(flat.getScrollbar(30), flat.getScrollTrack(30));
		final PickerLayout small = PickerLayout.compute(bank, WIDTH, true, 160, false, true, 30, 20, 2, PickerLayout.Frame.GAME);
		assertNull(small.getScrollDown(30));
		assertEquals(small.getScrollbar(30), small.getScrollTrack(30));

		// collapsed, it is its title bar in the frame, or an upright tab as wide as the title bar is tall
		final PickerLayout collapsed = PickerLayout.compute(bank, WIDTH, true, 160, true, false, 30, 20, 0, PickerLayout.Frame.GAME);
		assertEquals(PickerLayout.HEADER_HEIGHT + 12, collapsed.getBounds().height);
		assertEquals(collapsed.getBounds(), collapsed.getHeader());
		assertFalse(collapsed.isUprightTab());
		final PickerLayout tab = PickerLayout.compute(bank, WIDTH, true, 160, true, true, 30, 20, 0, PickerLayout.Frame.GAME);
		assertTrue(tab.isUprightTab());
		assertEquals(PickerLayout.COLLAPSED_WIDTH, tab.getTitle().width);
		assertEquals(bank.x - PickerLayout.GAP, tab.getBounds().x + tab.getBounds().width);

		// with no room either side, it sits over the bank's edge, frame and all
		final Rectangle fixed = new Rectangle(12, 2, 488, 334);
		final PickerLayout over = PickerLayout.compute(fixed, 512, true, 160, false, true, 30, 20, 0, PickerLayout.Frame.GAME);
		assertEquals(fixed.x, over.getBounds().x);
		assertEquals(172, over.getBounds().width);
		final PickerLayout overRight = PickerLayout.compute(fixed, 512, false, 160, false, true, 30, 20, 0, PickerLayout.Frame.GAME);
		assertEquals(fixed.x + fixed.width, overRight.getBounds().x + overRight.getBounds().width);

		// dragged into a corner, all of it stays on the canvas
		final Rectangle canvas = new Rectangle(WIDTH, 500);
		final PickerLayout cornered = PickerLayout.computeMoved(bank, canvas, new Point(900, 900), 160, true, 30, 20, 0, PickerLayout.Frame.GAME);
		assertEquals(new Rectangle(WIDTH - 172, 500 - PickerLayout.HEADER_HEIGHT - 12, 172, PickerLayout.HEADER_HEIGHT + 12), cornered.getBounds());
		assertEquals(new Point(WIDTH - 172, 0), PickerLayout.clamp(new Point(900, -50), 160, canvas, PickerLayout.Frame.GAME));
		// and with a max of rows, no more than that
		assertEquals(4, PickerLayout.computeMoved(bank, canvas, new Point(-200, 100), 160, false, 30, 20, 4, PickerLayout.Frame.GAME).getVisibleRows());

		// the popup is as wide inside as in colors and centred, and on a canvas too narrow for its frame fits the canvas
		final PickerLayout popup = PickerLayout.computePalette(new Rectangle(WIDTH, HEIGHT), 240, 30, 20, 10, PickerLayout.Frame.GAME);
		assertEquals(PickerLayout.computePalette(new Rectangle(WIDTH, HEIGHT), 240, 30, 20, 10).getList().width, popup.getList().width);
		assertEquals((WIDTH - 252) / 2, popup.getBounds().x);
		final PickerLayout cramped = PickerLayout.computePalette(new Rectangle(245, 200), 240, 30, 20, 10, PickerLayout.Frame.GAME);
		assertEquals(0, cramped.getBounds().x);
		assertEquals(245, cramped.getBounds().width);
		assertTrue(cramped.getBounds().y + cramped.getBounds().height <= 200);
	}

	@Test
	public void frameGrowsAwayFromTheBank()
	{
		final Rectangle canvas = new Rectangle(WIDTH, 500);
		final Rectangle bank = new Rectangle(220, 60, 488, 300);
		final PickerLayout.Frame game = PickerLayout.Frame.GAME;

		// dragged left of the bank with a gap: the edge facing the bank stays, and the frame goes on the left
		final Point left = new Point(-180, 40);
		final Rectangle flatLeft = PickerLayout.computeMoved(bank, canvas, left, 160, false, 30, 20, 0).getBounds();
		final Rectangle gameLeft = PickerLayout.computeMoved(bank, canvas, left, 160, false, 30, 20, 0, game).getBounds();
		assertEquals(flatLeft.x + flatLeft.width, gameLeft.x + gameLeft.width);
		assertEquals(flatLeft.x - 12, gameLeft.x);
		assertEquals(flatLeft.y, gameLeft.y);

		// dragged right of it: the left edge stays, and the frame goes on the right
		final Point right = new Point(bank.width + 10, 40);
		final Rectangle wide = new Rectangle(1000, 500);
		final Rectangle flatRight = PickerLayout.computeMoved(bank, wide, right, 160, false, 30, 20, 0).getBounds();
		final Rectangle gameRight = PickerLayout.computeMoved(bank, wide, right, 160, false, 30, 20, 0, game).getBounds();
		assertEquals(flatRight.x, gameRight.x);
		assertEquals(flatRight.x + flatRight.width + 12, gameRight.x + gameRight.width);

		// collapsed, the same
		final Rectangle barLeft = PickerLayout.computeMoved(bank, canvas, left, 160, true, 30, 20, 0, game).getBounds();
		assertEquals(flatLeft.x + flatLeft.width, barLeft.x + barLeft.width);
		final Rectangle barRight = PickerLayout.computeMoved(bank, wide, right, 160, true, 30, 20, 0, game).getBounds();
		assertEquals(flatRight.x, barRight.x);

		// the place saved is the same with the frame or without, so turning it on and off comes back to the same spot
		for (Point offset : Arrays.asList(left, right))
		{
			for (PickerLayout.Frame frame : Arrays.asList(PickerLayout.Frame.FLAT, game))
			{
				final Point corner = PickerLayout.movedCorner(bank, wide, offset, 160, frame);
				assertEquals(offset, PickerLayout.offsetFor(bank, corner, 160, frame));
			}
		}

		// and it is still kept on the canvas
		assertEquals(0, PickerLayout.computeMoved(bank, canvas, new Point(-215, 40), 160, false, 30, 20, 0, game).getBounds().x);

		// let go against the canvas's edge with the frame on: saved where it was let go, and that place is the same
		// whether it's next drawn with the frame or without, however often that is turned on and off
		final Point savedLeft = PickerLayout.keepOnCanvas(bank, canvas, new Point(-260, 40), 160, game);
		assertEquals(0, PickerLayout.computeMoved(bank, canvas, savedLeft, 160, false, 30, 20, 0, game).getBounds().x);
		assertEquals(12, PickerLayout.computeMoved(bank, canvas, savedLeft, 160, false, 30, 20, 0).getBounds().x);
		assertEquals(savedLeft, PickerLayout.keepOnCanvas(bank, canvas, savedLeft, 160, game));
		assertEquals(savedLeft, PickerLayout.keepOnCanvas(bank, canvas, savedLeft, 160, PickerLayout.Frame.FLAT));
		final Point savedRight = PickerLayout.keepOnCanvas(bank, canvas, new Point(WIDTH, 40), 160, game);
		final Rectangle framedRight = PickerLayout.computeMoved(bank, canvas, savedRight, 160, false, 30, 20, 0, game).getBounds();
		assertEquals(WIDTH, framedRight.x + framedRight.width);
		assertEquals(framedRight.x, PickerLayout.computeMoved(bank, canvas, savedRight, 160, false, 30, 20, 0).getBounds().x);
		assertEquals(savedRight, PickerLayout.keepOnCanvas(bank, canvas, savedRight, 160, game));
		assertEquals(savedRight, PickerLayout.keepOnCanvas(bank, canvas, savedRight, 160, PickerLayout.Frame.FLAT));

		// beside the bank, the gap stays on either side, expanded, collapsed or as an upright tab
		for (boolean collapsed : new boolean[]{false, true})
		{
			for (boolean upright : new boolean[]{false, true})
			{
				final Rectangle beside = PickerLayout.compute(bank, WIDTH, true, 160, collapsed, upright, 30, 20, 0, game).getBounds();
				assertEquals(bank.x - PickerLayout.GAP, beside.x + beside.width);
				final Rectangle other = PickerLayout.compute(bank, 1000, false, 160, collapsed, upright, 30, 20, 0, game).getBounds();
				assertEquals(bank.x + bank.width + PickerLayout.GAP, other.x);
			}
		}
	}

	@Test
	public void spritesAreTrimmedToWhatShows()
	{
		// an edge as the cache keeps it, a strip within a larger box
		final BufferedImage box = new BufferedImage(36, 36, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = box.createGraphics();
		g.setColor(Color.GRAY);
		g.fillRect(0, 15, 36, 6);
		g.dispose();
		final BufferedImage strip = PickerSkin.trim(box);
		assertEquals(36, strip.getWidth());
		assertEquals(6, strip.getHeight());
		assertEquals(Color.GRAY.getRGB(), strip.getRGB(0, 0));
		// one with nothing to show isn't missing: a pack can leave a sprite blank on purpose
		assertSame(PickerSkin.EMPTY, PickerSkin.trim(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)));
	}

	@Test
	public void blankSpritesDrawNothing()
	{
		// a pack with no scrollbar track and a see-through background, as duckscape's and others are
		final PickerSkin skin = new PickerSkin(id -> id == SpriteID.ScrollbarDraggerV2.TRACK || id == SpriteID.TRADEBACKING
			? PickerSkin.EMPTY : GAME_SPRITES.get(id));
		assertTrue(skin.isComplete());
		// the background's color falls back to the theme's rather than coming from no pixels at all
		assertEquals(PickerTheme.DEFAULT_BACKGROUND, skin.getBackground());

		final BufferedImage image = new BufferedImage(200, 300, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = image.createGraphics();
		final PickerLayout layout = PickerLayout.computePalette(new Rectangle(200, 300), 160, 30, 20, 8, PickerLayout.Frame.GAME);
		skin.paintFrame(g, layout.getBounds(), layout.getDivider());
		final Rectangle track = layout.getScrollTrack(30);
		skin.paintScrollbar(g, track, layout.getScrollThumb(30, 22), layout.getScrollUp(30), layout.getScrollDown(30));
		g.dispose();
		// nothing behind the frame's middle, nor in the track above the thumb at the bottom
		assertEquals(0, image.getRGB(layout.getList().x + 20, layout.getList().y + 20));
		assertEquals(0, image.getRGB(track.x + 4, track.y + 4));
	}

	@Test
	public void renderNotesPreview() throws Exception
	{
		final BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = image.createGraphics();
		g.setColor(new Color(60, 90, 60));
		g.fillRect(0, 0, WIDTH, HEIGHT);

		// The popup with the selection on a setup that has notes, which are shown beside it
		final List<SetupEntry> entries = new ArrayList<>(setups().subList(0, 4));
		entries.add(1, new SetupEntry("Zulrah (tbow)", false, null, 30,
			"Ring of recoil in the inventory, not worn.\nSwap to the blowpipe for the jad phase and remember the antivenom+ from the bank chest"));
		final PickerModel model = new PickerModel();
		model.setShowNotes(true);
		model.setSetups(entries);
		model.openPalette();
		final PickerLayout layout = PickerLayout.computePalette(new Rectangle(WIDTH, HEIGHT), 240,
			model.view().getRows().size(), PickerLayout.ROW_HEIGHT_ICONS, 10);
		model.setVisibleRows(layout.getVisibleRows());
		model.moveSelection(1);
		final PickerPainter painter = new PickerPainter(PickerTheme.DEFAULT);
		final Font font = FontManager.getRunescapeSmallFont();
		painter.paint(g, layout, model.view(), null, ICONS, font, "");
		painter.paintNotes(g, layout, model.view(), null, font, new Rectangle(WIDTH, HEIGHT), false);

		final FontMetrics fm = g.getFontMetrics(font);
		final List<String> lines = PickerPainter.wrap(fm, entries.get(1).getNotes(), 228, 8);
		// the line break is kept, and no line is wider than the box
		assertTrue(lines.get(0).startsWith("Ring of recoil"));
		assertTrue(lines.stream().anyMatch(line -> line.startsWith("Swap to")));
		assertEquals(entries.get(1).getNotes().replace('\n', ' '), String.join(" ", lines));
		for (String line : lines)
		{
			assertTrue(line, fm.stringWidth(line) <= 228);
		}
		// too much to show is cut short
		final List<String> cut = PickerPainter.wrap(fm, entries.get(1).getNotes(), 228, 2);
		assertEquals(2, cut.size());
		assertTrue(cut.get(1).endsWith("..."));
		assertTrue(fm.stringWidth(cut.get(1)) <= 228);
		// a word longer than a line is broken up
		for (String line : PickerPainter.wrap(fm, "https://example.com/a/very/long/link/that/does/not/fit/on/one/line/at/all", 100, 8))
		{
			assertTrue(line, fm.stringWidth(line) <= 100);
		}
		g.dispose();

		final File out = new File("build/preview-notes.png");
		out.getParentFile().mkdirs();
		ImageIO.write(image, "png", out);
	}

	@Test
	public void scrollbarThumbFollowsTheScrollPositionAndBack()
	{
		// 30 rows, 10 of them showing
		final PickerLayout layout = PickerLayout.computePalette(new Rectangle(WIDTH, HEIGHT), 240, 30, PickerLayout.ROW_HEIGHT_ICONS, 10);
		final Rectangle bar = layout.getScrollbar(30);
		assertEquals(layout.getList().y, bar.y);
		assertEquals(layout.getList().height, bar.height);
		assertEquals(layout.getList().x + layout.getList().width, bar.x + bar.width);

		// a third of the list shows, so the thumb is a third of the bar, and runs from its top to its bottom
		assertEquals(bar.height / 3, layout.getScrollThumb(30, 0).height);
		assertEquals(bar.y, layout.getScrollThumb(30, 0).y);
		final Rectangle last = layout.getScrollThumb(30, 20);
		assertEquals(bar.y + bar.height, last.y + last.height);

		// dragging the thumb to where it is for a scroll position gives that position, and stops at either end
		for (int scroll = 0; scroll <= 20; scroll++)
		{
			assertEquals(scroll, layout.scrollAt(30, layout.getScrollThumb(30, scroll).y));
		}
		assertEquals(0, layout.scrollAt(30, bar.y - 500));
		assertEquals(20, layout.scrollAt(30, bar.y + 500));

		// nothing to scroll: no scrollbar
		assertNull(layout.getScrollbar(10));
		assertNull(layout.getScrollThumb(10, 0));
		assertEquals(0, layout.scrollAt(10, bar.y + 50));
	}

	@Test
	public void dockedFallsBackWhenThereIsNoRoom()
	{
		// fixed mode: the bank spans the game area, with the inventory to its right
		final Rectangle bank = new Rectangle(12, 2, 488, 334);
		final PickerLayout left = PickerLayout.compute(bank, WIDTH, true, 160, false, true, 5, 20, 0);
		assertEquals(bank.x + bank.width + PickerLayout.GAP, left.getBounds().x);

		final PickerLayout none = PickerLayout.compute(bank, 512, true, 160, true, false, 5, 20, 0);
		assertEquals(bank.x, none.getBounds().x);
		assertTrue(none.isCollapsed());
		assertEquals(-1, none.rowAt(new Point(bank.x + 5, bank.y + 5)));
		// with the upright tab turned off, collapsing leaves the full-width title bar
		assertFalse(none.isUprightTab());
		assertEquals(160, none.getBounds().width);
		assertEquals(PickerLayout.HEADER_HEIGHT, none.getBounds().height);

		// the upright tab is narrow enough to fit beside the bank where the list doesn't
		final PickerLayout tab = PickerLayout.compute(bank, 530, true, 160, true, true, 5, 20, 0);
		assertTrue(tab.isUprightTab());
		assertEquals(bank.x + bank.width + PickerLayout.GAP, tab.getBounds().x);
	}

	@Test
	public void dockedShowsNoMoreThanItsMaxRows()
	{
		final Rectangle bank = new Rectangle(220, 60, 488, 300);
		final PickerLayout unlimited = PickerLayout.compute(bank, WIDTH, true, 160, false, true, 30, 20, 0);
		assertEquals(12, unlimited.getVisibleRows());

		final PickerLayout limited = PickerLayout.compute(bank, WIDTH, true, 160, false, true, 30, 20, 5);
		assertEquals(5, limited.getVisibleRows());
		assertEquals(5 * 20, limited.getList().height);
		assertTrue(limited.getBounds().height < unlimited.getBounds().height);
		// the rest are scrolled to
		assertNotNull(limited.getScrollbar(30));

		// fewer setups than the limit, or less room than it, and it is no taller than it has to be
		assertEquals(3, PickerLayout.compute(bank, WIDTH, true, 160, false, true, 3, 20, 5).getVisibleRows());
		assertEquals(12, PickerLayout.compute(bank, WIDTH, true, 160, false, true, 30, 20, 40).getVisibleRows());
	}

	@Test
	public void draggedListGoesWhereItIsPutAndStaysOnTheCanvas()
	{
		final Rectangle canvas = new Rectangle(WIDTH, 500);
		final Rectangle bank = new Rectangle(220, 60, 488, 300);
		final PickerLayout moved = PickerLayout.computeMoved(bank, canvas, new Point(-200, 100), 160, false, 30, 20, 0);
		assertEquals(new Point(20, 160), moved.getBounds().getLocation());
		assertEquals(160, moved.getBounds().width);
		// it runs to the bottom of the canvas, not of the bank
		assertEquals((500 - moved.getList().y - PickerLayout.PADDING) / 20, moved.getVisibleRows());
		assertTrue(moved.getBounds().y + moved.getBounds().height > bank.y + bank.height);
		assertTrue(moved.getBounds().y + moved.getBounds().height <= 500);
		assertEquals(4, PickerLayout.computeMoved(bank, canvas, new Point(-200, 100), 160, false, 30, 20, 4).getVisibleRows());

		// it follows the bank
		final Rectangle shifted = new Rectangle(240, 70, 488, 300);
		assertEquals(new Point(40, 170),
			PickerLayout.computeMoved(shifted, canvas, new Point(-200, 100), 160, false, 30, 20, 0).getBounds().getLocation());

		// collapsed, it is its title bar wherever it is
		final PickerLayout collapsed = PickerLayout.computeMoved(bank, canvas, new Point(-200, 100), 160, true, 30, 20, 0);
		assertTrue(collapsed.isCollapsed());
		assertFalse(collapsed.isUprightTab());
		assertEquals(new Rectangle(20, 160, 160, PickerLayout.HEADER_HEIGHT), collapsed.getBounds());

		// off the canvas, it is brought back to where its title bar can be reached
		assertEquals(new Point(0, 0),
			PickerLayout.computeMoved(bank, canvas, new Point(-900, -900), 160, false, 30, 20, 0).getBounds().getLocation());
		final PickerLayout corner = PickerLayout.computeMoved(bank, canvas, new Point(900, 900), 160, false, 30, 20, 0);
		assertEquals(new Point(WIDTH - 160, 500 - PickerLayout.HEADER_HEIGHT), corner.getBounds().getLocation());
		assertEquals(1, corner.getVisibleRows());
	}
}
