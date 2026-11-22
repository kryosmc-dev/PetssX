package me.mascotasx;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

public class AdminPetEditMenu extends AdminBase {
    private static final List<String> ATTACKS = List.of("melee", "arrow", "fireball", "snowball", "spit", "potion", "wind", "fangs");
    private final String petId;

    public AdminPetEditMenu(MascotasX plugin, Player p, String petId) {
        super(plugin, p, title("Editar mascota"));
        this.petId = petId;
    }

    @Override
    protected void build() {
        fill();
        PetType t = plugin.manager().type(petId);
        if (t == null) { player.closeInventory(); return; }
        inv.setItem(4, Items.of(t.icon(), "<gold>" + t.name(), " ", "<gray>ID: <white>" + t.id(), "<gray>Entidad: <white>" + t.entity().name()));
        inv.setItem(20, Items.of(Material.GOLD_INGOT, "<green>Precio", " ", "<gray>Actual: <green>" + Text.money(t.price()), " ",
                "<yellow>Click izq: <white>+100", "<yellow>Click der: <white>-100", "<yellow>Shift: <white>x10"));
        inv.setItem(22, Items.of(Material.DIAMOND_SWORD, "<red>Daño base", " ", "<gray>Actual: <red>" + Text.num(t.damage()), " ",
                "<yellow>Click izq: <white>+0.5", "<yellow>Click der: <white>-0.5", "<yellow>Shift: <white>x10"));
        inv.setItem(24, Items.of(t.enabled() ? Material.LIME_DYE : Material.GRAY_DYE, "<yellow>En la tienda", " ",
                "<gray>Estado: " + (t.enabled() ? "<green>Visible" : "<red>Oculta"), " ", "<yellow>Click para cambiar"));
        inv.setItem(29, Items.of(Material.BOW, "<aqua>Tipo de ataque", " ", "<gray>Actual: <white>" + t.attack(),
                "<gray>melee, arrow, fireball, snowball,", "<gray>spit, potion, wind, fangs", " ", "<yellow>Click para cambiar"));
        inv.setItem(31, Items.of(Material.FEATHER, "<aqua>Vuela", " ", "<gray>Estado: " + (t.flying() ? "<green>Sí" : "<red>No"), " ", "<yellow>Click para cambiar"));
        inv.setItem(33, Items.of(Material.NAME_TAG, "<yellow>Nombre en la tienda", " ", "<gray>Actual: <white>" + t.name(), " ", "<yellow>Click para cambiar (chat)"));
        inv.setItem(35, Items.of(t.icon(), "<yellow>Icono en la tienda", " ", "<gray>Actual: <white>" + t.icon().name().toLowerCase().replace('_', ' '), " ",
                "<yellow>Click izq: <white>usar el ítem de tu mano", "<yellow>Click der: <white>restaurar"));
        inv.setItem(38, Items.of(Material.BOOK, "<aqua>Página de la tienda", " ", "<gray>Actual: <white>" + t.page(), " ",
                "<yellow>Click izq: <white>+1", "<yellow>Click der: <white>-1"));
        inv.setItem(40, Items.of(Material.COMPASS, "<aqua>Posición (slot)", " ", "<gray>Actual: <white>" + t.slot(), " ",
                "<yellow>Click izq: <white>+1", "<yellow>Click der: <white>-1", "<yellow>Shift: <white>±9 (una fila)"));
        inv.setItem(49, Items.of(Material.BARRIER, "<red>Volver"));
    }

    @Override
    protected void onClick(InventoryClickEvent e) {
        PetType t = plugin.manager().type(petId);
        if (t == null) return;
        PetManager m = plugin.manager();
        switch (e.getRawSlot()) {
            case 20 -> {
                double d = delta(e, 100);
                if (d == 0) return;
                m.setPetValue(petId, "price", Math.max(0, t.price() + d));
                build();
            }
            case 22 -> {
                double d = delta(e, 0.5);
                if (d == 0) return;
                m.setPetValue(petId, "damage", Math.max(0.5, Math.round((t.damage() + d) * 10) / 10.0));
                build();
            }
            case 24 -> { m.setPetValue(petId, "enabled", !t.enabled()); build(); }
            case 29 -> {
                int i = ATTACKS.indexOf(t.attack());
                m.setPetValue(petId, "attack", ATTACKS.get((i + 1) % ATTACKS.size()));
                build();
            }
            case 31 -> { m.setPetValue(petId, "flying", !t.flying()); build(); }
            case 33 -> plugin.listener().awaitInput(player, "<gold><bold>ADMIN</bold> <dark_gray>» <yellow>Escribe el nuevo nombre <gray>(o <white>cancelar<gray>).", text -> {
                m.setPetValue(petId, "name", text);
                say("<green>Nombre actualizado.");
                new AdminPetEditMenu(plugin, player, petId).open();
            });
            case 35 -> {
                if (e.isRightClick()) { m.setPetValue(petId, "icon", null); build(); return; }
                Material hand = player.getInventory().getItemInMainHand().getType();
                if (hand.isAir() || !hand.isItem()) { say("<red>Sostén en la mano el ítem que quieres usar de icono."); return; }
                m.setPetValue(petId, "icon", hand.name());
                build();
            }
            case 38 -> {
                int d = (int) delta(e, 1);
                if (d == 0) return;
                m.setPetValue(petId, "page", Math.max(1, t.page() + (d > 0 ? 1 : -1)));
                build();
            }
            case 40 -> {
                double d = delta(e, 1);
                if (d == 0) return;
                int step = e.isShiftClick() ? 9 : 1;
                m.setPetValue(petId, "slot", Math.max(0, Math.min(53, t.slot() + (d > 0 ? step : -step))));
                build();
            }
            case 49 -> new AdminPetPickMenu(plugin, player, AdminPetPickMenu.Mode.EDIT, 1, null).open();
            default -> { }
        }
    }
}
