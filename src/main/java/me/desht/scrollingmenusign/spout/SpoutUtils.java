package me.desht.scrollingmenusign.spout;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import me.desht.scrollingmenusign.SMSConfig;
import me.desht.scrollingmenusign.util.MiscUtil;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.getspout.spoutapi.material.MaterialData;
import org.getspout.spoutapi.packet.PacketItemName;
import org.getspout.spoutapi.player.SpoutPlayer;

public class SpoutUtils {
	private static final Map<String, SMSSpoutKeyMap> wantedKeys = new HashMap<String, SMSSpoutKeyMap>();
	
	public static void setSpoutMapName(short mapID, String name) {
		MaterialData.getOrCreateMaterial(358, mapID).setName(name);
		for (Player p : Bukkit.getServer().getOnlinePlayers()) {
			SpoutPlayer sp = (SpoutPlayer)p;
			if (sp.isSpoutCraftEnabled()) {
				sp.sendPacket(new PacketItemName(358, mapID, name));
			}
		}
	}

	public static void loadKeyDefinitions() {
		addKeyDefinition("sms.actions.spout.up");
		addKeyDefinition("sms.actions.spout.down");
		addKeyDefinition("sms.actions.spout.execute");
	}

	public static boolean tryKeyboardMatch(String key, SMSSpoutKeyMap pressed) {
		return wantedKeys.get(key).equals(pressed);
	}

	private static void addKeyDefinition(String key) {
		String wanted = SMSConfig.getConfig().getString(key);
		try {
			wantedKeys.put(key, new SMSSpoutKeyMap(wanted));
		} catch (IllegalArgumentException e) {
			MiscUtil.log(Level.WARNING, "invalid key definition [" + wanted + "] for " + key);
			wantedKeys.put(key, new SMSSpoutKeyMap(SMSConfig.getConfig().getDefaults().getString(key)));
		}
	}
}