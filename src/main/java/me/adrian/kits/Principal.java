package me.adrian.kits;

import net.milkbowl.vault.economy.Economy;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Principal extends JavaPlugin implements Listener {
    private static Economy econ = null;
    private final Map<UUID, String> categoriaActual = new HashMap<>();
    private final Map<UUID, String> editandoKit = new HashMap<>();
    private FileConfiguration lang;
    private FileConfiguration mensajes;

    @Override
    public void onEnable() {
        setupEconomy();
        saveDefaultConfig();
        
        // Crear carpetas necesarias
        new File(getDataFolder(), "kits").mkdirs();
        new File(getDataFolder(), "userdata").mkdirs();
        
        cargarArchivosInternos();
        
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("akits").setExecutor(new KitsCommand());
        
        Bukkit.getConsoleSender().sendMessage(c("&#00fbff&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        Bukkit.getConsoleSender().sendMessage(c("&b&lAdvancedKits &#00ff88&lV3.0 COMPILACIÓN EXITOSA"));
        Bukkit.getConsoleSender().sendMessage(c("&#00fbff&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
    }

    private void cargarArchivosInternos() {
        File fLang = new File(getDataFolder(), "lang.yml");
        File fMsgs = new File(getDataFolder(), "mensajes.yml");

        if (!fLang.exists()) {
            YamlConfiguration defLang = new YamlConfiguration();
            defLang.set("menus.principal-titulo", "&#00fbff&lKITS &#7f8c8d| &f{categoria}");
            defLang.set("menus.preview-titulo", "&#00fbff&lVISTA: &f{kit}");
            defLang.set("items.cambiar-categoria", "&#fbff00&lCATEGORÍA: &fClick para cambiar");
            defLang.set("items.kit-nombre", "&#00fbff&lKit: &f&n{nombre}");
            defLang.set("items.kit-lore-precio", "&#00fbff Precio: &a${precio}");
            defLang.set("items.estado-listo", "&a Estado: &#00ff88¡LISTO!");
            defLang.set("titulos.exito-titulo", "&#00ff88&l¡KIT RECIBIDO!");
            try { defLang.save(fLang); } catch (IOException e) { e.printStackTrace(); }
        }

        if (!fMsgs.exists()) {
            YamlConfiguration defMsgs = new YamlConfiguration();
            defMsgs.set("mensajes.reload", "&a¡Configuración recargada!");
            defMsgs.set("mensajes.recibido-actionbar", "&#00ff88&l✔ &fKit entregado con éxito");
            defMsgs.set("mensajes.sin-dinero", "&c&l✘ &7No tienes dinero suficiente.");
            try { defMsgs.save(fMsgs); } catch (IOException e) { e.printStackTrace(); }
        }

        lang = YamlConfiguration.loadConfiguration(fLang);
        mensajes = YamlConfiguration.loadConfiguration(fMsgs);
    }

    public String getMsg(String path) {
        String msg = lang.getString(path);
        if (msg == null) msg = mensajes.getString(path, "&cError: " + path);
        return c(msg);
    }

    public String c(String message) {
        if (message == null) return "";
        Pattern pattern = Pattern.compile("&#[a-fA-F0-9]{6}");
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            String color = message.substring(matcher.start(), matcher.end());
            message = message.replace(color, net.md_5.bungee.api.ChatColor.of(color.substring(1)) + "");
            matcher = pattern.matcher(message);
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        return rsp != null && (econ = rsp.getProvider()) != null;
    }

    // --- COMANDO ---
    public class KitsCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender s, Command cmd, String l, String[] a) {
            if (!(s instanceof Player)) return true;
            Player p = (Player) s;
            if (a.length == 0) {
                categoriaActual.putIfAbsent(p.getUniqueId(), "GRATIS");
                abrirMenuKits(p);
                return true;
            }
            if (a[0].equalsIgnoreCase("admin") && p.hasPermission("kitsadvanced.admin")) {
                abrirPanelAdmin(p);
                return true;
            }
            if (a[0].equalsIgnoreCase("reload") && p.hasPermission("kitsadvanced.admin")) {
                cargarArchivosInternos();
                p.sendMessage(getMsg("mensajes.reload"));
                return true;
            }
            return true;
        }
    }

    // --- MENÚS ---
    public void abrirMenuKits(Player p) {
        String cat = categoriaActual.getOrDefault(p.getUniqueId(), "GRATIS");
        Inventory inv = Bukkit.createInventory(null, 54, getMsg("menus.principal-titulo").replace("{categoria}", cat));
        
        ItemStack frame = createItem(Material.CYAN_STAINED_GLASS_PANE, " ", false);
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i > 44 || i % 9 == 0 || (i + 1) % 9 == 0) inv.setItem(i, frame);
        }
        
        inv.setItem(49, createItem(Material.NETHER_STAR, getMsg("items.cambiar-categoria"), true));

        File[] archivos = new File(getDataFolder(), "kits").listFiles();
        if (archivos != null) {
            for (File f : archivos) {
                String nombre = f.getName().replace(".yml", "");
                FileConfiguration conf = getKitConfig(nombre);
                if (conf != null && conf.getBoolean("requiere-permiso") == cat.equals("PREMIUM")) {
                    inv.addItem(createItem(Material.getMaterial(conf.getString("icono", "STONE_SWORD")), 
                        getMsg("items.kit-nombre").replace("{nombre}", nombre), false, 
                        getMsg("items.kit-lore-precio").replace("{precio}", String.valueOf(conf.getDouble("precio"))),
                        "&eClick: &bReclamar", "&7Derecho: &bPreview"));
                }
            }
        }
        p.openInventory(inv);
    }

    public void abrirPanelAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c("&0&lADMIN &8- &7Kits"));
        inv.setItem(4, createItem(Material.NETHER_STAR, "&b&l[+] CREAR NUEVO KIT", true, "&7Click para crear kit vacío"));
        
        File[] archivos = new File(getDataFolder(), "kits").listFiles();
        if (archivos != null) {
            int slot = 9;
            for (File f : archivos) {
                if (slot > 44) break;
                String k = f.getName().replace(".yml", "");
                inv.setItem(slot++, createItem(Material.PAPER, "&eEditar: &f" + k, false, "&7Click para configurar"));
            }
        }
        p.openInventory(inv);
    }

    public void abrirConfigKit(Player p, String k) {
        editandoKit.put(p.getUniqueId(), k);
        FileConfiguration cf = getKitConfig(k);
        Inventory inv = Bukkit.createInventory(null, 27, c("&0Configurar: " + k));
        
        inv.setItem(10, createItem(Material.CHEST, "&6Gestionar Items", false, "&7Copia tu inventario al kit"));
        inv.setItem(12, createItem(Material.SUNFLOWER, "&ePrecio: &a$" + cf.getDouble("precio"), false, "&7L-Click +10 / R-Click -10"));
        inv.setItem(14, createItem(Material.CLOCK, "&bCooldown: &f" + cf.getInt("cooldown") + "s", false, "&7L-Click +60s / R-Click -60s"));
        inv.setItem(16, createItem(Material.BARRIER, "&4&lELIMINAR KIT", true, "&c¡Borrar permanentemente!"));
        inv.setItem(26, createItem(Material.ARROW, "&cVolver", false));
        
        p.openInventory(inv);
    }

    // --- EVENTOS ---
    @EventHandler
    public void alHacerClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.getCurrentItem() == null) return;
        Player p = (Player) e.getWhoClicked();
        String t = ChatColor.stripColor(e.getView().getTitle());

        if (t.contains("|")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 49) {
                categoriaActual.put(p.getUniqueId(), categoriaActual.get(p.getUniqueId()).equals("GRATIS") ? "PREMIUM" : "GRATIS");
                abrirMenuKits(p);
            } else if (e.getRawSlot() < 45) {
                String kitName = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("Kit: ", "");
                if (e.getClick().isRightClick()) abrirPreview(p, kitName);
                else darKit(p, kitName);
            }
        }
        else if (t.equals("ADMIN - Kits")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 4) {
                crearKitBase("Kit_" + (new Random().nextInt(999)));
                abrirPanelAdmin(p);
            } else if (e.getRawSlot() >= 9) {
                String k = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("Editar: ", "");
                abrirConfigKit(p, k);
            }
        }
        else if (t.contains("Configurar:")) {
            e.setCancelled(true);
            String k = editandoKit.get(p.getUniqueId());
            FileConfiguration cf = getKitConfig(k);
            if (e.getRawSlot() == 10) {
                List<ItemStack> items = new ArrayList<>();
                for (ItemStack i : p.getInventory().getContents()) if (i != null) items.add(i);
                cf.set("items", items);
                p.sendMessage(c("&a¡Items guardados!"));
            } else if (e.getRawSlot() == 12) {
                double pr = cf.getDouble("precio");
                cf.set("precio", e.isLeftClick() ? pr + 10 : Math.max(0, pr - 10));
            } else if (e.getRawSlot() == 14) {
                int co = cf.getInt("cooldown");
                cf.set("cooldown", e.isLeftClick() ? co + 60 : Math.max(0, co - 60));
            } else if (e.getRawSlot() == 16) {
                new File(getDataFolder() + "/kits", k + ".yml").delete();
                abrirPanelAdmin(p); return;
            } else if (e.getRawSlot() == 26) { abrirPanelAdmin(p); return; }
            guardarKit(k, cf); abrirConfigKit(p, k);
        }
    }

    // --- LÓGICA CORE ---
    private void darKit(Player p, String k) {
        FileConfiguration cf = getKitConfig(k);
        if (cf == null) return;
        
        double precio = cf.getDouble("precio");
        if (precio > 0 && (econ == null || econ.getBalance(p) < precio)) {
            p.sendMessage(getMsg("mensajes.sin-dinero"));
            return;
        }

        if (precio > 0) econ.withdrawPlayer(p, precio);
        for (Object o : cf.getList("items")) if (o instanceof ItemStack) p.getInventory().addItem((ItemStack) o);
        
        p.sendTitle(getMsg("titulos.exito-titulo"), "", 10, 40, 10);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(getMsg("mensajes.recibido-actionbar")));
    }

    public void abrirPreview(Player p, String k) {
        FileConfiguration cf = getKitConfig(k);
        Inventory inv = Bukkit.createInventory(null, 45, getMsg("menus.preview-titulo").replace("{kit}", k));
        for (Object i : cf.getList("items")) if (i instanceof ItemStack) inv.addItem((ItemStack) i);
        p.openInventory(inv);
    }

    // --- UTILS ---
    private ItemStack createItem(Material m, String n, boolean glint, String... lore) {
        ItemStack i = new ItemStack(m); ItemMeta mt = i.getItemMeta();
        if (mt != null) {
            mt.setDisplayName(c(n));
            List<String> l = new ArrayList<>();
            for (String s : lore) l.add(c(s));
            mt.setLore(l);
            if (glint) {
                // AQUÍ ESTÁ LA CORRECCIÓN: UNBREAKING en lugar de DURABILITY
                mt.addEnchant(Enchantment.UNBREAKING, 1, true);
                mt.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            i.setItemMeta(mt);
        }
        return i;
    }

    private void crearKitBase(String n) {
        FileConfiguration c = new YamlConfiguration();
        c.set("precio", 0.0); c.set("cooldown", 0); c.set("requiere-permiso", false);
        c.set("icono", "STONE_SWORD"); c.set("items", new ArrayList<ItemStack>());
        guardarKit(n, c);
    }

    public FileConfiguration getKitConfig(String n) {
        File f = new File(getDataFolder() + "/kits", n + ".yml");
        return f.exists() ? YamlConfiguration.loadConfiguration(f) : null;
    }

    public void guardarKit(String n, FileConfiguration conf) {
        try { conf.save(new File(getDataFolder() + "/kits", n + ".yml")); } catch (IOException e) { e.printStackTrace(); }
    }
}
