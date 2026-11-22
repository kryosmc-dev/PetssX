package me.mascotasx;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.UUID;

public class AdminPlayerMenu extends AdminBase {
    private final UUID target;

    public AdminPlayerMenu(MascotasX plugin, Player p, UUID target) {
        super(plugin, p, title("Jugador"));
        this.target = target;
    }

    private Player tp() { return plugin.getServer().getPlayer(target); }

    @Override
    protected void build() {
        fill();
        Player t = tp();
        if (t == null) { player.closeInventory(); return; }
        Profile pr = plugin.manager().profile(target);
        PetData d = pr.active == null ? null : pr.owned.get(pr.active);
        inv.setItem(4, Items.of(Material.PLAYER_HEAD, "<yellow>" + t.getName(), " ", "<gray>Mascotas: <white>" + pr.owned.size()));
        inv.setItem(20, Items.of(Material.CHEST, "<green>Dar mascota", " ", "<gray>Elige una mascota para entregarle.", " ", "<yellow>Click para elegir"));
        inv.setItem(22, Items.of(Material.EXPERIENCE_BOTTLE, "<aqua>Nivel de su mascota invocada", " ",
                d == null ? "<red>No tiene una mascota invocada" : "<gray>Nivel actual: <white>" + d.level + "<gray>/" + plugin.manager().maxLevel(), " ",
                "<yellow>Click izq: <white>+1", "<yellow>Click der: <white>-1", "<yellow>Shift: <white>x5"));
        inv.setItem(24, Items.of(Material.TNT, "<red>Quitar todas sus mascotas", " ", "<gray>Esto no se puede deshacer.", " ", "<yellow>Shift + click para confirmar"));
        inv.setItem(49, Items.of(Material.BARRIER, "<red>Volver"));
    }

    @Override
    protected void onClick(InventoryClickEvent e) {
        Player t = tp();
        if (t == null) { player.closeInventory(); return; }
        PetManager m = plugin.manager();
        switch (e.getRawSlot()) {
            case 20 -> new AdminPetPickMenu(plugin, player, AdminPetPickMenu.Mode.GIVE, 1, target).open();
            case 22 -> {
                Profile pr = m.profile(target);
                PetData d = pr.active == null ? null : pr.owned.get(pr.active);
                if (d == null) { say("<red>No tiene una mascota invocada."); return; }
                int step = e.isShiftClick() ? 5 : 1;
                d.level = Math.max(1, Math.min(m.maxLevel(), d.level + (e.isLeftClick() ? step : -step)));
                d.xp = 0;
                m.refresh(t);
                m.save(target);
                build();
            }
            case 24 -> {
                if (!e.isShiftClick()) { say("<yellow>Usa Shift + click para confirmar."); return; }
                m.wipe(t);
                say("<green>Se quitaron las mascotas de " + t.getName() + ".");
                build();
            }
            case 49 -> new AdminPlayersMenu(plugin, player).open();
            default -> { }
        }
    }
}
