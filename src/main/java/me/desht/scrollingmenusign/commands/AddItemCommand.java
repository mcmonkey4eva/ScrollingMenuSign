package me.desht.scrollingmenusign.commands;

import me.desht.dhutils.MiscUtil;
import me.desht.dhutils.commands.AbstractCommand;
import me.desht.scrollingmenusign.SMSException;
import me.desht.scrollingmenusign.SMSMenu;
import me.desht.scrollingmenusign.SMSMenuItem;
import me.desht.scrollingmenusign.enums.SMSMenuAction;
import me.desht.scrollingmenusign.parser.CommandParser;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class AddItemCommand extends AbstractCommand {

	public AddItemCommand() {
		super("sms add", 2);
		setPermissionNode("scrollingmenusign.commands.add");
		setUsage(new String[] { 
				"/sms add <menu-name> <label> [<command>] [<options...>]",
				"Options (-at takes integer, all others take string):",
				"  -at         Position to add the new item at",
				"  -feedback   The new feedback message to display",
				"  -icon       The new material used for the item's icon",
				"  -lore       The lore for the item (line delimiter '\\\\')",
		});
		setQuotedArgs(true);
		setOptions(new String[] { "at:i", "feedback:s", "icon:s", "lore:s" });
	}

	@Override
	public boolean execute(Plugin plugin, CommandSender sender, String[] args) {
		String menuName = args[0];

		SMSMenu menu = SMSMenu.getMenu(menuName);

		if (args.length < 3 && menu.getDefaultCommand().isEmpty()) {
			throw new SMSException(getUsage()[0]);
		}

		int pos = hasOption("at") ? getIntOption("at") : -1;
		String label = MiscUtil.parseColourSpec(sender, args[1]);
		String cmd = args.length >= 3 ? args[2] : "";
		String msg = hasOption("feedback") ? getStringOption("feedback") : "";
		String iconMat = hasOption("icon") ? getStringOption("icon") : plugin.getConfig().getString("sms.inv_view.default_icon", "stone");
		String[] lore = hasOption("lore") ? getStringOption("lore").split("\\\\\\\\") : new String[0];

		if (sender instanceof Player && !new CommandParser().verifyCreationPerms((Player) sender, cmd)) {
			throw new SMSException("You do not have permission to add that kind of command.");
		}

		SMSMenuItem newItem = new SMSMenuItem(menu, label, cmd, msg, iconMat, lore);
		if (pos < 0) {
			menu.addItem(newItem);
			MiscUtil.statusMessage(sender, "Menu item &f" + label + "&- added to &e" + menuName);
		} else {
			menu.insertItem(pos, newItem);
			int actualPos = menu.indexOfItem(label);
			MiscUtil.statusMessage(sender, "Menu item &f" + label + "&- inserted in &e" + menuName + "&- at position " + actualPos);
		}

		menu.notifyObservers(SMSMenuAction.REPAINT);

		return true;
	}

}
