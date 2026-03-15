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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class Principal extends JavaPlugin implements Listener {
    private final Map<UUID, String> editandoKit = new HashMap<>();
    private final Map<UUID, String> modoChat = new HashMap<>();
    private final Map<UUID, Integer> paginaActual = new HashMap<>();

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdir();
        File carpetaKits = new File(getDataFolder(), "kits");
        if (!carpetaKits.exists()) carpetaKits.mkdirs();
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("akits").setExecutor(new KitsCommand());
    }

    private String c(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    public FileConfiguration getKitConfig(String n) {
        File f = new File(getDataFolder() + "/kits", n + ".yml");
        return f.exists() ? YamlConfiguration.loadConfiguration(f) : null;
    }

    public void guardarKit(String n, FileConfiguration conf) {
        try { conf.save(new File(getDataFolder() + "/kits", n + ".yml")); } catch (IOException e) { e.printStackTrace(); }
    }

    public class KitsCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender s, Command cmd, String l, String[] a) {
            if (!(s instanceof Player)) return true;
            Player p = (Player) s;
            if (a.length == 0) { abrirMenuKits(p); return true; }
            if (!p.hasPermission("kitsadvanced.admin")) return true;
            if (a[0].equalsIgnoreCase("panel")) { paginaActual.put(p.getUniqueId(), 0); abrirPanelAdmin(p); return true; }
            return true;
        }
    }

    // --- MENÚ PRINCIPAL ---
    public void abrirMenuKits(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c("&0&lMENÚ DE KITS"));
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i > 44 || i % 9 == 0 || (i + 1) % 9 == 0) 
                inv.setItem(i, createItem(Material.CYAN_STAINED_GLASS_PANE, " ", false));
        }
        inv.setItem(49, createItem(Material.DIAMOND_BLOCK, "&b&lINFO DE KITS", true, 
                "&7Los kits con brillo son &bPREMIUM&7.", "&7Los normales son &aGRATUITOS&7."));

        File[] archivos = new File(getDataFolder(), "kits").listFiles((dir, name) -> name.endsWith(".yml"));
        if (archivos != null) {
            for (File f : archivos) {
                String nombre = f.getName().replace(".yml", "");
                inv.addItem(createKitIcon(nombre));
            }
        }
        p.openInventory(inv);
    }

    private ItemStack createKitIcon(String k) {
        FileConfiguration conf = getKitConfig(k);
        Material icono = Material.valueOf(conf.getString("icono", "CHEST"));
        boolean pre = conf.getBoolean("requiere-permiso", false);
        return createItem(icono, "&e&lKit: &f" + k, pre, 
                "&7Precio: &a$" + conf.getDouble("precio"), 
                pre ? "&b[CATEGORIA PREMIUM]" : "&a[CATEGORIA GRATIS]",
                "&e▶ Click para reclamar");
    }

    // --- PANEL DE ADMIN ---
    public void abrirPanelAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c("&0Panel: Gestionar Kits"));
        File[] archivos = new File(getDataFolder(), "kits").listFiles((dir, name) -> name.endsWith(".yml"));
        int pg = paginaActual.getOrDefault(p.getUniqueId(), 0);

        if (archivos == null || archivos.length == 0) {
            inv.setItem(22, createItem(Material.CHEST, "&b&lCREAR TU PRIMER KIT", true, "&7No tienes kits aún.", "&e▶ Click para empezar"));
        } else {
            inv.setItem(0, createItem(Material.TRAPPED_CHEST, "&b&l[+] CREAR NUEVO KIT", true, "&7Haz click para añadir otro kit"));
            int start = pg * 44;
            for (int i = 0; i < 44 && (start + i) < archivos.length; i++) {
                String nombre = archivos[start + i].getName().replace(".yml", "");
                inv.setItem(i + 1, createItem(Material.PAPER, "&eEditar: &f" + nombre, false, "&7Configura este kit."));
            }
            if (archivos.length > (pg + 1) * 44) inv.setItem(53, createItem(Material.ARROW, "&aPágina Siguiente", false));
            if (pg > 0) inv.setItem(45, createItem(Material.ARROW, "&cPágina Anterior", false));
        }
        p.openInventory(inv);
    }

    // --- PANEL DE AJUSTES CON LAS 9 OPCIONES MEJORADAS ---
    public void abrirOpcionesKit(Player p, String k) {
        editandoKit.put(p.getUniqueId(), k);
        FileConfiguration conf = getKitConfig(k);
        Inventory inv = Bukkit.createInventory(null, 45, c("&0Ajustes: &8" + k));
        for (int i = 36; i < 45; i++) inv.setItem(i, createItem(Material.YELLOW_STAINED_GLASS_PANE, " ", false));

        boolean pre = conf.getBoolean("requiere-permiso", false);
        boolean cool = conf.getBoolean("cooldown-activado", false);
        boolean temp = conf.getBoolean("es-temporal", false);

        // 1. Ítems
        inv.setItem(10, createItem(Material.TRAPPED_CHEST, "&6&l1. Editar Ítems", false, 
                "&7Configura los objetos que da el kit.", "&a✔ Siempre activo"));
        
        // 2. Permiso Especial
        inv.setItem(11, createItem(Material.REDSTONE, "&e&l2. Permiso Especial", pre, 
                "&7Si se activa, requiere permiso para usar.", 
                pre ? "&a✔ Activado (Premium)" : "&c✘ Desactivado (Gratis)"));
        
        // 3. Precio
        inv.setItem(12, createItem(Material.SUNFLOWER, "&e&l3. Precio", false, 
                "&7Establece el costo de compra.", "&7Actual: &a$" + conf.getDouble("precio")));
        
        // 4. Cooldown
        inv.setItem(13, createItem(Material.CLOCK, "&b&l4. Cooldown", cool, 
                "&7Define un tiempo de espera para re-uso.", 
                cool ? "&a✔ Activado" : "&c✘ Desactivado"));
        
        // 5. Kit Temporal
        inv.setItem(14, createItem(Material.SOUL_SAND, "&b&l5. Kit Temporal", temp, 
                "&7Los items desaparecen tras el uso.", 
                temp ? "&a✔ Activado" : "&c✘ Desactivado"));

        // 6. Cambiar Icono
        inv.setItem(20, createItem(Material.PAINTING, "&d&l6. Cambiar Icono", false, 
                "&7Cambia la apariencia en el menú.", "&e▶ Click para elegir"));

        // 7. Modificar Nombre
        inv.setItem(21, createItem(Material.NAME_TAG, "&f&l7. Modificar Nombre", false, 
                "&7Cambia el nombre interno del kit.", "&7Actual: &f" + k));

        // 8. Tipo Rápido
        inv.setItem(22, createItem(Material.EMERALD, "&a&l8. Tipo: " + (pre ? "&dPREMIUM" : "&aGRATIS"), pre, 
                "&7Cambio rápido de categoría.", 
                pre ? "&a✔ Activado (Premium)" : "&c✘ Desactivado (Gratis)"));

        // 9. Eliminar
        inv.setItem(24, createItem(Material.BARRIER, "&4&l9. ELIMINAR KIT", false, 
                "&7Borra el kit para siempre.", "&c⚠ No se puede deshacer"));

        inv.setItem(40, createItem(Material.ARROW, "&c« Volver al Panel", false));
        p.openInventory(inv);
    }

    @EventHandler
    public void alHacerClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.getCurrentItem() == null) return;
        Player p = (Player) e.getWhoClicked();
        String t = ChatColor.stripColor(e.getView().getTitle());
        String k = editandoKit.get(p.getUniqueId());

        if (t.equals("MENÚ DE KITS")) {
            e.setCancelled(true);
            if (e.getCurrentItem().getType() != Material.CYAN_STAINED_GLASS_PANE && e.getRawSlot() != 49) {
                darKit(p, ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("Kit: ", ""));
            }
        } else if (t.equals("Panel: Gestionar Kits")) {
            e.setCancelled(true);
            if (e.getCurrentItem().getType() == Material.TRAPPED_CHEST) {
                p.closeInventory(); modoChat.put(p.getUniqueId(), "CREAR");
                p.sendMessage(c("&b&l[+] &f¿Nombre del kit?"));
            } else if (e.getCurrentItem().getType() == Material.PAPER) {
                abrirOpcionesKit(p, ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("Editar: ", ""));
            }
        } else if (t.startsWith("Ajustes:")) {
            e.setCancelled(true);
            FileConfiguration cf = getKitConfig(k);
            if (e.getRawSlot() == 11 || e.getRawSlot() == 22) { toggle(p, cf, "requiere-permiso", k); abrirOpcionesKit(p, k); }
            if (e.getRawSlot() == 13) { toggle(p, cf, "cooldown-activado", k); abrirOpcionesKit(p, k); }
            if (e.getRawSlot() == 14) { toggle(p, cf, "es-temporal", k); abrirOpcionesKit(p, k); }
            if (e.getRawSlot() == 10) abrirCofreEditor(p, k);
            if (e.getRawSlot() == 12) { p.closeInventory(); modoChat.put(p.getUniqueId(), "PRECIO"); }
            if (e.getRawSlot() == 24) { new File(getDataFolder() + "/kits", k + ".yml").delete(); abrirPanelAdmin(p); }
            if (e.getRawSlot() == 40) abrirPanelAdmin(p);
        } else if (t.startsWith("Items de: ")) {
            if (e.getRawSlot() >= 36) {
                e.setCancelled(true);
                if (e.getRawSlot() == 44) { abrirOpcionesKit(p, k); }
                else { guardarItemsEditor(e.getInventory(), k); abrirOpcionesKit(p, k); }
            }
        }
    }

    private void darKit(Player p, String k) {
        FileConfiguration cf = getKitConfig(k);
        if (cf == null) return;
        if (cf.getBoolean("requiere-permiso") && !p.hasPermission("advanced.kits." + k.toLowerCase())) {
            p.sendMessage(c("&c&l✘ &7No tienes permiso para este kit Premium.")); return;
        }
        for (Object o : cf.getList("items")) p.getInventory().addItem((ItemStack) o);
        p.sendMessage(c("&a&l✔ &fRecibiste el kit &e" + k));
    }

    @EventHandler
    public void alChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!modoChat.containsKey(p.getUniqueId())) return;
        e.setCancelled(true);
        String msg = e.getMessage();
        String k = editandoKit.get(p.getUniqueId());
        Bukkit.getScheduler().runTask(this, () -> {
            if (modoChat.get(p.getUniqueId()).equals("CREAR")) { crearKitBase(msg); abrirOpcionesKit(p, msg); }
            else if (modoChat.get(p.getUniqueId()).equals("PRECIO")) {
                FileConfiguration cf = getKitConfig(k);
                try { cf.set("precio", Double.parseDouble(msg)); guardarKit(k, cf); } catch (Exception ex) {}
                abrirOpcionesKit(p, k);
            }
            modoChat.remove(p.getUniqueId());
        });
    }

    private void toggle(Player p, FileConfiguration cf, String path, String k) {
        boolean v = !cf.getBoolean(path); cf.set(path, v); guardarKit(k, cf);
        p.playSound(p.getLocation(), v ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BASS, 1, 2);
    }

    private void crearKitBase(String n) {
        File f = new File(getDataFolder() + "/kits", n + ".yml");
        FileConfiguration c = YamlConfiguration.loadConfiguration(f);
        c.set("requiere-permiso", false); c.set("precio", 0.0);
        c.set("cooldown-activado", false); c.set("es-temporal", false);
        c.set("icono", "CHEST"); c.set("items", new ArrayList<ItemStack>());
        try { c.save(f); } catch (IOException e) {}
    }

    private void abrirCofreEditor(Player p, String k) {
        Inventory ed = Bukkit.createInventory(null, 45, c("&0Items de: " + k));
        List<?> items = getKitConfig(k).getList("items");
        if (items != null) for (Object i : items) ed.addItem((ItemStack) i);
        for (int i = 36; i < 44; i++) ed.setItem(i, createItem(Material.LIME_STAINED_GLASS_PANE, "&a&l✔ GUARDAR ÍTEMS", true));
        ed.setItem(44, createItem(Material.ARROW, "&c« Volver sin guardar", false));
        p.openInventory(ed);
    }

    private void guardarItemsEditor(Inventory inv, String k) {
        FileConfiguration cf = getKitConfig(k);
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) list.add(item);
        }
        cf.set("items", list); guardarKit(k, cf);
    }

    private ItemStack createItem(Material m, String n, boolean g, String... lore) {
        ItemStack i = new ItemStack(m); ItemMeta mt = i.getItemMeta();
        if (mt == null) return i;
        mt.setDisplayName(c(n)); List<String> l = new ArrayList<>();
        for (String s : lore) l.add(c(s)); mt.setLore(l);
        if (g) { mt.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 1, true); mt.addItemFlags(ItemFlag.HIDE_ENCHANTS); }
        i.setItemMeta(mt); return i;
    }
}
