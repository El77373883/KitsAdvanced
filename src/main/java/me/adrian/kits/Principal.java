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
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class Principal extends JavaPlugin implements Listener {
    private final Map<UUID, String> editandoKit = new HashMap<>();
    private final Map<UUID, String> modoChat = new HashMap<>();

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdir();
        File carpetaKits = new File(getDataFolder(), "kits");
        if (!carpetaKits.exists()) carpetaKits.mkdirs();

        crearKitPrueba("Gratis", false, Material.STONE_SWORD, 0.0);
        crearKitPrueba("Premium_VIP", true, Material.DIAMOND_SWORD, 500.0);

        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("akits").setExecutor(new KitsCommand());
    }

    private void crearKitPrueba(String nombre, boolean premium, Material icono, double precio) {
        File f = new File(getDataFolder() + "/kits", nombre + ".yml");
        if (!f.exists()) {
            FileConfiguration c = YamlConfiguration.loadConfiguration(f);
            c.set("requiere-permiso", premium);
            c.set("precio", precio);
            c.set("cooldown-activado", true);
            c.set("cooldown-segundos", 60);
            c.set("icono", icono.name());
            c.set("auto-equipar", false);
            c.set("items", new ArrayList<ItemStack>());
            try { c.save(f); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private String c(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    public FileConfiguration getKitConfig(String nombre) {
        try {
            File file = new File(getDataFolder() + "/kits", nombre + ".yml");
            YamlConfiguration config = new YamlConfiguration();
            config.load(file);
            return config;
        } catch (Exception e) { return null; }
    }

    public void guardarKit(String nombre, FileConfiguration config) {
        try { config.save(new File(getDataFolder() + "/kits", nombre + ".yml")); } catch (IOException e) { e.printStackTrace(); }
    }

    public class KitsCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender s, Command cmd, String l, String[] a) {
            if (!(s instanceof Player)) return true;
            Player p = (Player) s;
            if (a.length == 0) { abrirMenuKits(p); return true; }
            if (!p.hasPermission("kitsadvanced.admin")) return true;

            if (a[0].equalsIgnoreCase("reload")) {
                boolean error = false;
                File[] archivos = new File(getDataFolder(), "kits").listFiles((dir, name) -> name.endsWith(".yml"));
                if (archivos != null) {
                    for (File f : archivos) if (getKitConfig(f.getName().replace(".yml", "")) == null) error = true;
                }
                if (error) {
                    p.sendMessage(c("&8&l&m---------------------------------------"));
                    p.sendMessage(c("&c&l⚠ ERROR CRÍTICO DE CONFIGURACIÓN ⚠"));
                    p.sendMessage(c("&7Recarga abortada por fallo en archivos .yml"));
                    p.sendMessage(c("&8&l&m---------------------------------------"));
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1);
                } else {
                    p.sendMessage(c("&a&l✔ &f¡Kits recargados con éxito!"));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 2);
                }
                return true;
            }
            if (a[0].equalsIgnoreCase("panel")) { abrirPanelAdmin(p); return true; }
            return true;
        }
    }

    public void abrirMenuKits(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c("&0&lMENÚ DE KITS"));
        
        // --- OPCIÓN 1: BORDES DECORATIVOS ---
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i > 44 || i % 9 == 0 || (i + 1) % 9 == 0) {
                inv.setItem(i, createItem(Material.CYAN_STAINED_GLASS_PANE, " ", false));
            }
        }

        File[] archivos = new File(getDataFolder(), "kits").listFiles((dir, name) -> name.endsWith(".yml"));
        int kitCount = 0;
        if (archivos != null) {
            for (File f : archivos) {
                String n = f.getName().replace(".yml", "");
                FileConfiguration conf = getKitConfig(n);
                if (conf == null) continue;
                kitCount++;
                inv.addItem(createKitIcon(n));
            }
        }

        // --- OPCIÓN 2: ESTADÍSTICAS ---
        inv.setItem(49, createItem(Material.BOOK, "&b&lTUS ESTADÍSTICAS", true, 
            "&7Kits Totales: &f" + kitCount, 
            "&7Tu Rango: &e" + (p.isOp() ? "ADMIN" : "JUGADOR")));

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
    }

    private ItemStack createKitIcon(String k) {
        FileConfiguration conf = getKitConfig(k);
        Material icono = Material.valueOf(conf.getString("icono", "CHEST"));
        boolean pre = conf.getBoolean("requiere-permiso");
        
        List<String> lore = new ArrayList<>();
        // --- OPCIÓN 3: DESTACADOS ---
        if (pre) lore.add(c("&d&l⭐ KIT EXCLUSIVO ⭐"));
        else lore.add(c("&a&l✔ KIT PÚBLICO"));
        
        lore.add(c("&8&m--------------------------"));
        lore.add(c("&7Precio: &a$" + conf.getDouble("precio")));
        if (conf.getBoolean("cooldown-activado")) lore.add(c("&7Espera: &e" + conf.getInt("cooldown-segundos") + "s"));
        lore.add(c("&e▶ Click para reclamar"));
        
        return createItem(icono, "&e&lKit: &f" + k, pre, lore.toArray(new String[0]));
    }

    public void abrirPanelAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c("&0Panel de Administración"));
        
        // Borde Rojo para modo Admin
        for (int i = 0; i < 9; i++) inv.setItem(i, createItem(Material.RED_STAINED_GLASS_PANE, " ", false));
        
        inv.setItem(4, createItem(Material.NETHER_STAR, "&b&l[+] CREAR KIT", true));
        File[] archivos = new File(getDataFolder(), "kits").listFiles((dir, name) -> name.endsWith(".yml"));
        if (archivos != null) for (File f : archivos) inv.addItem(createItem(Material.PAPER, "&eEditar: &f" + f.getName(), false, "&7Click: &aAbrir", "&cQ: &7Eliminar"));
        
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1);
    }

    public void abrirOpcionesKit(Player p, String k) {
        editandoKit.put(p.getUniqueId(), k);
        FileConfiguration conf = getKitConfig(k);
        Inventory inv = Bukkit.createInventory(null, 45, c("&0Ajustes: &8" + k));
        
        // Bordes Amarillos para ajustes
        for (int i = 36; i < 45; i++) inv.setItem(i, createItem(Material.YELLOW_STAINED_GLASS_PANE, " ", false));

        boolean pre = conf.getBoolean("requiere-permiso");
        boolean time = conf.getBoolean("cooldown-activado");
        boolean auto = conf.getBoolean("auto-equipar");

        inv.setItem(10, createItem(Material.TRAPPED_CHEST, "&6&lÍtems", false));
        inv.setItem(11, createItem(Material.REDSTONE, "&e&lPermisos", pre, "&7Estado: " + (pre ? "&aON" : "&cOFF")));
        inv.setItem(12, createItem(Material.SUNFLOWER, "&e&lPrecio", false, "&7Actual: &a$" + conf.getDouble("precio")));
        inv.setItem(13, createItem(Material.CLOCK, "&b&lCooldown", time, "&7Estado: " + (time ? "&aON" : "&cOFF"), "&7Tiempo: &e" + conf.getInt("cooldown-segundos") + "s", "&8(Derecho p/ segundos)"));
        inv.setItem(14, createItem(Material.DIAMOND_CHESTPLATE, "&b&lAuto-Equipar", auto, "&7Estado: " + (auto ? "&aON" : "&cOFF")));
        inv.setItem(40, createItem(Material.ARROW, "&c« Volver", false));
        
        p.openInventory(inv);
        // --- OPCIÓN 4: SONIDO TRANSICIÓN ---
        p.playSound(p.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1, 1);
    }

    @EventHandler
    public void alHacerClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.getCurrentItem() == null) return;
        Player p = (Player) e.getWhoClicked();
        String t = ChatColor.stripColor(e.getView().getTitle());
        String k = editandoKit.get(p.getUniqueId());

        if (t.equals("MENÚ DE KITS")) {
            e.setCancelled(true);
            if (e.getCurrentItem().getType() != Material.CYAN_STAINED_GLASS_PANE && e.getRawSlot() < 54 && e.getRawSlot() != 49) {
                darKit(p, ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("Kit: ", ""));
            }
        } else if (t.equals("Panel de Administración")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 4) { p.closeInventory(); modoChat.put(p.getUniqueId(), "CREAR"); p.sendMessage(c("&b&l[+] &fEscribe el nombre:")); }
            else if (e.getCurrentItem().getType() == Material.PAPER) {
                String kit = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("Editar: ", "").replace(".yml", "");
                if (e.getClick() == ClickType.DROP) { new File(getDataFolder() + "/kits", kit + ".yml").delete(); p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1, 1); abrirPanelAdmin(p); }
                else abrirOpcionesKit(p, kit);
            }
        } else if (t.startsWith("Ajustes:")) {
            e.setCancelled(true);
            FileConfiguration conf = getKitConfig(k);
            if (e.getRawSlot() == 10) abrirCofreEditor(p, k);
            if (e.getRawSlot() == 11) { toggle(p, conf, "requiere-permiso", k); abrirOpcionesKit(p, k); }
            if (e.getRawSlot() == 12) { p.closeInventory(); modoChat.put(p.getUniqueId(), "PRECIO"); p.sendMessage(c("&e&l[!] &fEscribe el precio:")); }
            if (e.getRawSlot() == 13) { 
                if (e.getClick() == ClickType.RIGHT) { p.closeInventory(); modoChat.put(p.getUniqueId(), "TIEMPO"); p.sendMessage(c("&b&l[!] &fEscribe segundos:")); }
                else { toggle(p, conf, "cooldown-activado", k); abrirOpcionesKit(p, k); }
            }
            if (e.getRawSlot() == 14) { toggle(p, conf, "auto-equipar", k); abrirOpcionesKit(p, k); }
            if (e.getRawSlot() == 40) abrirPanelAdmin(p);
        }
    }

    private void toggle(Player p, FileConfiguration conf, String path, String k) {
        boolean val = !conf.getBoolean(path);
        conf.set(path, val); guardarKit(k, conf);
        if (val) { p.sendMessage(c("&a&l✔ &fActivado correctamente")); p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 2); }
        else { p.sendMessage(c("&c&l✘ &fDesactivado correctamente")); p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 1); }
    }

    @EventHandler
    public void alChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!modoChat.containsKey(p.getUniqueId())) return;
        e.setCancelled(true);
        String msg = e.getMessage();
        String modo = modoChat.get(p.getUniqueId());
        String k = editandoKit.get(p.getUniqueId());

        Bukkit.getScheduler().runTask(this, () -> {
            try {
                if (modo.equals("CREAR")) { crearKitPrueba(msg.replace(" ", "_"), false, Material.CHEST, 0.0); modoChat.remove(p.getUniqueId()); abrirOpcionesKit(p, msg.replace(" ", "_")); }
                else if (modo.equals("PRECIO")) { FileConfiguration cf = getKitConfig(k); cf.set("precio", Double.parseDouble(msg)); guardarKit(k, cf); modoChat.remove(p.getUniqueId()); abrirOpcionesKit(p, k); }
                else if (modo.equals("TIEMPO")) { FileConfiguration cf = getKitConfig(k); cf.set("cooldown-segundos", Integer.parseInt(msg)); guardarKit(k, cf); modoChat.remove(p.getUniqueId()); abrirOpcionesKit(p, k); }
            } catch (Exception ex) {
                p.sendMessage(c("&c&l[!] &7Entrada inválida.")); p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                modoChat.remove(p.getUniqueId()); abrirOpcionesKit(p, k);
            }
        });
    }

    private void darKit(Player p, String k) {
        FileConfiguration conf = getKitConfig(k);
        if (conf == null) return;
        if (conf.getBoolean("requiere-permiso") && !p.hasPermission("kits.use." + k)) {
            p.sendMessage(c("&c&l✘ &7Sin permiso para &f" + k));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1); return;
        }
        if (conf.getBoolean("cooldown-activado")) {
            long last = conf.getLong("users." + p.getUniqueId(), 0);
            long cooldown = conf.getInt("cooldown-segundos") * 1000L;
            if (System.currentTimeMillis() < last + cooldown) {
                long rest = (last + cooldown - System.currentTimeMillis()) / 1000;
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(c("&cEspera: &f" + rest + "s")));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HIT, 1, 1); return;
            }
        }
        List<?> items = conf.getList("items");
        if (items != null) {
            conf.set("users." + p.getUniqueId(), System.currentTimeMillis()); guardarKit(k, conf);
            for (Object o : items) p.getInventory().addItem((ItemStack) o);
            p.sendMessage(c("&a&l✔ &7Kit &f" + k + " &7equipado."));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 2);
        }
    }

    private void abrirCofreEditor(Player p, String k) {
        Inventory ed = Bukkit.createInventory(null, 36, c("&0Items de: " + k));
        List<?> items = getKitConfig(k).getList("items");
        if (items != null) for (Object i : items) if (i instanceof ItemStack) ed.addItem((ItemStack) i);
        p.openInventory(ed);
    }

    @EventHandler
    public void alCerrar(InventoryCloseEvent e) {
        if (ChatColor.stripColor(e.getView().getTitle()).startsWith("Items de: ")) {
            String k = ChatColor.stripColor(e.getView().getTitle()).replace("Items de: ", "");
            FileConfiguration conf = getKitConfig(k);
            if (conf == null) return;
            List<ItemStack> list = new ArrayList<>();
            for (ItemStack i : e.getInventory().getContents()) if (i != null && i.getType() != Material.AIR) list.add(i);
            conf.set("items", list); guardarKit(k, conf);
        }
    }

    private ItemStack createItem(Material m, String n, boolean glint, String... lore) {
        ItemStack i = new ItemStack(m); ItemMeta mt = i.getItemMeta();
        if (mt != null) {
            mt.setDisplayName(c(n)); List<String> l = new ArrayList<>();
            for (String s : lore) l.add(c(s)); mt.setLore(l);
            if (glint) { mt.addEnchant(Enchantment.UNBREAKING, 1, true); mt.addItemFlags(ItemFlag.HIDE_ENCHANTS); }
            i.setItemMeta(mt);
        }
        return i;
    }
}
