package me.desht.scrollingmenusign.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.desht.dhutils.ConfigurationManager;
import me.desht.dhutils.MiscUtil;
import me.desht.scrollingmenusign.SMSException;
import me.desht.scrollingmenusign.ScrollingMenuSign;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

public class SetConfigCommand extends SMSAbstractCommand {

    public SetConfigCommand() {
        super("sms setcfg", 2);
        setPermissionNode("scrollingmenusign.commands.setcfg");
        setUsage("/sms setcfg <key> <value>");
        setQuotedArgs(true);
    }

    @Override
    public boolean execute(Plugin plugin, CommandSender player, String[] args) {
        String key = args[0], val = args[1];

        ConfigurationManager configManager = ((ScrollingMenuSign) plugin).getConfigManager();

        try {
            if (args.length > 2) {
                List<String> list = new ArrayList<String>(args.length - 1);
                list.addAll(Arrays.asList(args).subList(1, args.length));
                configManager.set(key, list);
            } else {
                configManager.set(key, val);
            }
            Object res = configManager.get(key);
            MiscUtil.rawMessage(player, MiscUtil.STATUS_COLOUR + key + " is now set to '" + ChatColor.YELLOW + res + MiscUtil.STATUS_COLOUR + "'");
        } catch (SMSException e) {
            MiscUtil.errorMessage(player, e.getMessage());
            MiscUtil.errorMessage(player, "Use /sms getcfg to list all valid keys");
        } catch (IllegalArgumentException e) {
            MiscUtil.errorMessage(player, e.getMessage());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(Plugin plugin, CommandSender sender, String[] args) {
        ConfigurationSection config = ScrollingMenuSign.getInstance().getConfig().getConfigurationSection("sms");
        switch (args.length) {
            case 1:
                return getConfigCompletions(sender, config, args[0]);
            case 2:
                return getConfigValueCompletions(sender, args[0], config.get(args[0]), "", args[1]);
            default:
                return noCompletions(sender);
        }
    }
}
