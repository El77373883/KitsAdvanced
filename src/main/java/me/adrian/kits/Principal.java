package me.adrian.kits;

import net.milkbowl.vault.economy.Economy;
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
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class Principal extends JavaPlugin implements Listener {
    private static Economy econ = null;
    private final Map<UUID, String> editandoKit = new HashMap<>();
    private final Map<UUID, String> modoChat = new HashMap<>();
    private final Map<UUID, String> categoriaActual = new HashMap<>();
    private final Map<String, Map<UUID, Long>> cooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("¡Vault no encontrado! La economía ha sido desactivada.");
        }
        saveDefaultConfig(); 
        File carpetaKits = new File(getDataFolder(), "kits");
        if (!carpetaKits.exists()) carpetaKits.mkdirs();
        
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("akits").setExecutor(new KitsCommand());
        getLogger().info("-------------------------------");
        getLogger().info(" KitsAdvanced v3.0 ACTIVADO ");
        getLogger().info("-------------------------------");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
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
            if (a.length > 0 && a[0].equalsIgnoreCase("panel") && p.hasPermission("kitsadvanced.admin")) {
                abrirPanelAdmin(p); return true;
            }
            categoriaActual.putIfAbsent(p.getUniqueId(), "GRATIS");
            abrirMenuKits(p); 
            return true;
        }
    }

    // --- MENÚS ---
    public void abrirMenuKits(Player p) {
        String cat = categoriaActual.getOrDefault(p.getUniqueId(), "GRATIS");
        Inventory inv = Bukkit.createInventory(null, 54, c("&8&lKITS &7- " + (cat.equals("GRATIS") ? "&fGRATIS" : "&bPREMIUM")));
        
        ItemStack vidrio = createItem(cat.equals("GRATIS") ? Material.WHITE_STAINED_GLASS_PANE : Material.PURPLE_STAINED_GLASS_PANE, " ", false);
        for (int i = 0; i < 54; i++) if (i < 9 || i > 44 || i % 9 == 0 || (i + 1) % 9 == 0) inv.setItem(i, vidrio);

        inv.setItem(49, createItem(Material.NETHER_STAR, "&e&lCAMBIAR CATEGORÍA", true, "&7Clic para alternar entre Gratis/Premium"));

        File folder = new File(getDataFolder(), "kits");
        File[] archivos = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (archivos != null) {
            for (File f : archivos) {
                String nombre = f.getName().replace(".yml", "");
                FileConfiguration conf = getKitConfig(nombre);
                if (conf != null && conf.getBoolean("requiere-permiso") == cat.equals("PREMIUM")) {
                    inv.addItem(createItem(Material.getMaterial(conf.getString("icono", "CHEST")), "&a&lKit: &f" + nombre, false, "&7Precio: &a$" + conf.getDouble("precio")));
                }
            }
        }
        p.openInventory(inv);
    }

    public void abrirPanelAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c("&0&lADMIN &8- Panel"));
        inv.setItem(0, createItem(Material.NETHER_STAR, "&b&l[+] 1. CREAR NUEVO KIT", true));
        File folder = new File(getDataFolder(), "kits");
        File[] archivos = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (archivos != null) {
            int slot = 1;
            for (File f : archivos) {
                if (slot > 44) break;
                inv.setItem(slot++, createItem(Material.PAPER, "&e&lEDITAR: &f" + f.getName().replace(".yml", ""), false));
            }
        }
        p.openInventory(inv);
    }

    // --- LAS 10 OPCIONES DE AJUSTE ---
    public void abrirOpcionesKit(Player p, String k) {
        editandoKit.put(p.getUniqueId(), k);
        FileConfiguration cf = getKitConfig(k);
        Inventory inv = Bukkit.createInventory(null, 54, c("&0&lAJUSTES: &8" + k));

        // Fondo decorativo
        for (int i = 0; i < 54; i++) inv.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, " ", false));

        inv.setItem(10, createItem(Material.CHEST, "&6&l1. EDITAR ITEMS", false, "&7Añade o quita objetos del kit."));
        inv.setItem(11, createItem(Material.SUNFLOWER, "&e&l2. PRECIO", true, "&7Actual: &a$" + cf.getDouble("precio")));
        inv.setItem(12, createItem(Material.CLOCK, "&b&l3. COOLDOWN", false, "&7Actual: &f" + cf.getInt("cooldown") + "s"));
        inv.setItem(13, createItem(Material.REDSTONE, "&d&l4. TIPO (VIP/GRATIS)", cf.getBoolean("requiere-permiso"), "&7Estado: " + (cf.getBoolean("requiere-permiso") ? "&bPREMIUM" : "&aGRATIS")));
        inv.setItem(14, createItem(Material.IRON_CHESTPLATE, "&f&l5. AUTO-EQUIPAR", cf.getBoolean("auto-equip"), "&7¿Equipar armadura al recibir?"));
        inv.setItem(15, createItem(Material.ITEM_FRAME, "&6&l6. CAMBIAR ICONO", false, "&7Usa el item de tu mano."));
        inv.setItem(16, createItem(Material.NAME_TAG, "&3&l7. RENOMBRAR", false, "&7Cambia el nombre del kit."));
        inv.setItem(19, createItem(Material.COMMAND_BLOCK, "&5&l8. COMANDO CONSOLA", false, "&7Ejecutar al reclamar el kit."));
        inv.setItem(20, createItem(Material.ENCHANTED_BOOK, "&b&l9. PERMISO CUSTOM", false, "&7Asignar permiso específico."));
        inv.setItem(21, createItem(Material.BARRIER, "&4&l10. ELIMINAR KIT", false, "&cBorrar el archivo .yml"));

        inv.setItem(49, createItem(Material.ARROW, "&c« VOLVER", false));
        p.openInventory(inv);
    }

    @EventHandler
    public void alHacerClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.getCurrentItem() == null) return;
        Player p = (Player) e.getWhoClicked();
        String t = ChatColor.stripColor(e.getView().getTitle());
        String k = editandoKit.get(p.getUniqueId());

        if (t.startsWith("AJUSTES:")) {
            e.setCancelled(true);
            FileConfiguration cf = getKitConfig(k);
            switch (e.getRawSlot()) {
                case 10: abrirCofreEditor(p, k); break;
                case 11: p.closeInventory(); modoChat.put(p.getUniqueId(), "PRECIO"); p.sendMessage(c("&e&l2. &fEscribe el precio:")); break;
                case 12: p.closeInventory(); modoChat.put(p.getUniqueId(), "COOLDOWN"); p.sendMessage(c("&b&l3. &fEscribe los segundos:")); break;
                case 13: cf.set("requiere-permiso", !cf.getBoolean("requiere-permiso")); guardarKit(k, cf); abrirOpcionesKit(p, k); break;
                case 14: cf.set("auto-equip", !cf.getBoolean("auto-equip")); guardarKit(k, cf); abrirOpcionesKit(p, k); break;
                case 15: 
                    Material m = p.getInventory().getItemInMainHand().getType();
                    if (m != Material.AIR) { cf.set("icono", m.name()); guardarKit(k, cf); p.sendMessage(c("&aIcono actualizado.")); }
                    abrirOpcionesKit(p, k); break;
                case 21: new File(getDataFolder() + "/kits", k + ".yml").delete(); abrirPanelAdmin(p); break;
                case 49: abrirPanelAdmin(p); break;
            }
        } else if (t.equals("ADMIN - Panel")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 0) { p.closeInventory(); modoChat.put(p.getUniqueId(), "CREAR"); p.sendMessage(c("&bEscribe el nombre:")); }
            else if (e.getCurrentItem().getType() == Material.PAPER) abrirOpcionesKit(p, ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("EDITAR: ", ""));
        } else if (t.contains("KITS -")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 49) { categoriaActual.put(p.getUniqueId(), categoriaActual.get(p.getUniqueId()).equals("GRATIS") ? "PREMIUM" : "GRATIS"); abrirMenuKits(p); }
            else { darKit(p, ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("Kit: ", "")); }
        }
    }

    private void darKit(Player p, String k) {
        FileConfiguration cf = getKitConfig(k);
        if (cf == null) return;
        double precio = cf.getDouble("precio");
        if (precio > 0 && econ != null && econ.getBalance(p) < precio) { p.sendMessage(c("&cNo tienes dinero.")); return; }
        if (precio > 0) econ.withdrawPlayer(p, precio);
        
        List<?> items = cf.getList("items");
        if (items != null) {
            for (Object o : items) p.getInventory().addItem((ItemStack) o);
            p.sendMessage(c("&aRecibiste el kit " + k));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
        }
    }

    @EventHandler
    public void alChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!modoChat.containsKey(p.getUniqueId())) return;
        e.setCancelled(true);
        String msg = e.getMessage();
        Bukkit.getScheduler().runTask(this, () -> {
            String modo = modoChat.get(p.getUniqueId());
            String k = editandoKit.get(p.getUniqueId());
            if (modo.equals("CREAR")) { crearKitBase(msg); abrirOpcionesKit(p, msg); }
            else {
                FileConfiguration cf = getKitConfig(k);
                try {
                    if (modo.equals("PRECIO")) cf.set("precio", Double.parseDouble(msg));
                    if (modo.equals("COOLDOWN")) cf.set("cooldown", Integer.parseInt(msg));
                    guardarKit(k, cf);
                    p.sendMessage(c("&a&l✔ &7Dato actualizado."));
                } catch (Exception ex) { p.sendMessage(c("&cValor inválido.")); }
                abrirOpcionesKit(p, k);
            }
            modoChat.remove(p.getUniqueId());
        });
    }

    private void crearKitBase(String n) {
        File f = new File(getDataFolder() + "/kits", n + ".yml");
        FileConfiguration c = YamlConfiguration.loadConfiguration(f);
        c.set("precio", 0.0); c.set("cooldown", 0); c.set("requiere-permiso", false);
        c.set("icono", "CHEST"); c.set("auto-equip", false);
        c.set("items", new ArrayList<ItemStack>());
        try { c.save(f); } catch (IOException e) { e.printStackTrace(); }
    }

    private void abrirCofreEditor(Player p, String k) {
        Inventory ed = Bukkit.createInventory(null, 45, c("&0Items de: " + k));
        FileConfiguration cf = getKitConfig(k);
        List<?> items = cf.getList("items");
        if (items != null) for (Object i : items) if (i instanceof ItemStack) ed.addItem((ItemStack) i);
        for (int i = 36; i < 44; i++) ed.setItem(i, createItem(Material.LIME_STAINED_GLASS_PANE, "&a&lGUARDAR", true));
        ed.setItem(44, createItem(Material.ARROW, "&cVOLVER", false));
        p.openInventory(ed);
    }

    private ItemStack createItem(Material m, String n, boolean g, String... lore) {
        ItemStack i = new ItemStack(m); ItemMeta mt = i.getItemMeta();
        mt.setDisplayName(c(n)); List<String> l = new ArrayList<>();
        for (String s : lore) l.add(c(s)); mt.setLore(l);
        if (g) { mt.addEnchant(Enchantment.PROTECTION, 1, true); mt.addItemFlags(ItemFlag.HIDE_ENCHANTS); }
        i.setItemMeta(mt); return i;
    }
}
