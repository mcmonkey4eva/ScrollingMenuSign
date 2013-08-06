package me.desht.scrollingmenusign;

import me.desht.dhutils.LogUtils;
import me.desht.dhutils.MiscUtil;
import me.desht.dhutils.PermissionUtils;
import me.desht.scrollingmenusign.views.SMSGlobalScrollableView;

import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockRedstoneEvent;

public class TooltipSign implements SMSInteractableBlock {

	private final SMSGlobalScrollableView view;

	public TooltipSign(SMSGlobalScrollableView view) {
		this.view = view;
	}

	@Override
	public void processEvent(ScrollingMenuSign plugin, BlockDamageEvent event) {
		if (!view.isOwnedBy(event.getPlayer()) && !PermissionUtils.isAllowedTo(event.getPlayer(), "scrollingmenusign.destroy")) {
			event.setCancelled(true);
		}
	}

	@Override
	public void processEvent(ScrollingMenuSign plugin, BlockBreakEvent event) {
		view.removeTooltipSign();
		MiscUtil.statusMessage(event.getPlayer(), String.format("Tooltip sign @ &f%s&- was removed from view &e%s&-.",
				MiscUtil.formatLocation(event.getBlock().getLocation()), view.getName()));
	}

	@Override
	public void processEvent(ScrollingMenuSign plugin, BlockPhysicsEvent event) {
		if (plugin.isAttachableDetached(event.getBlock())) {
			if (plugin.getConfig().getBoolean("sms.no_physics")) {
				event.setCancelled(true);
			} else {
				LogUtils.info("Tooltip sign for " + view.getName() + " @ " + event.getBlock().getLocation() + " has become detached: deleting");
				view.removeTooltipSign();
			}
		}
	}

	@Override
	public void processEvent(ScrollingMenuSign plugin, BlockRedstoneEvent event) {
		// ignore
	}

}