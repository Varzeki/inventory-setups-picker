package com.setuppicker;

import java.awt.Color;
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
import net.runelite.client.ui.FontManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
		// the outline and a thin red line in one block, as along a staff, and nothing of the item in the next
		final BufferedImage sprite = new BufferedImage(6, 4, BufferedImage.TYPE_INT_ARGB);
		sprite.setRGB(0, 0, 0xff000001);
		sprite.setRGB(1, 0, 0xff808080);
		sprite.setRGB(0, 1, 0xffcc2020);
		final BufferedImage icon = SetupIcons.halve(sprite);
		assertEquals(3, icon.getWidth());
		assertEquals(2, icon.getHeight());
		// a pixel of the sprite's own, the most colorful of its block, not a blend
		assertEquals(0xffcc2020, icon.getRGB(0, 0));
		// outlined round it at the small size, with the shadow down and to the right
		assertEquals(0xff000001, icon.getRGB(1, 0));
		assertEquals(0xff000001, icon.getRGB(0, 1));
		assertEquals(0xff302020, icon.getRGB(1, 1));
		assertEquals(0, icon.getRGB(2, 0));
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
