package com.setuppicker;

import java.awt.Canvas;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class PickerInputTest
{
	private final Canvas canvas = new Canvas();
	private int platformModifier = InputEvent.CTRL_DOWN_MASK;
	private boolean requireModifier = true;
	private PickerModel model;
	private PickerInput input;

	@Before
	public void setUp()
	{
		final Client client = (Client) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Client.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getCanvas":
						return canvas;
					case "getGameState":
						return GameState.LOGGED_IN;
					default:
						throw new UnsupportedOperationException(method.getName());
				}
			});
		final SetupPickerConfig config = new SetupPickerConfig()
		{
			@Override
			public boolean requireModifier()
			{
				return requireModifier;
			}
		};
		model = new PickerModel();
		final List<SetupEntry> setups = new ArrayList<>();
		for (String name : new String[]{"Vorkath", "Zulrah", "Wintertodt", "Slayer", "Barrows"})
		{
			setups.add(new SetupEntry(name, false, null, 1));
		}
		model.setSetups(setups);
		model.setVisibleRows(5);
		input = new PickerInput(client, null, config, new SetupPickerOverlay(client, config, model, null, null), model,
			new PlatformModifier(null)
			{
				@Override
				public int get()
				{
					return platformModifier;
				}
			});
	}

	/**
	 * Delivers a key event the way KeyManager would.
	 *
	 * @param physicalKey the key actually pressed, which real events carry as the extended key code
	 * @param keyCode     the event's key code, which differs from physicalKey once a remapping plugin has had it
	 * @return whether the picker consumed the event
	 */
	private boolean send(int id, int modifiers, int physicalKey, int keyCode, char keyChar)
	{
		final KeyEvent e = new KeyEvent(canvas, id, 0, modifiers, keyCode, keyChar)
		{
			@Override
			public int getExtendedKeyCode()
			{
				return physicalKey;
			}
		};
		switch (id)
		{
			case KeyEvent.KEY_PRESSED:
				input.keyPressed(e);
				break;
			case KeyEvent.KEY_TYPED:
				input.keyTyped(e);
				break;
			default:
				input.keyReleased(e);
				break;
		}
		return e.isConsumed();
	}

	private boolean send(int id, int modifiers, int keyCode, char keyChar)
	{
		return send(id, modifiers, keyCode, keyCode, keyChar);
	}

	// a full press, type, release of a character key, as AWT delivers it
	private void tap(int keyCode, char c)
	{
		assertTrue(send(KeyEvent.KEY_PRESSED, 0, keyCode, c));
		assertTrue(send(KeyEvent.KEY_TYPED, 0, KeyEvent.VK_UNDEFINED, c));
		assertTrue(send(KeyEvent.KEY_RELEASED, 0, keyCode, c));
	}

	private void openWithHotkey()
	{
		assertTrue(send(KeyEvent.KEY_PRESSED, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_K, (char) 0x0b));
		assertTrue(send(KeyEvent.KEY_TYPED, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_UNDEFINED, (char) 0x0b));
		assertTrue(send(KeyEvent.KEY_RELEASED, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_K, (char) 0x0b));
		assertTrue(model.view().isPaletteOpen());
	}

	@Test
	public void keysPassThroughWhileClosed()
	{
		assertFalse(send(KeyEvent.KEY_PRESSED, 0, KeyEvent.VK_W, 'w'));
		assertFalse(send(KeyEvent.KEY_TYPED, 0, KeyEvent.VK_UNDEFINED, 'w'));
		assertFalse(send(KeyEvent.KEY_RELEASED, 0, KeyEvent.VK_W, 'w'));
		assertFalse(send(KeyEvent.KEY_PRESSED, 0, KeyEvent.VK_ENTER, '\n'));
	}

	@Test
	public void typingFiltersWithoutDoubling()
	{
		openWithHotkey();
		tap(KeyEvent.VK_S, 's');
		tap(KeyEvent.VK_W, 'w');
		assertEquals("sw", model.view().getQuery());
		assertEquals(0, model.view().getSelected());
		tap(KeyEvent.VK_BACK_SPACE, '\b');
		assertEquals("s", model.view().getQuery());
		assertEquals(2, model.view().getRows().size());
	}

	@Test
	public void keysRewrittenByKeyRemappingAreStillTyped()
	{
		openWithHotkey();
		// Key Remapping has turned S into Down and W into Up, blanked their characters, and consumed the
		// typed events, so those never arrive
		assertTrue(send(KeyEvent.KEY_PRESSED, 0, KeyEvent.VK_S, KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED));
		assertTrue(send(KeyEvent.KEY_RELEASED, 0, KeyEvent.VK_S, KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED));
		assertEquals("s", model.view().getQuery());
		assertEquals(0, model.view().getSelected());

		assertTrue(send(KeyEvent.KEY_PRESSED, InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_W, KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED));
		assertEquals("sW", model.view().getQuery());
		assertEquals(0, model.view().getSelected());
	}

	@Test
	public void numberKeysRewrittenToFunctionKeysAreStillTyped()
	{
		openWithHotkey();
		// Platform Keys sends 1 as F1, and Key Remapping can do the same
		assertTrue(send(KeyEvent.KEY_PRESSED, 0, KeyEvent.VK_1, KeyEvent.VK_F1, KeyEvent.CHAR_UNDEFINED));
		assertTrue(send(KeyEvent.KEY_PRESSED, 0, KeyEvent.VK_NUMPAD2, KeyEvent.VK_F2, KeyEvent.CHAR_UNDEFINED));
		assertTrue(send(KeyEvent.KEY_PRESSED, 0, KeyEvent.VK_SPACE, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED));
		assertEquals("12 ", model.view().getQuery());
		// a real function key types nothing
		assertTrue(send(KeyEvent.KEY_PRESSED, 0, KeyEvent.VK_F5, KeyEvent.CHAR_UNDEFINED));
		assertEquals("12 ", model.view().getQuery());
	}

	@Test
	public void arrowsNavigateAndEscapeCloses()
	{
		openWithHotkey();
		assertTrue(send(KeyEvent.KEY_PRESSED, 0, KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED));
		assertTrue(send(KeyEvent.KEY_PRESSED, 0, KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED));
		assertTrue(send(KeyEvent.KEY_PRESSED, 0, KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED));
		assertEquals("Zulrah", model.getSelectedSetup().getName());

		assertTrue(send(KeyEvent.KEY_PRESSED, 0, KeyEvent.VK_ESCAPE, (char) 0x1b));
		assertFalse(model.view().isPaletteOpen());
		// the release of the key that closed it doesn't leak to the game either
		assertTrue(send(KeyEvent.KEY_RELEASED, 0, KeyEvent.VK_ESCAPE, (char) 0x1b));
		assertFalse(send(KeyEvent.KEY_PRESSED, 0, KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED));
	}

	@Test
	public void hotkeyTogglesAndItsCharacterIsNotTyped()
	{
		openWithHotkey();
		assertEquals("", model.view().getQuery());
		assertTrue(send(KeyEvent.KEY_PRESSED, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_K, (char) 0x0b));
		assertFalse(model.view().isPaletteOpen());
	}

	@Test
	public void keyHeldBeforeOpeningIsStillReleasedToTheGame()
	{
		assertFalse(send(KeyEvent.KEY_PRESSED, 0, KeyEvent.VK_W, 'w'));
		openWithHotkey();
		assertFalse(send(KeyEvent.KEY_RELEASED, 0, KeyEvent.VK_W, 'w'));
	}

	@Test
	public void hotkeyUsesThePlatformModifier()
	{
		platformModifier = InputEvent.META_DOWN_MASK;
		// Ctrl+K is not the shortcut on a Mac, and neither is K alone or Cmd+Shift+K
		assertFalse(send(KeyEvent.KEY_PRESSED, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_K, (char) 0x0b));
		assertFalse(send(KeyEvent.KEY_PRESSED, 0, KeyEvent.VK_K, 'k'));
		assertFalse(send(KeyEvent.KEY_PRESSED, InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_K, 'K'));
		assertFalse(model.view().isPaletteOpen());

		assertTrue(send(KeyEvent.KEY_PRESSED, InputEvent.META_DOWN_MASK, KeyEvent.VK_K, 'k'));
		assertTrue(model.view().isPaletteOpen());
		// Cmd+K leaves a typed 'k' on some systems; it isn't put in the search box
		assertTrue(send(KeyEvent.KEY_TYPED, InputEvent.META_DOWN_MASK, KeyEvent.VK_UNDEFINED, 'k'));
		assertEquals("", model.view().getQuery());

		assertTrue(send(KeyEvent.KEY_PRESSED, InputEvent.META_DOWN_MASK, KeyEvent.VK_K, 'k'));
		assertFalse(model.view().isPaletteOpen());
	}

	@Test
	public void hotkeyWithoutModifierStaysTypeable()
	{
		requireModifier = false;
		assertFalse(send(KeyEvent.KEY_PRESSED, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_K, (char) 0x0b));
		assertTrue(send(KeyEvent.KEY_PRESSED, 0, KeyEvent.VK_K, 'k'));
		assertTrue(send(KeyEvent.KEY_TYPED, 0, KeyEvent.VK_UNDEFINED, 'k'));
		assertTrue(model.view().isPaletteOpen());
		assertEquals("", model.view().getQuery());

		// now it's a letter like any other
		tap(KeyEvent.VK_K, 'k');
		assertTrue(model.view().isPaletteOpen());
		assertEquals("k", model.view().getQuery());
	}

	@Test
	public void platformKeysProfileOverridesTheComputer()
	{
		assertEquals(InputEvent.META_DOWN_MASK, PlatformModifier.resolve(null, true));
		assertEquals(InputEvent.CTRL_DOWN_MASK, PlatformModifier.resolve(null, false));
		assertEquals(InputEvent.META_DOWN_MASK, PlatformModifier.resolve("AUTO", true));
		assertEquals(InputEvent.CTRL_DOWN_MASK, PlatformModifier.resolve("WINDOWS", true));
		assertEquals(InputEvent.META_DOWN_MASK, PlatformModifier.resolve("MAC", false));
	}

	@Test
	public void selectAllHighlightsTheSearchTextForReplacing()
	{
		openWithHotkey();
		tap(KeyEvent.VK_S, 's');
		tap(KeyEvent.VK_L, 'l');

		// Cmd+A, then typing replaces what was there
		assertTrue(send(KeyEvent.KEY_PRESSED, InputEvent.META_DOWN_MASK, KeyEvent.VK_A, 'a'));
		assertTrue(model.view().isQuerySelected());
		assertEquals("sl", model.view().getQuery());
		tap(KeyEvent.VK_Z, 'z');
		assertEquals("z", model.view().getQuery());
		assertFalse(model.view().isQuerySelected());

		// Ctrl+A, then Backspace clears it in one go
		tap(KeyEvent.VK_U, 'u');
		assertTrue(send(KeyEvent.KEY_PRESSED, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_A, (char) 1));
		assertTrue(model.view().isQuerySelected());
		tap(KeyEvent.VK_BACK_SPACE, '\b');
		assertEquals("", model.view().getQuery());
		assertFalse(model.view().isQuerySelected());

		// nothing to highlight in an empty box, and a plain "a" is still just a letter
		assertTrue(send(KeyEvent.KEY_PRESSED, InputEvent.META_DOWN_MASK, KeyEvent.VK_A, 'a'));
		assertFalse(model.view().isQuerySelected());
		tap(KeyEvent.VK_A, 'a');
		assertEquals("a", model.view().getQuery());
	}

	private void press(int keyCode)
	{
		assertTrue(send(KeyEvent.KEY_PRESSED, 0, keyCode, KeyEvent.CHAR_UNDEFINED));
		assertTrue(send(KeyEvent.KEY_RELEASED, 0, keyCode, KeyEvent.CHAR_UNDEFINED));
	}

	@Test
	public void cursorMovesThroughTheSearchText()
	{
		openWithHotkey();
		tap(KeyEvent.VK_S, 's');
		tap(KeyEvent.VK_A, 'a');
		tap(KeyEvent.VK_Y, 'y');
		assertEquals(3, model.view().getCaret());

		// back one and insert the missing letter
		press(KeyEvent.VK_LEFT);
		press(KeyEvent.VK_LEFT);
		tap(KeyEvent.VK_L, 'l');
		assertEquals("slay", model.view().getQuery());
		assertEquals(2, model.view().getCaret());
		assertEquals("Slayer", model.getSelectedSetup().getName());

		// Backspace takes the letter before the cursor, Delete the one after
		tap(KeyEvent.VK_BACK_SPACE, '\b');
		assertEquals("say", model.view().getQuery());
		tap(KeyEvent.VK_DELETE, (char) KeyEvent.VK_DELETE);
		assertEquals("sy", model.view().getQuery());
		assertEquals(1, model.view().getCaret());

		// the ends, which it doesn't run past
		press(KeyEvent.VK_HOME);
		press(KeyEvent.VK_LEFT);
		assertEquals(0, model.view().getCaret());
		tap(KeyEvent.VK_BACK_SPACE, '\b');
		assertEquals("sy", model.view().getQuery());
		press(KeyEvent.VK_END);
		press(KeyEvent.VK_RIGHT);
		assertEquals(2, model.view().getCaret());

		// after select all, an arrow drops the highlight and goes to that end
		assertTrue(send(KeyEvent.KEY_PRESSED, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_A, (char) 1));
		press(KeyEvent.VK_LEFT);
		assertFalse(model.view().isQuerySelected());
		assertEquals(0, model.view().getCaret());
		assertEquals("sy", model.view().getQuery());
	}
}
