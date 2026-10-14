package com.setuppicker;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Draws the picker. Has no dependency on the game client so it can be rendered in tests.
 */
public final class PickerPainter
{
	private static final Color BORDER_OUTER = new Color(14, 13, 15);
	private static final Color FIELD_BACKGROUND = new Color(0, 0, 0, 110);
	private static final Color ROW_HOVER = new Color(255, 255, 255, 28);
	private static final Color SECTION_BACKGROUND = new Color(0, 0, 0, 60);
	private static final Color FAVORITE = new Color(255, 215, 0);
	private static final Color SCROLLBAR = new Color(120, 110, 90);
	private static final Color SCROLLBAR_HOVER = new Color(160, 148, 120);
	private static final Color SCROLLBAR_TRACK = new Color(0, 0, 0, 70);
	private static final Color SHADOW = Color.BLACK;

	static final int ICON_WIDTH = 18;
	static final int ICON_HEIGHT = 16;
	private static final int STAR_SIZE = 7;
	private static final int CLOCK_SIZE = 9;
	private static final int SOURCE_SIZE = 9;
	private static final int NOTE_WIDTH = 7;
	private static final int NOTE_HEIGHT = 9;
	private static final int NOTES_PADDING = 6;
	private static final int NOTES_MAX_LINES = 8;
	private static final int NOTES_WIDTH = 190;
	private static final int SECTION_ARROW_WIDTH = 5;
	// lines of pixels from an arrowhead's flat side to its point
	private static final int ARROW_DEPTH = 5;

	// The marks on rows, a # for each pixel
	private static final String[] STAR = {
		"...#...",
		"..###..",
		"#######",
		".#####.",
		"..###..",
		".##.##.",
		".#...#.",
	};
	private static final String[] CLOCK = {
		"...###...",
		".##...##.",
		".#..#..#.",
		"#...#...#",
		"#...###.#",
		"#.......#",
		".#.....#.",
		".##...##.",
		"...###...",
	};
	private static final String[] NOTE = {
		"#######",
		"#.....#",
		"#.....#",
		"#.###.#",
		"#.....#",
		"#.###.#",
		"#.....#",
		"#.....#",
		"#######",
	};
	// pointing left, with the hole for its string at the point
	private static final String[] TAG = {
		".........",
		"...######",
		"..#######",
		".########",
		"###.#####",
		".########",
		"..#######",
		"...######",
	};
	// six dots, for something that can be taken hold of
	private static final String[] GRIP = {
		"##..##",
		"##..##",
		"......",
		"......",
		"##..##",
		"##..##",
		"......",
		"......",
		"##..##",
		"##..##",
	};
	// head, shoulders and arms, body, legs
	private static final String[] PERSON = {
		"...###...",
		"...###...",
		"...###...",
		".#######.",
		".#.###.#.",
		".#.###.#.",
		"...###...",
		"...#.#...",
		"...#.#...",
	};

	private final PickerTheme theme;
	private final String title;
	private final PickerSkin skin;

	public PickerPainter(PickerTheme theme)
	{
		this(theme, "Setups");
	}

	/**
	 * @param title what the title bar calls the things listed
	 */
	public PickerPainter(PickerTheme theme, String title)
	{
		this(theme, title, null);
	}

	/**
	 * @param skin the game's sprites to draw the frame, search box and scrollbar with, laid out for
	 *             {@link PickerLayout.Frame#GAME}, or null to draw them in the theme's colors
	 */
	public PickerPainter(PickerTheme theme, String title, PickerSkin skin)
	{
		this.theme = theme;
		this.title = title;
		this.skin = skin;
	}

	/**
	 * @param mouse  mouse position on the canvas, or null
	 * @param icons  item id to image, or null to draw no icons. May return null for an image that isn't ready.
	 *               Images of the icon size, or a pixel larger all round, are drawn pixel for pixel in the middle of
	 *               its place; larger ones are stretched to it.
	 * @param status message for the empty list when there are no setups at all
	 */
	public void paint(Graphics2D g, PickerLayout layout, PickerModel.View view, Point mouse,
		IntFunction<BufferedImage> icons, Font font, String status)
	{
		prepare(g);
		g.setFont(font);
		final FontMetrics fm = g.getFontMetrics();

		final Rectangle bounds = layout.getBounds();
		if (skin != null)
		{
			skin.paintFrame(g, bounds, layout.getDivider());
		}
		else
		{
			g.setColor(theme.getBackground());
			g.fill(bounds);
		}

		paintHeader(g, fm, layout, view, mouse);

		if (!layout.isCollapsed())
		{
			paintSearch(g, fm, layout.getSearch(), view);
			paintList(g, fm, layout, view, mouse, icons, status);
		}
		if (layout.isPalette())
		{
			final SetupEntry selected = view.getSelected() < view.getRows().size() ? view.getRows().get(view.getSelected()).getSetup() : null;
			// picking the bank tag that's already open leaves it open, as clicking its tab does
			final boolean closes = selected != null && !selected.isBankTag() && view.isActive(selected);
			text(g, truncate(fm, "Up/Down: move   Enter: " + (closes ? "close" : "open") + "   Esc: cancel",
				layout.getFooter().width - 8), layout.getFooter().x + 4, baseline(fm, layout.getFooter()), theme.getMutedText());
		}

		if (skin == null)
		{
			paintBorder(g, bounds);
		}
	}

	private void paintBorder(Graphics2D g, Rectangle bounds)
	{
		g.setColor(theme.getBorder());
		g.drawRect(bounds.x + 1, bounds.y + 1, bounds.width - 3, bounds.height - 3);
		g.setColor(BORDER_OUTER);
		g.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
	}

	private void paintHeader(Graphics2D g, FontMetrics fm, PickerLayout layout, PickerModel.View view, Point mouse)
	{
		final Rectangle header = layout.getTitle();
		// on the game's backing the title sits straight on the background, as the bank's does
		if (skin == null)
		{
			g.setColor(theme.getHeader());
			g.fill(header);
		}
		final boolean hovered = !layout.isPalette() && mouse != null && layout.getHeader().contains(mouse);
		if (hovered)
		{
			g.setColor(ROW_HOVER);
			g.fill(header);
		}

		g.setColor(theme.getAccent());
		final String counted = view.getTotal() > 0 ? title + " (" + view.getTotal() + ")" : title;
		if (layout.isUprightTab())
		{
			paintCollapsedTab(g, fm, header, counted);
			return;
		}
		if (layout.isPalette())
		{
			text(g, counted, header.x + 7, baseline(fm, header), theme.getAccent());
			return;
		}
		// a grip, for the list beside the bank being something that can be dragged by its title bar
		mark(g, GRIP, header.x + 5, header.y + header.height / 2 - 5, hovered ? theme.getAccent() : theme.getMutedText());
		text(g, counted, header.x + 16, baseline(fm, header), theme.getAccent());

		// collapse arrow: points down when open, right when collapsed
		final int cx = header.x + header.width - 11;
		final int cy = header.y + header.height / 2;
		if (layout.isCollapsed())
		{
			arrowhead(g, cx - 2, cy, 1, 0, theme.getAccent());
		}
		else
		{
			arrowhead(g, cx, cy - 2, 0, 1, theme.getAccent());
		}
	}

	/**
	 * The collapsed list in its upright form: a vertical tab with the expand arrow at the top and the title reading upwards.
	 */
	private void paintCollapsedTab(Graphics2D g, FontMetrics fm, Rectangle tab, String title)
	{
		final int arrowSpace = 16;
		final int cx = tab.x + tab.width / 2;
		final int cy = tab.y + arrowSpace / 2 + 2;
		arrowhead(g, cx - 2, cy, 1, 0, theme.getAccent());

		// Turned a quarter turn about the tab's bottom left corner, x runs up the tab and y across it
		final AffineTransform upright = g.getTransform();
		final int bottom = tab.y + tab.height;
		g.rotate(-Math.PI / 2, tab.x, bottom);
		final int across = (tab.width - fm.getAscent() - fm.getDescent()) / 2 + fm.getAscent();
		// the shadow still falls down and to the right on screen, which here is back along x and across y
		text(g, truncate(fm, title, tab.height - arrowSpace - 12), tab.x + 7, bottom + across, theme.getAccent(), -1, 1);
		g.setTransform(upright);
	}

	private void paintSearch(Graphics2D g, FontMetrics fm, Rectangle search, PickerModel.View view)
	{
		if (skin != null)
		{
			skin.paintField(g, search, view.isSearchFocused());
		}
		else
		{
			g.setColor(FIELD_BACKGROUND);
			g.fill(search);
			g.setColor(view.isSearchFocused() ? theme.getAccent() : theme.getBorder());
			g.drawRect(search.x, search.y, search.width - 1, search.height - 1);
		}

		// clear of the stone outline's bevelled corners, with the game's sprites
		final int inset = skin != null ? 8 : 5;
		final int textX = search.x + inset;
		final int maxWidth = search.width - 2 * inset;
		if (view.getQuery().isEmpty() && !view.isSearchFocused())
		{
			text(g, "Search...", textX, baseline(fm, search), theme.getMutedText());
			return;
		}

		// A search too long for the box is shown from far enough in to keep the cursor in view
		final String query = view.getQuery();
		final int caret = Math.min(view.getCaret(), query.length());
		int start = 0;
		while (start < caret && fm.stringWidth(query.substring(start, caret)) > maxWidth - 4)
		{
			start++;
		}
		int end = query.length();
		while (end > caret && fm.stringWidth(query.substring(start, end)) > maxWidth - 4)
		{
			end--;
		}
		final String shown = query.substring(start, end);

		if (view.isQuerySelected())
		{
			g.setColor(theme.getActiveRow());
			g.fillRect(textX - 1, search.y + 3, fm.stringWidth(shown) + 2, search.height - 6);
		}
		text(g, shown, textX, baseline(fm, search), theme.getText());

		if (view.isSearchFocused() && System.currentTimeMillis() / 500 % 2 == 0)
		{
			final int caretX = textX + fm.stringWidth(query.substring(start, caret)) + (caret == start ? 0 : 1);
			g.setColor(SHADOW);
			g.drawLine(caretX + 1, search.y + 5, caretX + 1, search.y + search.height - 4);
			g.setColor(theme.getText());
			g.drawLine(caretX, search.y + 4, caretX, search.y + search.height - 5);
		}
	}

	private void paintList(Graphics2D g, FontMetrics fm, PickerLayout layout, PickerModel.View view, Point mouse,
		IntFunction<BufferedImage> icons, String status)
	{
		final Rectangle list = layout.getList();
		final List<PickerRow> rows = view.getRows();

		if (rows.isEmpty())
		{
			final String message = view.getTotal() > 0 ? "No matches" : status;
			text(g, truncate(fm, message, list.width - 8), list.x + 4, baseline(fm, list), theme.getMutedText());
			return;
		}

		final Rectangle scrollbar = layout.getScrollbar(rows.size());
		final int rowWidth = list.width - (scrollbar != null ? scrollbar.width + 2 : 0);

		for (int i = 0; i < layout.getVisibleRows(); i++)
		{
			final int index = view.getScroll() + i;
			if (index >= rows.size())
			{
				break;
			}
			final Rectangle row = layout.getRow(i);
			row.width = rowWidth;
			final boolean hovered = mouse != null && row.contains(mouse);
			final boolean selected = view.isSearchFocused() && index == view.getSelected();
			if (rows.get(index).isHeader())
			{
				paintSectionHeader(g, fm, row, rows.get(index), view.isSectionPages(), hovered, selected);
				continue;
			}
			final SetupEntry setup = rows.get(index).getSetup();

			final boolean active = view.isActive(setup);
			if (active)
			{
				g.setColor(theme.getActiveRow());
				g.fill(row);
			}
			if (hovered || selected)
			{
				g.setColor(ROW_HOVER);
				g.fill(row);
			}
			if (selected)
			{
				g.setColor(theme.getAccent());
				g.drawRect(row.x, row.y, row.width - 1, row.height - 1);
			}

			int textX = row.x + 4;
			if (icons != null)
			{
				final BufferedImage icon = icons.apply(setup.getIconItemId());
				if (icon != null)
				{
					final int iconX = row.x + 2;
					final int iconY = row.y + (row.height - ICON_HEIGHT) / 2;
					if (icon.getWidth() <= ICON_WIDTH + 2 && icon.getHeight() <= ICON_HEIGHT + 2)
					{
						// with the margin for its outline spilling into the row's padding
						g.drawImage(icon, iconX + (ICON_WIDTH - icon.getWidth()) / 2, iconY + (ICON_HEIGHT - icon.getHeight()) / 2, null);
					}
					else
					{
						g.drawImage(icon, iconX, iconY, ICON_WIDTH, ICON_HEIGHT, null);
					}
				}
				textX = row.x + 2 + ICON_WIDTH + 4;
			}

			int textRight = row.x + row.width - 4;
			// rightmost, so that the marks line up down the list
			if (view.isMixed())
			{
				textRight -= SOURCE_SIZE + 3;
				paintSource(g, textRight + 3, row.y + (row.height - SOURCE_SIZE) / 2, setup.isBankTag());
			}
			if (setup.isFavorite())
			{
				textRight -= STAR_SIZE + 3;
				paintStar(g, textRight + 3 + STAR_SIZE / 2, row.y + row.height / 2);
			}
			if (rows.get(index).isRecent())
			{
				textRight -= CLOCK_SIZE + 3;
				paintClock(g, textRight + 3 + CLOCK_SIZE / 2, row.y + row.height / 2);
			}
			if (view.isNotesShown() && !setup.getNotes().isEmpty())
			{
				textRight -= NOTE_WIDTH + 3;
				paintNote(g, textRight + 3, row.y + (row.height - NOTE_HEIGHT) / 2);
			}

			final Color color = setup.getDisplayColor() != null ? setup.getDisplayColor() : (active ? theme.getAccent() : theme.getText());
			text(g, truncate(fm, setup.getName(), textRight - textX), textX, baseline(fm, row), color);
		}

		if (scrollbar != null && skin != null)
		{
			skin.paintScrollbar(g, layout.getScrollTrack(rows.size()), layout.getScrollThumb(rows.size(), view.getScroll()),
				layout.getScrollUp(rows.size()), layout.getScrollDown(rows.size()));
		}
		else if (scrollbar != null)
		{
			g.setColor(SCROLLBAR_TRACK);
			g.fill(scrollbar);
			// it can be dragged, or clicked above or below to jump there
			g.setColor(view.isScrollbarDragged() || mouse != null && scrollbar.contains(mouse) ? SCROLLBAR_HOVER : SCROLLBAR);
			g.fill(layout.getScrollThumb(rows.size(), view.getScroll()));
		}
	}

	/**
	 * @param pickable the heading leads to the section's page, or back from it, so gets an arrow saying which
	 */
	private void paintSectionHeader(Graphics2D g, FontMetrics fm, Rectangle row, PickerRow header,
		boolean pickable, boolean hovered, boolean selected)
	{
		g.setColor(SECTION_BACKGROUND);
		g.fill(row);
		if (pickable && (hovered || selected))
		{
			g.setColor(ROW_HOVER);
			g.fill(row);
		}
		if (pickable && selected)
		{
			g.setColor(theme.getAccent());
			g.drawRect(row.x, row.y, row.width - 1, row.height - 1);
		}

		// muted reads as disabled, which a heading that can be picked isn't
		final Color color = header.getSectionColor() != null ? header.getSectionColor() : (pickable ? theme.getText() : theme.getMutedText());
		final int cy = row.y + row.height / 2;
		int textX = row.x + 4;
		int right = row.x + row.width - 4;
		g.setColor(color);
		if (header.isBack())
		{
			paintArrow(g, textX, cy, -1, color);
			textX += SECTION_ARROW_WIDTH + 4;
		}
		else if (pickable)
		{
			right -= SECTION_ARROW_WIDTH;
			paintArrow(g, right, cy, 1, color);
			right -= 4;
		}

		final String name = truncate(fm, header.getSectionName(), right - textX);
		text(g, name, textX, baseline(fm, row), color);

		// rule filling the rest of the row
		final int ruleX = textX + fm.stringWidth(name) + 5;
		if (ruleX < right)
		{
			g.setColor(theme.getBorder());
			g.drawLine(ruleX, cy, right - 1, cy);
		}
	}

	/**
	 * A small arrowhead filling the section arrow's width from x, pointing right for a direction of 1 and left for -1.
	 */
	private void paintArrow(Graphics2D g, int x, int cy, int direction, Color color)
	{
		arrowhead(g, direction > 0 ? x : x + SECTION_ARROW_WIDTH - 1, cy, direction, 0, color);
	}

	/**
	 * An arrowhead, made of lines of 9, 7, 5, 3 and 1 pixels from its flat side to its point, which drawn pixel by
	 * pixel comes out even on both sides at this size, as a filled triangle doesn't.
	 *
	 * @param x  the middle of its flat side
	 * @param dx the way it points across: 1 for right, -1 for left, or 0 when it points up or down
	 * @param dy the way it points down: 1 for down, -1 for up, or 0 when it points left or right
	 */
	private static void arrowhead(Graphics2D g, int x, int y, int dx, int dy, Color color)
	{
		for (int pass = 0; pass < 2; pass++)
		{
			// shadow first, as for text
			final int offset = pass == 0 ? 1 : 0;
			g.setColor(pass == 0 ? SHADOW : color);
			for (int step = 0; step < ARROW_DEPTH; step++)
			{
				final int half = ARROW_DEPTH - 1 - step;
				if (dx != 0)
				{
					g.fillRect(x + dx * step + offset, y - half + offset, 1, 2 * half + 1);
				}
				else
				{
					g.fillRect(x - half + offset, y + dy * step + offset, 2 * half + 1, 1);
				}
			}
		}
	}

	/**
	 * Marks a setup that has notes: a page with a couple of lines of writing on it.
	 */
	private void paintNote(Graphics2D g, int x, int y)
	{
		mark(g, NOTE, x, y, theme.getMutedText());
	}

	/**
	 * Draws one of the little pictures that mark rows, pixel by pixel so that it is as crisp as the text beside it,
	 * and with the same shadow.
	 *
	 * @param picture rows of the picture, with a # for each pixel to draw
	 * @param x       left of the picture
	 * @param y       top of the picture
	 */
	private static void mark(Graphics2D g, String[] picture, int x, int y, Color color)
	{
		for (int pass = 0; pass < 2; pass++)
		{
			final int offset = pass == 0 ? 1 : 0;
			g.setColor(pass == 0 ? SHADOW : color);
			for (int row = 0; row < picture.length; row++)
			{
				for (int column = 0; column < picture[row].length(); column++)
				{
					if (picture[row].charAt(column) == '#')
					{
						g.fillRect(x + column + offset, y + row + offset, 1, 1);
					}
				}
			}
		}
	}

	/**
	 * Draws the notes of the setup under the mouse, or else of the one the keyboard selection is on, in a box
	 * beside the picker, level with the setup. Nothing is drawn when it has no notes, or when notes aren't shown.
	 *
	 * @param mouse      mouse position on the canvas, or null
	 * @param canvas     the area the box has to stay within
	 * @param preferLeft put the box to the left of the picker when there is room on both sides, as for the list
	 *                   that has the bank on its right. It goes on the other side when there's only room there.
	 */
	public void paintNotes(Graphics2D g, PickerLayout layout, PickerModel.View view, Point mouse, Font font,
		Rectangle canvas, boolean preferLeft)
	{
		if (!view.isNotesShown() || layout.isCollapsed())
		{
			return;
		}
		final List<PickerRow> rows = view.getRows();
		final int hovered = mouse == null ? -1 : layout.rowAt(mouse);
		final int index = hovered >= 0 ? view.getScroll() + hovered : (view.isSearchFocused() ? view.getSelected() : -1);
		final SetupEntry setup = index >= 0 && index < rows.size() ? rows.get(index).getSetup() : null;
		if (setup == null || setup.getNotes().isEmpty())
		{
			return;
		}

		prepare(g);
		g.setFont(font);
		final FontMetrics fm = g.getFontMetrics();
		final Rectangle bounds = layout.getBounds();
		final int leftRoom = bounds.x - canvas.x;
		final int rightRoom = canvas.x + canvas.width - bounds.x - bounds.width;
		// clear of the game's thicker frame too, which goes round the room the text has rather than taking from it
		final int padding = Math.max(NOTES_PADDING, layout.getFrame().edge + 4);
		final int boxWidth = NOTES_WIDTH + 2 * (padding - NOTES_PADDING);
		final boolean left = leftRoom >= boxWidth && (preferLeft || rightRoom < boxWidth)
			// too narrow a canvas for it either way: whichever side has more of it
			|| rightRoom < boxWidth && leftRoom > rightRoom;
		final int width = Math.max(4 * padding, Math.min(boxWidth, left ? leftRoom : rightRoom));
		final List<String> lines = wrap(fm, setup.getNotes(), width - 2 * padding, NOTES_MAX_LINES);
		final int height = lines.size() * fm.getHeight() + 2 * padding;

		// sharing the picker's edge, or against the game's frame, and starting level with the setup unless that runs
		// off the bottom
		final int row = index - view.getScroll();
		final int level = row >= 0 && row < layout.getVisibleRows() ? layout.getRow(row).y : layout.getList().y;
		final int y = Math.max(canvas.y, Math.min(level, canvas.y + canvas.height - height));
		final int overlap = skin == null ? 1 : 0;
		final Rectangle box = new Rectangle(left ? bounds.x - width + overlap : bounds.x + bounds.width - overlap, y, width, height);

		if (skin != null)
		{
			skin.paintFrame(g, box, 0);
		}
		else
		{
			g.setColor(theme.getBackground());
			g.fill(box);
			paintBorder(g, box);
		}

		int baseline = box.y + padding + fm.getAscent();
		for (String line : lines)
		{
			text(g, line, box.x + padding, baseline, theme.getText());
			baseline += fm.getHeight();
		}
	}

	/**
	 * Breaks text into lines no wider than given, at spaces where it can, keeping the line breaks it has.
	 *
	 * @param maxLines the most lines to return. Text that would take more is cut short there with an ellipsis.
	 */
	static List<String> wrap(FontMetrics fm, String text, int maxWidth, int maxLines)
	{
		final List<String> lines = new ArrayList<>();
		for (String paragraph : text.replace("\r", "").replace('\t', ' ').split("\n"))
		{
			String line = "";
			for (String word : paragraph.trim().split(" +"))
			{
				// a word too long for a line of its own is broken wherever it has to be
				while (fm.stringWidth(word) > maxWidth && word.length() > 1)
				{
					if (!line.isEmpty())
					{
						lines.add(line);
						line = "";
					}
					int fits = 1;
					while (fits < word.length() && fm.stringWidth(word.substring(0, fits + 1)) <= maxWidth)
					{
						fits++;
					}
					lines.add(word.substring(0, fits));
					word = word.substring(fits);
				}
				final String longer = line.isEmpty() ? word : line + " " + word;
				if (fm.stringWidth(longer) <= maxWidth)
				{
					line = longer;
				}
				else
				{
					lines.add(line);
					line = word;
				}
			}
			lines.add(line);
		}
		if (lines.size() <= maxLines)
		{
			return lines;
		}
		final List<String> cut = new ArrayList<>(lines.subList(0, maxLines));
		String last = cut.get(maxLines - 1);
		while (!last.isEmpty() && fm.stringWidth(last + "...") > maxWidth)
		{
			last = last.substring(0, last.length() - 1);
		}
		cut.set(maxLines - 1, last + "...");
		return cut;
	}

	/**
	 * Marks a recently used setup: a clock face showing about three o'clock.
	 */
	private void paintClock(Graphics2D g, int cx, int cy)
	{
		mark(g, CLOCK, cx - CLOCK_SIZE / 2, cy - CLOCK_SIZE / 2, theme.getMutedText());
	}

	/**
	 * Marks where a row is from when both are listed: a luggage tag for a bank tag, and for an inventory setup
	 * a little person, after the icon of Inventory Setups.
	 *
	 * @param x left of the mark
	 * @param y top of the mark
	 */
	private void paintSource(Graphics2D g, int x, int y, boolean bankTag)
	{
		mark(g, bankTag ? TAG : PERSON, x, y, theme.getMutedText());
	}

	private void paintStar(Graphics2D g, int cx, int cy)
	{
		mark(g, STAR, cx - STAR_SIZE / 2, cy - STAR_SIZE / 2, FAVORITE);
	}

	/**
	 * Sets the graphics up to draw as the game draws its interfaces. Overlays are given antialiasing, which blurs
	 * the RuneScape fonts' pixels at their edges and softens small shapes, and the game draws both without. The
	 * overlay renderer puts the hints back after each overlay, so this goes no further than the picker.
	 */
	static void prepare(Graphics2D g)
	{
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
	}

	/**
	 * Draws text as the game draws it on its interfaces: over a black copy of itself a pixel down and to the right.
	 */
	private static void text(Graphics2D g, String text, int x, int y, Color color)
	{
		text(g, text, x, y, color, 1, 1);
	}

	/**
	 * @param shadowX where the shadow goes from the text, in the graphics' own coordinates
	 */
	private static void text(Graphics2D g, String text, int x, int y, Color color, int shadowX, int shadowY)
	{
		g.setColor(SHADOW);
		g.drawString(text, x + shadowX, y + shadowY);
		g.setColor(color);
		g.drawString(text, x, y);
	}

	private static int baseline(FontMetrics fm, Rectangle box)
	{
		return box.y + (box.height - fm.getAscent() - fm.getDescent()) / 2 + fm.getAscent();
	}

	private static String truncate(FontMetrics fm, String text, int maxWidth)
	{
		if (fm.stringWidth(text) <= maxWidth)
		{
			return text;
		}
		final String ellipsis = "...";
		String cut = text;
		while (!cut.isEmpty() && fm.stringWidth(cut + ellipsis) > maxWidth)
		{
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut + ellipsis;
	}
}
