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

    public void abrirMenuKits(Player p) {
        String cat = categoriaActual.getOrDefault(p.getUniqueId(), "GRATIS");
        Inventory inv = Bukkit.createInventory(null, 54, c("&8&lKITS &7- " + (cat.equals("GRATIS") ? "&fGRATIS" : "&bPREMIUM")));
        
        ItemStack vidrio = createItem(cat.equals("GRATIS") ? Material.WHITE_STAINED_GLASS_PANE : Material.PURPLE_STAINED_GLASS_PANE, " ", false);
        for (int i = 0; i < 54; i++) if (i < 9 || i > 44 || i % 9 == 0 || (i + 1) % 9 == 0) inv.setItem(i, vidrio);

        inv.setItem(49, createItem(Material.NETHER_STAR, "&e&lCAMBIAR CATEGORÍA", true, "&7Clic para alternar"));

        File folder = new File(getDataFolder(), "kits");
        File[] archivos = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (archivos != null) {
            for (File f : archivos) {
                String nombre = f.getName().replace(".yml", "");
                FileConfiguration conf = getKitConfig(nombre);
                if (conf != null && conf.getBoolean("requiere-permiso") == cat.equals("PREMIUM")) {
                    inv.addItem(createItem(Material.getMaterial(conf.getString("icono", "STONE_SWORD")), "&a&lKit: &f" + nombre, false, "&7Precio: &a$" + conf.getDouble("precio")));
                }
            }
        }
        p.openInventory(inv);
    }

    public void abrirPanelAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c("&0&lADMIN &8- Panel"));
        inv.setItem(0, createItem(Material.NETHER_STAR, "&b&l[+] CREAR NUEVO KIT", true));
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

    public void abrirOpcionesKit(Player p, String k) {
        editandoKit.put(p.getUniqueId(), k);
        FileConfiguration cf = getKitConfig(k);
        Inventory inv = Bukkit.createInventory(null, 54, c("&0&lAJUSTES: &8" + k));

        for (int i = 0; i < 54; i++) inv.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, " ", false));

        boolean esPremium = cf.getBoolean("requiere-permiso");

        inv.setItem(10, createItem(Material.CHEST, "&6&lEDITAR ITEMS", false));
        inv.setItem(11, createItem(Material.SUNFLOWER, "&e&lPRECIO", true, "&7Actual: &a$" + cf.getDouble("precio")));
        inv.setItem(12, createItem(Material.CLOCK, "&b&lCOOLDOWN", false, "&7Actual: &f" + cf.getInt("cooldown") + "s"));
        inv.setItem(13, createItem(Material.REDSTONE, "&d&lTIPO", esPremium, "&7Estado: " + (esPremium ? "&bPREMIUM" : "&aGRATIS")));
        inv.setItem(14, createItem(Material.IRON_CHESTPLATE, "&f&lAUTO-EQUIPAR", cf.getBoolean("auto-equip"), "&7Estado: " + (cf.getBoolean("auto-equip") ? "&aON" : "&cOFF")));
        inv.setItem(15, createItem(Material.ITEM_FRAME, "&6&lCAMBIAR ICONO", false, "&7Usa el item de tu mano."));
        inv.setItem(16, createItem(Material.NAME_TAG, "&3&lRENOMBRAR", false));
        inv.setItem(19, createItem(Material.COMMAND_BLOCK, "&5&lCOMANDO CONSOLA", false));
        inv.setItem(20, createItem(Material.ENCHANTED_BOOK, "&b&lPERMISO CUSTOM", false));
        inv.setItem(21, createItem(Material.BARRIER, "&4&lELIMINAR KIT", false));

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
                case 11: p.closeInventory(); modoChat.put(p.getUniqueId(), "PRECIO"); p.sendMessage(c("&eEscribe el nuevo precio:")); break;
                case 12: p.closeInventory(); modoChat.put(p.getUniqueId(), "COOLDOWN"); p.sendMessage(c("&bEscribe el cooldown:")); break;
                case 13: 
                    boolean nuevoEstado = !cf.getBoolean("requiere-permiso");
                    cf.set("requiere-permiso", nuevoEstado);
                    cf.set("icono", nuevoEstado ? "DIAMOND_SWORD" : "STONE_SWORD");
                    guardarKit(k, cf); abrirOpcionesKit(p, k); 
                    break;
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
            if (e.getRawSlot() == 0) { p.closeInventory(); modoChat.put(p.getUniqueId(), "CREAR"); p.sendMessage(c("&bEscribe el nombre del kit:")); }
            else if (e.getCurrentItem().getType() == Material.PAPER) abrirOpcionesKit(p, ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("EDITAR: ", ""));
        } else if (t.contains("KITS -")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 49) { categoriaActual.put(p.getUniqueId(), categoriaActual.get(p.getUniqueId()).equals("GRATIS") ? "PREMIUM" : "GRATIS"); abrirMenuKits(p); }
            else { darKit(p, ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("Kit: ", "")); }
        } else if (t.startsWith("Items de:")) {
            if (e.getRawSlot() >= 36) {
                e.setCancelled(true);
                if (e.getRawSlot() >= 36 && e.getRawSlot() <= 43) { guardarItemsEditor(e.getInventory(), k); abrirOpcionesKit(p, k); }
                else if (e.getRawSlot() == 44) { abrirOpcionesKit(p, k); }
            }
        }
    }

    private void darKit(Player p, String k) {
        FileConfiguration cf = getKitConfig(k);
        if (cf == null) return;
        double precio = cf.getDouble("precio");
        if (precio > 0 && econ != null && econ.getBalance(p) < precio) { p.sendMessage(c("&cNo tienes dinero suficiente.")); return; }
        if (precio > 0) econ.withdrawPlayer(p, precio);
        
        List<?> items = cf.getList("items");
        if (items != null) {
            for (Object o : items) p.getInventory().addItem((ItemStack) o);
            p.sendMessage(c("&aHas recibido el kit " + k));
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
            if (modo.equals("CREAR")) { 
                boolean esPre = categoriaActual.getOrDefault(p.getUniqueId(), "GRATIS").equals("PREMIUM");
                crearKitBase(msg, esPre); 
                abrirOpcionesKit(p, msg); 
            } else {
                FileConfiguration cf = getKitConfig(k);
                try {
                    if (modo.equals("PRECIO")) cf.set("precio", Double.parseDouble(msg));
                    if (modo.equals("COOLDOWN")) cf.set("cooldown", Integer.parseInt(msg));
                    guardarKit(k, cf);
                    p.sendMessage(c("&aActualizado correctamente."));
                } catch (Exception ex) { p.sendMessage(c("&cError en el formato.")); }
                abrirOpcionesKit(p, k);
            }
            modoChat.remove(p.getUniqueId());
        });
    }

    private void crearKitBase(String n, boolean esPremium) {
        File f = new File(getDataFolder() + "/kits", n + ".yml");
        FileConfiguration c = YamlConfiguration.loadConfiguration(f);
        c.set("precio", 0.0); 
        c.set("cooldown", 0); 
        c.set("requiere-permiso", esPremium);
        c.set("icono", esPremium ? "DIAMOND_SWORD" : "STONE_SWORD");
        c.set("auto-equip", false);
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

    private void guardarItemsEditor(Inventory inv, String k) {
        FileConfiguration cf = getKitConfig(k);
        if (cf == null) return;
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) list.add(item);
        }
        cf.set("items", list); guardarKit(k, cf);
    }

    private ItemStack createItem(Material m, String n, boolean g, String... lore) {
        ItemStack i = new ItemStack(m); ItemMeta mt = i.getItemMeta();
        mt.setDisplayName(c(n)); List<String> l = new ArrayList<>();
        for (String s : lore) l.add(c(s)); mt.setLore(l);
        if (g) { mt.addEnchant(Enchantment.PROTECTION, 1, true); mt.addItemFlags(ItemFlag.HIDE_ENCHANTS); }
        i.setItemMeta(mt); return i;
    }
}
