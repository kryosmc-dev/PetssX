package me.mascotasx;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public class AdminMenu extends AdminBase {
    public AdminMenu(MascotasX plugin, Player p) { super(plugin, p, title("Panel")); }

    @Override
    protected void build() {
        fill();
        inv.setItem(10, Items.of(Material.BONE, "<gold>Mascotas", " ", "<gray>Precio, daño, tipo de ataque,", "<gray>nombre y visibilidad en la tienda.", " ", "<yellow>Click para abrir"));
        inv.setItem(12, Items.of(Material.COMPARATOR, "<aqua>Ajustes y niveles", " ", "<gray>XP, dificultad, bonos de daño,", "<gray>niveles de montar y sombrero.", " ", "<yellow>Click para abrir"));
        inv.setItem(14, Items.of(Material.CHEST, "<green>Recompensas por nivel", " ", "<gray>Dinero y comandos de cada nivel.", " ", "<yellow>Click para abrir"));
        inv.setItem(16, Items.of(Material.BREWING_STAND, "<light_purple>Efectos por mascota", " ", "<gray>Nivel de desbloqueo de cada efecto.", " ", "<yellow>Click para abrir"));
        inv.setItem(28, Items.of(Material.PAINTING, "<gold>Personalizar menús", " ", "<gray>Títulos, fondo, iconos y botones", "<gray>de los menús.", " ", "<yellow>Click para abrir"));
        inv.setItem(30, Items.of(Material.WRITABLE_BOOK, "<aqua>Mensajes", " ", "<gray>Edita todos los textos del plugin.", " ", "<yellow>Click para abrir"));
        inv.setItem(32, Items.of(Material.PLAYER_HEAD, "<yellow>Jugadores", " ", "<gray>Dar mascotas, cambiar niveles", "<gray>o quitar mascotas a jugadores.", " ", "<yellow>Click para abrir"));
        inv.setItem(34, Items.of(Material.LIME_DYE, "<green>Recargar archivos", " ", "<gray>Vuelve a leer config.yml,", "<gray>pets.yml y effects.yml.", " ", "<yellow>Click para recargar"));
        inv.setItem(49, Items.of(Material.BARRIER, "<red>Cerrar"));
    }

    @Override
    protected void onClick(InventoryClickEvent e) {
        switch (e.getRawSlot()) {
            case 10 -> new AdminPetPickMenu(plugin, player, AdminPetPickMenu.Mode.EDIT, 1, null).open();
            case 12 -> new AdminSettingsMenu(plugin, player).open();
            case 14 -> new AdminRewardsMenu(plugin, player, 1).open();
            case 16 -> new AdminPetPickMenu(plugin, player, AdminPetPickMenu.Mode.EFFECTS, 1, null).open();
            case 28 -> new AdminCustomizeMenu(plugin, player).open();
            case 30 -> new AdminMessagesMenu(plugin, player, 1).open();
            case 32 -> new AdminPlayersMenu(plugin, player).open();
            case 34 -> { plugin.reload(); say("<green>Archivos recargados."); }
            case 49 -> player.closeInventory();
            default -> { }
        }
    }
}
