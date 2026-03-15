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
import org.bukkit.event.player.AsyncPlayerChatEvent;
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
    private final Map<UUID, String> esperandoPrecio = new HashMap<>();
    private final Map<UUID, String> pendienteConfirmar = new HashMap<>();
    private final Map<String, Map<UUID, Long>> cooldowns = new HashMap<>(); 
    private FileConfiguration lang;
    private FileConfiguration mensajes;

    @Override
    public void onEnable() {
        setupEconomy();
        saveDefaultConfig();
        new File(getDataFolder(), "kits").mkdirs();
        cargarArchivosInternos();
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("akits").setExecutor(new KitsCommand());
    }

    private void cargarArchivosInternos() {
        File fLang = new File(getDataFolder(), "lang.yml");
        File fMsgs = new File(getDataFolder(), "mensajes.yml");
        if (!fLang.exists()) {
            YamlConfiguration defLang = new YamlConfiguration();
            defLang.set("menus.principal-titulo", "&#00fbff&lKITS &#7f8c8d| &f{categoria}");
            defLang.set("menus.preview-titulo", "&#00fbff&lVISTA: &f{kit}");
            defLang.set("menus.confirmar-titulo", "&8¿Confirmar compra?");
            defLang.set("items.cambiar-categoria", "&#fbff00&lCATEGORÍA: &fClick para cambiar");
            defLang.set("items.kit-nombre", "&#00fbff&lKit: &f&n{nombre}");
            defLang.set("items.kit-lore-precio", "&#00fbff Precio: &a${precio}");
            defLang.set("titulos.exito-titulo", "&#00ff88&l¡KIT RECIBIDO!");
            try { defLang.save(fLang); } catch (IOException e) { e.printStackTrace(); }
        }
        if (!fMsgs.exists()) {
            YamlConfiguration defMsgs = new YamlConfiguration();
            defMsgs.set("mensajes.reload", "&a¡Configuración recargada!");
            defMsgs.set("mensajes.recibido-actionbar", "&#00ff88&l✔ &fKit entregado con éxito");
            defMsgs.set("mensajes.sin-dinero", "&c&l✘ &7No tienes dinero suficiente.");
            defMsgs.set("mensajes.sin-permiso", "&c&l✘ &7No tienes permiso (kits.premium.{kit})");
            defMsgs.set("mensajes.cooldown", "&c&l✘ &7Debes esperar &e{tiempo}s &7para usar esto.");
            defMsgs.set("mensajes.compra-cancelada", "&cCompra cancelada.");
            try { defMsgs.save(fMsgs); } catch (IOException e) { e.printStackTrace(); }
        }
        lang = YamlConfiguration.loadConfiguration(fLang);
        mensajes = YamlConfiguration.loadConfiguration(fMsgs);
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
            return true;
        }
    }

    public void abrirConfigKit(Player p, String k) {
        editandoKit.put(p.getUniqueId(), k);
        FileConfiguration cf = getKitConfig(k);
        Inventory inv = Bukkit.createInventory(null, 45, c("&0Configurar Kit: " + k));
        
        // Diseño de fondo profesional
        ItemStack bg = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", false);
        for(int i=0; i<45; i++) inv.setItem(i, bg);

        inv.setItem(10, createItem(Material.CHEST, "&6&l1. &eEditar Items", true, "&7Configura el contenido del kit."));
        inv.setItem(11, createItem(Material.SUNFLOWER, "&e&l2. &ePrecio: &a$" + cf.getDouble("precio"), false, "&7Haz click para cambiar el coste."));
        inv.setItem(12, createItem(Material.CLOCK, "&b&l3. &eCooldown: &f" + cf.getInt("cooldown") + "s", false, "&eL-Click &7+60s / &eR-Click &7-60s"));
        inv.setItem(13, createItem(Material.IRON_DOOR, "&d&l4. &eTipo: " + (cf.getBoolean("requiere-permiso") ? "&aPREMIUM" : "&bGRATIS"), false, "&7¿Requiere permiso kits.premium."+k+"?"));
        inv.setItem(14, createItem(Material.ITEM_FRAME, "&f&l5. &eCambiar Icono", false, "&7Usa el item de tu mano como icono."));
        inv.setItem(15, createItem(Material.ARMOR_STAND, "&a&l6. &eAuto-Equipar: " + (cf.getBoolean("auto-equip", true) ? "&aON" : "&cOFF"), false, "&7Equipa armaduras automáticamente."));
        inv.setItem(16, createItem(Material.BUCKET, "&c&l7. &eLimpiar Inv: " + (cf.getBoolean("clear-inv", false) ? "&aON" : "&cOFF"), false, "&7Borra el inventario del usuario antes."));
        inv.setItem(19, createItem(Material.COMMAND_BLOCK, "&5&l8. &eEditar Comandos", false, "&7Añade comandos de consola al entregar."));
        
        inv.setItem(31, createItem(Material.ARROW, "&c&lVOLVER AL PANEL", false));
        inv.setItem(35, createItem(Material.BARRIER, "&4&lELIMINAR KIT", true, "&c¡Esta acción es irreversible!"));
        
        p.openInventory(inv);
    }

    @EventHandler
    public void alHacerClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.getCurrentItem() == null) return;
        Player p = (Player) e.getWhoClicked();
        String t = ChatColor.stripColor(e.getView().getTitle());

        // Menú Principal de Kits (Lógica de Clics solicitada)
        if (t.contains("|")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 49) {
                categoriaActual.put(p.getUniqueId(), categoriaActual.get(p.getUniqueId()).equals("GRATIS") ? "PREMIUM" : "GRATIS");
                abrirMenuKits(p);
            } else if (e.getRawSlot() < 45 && e.getCurrentItem().getType() != Material.AIR && e.getCurrentItem().getType() != Material.CYAN_STAINED_GLASS_PANE) {
                String kitName = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("Kit: ", "");
                if (e.getClick().isRightClick()) {
                    abrirPreview(p, kitName);
                } else {
                    abrirConfirmacion(p, kitName);
                }
            }
        }
        // Confirmación Premium
        else if (t.equals("¿Confirmar compra?")) {
            e.setCancelled(true);
            String kitName = pendienteConfirmar.get(p.getUniqueId());
            if (e.getRawSlot() >= 10 && e.getRawSlot() <= 12) {
                darKit(p, kitName); p.closeInventory();
            } else if (e.getRawSlot() >= 14 && e.getRawSlot() <= 16) {
                p.sendMessage(c(mensajes.getString("mensajes.compra-cancelada"))); p.closeInventory();
            }
        }
        // Admin y Config
        else if (t.equals("ADMIN - Kits")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 4) { crearKitBase("Kit_" + (new Random().nextInt(999))); abrirPanelAdmin(p); }
            else if (e.getRawSlot() >= 9) {
                String k = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).split(":")[1].trim();
                abrirConfigKit(p, k);
            }
        }
        else if (t.contains("Configurar Kit:")) {
            e.setCancelled(true);
            String k = editandoKit.get(p.getUniqueId());
            FileConfiguration cf = getKitConfig(k);
            switch(e.getRawSlot()) {
                case 10: abrirEditorDeItems(p, k); break;
                case 11: p.closeInventory(); esperandoPrecio.put(p.getUniqueId(), k); break;
                case 12: cf.set("cooldown", e.isLeftClick() ? cf.getInt("cooldown") + 60 : Math.max(0, cf.getInt("cooldown") - 60)); break;
                case 13: cf.set("requiere-permiso", !cf.getBoolean("requiere-permiso")); break;
                case 14: if (p.getInventory().getItemInMainHand().getType() != Material.AIR) cf.set("icono", p.getInventory().getItemInMainHand().getType().toString()); break;
                case 15: cf.set("auto-equip", !cf.getBoolean("auto-equip", true)); break;
                case 16: cf.set("clear-inv", !cf.getBoolean("clear-inv", false)); break;
                case 35: new File(getDataFolder() + "/kits", k + ".yml").delete(); abrirPanelAdmin(p); return;
                case 31: abrirPanelAdmin(p); return;
            }
            guardarKit(k, cf); 
            if (!esperandoPrecio.containsKey(p.getUniqueId())) abrirConfigKit(p, k);
        }
    }

    public void abrirConfirmacion(Player p, String k) {
        pendienteConfirmar.put(p.getUniqueId(), k);
        Inventory inv = Bukkit.createInventory(null, 27, c("&8¿Confirmar compra?"));
        ItemStack bg = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", false);
        for(int i=0; i<27; i++) inv.setItem(i, bg);

        inv.setItem(11, createItem(Material.LIME_STAINED_GLASS_PANE, "&a&lCONFIRMAR", true, "&7Obtener el kit &e" + k));
        inv.setItem(13, createItem(Material.PAPER, "&f&lDETALLES", false, "&7Kit: &b" + k, "&7Precio: &a$" + getKitConfig(k).getDouble("precio")));
        inv.setItem(15, createItem(Material.RED_STAINED_GLASS_PANE, "&c&lCANCELAR", false, "&7Cerrar menú."));
        
        p.openInventory(inv);
    }

    public void abrirMenuKits(Player p) {
        String cat = categoriaActual.getOrDefault(p.getUniqueId(), "GRATIS");
        Inventory inv = Bukkit.createInventory(null, 54, c(lang.getString("menus.principal-titulo").replace("{categoria}", cat)));
        
        // Bordes Premium
        ItemStack frame = createItem(Material.CYAN_STAINED_GLASS_PANE, " ", false);
        for (int i = 0; i < 54; i++) if (i < 9 || i > 44 || i % 9 == 0 || (i + 1) % 9 == 0) inv.setItem(i, frame);
        
        inv.setItem(49, createItem(Material.NETHER_STAR, c(lang.getString("items.cambiar-categoria")), true, "&7Haz click para ver kits " + (cat.equals("GRATIS") ? "Premium" : "Gratis")));

        File[] archivos = new File(getDataFolder(), "kits").listFiles();
        if (archivos != null) {
            for (File f : archivos) {
                String n = f.getName().replace(".yml", "");
                FileConfiguration conf = getKitConfig(n);
                if (conf.getBoolean("requiere-permiso") == cat.equals("PREMIUM")) {
                    inv.addItem(createItem(Material.getMaterial(conf.getString("icono", "STONE_SWORD")), 
                        c(lang.getString("items.kit-nombre").replace("{nombre}", n)), false, 
                        c(lang.getString("items.kit-lore-precio").replace("{precio}", String.valueOf(conf.getDouble("precio")))),
                        " ",
                        "&e&lClick Izquierdo: &bComprar",
                        "&e&lClick Derecho: &bVer Contenido",
                        " "));
                }
            }
        }
        p.openInventory(inv);
    }

    private void crearKitBase(String n) {
        FileConfiguration c = new YamlConfiguration();
        c.set("precio", 0.0);
        c.set("cooldown", 3600); // 1 hora por defecto
        c.set("requiere-permiso", false);
        c.set("icono", "CHEST");
        c.set("auto-equip", true);
        c.set("clear-inv", false);
        c.set("items", new ArrayList<ItemStack>());
        guardarKit(n, c);
    }

    // --- MÉTODOS DE APOYO --- (Se mantienen igual para estabilidad)
    private void darKit(Player p, String k) {
        FileConfiguration cf = getKitConfig(k);
        if (cf == null) return;
        if (cf.getBoolean("requiere-permiso") && !p.hasPermission("kits.premium." + k)) {
            p.sendMessage(c(mensajes.getString("mensajes.sin-permiso").replace("{kit}", k))); return;
        }
        int cooldownSec = cf.getInt("cooldown", 0);
        if (cooldownSec > 0 && !p.hasPermission("kits.admin")) {
            Map<UUID, Long> kitCooldowns = cooldowns.computeIfAbsent(k, key -> new HashMap<>());
            if (kitCooldowns.containsKey(p.getUniqueId())) {
                long resta = (kitCooldowns.get(p.getUniqueId()) / 1000L + cooldownSec) - (System.currentTimeMillis() / 1000L);
                if (resta > 0) { p.sendMessage(c(mensajes.getString("mensajes.cooldown").replace("{tiempo}", String.valueOf(resta)))); return; }
            }
            kitCooldowns.put(p.getUniqueId(), System.currentTimeMillis());
        }
        double precio = cf.getDouble("precio");
        if (precio > 0 && (econ != null && econ.getBalance(p) < precio)) { p.sendMessage(c(mensajes.getString("mensajes.sin-dinero"))); return; }
        if (precio > 0) econ.withdrawPlayer(p, precio);
        if (cf.getBoolean("clear-inv")) p.getInventory().clear();
        List<?> items = cf.getList("items");
        if (items != null) {
            for (Object o : items) {
                if (!(o instanceof ItemStack)) continue;
                ItemStack item = (ItemStack) o;
                if (cf.getBoolean("auto-equip", true)) {
                    String type = item.getType().toString();
                    if (type.contains("HELMET")) p.getInventory().setHelmet(item);
                    else if (type.contains("CHESTPLATE")) p.getInventory().setChestplate(item);
                    else if (type.contains("LEGGINGS")) p.getInventory().setLeggings(item);
                    else if (type.contains("BOOTS")) p.getInventory().setBoots(item);
                    else p.getInventory().addItem(item);
                } else { p.getInventory().addItem(item); }
            }
        }
        p.sendTitle(c(lang.getString("titulos.exito-titulo")), "", 10, 40, 10);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(c(mensajes.getString("mensajes.recibido-actionbar"))));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
    }

    private ItemStack createItem(Material m, String n, boolean glint, String... lore) {
        ItemStack i = new ItemStack(m != null ? m : Material.STONE);
        ItemMeta mt = i.getItemMeta();
        if (mt != null) {
            mt.setDisplayName(c(n));
            List<String> l = new ArrayList<>();
            for (String s : lore) l.add(c(s)); mt.setLore(l);
            if (glint) { mt.addEnchant(Enchantment.UNBREAKING, 1, true); mt.addItemFlags(ItemFlag.HIDE_ENCHANTS); }
            i.setItemMeta(mt);
        }
        return i;
    }

    public void abrirPanelAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c("&0&lADMIN &8- &7Kits"));
        ItemStack bg = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", false);
        for(int i=0; i<54; i++) if(i<9 || i>44) inv.setItem(i, bg);
        inv.setItem(4, createItem(Material.NETHER_STAR, "&b&l[+] CREAR KIT", true, "&7Haz click para generar un kit nuevo."));
        File[] archivos = new File(getDataFolder(), "kits").listFiles();
        if (archivos != null) {
            int slot = 9;
            for (File f : archivos) {
                if (slot > 44) break;
                inv.setItem(slot++, createItem(Material.PAPER, "&eEditar: &f" + f.getName().replace(".yml", ""), false, "&7Click para configurar este kit."));
            }
        }
        p.openInventory(inv);
    }

    public void abrirPreview(Player p, String k) {
        FileConfiguration cf = getKitConfig(k);
        Inventory inv = Bukkit.createInventory(null, 45, c(lang.getString("menus.preview-titulo").replace("{kit}", k)));
        List<?> items = cf.getList("items");
        if (items != null) for (Object i : items) if (i instanceof ItemStack) inv.addItem((ItemStack) i);
        p.openInventory(inv);
    }

    public void abrirEditorDeItems(Player p, String k) {
        Inventory inv = Bukkit.createInventory(null, 45, c("&0Editando Items: " + k));
        FileConfiguration cf = getKitConfig(k);
        List<?> items = cf.getList("items");
        if (items != null) {
            int slot = 0;
            for (Object item : items) if (slot < 36 && item instanceof ItemStack) inv.setItem(slot++, (ItemStack) item);
        }
        ItemStack deco = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", false);
        for (int i = 36; i < 45; i++) inv.setItem(i, deco);
        inv.setItem(40, createItem(Material.ARROW, "&a&lGUARDAR Y VOLVER", true));
        p.openInventory(inv);
    }

    public FileConfiguration getKitConfig(String n) {
        File f = new File(getDataFolder() + "/kits", n + ".yml");
        return f.exists() ? YamlConfiguration.loadConfiguration(f) : null;
    }

    public void guardarKit(String n, FileConfiguration conf) {
        try { conf.save(new File(getDataFolder() + "/kits", n + ".yml")); } catch (IOException e) { e.printStackTrace(); }
    }

    @EventHandler public void alHablar(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (esperandoPrecio.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
            String k = esperandoPrecio.remove(p.getUniqueId());
            try {
                double nuevoPrecio = Double.parseDouble(e.getMessage());
                FileConfiguration cf = getKitConfig(k); cf.set("precio", nuevoPrecio);
                guardarKit(k, cf); p.sendMessage(c("&a&l✔ &fPrecio establecido."));
                Bukkit.getScheduler().runTask(this, () -> abrirConfigKit(p, k));
            } catch (NumberFormatException ex) { p.sendMessage(c("&c&l✘ &7Número inválido.")); }
        }
    }
}
