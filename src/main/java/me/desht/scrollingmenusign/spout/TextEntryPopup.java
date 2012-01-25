package me.desht.scrollingmenusign.spout;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import me.desht.scrollingmenusign.SMSException;
import me.desht.scrollingmenusign.ScrollingMenuSign;
import me.desht.scrollingmenusign.expector.ExpectCommandSubstitution;
import me.desht.scrollingmenusign.util.MiscUtil;

import org.bukkit.ChatColor;
import org.getspout.spoutapi.gui.GenericLabel;
import org.getspout.spoutapi.gui.GenericPopup;
import org.getspout.spoutapi.gui.GenericTextField;
import org.getspout.spoutapi.gui.Label;
import org.getspout.spoutapi.gui.Screen;
import org.getspout.spoutapi.gui.TextField;
import org.getspout.spoutapi.keyboard.Keyboard;
import org.getspout.spoutapi.player.SpoutPlayer;

public class TextEntryPopup extends GenericPopup {
	private static Map<String,TextEntryPopup> allPopups = new HashMap<String, TextEntryPopup>();
	private static Set<String> visiblePopups = new HashSet<String>();
	
	private SpoutPlayer sp;
	private Label label;
	private TextField textField;
	
	public TextEntryPopup(SpoutPlayer sp, String prompt) {
		this.sp = sp;
		Screen mainScreen = sp.getMainScreen();

		int width = mainScreen.getWidth() / 2;
		int x = (mainScreen.getWidth() - width) / 2;
		int y = mainScreen.getHeight() / 2 - 20;
		
		label = new GenericLabel(ChatColor.YELLOW + prompt);
		label.setX(x).setY(y).setWidth(width).setHeight(10);
		
		textField = new GenericTextField();
		textField.setX(x).setY(y + label.getHeight() + 2).setWidth(width).setHeight(20);
		textField.setFocus(true	);
		
		Label label2 = new GenericLabel(ChatColor.GRAY + "Press Return to confirm, or Escape to cancel");
		label2.setX(x).setY(mainScreen.getHeight() - 50).setHeight(10);
		
		this.attachWidget(ScrollingMenuSign.getInstance(), label);
		this.attachWidget(ScrollingMenuSign.getInstance(), textField);
		this.attachWidget(ScrollingMenuSign.getInstance(), label2);
	}
	
	private void setPrompt(String prompt) {
		label.setText(prompt);
		textField.setText("");
	}

	public void confirm() {
		ScrollingMenuSign plugin = ScrollingMenuSign.getInstance();
		
		if (plugin.expecter.isExpecting(sp, ExpectCommandSubstitution.class)) {
			try {
				ExpectCommandSubstitution cs = (ExpectCommandSubstitution) plugin.expecter.getAction(sp, ExpectCommandSubstitution.class);
				cs.setSub(textField.getText());
				plugin.expecter.handleAction(sp, cs.getClass());
			} catch (SMSException e) {
				MiscUtil.errorMessage(sp, e.getMessage());
			}
		}
		
		close();
		visiblePopups.remove(sp.getName());
	}

	private void cancel() {
		ScrollingMenuSign.getInstance().expecter.cancelAction(sp, ExpectCommandSubstitution.class);
		
		close();
		visiblePopups.remove(sp.getName());
	}

	public static void show(SpoutPlayer sp, String prompt) {
		TextEntryPopup popup;
		String name = sp.getName();
		if (!allPopups.containsKey(name)) {
			popup = new TextEntryPopup(sp, prompt);
			allPopups.put(sp.getName(), popup);
		} else {
			popup = allPopups.get(name);
			popup.setPrompt(prompt);
		}
		sp.getMainScreen().attachPopupScreen(popup);
		visiblePopups.add(name);
	}
	
	public static boolean isPoppedUp(SpoutPlayer sp) {
		return visiblePopups.contains(sp.getName());
	}

	public static void handleKeypress(SpoutPlayer sp, Keyboard key) {
		switch (key) {
		case KEY_RETURN:
			allPopups.get(sp.getName()).confirm();
			break;
		case KEY_ESCAPE:
			allPopups.get(sp.getName()).cancel();
			break;
		}
	}
}
