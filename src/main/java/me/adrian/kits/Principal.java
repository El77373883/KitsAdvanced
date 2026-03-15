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

import java.util.ArrayList;
import java.util.List;

public class Principal extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("KitsAdvanced por Adrian YT activo.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("advancedkits")) {
            if (args.length == 0) {
                p.sendMessage(color(getConfig().getString("prefix") + getConfig().getString("Messages.Usage")));
                return true;
            }

            // COMANDO RELOAD
            if (args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                p.sendMessage(color(getConfig().getString("prefix") + getConfig().getString("Messages.Reloaded")));
                return true;
            }

            // COMANDO INFO
            if (args[0].equalsIgnoreCase("info")) {
                p.sendMessage(color("&8&m-------&6 KitsAdvanced &8&m-------"));
                p.sendMessage(color("&eAutor: &fAdrian YT"));
                p.sendMessage(color("&eVersion: &f1.0"));
                p.sendMessage(color("&7Protegido y optimizado desde iPhone."));
                return true;
            }

            // COMANDO CREATE
            if (args[0].equalsIgnoreCase("create") && args.length == 2) {
                ItemStack item = p.getInventory().getItemInMainHand();
                if (item.getType() == Material.AIR) {
                    p.sendMessage(color(getConfig().getString("prefix") + getConfig().getString("Messages.HoldItem")));
                    return true;
                }
                getConfig().set("Kits." + args[1] + ".item", item);
                saveConfig();
                p.sendMessage(color(getConfig().getString("prefix") + getConfig().getString("Messages.KitCreated").replace("%kit%", args[1])));
                return true;
            }

            // COMANDO PANEL
            if (args[0].equalsIgnoreCase("panel")) {
                openAdminPanel(p);
                return true;
            }

            // SI EL COMANDO NO EXISTE
            p.sendMessage(color(getConfig().getString("prefix") + getConfig().getString("Messages.UnknownCommand")));
            return true;
        }
        return true;
    }

    public void openAdminPanel(Player p) {
        String title = color(getConfig().getString("Messages.AdminPanelTitle"));
        Inventory inv = Bukkit.createInventory(null, 27, title);
        
        if (getConfig().getConfigurationSection("Kits") != null) {
            for (String key : getConfig().getConfigurationSection("Kits").keySet()) {
                ItemStack icon = new ItemStack(Material.CHEST);
                ItemMeta meta = icon.getItemMeta();
                meta.setDisplayName(ChatColor.GOLD + "Kit: " + ChatColor.YELLOW + key);
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Administrar este kit");
                meta.setLore(lore);
                icon.setItemMeta(meta);
                inv.addItem(icon);
            }
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTitle().equals(color(getConfig().getString("Messages.AdminPanelTitle")))) {
            e.setCancelled(true);
        }
    }

    public String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
