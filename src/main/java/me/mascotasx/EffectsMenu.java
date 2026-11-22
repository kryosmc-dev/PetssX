package me.mascotasx;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

/** Efectos propios de cada mascota. Solo se puede tener 1 equipado a la vez. */
public class EffectsMenu extends Menu {
    private final String petId;
    private final List<String> keys = new ArrayList<>();

    public EffectsMenu(MascotasX plugin, Player p, String petId) {
        super(plugin, p, plugin.getConfig().getString("titles.effects", "Efectos"));
        this.petId = petId;
    }

    @Override
    protected void build() {
        fill();
        keys.clear();
        PetData d = data(petId);
        if (d == null) { player.closeInventory(); return; }
        ConfigurationSection es = plugin.manager().effectSection(petId);
        if (es != null) keys.addAll(es.getKeys(false));
        for (int i = 0; i < keys.size() && i < 28; i++) {
            String k = keys.get(i);
            String name = es.getString(k + ".name", k);
            int req = es.getInt(k + ".level", 1);
            if (d.level < req) {
                inv.setItem(grid(i), Items.of(Material.BARRIER, "<red>" + name, " ", "<gray>Requiere nivel <white>" + req, "<red>BLOQUEADO"));
                continue;
            }
            Material m = Material.matchMaterial(es.getString(k + ".icon", "POTION"));
            boolean on = k.equals(d.effect);
            inv.setItem(grid(i), Items.glow(Items.of(m == null ? Material.POTION : m, "<aqua>" + name, " ",
                    "<gray>Estado: " + (on ? "<green>Equipado" : "<red>No equipado"),
                    "<gray>Solo puedes tener <white>1 <gray>efecto equipado.",
                    "<gray>Se aplica mientras tu mascota esté invocada.", " ",
                    "<yellow>Click para " + (on ? "quitarlo" : "equiparlo")), on));
        }
        inv.setItem(52, Items.of(Material.BARRIER, "<red>Volver"));
    }

    @Override
    public void click(InventoryClickEvent e) {
        int slot = e.getRawSlot();
        if (slot == 52) { new DataMenu(plugin, player, petId).open(); return; }
        PetData d = data(petId);
        ConfigurationSection es = plugin.manager().effectSection(petId);
        if (d == null || es == null) return;
        for (int i = 0; i < keys.size() && i < 28; i++) {
            if (grid(i) != slot) continue;
            String k = keys.get(i);
            int req = es.getInt(k + ".level", 1);
            if (d.level < req) { plugin.msg(player, "locked", "%level%", String.valueOf(req)); return; }
            plugin.manager().clearEffects(player);          // quita el efecto anterior
            d.effect = k.equals(d.effect) ? null : k;       // equipar / desequipar (siempre solo 1)
            plugin.manager().save(player.getUniqueId());
            build();
            return;
        }
    }
}
