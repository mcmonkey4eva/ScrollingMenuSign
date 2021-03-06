package me.desht.scrollingmenusign.commands;

import me.desht.dhutils.MessagePager;
import me.desht.dhutils.MiscUtil;
import me.desht.dhutils.PermissionUtils;
import me.desht.scrollingmenusign.RedstoneControlSign;
import me.desht.scrollingmenusign.SMSException;
import me.desht.scrollingmenusign.SMSValidate;
import me.desht.scrollingmenusign.ScrollingMenuSign;
import me.desht.scrollingmenusign.views.PoppableView;
import me.desht.scrollingmenusign.views.SMSGlobalScrollableView;
import me.desht.scrollingmenusign.views.SMSView;
import me.desht.scrollingmenusign.views.action.RepaintAction;
import me.desht.scrollingmenusign.views.redout.Switch;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class ViewCommand extends SMSAbstractCommand {

    public ViewCommand() {
        super("sms view", 0, 3);
        setPermissionNode("scrollingmenusign.commands.view");
        setUsage(new String[]{
                "/sms view [-l]",
                "/sms view <view-name> <attribute-name>|<$var> [<new-value>]",
                "/sms view <view-name> -d [<$var>]",
                "/sms view <view-name> -popup",
        });
        setQuotedArgs(true);
        setOptions("popup", "popup-for:s", "d:s", "l");
    }

    @Override
    public boolean execute(Plugin plugin, CommandSender sender, String[] args) {
        SMSView view;
        if (getBooleanOption("l")) {
            listAllViews(sender, plugin);
            return true;
        }
        if (args.length > 0) {
            view = getView(sender, args[0]);
        } else {
            notFromConsole(sender);
            Player player = (Player) sender;
            view = getViewManager(plugin).getTargetedView(player);
        }

        if (view == null) {
            // maybe the player's looking at an output switch
            if (lookingAtSwitch((ScrollingMenuSign) plugin, sender)) {
                return true;
            } else {
                MiscUtil.errorMessage(sender, "No suitable view found.");
                return true;
            }
        }

        if (getBooleanOption("popup")) {
            notFromConsole(sender);
            view.ensureAllowedToUse(sender);
            SMSValidate.isTrue(view instanceof PoppableView, "This view type can't be popped up.");
            ((PoppableView) view).toggleGUI((Player) sender);
        } else if (hasOption("popup-for")) {
            PermissionUtils.requirePerms(sender, "scrollingmenusign.popup.other");
            String otherName = getStringOption("popup-for");
            @SuppressWarnings("deprecation") Player other = Bukkit.getPlayer(otherName);
            SMSValidate.notNull(other, "Player " + otherName + " is not online.");
            SMSValidate.isTrue(view instanceof PoppableView, "This view type can't be popped up.");
            ((PoppableView) view).toggleGUI(other);
        } else if (hasOption("d")) {
            view.ensureAllowedToModify(sender);
            String varName = getStringOption("d");
            if (varName.startsWith("$")) {
                varName = varName.substring(1);
                view.setVariable(varName, null);
                view.autosave();
                view.update(null, new RepaintAction());
                MiscUtil.statusMessage(sender, "Deleted view variable: &a" + varName + "&-.");
            }
        } else if (args.length <= 1) {
            showViewDetails(sender, view);
        } else {
            String attr = args[1];
            if (attr.startsWith("$")) {
                String varName = attr.substring(1);
                // user-defined view variable
                if (args.length == 3) {
                    view.ensureAllowedToModify(sender);
                    view.setVariable(varName, args[2]);
                    view.autosave();
                    view.update(null, new RepaintAction());
                }
                MiscUtil.statusMessage(sender, String.format("&a%s.$%s&- = &a%s&-", view.getName(), varName, view.getVariable(varName)));
            } else {
                // predefined view attribute
                if (args.length == 3) {
                    view.ensureAllowedToModify(sender);
                    if (attr.equals(SMSView.OWNER) && !PermissionUtils.isAllowedTo(sender, "scrollingmenusign.edit.any")) {
                        throw new SMSException("You may not change the owner of a view.");
                    }
                    view.setAttribute(attr, args[2]);
                    view.autosave();
                }
                MiscUtil.statusMessage(sender, String.format("&e%s.%s&- = &e%s&-", view.getName(), attr, view.getAttributeAsString(attr)));
            }
        }

        return true;
    }

    private void listAllViews(CommandSender sender, Plugin plugin) {
        MessagePager pager = MessagePager.getPager(sender).clear().setParseColours(true);
        for (SMSView view : MiscUtil.asSortedList(getViewManager(plugin).listViews())) {
            pager.add(MessagePager.BULLET + " " + view.getName() + ": &e" + view.toString());
        }
        pager.showPage();
    }

    private void showViewDetails(CommandSender sender, SMSView view) {
        MessagePager pager = MessagePager.getPager(sender).clear().setParseColours(true);
        pager.add(String.format("View &6%s&f (%s) :",
                view.getName(), view.toString()));
        pager.add(String.format("Native menu: &6%s&f, Active menu: &6%s",
                view.getNativeMenu().getName(), view.getActiveMenu(sender instanceof Player ? (Player) sender : null).getName()));
        pager.add("Owner ID: &6" + view.getOwnerId());
        for (String k : view.listAttributeKeys(true)) {
            pager.add(String.format(MessagePager.BULLET + "&e%s&f = &e%s", k, view.getAttributeAsString(k, "")));
        }
        for (String k : MiscUtil.asSortedList(view.listVariables())) {
            pager.add(String.format("&4\u2022 &a$%s&f = &a%s", k, view.getVariable(k)));
        }
        if (view instanceof SMSGlobalScrollableView) {
            SMSGlobalScrollableView gsv = (SMSGlobalScrollableView) view;
            int nSwitches = gsv.getSwitches().size();
            if (nSwitches > 0) {
                String s = nSwitches > 1 ? "s" : "";
                pager.add("&f" + nSwitches + "&- redstone output" + s + ":");
                for (Switch sw : MiscUtil.asSortedList(gsv.getSwitches())) {
                    pager.add(String.format(MessagePager.BULLET + "&e%s&f @ &e%s",
                            sw.getTrigger(), MiscUtil.formatLocation(sw.getLocation())));
                }
            }
            int nCtrlSigns = gsv.getControlSigns().size();
            if (nCtrlSigns > 0) {
                String s = nCtrlSigns > 1 ? "s" : "";
                pager.add("&f" + nCtrlSigns + "&- redstone control sign" + s + ":");
                for (RedstoneControlSign sign : gsv.getControlSigns()) {
                    pager.add(String.format(MessagePager.BULLET + "&e%s&-", sign));
                }
            }
        }
        pager.showPage();
    }

    private boolean lookingAtSwitch(ScrollingMenuSign plugin, CommandSender sender) {
        if (!(sender instanceof Player)) {
            return false;
        }
        try {
            Block b = ((Player) sender).getTargetBlock(null, ScrollingMenuSign.BLOCK_TARGET_DIST);
            Switch sw = plugin.getLocationManager().getInteractableAt(b.getLocation(), Switch.class);
            if (sw != null) {
                MiscUtil.statusMessage(sender, String.format("Output switch @ &e%s&- for view &e%s&- / &e%s&-",
                        MiscUtil.formatLocation(sw.getLocation()),
                        sw.getView().getName(), sw.getTrigger()));
                return true;
            }
        } catch (IllegalStateException e) {
            return false;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(Plugin plugin, CommandSender sender, String[] args) {
        SMSView view;
        switch (args.length) {
            case 1:
                return getViewCompletions(sender, args[0]);
            case 2:
                view = getView(sender, args[0]);
                return filterPrefix(sender, view.getAttributes().listAttributeKeys(false), args[1]);
            case 3:
                view = getView(sender, args[0]);
                Object o = view.getAttribute(args[1]);
                String desc = view.getAttributes().getDescription(args[1]);
                if (!desc.isEmpty())
                    desc = ChatColor.GRAY.toString() + ChatColor.ITALIC + " [" + desc + "]";
                return getConfigValueCompletions(sender, args[1], o, desc, args[2]);
            default:
                return noCompletions(sender);
        }
    }

}
