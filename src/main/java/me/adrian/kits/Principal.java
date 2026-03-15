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
    private final Map<UUID, String> configurandoItems = new HashMap<>();
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

    // --- MENÚS (TODO INCLUIDO) ---
    public void abrirMenuPrincipal(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c("&8Menú Global de Kits"));
        ItemStack vidrio = crearIcono(Material.STAINED_GLASS_PANE, " ", false, 0);
        for (int i = 0; i < 54; i++) inv.setItem(i, vidrio);
        inv.setItem(22, crearIcono(Material.CHEST, "&a&lKITS GRATUITOS", true, 0, "&7Click para ver kits normales."));
        inv.setItem(49, crearIcono(Material.DIAMOND_BLOCK, "&b&lSECCIÓN PREMIUM", true, 0, "&7Click para ver kits de prestigio."));
        p.openInventory(inv);
    }

    public void abrirListaKits(Player p, boolean premium) {
        Inventory inv = Bukkit.createInventory(null, 54, c(premium ? "&bSección VIP" : "&aSección Gratuita"));
        int col = premium ? 3 : 5;
        ItemStack vidrio = crearIcono(Material.STAINED_GLASS_PANE, " ", false, col);
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i > 44 || i % 9 == 0 || (i + 1) % 9 == 0) inv.setItem(i, vidrio);
        }
        File folder = new File(getDataFolder(), "kits");
        File[] archivos = folder.listFiles();
        if (archivos != null) {
            for (File f : archivos) {
                FileConfiguration cf = YamlConfiguration.loadConfiguration(f);
                if (cf.getBoolean("premium") == premium) {
                    String id = f.getName().replace(".yml", "");
                    String iconName = cf.getString("icono", "STONE_SWORD");
                    inv.addItem(crearIcono(Material.valueOf(iconName), "&e&l" + cf.getString("nombre_visual"), premium, 0, "&7ID: " + id, "&eClick Izquierdo: &fVer", "&bClick Derecho: &fObtener"));
                }
            }
        }
        inv.setItem(49, crearIcono(Material.ARROW, "&c« Volver", false, 0));
        p.openInventory(inv);
    }

    public void abrirEditorKit(Player p, String id) {
        editandoKit.put(p.getUniqueId(), id);
        FileConfiguration cf = getKitConfig(id);
        Inventory inv = Bukkit.createInventory(null, 54, c("&8Ajustes: " + id));
        inv.setItem(10, crearIcono(Material.CHEST, "&61. Editar ítems", true, 0));
        inv.setItem(11, crearIcono(Material.REDSTONE, "&e2. Permiso", cf.getBoolean("permiso_status"), 0));
        inv.setItem(12, crearIcono(Material.SUNFLOWER, "&e3. Precio", cf.getDouble("precio") > 0, 0));
        inv.setItem(13, crearIcono(Material.CLOCK, "&b4. Cooldown", cf.getLong("cooldown") > 0, 0));
        inv.setItem(15, crearIcono(Material.ITEM_FRAME, "&d6. Cambiar Icono", true, 0));
        inv.setItem(16, crearIcono(Material.NAME_TAG, "&f7. Nombre Visual", true, 0));
        inv.setItem(19, crearIcono(cf.getBoolean("premium") ? Material.DIAMOND : Material.EMERALD, "&a8. Categoría", true, 0));
        inv.setItem(21, crearIcono(Material.BARRIER, "&c&l10. ELIMINAR KIT", false, 0));
        inv.setItem(49, crearIcono(Material.ARROW, "&c« Volver", false, 0));
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
            else if (slot == 19) {
                boolean b = !cf.getBoolean("premium");
                cf.set("premium", b);
            } else if (slot == 21) { f.delete(); p.closeInventory(); p.sendMessage(c("&cKit eliminado.")); return; }
            else if (slot == 49) { abrirMenuPrincipal(p); return; }
            saveKit(f, cf); abrirEditorKit(p, id);
        }
    }

    @EventHandler
    public void alChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (esperandoNombre.containsKey(p.getUniqueId())) actualizarDato(p, e, "nombre_visual", esperandoNombre, false);
        else if (esperandoPrecio.containsKey(p.getUniqueId())) actualizarDato(p, e, "precio", esperandoPrecio, true);
        else if (esperandoCooldown.containsKey(p.getUniqueId())) actualizarDato(p, e, "cooldown", esperandoCooldown, true);
    }

    private void actualizarDato(Player p, AsyncPlayerChatEvent e, String ruta, Map<UUID, String> mapa, boolean n) {
        e.setCancelled(true);
        String id = mapa.remove(p.getUniqueId());
        FileConfiguration cf = getKitConfig(id);
        if (n) { try { cf.set(ruta, Double.parseDouble(e.getMessage())); } catch (Exception ex) {} }
        else { cf.set(ruta, e.getMessage()); }
        saveKit(new File(getDataFolder(), "kits/" + id + ".yml"), cf);
        Bukkit.getScheduler().runTask(this, () -> abrirEditorKit(p, id));
    }

    private void saveKit(File f, FileConfiguration c) { try { c.save(f); } catch (IOException e) {} }
    private FileConfiguration getKitConfig(String k) { return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "kits/" + k + ".yml")); }

    // --- EL MÉTODO DEFINITIVO (RENOMBRADO PARA EVITAR CONFLICTOS) ---
    private ItemStack crearIcono(Material m, String n, boolean g, int d, String... lore) {
        ItemStack item = new ItemStack(m, 1, (short) d);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(c(n));
            if (lore.length > 0) {
                List<String> l = new ArrayList<>();
                for (String s : lore) l.add(c(s));
                meta.setLore(l);
            }
            if (g) meta.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, true);
            item.setItemMeta(meta);
        }
        return item;
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
