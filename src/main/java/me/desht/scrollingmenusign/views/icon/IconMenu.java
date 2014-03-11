package me.desht.scrollingmenusign.views.icon;

import me.desht.dhutils.Debugger;
import me.desht.dhutils.LogUtils;
import me.desht.dhutils.MiscUtil;
import me.desht.scrollingmenusign.SMSException;
import me.desht.scrollingmenusign.SMSMenuItem;
import me.desht.scrollingmenusign.ScrollingMenuSign;
import me.desht.scrollingmenusign.enums.ViewJustification;
import me.desht.scrollingmenusign.views.SMSInventoryView;
import me.desht.scrollingmenusign.views.SMSPopup;
import me.desht.scrollingmenusign.views.SMSView;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.material.MaterialData;

public class IconMenu implements Listener, SMSPopup {
	private static final int INVENTORY_WIDTH = 9;
	private static final int MAX_INVENTORY_ROWS = 6;

	private final SMSInventoryView view;
	private final String menuName;

	private int size = 0;
	private ItemStack[] optionIcons;
	private String[] optionNames;

	public IconMenu(SMSInventoryView view, String menuName) {
		this.view = view;
		this.menuName = menuName;
		Debugger.getInstance().debug("icon menu: register events: " + this + " view=" + view.getName());
		Bukkit.getPluginManager().registerEvents(this, ScrollingMenuSign.getInstance());
	}

	public int getSlots() {
		return optionIcons.length;
	}

	@Override
	public SMSView getView() {
		return view;
	}

	@Override
	public boolean isPoppedUp(Player player) {
		return player.getOpenInventory().getTitle().equals(getView().getActiveMenuTitle(player.getName()));
	}

	@Override
	public void repaint() {
		getView().setDirty(true);
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (isPoppedUp(p)) {
				popdown(p);
				popup(p);
			}
		}
	}

	@Override
	public void popup(Player p) {
		if (!isPoppedUp(p)) {
			String title = getView().variableSubs(getView().getActiveMenuTitle(p.getName()));
			if (size == 0 || getView().isDirty(p.getName())) {
				buildMenu(p);
			}
			Inventory inventory = Bukkit.createInventory(p, size, title);
			getView().setDirty(p.getName(), false);
			for (int i = 0; i < size; i++) {
				inventory.setItem(i, optionIcons[i]);
			}
			p.openInventory(inventory);
		}
	}

	@Override
	public void popdown(Player p) {
		if (isPoppedUp(p)) {
			p.closeInventory();
		}
	}

	private void buildMenu(Player p) {
		int width = (Integer) getView().getAttribute(SMSInventoryView.WIDTH);
		int spacing = (Integer) getView().getAttribute(SMSInventoryView.SPACING);
		int nItems = getView().getActiveMenuItemCount(p.getName());
		int nRows = Math.min(MAX_INVENTORY_ROWS, (((nItems - 1) / width) + 1) * spacing);

		size = INVENTORY_WIDTH * nRows;
		optionIcons = new ItemStack[size];
		optionNames = new String[size];

		int xOff = getXOffset(width);

		for (int i = 0; i < nItems; i++) {
			int i2 = i * spacing;
			int row = i2 / width;
			int pos = row * INVENTORY_WIDTH + xOff + i2 % width;
			if (pos >= size) {
				LogUtils.warning("inventory view " + getView().getName() + " doesn't have enough slots to display its items");
				break;
			}
			SMSMenuItem menuItem = getView().getActiveMenuItemAt(p.getName(), i + 1);
			ItemStack icon = getItemIcon(menuItem.getIconMaterial());
			String label = getView().variableSubs(menuItem.getLabel());
			ItemMeta im = icon.getItemMeta();
			im.setDisplayName(ChatColor.RESET + label);
			im.setLore(menuItem.getLoreAsList());
			icon.setItemMeta(im);
			optionIcons[pos] = icon;
			optionNames[pos] = menuItem.getLabel();
		}

		Debugger.getInstance().debug("built icon menu inventory for " + p.getName() + ": " + size + " slots");
	}

	private ItemStack getItemIcon(MaterialData iconMaterial) {
		if (iconMaterial == null) {
			String defIcon = ScrollingMenuSign.getInstance().getConfig().getString("sms.inv_view.default_icon", "STONE");
			MaterialData mat = SMSMenuItem.parseIconMaterial(defIcon);
			return mat.toItemStack(1);
		} else {
			return iconMaterial.toItemStack(1);
		}
	}

	private int getMenuIndexForSlot(int invSlot) {
		int width = (Integer) getView().getAttribute(SMSInventoryView.WIDTH);

		int row = invSlot / INVENTORY_WIDTH;
		int col = invSlot % INVENTORY_WIDTH;

		return row * width + (col - getXOffset(width)) + 1;
	}

	private int getXOffset(int width) {
		ViewJustification ij = view.getItemJustification();
		switch (ij) {
			case LEFT:
				return 0;
			case RIGHT:
				return INVENTORY_WIDTH - width;
			default:
				return (INVENTORY_WIDTH - width) / 2;
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	void onInventoryClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player)) {
			return;
		}
		final Player player = (Player) event.getWhoClicked();
		String playerName = player.getName();
		String menuTitle = getView().variableSubs(getView().getActiveMenuTitle(playerName));
		String activeMenuName = view.getActiveMenu(playerName).getName();

		if (isPoppedUp(player) && event.getInventory().getTitle().equals(menuTitle) && menuName.equals(activeMenuName)) {
			Debugger.getInstance().debug("InventoryClickEvent: player = " + playerName + ", view = " + getView().getName() +
					", inventory name = " + event.getInventory().getTitle() + ", icon menu = " + this);

			event.setCancelled(true);
			int slot = event.getRawSlot();
			int spacing = (Integer) getView().getAttribute(SMSInventoryView.SPACING);
			int slot2 = slot == 0 ? 0 : ((slot - 1) / spacing) + 1;
			if (slot >= 0 && slot < size && optionNames[slot] != null) {
				OptionClickEvent optionEvent = new OptionClickEvent(player, getMenuIndexForSlot(slot2), optionNames[slot]);
				try {
					view.onOptionClick(optionEvent);
				} catch (SMSException e) {
					MiscUtil.errorMessage(player, e.getMessage());
				}
				if (optionEvent.willClose()) {
					Bukkit.getScheduler().runTaskLater(ScrollingMenuSign.getInstance(), new Runnable() {
						@Override
						public void run() {
							player.closeInventory();
						}
					}, 1L);
				}
				if (optionEvent.willDestroy()) {
					destroy();
				}
			}
		}
	}

	public void destroy() {
		Debugger.getInstance().debug("icon menu: unregister events: " + this + " view=" + view.getName());
		HandlerList.unregisterAll(this);
	}

	public interface OptionClickEventHandler {
		public void onOptionClick(OptionClickEvent event);
	}

	public class OptionClickEvent {
		private final Player player;
		private final int index;
		private final String name;

		private boolean close;
		private boolean destroy;

		public OptionClickEvent(Player player, int index, String name) {
			this.player = player;
			this.index = index;
			this.name = name;
			this.close = true;
			this.destroy = false;
		}

		public Player getPlayer() {
			return player;
		}

		public int getIndex() {
			return index;
		}

		public String getName() {
			return name;
		}

		public boolean willClose() {
			return close;
		}

		public boolean willDestroy() {
			return destroy;
		}

		public void setWillClose(boolean close) {
			this.close = close;
		}

		public void setWillDestroy(boolean destroy) {
			this.destroy = destroy;
		}
	}
}
