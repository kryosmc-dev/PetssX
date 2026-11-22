package me.mascotasx;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/** Base de los menus del panel de administracion (exige el permiso mascotas.admin). */
public abstract class AdminBase extends Menu {
    protected AdminBase(MascotasX plugin, Player player, String title) {
        super(plugin, player, title);
    }

    @Override
    public void open() {
        if (!player.hasPermission("mascotas.admin")) { plugin.msg(player, "no-perm"); return; }
        super.open();
    }

    @Override
    public final void click(InventoryClickEvent e) {
        if (!player.hasPermission("mascotas.admin")) { player.closeInventory(); return; }
        onClick(e);
    }

    protected abstract void onClick(InventoryClickEvent e);

    /** Click izquierdo suma, derecho resta; con Shift x10. */
    protected static double delta(InventoryClickEvent e, double step) {
        double m = e.isShiftClick() ? 10 : 1;
        if (e.isLeftClick()) return step * m;
        if (e.isRightClick()) return -step * m;
        return 0;
    }

    protected static String title(String sub) {
        return "<dark_gray><bold>ADMIN</bold> | <gray>" + sub;
    }

    protected void say(String s) { player.sendMessage(Text.c("<gold><bold>ADMIN</bold> <dark_gray>» " + s)); }
}
