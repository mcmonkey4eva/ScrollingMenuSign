package me.desht.scrollingmenusign.views;

import me.desht.dhutils.MiscUtil;
import me.desht.scrollingmenusign.SMSException;
import me.desht.scrollingmenusign.SMSMenu;
import me.desht.scrollingmenusign.SMSValidate;
import me.desht.scrollingmenusign.ScrollingMenuSign;
import me.desht.scrollingmenusign.enums.ViewJustification;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;

import java.util.List;
import java.util.Observable;

/**
 * This view draws menus on signs.
 */
public class SMSSignView extends SMSGlobalScrollableView {

    /**
     * Create a new sign view object.
     *
     * @param name Unique name for this view.
     * @param menu The SMSMenu object to attach this view to.
     * @param loc  The location of this view's sign
     * @throws SMSException if the given location is not suitable for this view
     */
    public SMSSignView(String name, SMSMenu menu, Location loc) throws SMSException {
        super(name, menu);
        addLocation(loc);
    }

    /**
     * Create a new sign view object with no registered location.  A location
     * which contains a sign must be added with @see #addLocation(Location) before
     * this view is useful.
     *
     * @param name Unique name for this view.
     * @param menu The SMSMenu object to attach this view to.
     */
    public SMSSignView(String name, SMSMenu menu) {
        super(name, menu);
    }

    /**
     * Create a new sign view object.  Equivalent to calling SMSSignView(null, menu, loc).  The
     * view's name will be automatically generated, based on the menu name.
     *
     * @param menu The SMSMenu object to attach this view to.
     * @param loc  The location of this view's sign
     * @throws SMSException if the given location is not suitable for this view
     */
    public SMSSignView(SMSMenu menu, Location loc) throws SMSException {
        this(null, menu, loc);
    }

    /* (non-Javadoc)
     * @see me.desht.scrollingmenusign.views.SMSView#addLocation(org.bukkit.Location)
     */
    @Override
    public void addLocation(Location loc) throws SMSException {
        Block b = loc.getBlock();
        SMSValidate.isTrue(b.getType() == Material.WALL_SIGN || b.getType() == Material.SIGN_POST,
                "Location " + MiscUtil.formatLocation(loc) + " does not contain a sign.");
        super.addLocation(loc);
    }

    /* (non-Javadoc)
     * @see me.desht.scrollingmenusign.views.SMSScrollableView#update(java.util.Observable, java.lang.Object)
     */
    @Override
    public void update(Observable menu, Object arg1) {
        super.update(menu, arg1);

        repaintAll();
    }

    @Override
    public void onDeleted(boolean permanent) {
        super.onDeleted(permanent);
        if (permanent) {
            Sign sign = getSign();
            if (sign != null) {
                for (int i = 0; i < SIGN_LINES; i++) {
                    sign.setLine(i, "");
                }
                sign.update();
            }
        }
    }

    private void repaintAll() {
        Sign sign = getSign();
        if (sign != null) {
            String[] lines = buildSignText(getScrollPos());
            for (int i = 0; i < lines.length; i++) {
                sign.setLine(i, lines[i]);
            }
            sign.update();
            setDirty(false);
        }
    }

    private String[] buildSignText(int scrollPos) {
        String[] res = new String[SIGN_LINES];

        List<String> title = splitTitle(null);

        // first line (or two) of the sign is the menu title
        for (int i = 0; i < title.size(); i++) {
            res[i] = String.format(makePrefix("", getTitleJustification()), title.get(i));
        }

        String prefixNotSel = ScrollingMenuSign.getInstance().getConfig().getString("sms.item_prefix.not_selected", "  ").replace("%", "%%");
        String prefixSel = ScrollingMenuSign.getInstance().getConfig().getString("sms.item_prefix.selected", "> ").replace("%", "%%");
        ViewJustification ij = getItemJustification();
        int pageSize = res.length - title.size();
        int menuSize = getActiveMenuItemCount(null);

        switch (getScrollType()) {
            case SCROLL:
                // line 2-4 are the menu items around the current menu position
                // line 3 is the current position
                if (title.size() < 2) {
                    res[1] = String.format(makePrefix(prefixNotSel, ij), getLine2Item(scrollPos));
                }
                res[2] = String.format(makePrefix(prefixSel, ij), getLine3Item(scrollPos));
                res[3] = String.format(makePrefix(prefixNotSel, ij), getLine4Item(scrollPos));
                break;
            case PAGE:
                int pageNum = (scrollPos - 1) / pageSize;
                for (int j = 0, pos = (pageNum * pageSize) + 1; j < pageSize && pos <= menuSize; j++, pos++) {
                    String pre = pos == scrollPos ? prefixSel : prefixNotSel;
                    res[j + title.size()] = String.format(makePrefix(pre, ij), getActiveItemLabel(null, pos));
                }
                break;
        }

        return res;
    }

    private String getLine2Item(int pos) {
        if (getActiveMenuItemCount(null) < 3)
            return "";

        int prevPos = pos - 1;
        if (prevPos < 1) {
            prevPos = getActiveMenuItemCount(null);
        }
        return getActiveItemLabel(null, prevPos);
    }

    private String getLine3Item(int pos) {
        if (getActiveMenuItemCount(null) < 1) {
            return "";
        }
        return getActiveItemLabel(null, pos);
    }

    private String getLine4Item(int pos) {
        if (getActiveMenuItemCount(null) < 2)
            return "";

        int nextPos = pos + 1;
        if (nextPos > getActiveMenuItemCount(null)) {
            nextPos = 1;
        }
        return getActiveItemLabel(null, nextPos);
    }

    private String makePrefix(String prefix, ViewJustification just) {
        int l = SIGN_WIDTH - prefix.length();
        String s = "";
        switch (just) {
            case LEFT:
                s = prefix + "%1$-" + l + "s";
                break;
            case CENTER:
                s = prefix + "%1$s";
                break;
            case RIGHT:
                s = prefix + "%1$" + l + "s";
                break;
            default:
                break;
        }
        return MiscUtil.parseColourSpec(s);
    }

    /**
     * Get the actual Bukkit Sign object for this view.
     *
     * @return The Sign object
     */
    private Sign getSign() {
        if (getLocations().isEmpty())
            return null;

        Location loc = getLocationsArray()[0];
        Block b = loc.getBlock();
        if (b.getType() != Material.SIGN_POST && b.getType() != Material.WALL_SIGN) {
            return null;
        }
        return (Sign) b.getState();
    }


    @Override
    public String toString() {
        Location[] locs = getLocationsArray();
        return "sign @ " + (locs.length == 0 ? "NONE" : MiscUtil.formatLocation(getLocationsArray()[0]));
    }

    /* (non-Javadoc)
     * @see me.desht.scrollingmenusign.views.SMSView#getType()
     */
    @Override
    public String getType() {
        return "sign";
    }

    /* (non-Javadoc)
     * @see me.desht.scrollingmenusign.views.SMSScrollableView#getLineLength()
     */
    @Override
    protected int getLineLength() {
        return SIGN_WIDTH;
    }

    /* (non-Javadoc)
     * @see me.desht.scrollingmenusign.views.SMSScrollableView#getHardMaxTitleLines()
     */
    @Override
    protected int getHardMaxTitleLines() {
        return 2;
    }
}
