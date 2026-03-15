package me.adrian.kits;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class Principal extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("KitsAdvanced activo.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("akits")) {
            if (args.length == 0) {
                p.sendMessage(color("&eUsa: /akits create <nombre> o /akits panel"));
                return true;
            }

            if (args[0].equalsIgnoreCase("create") && args.length == 2) {
                ItemStack item = p.getInventory().getItemInMainHand();
                if (item == null || item.getType() == Material.AIR) {
                    p.sendMessage(color("&c¡Debes tener un item en la mano!"));
                    return true;
                }
                getConfig().set("Kits." + args[1] + ".item", item);
                saveConfig();
                p.sendMessage(color("&aKit &e" + args[1] + " &acreado con el item de tu mano."));
                return true;
            }

            if (args[0].equalsIgnoreCase("panel")) {
                Inventory inv = Bukkit.createInventory(null, 27, color("&0&lPanel de Kits"));
                if (getConfig().getConfigurationSection("Kits") != null) {
                    // AQUÍ ESTABA EL ERROR: Se cambió keySet() por getKeys(false)
                    for (String key : getConfig().getConfigurationSection("Kits").getKeys(false)) {
                        ItemStack icon = new ItemStack(Material.CHEST);
                        ItemMeta meta = icon.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName(color("&6Kit: &e" + key));
                            icon.setItemMeta(meta);
                        }
                        inv.addItem(icon);
                    }
                }
                p.openInventory(inv);
                return true;
            }
        }
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTitle().equals(color("&0&lPanel de Kits"))) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;
            
            Player p = (Player) e.getWhoClicked();
            // Corregido para evitar errores de texto
            String displayName = e.getCurrentItem().getItemMeta().getDisplayName();
            String name = ChatColor.stripColor(displayName).replace("Kit: ", "");
            
            ItemStack kitItem = getConfig().getItemStack("Kits." + name + ".item");
            if (kitItem != null) {
                p.getInventory().addItem(kitItem.clone());
                p.sendMessage(color("&aHas recibido el kit: &e" + name));
            }
        }
    }

    public String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
