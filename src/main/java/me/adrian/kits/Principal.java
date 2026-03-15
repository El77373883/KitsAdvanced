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

    // --- 1. MENÚ GLOBAL (EL HUB) ---
    public void abrirMenuPrincipal(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c("&8Menú Global de Kits"));
        ItemStack vidrio = createItem(Material.STAINED_GLASS_PANE, " ", false);
        for (int i = 0; i < 54; i++) inv.setItem(i, vidrio);

        inv.setItem(22, createItem(Material.CHEST, "&a&lKITS GRATUITOS", true, "&7Click para ver kits normales."));
        // Slot 49: El acceso a los kits VIP que pediste
        inv.setItem(49, createItem(Material.DIAMOND_BLOCK, "&b&lSECCIÓN PREMIUM", true, "&7Click para ver kits de prestigio.", "&7Solo para rangos especiales."));
        
        p.openInventory(inv);
    }

    // --- 2. LISTA DE KITS (FILTRADA) ---
    public void abrirListaKits(Player p, boolean premium) {
        Inventory inv = Bukkit.createInventory(null, 45, c(premium ? "&bExplorando Kits Premium" : "&aExplorando Kits Gratis"));
        
        File folder = new File(getDataFolder(), "kits");
        File[] archivos = folder.listFiles();
        
        if (archivos != null) {
            for (File f : archivos) {
                FileConfiguration cf = YamlConfiguration.loadConfiguration(f);
                if (cf.getBoolean("premium") == premium) {
                    String id = f.getName().replace(".yml", "");
                    Material mat = Material.valueOf(cf.getString("icono", premium ? "DIAMOND_SWORD" : "STONE_SWORD"));
                    
                    // Descripción Profesional por defecto
                    String desc = cf.getString("descripcion", premium ? "&7¡Equipo de Élite! Solo para campeones." : "&7¡Un regalo para nuestros guerreros!");
                    
                    inv.addItem(createItem(mat, "&e&l" + cf.getString("nombre_visual"), premium, 
                        desc, "&8-------------------------", "&eClick Izquierdo: &fVer contenido", "&bClick Derecho: &fObtener Kit"));
                }
            }
        }
        inv.setItem(40, createItem(Material.ARROW, "&c« Volver al Hub", false));
        p.openInventory(inv);
    }

    // --- 3. EDITOR DE ICONO (TU PETICIÓN DE SUBIR MANUAL) ---
    public void abrirEditorIcono(Player p, String id) {
        configurandoIcono.put(p.getUniqueId(), id);
        Inventory inv = Bukkit.createInventory(null, 27, c("&8Icono de: " + id));
        
        // Vidrios de relleno para que se vea profesional
        ItemStack vidrio = createItem(Material.STAINED_GLASS_PANE, " ", false);
        for (int i = 0; i < 27; i++) if(i != 13) inv.setItem(i, vidrio);

        inv.setItem(22, createItem(Material.EMERALD_BLOCK, "&a&lGUARDAR ICONO", true, "&7Pon el ítem en el centro y click."));
        p.openInventory(inv);
    }

    // --- 4. PANEL DE AJUSTES (LAS 10 OPCIONES) ---
    public void abrirEditorKit(Player p, String id) {
        editandoKit.put(p.getUniqueId(), id);
        FileConfiguration cf = getKitConfig(id);
        Inventory inv = Bukkit.createInventory(null, 54, c("&8Ajustes: " + id));

        // Relleno de vidrios
        ItemStack vidrio = createItem(Material.STAINED_GLASS_PANE, " ", false);
        for (int i = 0; i < 54; i++) if(i < 9 || i > 44 || i % 9 == 0 || (i+1) % 9 == 0) inv.setItem(i, vidrio);

        inv.setItem(10, createItem(Material.CHEST, "&61. Editar ítems", true));
        boolean perm = cf.getBoolean("permiso_status");
        inv.setItem(11, createItem(Material.REDSTONE, "&e2. Permiso", perm, "", perm ? "&a&lACTIVADO" : "&c&lDESACTIVADO"));
        inv.setItem(12, createItem(Material.SUNFLOWER, "&e3. Precio", cf.getDouble("precio") > 0, "&7$" + cf.getDouble("precio")));
        inv.setItem(13, createItem(Material.CLOCK, "&b4. Cooldown", cf.getLong("cooldown") > 0, "&7" + cf.getLong("cooldown") + "s"));
        boolean temp = cf.getBoolean("temporal");
        inv.setItem(14, createItem(Material.SOUL_SAND, "&35. Temporal", temp, "", temp ? "&a&lSÍ" : "&c&lNO"));
        
        // Mostrar icono actual
        Material iconMat = Material.valueOf(cf.getString("icono", cf.getBoolean("premium") ? "DIAMOND_SWORD" : "STONE_SWORD"));
        inv.setItem(15, createItem(iconMat, "&d6. Cambiar Icono", true, "&7Click para subir uno manual."));
        
        inv.setItem(16, createItem(Material.NAME_TAG, "&f7. Nombre Visual", true, "&7" + cf.getString("nombre_visual")));
        
        boolean prem = cf.getBoolean("premium");
        inv.setItem(19, createItem(prem ? Material.DIAMOND : Material.EMERALD, "&a8. CATEGORÍA", prem, 
            "", prem ? "&b&lESTADO: PREMIUM" : "&a&lESTADO: GRATIS", "&eClick para cambiar."));
        
        inv.setItem(20, createItem(Material.CHAINMAIL_CHESTPLATE, "&b9. Auto-Equipar", cf.getBoolean("auto_equipar")));
        inv.setItem(21, createItem(Material.BARRIER, "&c&l10. ELIMINAR", false));

        inv.setItem(49, createItem(Material.ARROW, "&c« Regresar", false));
        p.openInventory(inv);
    }

    @EventHandler
    public void alClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.getCurrentItem() == null) return;
        Player p = (Player) e.getWhoClicked();
        String t = e.getView().getTitle();

        // Manejo de Menús de Jugador
        if (t.contains("Explorando") || t.contains("Menú Global")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 22) abrirListaKits(p, false);
            if (e.getRawSlot() == 49 && t.contains("Menú Global")) abrirListaKits(p, true);
            if (e.getRawSlot() == 40) abrirMenuPrincipal(p);
            return;
        }

        // Guardar Icono Manual
        if (t.contains("Icono de:")) {
            if (e.getRawSlot() == 22) {
                e.setCancelled(true);
                String id = configurandoIcono.remove(p.getUniqueId());
                ItemStack itemManual = e.getInventory().getItem(13);
                if (itemManual != null) {
                    FileConfiguration cf = getKitConfig(id);
                    cf.set("icono", itemManual.getType().toString());
                    saveKit(new File(getDataFolder(), "kits/" + id + ".yml"), cf);
                }
                abrirEditorKit(p, id);
            }
            return;
        }

        // Panel de Ajustes
        if (t.contains("Ajustes:")) {
            e.setCancelled(true);
            String id = editandoKit.get(p.getUniqueId());
            File f = new File(getDataFolder(), "kits/" + id + ".yml");
            FileConfiguration cf = YamlConfiguration.loadConfiguration(f);

            switch (e.getRawSlot()) {
                case 11: cf.set("permiso_status", !cf.getBoolean("permiso_status")); break;
                case 15: abrirEditorIcono(p, id); return;
                case 19: 
                    boolean nP = !cf.getBoolean("premium");
                    cf.set("premium", nP);
                    cf.set("icono", nP ? "DIAMOND_SWORD" : "STONE_SWORD");
                    break;
                case 21: f.delete(); p.closeInventory(); return;
                case 49: abrirMenuPrincipal(p); return;
            }
            saveKit(f, cf);
            abrirEditorKit(p, id);
        }
    }

    private void saveKit(File f, FileConfiguration c) { try { c.save(f); } catch (IOException e) {} }
    private FileConfiguration getKitConfig(String k) { return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "kits/" + k + ".yml")); }

    private ItemStack createItem(Material m, String n, boolean glint, String... lore) {
        ItemStack i = new ItemStack(m);
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
                if (a.length > 0 && a[0].equalsIgnoreCase("admin")) abrirEditorKit(p, a[1]);
                else abrirMenuPrincipal(p);
            }
            return true;
        }
    }
}
