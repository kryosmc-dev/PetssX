package me.mascotasx;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

public class AdminEffectsMenu extends AdminBase {
    private final String petId;
    private final List<String> keys = new ArrayList<>();

    public AdminEffectsMenu(MascotasX plugin, Player p, String petId) {
        super(plugin, p, title("Efectos de mascota"));
        this.petId = petId;
    }

    @Override
    protected void build() {
        fill();
        keys.clear();
        ConfigurationSection es = plugin.manager().effectSection(petId);
        if (es != null) keys.addAll(es.getKeys(false));
        for (int i = 0; i < keys.size() && i < 28; i++) {
            String k = keys.get(i);
            Material m = Material.matchMaterial(es.getString(k + ".icon", "POTION"));
            inv.setItem(grid(i), Items.of(m == null ? Material.POTION : m, "<aqua>" + es.getString(k + ".name", k), " ",
                    "<gray>Efecto: <white>" + es.getString(k + ".type", "?") + " <gray>(nivel " + (es.getInt(k + ".amplifier", 0) + 1) + ")",
                    "<gray>Se desbloquea en el nivel: <gold>" + es.getInt(k + ".level", 1), " ",
                    "<yellow>Click izq: <white>+1", "<yellow>Click der: <white>-1", "<yellow>Shift: <white>x10"));
        }
        inv.setItem(4, Items.of(Material.PAPER, "<gold>" + (plugin.manager().type(petId) == null ? petId : plugin.manager().type(petId).name()),
                " ", "<gray>Para cambiar tipo o potencia de un efecto", "<gray>edita <white>effects.yml<gray>."));
        inv.setItem(49, Items.of(Material.BARRIER, "<red>Volver"));
    }

    @Override
    protected void onClick(InventoryClickEvent e) {
        int slot = e.getRawSlot();
        if (slot == 49) { new AdminPetPickMenu(plugin, player, AdminPetPickMenu.Mode.EFFECTS, 1, null).open(); return; }
        ConfigurationSection es = plugin.manager().effectSection(petId);
        if (es == null) return;
        for (int i = 0; i < keys.size() && i < 28; i++) {
            if (grid(i) != slot) continue;
            double d = delta(e, 1);
            if (d == 0) return;
            String k = keys.get(i);
            plugin.manager().setEffectLevel(petId, k, es.getInt(k + ".level", 1) + (int) d);
            build();
            return;
        }
    }
}
