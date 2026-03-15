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
import org.bukkit.enchantments.Enchantment; // Importación necesaria corregida
import java.io.File;
import java.io.IOException;
import java.util.*;

public class Principal extends JavaPlugin implements Listener {

    private final Map<UUID, String> editandoKit = new HashMap<>();
    private final Map<UUID, String> editandoItems = new HashMap<>();
    private final Map<UUID, String> esperandoNombre = new HashMap<>();
    private final Map<UUID, Boolean> viendoPremium = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getConsoleSender().sendMessage(c("&b&lAdvancedKits &8» &aIniciando sistema..."));
        Bukkit.getConsoleSender().sendMessage(c("&bAutor: &fAdrianYTOFFICIAL"));

        saveDefaultConfig();
        File f = new File(getDataFolder(), "kits");
        if (!f.exists()) f.mkdirs();

        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("akits").setExecutor(new KitsCommand());
    }

    public String c(String m) {
        return ChatColor.translateAlternateColorCodes('&', m);
    }

    // --- PANEL DE AJUSTES (10 OPCIONES) ---
    public void abrirEditorKit(Player p, String kitId) {
        editandoKit.put(p.getUniqueId(), kitId);
        FileConfiguration config = getKitConfig(kitId);
        String nombreVisual = config.getString("nombre_visual", kitId);
        
        Inventory inv = Bukkit.createInventory(null, 54, c("&8Ajustes: &7" + kitId));

        inv.setItem(10, createItem(Material.CHEST, "&61. Editar ítems", false, "&7Mete los objetos del kit aquí."));
        inv.setItem(11, createItem(Material.REDSTONE, "&e2. Permiso Requerido", config.getBoolean("permiso_status"), "&7Click para cambiar."));
        inv.setItem(12, createItem(Material.SUNFLOWER, "&e3. Precio", config.getDouble("precio") > 0, "&7Costo del kit."));
        inv.setItem(13, createItem(Material.CLOCK, "&b4. Cooldown", config.getLong("cooldown") > 0, "&7Tiempo de espera."));
        inv.setItem(14, createItem(Material.SOUL_SAND, "&35. Kit Temporal", config.getBoolean("temporal"), "&7Estado temporal."));
        inv.setItem(15, createItem(Material.ITEM_FRAME, "&d6. Cambiar Icono", false, "&7Usa el ítem de tu mano."));
        inv.setItem(16, createItem(Material.NAME_TAG, "&f7. Modificar Nombre", false, "&7Actual: &f" + nombreVisual, "&eClick para cambiar por chat."));
        inv.setItem(19, createItem(Material.EMERALD, "&a8. PREMIUM", config.getBoolean("premium"), "&7Sección del menú."));
        inv.setItem(20, createItem(Material.CHAINMAIL_CHESTPLATE, "&b9. Auto-Equipar", config.getBoolean("auto_equipar"), "&7¿Poner al reclamar?"));
        inv.setItem(21, createItem(Material.BARRIER, "&c&l10. ELIMINAR KIT", true, "&4¡Borrado permanente!"));

        inv.setItem(49, createItem(Material.ARROW, "&c« Volver al Panel", false));
        p.openInventory(inv);
    }

    // --- EDITOR DE CONTENIDO ---
    public void abrirEditorItems(Player p, String kit) {
        editandoItems.put(p.getUniqueId(), kit);
        Inventory inv = Bukkit.createInventory(null, 54, c("&8Contenido: &7" + kit));
        FileConfiguration config = getKitConfig(kit);
        List<?> items = config.getList("items");
        if (items != null) {
            for (Object item : items) { if (item instanceof ItemStack) inv.addItem((ItemStack) item); }
        }
        inv.setItem(49, createItem(Material.ARROW, "&a&l✔ GUARDAR Y REGRESAR", true, "&7Click para volver."));
        p.openInventory(inv);
    }

    @EventHandler
    public void alClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.getCurrentItem() == null) return;
        Player p = (Player) e.getWhoClicked();
        String t = e.getView().getTitle();

        if (t.contains("Ajustes:")) {
            e.setCancelled(true);
            String kit = editandoKit.get(p.getUniqueId());
            File f = new File(getDataFolder(), "kits/" + kit + ".yml");
            FileConfiguration cf = YamlConfiguration.loadConfiguration(f);

            if (e.getRawSlot() == 10) { p.closeInventory(); abrirEditorItems(p, kit); }
            else if (e.getRawSlot() == 11) { cf.set("permiso_status", !cf.getBoolean("permiso_status")); saveKit(f, cf); abrirEditorKit(p, kit); }
            else if (e.getRawSlot() == 16) { 
                p.closeInventory(); 
                esperandoNombre.put(p.getUniqueId(), kit); 
                p.sendMessage(c("&b&lKits &8» &fEscribe el nuevo nombre en el chat:")); 
            }
            else if (e.getRawSlot() == 19) { cf.set("premium", !cf.getBoolean("premium")); saveKit(f, cf); abrirEditorKit(p, kit); }
            else if (e.getRawSlot() == 20) { cf.set("auto_equipar", !cf.getBoolean("auto_equipar")); saveKit(f, cf); abrirEditorKit(p, kit); }
            else if (e.getRawSlot() == 21) { f.delete(); p.sendMessage(c("&cKit eliminado.")); p.performCommand("akits panel"); }
            else if (e.getRawSlot() == 49) { p.performCommand("akits panel"); }
        } 
        else if (t.contains("Contenido:")) {
            if (e.getRawSlot() == 49) { e.setCancelled(true); p.closeInventory(); }
        }
    }

    @EventHandler
    public void alCerrar(InventoryCloseEvent e) {
        if (e.getView().getTitle().contains("Contenido:")) {
            Player p = (Player) e.getPlayer();
            String kit = editandoItems.get(p.getUniqueId());
            if (kit == null) return;
            File f = new File(getDataFolder(), "kits/" + kit + ".yml");
            FileConfiguration cf = YamlConfiguration.loadConfiguration(f);
            List<ItemStack> content = new ArrayList<>();
            for (int i = 0; i < 48; i++) {
                ItemStack item = e.getInventory().getItem(i);
                if (item != null) content.add(item);
            }
            cf.set("items", content);
            saveKit(f, cf);
            editandoItems.remove(p.getUniqueId());
            Bukkit.getScheduler().runTaskLater(this, () -> abrirEditorKit(p, kit), 2L);
        }
    }

    @EventHandler
    public void alEscribir(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (esperandoNombre.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
            String kit = esperandoNombre.get(p.getUniqueId());
            File f = new File(getDataFolder(), "kits/" + kit + ".yml");
            FileConfiguration cf = YamlConfiguration.loadConfiguration(f);
            cf.set("nombre_visual", e.getMessage());
            saveKit(f, cf);
            esperandoNombre.remove(p.getUniqueId());
            p.sendMessage(c("&aNombre actualizado."));
            Bukkit.getScheduler().runTask(this, () -> abrirEditorKit(p, kit));
        }
    }

    // --- MODO DE CREACIÓN Y COMANDO ---
    private void crearEstructura(String n) {
        File f = new File(getDataFolder() + "/kits", n + ".yml");
        FileConfiguration c = YamlConfiguration.loadConfiguration(f);
        c.set("nombre_visual", n);
        c.set("premium", false);
        c.set("items", new ArrayList<>());
        saveKit(f, c);
    }

    public class KitsCommand implements CommandExecutor {
        @Override public boolean onCommand(CommandSender s, Command cmd, String l, String[] a) {
            if (!(s instanceof Player)) return true;
            Player p = (Player) s;
            if (a.length > 0 && a[0].equalsIgnoreCase("panel")) abrirPanelAdmin(p);
            else if (a.length > 1 && a[0].equalsIgnoreCase("create")) {
                crearEstructura(a[1]); p.sendMessage(c("&aKit " + a[1] + " creado."));
            }
            return true;
        }
    }

    public void abrirPanelAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c("&8Panel: &7Gestionar Kits"));
        inv.setItem(0, createItem(Material.TRAPPED_CHEST, "&b&l[+] CREAR KIT", true));
        File[] archivos = new File(getDataFolder(), "kits").listFiles();
        if (archivos != null) {
            int slot = 1;
            for (File f : archivos) {
                if (slot > 53) break;
                inv.setItem(slot++, createItem(Material.PAPER, "&eEditar: &f" + f.getName().replace(".yml", ""), false));
            }
        }
        p.openInventory(inv);
    }

    private void saveKit(File f, FileConfiguration c) { try { c.save(f); } catch (IOException e) {} }
    private FileConfiguration getKitConfig(String k) { return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "kits/" + k + ".yml")); }

    // --- MÉTODO CORREGIDO (SIN TYPOS) ---
    private ItemStack createItem(Material m, String n, boolean enc, String... lore) {
        ItemStack i = new ItemStack(m); 
        ItemMeta mt = i.getItemMeta();
        if (mt != null) {
            mt.setDisplayName(c(n));
            List<String> l = new ArrayList<>();
            for (String s : lore) l.add(c(s));
            mt.setLore(l);
            if (enc) { 
                mt.addEnchant(Enchantment.ARROW_INFINITE, 1, true); 
                mt.addItemFlags(ItemFlag.HIDE_ENCHANTS); 
            }
            i.setItemMeta(mt);
        }
        return i;
    }
}
