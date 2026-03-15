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
    private final Map<UUID, String> editandoItems = new HashMap<>();
    private final Map<UUID, String> esperandoNombre = new HashMap<>();
    private final Map<UUID, Boolean> viendoPremium = new HashMap<>();

    @Override
    public void onEnable() {
        // --- MENSAJE PROFESIONAL DE INICIO ---
        Bukkit.getConsoleSender().sendMessage(c(" "));
        Bukkit.getConsoleSender().sendMessage(c("&b&lAdvancedKits &8» &aIniciando sistema profesional..."));
        Bukkit.getConsoleSender().sendMessage(c("&bAutor: &fAdrianYTOFFICIAL"));
        Bukkit.getConsoleSender().sendMessage(c(" "));

        saveDefaultConfig();
        File f = new File(getDataFolder(), "kits");
        if (!f.exists()) f.mkdirs();

        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("akits").setExecutor(new KitsCommand());
    }

    public String c(String m) {
        return ChatColor.translateAlternateColorCodes('&', m);
    }

    // --- PANEL DE LAS 10 HERRAMIENTAS ---
    public void abrirEditorKit(Player p, String kitId) {
        editandoKit.put(p.getUniqueId(), kitId);
        FileConfiguration config = getKitConfig(kitId);
        String nombreVisual = config.getString("nombre_visual", kitId);
        
        Inventory inv = Bukkit.createInventory(null, 54, c("&8Ajustes: &7" + kitId));

        // 1. Items
        inv.setItem(10, createItem(Material.CHEST, "&61. Editar ítems", false, "&7Mete los objetos del kit aquí."));
        // 2. Permiso
        boolean pAct = config.getBoolean("permiso_status", false);
        inv.setItem(11, createItem(Material.REDSTONE, "&e2. Permiso Requerido", pAct, pAct ? "&a✔ Activado" : "&c✘ Desactivado"));
        // 3. Precio
        double precio = config.getDouble("precio", 0.0);
        inv.setItem(12, createItem(Material.SUNFLOWER, "&e3. Precio", precio > 0, precio > 0 ? "&a✔ Costo: $" + precio : "&c✘ Gratis"));
        // 4. Cooldown
        long cd = config.getLong("cooldown", 0);
        inv.setItem(13, createItem(Material.CLOCK, "&b4. Cooldown", cd > 0, cd > 0 ? "&a✔ Tiempo: " + cd + "s" : "&c✘ Sin espera"));
        // 5. Temporal
        boolean temp = config.getBoolean("temporal", false);
        inv.setItem(14, createItem(Material.SOUL_SAND, "&35. Kit Temporal", temp, temp ? "&a✔ Activado" : "&c✘ Permanente"));
        // 6. Icono
        inv.setItem(15, createItem(Material.ITEM_FRAME, "&d6. Cambiar Icono", false, "&7Usa el ítem de tu mano."));
        // 7. NOMBRE (POR CHAT)
        inv.setItem(16, createItem(Material.NAME_TAG, "&f7. Modificar Nombre", false, "&7Nombre actual: &f" + nombreVisual, "&eClick para cambiar por chat."));
        // 8. Premium
        boolean pre = config.getBoolean("premium", false);
        inv.setItem(19, createItem(Material.EMERALD, "&a8. PREMIUM", pre, pre ? "&a✔ Secc. Diamante" : "&c✘ Secc. Madera"));
        // 9. Auto-Equipar
        boolean ae = config.getBoolean("auto_equipar", false);
        inv.setItem(20, createItem(Material.CHAINMAIL_CHESTPLATE, "&b9. Auto-Equipar", ae, ae ? "&a✔ Activado" : "&c✘ Desactivado"));
        // 10. Eliminar
        inv.setItem(21, createItem(Material.BARRIER, "&c&l10. ELIMINAR KIT", true, "&4¡Borrado permanente!"));

        inv.setItem(49, createItem(Material.ARROW, "&c« Volver al Panel", false));
        p.openInventory(inv);
    }

    // --- EDITOR DE ITEMS (CON FLECHA DE RETORNO) ---
    public void abrirEditorItems(Player p, String kit) {
        editandoItems.put(p.getUniqueId(), kit);
        Inventory inv = Bukkit.createInventory(null, 54, c("&8Contenido: &7" + kit));
        FileConfiguration config = getKitConfig(kit);
        List<?> items = config.getList("items");
        if (items != null) {
            for (Object item : items) { if (item instanceof ItemStack) inv.addItem((ItemStack) item); }
        }
        inv.setItem(49, createItem(Material.ARROW, "&a&l✔ GUARDAR Y REGRESAR", true, "&7Click para volver a ajustes."));
        p.openInventory(inv);
    }

    // --- EVENTOS DE INVENTARIO ---
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
            else if (e.getRawSlot() == 16) { // CAMBIAR NOMBRE
                p.closeInventory();
                esperandoNombre.put(p.getUniqueId(), kit);
                p.sendMessage(c("&b&lAdvancedKits &8» &fEscribe el nuevo &eNombre &f(Usa &):"));
            }
            else if (e.getRawSlot() == 19) { cf.set("premium", !cf.getBoolean("premium")); saveKit(f, cf); abrirEditorKit(p, kit); }
            else if (e.getRawSlot() == 20) { cf.set("auto_equipar", !cf.getBoolean("auto_equipar")); saveKit(f, cf); abrirEditorKit(p, kit); }
            else if (e.getRawSlot() == 21) { f.delete(); p.sendMessage(c("&cKit eliminado.")); p.performCommand("akits panel"); }
            else if (e.getRawSlot() == 49) { p.performCommand("akits panel"); }
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
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

    // --- CAPTURA DE NOMBRE POR CHAT ---
    @EventHandler
    public void alEscribir(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (esperandoNombre.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
            String kit = esperandoNombre.get(p.getUniqueId());
            String nuevoNombre = e.getMessage();
            
            File f = new File(getDataFolder(), "kits/" + kit + ".yml");
            FileConfiguration cf = YamlConfiguration.loadConfiguration(f);
            cf.set("nombre_visual", nuevoNombre);
            saveKit(f, cf);

            esperandoNombre.remove(p.getUniqueId());
            p.sendMessage(c("&a✔ Nombre cambiado a: " + nuevoNombre));
            Bukkit.getScheduler().runTask(this, () -> abrirEditorKit(p, kit));
        }
    }

    // --- COMANDOS Y PANEL ADMIN ---
    public void abrirPanelAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c("&8Panel: &7Gestionar Kits"));
        inv.setItem(0, createItem(Material.TRAPPED_CHEST, "&b&l[+] CREAR KIT", true, "&7Click para crear."));
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

    private void crearEstructura(String n) {
        File f = new File(getDataFolder() + "/kits", n + ".yml");
        FileConfiguration c = YamlConfiguration.loadConfiguration(f);
        c.set("nombre_visual", n);
        c.set("premium", false);
        c.set("items", new ArrayList<>());
        saveKit(f, c);
    }

    private void saveKit(File f, FileConfiguration c) { try { c.save(f); } catch (IOException e) {} }
    private FileConfiguration getKitConfig(String k) { return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "kits/" + k + ".yml")); }
    
    private ItemStack createItem(Material m, String n, boolean enc, String... lore) {
        ItemStack i = new ItemStack(m); ItemMeta mt = i.getItemMeta();
        mt.setDisplayName(c(n));
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(c(s));
        mt.setLore(l);
        if (enc) { mt.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true); mt.addItemFlags(ItemFlag.HIDE_CHENCHANTS); }
        i.setItemMeta(mt); return i;
    }
}
