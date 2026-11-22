package me.mascotasx;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/** Personalizar titulos de los menus y los iconos (fondo, camino de niveles y botones del menu de datos). */
public class AdminCustomizeMenu extends AdminBase {
    private record T(String key, String name, int slot) {}
    private record Ic(String key, String name, Material def, int slot) {}

    private static final List<T> TITLES = List.of(
            new T("buy", "Comprar", 10), new T("pets", "Mascotas", 11), new T("data", "Datos", 12),
            new T("cosmetics", "Cosméticos", 13), new T("levels", "Niveles", 14), new T("effects", "Efectos", 15));

    private static final List<Ic> ICONS = List.of(
            new Ic("filler", "Fondo de los menús", Material.GRAY_STAINED_GLASS_PANE, 28),
            new Ic("levels.reached", "Nivel desbloqueado", Material.LIME_STAINED_GLASS_PANE, 30),
            new Ic("levels.next", "Siguiente nivel", Material.YELLOW_STAINED_GLASS_PANE, 31),
            new Ic("levels.locked", "Nivel bloqueado", Material.PINK_STAINED_GLASS_PANE, 32),
            new Ic("data.mute", "Botón Silenciar", Material.BELL, 37),
            new Ic("data.burn", "Botón Ardiendo", Material.LAVA_BUCKET, 38),
            new Ic("data.cosmetics", "Botón Cosméticos", Material.ARMOR_STAND, 39),
            new Ic("data.levels", "Botón Niveles", Material.EXPERIENCE_BOTTLE, 40),
            new Ic("data.effects", "Botón Efectos", Material.BREWING_STAND, 41),
            new Ic("data.attack", "Botón Modo de ataque", Material.DIAMOND_SWORD, 42),
            new Ic("data.summon", "Botón Invocar/Guardar", Material.ENDER_PEARL, 43),
            new Ic("data.back", "Botón Volver", Material.BARRIER, 44));

    public AdminCustomizeMenu(MascotasX plugin, Player p) { super(plugin, p, title("Personalizar")); }

    @Override
    protected void build() {
        fill();
        inv.setItem(4, Items.of(Material.BOOK, "<gold>Personalizar menús", " ",
                "<gray>Títulos: click para escribir uno nuevo", "<gray>en el chat (admite colores MiniMessage).",
                "<gray>Iconos: click con un ítem en la mano para", "<gray>usarlo; sin ítem, escribes el material.",
                "<gray>Click derecho: restaurar el original."));
        for (T t : TITLES) {
            inv.setItem(t.slot, Items.of(Material.PAPER, "<yellow>Título: " + t.name, " ",
                    "<gray>Actual:", "<white>" + plugin.getConfig().getString("titles." + t.key, "?"), " ",
                    t.key.equals("levels") ? "<gray>Usa %pet%, %page% y %pages%." : "<gray>Texto libre.",
                    "<yellow>Click izq: <white>cambiar", "<yellow>Click der: <white>restaurar"));
        }
        for (Ic ic : ICONS) {
            Material cur = plugin.icon(ic.key, ic.def);
            inv.setItem(ic.slot, Items.of(cur, "<aqua>" + ic.name, " ",
                    "<gray>Actual: <white>" + cur.name().toLowerCase(Locale.ROOT).replace('_', ' '), " ",
                    "<yellow>Click izq: <white>usar el ítem de tu mano", "<yellow>Click der: <white>restaurar"));
        }
        inv.setItem(49, Items.of(Material.BARRIER, "<red>Volver"));
    }

    @Override
    protected void onClick(InventoryClickEvent e) {
        int slot = e.getRawSlot();
        if (slot == 49) { new AdminMenu(plugin, player).open(); return; }
        for (T t : TITLES) {
            if (t.slot != slot) continue;
            if (e.isRightClick()) {
                plugin.getConfig().set("titles." + t.key, null); // vuelve al valor original
                plugin.saveConfig();
                build();
            } else {
                plugin.listener().awaitInput(player, "<gold><bold>ADMIN</bold> <dark_gray>» <yellow>Escribe el nuevo título de «" + t.name
                        + "» <gray>(o <white>cancelar<gray>).", text -> {
                    plugin.getConfig().set("titles." + t.key, text);
                    plugin.saveConfig();
                    say("<green>Título actualizado.");
                    new AdminCustomizeMenu(plugin, player).open();
                });
            }
            return;
        }
        for (Ic ic : ICONS) {
            if (ic.slot != slot) continue;
            if (e.isRightClick()) {
                plugin.getConfig().set("icons." + ic.key, null);
                plugin.saveConfig();
                build();
                return;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (!hand.getType().isAir() && hand.getType().isItem()) {
                plugin.getConfig().set("icons." + ic.key, hand.getType().name());
                plugin.saveConfig();
                build();
            } else {
                plugin.listener().awaitInput(player, "<gold><bold>ADMIN</bold> <dark_gray>» <yellow>Escribe el material para «" + ic.name
                        + "» <gray>(ej: DIAMOND; o <white>cancelar<gray>).", text -> {
                    Material m = Material.matchMaterial(text.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
                    if (m == null || !m.isItem()) say("<red>Material no válido.");
                    else {
                        plugin.getConfig().set("icons." + ic.key, m.name());
                        plugin.saveConfig();
                        say("<green>Icono actualizado.");
                    }
                    new AdminCustomizeMenu(plugin, player).open();
                });
            }
            return;
        }
    }
}
