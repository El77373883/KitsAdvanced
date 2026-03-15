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
    private final Map<UUID, Integer> paginaActual = new HashMap<>();

    @Override
    public void onEnable() {
        File f = new File(getDataFolder(), "kits");
        if (!f.exists()) f.mkdirs();
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("akits").setExecutor(new KitsCommand());
    }

    public String c(String m) { return ChatColor.translateAlternateColorCodes('&', m); }

    // --- 1. MENÚ DE CATEGORÍAS (NORMAL O PREMIUM) ---
    public void abrirMenuPrincipal(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, c("&8Menú de Kits"));
        inv.setItem(11, createItem(Material.CHEST, "&a&lKITS NORMALES", false, "&7Click para ver kits gratis."));
        inv.setItem(15, createItem(Material.DIAMOND, "&b&lKITS PREMIUM", true, "&7Click para ver kits VIP."));
        p.openInventory(inv);
    }

    // --- 2. LISTA DE KITS CON PAGINACIÓN Y VIDRIOS ---
    public void abrirListaKits(Player p, boolean premium, int pag) {
        paginaActual.put(p.getUniqueId(), pag);
        Inventory inv = Bukkit.createInventory(null, 54, c(premium ? "&bKits Premium" : "&aKits Normales"));
        
        ItemStack vidrio = createItem(Material.STAINED_GLASS_PANE, " ", false);
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i > 44 || i % 9 == 0 || (i + 1) % 9 == 0) inv.setItem(i, vidrio);
        }

        File folder = new File(getDataFolder(), "kits");
        File[] archivos = folder.listFiles();
        List<File> validos = new ArrayList<>();
        if (archivos != null) {
            for (File f : archivos) {
                if (YamlConfiguration.loadConfiguration(f).getBoolean("premium") == premium) validos.add(f);
            }
        }

        int inicio = pag * 28;
        int slot = 10;
        for (int i = inicio; i < inicio + 28 && i < validos.size(); i++) {
            if (slot == 17 || slot == 26 || slot == 35) slot += 2;
            FileConfiguration cf = YamlConfiguration.loadConfiguration(validos.get(i));
            String id = validos.get(i).getName().replace(".yml", "");
            inv.setItem(slot++, createItem(Material.CHEST, "&e&l" + cf.getString("nombre_visual"), false, 
                "&7ID: " + id, "&eClick Izquierdo: &fPREVIEW", "&bClick Derecho: &fCOMPRAR/RECLAMAR"));
        }

        if (validos.size() > inicio + 28) inv.setItem(53, createItem(Material.ARROW, "&aPágina Siguiente", false));
        if (pag > 0) inv.setItem(45, createItem(Material.ARROW, "&cPágina Anterior", false));
        inv.setItem(49, createItem(Material.ARROW, "&c« Volver", false));
        p.openInventory(inv);
    }

    // --- 3. PREVIEW DE KIT (VISTA PREVIA) ---
    public void abrirPreview(Player p, String id) {
        FileConfiguration cf = getKitConfig(id);
        Inventory inv = Bukkit.createInventory(null, 45, c("&8Vista Previa: &7" + id));
        List<?> items = cf.getList("items");
        if (items != null) {
            for (Object item : items) { if (item instanceof ItemStack) inv.addItem((ItemStack) item); }
        }
        inv.setItem(40, createItem(Material.ARROW, "&c« Volver", false));
        p.openInventory(inv);
    }

    // --- 4. PANEL DE AJUSTES (LAS 10 OPCIONES) ---
    public void abrirEditorKit(Player p, String id) {
        editandoKit.put(p.getUniqueId(), id);
        FileConfiguration cf = getKitConfig(id);
        Inventory inv = Bukkit.createInventory(null, 54, c("&8Ajustes: " + id));

        String status(boolean b) { return b ? "&a&lACTIVADO" : "&c&lDESACTIVADO"; }

        inv.setItem(10, createItem(Material.CHEST, "&61. Editar ítems", true, "&7Click para meter objetos."));
        inv.setItem(11, createItem(Material.REDSTONE, "&e2. Permiso", cf.getBoolean("permiso_status"), "&7Estado: " + status(cf.getBoolean("permiso_status"))));
        inv.setItem(12, createItem(Material.SUNFLOWER, "&e3. Precio", cf.getDouble("precio") > 0, "&7Costo: &6$" + cf.getDouble("precio"), "&eClick para cambiar."));
        inv.setItem(13, createItem(Material.CLOCK, "&b4. Cooldown", cf.getLong("cooldown") > 0, "&7Segundos: &3" + cf.getLong("cooldown"), "&eClick para cambiar."));
        inv.setItem(14, createItem(Material.SOUL_SAND, "&35. Temporal", cf.getBoolean("temporal"), "&7Estado: " + status(cf.getBoolean("temporal"))));
        inv.setItem(15, createItem(Material.ITEM_FRAME, "&d6. Icono", false, "&7Usa el ítem de tu mano."));
        inv.setItem(16, createItem(Material.NAME_TAG, "&f7. Nombre", true, "&7Actual: " + cf.getString("nombre_visual"), "&eClick para cambiar."));
        inv.setItem(19, createItem(Material.EMERALD, "&a8. PREMIUM", cf.getBoolean("premium"), "&7Estado: " + (cf.getBoolean("premium") ? "&bPREMIUM" : "&aGRATIS")));
        inv.setItem(20, createItem(Material.CHAINMAIL_CHESTPLATE, "&b9. Auto-Equipar", cf.getBoolean("auto_equipar"), "&7Estado: " + status(cf.getBoolean("auto_equipar"))));
        inv.setItem(21, createItem(Material.BARRIER, "&c&l10. ELIMINAR", false, "&4Borrado permanente."));
        
        inv.setItem(49, createItem(Material.ARROW, "&c« Volver", false));
        p.openInventory(inv);
    }

    // --- 5. MANEJO DE TODOS LOS CLICKS ---
    @EventHandler
    public void alClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.getCurrentItem() == null) return;
        Player p = (Player) e.getWhoClicked();
        String t = e.getView().getTitle();

        if (t.contains("Categorías") || t.contains("Kits") || t.contains("Vista Previa:")) {
            e.setCancelled(true);
            if (t.contains("Categorías")) {
                if (e.getRawSlot() == 11) abrirListaKits(p, false, 0);
                if (e.getRawSlot() == 15) abrirListaKits(p, true, 0);
            } else if (t.contains("Kits")) {
                if (e.getRawSlot() == 49) abrirMenuPrincipal(p);
                else if (e.getCurrentItem().getType() == Material.CHEST) {
                    String id = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getLore().get(0).replace("ID: ", ""));
                    if (e.getClick().isLeftClick()) abrirPreview(p, id);
                    else p.sendMessage(c("&aHas reclamado el kit " + id));
                }
            } else if (t.contains("Vista Previa:") && e.getRawSlot() == 40) abrirMenuPrincipal(p);
        }
        else if (t.contains("Ajustes:")) {
            e.setCancelled(true);
            String id = editandoKit.get(p.getUniqueId());
            File f = new File(getDataFolder(), "kits/" + id + ".yml");
            FileConfiguration cf = YamlConfiguration.loadConfiguration(f);

            switch (e.getRawSlot()) {
                case 11: cf.set("permiso_status", !cf.getBoolean("permiso_status")); break;
                case 12: p.closeInventory(); esperandoPrecio.put(p.getUniqueId(), id); p.sendMessage(c("&eEscribe el precio:")); return;
                case 16: p.closeInventory(); esperandoNombre.put(p.getUniqueId(), id); p.sendMessage(c("&eEscribe el nombre:")); return;
                case 19: cf.set("premium", !cf.getBoolean("premium")); break;
                case 20: cf.set("auto_equipar", !cf.getBoolean("auto_equipar")); break;
                case 21: f.delete(); p.closeInventory(); return;
                case 49: abrirMenuPrincipal(p); return;
            }
            saveKit(f, cf); abrirEditorKit(p, id);
        }
    }

    @EventHandler
    public void alChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (esperandoNombre.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
            String id = esperandoNombre.remove(p.getUniqueId());
            FileConfiguration cf = getKitConfig(id);
            cf.set("nombre_visual", e.getMessage());
            saveKit(new File(getDataFolder(), "kits/" + id + ".yml"), cf);
            Bukkit.getScheduler().runTask(this, () -> abrirEditorKit(p, id));
        }
    }

    public class KitsCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender s, Command cmd, String l, String[] a) {
            if (!(s instanceof Player)) return true;
            Player p = (Player) s;
            if (a.length > 0 && a[0].equalsIgnoreCase("admin")) { abrirEditorKit(p, a[1]); return true; }
            abrirMenuPrincipal(p);
            return true;
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
}
