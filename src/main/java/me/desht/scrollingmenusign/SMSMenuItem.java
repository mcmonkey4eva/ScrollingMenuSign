package me.desht.scrollingmenusign;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.desht.dhutils.LogUtils;
import me.desht.dhutils.MiscUtil;
import me.desht.dhutils.block.MaterialWithData;
import me.desht.scrollingmenusign.parser.CommandUtils;
import me.desht.scrollingmenusign.views.CommandTrigger;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class SMSMenuItem implements Comparable<SMSMenuItem>, SMSUseLimitable {
	private final String label;
	private final String command;
	private final String message;
	private final List<String> lore;
	private final MaterialWithData iconMaterial;
	private SMSRemainingUses uses;
	private final SMSMenu menu;

	public SMSMenuItem(SMSMenu menu, String label, String command, String message) {
		this(menu, label, command, message, null);
	}

	public SMSMenuItem(SMSMenu menu, String label, String command, String message, String iconMaterialName) {
		this(menu, label, command, message, iconMaterialName, new String[0]);
	}

	public SMSMenuItem(SMSMenu menu, String label, String command, String message, String iconMaterialName, String[] lore) {
		if (label == null || command == null || message == null)
			throw new NullPointerException();
		this.menu = menu;
		this.label = label;
		this.command = command;
		this.message = message;
		try {
			if (iconMaterialName == null || iconMaterialName.isEmpty())
				iconMaterialName = getIconMaterialName();
			this.iconMaterial = MaterialWithData.get(iconMaterialName);
		} catch (IllegalArgumentException e) {
			throw new SMSException("invalid material '" + iconMaterialName + "'");
		}
		this.lore = new ArrayList<String>();
		for (String l : lore) {
			this.lore.add(MiscUtil.parseColourSpec(l));
		}
		this.uses = new SMSRemainingUses(this);
	}

	public SMSMenuItem(SMSMenu menu, ConfigurationSection node) throws SMSException {
		SMSPersistence.mustHaveField(node, "label");
		SMSPersistence.mustHaveField(node, "command");
		SMSPersistence.mustHaveField(node, "message");

		this.menu = menu;
		this.label = MiscUtil.parseColourSpec(node.getString("label"));
		this.command = node.getString("command");
		this.message = MiscUtil.parseColourSpec(node.getString("message"));
		String defMat = getIconMaterialName();
		String iconMat = node.getString("icon", defMat);
		this.iconMaterial = MaterialWithData.get(iconMat);
		this.uses = new SMSRemainingUses(this, node.getConfigurationSection("usesRemaining"));
		this.lore = new ArrayList<String>();
		if (node.contains("lore")) {
			for (String l : node.getStringList("lore")) {
				lore.add(MiscUtil.parseColourSpec(l));
			}
		}
	}

	private String getIconMaterialName() {
		ScrollingMenuSign plugin = ScrollingMenuSign.getInstance();
		if (plugin == null) {
			return "stone";
		} else {
			return plugin.getConfig().getString("sms.inv_view.default_icon", "stone");
		}
	}

	/**
	 * Get the label for this menu item
	 *
	 * @return The label
	 */
	public String getLabel() {
		return label;
	}

	/**
	 * Get the label for this menu item with all colour codes removed
	 *
	 * @return The label
	 */
	public String getLabelStripped() {
		return ChatColor.stripColor(label);
	}

	/**
	 * Get the command for this menu item
	 *
	 * @return The command
	 */
	public String getCommand() {
		return command;
	}

	/**
	 * Get the feedback message for this menu item
	 *
	 * @return The feedback message
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * Return the material used for this menu item's icon, in those views which
	 * support icons.
	 *
	 * @return the material used for the menu item's icon
	 */
	public MaterialWithData getIconMaterial() {
		return iconMaterial;
	}

	/**
	 * Get the lore (tooltip) for this menu item.  Note that not all view types necessarily support
	 * display of lore.
	 *
	 * @return the lore for the menu item, as a String array
	 */
	public String[] getLore() {
		return lore.toArray(new String[lore.size()]);
	}

	/**
	 * Get the lore (tooltip) for this menu item.  Note that not all view types necessarily support
	 * display of lore.
	 *
	 * @return the lore for the menu item, as a list of String
	 */
	public List<String> getLoreAsList() {
		return new ArrayList<String>(lore);
	}

	/**
	 * Append a line of text to the item's lore.
	 *
	 * @param lore the lore text to append
	 */
	public void appendLore(String lore) {
		this.lore.add(lore);
	}

	/**
	 * Replace the item's lore with a line of text.
	 *
	 * @param lore the new lore text for the item
	 */
	public void setLore(String lore) {
		this.lore.clear();
		this.lore.add(lore);
	}

	/**
	 * Executes the command for this item
	 *
	 * @param sender  the command sender who triggered the execution
	 * @param trigger the view that triggered this execution
	 * @throws SMSException if the usage limit for this player is exhausted
	 */
	public void executeCommand(CommandSender sender, CommandTrigger trigger) {
		if (sender instanceof Player) {
			boolean itemUses = verifyRemainingUses(this, (Player) sender);
			boolean menuUses = verifyRemainingUses(menu, (Player) sender);
			if (itemUses) {
				decrementRemainingUses(this, (Player) sender);
			}
			if (menuUses) {
				decrementRemainingUses(menu, (Player) sender);
			}
			if (itemUses || menuUses) {
				menu.autosave();
			}
		}
		String cmd = getCommand();
		if ((cmd == null || cmd.isEmpty()) && !menu.getDefaultCommand().isEmpty()) {
			cmd = menu.getDefaultCommand().replace("<LABEL>", ChatColor.stripColor(getLabel())).replace("<RAWLABEL>", getLabel());
		}

		CommandUtils.executeCommand(sender, cmd, trigger);
	}

	/**
	 * Executes the command for this item
	 *
	 * @param sender the command sender who triggered the execution
	 * @throws SMSException if the usage limit for this player is exhausted
	 */
	public void executeCommand(CommandSender sender) {
		executeCommand(sender, null);
	}

	/**
	 * Verify that the given object (item or menu) has not exhausted its usage limits.
	 *
	 * @param useLimitable the menu or item to check
	 * @param player       the player to check
	 * @return true if there is a valid usage limit, false if the item has no usage limits at all
	 * @throws SMSException if the usage limits for the item were already exhausted
	 */
	private boolean verifyRemainingUses(SMSUseLimitable useLimitable, Player player) throws SMSException {
		String playerName = player.getName();
		SMSRemainingUses limits = useLimitable.getUseLimits();

		if (limits.hasLimitedUses()) {
			String desc = limits.getDescription();
			if (limits.getRemainingUses(playerName) <= 0) {
				throw new SMSException("You can't use that " + desc + " anymore.");
			}
			return true;
		} else {
			return false;
		}
	}

	private void decrementRemainingUses(SMSUseLimitable useLimitable, Player player) {
		String playerName = player.getName();
		SMSRemainingUses limits = useLimitable.getUseLimits();

		if (limits.hasLimitedUses()) {
			String desc = limits.getDescription();
			limits.use(playerName);
			if ((Boolean) menu.getAttributes().get(SMSMenu.REPORT_USES)) {
				MiscUtil.statusMessage(player, "&6[Uses remaining for this " + desc + ": &e" + limits.getRemainingUses(playerName) + "&6]");
			}
		}
	}

	/**
	 * Displays the feedback message for this menu item
	 *
	 * @param player Player to show the message to
	 */
	public void feedbackMessage(Player player) {
		if (player != null) {
			sendFeedback(player, getMessage());
		}
	}

	private void sendFeedback(Player player, String message) {
		sendFeedback(player, message, new HashSet<String>());
	}

	private void sendFeedback(Player player, String message, Set<String> history) {
		if (message == null || message.length() == 0)
			return;
		if (message.startsWith("%")) {
			// macro expansion
			String macro = message.substring(1);
			if (history.contains(macro)) {
				LogUtils.warning("Recursive loop detected in macro [" + macro + "]!");
				throw new SMSException("Recursive loop detected in macro [" + macro + "]!");
			} else if (SMSMacro.hasMacro(macro)) {
				history.add(macro);
				sendFeedback(player, SMSMacro.getCommands(macro), history);
			} else {
				throw new SMSException("No such macro [" + macro + "].");
			}
		} else {
			MiscUtil.alertMessage(player, message);
		}
	}

	private void sendFeedback(Player player, List<String> messages, Set<String> history) {
		for (String m : messages) {
			sendFeedback(player, m, history);
		}
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "SMSMenuItem [label=" + label + ", command=" + command + ", message=" + message + ", icon=" + iconMaterial + "]";
	}

	/**
	 * Get the remaining use details for this menu item
	 *
	 * @return The remaining use details
	 */
	public SMSRemainingUses getUseLimits() {
		return uses;
	}

	/**
	 * Sets the remaining use details for this menu item.
	 *
	 * @param uses the remaining use details
	 */
	public void setUseLimits(SMSRemainingUses uses) {
		this.uses = uses;
	}

	/**
	 * Returns a printable representation of the number of uses remaining for this item.
	 *
	 * @return Formatted usage information
	 */
	String formatUses() {
		return uses.toString();
	}

	/**
	 * Returns a printable representation of the number of uses remaining for this item, for the given player.
	 *
	 * @param sender Player to retrieve the usage information for
	 * @return Formatted usage information
	 */
	@Override
	public String formatUses(CommandSender sender) {
		if (sender instanceof Player) {
			return uses.toString(sender.getName());
		} else {
			return formatUses();
		}
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((command == null) ? 0 : command.hashCode());
		result = prime * result + ((label == null) ? 0 : label.hashCode());
		result = prime * result + ((message == null) ? 0 : message.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SMSMenuItem other = (SMSMenuItem) obj;
		if (command == null) {
			if (other.command != null)
				return false;
		} else if (!command.equals(other.command))
			return false;
		if (label == null) {
			if (other.label != null)
				return false;
		} else if (!label.equals(other.label))
			return false;
		if (message == null) {
			if (other.message != null)
				return false;
		} else if (!message.equals(other.message))
			return false;
		return true;
	}

	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 *
	 * Two menu items are equal if their labels are the same.  Colour codes do not count, only the text.
	 */
	@Override
	public int compareTo(SMSMenuItem other) {
		return getLabelStripped().compareToIgnoreCase(other.getLabelStripped());
	}

	Map<String, Object> freeze() {
		Map<String, Object> map = new HashMap<String, Object>();

		map.put("label", MiscUtil.unParseColourSpec(label));
		map.put("command", command);
		map.put("message", MiscUtil.unParseColourSpec(message));
		map.put("icon", iconMaterial.toString());
		map.put("usesRemaining", uses.freeze());
		List<String> lore2 = new ArrayList<String>(lore.size());
		for (String l : lore) {
			lore2.add(MiscUtil.unParseColourSpec(l));
		}
		map.put("lore", lore2);
		return map;
	}

	public void autosave() {
		if (menu != null)
			menu.autosave();
	}

	@Override
	public String getDescription() {
		return "menu item";
	}

	SMSMenuItem uniqueItem() {
		if (menu.getItem(getLabelStripped()) == null) {
			return this;
		}
		// the label already exists in this menu - try to get a unique one
		int n = 0;
		String ls;
		do {
			n++;
			ls = getLabelStripped() + "-" + n;
		} while (menu.getItem(ls) != null);

		return new SMSMenuItem(menu, getLabel() + "-" + n, getCommand(), getMessage(), getIconMaterial().toString());
	}
}
