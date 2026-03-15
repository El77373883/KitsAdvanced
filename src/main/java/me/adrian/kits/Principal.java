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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Principal extends JavaPlugin implements Listener {
    private static Economy econ = null;
    private final Map<UUID, String> categoriaActual = new HashMap<>();
    private final Map<UUID, String> editandoKit = new HashMap<>();
    private final Map<UUID, String> esperandoPrecio = new HashMap<>();
    private final Map<UUID, Boolean> esperandoNombre = new HashMap<>();
    private final Map<UUID, String> pendienteConfirmar = new HashMap<>();
    private final Map<UUID, String> pendienteEliminar = new HashMap<>();
    private FileConfiguration lang;
    private FileConfiguration mensajes;

    @Override
    public void onEnable() {
        setupEconomy();
        saveDefaultConfig();
        if (!new File(getDataFolder(), "kits").exists()) new File(getDataFolder(), "kits").mkdirs();
        if (!new File(getDataFolder(), "data").exists()) new File(getDataFolder(), "data").mkdirs();
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
            try { defLang.save(fLang); } catch (IOException e) { e.printStackTrace(); }
        }
        if (!fMsgs.exists()) {
            YamlConfiguration defMsgs = new YamlConfiguration();
            defMsgs.set("mensajes.recibido", "&#00ff88&l✔ &f¡Has recibido el kit &e{kit}&f!");
            defMsgs.set("mensajes.sin-dinero", "&c&l✘ &7No tienes dinero suficiente ($ {precio}).");
            defMsgs.set("mensajes.cooldown", "&c&l✘ &7Espera &e{tiempo}s &7para volver a usarlo.");
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
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
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
            if (a[0].equalsIgnoreCase("panel") && p.hasPermission("kitsadvanced.admin")) {
                abrirPanelAdmin(p);
                return true;
            }
            return true;
        }
    }

    @EventHandler
    public void alHacerClick(InventoryClickEvent e) {
        if (e.getView().getTitle() == null) return;
        Player p = (Player) e.getWhoClicked();
        String t = ChatColor.stripColor(e.getView().getTitle());

        if (t.equals("¿Confirmar compra?")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 11) {
                String kitName = pendienteConfirmar.get(p.getUniqueId());
                darKit(p, kitName);
                p.closeInventory();
            } else if (e.getRawSlot() == 15) {
                p.closeInventory();
                p.sendMessage(c("&cCompra cancelada."));
            }
            return;
        }

        if (t.equals("¿Eliminar Kit?")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 11) {
                String kit = pendienteEliminar.get(p.getUniqueId());
                new File(getDataFolder() + "/kits", kit + ".yml").delete();
                p.sendMessage(c("&aKit eliminado."));
                p.closeInventory();
            } else if (e.getRawSlot() == 15) p.closeInventory();
            return;
        }

        if (t.contains("|")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 49) {
                String nueva = categoriaActual.get(p.getUniqueId()).equals("GRATIS") ? "PREMIUM" : "GRATIS";
                categoriaActual.put(p.getUniqueId(), nueva);
                abrirMenuKits(p);
            } else if (e.getRawSlot() < 45 && e.getCurrentItem() != null && e.getCurrentItem().getType() != Material.AIR) {
                String kitName = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("Kit: ", "");
                if (e.getClick().isRightClick()) abrirPreview(p, kitName);
                else abrirConfirmacion(p, kitName);
            }
        }

        if (t.equals("ADMIN - Kits")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 4) { 
                p.closeInventory(); 
                esperandoNombre.put(p.getUniqueId(), true); 
                p.sendMessage(c("&eEscribe el nombre del kit:")); 
            }
            else if (e.getRawSlot() >= 9 && e.getCurrentItem() != null) {
                String k = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("Editar: ", "");
                abrirConfigKit(p, k);
            }
        }

        if (t.contains("Configurar Kit:")) {
            e.setCancelled(true);
            String k = editandoKit.get(p.getUniqueId());
            FileConfiguration cf = getKitConfig(k);
            switch(e.getRawSlot()) {
                case 10: abrirEditorDeItems(p, k); return;
                case 11: p.closeInventory(); esperandoPrecio.put(p.getUniqueId(), k); p.sendMessage(c("&eEscribe el precio:")); return;
                case 13: cf.set("requiere-permiso", !cf.getBoolean("requiere-permiso")); break;
                case 15: cf.set("auto-equip", !cf.getBoolean("auto-equip", false)); break;
                case 31: abrirPanelAdmin(p); return;
                case 35: abrirConfirmacionEliminar(p, k); return;
            }
            guardarKit(k, cf);
            abrirConfigKit(p, k);
        }

        if (t.contains("Editando Items:")) {
            if (e.getRawSlot() == 40) {
                guardarItemsDelMenu(e.getInventory(), editandoKit.get(p.getUniqueId()));
                abrirConfigKit(p, editandoKit.get(p.getUniqueId()));
            }
        }
    }

    public void darKit(Player p, String kitName) {
        FileConfiguration cf = getKitConfig(kitName);
        if (checkCooldown(p, kitName, cf.getInt("cooldown"))) return;

        double precio = cf.getDouble("precio", 0);
        if (precio > 0 && econ != null) {
            if (econ.getBalance(p) < precio) {
                p.sendMessage(c(mensajes.getString("mensajes.sin-dinero").replace("{precio}", String.valueOf(precio))));
                return;
            }
            econ.withdrawPlayer(p, precio);
        }

        boolean autoEquip = cf.getBoolean("auto-equip", false);
        List<?> items = cf.getList("items");
        
        if (items != null) {
            for (Object itemObj : items) {
                if (itemObj instanceof ItemStack) {
                    ItemStack item = (ItemStack) itemObj;
                    if (autoEquip && esArmadura(item.getType())) {
                        equiparOAdd(p, item);
                    } else {
                        if (p.getInventory().firstEmpty() == -1) p.getWorld().dropItemNaturally(p.getLocation(), item);
                        else p.getInventory().addItem(item);
                    }
                }
            }
        }
        p.sendMessage(c(mensajes.getString("mensajes.recibido").replace("{kit}", kitName)));
        
        // CORRECCIÓN: Usar un sonido genérico compatible
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }

    private boolean esArmadura(Material m) {
        String n = m.name();
        return n.contains("HELMET") || n.contains("CHESTPLATE") || n.contains("LEGGINGS") || n.contains("BOOTS");
    }

    private void equiparOAdd(Player p, ItemStack item) {
        String n = item.getType().name();
        PlayerInventory inv = p.getInventory();
        if (n.contains("HELMET") && inv.getHelmet() == null) inv.setHelmet(item);
        else if (n.contains("CHESTPLATE") && inv.getChestplate() == null) inv.setChestplate(item);
        else if (n.contains("LEGGINGS") && inv.getLeggings() == null) inv.setLeggings(item);
        else if (n.contains("BOOTS") && inv.getBoots() == null) inv.setBoots(item);
        else inv.addItem(item);
    }

    private boolean checkCooldown(Player p, String kit, int segs) {
        if (p.hasPermission("akits.admin")) return false;
        File f = new File(getDataFolder(), "data/" + p.getUniqueId() + ".yml");
        FileConfiguration data = YamlConfiguration.loadConfiguration(f);
        long ahora = System.currentTimeMillis();
        long resta = (data.getLong(kit, 0) + (segs * 1000L)) - ahora;
        if (resta > 0) {
            p.sendMessage(c(mensajes.getString("mensajes.cooldown").replace("{tiempo}", String.valueOf(resta/1000))));
            return true;
        }
        data.set(kit, ahora);
        try { data.save(f); } catch (IOException e) { e.printStackTrace(); }
        return false;
    }

    public void abrirMenuKits(Player p) {
        String cat = categoriaActual.getOrDefault(p.getUniqueId(), "GRATIS");
        Inventory inv = Bukkit.createInventory(null, 54, c(lang.getString("menus.principal-titulo").replace("{categoria}", cat)));
        inv.setItem(49, createItem(Material.NETHER_STAR, c(lang.getString("items.cambiar-categoria")), true));
        File folder = new File(getDataFolder(), "kits");
        File[] kits = folder.listFiles();
        if (kits != null) {
            for (File f : kits) {
                if (!f.getName().endsWith(".yml")) continue;
                String n = f.getName().replace(".yml", "");
                FileConfiguration conf = YamlConfiguration.loadConfiguration(f);
                if (conf.getBoolean("requiere-permiso") == cat.equals("PREMIUM")) {
                    Material icon = Material.getMaterial(conf.getString("icono", "CHEST"));
                    if (icon == null) icon = Material.CHEST;
                    inv.addItem(createItem(icon, c("&bKit: " + n), false, "&7Precio: &a$" + conf.getDouble("precio")));
                }
            }
        }
        p.openInventory(inv);
    }

    public void abrirConfirmacion(Player p, String k) {
        pendienteConfirmar.put(p.getUniqueId(), k);
        Inventory inv = Bukkit.createInventory(null, 27, c("&8¿Confirmar compra?"));
        inv.setItem(11, createItem(Material.LIME_DYE, "&a&lCONFIRMAR", true));
        inv.setItem(15, createItem(Material.RED_DYE, "&c&lCANCELAR", false));
        p.openInventory(inv);
    }

    public void abrirPanelAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c("ADMIN - Kits"));
        inv.setItem(4, createItem(Material.NETHER_STAR, "&b&l[+] CREAR KIT", true));
        File folder = new File(getDataFolder(), "kits");
        File[] files = folder.listFiles();
        if (files != null) {
            int s = 9;
            for (File f : files) {
                if (f.getName().endsWith(".yml") && s < 54) {
                    inv.setItem(s++, createItem(Material.PAPER, "&eEditar: " + f.getName().replace(".yml", ""), false));
                }
            }
        }
        p.openInventory(inv);
    }

    public void abrirConfigKit(Player p, String k) {
        editandoKit.put(p.getUniqueId(), k);
        FileConfiguration cf = getKitConfig(k);
        Inventory inv = Bukkit.createInventory(null, 45, c("&0Configurar Kit: " + k));
        
        inv.setItem(10, createItem(Material.CHEST, "&6&l1. &eEditar Items", true));
        inv.setItem(11, createItem(Material.SUNFLOWER, "&e&l2. &ePrecio: &a$" + cf.getDouble("precio"), false));
        inv.setItem(13, createItem(Material.IRON_DOOR, "&d&l3. &eRequiere Permiso", cf.getBoolean("requiere-permiso")));
        inv.setItem(15, createItem(Material.ARMOR_STAND, "&a&l4. &eAuto-Equipar", cf.getBoolean("auto-equip", false), "&7Estado: " + (cf.getBoolean("auto-equip") ? "&aON" : "&cOFF")));
        
        inv.setItem(31, createItem(Material.ARROW, "&cVolver", false));
        inv.setItem(35, createItem(Material.BARRIER, "&4&lELIMINAR", true));
        p.openInventory(inv);
    }

    public void abrirEditorDeItems(Player p, String k) {
        Inventory inv = Bukkit.createInventory(null, 45, c("&0Editando Items: " + k));
        FileConfiguration cf = getKitConfig(k);
        List<?> items = cf.getList("items");
        if (items != null) { int s = 0; for (Object i : items) if (s < 36 && i instanceof ItemStack) inv.setItem(s++, (ItemStack) i); }
        inv.setItem(40, createItem(Material.LIME_DYE, "&aGUARDAR", true));
        p.openInventory(inv);
    }

    public void abrirConfirmacionEliminar(Player p, String k) {
        pendienteEliminar.put(p.getUniqueId(), k);
        Inventory inv = Bukkit.createInventory(null, 27, c("¿Eliminar Kit?"));
        inv.setItem(11, createItem(Material.LIME_DYE, "&aSI", true));
        inv.setItem(15, createItem(Material.RED_DYE, "&cNO", false));
        p.openInventory(inv);
    }

    public void abrirPreview(Player p, String k) {
        Inventory inv = Bukkit.createInventory(null, 45, c("&0Vista: " + k));
        List<?> items = getKitConfig(k).getList("items");
        if (items != null) for (Object i : items) if (i instanceof ItemStack) inv.addItem((ItemStack) i);
        p.openInventory(inv);
    }

    private ItemStack createItem(Material m, String n, boolean glint, String... lore) {
        ItemStack i = new ItemStack(m != null ? m : Material.STONE);
        ItemMeta mt = i.getItemMeta();
        if (mt != null) {
            mt.setDisplayName(c(n));
            if (lore.length > 0) {
                List<String> l = new ArrayList<>();
                for (String s : lore) l.add(c(s));
                mt.setLore(l);
            }
            if (glint) {
                // CORRECCIÓN: Usar Enchantment.PROTECTION para mayor compatibilidad
                mt.addEnchant(Enchantment.PROTECTION, 1, true);
                mt.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            i.setItemMeta(mt);
        }
        return i;
    }

    private void guardarItemsDelMenu(Inventory inv, String kit) {
        FileConfiguration cf = getKitConfig(kit);
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 36; i++) if (inv.getItem(i) != null) items.add(inv.getItem(i));
        cf.set("items", items);
        guardarKit(kit, cf);
    }

    public FileConfiguration getKitConfig(String n) { return YamlConfiguration.loadConfiguration(new File(getDataFolder() + "/kits", n + ".yml")); }
    public void guardarKit(String n, FileConfiguration conf) { try { conf.save(new File(getDataFolder() + "/kits", n + ".yml")); } catch (IOException e) { e.printStackTrace(); } }

    @EventHandler
    public void alHablar(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (esperandoNombre.containsKey(p.getUniqueId())) {
            e.setCancelled(true); esperandoNombre.remove(p.getUniqueId());
            String n = e.getMessage().replace(" ", "_");
            crearKitBase(n);
            Bukkit.getScheduler().runTask(this, () -> abrirConfigKit(p, n));
        }
        if (esperandoPrecio.containsKey(p.getUniqueId())) {
            e.setCancelled(true); String k = esperandoPrecio.remove(p.getUniqueId());
            try {
                double pr = Double.parseDouble(e.getMessage());
                FileConfiguration cf = getKitConfig(k); cf.set("precio", pr);
                guardarKit(k, cf);
                p.sendMessage(c("&aPrecio actualizado a " + pr));
            } catch (Exception ex) { p.sendMessage(c("&cNúmero inválido.")); }
            Bukkit.getScheduler().runTask(this, () -> abrirConfigKit(p, k));
        }
    }

    private void crearKitBase(String n) {
        File f = new File(getDataFolder() + "/kits", n + ".yml");
        FileConfiguration c = YamlConfiguration.loadConfiguration(f);
        c.set("precio", 0.0); c.set("cooldown", 3600); c.set("requiere-permiso", false);
        c.set("auto-equip", false);
        c.set("items", new ArrayList<ItemStack>());
        try { c.save(f); } catch (IOException e) { e.printStackTrace(); }
    }
}
