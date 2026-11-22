package me.mascotasx;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

/** Edita todos los mensajes del plugin (messages.* en config.yml) desde el juego. */
public class AdminMessagesMenu extends AdminBase {
    private static final int PER_PAGE = 28;
    private final int page;
    private final List<String> keys = new ArrayList<>();

    public AdminMessagesMenu(MascotasX plugin, Player p, int page) {
        super(plugin, p, title("Mensajes (" + page + ")"));
        this.page = page;
    }

    @Override
    protected void build() {
        fill();
        keys.clear();
        ConfigurationSection ms = plugin.getConfig().getConfigurationSection("messages");
        if (ms != null) keys.addAll(ms.getKeys(false));
        int start = (page - 1) * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < keys.size(); i++) {
            String k = keys.get(start + i);
            inv.setItem(grid(i), Items.of(Material.PAPER, "<yellow>" + k, " ", "<gray>Actual:", "<white>" + ms.getString(k, ""), " ",
                    "<yellow>Click izq: <white>editar (chat)", "<yellow>Click der: <white>restaurar original",
                    "<gray>Variables: %pet% %price% %level% %name% %player%"));
        }
        if (page > 1) inv.setItem(45, Items.of(Material.ARROW, "<green>« Anterior"));
        if (start + PER_PAGE < keys.size()) inv.setItem(53, Items.of(Material.ARROW, "<green>Siguiente »"));
        inv.setItem(49, Items.of(Material.BARRIER, "<red>Volver"));
    }

    @Override
    protected void onClick(InventoryClickEvent e) {
        int slot = e.getRawSlot();
        if (slot == 49) { new AdminMenu(plugin, player).open(); return; }
        if (slot == 45 && page > 1) { new AdminMessagesMenu(plugin, player, page - 1).open(); return; }
        if (slot == 53 && page * PER_PAGE < keys.size()) { new AdminMessagesMenu(plugin, player, page + 1).open(); return; }
        for (int i = 0; i < PER_PAGE; i++) {
            if (grid(i) != slot) continue;
            int idx = (page - 1) * PER_PAGE + i;
            if (idx >= keys.size()) return;
            String path = "messages." + keys.get(idx);
            if (e.isRightClick()) {
                plugin.getConfig().set(path, null); // vuelve al original del jar
                plugin.saveConfig();
                build();
            } else {
                plugin.listener().awaitInput(player, "<gold><bold>ADMIN</bold> <dark_gray>» <yellow>Escribe el nuevo texto de <white>" + keys.get(idx)
                        + " <gray>(o <white>cancelar<gray>).", text -> {
                    plugin.getConfig().set(path, text);
                    plugin.saveConfig();
                    say("<green>Mensaje actualizado.");
                    new AdminMessagesMenu(plugin, player, page).open();
                });
            }
            return;
        }
    }
}
