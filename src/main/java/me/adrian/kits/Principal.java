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
    private final Map<UUID, String> configurandoIcono = new HashMap<>();
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

    // --- MENÚS ---
    public void abrirMenuPrincipal(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c("&8Menú Global de Kits"));
        ItemStack vidrio = createItem(Material.STAINED_GLASS_PANE, " ", false, (short) 0);
        for (int i = 0; i < 54; i++) inv.setItem(i, vidrio);
        inv.setItem(22, createItem(Material.CHEST, "&a&lKITS GRATUITOS", true, (short) 0, "&7Click para ver kits normales."));
        inv.setItem(49, createItem(Material.DIAMOND_BLOCK, "&b&lSECCIÓN PREMIUM", true, (short) 0, "&7Click para ver kits de prestigio."));
        p.openInventory(inv);
    }

    public void abrirListaKits(Player p, boolean premium) {
        Inventory inv = Bukkit.createInventory(null, 54, c(premium ? "&bSección VIP" : "&aSección Gratuita"));
        short color = (short) (premium ? 3 : 5);
        ItemStack vidrio = createItem(Material.STAINED_GLASS_PANE, " ", false, color);
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i > 44 || i % 9 == 0 || (i + 1) % 9 == 0) inv.setItem(i, vidrio);
        }
        File folder = new File(getDataFolder(), "kits");
        File[] archivos = folder.listFiles();
        if (archivos != null) {
            for (File f : archivos) {
                FileConfiguration cf = YamlConfiguration.loadConfiguration(f);
                if (cf.getBoolean("premium") == premium) {
                    Material mat = Material.valueOf(cf.getString("icono", premium ? "DIAMOND_SWORD" : "STONE_SWORD"));
                    inv.addItem(createItem(mat, "&e&l" + cf.getString("nombre_visual"), premium, (short) 0, "&7ID: " + f.getName().replace(".yml", "")));
                }
            }
        }
        inv.setItem(49, createItem(Material.ARROW, "&c« Volver", false, (short) 0));
        p.openInventory(inv);
    }

    public void abrirEditorIcono(Player p, String id) {
        configurandoIcono.put(p.getUniqueId(), id);
        Inventory inv = Bukkit.createInventory(null, 27, c("&8Icono de: " + id));
        inv.setItem(22, createItem(Material.EMERALD_BLOCK, "&a&lGUARDAR ICONO", true, (short) 0));
        p.openInventory(inv);
    }

    public void abrirEditorKit(Player p, String id) {
        editandoKit.put(p.getUniqueId(), id);
        FileConfiguration cf = getKitConfig(id);
        Inventory inv = Bukkit.createInventory(null, 54, c("&8Ajustes: " + id));
        inv.setItem(11, createItem(Material.REDSTONE, "&e2. Permiso", cf.getBoolean("permiso_status"), (short) 0));
        inv.setItem(12, createItem(Material.SUNFLOWER, "&e3. Precio", cf.getDouble("precio") > 0, (short) 0));
        inv.setItem(13, createItem(Material.CLOCK, "&b4. Cooldown", cf.getLong("cooldown") > 0, (short) 0));
        inv.setItem(15, createItem(Material.ITEM_FRAME, "&d6. Cambiar Icono", true, (short) 0));
        inv.setItem(16, createItem(Material.NAME_TAG, "&f7. Nombre Visual", true, (short) 0));
        inv.setItem(19, createItem(cf.getBoolean("premium") ? Material.DIAMOND : Material.EMERALD, "&a8. Categoría", true, (short) 0));
        inv.setItem(49, createItem(Material.ARROW, "&c« Volver", false, (short) 0));
        p.openInventory(inv);
    }

    @EventHandler
    public void alClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.getCurrentItem() == null) return;
        Player p = (Player) e.getWhoClicked();
        String t = e.getView().getTitle();

        if (t.contains("Global") || t.contains("Sección")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 22) abrirListaKits(p, false);
            else if (e.getRawSlot() == 49) {
                if(t.contains("Global")) abrirListaKits(p, true);
                else abrirMenuPrincipal(p);
            }
        } else if (t.contains("Icono de:")) {
            if (e.getRawSlot() == 22) {
                e.setCancelled(true);
                String id = configurandoIcono.remove(p.getUniqueId());
                if (e.getInventory().getItem(13) != null) {
                    FileConfiguration cf = getKitConfig(id);
                    cf.set("icono", e.getInventory().getItem(13).getType().toString());
                    saveKit(new File(getDataFolder(), "kits/" + id + ".yml"), cf);
                }
                abrirEditorKit(p, id);
            }
        } else if (t.contains("Ajustes:")) {
            e.setCancelled(true);
            String id = editandoKit.get(p.getUniqueId());
            File f = new File(getDataFolder(), "kits/" + id + ".yml");
            FileConfiguration cf = YamlConfiguration.loadConfiguration(f);
            
            if (e.getRawSlot() == 11) cf.set("permiso_status", !cf.getBoolean("permiso_status"));
            else if (e.getRawSlot() == 12) { p.closeInventory(); esperandoPrecio.put(p.getUniqueId(), id); return; }
            else if (e.getRawSlot() == 13) { p.closeInventory(); esperandoCooldown.put(p.getUniqueId(), id); return; }
            else if (e.getRawSlot() == 15) { abrirEditorIcono(p, id); return; }
            else if (e.getRawSlot() == 16) { p.closeInventory(); esperandoNombre.put(p.getUniqueId(), id); return; }
            else if (e.getRawSlot() == 19) {
                boolean b = !cf.getBoolean("premium");
                cf.set("premium", b);
                cf.set("icono", b ? "DIAMOND_SWORD" : "STONE_SWORD");
            } else if (e.getRawSlot() == 49) { abrirMenuPrincipal(p); return; }
            
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

    private void actualizarDato(Player p, AsyncPlayerChatEvent e, String ruta, Map<UUID, String> mapa, boolean esNumero) {
        e.setCancelled(true);
        String id = mapa.remove(p.getUniqueId());
        File f = new File(getDataFolder(), "kits/" + id + ".yml");
        FileConfiguration cf = YamlConfiguration.loadConfiguration(f);
        if (esNumero) {
            try { cf.set(ruta, Double.parseDouble(e.getMessage())); } catch (Exception ex) { p.sendMessage(c("&cError: Escribe un número.")); }
        } else { cf.set(ruta, e.getMessage()); }
        saveKit(f, cf);
        Bukkit.getScheduler().runTask(this, () -> abrirEditorKit(p, id));
    }

    private void saveKit(File f, FileConfiguration c) { try { c.save(f); } catch (IOException e) {} }
    private FileConfiguration getKitConfig(String k) { return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "kits/" + k + ".yml")); }

    private ItemStack createItem(Material m, String n, boolean glint, short data, String... lore) {
        ItemStack i = new ItemStack(m, 1, data);
        ItemMeta mt = i.getItemMeta();
        mt.setDisplayName(c(n));
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(c(s));
        mt.setLore(l);
        if (glint) mt.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, true);
        i.setItemMeta(mt);
        return i;
    }

    public class KitsCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender s, Command cmd, String l, String[] a) {
            if (s instanceof Player) {
                Player p = (Player) s;
                if (a.length > 1 && a[0].equalsIgnoreCase("admin")) abrirEditorKit(p, a[1]);
                else abrirMenuPrincipal(p);
            }
            return true;
        }
    }
}
