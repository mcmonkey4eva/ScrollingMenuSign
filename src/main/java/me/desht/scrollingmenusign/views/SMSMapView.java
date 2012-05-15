package me.desht.scrollingmenusign.views;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import javax.imageio.ImageIO;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapFont;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;

import me.desht.scrollingmenusign.SMSConfig;
import me.desht.scrollingmenusign.SMSException;
import me.desht.scrollingmenusign.SMSMenu;
import me.desht.scrollingmenusign.ScrollingMenuSign;
import me.desht.scrollingmenusign.enums.SMSMenuAction;
import me.desht.scrollingmenusign.spout.SpoutUtils;
import me.desht.scrollingmenusign.util.SMSLogger;
import me.desht.scrollingmenusign.views.map.SMSMapRenderer;

/**
 * @author des
 * 
 * With thanks to dumptruckman for MapActionMenu, upon which much of this is based
 * 
 */
public class SMSMapView extends SMSScrollableView {

	// magic map X value used by the Courier plugin
	public static final int COURIER_MAP_X = 2147087904;

	private static final String CACHED_FILE_FORMAT = "png";

	// attributes
	public static final String IMAGE_FILE = "imagefile";

	private MapView mapView = null;
	private final SMSMapRenderer mapRenderer;
	private MapFont mapFont = MinecraftFont.Font;
	private int x, y;
	private int width, height;
	private int lineSpacing;
	private final List<MapRenderer> previousRenderers = new ArrayList<MapRenderer>();
	private BufferedImage image = null;

	private static Map<Short,SMSMapView> allMapViews = new HashMap<Short, SMSMapView>();

	/**
	 * Create a new map view on the given menu.  The view name is chosen automatically.
	 * 
	 * @param menu	The menu to attach the new view to
	 */
	public SMSMapView (SMSMenu menu) {
		this(null, menu);
	}

	/**
	 * Create a new map view on the given menu.
	 * 
	 * @param name	The new view's name.
	 * @param menu	The menu to attach the new view to.
	 */
	public SMSMapView(String name, SMSMenu menu) {
		super(name, menu);

		registerAttribute(IMAGE_FILE, "");

		x = 4;
		y = 10;	// leaving space for the map name in the top left
		width = 120;
		height = 120;
		lineSpacing = 1;

		mapRenderer = new SMSMapRenderer(this);
	}

	private void loadBackgroundImage() {
		image = null;

		String file = getAttributeAsString(IMAGE_FILE, "");
		if (file.isEmpty()) {
			return;
		}
		
		// Load the file from the given URL, and write a cached copy (PNG, 128x128) to our local
		// directory structure.  The cached file can be used for subsequent loads to improve performance.
		try {
			URL url = ScrollingMenuSign.makeImageURL(file);
			File cached = getCachedFile(url);
			BufferedImage resizedImage;
			if (cached != null && cached.canRead()) {
				resizedImage = ImageIO.read(cached);
			} else {
				BufferedImage orig = ImageIO.read(url);
				resizedImage = MapPalette.resizeImage(orig);
				if (cached != null) {
					ImageIO.write(resizedImage, CACHED_FILE_FORMAT, cached);
					SMSLogger.info("Cached image " + url + " as " + cached);
				}
			}
			image = resizedImage;
		} catch (MalformedURLException e) {
			SMSLogger.warning("malformed image URL for map view " + getName() + ": " + e.getMessage());
		} catch (IOException e) {
			SMSLogger.warning("cannot load image URL for map view " + getName() + ": " + e.getMessage());
		}
	}

	private static File getCachedFile(URL url) {
		byte[] bytes = url.toString().getBytes();
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("MD5");
			byte[] d = md.digest(bytes);
			BigInteger i = new BigInteger(d);
			return new File(SMSConfig.getImgCacheFolder(), String.format("%1$032X", i) + "." + CACHED_FILE_FORMAT);
		} catch (NoSuchAlgorithmException e) {
			SMSLogger.warning("Can't get MD5 MessageDigest algorithm, no image caching");
			return null;
		}
	}

	@Override
	public Map<String, Object> freeze() {
		Map<String, Object> map = super.freeze();
		map.put("mapId", mapView == null ? -1 : mapView.getId());
		return map;
	}

	protected void thaw(ConfigurationSection node) throws SMSException {
		super.thaw(node);
		short mapId = (short) node.getInt("mapId", -1);
		if (mapId >= 0)
			setMapId((short) node.getInt("mapId", 0));
	}

	/**
	 * Associate this view with a map ID.  Removes (and saves) all renderers currently on
	 * the map, and adds our own SMSRenderer to the map.
	 *
	 * @param id
	 */
	public void setMapId(short id) {
		mapView = Bukkit.getServer().getMap(id);
		if (mapView == null) {
			SMSLogger.warning("No such map view for map ID " + id);
			return;
		}

		for (MapRenderer r : mapView.getRenderers()) {
			previousRenderers.add(r);
			mapView.removeRenderer(r);
		}
		mapView.addRenderer(getMapRenderer());

		allMapViews.put(mapView.getId(), this);

		if (ScrollingMenuSign.getInstance().isSpoutEnabled()) {
			SpoutUtils.setSpoutMapName(mapView.getId(), getMenu().getTitle());
		}

		loadBackgroundImage();

		autosave();
	}

	/* (non-Javadoc)
	 * @see me.desht.scrollingmenusign.views.SMSView#deletePermanent()
	 */
	@Override
	public void deletePermanent() {
		if (mapView != null) {
			if (ScrollingMenuSign.getInstance().isSpoutEnabled())
				SpoutUtils.setSpoutMapName(mapView.getId(), "map_" + mapView.getId());
			allMapViews.remove(mapView.getId());
			mapView.removeRenderer(getMapRenderer());
			for (MapRenderer r : previousRenderers) {
				mapView.addRenderer(r);
			}
		}
		super.deletePermanent();
	}

	/**
	 * Get the Bukkit @see org.bukkit.map.MapView associated with this map view object.
	 * 
	 * @return	The Bukkit MapView object
	 */
	public MapView getMapView() {
		return mapView;
	}

	/**
	 * Get the custom map renderer for this map view object.
	 * 
	 * @return	The SMSMapRenderer object
	 */
	public SMSMapRenderer getMapRenderer() {
		return mapRenderer;
	}

	/**
	 * Get the X co-ordinate to start drawing at - the left-hand bounds of the drawing space
	 * 
	 * @return	The X co-ordinate
	 */
	public int getX() {
		return x;
	}

	/**
	 * Set the X co-ordinate to start drawing at - the left-hand bounds of the drawing space
	 * 
	 * @param x	The X co-ordinate
	 */
	public void setX(int x) {
		this.x = x;
	}

	/**
	 * Get the Y co-ordinate to start drawing at - the upper bounds of the drawing space
	 * 
	 * @return	The Y co-ordinate
	 */
	public int getY() {
		return y;
	}

	/**
	 * Set the Y co-ordinate to start drawing at - the upper bounds of the drawing space
	 * 
	 * @param y		The Y co-ordinate
	 */
	public void setY(int y) {
		this.y = y;
	}

	/**
	 * Get the width of the drawing area on the map
	 * 
	 * @return	The width
	 */
	public int getWidth() {
		return width;
	}

	/**
	 * Set the width of the drawing area on the map
	 * 
	 * @param width	The width
	 */
	public void setWidth(int width) {
		this.width = width;
	}

	/**
	 * Get the height of the drawing area on the map
	 * 
	 * @return	The height
	 */
	public int getHeight() {
		return height;
	}

	/**
	 * Set the height of the drawing area on the map
	 * 
	 * @param height	The height
	 */
	public void setHeight(int height) {
		this.height = height;
	}

	/**
	 * Get the pixel spacing between each line of text in the menu
	 * 
	 * @return	The spacing
	 */
	public int getLineSpacing() {
		return lineSpacing;
	}

	/**
	 * Set the pixel spacing between each line of text in the menu
	 * 
	 * @param lineSpacing	The spacing
	 */
	public void setLineSpacing(int lineSpacing) {
		this.lineSpacing = lineSpacing;
	}

	/**
	 * Get the font used for drawing menu text
	 * 
	 * @return	The font
	 */
	public MapFont getMapFont() {
		return mapFont;
	}

	/**
	 * Set the font used for drawing menu text
	 * 
	 * @param mapFont	The font
	 */
	public void setMapFont(MapFont mapFont) {
		this.mapFont = mapFont;
	}

	public BufferedImage getImage() {
		return image;
	}

	/* (non-Javadoc)
	 * @see me.desht.scrollingmenusign.views.SMSScrollableView#update(java.util.Observable, java.lang.Object)
	 */
	@Override
	public void update(Observable menu, Object arg1) {
		if (mapView == null)
			return;

		if (mapView.getRenderers().contains(getMapRenderer())) {
			mapView.removeRenderer(getMapRenderer());
		}
		mapView.addRenderer(getMapRenderer());
		setDirty(true);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return "map id: " + (mapView == null ? "NONE" : mapView.getId());
	}

	/**
	 * Given a map ID, return the map view object for that ID, if any.
	 * 
	 * @param mapId	The ID of the map
	 * @return	The SMSMapView object for the ID, or null if this map ID isn't used for a SMSMapView
	 */
	public static SMSMapView getViewForId(short mapId) {
		return allMapViews.get(mapId);
	}

	/**
	 * Check if the given map ID is used for a SMSMapView
	 * 
	 * @param mapId	The ID of the map
	 * @return	true if the ID is used for a SMSMapView, false otherwise
	 */
	public static boolean checkForMapId(short mapId) {
		return allMapViews.containsKey(mapId);
	}

	/**
	 * Convenience routine.  Add the given mapId as a view on the given menu.
	 * 
	 * @param mapId
	 * @param menu
	 * @return	The SMSMapView object that was just created
	 * @throws SMSException if the given mapId is already a view
	 * @deprecated Use addMapToMenu(menu, mapId)
	 */
	@Deprecated
	public static SMSMapView addMapToMenu(short mapId, SMSMenu menu) throws SMSException {
		return addMapToMenu(menu, mapId);
	}

	/**
	 * Convenience routine.  Add the given mapId as a view on the given menu.
	 * 
	 * @param menu	The menu to add the view to
	 * @param mapId		ID of the map that will be used as a view
	 * @return	The SMSMapView object that was just created
	 * @throws SMSException if the given mapId is already a view
	 */	
	public static SMSMapView addMapToMenu(SMSMenu menu, short mapId) throws SMSException {
		if (SMSMapView.checkForMapId(mapId)) {
			throw new SMSException("This map already has a menu view associated with it");
		}
		SMSMapView mapView = new SMSMapView(menu);
		mapView.register();
		mapView.setMapId(mapId);
		mapView.update(menu, SMSMenuAction.REPAINT);

		return mapView;		
	}

	/**
	 * Convenience routine.  Get the map view that the player is holding, if any.
	 * 
	 * @param player	The player to check for
	 * @return			A SMSMapView object if the player is holding one, null otherwise
	 */
	public static SMSMapView getHeldMapView(Player player) {
		if (player.getItemInHand().getType() == Material.MAP) {
			return getViewForId(player.getItemInHand().getDurability());
		} else {
			return null;
		}
	}
	
	@Override
	public String getType() {
		return "map";
	}

	@Override
	protected void onAttributeChanged(String attribute, String oldVal, String newVal) {
		super.onAttributeChanged(attribute, oldVal, newVal);

		if (attribute.equals(IMAGE_FILE)) {
			loadBackgroundImage();
			setDirty(true);
		}
	}
	
	/**
	 * Check to see if this map ID is used by another plugin, to avoid toe-stepping-upon...
	 * Right now, only Courier is checked for.
	 * 
	 * @param item	The map item to check
	 * @return	True if it's used by someone else, false otherwise
	 */
	public static boolean usedByOtherPlugin(ItemStack item) {
		short mapId = item.getDurability();
		MapView mapView = Bukkit.getServer().getMap(mapId);
		
		// Courier uses a magic X value to indicate the map it uses
		return mapView.getCenterX() == COURIER_MAP_X;
	}
}
