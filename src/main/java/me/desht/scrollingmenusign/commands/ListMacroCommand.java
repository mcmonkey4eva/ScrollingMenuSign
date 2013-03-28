package me.desht.scrollingmenusign.commands;

import me.desht.scrollingmenusign.SMSMacro;
import me.desht.dhutils.MessagePager;
import me.desht.dhutils.commands.AbstractCommand;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

public class ListMacroCommand extends AbstractCommand {

	public ListMacroCommand() {
		super("sms macro list", 0, 1);
		setPermissionNode("scrollingmenusign.commands.macro");
		setUsage("/sms macro list [<macro-name>]");
	}

	@Override
	public boolean execute(Plugin plugin, CommandSender sender, String[] args) {
		MessagePager pager = MessagePager.getPager(sender).clear();

		if (args.length == 0) {
			pager.add("&e" + SMSMacro.getMacros().size() + " macros:");
			for (SMSMacro m : SMSMacro.listMacros(true)) {
				pager.add(MessagePager.BULLET + ChatColor.WHITE + m.getName() + "   &e[" + m.getLines().size() + " lines]");
			}
		} else {
			int i = 1;
			SMSMacro m = SMSMacro.getMacro(args[0], false);
			pager.add("&fMacro &e" + m.getName() + "&f [" + m.getLines().size() + " lines]:");
			for (String l : m.getLines()) {
				pager.add(" &e" + i++ + ") &f" + l.replace(" && ", " &&&& "));
			}
		}
		pager.showPage();
		
		return true;
	}

}
