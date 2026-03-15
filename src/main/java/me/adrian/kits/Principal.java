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
        new File(getDataFolder(), "kits").mkdirs();
        new File(getDataFolder(), "userdata").mkdirs();
        cargarArchivosInternos();
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("akits").setExecutor(new KitsCommand());
        
        Bukkit.getConsoleSender().sendMessage(c("&#00fbff&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        Bukkit.getConsoleSender().sendMessage(c("&b&lAdvancedKits &#00ff88&lV3.0 | 12 FUNCIONES & COMANDOS"));
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
            defLang.set("titulos.exito-titulo", "&#00ff88&l¡KIT RECIBIDO!");
            try { defLang.save(fLang); } catch (IOException e) { e.printStackTrace(); }
        }
        if (!fMsgs.exists()) {
            YamlConfiguration defMsgs = new YamlConfiguration();
            defMsgs.set("mensajes.reload", "&a¡Configuración recargada!");
            defMsgs.set("mensajes.recibido-actionbar", "&#00ff88&l✔ &fKit entregado con éxito");
            defMsgs.set("mensajes.sin-dinero", "&c&l✘ &7No tienes dinero suficiente.");
            defMsgs.set("mensajes.sin-permiso", "&c&l✘ &7No tienes permiso (kits.premium.{kit})");
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

    // --- PANEL DE ADMINISTRACIÓN (12 FUNCIONES) ---
    public void abrirConfigKit(Player p, String k) {
        editandoKit.put(p.getUniqueId(), k);
        FileConfiguration cf = getKitConfig(k);
        Inventory inv = Bukkit.createInventory(null, 45, c("&0Configurar Kit: " + k));
        
        inv.setItem(10, createItem(Material.CHEST, "&61. Items del Kit", false, "&7Copia tu inventario actual"));
        inv.setItem(11, createItem(Material.SUNFLOWER, "&e2. Precio: &a$" + cf.getDouble("precio"), false, "&7L-Click +10 / R-Click -10"));
        inv.setItem(12, createItem(Material.CLOCK, "&b3. Cooldown: &f" + cf.getInt("cooldown") + "s", false, "&7L-Click +60s / R-Click -60s"));
        inv.setItem(13, createItem(Material.IRON_DOOR, "&d4. Tipo: " + (cf.getBoolean("requiere-permiso") ? "&aPREMIUM" : "&bGRATIS"), false, "&7Define si requiere permiso"));
        inv.setItem(14, createItem(Material.ITEM_FRAME, "&f5. Cambiar Icono", false, "&7Usa el item de tu mano"));
        inv.setItem(15, createItem(Material.ARMOR_STAND, "&a6. Auto-Equipar: " + (cf.getBoolean("auto-equip", true) ? "&aON" : "&cOFF"), false, "&7Equipa armadura al recibir"));
        inv.setItem(16, createItem(Material.BUCKET, "&c7. Limpiar Inv: " + (cf.getBoolean("clear-inv", false) ? "&aON" : "&cOFF"), false, "&7Borra inventario antes de dar kit"));
        inv.setItem(19, createItem(Material.PAPER, "&e8. Mensaje Privado", false, "&7Envía mensaje al jugador"));
        inv.setItem(20, createItem(Material.JUKEBOX, "&69. Sonido de Éxito", false, "&7Reproduce sonido al reclamar"));
        inv.setItem(21, createItem(Material.BLAZE_POWDER, "&d10. Partículas", false, "&7Efectos al reclamar"));
        inv.setItem(22, createItem(Material.COMMAND_BLOCK, "&b12. Comandos de Consola", true, "&7Ejecuta comandos al reclamar", "&8(Editar en el .yml del kit)"));
        inv.setItem(25, createItem(Material.BARRIER, "&4&l11. ELIMINAR KIT", true, "&c¡Borrar permanentemente!"));

        inv.setItem(40, createItem(Material.ARROW, "&cVolver al Panel", false));
        p.openInventory(inv);
    }

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
            if (e.getRawSlot() == 4) { crearKitBase("Kit_" + (new Random().nextInt(999))); abrirPanelAdmin(p); }
            else if (e.getRawSlot() >= 9) {
                String k = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("Editar: ", "");
                abrirConfigKit(p, k);
            }
        }
        else if (t.contains("Configurar Kit:")) {
            e.setCancelled(true);
            String k = editandoKit.get(p.getUniqueId());
            FileConfiguration cf = getKitConfig(k);
            
            switch(e.getRawSlot()) {
                case 10: 
                    List<ItemStack> items = new ArrayList<>();
                    for (ItemStack i : p.getInventory().getContents()) if (i != null) items.add(i);
                    cf.set("items", items); p.sendMessage(c("&a¡Items guardados!")); break;
                case 11: cf.set("precio", e.isLeftClick() ? cf.getDouble("precio") + 10 : Math.max(0, cf.getDouble("precio") - 10)); break;
                case 12: cf.set("cooldown", e.isLeftClick() ? cf.getInt("cooldown") + 60 : Math.max(0, cf.getInt("cooldown") - 60)); break;
                case 13: cf.set("requiere-permiso", !cf.getBoolean("requiere-permiso")); break;
                case 14: 
                    if (p.getInventory().getItemInMainHand().getType() != Material.AIR) {
                        cf.set("icono", p.getInventory().getItemInMainHand().getType().toString());
                    } break;
                case 15: cf.set("auto-equip", !cf.getBoolean("auto-equip", true)); break;
                case 16: cf.set("clear-inv", !cf.getBoolean("clear-inv", false)); break;
                case 22: p.sendMessage(c("&b[!] &7Para añadir comandos, edita el archivo &f" + k + ".yml &7en la sección 'comandos'.")); break;
                case 25: new File(getDataFolder() + "/kits", k + ".yml").delete(); abrirPanelAdmin(p); return;
                case 40: abrirPanelAdmin(p); return;
            }
            guardarKit(k, cf); abrirConfigKit(p, k);
        }
    }

    private void darKit(Player p, String k) {
        FileConfiguration cf = getKitConfig(k);
        if (cf == null) return;
        
        if (cf.getBoolean("requiere-permiso") && !p.hasPermission("kits.premium." + k)) {
            p.sendMessage(c(mensajes.getString("mensajes.sin-permiso").replace("{kit}", k))); return;
        }
        
        double precio = cf.getDouble("precio");
        if (precio > 0 && (econ != null && econ.getBalance(p) < precio)) {
            p.sendMessage(c(mensajes.getString("mensajes.sin-dinero"))); return;
        }

        if (precio > 0) econ.withdrawPlayer(p, precio);
        if (cf.getBoolean("clear-inv")) p.getInventory().clear();

        // Entrega de Items
        for (Object o : cf.getList("items")) {
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

        // --- FUNCIÓN 12: EJECUCIÓN DE COMANDOS ---
        List<String> comandos = cf.getStringList("comandos");
        for (String cmd : comandos) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
        }
        
        p.sendTitle(c(lang.getString("titulos.exito-titulo")), "", 10, 40, 10);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(c(mensajes.getString("mensajes.recibido-actionbar"))));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
    }

    // --- MÉTODOS AUXILIARES ---
    public void abrirMenuKits(Player p) {
        String cat = categoriaActual.getOrDefault(p.getUniqueId(), "GRATIS");
        Inventory inv = Bukkit.createInventory(null, 54, c(lang.getString("menus.principal-titulo").replace("{categoria}", cat)));
        ItemStack frame = createItem(Material.CYAN_STAINED_GLASS_PANE, " ", false);
        for (int i = 0; i < 54; i++) if (i < 9 || i > 44 || i % 9 == 0 || (i + 1) % 9 == 0) inv.setItem(i, frame);
        inv.setItem(49, createItem(Material.NETHER_STAR, c(lang.getString("items.cambiar-categoria")), true));
        File[] archivos = new File(getDataFolder(), "kits").listFiles();
        if (archivos != null) {
            for (File f : archivos) {
                String n = f.getName().replace(".yml", "");
                FileConfiguration conf = getKitConfig(n);
                if (conf.getBoolean("requiere-permiso") == cat.equals("PREMIUM")) {
                    inv.addItem(createItem(Material.getMaterial(conf.getString("icono", "STONE_SWORD")), 
                        c(lang.getString("items.kit-nombre").replace("{nombre}", n)), false, 
                        c(lang.getString("items.kit-lore-precio").replace("{precio}", String.valueOf(conf.getDouble("precio")))),
                        "&eClick: &bReclamar", "&7Derecho: &bPreview"));
                }
            }
        }
        p.openInventory(inv);
    }

    public void abrirPanelAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c("&0&lADMIN &8- &7Kits"));
        inv.setItem(4, createItem(Material.NETHER_STAR, "&b&l[+] CREAR KIT", true, "&7Crea un kit nuevo"));
        File[] archivos = new File(getDataFolder(), "kits").listFiles();
        if (archivos != null) {
            int slot = 9;
            for (File f : archivos) {
                if (slot > 44) break;
                inv.setItem(slot++, createItem(Material.PAPER, "&eEditar: &f" + f.getName().replace(".yml", ""), false, "&7Click para configurar"));
            }
        }
        p.openInventory(inv);
    }

    public void abrirPreview(Player p, String k) {
        FileConfiguration cf = getKitConfig(k);
        Inventory inv = Bukkit.createInventory(null, 45, c(lang.getString("menus.preview-titulo").replace("{kit}", k)));
        for (Object i : cf.getList("items")) if (i instanceof ItemStack) inv.addItem((ItemStack) i);
        p.openInventory(inv);
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

    private void crearKitBase(String n) {
        FileConfiguration c = new YamlConfiguration();
        c.set("precio", 0.0); c.set("cooldown", 0); c.set("requiere-permiso", false);
        c.set("icono", "STONE_SWORD"); c.set("auto-equip", true); c.set("clear-inv", false);
        c.set("comandos", new ArrayList<String>());
        c.set("items", new ArrayList<ItemStack>());
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
