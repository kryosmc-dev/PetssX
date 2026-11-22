package me.mascotasx;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Camino de niveles como en la imagen de referencia: solo los slots del camino llevan cristal,
 * el resto queda vacio. 23 niveles por pagina, el camino sube en zigzag desde abajo.
 */
public class LevelsMenu extends Menu {
    private static final int PER_PAGE = 23;
    private final String petId;
    private final int page;

    public LevelsMenu(MascotasX plugin, Player p, String petId, int page) {
        super(plugin, p, title(plugin, petId, page));
        this.petId = petId;
        this.page = page;
    }

    private static int pages(MascotasX plugin) {
        return Math.max(1, (int) Math.ceil(plugin.manager().maxLevel() / (double) PER_PAGE));
    }

    private static String title(MascotasX plugin, String petId, int page) {
        PetType t = plugin.manager().type(petId);
        String raw = plugin.getConfig().getString("titles.levels", "");
        if (!raw.contains("%pet%")) raw = "<dark_gray><bold>MENU</bold> | <gray>%pet% (%page%/%pages%)";
        return raw.replace("%pet%", t == null ? petId : t.name())
                .replace("%page%", String.valueOf(page)).replace("%pages%", String.valueOf(pages(plugin)));
    }

    /** Camino: fila 4 (izq->der), sube por la derecha, fila 2 (der->izq), sube por la izquierda, fila 0 (izq->der). */
    private static int pathSlot(int i) {
        if (i < 7) return 36 + 1 + i;          // 37..43
        if (i == 7) return 34;                 // conector derecho
        if (i < 15) return 18 + (7 - (i - 8)); // 25..19
        if (i == 15) return 10;                // conector izquierdo
        return 1 + (i - 16);                   // 1..7
    }

    @Override
    protected void build() {
        inv.clear(); // sin relleno: solo el camino, como en la referencia
        PetData d = data(petId);
        PetType t = plugin.manager().type(petId);
        if (d == null || t == null) { player.closeInventory(); return; }
        int max = plugin.manager().maxLevel();
        int start = (page - 1) * PER_PAGE + 1;

        for (int i = 0; i < PER_PAGE && start + i <= max; i++) {
            int lvl = start + i;
            boolean reached = d.level >= lvl;
            Material m = reached ? plugin.icon("levels.reached", Material.LIME_STAINED_GLASS_PANE)
                    : lvl == d.level + 1 ? plugin.icon("levels.next", Material.YELLOW_STAINED_GLASS_PANE)
                    : plugin.icon("levels.locked", Material.PINK_STAINED_GLASS_PANE);

            List<String> lore = new ArrayList<>();
            lore.add(" ");
            lore.add("<white>Recompensas:");
            lore.add("<gray>» Otorga <green>+" + Text.num(lvl * plugin.manager().bonus()) + "%<gray> de bonificación de daño");
            ConfigurationSection r = plugin.getConfig().getConfigurationSection("levels.rewards." + lvl);
            if (r != null) {
                double money = r.getDouble("money", 0);
                if (money > 0) lore.add("<gray>» Dinero: <green>" + Text.money(money));
                List<String> desc = r.getStringList("description");
                for (String s : desc) lore.add("<gray>» " + s);
                if (desc.isEmpty() && !r.getStringList("commands").isEmpty()) lore.add("<gray>» Recompensa especial");
            }
            ConfigurationSection es = plugin.manager().effectSection(petId);
            if (es != null) for (String k : es.getKeys(false))
                if (es.getInt(k + ".level", 1) == lvl) lore.add("<gray>» Desbloquea el efecto <aqua>" + es.getString(k + ".name", k));
            if (lvl == plugin.getConfig().getInt("cosmetics.hat-level", 5)) lore.add("<gray>» Desbloquea <aqua>usarla de sombrero");
            if (lvl == plugin.getConfig().getInt("cosmetics.mount-level", 10)) lore.add("<gray>» Desbloquea <aqua>montarla");

            lore.add(" ");
            lore.add(reached ? "<green><bold>DESBLOQUEADO" : "<red><bold>BLOQUEADO");
            if (!reached) lore.add("<gray>XP para este nivel: <white>" + (int) plugin.manager().need(lvl - 1));
            inv.setItem(pathSlot(i), Items.glow(Items.of(m, lvl, "<gold>" + d.displayName(t) + " <white>Lvl. " + lvl, lore), d.level == lvl));
        }
        if (page > 1) inv.setItem(45, Items.of(Material.ARROW, "<green>« Anterior"));
        if (page < pages(plugin)) inv.setItem(53, Items.of(Material.ARROW, "<green>Siguiente »"));
        inv.setItem(49, Items.of(Material.BARRIER, "<red>Volver"));
    }

    @Override
    public void click(InventoryClickEvent e) {
        int slot = e.getRawSlot();
        if (slot == 49) new DataMenu(plugin, player, petId).open();
        else if (slot == 45 && page > 1) new LevelsMenu(plugin, player, petId, page - 1).open();
        else if (slot == 53 && page < pages(plugin)) new LevelsMenu(plugin, player, petId, page + 1).open();
    }
}
