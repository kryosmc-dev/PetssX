package me.mascotasx;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Lista de todas las mascotas del servidor; segun el modo abre su edicion, sus efectos o se la da a un jugador. */
public class AdminPetPickMenu extends AdminBase {
    public enum Mode { EDIT, EFFECTS, GIVE }

    private static final int PER_PAGE = 28;
    private final Mode mode;
    private final int page;
    private final UUID target;
    private final List<PetType> list = new ArrayList<>();

    public AdminPetPickMenu(MascotasX plugin, Player p, Mode mode, int page, UUID target) {
        super(plugin, p, title(mode == Mode.EDIT ? "Mascotas" : mode == Mode.EFFECTS ? "Efectos" : "Dar mascota"));
        this.mode = mode;
        this.page = page;
        this.target = target;
    }

    @Override
    protected void build() {
        fill();
        list.clear();
        list.addAll(plugin.manager().types());
        int start = (page - 1) * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < list.size(); i++) {
            PetType t = list.get(start + i);
            List<String> lore = new ArrayList<>();
            lore.add(" ");
            lore.add("<gray>Precio: <green>" + Text.money(t.price()));
            lore.add("<gray>Daño base: <red>" + Text.num(t.damage()));
            lore.add("<gray>Ataque: <white>" + t.attack());
            lore.add("<gray>En la tienda: " + (t.enabled() ? "<green>Sí" : "<red>No"));
            lore.add(" ");
            lore.add("<yellow>Click para " + (mode == Mode.EDIT ? "editar" : mode == Mode.EFFECTS ? "ver efectos" : "entregar"));
            inv.setItem(grid(i), Items.of(t.icon(), "<gold>" + t.name(), lore));
        }
        if (page > 1) inv.setItem(45, Items.of(Material.ARROW, "<green>« Anterior"));
        if (start + PER_PAGE < list.size()) inv.setItem(53, Items.of(Material.ARROW, "<green>Siguiente »"));
        inv.setItem(49, Items.of(Material.BARRIER, "<red>Volver"));
    }

    private void back() {
        if (mode == Mode.GIVE && target != null) new AdminPlayerMenu(plugin, player, target).open();
        else new AdminMenu(plugin, player).open();
    }

    @Override
    protected void onClick(InventoryClickEvent e) {
        int slot = e.getRawSlot();
        if (slot == 49) { back(); return; }
        if (slot == 45 && page > 1) { new AdminPetPickMenu(plugin, player, mode, page - 1, target).open(); return; }
        if (slot == 53 && (page * PER_PAGE) < list.size()) { new AdminPetPickMenu(plugin, player, mode, page + 1, target).open(); return; }
        for (int i = 0; i < PER_PAGE; i++) {
            if (grid(i) != slot) continue;
            int idx = (page - 1) * PER_PAGE + i;
            if (idx >= list.size()) return;
            PetType t = list.get(idx);
            switch (mode) {
                case EDIT -> new AdminPetEditMenu(plugin, player, t.id()).open();
                case EFFECTS -> new AdminEffectsMenu(plugin, player, t.id()).open();
                case GIVE -> {
                    Player tp = target == null ? null : plugin.getServer().getPlayer(target);
                    if (tp == null) { say("<red>El jugador ya no está conectado."); player.closeInventory(); return; }
                    plugin.manager().give(tp, t);
                    say("<green>Entregaste <yellow>" + t.name() + " <green>a <yellow>" + tp.getName() + "<green>.");
                    new AdminPlayerMenu(plugin, player, target).open();
                }
            }
            return;
        }
    }
}
