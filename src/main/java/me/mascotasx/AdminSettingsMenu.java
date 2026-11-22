package me.mascotasx;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

public class AdminSettingsMenu extends AdminBase {
    /** kind: 0 = entero, 1 = decimal, 2 = si/no, 3 = lista de opciones */
    private record S(String path, String name, Material icon, double step, int kind, String help, List<String> options) {}

    private static S n(String path, String name, Material icon, double step, int kind, String help) {
        return new S(path, name, icon, step, kind, help, null);
    }

    private static final List<S> SETTINGS = List.of(
            n("levels.max", "Nivel máximo", Material.EXPERIENCE_BOTTLE, 5, 0, "Nivel más alto que puede tener una mascota."),
            n("levels.xp-base", "XP base", Material.GLOWSTONE_DUST, 10, 1, "Parte fija de la XP para subir."),
            n("levels.xp-per-level", "XP por nivel", Material.REDSTONE, 5, 1, "Suma XP extra por cada nivel."),
            n("levels.xp-curve", "Curva de dificultad", Material.NETHER_STAR, 0.5, 1, "Más alto = los niveles altos cuestan mucho más."),
            n("levels.xp-multiplier", "Multiplicador de XP", Material.GOLDEN_APPLE, 0.1, 1, "XP ganada por cada kill."),
            n("levels.damage-bonus-percent-per-level", "Bonus de daño (% por nivel)", Material.DIAMOND_SWORD, 0.5, 1, "Daño extra por cada nivel."),
            n("cosmetics.mount-level", "Nivel para montar", Material.SADDLE, 1, 0, "Nivel mínimo para montar la mascota."),
            n("cosmetics.hat-level", "Nivel para sombrero", Material.DIAMOND_HELMET, 1, 0, "Nivel mínimo para usarla de sombrero."),
            n("cosmetics.max-name-length", "Largo máx. del nombre", Material.NAME_TAG, 1, 0, "Caracteres permitidos al nombrar."),
            new S("xp-bar.mode", "Barra de XP: modo", Material.OAK_SIGN, 0, 3, "Cómo se muestra el progreso al ganar XP.", List.of("bossbar", "actionbar", "both")),
            new S("xp-bar.color", "Barra de XP: color", Material.LIME_DYE, 0, 3, "Color de la barra superior.", List.of("GREEN", "BLUE", "PINK", "PURPLE", "RED", "WHITE", "YELLOW")),
            n("xp-bar.duration", "Barra de XP: segundos visible", Material.CLOCK, 1, 0, "Tiempo que se ve tras ganar XP."),
            n("xp-bar.always", "Barra de XP: siempre visible", Material.ENDER_EYE, 0, 2, "Visible mientras la mascota esté invocada."),
            n("auto-summon", "Invocar al entrar", Material.ENDER_PEARL, 0, 2, "Invoca la última mascota al entrar."),
            n("economy.enabled", "Usar economía (Vault)", Material.GOLD_INGOT, 0, 2, "Cobrar las mascotas con dinero.")
    );

    public AdminSettingsMenu(MascotasX plugin, Player p) { super(plugin, p, title("Ajustes")); }

    private String value(S s) {
        if (s.kind == 2) return plugin.getConfig().getBoolean(s.path) ? "<green>Activado" : "<red>Desactivado";
        if (s.kind == 0) return "<white>" + plugin.getConfig().getInt(s.path);
        if (s.kind == 3) return "<white>" + plugin.getConfig().getString(s.path);
        return "<white>" + String.format("%.2f", plugin.getConfig().getDouble(s.path));
    }

    @Override
    protected void build() {
        fill();
        PetManager m = plugin.manager();
        int max = m.maxLevel();
        List<String> info = new ArrayList<>();
        info.add(" ");
        info.add("<gray>XP necesaria para subir:");
        for (int l : new int[]{1, 10, 25, Math.max(1, max - 1)})
            if (l < max) info.add("<gray>» Nivel " + l + " → " + (l + 1) + ": <white>" + (int) m.need(l));
        inv.setItem(4, Items.of(Material.BOOK, "<gold>Vista previa de dificultad", info));

        for (int i = 0; i < SETTINGS.size() && i < 28; i++) {
            S s = SETTINGS.get(i);
            List<String> lore = new ArrayList<>();
            lore.add(" ");
            lore.add("<gray>" + s.help);
            lore.add("<gray>Valor: " + value(s));
            lore.add(" ");
            if (s.kind == 2 || s.kind == 3) lore.add("<yellow>Click para cambiar");
            else {
                lore.add("<yellow>Click izq: <white>+" + Text.num(s.step));
                lore.add("<yellow>Click der: <white>-" + Text.num(s.step));
                lore.add("<yellow>Shift: <white>x10");
            }
            inv.setItem(grid(i), Items.of(s.icon, "<aqua>" + s.name, lore));
        }
        inv.setItem(49, Items.of(Material.BARRIER, "<red>Volver"));
    }

    @Override
    protected void onClick(InventoryClickEvent e) {
        int slot = e.getRawSlot();
        if (slot == 49) { new AdminMenu(plugin, player).open(); return; }
        for (int i = 0; i < SETTINGS.size() && i < 28; i++) {
            if (grid(i) != slot) continue;
            S s = SETTINGS.get(i);
            if (s.kind == 2) plugin.getConfig().set(s.path, !plugin.getConfig().getBoolean(s.path));
            else if (s.kind == 3) {
                int idx = s.options.indexOf(plugin.getConfig().getString(s.path));
                plugin.getConfig().set(s.path, s.options.get((idx + 1) % s.options.size()));
            } else {
                double d = delta(e, s.step);
                if (d == 0) return;
                double v = plugin.getConfig().getDouble(s.path) + d;
                if (s.kind == 0) plugin.getConfig().set(s.path, (int) Math.max(1, Math.round(v)));
                else plugin.getConfig().set(s.path, Math.max(0, Math.round(v * 100) / 100.0));
            }
            plugin.saveConfig();
            build();
            return;
        }
    }
}
