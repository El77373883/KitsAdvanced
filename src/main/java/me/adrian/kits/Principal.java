package me.adrian.kits;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class Principal extends JavaPlugin implements Listener {

    private final Map<UUID, String> editandoKit = new HashMap<>();
    private final Map<UUID, String> esperandoNombre = new HashMap<>();
    private final Map<UUID, String> esperandoPrecio = new HashMap<>();
    private final Map<UUID, String> esperandoCooldown = new HashMap<>();

    @Override
    public void onEnable() {
        File f = new File(getDataFolder(), "kits");
        if (!f.exists()) f.mkdirs();
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("akits").setExecutor(new KitsCommand());
    }

    public String c(String m) { return ChatColor.translateAlternateColorCodes('&', m); }

    // --- EL MÉTODO QUE ACEPTA TODO (FIJATE EN EL String... lore) ---
    private ItemStack crearItem(Material mat, String nombre, int data, String... lore) {
        ItemStack item = new ItemStack(mat, 1, (short) data);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(c(nombre));
            if (lore.length > 0) {
                List<String> lista = new ArrayList<>();
                for (String s : lore) lista.add(c(s));
                meta.setLore(lista);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public void abrirMenuPrincipal(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c("&8Menú Global de Kits"));
        for (int i = 0; i < 54; i++) inv.setItem(i, crearItem(Material.STAINED_GLASS_PANE, " ", 0));
        
        inv.setItem(22, crearItem(Material.CHEST, "&a&lKITS GRATUITOS", 0, "&7Click para ver kits normales."));
        inv.setItem(49, crearItem(Material.DIAMOND_BLOCK, "&b&lSECCIÓN PREMIUM", 0, "&7Click para ver kits VIP."));
        p.openInventory(inv);
    }

    public void abrirListaKits(Player p, boolean premium) {
        Inventory inv = Bukkit.createInventory(null, 54, c(premium ? "&bSección VIP" : "&aSección Gratuita"));
        int color = premium ? 3 : 5;
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i > 44 || i % 9 == 0 || (i + 1) % 9 == 0) inv.setItem(i, crearItem(Material.STAINED_GLASS_PANE, " ", color));
        }

        File folder = new File(getDataFolder(), "kits");
        File[] archivos = folder.listFiles();
        if (archivos != null) {
            for (File f : archivos) {
                FileConfiguration cf = YamlConfiguration.loadConfiguration(f);
                if (cf.getBoolean("premium") == premium) {
                    String id = f.getName().replace(".yml", "");
                    inv.addItem(crearItem(Material.CHEST, "&e" + cf.getString("nombre_visual"), 0, "&7ID: " + id, "&eClick para editar"));
                }
            }
        }
        inv.setItem(49, crearItem(Material.ARROW, "&c« Volver", 0));
        p.openInventory(inv);
    }

    public void abrirEditorKit(Player p, String id) {
        editandoKit.put(p.getUniqueId(), id);
        FileConfiguration cf = getKitConfig(id);
        Inventory inv = Bukkit.createInventory(null, 54, c("&8Ajustes: " + id));
        
        inv.setItem(11, crearItem(Material.REDSTONE, "&ePermiso Status", 0, "&7Estado: " + cf.getBoolean("permiso_status")));
        inv.setItem(12, crearItem(Material.SUNFLOWER, "&ePrecio", 0, "&7Actual: " + cf.getDouble("precio")));
        inv.setItem(13, crearItem(Material.CLOCK, "&eCooldown", 0, "&7Actual: " + cf.getLong("cooldown")));
        inv.setItem(16, crearItem(Material.NAME_TAG, "&eNombre Visual", 0, "&7Actual: " + cf.getString("nombre_visual")));
        inv.setItem(19, crearItem(cf.getBoolean("premium") ? Material.DIAMOND : Material.EMERALD, "&bCategoría VIP", 0));
        inv.setItem(21, crearItem(Material.BARRIER, "&c&lELIMINAR KIT", 0));
        inv.setItem(49, crearItem(Material.ARROW, "&c« Volver", 0));
        p.openInventory(inv);
    }

    @EventHandler
    public void alClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.getCurrentItem() == null) return;
        Player p = (Player) e.getWhoClicked();
        String t = e.getView().getTitle();
        int slot = e.getRawSlot();

        if (t.contains("Global") || t.contains("Sección")) {
            e.setCancelled(true);
            if (slot == 22) abrirListaKits(p, false);
            else if (slot == 49) {
                if(t.contains("Global")) abrirListaKits(p, true);
                else abrirMenuPrincipal(p);
            }
        } else if (t.contains("Ajustes:")) {
            e.setCancelled(true);
            String id = editandoKit.get(p.getUniqueId());
            File f = new File(getDataFolder(), "kits/" + id + ".yml");
            FileConfiguration cf = YamlConfiguration.loadConfiguration(f);
            
            if (slot == 11) cf.set("permiso_status", !cf.getBoolean("permiso_status"));
            else if (slot == 12) { p.closeInventory(); esperandoPrecio.put(p.getUniqueId(), id); return; }
            else if (slot == 13) { p.closeInventory(); esperandoCooldown.put(p.getUniqueId(), id); return; }
            else if (slot == 16) { p.closeInventory(); esperandoNombre.put(p.getUniqueId(), id); return; }
            else if (slot == 19) cf.set("premium", !cf.getBoolean("premium"));
            else if (slot == 21) { f.delete(); p.closeInventory(); p.sendMessage(c("&cKit eliminado.")); return; }
            else if (slot == 49) { abrirMenuPrincipal(p); return; }
            
            saveKit(f, cf);
            abrirEditorKit(p, id);
        }
    }

    @EventHandler
    public void alChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (esperandoNombre.containsKey(p.getUniqueId())) actualizarDato(p, e, "nombre_visual", esperandoNombre, false);
        else if (esperandoPrecio.containsKey(p.getUniqueId())) actualizarDato(p, e, "precio", esperandoPrecio, true);
        else if (esperandoCooldown.containsKey(p.getUniqueId())) actualizarDato(p, e, "cooldown", esperandoCooldown, true);
    }

    private void actualizarDato(Player p, AsyncPlayerChatEvent e, String ruta, Map<UUID, String> mapa, boolean esNum) {
        e.setCancelled(true);
        String id = mapa.remove(p.getUniqueId());
        FileConfiguration cf = getKitConfig(id);
        if (esNum) {
            try { cf.set(ruta, Double.parseDouble(e.getMessage())); } catch (Exception ex) {}
        } else { cf.set(ruta, e.getMessage()); }
        saveKit(new File(getDataFolder(), "kits/" + id + ".yml"), cf);
        Bukkit.getScheduler().runTask(this, () -> abrirEditorKit(p, id));
    }

    private void saveKit(File f, FileConfiguration c) { try { c.save(f); } catch (IOException e) {} }
    private FileConfiguration getKitConfig(String k) { return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "kits/" + k + ".yml")); }

    public class KitsCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
            if (s instanceof Player) {
                Player p = (Player) s;
                if (args.length > 1 && args[0].equalsIgnoreCase("admin")) abrirEditorKit(p, args[1]);
                else abrirMenuPrincipal(p);
            }
            return true;
        }
    }
}
