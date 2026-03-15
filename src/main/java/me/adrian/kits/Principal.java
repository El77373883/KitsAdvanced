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

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdir();
        File carpetaKits = new File(getDataFolder(), "kits");
        if (!carpetaKits.exists()) carpetaKits.mkdirs();

        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("akits").setExecutor(new KitsCommand());
    }

    private String c(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    public FileConfiguration getKitConfig(String nombre) {
        File file = new File(getDataFolder() + "/kits", nombre + ".yml");
        return YamlConfiguration.loadConfiguration(file);
    }

    public void guardarKit(String nombre, FileConfiguration config) {
        try {
            config.save(new File(getDataFolder() + "/kits", nombre + ".yml"));
        } catch (IOException e) { e.printStackTrace(); }
    }

    public class KitsCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
            if (!(s instanceof Player)) {
                if (a.length > 0 && a[0].equalsIgnoreCase("reload")) {
                    s.sendMessage(c("&a&l[!] &fKits recargados."));
                    return true;
                }
                return true;
            }
            Player p = (Player) s;
            if (a.length == 0) { abrirMenuKits(p); return true; }
            if (!p.hasPermission("kitsadvanced.admin")) return true;
            if (a[0].equalsIgnoreCase("reload")) {
                p.sendMessage(c("&a&l✔ &fArchivos .yml recargados."));
                return true;
            }
            if (a[0].equalsIgnoreCase("panel")) { abrirPanelAdmin(p); return true; }
            if (a[0].equalsIgnoreCase("edit") && a.length > 1) {
                if (new File(getDataFolder() + "/kits", a[1] + ".yml").exists()) abrirOpcionesKit(p, a[1]);
                else p.sendMessage(c("&cEl kit no existe."));
                return true;
            }
            return true;
        }
    }

    public void abrirMenuKits(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c("&0&lMENÚ DE KITS"));
        File carpeta = new File(getDataFolder(), "kits");
        File[] archivos = carpeta.listFiles((dir, name) -> name.endsWith(".yml"));

        if (archivos != null) {
            List<String> normales = new ArrayList<>();
            List<String> premium = new ArrayList<>();
            for (File f : archivos) {
                String nombre = f.getName().replace(".yml", "");
                FileConfiguration conf = getKitConfig(nombre);
                if (conf.getBoolean("requiere-permiso")) premium.add(nombre);
                else normales.add(nombre);
            }

            for (String k : normales) inv.addItem(createKitIcon(k));

            for (int i = 27; i <= 35; i++) inv.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, " ", false));
            inv.setItem(31, createItem(Material.DIAMOND_BLOCK, "&6&lKITS PREMIUM", true, "&7Categoría especial."));

            int slotPre = 36;
            for (String k : premium) {
                if (slotPre < 54) { inv.setItem(slotPre, createKitIcon(k)); slotPre++; }
            }
        }
        p.openInventory(inv);
    }

    private ItemStack createKitIcon(String nombreKit) {
        FileConfiguration conf = getKitConfig(nombreKit);
        Material icono = Material.valueOf(conf.getString("icono", "CHEST"));
        List<String> lore = new ArrayList<>();
        lore.add(c("&8&m-------------------"));
        lore.add(c("&6&lCONTENIDO:"));
        List<?> items = conf.getList("items");
        if (items != null && !items.isEmpty()) {
            for (int i = 0; i < Math.min(items.size(), 5); i++) {
                ItemStack it = (ItemStack) items.get(i);
                if (it == null) continue;
                String name = (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) 
                    ? it.getItemMeta().getDisplayName() : "&f" + it.getType().name().toLowerCase().replace("_", " ");
                lore.add(c(" &7• &f" + it.getAmount() + "x " + name));
            }
            if (items.size() > 5) lore.add(c(" &8y más..."));
        }
        lore.add(c("&8&m-------------------"));
        lore.add(c("&7Precio: &a$" + conf.getDouble("precio", 0.0)));
        lore.add(c("&e▶ Click para reclamar"));
        return createItem(icono, "&e&lKit: &f" + nombreKit, conf.getBoolean("requiere-permiso"), lore.toArray(new String[0]));
    }

    public void abrirPanelAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c("&0Gestión de Kits (.yml)"));
        inv.setItem(4, createItem(Material.NETHER_STAR, "&b&l[+] CREAR KIT", true, "&7Escribe el nombre en el chat."));
        File carpeta = new File(getDataFolder(), "kits");
        File[] archivos = carpeta.listFiles((dir, name) -> name.endsWith(".yml"));
        if (archivos != null) {
            for (File f : archivos) {
                inv.addItem(createItem(Material.PAPER, "&eArchivo: &f" + f.getName(), false, "&7Click: Editar", "&cQ: Eliminar"));
            }
        }
        p.openInventory(inv);
    }

    public void abrirOpcionesKit(Player p, String k) {
        editandoKit.put(p.getUniqueId(), k);
        FileConfiguration conf = getKitConfig(k);
        Inventory inv = Bukkit.createInventory(null, 45, c("&0Ajustes: &8" + k));
        boolean pre = conf.getBoolean("requiere-permiso");
        inv.setItem(11, createItem(Material.BEACON, "&e&lPremium", pre, "&7Estado: " + (pre ? "&aON" : "&cOFF")));
        inv.setItem(13, createItem(Material.CHEST, "&6&lItems", false, "&7Configura el contenido."));
        inv.setItem(40, createItem(Material.ARROW, "&c« Volver", false));
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
            if (e.getRawSlot() != 31 && e.getCurrentItem().getType() != Material.GRAY_STAINED_GLASS_PANE) {
                darKit(p, ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("Kit: ", ""));
            }
        } else if (t.equals("Gestión de Kits (.yml)")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 4) { 
                p.closeInventory(); modoChat.put(p.getUniqueId(), "CREAR"); 
                p.sendMessage(c("&b&l[+] &fEscribe el nombre:")); 
            } else if (e.getCurrentItem().getType() == Material.PAPER) {
                String kitName = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("Archivo: ", "").replace(".yml", "");
                if (e.getClick() == ClickType.DROP) {
                    new File(getDataFolder() + "/kits", kitName + ".yml").delete();
                    abrirPanelAdmin(p);
                } else abrirOpcionesKit(p, kitName);
            }
        } else if (t.startsWith("Ajustes:")) {
            e.setCancelled(true);
            FileConfiguration conf = getKitConfig(k);
            if (e.getRawSlot() == 11) { 
                conf.set("requiere-permiso", !conf.getBoolean("requiere-permiso")); 
                guardarKit(k, conf); abrirOpcionesKit(p, k); 
            }
            if (e.getRawSlot() == 13) abrirCofreEditor(p, k);
            if (e.getRawSlot() == 40) abrirPanelAdmin(p);
        }
    }

    @EventHandler
    public void alChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (modoChat.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
            String msg = e.getMessage().replace(" ", "_");
            Bukkit.getScheduler().runTask(this, () -> {
                FileConfiguration conf = getKitConfig(msg);
                conf.set("requiere-permiso", false);
                conf.set("precio", 0.0);
                conf.set("icono", "CHEST");
                conf.set("items", new ArrayList<ItemStack>());
                guardarKit(msg, conf);
                modoChat.remove(p.getUniqueId());
                abrirOpcionesKit(p, msg);
            });
        }
    }

    private void darKit(Player p, String k) {
        FileConfiguration conf = getKitConfig(k);
        if (conf.getBoolean("requiere-permiso") && !p.hasPermission("kits.use." + k)) {
            p.sendMessage(c("&c&l✘ &7Sin permiso para &f" + k));
            return;
        }
        List<?> items = conf.getList("items");
        if (items != null) {
            for (Object o : items) if (o instanceof ItemStack) p.getInventory().addItem((ItemStack) o);
            p.sendMessage(c("&a&l✔ &7Kit &f" + k + " &7equipado."));
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
            List<ItemStack> list = new ArrayList<>();
            for (ItemStack i : e.getInventory().getContents()) if (i != null && i.getType() != Material.AIR) list.add(i);
            conf.set("items", list);
            guardarKit(k, conf);
        }
    }

    private ItemStack createItem(Material m, String n, boolean glint, String... lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta mt = i.getItemMeta();
        if (mt != null) {
            mt.setDisplayName(c(n));
            List<String> l = new ArrayList<>();
            for (String s : lore) l.add(c(s));
            mt.setLore(l);
            if (glint) {
                // Aquí corregí el error DURABILITY -> UNBREAKING
                mt.addEnchant(Enchantment.UNBREAKING, 1, true);
                mt.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            i.setItemMeta(mt);
        }
        return i;
    }
}
