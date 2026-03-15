package me.adrian.kits;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.*;

public class Principal extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        File f = new File(getDataFolder(), "kits");
        if (!f.exists()) f.mkdirs();
        
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("akits").setExecutor(new KitsCommand());
    }

    public String c(String m) { 
        return ChatColor.translateAlternateColorCodes('&', m); 
    }

    // Método que NO da error en VS Code
    private ItemStack crearIcono(Material mat, String nombre, int data, String lore) {
        ItemStack item = new ItemStack(mat, 1, (short) data);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(c(nombre));
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(Collections.singletonList(c(lore)));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public void abrirMenuPrincipal(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c("&8Menú Global de Kits"));
        
        // Fondo de cristal negro
        ItemStack vidrio = crearIcono(Material.STAINED_GLASS_PANE, " ", 15, "");
        for (int i = 0; i < 54; i++) inv.setItem(i, vidrio);
        
        inv.setItem(22, crearIcono(Material.CHEST, "&a&lKITS GRATUITOS", 0, "&7Click para ver kits normales."));
        inv.setItem(49, crearIcono(Material.DIAMOND_BLOCK, "&b&lSECCIÓN PREMIUM", 0, "&7Click para ver kits VIP."));
        
        p.openInventory(inv);
    }

    public void abrirListaKits(Player p, boolean premium) {
        Inventory inv = Bukkit.createInventory(null, 54, c(premium ? "&bSección VIP" : "&aSección Gratuita"));
        
        int colorVidrio = premium ? 3 : 5; 
        ItemStack vidrio = crearIcono(Material.STAINED_GLASS_PANE, " ", colorVidrio, "");
        
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i > 44 || i % 9 == 0 || (i + 1) % 9 == 0) inv.setItem(i, vidrio);
        }

        File folder = new File(getDataFolder(), "kits");
        File[] archivos = folder.listFiles();
        if (archivos != null) {
            for (File f : archivos) {
                if (f.getName().endsWith(".yml")) {
                    FileConfiguration cf = YamlConfiguration.loadConfiguration(f);
                    if (cf.getBoolean("premium") == premium) {
                        String visual = cf.getString("nombre_visual", f.getName().replace(".yml", ""));
                        inv.addItem(crearIcono(Material.CHEST, "&e" + visual, 0, "&7Click para reclamar"));
                    }
                }
            }
        }
        
        inv.setItem(49, crearIcono(Material.ARROW, "&c« Volver", 0, ""));
        p.openInventory(inv);
    }

    @EventHandler
    public void alClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.getCurrentItem() == null) return;
        
        Player p = (Player) e.getWhoClicked();
        String titulo = e.getView().getTitle();
        int slot = e.getRawSlot();

        if (titulo.contains("Kits") || titulo.contains("Sección")) {
            e.setCancelled(true);
            
            if (titulo.contains("Global")) {
                if (slot == 22) abrirListaKits(p, false);
                else if (slot == 49) abrirListaKits(p, true);
            } else {
                if (slot == 49) abrirMenuPrincipal(p);
            }
        }
    }

    public class KitsCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
            if (s instanceof Player) abrirMenuPrincipal((Player) s);
            return true;
        }
    }
}
