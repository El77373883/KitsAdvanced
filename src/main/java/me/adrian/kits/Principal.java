package me.adrian.kits;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class Principal extends JavaPlugin implements Listener {

    private final Map<UUID, String> editandoKit = new HashMap<>();
    private final Map<UUID, String> esperandoNombre = new HashMap<>();
    private final Map<UUID, String> esperandoTiempo = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getConsoleSender().sendMessage(color("&b&l[KitsAdvanced] &fEsperando testeo... ¡Todo cargado!"));
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("akits").setExecutor(new KitsCommand());
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) new KitsPlaceholders().register();
    }

    private String getMsg(String ruta) {
        String lang = getConfig().getString("idioma", "es");
        String prefix = getConfig().getString("mensajes." + lang + ".prefix", "");
        String mensaje = getConfig().getString("mensajes." + lang + "." + ruta, "");
        return color(prefix + mensaje);
    }

    public class KitsCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
            if (!(s instanceof Player)) return true;
            Player p = (Player) s;

            if (a.length == 0) { abrirMenuKits(p); return true; }

            if (a[0].equalsIgnoreCase("help") || (a.length > 1 && a[0].equalsIgnoreCase("list") && a[1].equalsIgnoreCase("command"))) {
                enviarAyuda(p); return true;
            }

            if (p.hasPermission("kitsadvanced.admin")) {
                if (a[0].equalsIgnoreCase("create") && a.length > 1) {
                    crearNuevoKit(a[1].toLowerCase());
                    p.sendMessage(getMsg("kit-created").replace("%kit%", a[1]));
                    return true;
                }
                if (a[0].equalsIgnoreCase("panel")) { abrirPanelAdmin(p); return true; }
                if (a[0].equalsIgnoreCase("reload")) { reloadConfig(); p.sendMessage(getMsg("reload")); return true; }
            }
            return true;
        }
    }

    public void abrirMenuKits(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&0&lMenú de Kits"));
        ConfigurationSection sec = getConfig().getConfigurationSection("kits");

        if ((sec == null || sec.getKeys(false).isEmpty()) && p.hasPermission("kitsadvanced.admin")) {
            inv.setItem(22, createItem(Material.CHEST, "&b&l¡CREA TU PRIMER KIT!", false, "&7No hay kits.", "&eClick para abrir el panel."));
        } else if (sec != null) {
            for (String k : sec.getKeys(false)) {
                boolean pre = getConfig().getBoolean("kits." + k + ".requiere-permiso");
                inv.addItem(createItem(pre ? Material.NETHER_STAR : Material.CHEST, (pre ? "&6&l" : "&a&l") + k, pre, 
                    "&7Estado: " + (pre ? "&6Premium" : "&aGratis"), p.hasPermission("kitsadvanced.admin") ? "&e(Click para editar)" : "&7Click para reclamar."));
            }
        }
        if (p.hasPermission("kitsadvanced.admin")) inv.setItem(49, createItem(Material.DIAMOND, "&b&lPANEL DE ADMIN", true, "&7Configuración total."));
        p.openInventory(inv);
    }

    public void abrirOpcionesKit(Player p, String k) {
        editandoKit.put(p.getUniqueId(), k);
        Inventory inv = Bukkit.createInventory(null, 27, color("&0Configurando: " + k));
        inv.setItem(10, createItem(Material.CHEST, "&6&lEDITAR ÍTEMS", false, "&7Click para editar contenido."));
        boolean pre = getConfig().getBoolean("kits." + k + ".requiere-permiso");
        inv.setItem(12, createItem(pre ? Material.LIME_WOOL : Material.RED_WOOL, "&eESTADO: " + (pre ? "&6PREMIUM" : "&aGRATIS"), pre, "&7Click para cambiar."));
        inv.setItem(14, createItem(Material.CLOCK, "&b&lCAMBIAR TIEMPO", false, "&7Actual: " + getConfig().getLong("kits." + k + ".tiempo") + " min"));
        inv.setItem(16, createItem(Material.NAME_TAG, "&d&lCAMBIAR NOMBRE", false, "&7Actual: " + k));
        p.openInventory(inv);
    }

    @EventHandler
    public void alHacerClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.getCurrentItem() == null) return;
        Player p = (Player) e.getWhoClicked();
        String t = ChatColor.stripColor(e.getView().getTitle());

        if (t.equals("Menú de Kits")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 22 || e.getRawSlot() == 49) { abrirPanelAdmin(p); return; }
            String k = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
            if (p.hasPermission("kitsadvanced.admin")) abrirOpcionesKit(p, k); else darKit(p, k);
        }

        if (t.startsWith("Configurando: ")) {
            e.setCancelled(true);
            String k = editandoKit.get(p.getUniqueId());
            if (e.getRawSlot() == 10) abrirCofreEditor(p, k);
            if (e.getRawSlot() == 12) {
                getConfig().set("kits." + k + ".requiere-permiso", !getConfig().getBoolean("kits." + k + ".requiere-permiso"));
                saveConfig(); abrirOpcionesKit(p, k);
            }
            if (e.getRawSlot() == 14) { p.closeInventory(); esperandoTiempo.put(p.getUniqueId(), k); p.sendMessage(color("&bEscribe el tiempo en minutos:")); }
            if (e.getRawSlot() == 16) { p.closeInventory(); esperandoNombre.put(p.getUniqueId(), k); p.sendMessage(color("&dEscribe el nuevo nombre:")); }
        }

        if (t.equals("Panel de Control")) {
            e.setCancelled(true);
            String k = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("Kit: ", "");
            abrirOpcionesKit(p, k);
        }
    }

    @EventHandler
    public void alChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (esperandoTiempo.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
            String k = esperandoTiempo.remove(p.getUniqueId());
            try { getConfig().set("kits." + k + ".tiempo", Long.parseLong(e.getMessage())); saveConfig(); } catch (Exception ex) {}
            Bukkit.getScheduler().runTask(this, () -> abrirOpcionesKit(p, k));
        }
        if (esperandoNombre.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
            String viejo = esperandoNombre.remove(p.getUniqueId());
            String nuevo = e.getMessage().toLowerCase();
            getConfig().set("kits." + nuevo, getConfig().getConfigurationSection("kits." + viejo));
            getConfig().set("kits." + viejo, null); saveConfig();
            Bukkit.getScheduler().runTask(this, () -> abrirOpcionesKit(p, nuevo));
        }
    }

    private void darKit(Player p, String k) {
        if (getConfig().getBoolean("kits." + k + ".requiere-permiso") && !p.hasPermission("advancedkits.kits." + k)) {
            p.sendMessage(getMsg("no-permission")); return;
        }
        long rest = (getConfig().getLong("usuarios." + p.getUniqueId() + "." + k, 0) + (getConfig().getLong("kits." + k + ".tiempo", 0) * 60000)) - System.currentTimeMillis();
        if (rest > 0) { p.sendMessage(getMsg("wait").replace("%time%", (rest / 60000) + "m")); return; }
        List<?> items = getConfig().getList("kits." + k + ".items");
        if (items != null) {
            for (Object i : items) if (i instanceof ItemStack) p.getInventory().addItem((ItemStack) i);
            p.sendMessage(getMsg("kit-received").replace("%kit%", k));
            getConfig().set("usuarios." + p.getUniqueId() + "." + k, System.currentTimeMillis()); saveConfig();
        }
    }

    private void crearNuevoKit(String n) {
        getConfig().set("kits." + n + ".requiere-permiso", false);
        getConfig().set("kits." + n + ".tiempo", 0);
        getConfig().set("kits." + n + ".items", new ArrayList<ItemStack>());
        saveConfig();
    }

    public void abrirPanelAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&0Panel de Control"));
        ConfigurationSection sec = getConfig().getConfigurationSection("kits");
        if (sec != null) for (String k : sec.getKeys(false)) inv.addItem(createItem(Material.CHEST, "&bKit: &f" + k, false, "&7Click para opciones."));
        p.openInventory(inv);
    }

    private void abrirCofreEditor(Player p, String k) {
        Inventory ed = Bukkit.createInventory(null, 36, color("&0Editor: " + k));
        List<?> items = getConfig().getList("kits." + k + ".items");
        if (items != null) for (Object i : items) if (i instanceof ItemStack) ed.addItem((ItemStack) i);
        p.openInventory(ed);
    }

    @EventHandler
    public void alCerrar(InventoryCloseEvent e) {
        if (ChatColor.stripColor(e.getView().getTitle()).startsWith("Editor: ")) {
            String k = ChatColor.stripColor(e.getView().getTitle()).replace("Editor: ", "");
            List<ItemStack> c = new ArrayList<>();
            for (ItemStack i : e.getInventory().getContents()) if (i != null && i.getType() != Material.AIR) c.add(i);
            getConfig().set("kits." + k + ".items", c); saveConfig();
        }
    }

    private void enviarAyuda(Player p) {
        p.sendMessage(color("&b&l--- Ayuda ---"));
        p.sendMessage(color("&f/akits &7- Menú."));
        if (p.hasPermission("kitsadvanced.admin")) {
            p.sendMessage(color("&b/akits create <nombre> &7- Crear."));
            p.sendMessage(color("&b/akits panel &7- Panel."));
        }
    }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    private ItemStack createItem(Material m, String n, boolean brillo, String... lore) {
        ItemStack i = new ItemStack(m); ItemMeta mt = i.getItemMeta();
        mt.setDisplayName(color(n)); mt.setLore(Arrays.asList(lore));
        if (brillo) { mt.addEnchant(Enchantment.DURABILITY, 1, true); mt.addItemFlags(ItemFlag.HIDE_ENCHANTS); }
        i.setItemMeta(mt); return i;
    }

    public class KitsPlaceholders extends PlaceholderExpansion {
        @Override public @NotNull String getIdentifier() { return "akits"; }
        @Override public @NotNull String getAuthor() { return "Adrian"; }
        @Override public @NotNull String getVersion() { return "4.5"; }
        @Override public boolean persist() { return true; }
        @Override public String onPlaceholderRequest(Player p, @NotNull String params) { return null; }
    }
}
