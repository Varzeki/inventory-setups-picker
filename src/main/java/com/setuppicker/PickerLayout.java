package com.setuppicker;

import java.awt.Point;
import java.awt.Rectangle;

/**
 * Where the picker's parts are on the canvas for one frame. Shared by painting and mouse hit-testing.
 */
public final class PickerLayout
{
	static final int HEADER_HEIGHT = 20;
	static final int SEARCH_HEIGHT = 20;
	static final int PADDING = 3;
	static final int GAP = 4;
	static final int ROW_HEIGHT_ICONS = 20;
	static final int ROW_HEIGHT_TEXT = 16;
	static final int FOOTER_HEIGHT = 16;
	static final int COLLAPSED_WIDTH = HEADER_HEIGHT;
	static final int COLLAPSED_LENGTH = 96;
	static final int SCROLL_THUMB_MIN = 12;

	/**
	 * How much room the picker's chrome takes, which depends on what it is drawn with.
	 */
	public static final class Frame
	{
		/**
		 * Drawn in flat colors: a thin border inside the padding, and a narrow scrollbar.
		 */
		public static final Frame FLAT = new Frame(0, 0, 6, 0);

		/**
		 * Drawn with the game's sprites: the bank's steel frame round it and under its title, and the game's
		 * scrollbar with its arrows.
		 */
		public static final Frame GAME = new Frame(6, 6, 16, 16);

		// how thick the frame round everything is, outside the width set for the list and the padding inside it
		final int edge;
		// how thick the divider between the title bar and the rest is
		final int divider;
		final int scrollbarWidth;
		// how tall each of the scrollbar's arrows is, or 0 for none
		final int scrollArrow;

		private Frame(int edge, int divider, int scrollbarWidth, int scrollArrow)
		{
			this.edge = edge;
			this.divider = divider;
			this.scrollbarWidth = scrollbarWidth;
			this.scrollArrow = scrollArrow;
		}
	}

	private final Rectangle bounds;
	private final Rectangle header;
	private final Rectangle title;
	private final Rectangle search;
	private final Rectangle list;
	private final Rectangle footer;
	private final int rowHeight;
	private final int visibleRows;
	private final Frame frame;

	private PickerLayout(Rectangle bounds, Rectangle header, Rectangle title, Rectangle search, Rectangle list,
		Rectangle footer, int rowHeight, int visibleRows, Frame frame)
	{
		this.bounds = bounds;
		this.header = header;
		this.title = title;
		this.search = search;
		this.list = list;
		this.footer = footer;
		this.rowHeight = rowHeight;
		this.visibleRows = visibleRows;
		this.frame = frame;
	}

	/**
	 * The hotkey-opened popup, in flat colors.
	 *
	 * @see #computePalette(Rectangle, int, int, int, int, Frame)
	 */
	public static PickerLayout computePalette(Rectangle area, int width, int setupCount, int rowHeight, int maxRows)
	{
		return computePalette(area, width, setupCount, rowHeight, maxRows, Frame.FLAT);
	}

	/**
	 * The hotkey-opened popup: centered horizontally in the game area, in its upper part.
	 *
	 * @param area       the canvas
	 * @param setupCount number of rows to list (setups and section headings), after the search is applied
	 * @param maxRows    most rows to show before scrolling
	 */
	public static PickerLayout computePalette(Rectangle area, int width, int setupCount, int rowHeight, int maxRows,
		Frame frame)
	{
		// the frame goes round the width asked for, so that what's inside has as much room whatever draws it
		width = Math.min(width + 2 * frame.edge, area.width);
		final int chrome = HEADER_HEIGHT + SEARCH_HEIGHT + FOOTER_HEIGHT + 4 * PADDING + 2 * frame.edge + frame.divider;
		final int fittingRows = Math.max(1, (area.height - chrome) / rowHeight);
		final int visibleRows = Math.max(1, Math.min(setupCount, Math.min(maxRows, fittingRows)));

		final int x = area.x + (area.width - width) / 2;
		// Anchored where it would sit with a full list, so it doesn't jump around as typing narrows the results
		final int fullHeight = chrome + Math.min(maxRows, fittingRows) * rowHeight;
		final int y = area.y + Math.max(0, Math.min(area.height / 6, area.height - fullHeight));

		final Rectangle title = new Rectangle(x + frame.edge, y + frame.edge, width - 2 * frame.edge, HEADER_HEIGHT);
		final Rectangle header = new Rectangle(x, y, width, frame.edge + HEADER_HEIGHT);
		final int inset = frame.edge + PADDING;
		final Rectangle search = new Rectangle(x + inset, title.y + title.height + frame.divider + PADDING,
			width - 2 * inset, SEARCH_HEIGHT);
		final Rectangle list = new Rectangle(x + inset, search.y + search.height + PADDING, width - 2 * inset, visibleRows * rowHeight);
		final Rectangle footer = new Rectangle(x + inset, list.y + list.height + PADDING, width - 2 * inset, FOOTER_HEIGHT);
		final Rectangle bounds = new Rectangle(x, y, width, footer.y + footer.height + PADDING + frame.edge - y);
		return new PickerLayout(bounds, header, title, search, list, footer, rowHeight, visibleRows, frame);
	}

	/**
	 * The list docked beside the bank, in flat colors.
	 *
	 * @see #compute(Rectangle, int, boolean, int, boolean, boolean, int, int, int, Frame)
	 */
	public static PickerLayout compute(Rectangle bank, int canvasWidth, boolean preferLeft, int width,
		boolean collapsed, boolean uprightTab, int setupCount, int rowHeight, int maxRows)
	{
		return compute(bank, canvasWidth, preferLeft, width, collapsed, uprightTab, setupCount, rowHeight, maxRows, Frame.FLAT);
	}

	/**
	 * The list docked beside the bank.
	 *
	 * @param bank       bounds of the bank interface
	 * @param preferLeft sit left of the bank when both sides have room
	 * @param uprightTab when collapsed, be a narrow upright tab rather than just the title bar
	 * @param setupCount number of rows to list (setups and section headings), after the search is applied
	 * @param maxRows    most rows to show before scrolling, or 0 for as many as fit down the bank's side
	 */
	public static PickerLayout compute(Rectangle bank, int canvasWidth, boolean preferLeft, int width,
		boolean collapsed, boolean uprightTab, int setupCount, int rowHeight, int maxRows, Frame frame)
	{
		final boolean upright = collapsed && uprightTab;
		// A narrow tab standing against the bank's edge with its title running up it, or else the list with its
		// frame round the width asked for
		width = (upright ? COLLAPSED_WIDTH : width) + 2 * frame.edge;
		final int leftX = bank.x - GAP - width;
		final int rightX = bank.x + bank.width + GAP;
		final boolean leftFits = leftX >= 0;
		final boolean rightFits = rightX + width <= canvasWidth;

		final int x;
		if (leftFits && (preferLeft || !rightFits))
		{
			x = leftX;
		}
		else if (rightFits)
		{
			x = rightX;
		}
		else
		{
			// No room on either side (fixed mode): sit on top of the bank's edge instead
			x = preferLeft ? bank.x : bank.x + bank.width - width;
		}
		final int y = bank.y;

		if (upright)
		{
			final Rectangle tab = new Rectangle(x, y, width, Math.min(COLLAPSED_LENGTH, bank.height));
			final Rectangle title = new Rectangle(x + frame.edge, y + frame.edge, COLLAPSED_WIDTH, tab.height - 2 * frame.edge);
			return new PickerLayout(tab, tab, title, null, null, null, rowHeight, 0, frame);
		}
		return docked(x, y, width, collapsed, bank.y + bank.height, setupCount, rowHeight, maxRows, frame);
	}

	/**
	 * The list of the bank's, dragged away from the bank's side, in flat colors.
	 *
	 * @see #computeMoved(Rectangle, Rectangle, Point, int, boolean, int, int, int, Frame)
	 */
	public static PickerLayout computeMoved(Rectangle bank, Rectangle canvas, Point offset, int width,
		boolean collapsed, int setupCount, int rowHeight, int maxRows)
	{
		return computeMoved(bank, canvas, offset, width, collapsed, setupCount, rowHeight, maxRows, Frame.FLAT);
	}

	/**
	 * The list of the bank's, dragged away from the bank's side to somewhere of its own.
	 *
	 * @param offset     where its top left corner is from the bank's, as drawn without a frame; see {@link #movedCorner}
	 * @param setupCount number of rows to list (setups and section headings), after the search is applied
	 * @param maxRows    most rows to show before scrolling, or 0 for as many as fit above the canvas's bottom
	 */
	public static PickerLayout computeMoved(Rectangle bank, Rectangle canvas, Point offset, int width,
		boolean collapsed, int setupCount, int rowHeight, int maxRows, Frame frame)
	{
		final Point corner = movedCorner(bank, canvas, offset, width, frame);
		return docked(corner.x, corner.y, width + 2 * frame.edge, collapsed, canvas.y + canvas.height, setupCount, rowHeight,
			maxRows, frame);
	}

	/**
	 * Where a dragged list's top left corner goes, frame and all, kept on the canvas.
	 * <p>
	 * The place saved for it is where the list is without a frame. A frame grows it away from the bank, so that
	 * the gap left to the bank stays as it was: to the left for a list left of the bank, to the right for one right
	 * of it, and downwards. Turning the frame on and off moves nothing but the far edges, and the place saved stays
	 * the same either way.
	 *
	 * @param offset where the list's top left corner is from the bank's, as drawn without a frame
	 * @param width  the width set for the list, which its frame goes round
	 */
	public static Point movedCorner(Rectangle bank, Rectangle canvas, Point offset, int width, Frame frame)
	{
		final int x = bank.x + offset.x;
		return clamp(new Point(leftOf(bank, x, width) ? x - 2 * frame.edge : x, bank.y + offset.y), width, canvas, frame);
	}

	/**
	 * The place to save for a dragged list whose top left corner, frame and all, is here: the other way round from
	 * {@link #movedCorner}.
	 */
	public static Point offsetFor(Rectangle bank, Point corner, int width, Frame frame)
	{
		final int framed = corner.x + 2 * frame.edge;
		final int x = leftOf(bank, framed, width) ? framed : corner.x;
		return new Point(x - bank.x, corner.y - bank.y);
	}

	/**
	 * The place to save for a dragged list, moved as little as keeps it on the canvas when drawn. Which side of the
	 * bank it is on, and so which way its frame grows, is kept from the place it was given, so that the place saved
	 * is drawn where the list was let go, with the frame or without it.
	 *
	 * @param offset where the list's top left corner is from the bank's, as drawn without a frame
	 */
	public static Point keepOnCanvas(Rectangle bank, Rectangle canvas, Point offset, int width, Frame frame)
	{
		final int x = bank.x + offset.x;
		final int grown = leftOf(bank, x, width) ? 2 * frame.edge : 0;
		final Point corner = clamp(new Point(x - grown, bank.y + offset.y), width, canvas, frame);
		return new Point(corner.x + grown - bank.x, corner.y - bank.y);
	}

	/**
	 * Whether a list at this place without its frame is to the left of the bank, by their middles.
	 */
	private static boolean leftOf(Rectangle bank, int x, int width)
	{
		return x + width / 2 < bank.x + bank.width / 2;
	}

	/**
	 * The nearest place to a corner that keeps a list of this width, frame and all, or at least its title bar, on the
	 * canvas, where it can be taken hold of again.
	 *
	 * @param width the width set for the list, which its frame goes round
	 * @param frame what the list is drawn with
	 */
	public static Point clamp(Point corner, int width, Rectangle canvas, Frame frame)
	{
		final int x = Math.max(canvas.x, Math.min(corner.x, canvas.x + canvas.width - width - 2 * frame.edge));
		final int y = Math.max(canvas.y, Math.min(corner.y, canvas.y + canvas.height - HEADER_HEIGHT - 2 * frame.edge));
		return new Point(x, y);
	}

	/**
	 * @param bottom how far down the canvas the list may reach
	 */
	private static PickerLayout docked(int x, int y, int width, boolean collapsed, int bottom, int setupCount,
		int rowHeight, int maxRows, Frame frame)
	{
		final Rectangle title = new Rectangle(x + frame.edge, y + frame.edge, width - 2 * frame.edge, HEADER_HEIGHT);
		if (collapsed)
		{
			// just the title bar
			final Rectangle bar = new Rectangle(x, y, width, HEADER_HEIGHT + 2 * frame.edge);
			return new PickerLayout(bar, bar, title, null, null, null, rowHeight, 0, frame);
		}
		final Rectangle header = new Rectangle(x, y, width, frame.edge + HEADER_HEIGHT);
		final int inset = frame.edge + PADDING;
		final Rectangle search = new Rectangle(x + inset, title.y + title.height + frame.divider + PADDING,
			width - 2 * inset, SEARCH_HEIGHT);
		final int listY = search.y + search.height + PADDING;
		final int fittingRows = Math.max(1, (bottom - listY - PADDING - frame.edge) / rowHeight);
		// always leave one row's worth of space for the "no setups" message
		final int visibleRows = Math.max(1, Math.min(setupCount, maxRows > 0 ? Math.min(maxRows, fittingRows) : fittingRows));
		final Rectangle list = new Rectangle(x + inset, listY, width - 2 * inset, visibleRows * rowHeight);
		final Rectangle bounds = new Rectangle(x, y, width, list.y + list.height + PADDING + frame.edge - y);
		return new PickerLayout(bounds, header, title, search, list, null, rowHeight, visibleRows, frame);
	}

	public Rectangle getBounds()
	{
		return bounds;
	}

	/**
	 * Where the title bar can be taken hold of: the title bar and the frame above and beside it, or the whole of
	 * the collapsed list.
	 */
	public Rectangle getHeader()
	{
		return header;
	}

	/**
	 * The title bar itself, inside the frame.
	 */
	public Rectangle getTitle()
	{
		return title;
	}

	public Frame getFrame()
	{
		return frame;
	}

	/**
	 * How far down from the top the divider under the title bar is, or 0 when there is none.
	 */
	public int getDivider()
	{
		return frame.divider > 0 && list != null ? title.y + title.height - bounds.y : 0;
	}

	/**
	 * The search box, or null when collapsed.
	 */
	public Rectangle getSearch()
	{
		return search;
	}

	/**
	 * The area holding the rows, or null when collapsed.
	 */
	public Rectangle getList()
	{
		return list;
	}

	/**
	 * The key hint strip of the popup, or null for the list beside the bank.
	 */
	public Rectangle getFooter()
	{
		return footer;
	}

	public boolean isPalette()
	{
		return footer != null;
	}

	public boolean isCollapsed()
	{
		return list == null;
	}

	/**
	 * Whether this is the collapsed list in its upright tab form.
	 */
	public boolean isUprightTab()
	{
		return list == null && header.height > header.width;
	}

	public int getRowHeight()
	{
		return rowHeight;
	}

	public int getVisibleRows()
	{
		return visibleRows;
	}

	public Rectangle getRow(int row)
	{
		return new Rectangle(list.x, list.y + row * rowHeight, list.width, rowHeight);
	}

	/**
	 * The strip down the right of the list that the scrollbar runs in, arrows and all, or null when everything fits.
	 *
	 * @param rowCount number of rows there are to scroll through
	 */
	public Rectangle getScrollbar(int rowCount)
	{
		if (list == null || rowCount <= visibleRows)
		{
			return null;
		}
		return new Rectangle(list.x + list.width - frame.scrollbarWidth, list.y, frame.scrollbarWidth, list.height);
	}

	/**
	 * The arrow at the top of the scrollbar, which scrolls up a row, or null when it has none: the flat one
	 * doesn't, nor does one too short for them.
	 */
	public Rectangle getScrollUp(int rowCount)
	{
		final Rectangle bar = getScrollbar(rowCount);
		return bar == null || !hasArrows(bar) ? null : new Rectangle(bar.x, bar.y, bar.width, frame.scrollArrow);
	}

	/**
	 * The arrow at the bottom of the scrollbar, which scrolls down a row, or null when it has none.
	 */
	public Rectangle getScrollDown(int rowCount)
	{
		final Rectangle bar = getScrollbar(rowCount);
		return bar == null || !hasArrows(bar) ? null
			: new Rectangle(bar.x, bar.y + bar.height - frame.scrollArrow, bar.width, frame.scrollArrow);
	}

	/**
	 * The part of the scrollbar that its thumb moves along, which is all of it but its arrows, or null when
	 * everything fits.
	 */
	public Rectangle getScrollTrack(int rowCount)
	{
		final Rectangle bar = getScrollbar(rowCount);
		if (bar == null || !hasArrows(bar))
		{
			return bar;
		}
		return new Rectangle(bar.x, bar.y + frame.scrollArrow, bar.width, bar.height - 2 * frame.scrollArrow);
	}

	private boolean hasArrows(Rectangle bar)
	{
		return frame.scrollArrow > 0 && bar.height >= 2 * frame.scrollArrow + SCROLL_THUMB_MIN;
	}

	/**
	 * The part of the scrollbar that stands for the rows in view, or null when everything fits.
	 *
	 * @param scroll index of the first visible row
	 */
	public Rectangle getScrollThumb(int rowCount, int scroll)
	{
		final Rectangle track = getScrollTrack(rowCount);
		if (track == null)
		{
			return null;
		}
		final int height = Math.max(SCROLL_THUMB_MIN, track.height * visibleRows / rowCount);
		final int maxScroll = rowCount - visibleRows;
		final int y = track.y + (track.height - height) * Math.max(0, Math.min(scroll, maxScroll)) / maxScroll;
		return new Rectangle(track.x, y, track.width, height);
	}

	/**
	 * The scroll position that puts the top of the scrollbar's thumb nearest to a height on the canvas, for
	 * dragging it.
	 */
	public int scrollAt(int rowCount, int thumbY)
	{
		final Rectangle track = getScrollTrack(rowCount);
		final Rectangle thumb = getScrollThumb(rowCount, 0);
		if (thumb == null || track.height <= thumb.height)
		{
			return 0;
		}
		final int maxScroll = rowCount - visibleRows;
		final int travel = track.height - thumb.height;
		final int offset = Math.max(0, Math.min(thumbY - track.y, travel));
		return (offset * maxScroll + travel / 2) / travel;
	}

	/**
	 * The visible row under the point, or -1.
	 */
	public int rowAt(Point p)
	{
		if (list == null || !list.contains(p))
		{
			return -1;
		}
		return (p.y - list.y) / rowHeight;
	}
}
