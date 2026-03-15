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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class Principal extends JavaPlugin implements Listener {

    private final Map<UUID, String> editandoKit = new HashMap<>();
    private final Map<UUID, String> esperandoTiempoChat = new HashMap<>();

    @Override
    public void onEnable() {
        // Creamos la sección de configuración si no existe
        getConfig().addDefault("configuracion.idioma", "es");
        getConfig().options().copyDefaults(true);
        saveConfig();
        
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("akits").setExecutor(new KitsCommand());

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new KitsPlaceholders().register();
        }
    }

    // --- TRADUCTOR DINÁMICO (Lee directamente de la Config) ---
    private String getMsg(String ruta) {
        // Recarga el valor de la config en cada llamada para permitir cambios manuales en el archivo
        String lang = getConfig().getString("configuracion.idioma", "es").toLowerCase();
        boolean isEn = lang.equals("en");

        switch (ruta) {
            case "menu-titulo": return isEn ? "&0Select Category" : "&0Selecciona Categoría";
            case "cat-gratis": return isEn ? "&a&lFREE KITS" : "&a&lKITS GRATUITOS";
            case "cat-premium": return isEn ? "&6&lPREMIUM KITS" : "&6&lKITS PREMIUM";
            case "lore-gratis": return isEn ? "&7Access for everyone." : "&7Acceso para todos.";
            case "lore-premium": return isEn ? "&7Only for VIP ranks." : "&7Solo para rangos VIP.";
            case "back": return isEn ? "&cGo Back" : "&cVolver atrás";
            case "no-perm": return isEn ? "&c&l✘ &cNo permission." : "&c&l✘ &cSin permiso.";
            case "lang-changed": return isEn ? "&a&l✔ &fLanguage changed to &eEnglish&f." : "&a&l✔ &fIdioma cambiado a &eEspañol&f.";
            case "chat-ask": return isEn ? "&bType &lMINUTES &fin chat (or 'cancel')." : "&bEscribe los &lMINUTOS &fen el chat (o 'cancelar').";
            case "claimed": return isEn ? "&a&l✔ &fKit &e%k% &freceived!" : "&a&l✔ &f¡Kit &e%k% &frecibido!";
            case "wait": return isEn ? "&cWait &e%m%m %s%s" : "&cEspera &e%m%m %s%s";
            case "opt-title": return isEn ? "&0Options: " : "&0Opciones: ";
            case "opt-items": return isEn ? "&7Click to edit content." : "&7Click para editar contenido.";
            case "opt-cat": return isEn ? "&7Click to change category." : "&7Click para cambiar categoría.";
            case "opt-time": return isEn ? "&7Click to set time by chat." : "&7Click para poner tiempo por chat.";
            default: return "";
        }
    }

    public class KitsCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
            if (!(s instanceof Player)) return true;
            Player p = (Player) s;

            if (a.length > 0 && p.hasPermission("kitsadvanced.admin")) {
                // COMANDO: /akits lang <es/en> -> Esto edita la config.yml automáticamente
                if (a[0].equalsIgnoreCase("lang") && a.length > 1) {
                    String nuevoIdioma = a[1].toLowerCase();
                    if (nuevoIdioma.equals("es") || nuevoIdioma.equals("en")) {
                        getConfig().set("configuracion.idioma", nuevoIdioma);
                        saveConfig(); // Guarda el cambio en el archivo físico
                        p.sendMessage(color(getMsg("lang-changed")));
                        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1, 1);
                    } else {
                        p.sendMessage(color("&cUso/Use: /akits lang <es/en>"));
                    }
                    return true;
                }
                
                if (a[0].equalsIgnoreCase("edit") && a.length > 1) {
                    if (getConfig().contains("kits." + a[1])) abrirOpcionesKit(p, a[1]);
                    return true;
                }
                
                if (a[0].equalsIgnoreCase("create") && a.length > 1) {
                    crearNuevoKit(a[1]);
                    p.sendMessage(color("&a&l✔ &fKit &e" + a[1] + " &fcreado."));
                    return true;
                }
            }
            abrirMenuCategorias(p);
            return true;
        }
    }

    // --- MANEJO DE INVENTARIOS ---
    public void abrirMenuCategorias(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color(getMsg("menu-titulo")));
        inv.setItem(48, createItem(Material.CHEST, getMsg("cat-gratis"), getMsg("lore-gratis")));
        inv.setItem(50, createItem(Material.NETHER_STAR, getMsg("cat-premium"), getMsg("lore-premium")));
        p.openInventory(inv);
    }

    public void abrirMenuKits(Player p, boolean premium) {
        String titulo = premium ? getMsg("cat-premium") : getMsg("cat-gratis");
        Inventory inv = Bukkit.createInventory(null, 54, color(titulo));
        ConfigurationSection sec = getConfig().getConfigurationSection("kits");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                if (getConfig().getBoolean("kits." + k + ".requiere-permiso") == premium) {
                    String colorNombre = premium ? "&6&l" : "&a&l";
                    inv.addItem(createItem(premium ? Material.NETHER_STAR : Material.CHEST, colorNombre + k, "&7Click."));
                }
            }
        }
        inv.setItem(49, createItem(Material.ARROW, getMsg("back")));
        p.openInventory(inv);
    }

    @EventHandler
    public void alHacerClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.getCurrentItem() == null) return;
        Player p = (Player) e.getWhoClicked();
        String t = ChatColor.stripColor(e.getView().getTitle());

        // Navegación de categorías
        if (t.equals(ChatColor.stripColor(color(getMsg("menu-titulo"))))) {
            e.setCancelled(true);
            if (e.getRawSlot() == 48) abrirMenuKits(p, false);
            if (e.getRawSlot() == 50) abrirMenuKits(p, true);
        }

        // Navegación de kits
        if (t.contains("KITS")) { 
            e.setCancelled(true);
            if (e.getRawSlot() == 49) { abrirMenuCategorias(p); return; }
            darKit(p, ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()));
        }

        // Panel de Opciones
        if (t.startsWith(getMsg("opt-title"))) {
            e.setCancelled(true);
            String k = editandoKit.get(p.getUniqueId());
            if (e.getRawSlot() == 11) abrirCofreEditor(p, k);
            if (e.getRawSlot() == 13) {
                getConfig().set("kits." + k + ".requiere-permiso", !getConfig().getBoolean("kits." + k + ".requiere-permiso"));
                saveConfig(); abrirOpcionesKit(p, k);
            }
            if (e.getRawSlot() == 15) {
                p.closeInventory();
                esperandoTiempoChat.put(p.getUniqueId(), k);
                p.sendMessage(color(getMsg("chat-ask")));
            }
        }
    }

    // --- LÓGICA DE KITS ---
    private void darKit(Player p, String k) {
        long last = getConfig().getLong("usuarios." + p.getUniqueId() + "." + k, 0);
        long wait = getConfig().getLong("kits." + k + ".tiempo", 0) * 60000;
        long rest = (last + wait) - System.currentTimeMillis();

        if (rest > 0 && !p.hasPermission("kitsadvanced.admin")) {
            long m = TimeUnit.MILLISECONDS.toMinutes(rest);
            long s = TimeUnit.MILLISECONDS.toSeconds(rest) - TimeUnit.MINUTES.toSeconds(m);
            p.sendMessage(color(getMsg("wait").replace("%m%", String.valueOf(m)).replace("%s%", String.valueOf(s))));
            return;
        }

        if (getConfig().getBoolean("kits." + k + ".requiere-permiso") && !p.hasPermission("kitsadvanced.kit." + k)) {
            p.sendMessage(color(getMsg("no-perm"))); return;
        }

        List<?> items = getConfig().getList("kits." + k + ".items");
        if (items != null && !items.isEmpty()) {
            for (Object i : items) if (i instanceof ItemStack) p.getInventory().addItem((ItemStack) i);
            getConfig().set("usuarios." + p.getUniqueId() + "." + k, System.currentTimeMillis());
            saveConfig();
            p.sendMessage(color(getMsg("claimed").replace("%k%", k)));
            p.closeInventory();
        }
    }

    @EventHandler
    public void alEscribirEnChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (esperandoTiempoChat.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
            String k = esperandoTiempoChat.get(p.getUniqueId());
            try {
                long m = Long.parseLong(e.getMessage());
                getConfig().set("kits." + k + ".tiempo", m); saveConfig();
                esperandoTiempoChat.remove(p.getUniqueId());
                Bukkit.getScheduler().runTask(this, () -> abrirOpcionesKit(p, k));
            } catch (Exception ex) { p.sendMessage(color("&cError: Escribe un número / Type a number.")); }
        }
    }

    public void abrirOpcionesKit(Player p, String k) {
        editandoKit.put(p.getUniqueId(), k);
        Inventory inv = Bukkit.createInventory(null, 27, color(getMsg("opt-title") + k));
        
        List<?> i = getConfig().getList("kits." + k + ".items");
        inv.setItem(11, createItem(Material.CHEST, "&6&lITEMS", getMsg("opt-items"), "&7Total: &e" + (i != null ? i.size() : 0)));
        
        boolean pre = getConfig().getBoolean("kits." + k + ".requiere-permiso");
        inv.setItem(13, createItem(pre ? Material.LIME_WOOL : Material.RED_WOOL, "&e" + (pre ? "PREMIUM" : "FREE"), getMsg("opt-cat")));
        
        long t = getConfig().getLong("kits." + k + ".tiempo", 0);
        inv.setItem(15, createItem(Material.CLOCK, "&b&lCOOLDOWN", "&7" + t + " min", getMsg("opt-time")));
        
        p.openInventory(inv);
    }

    private void abrirCofreEditor(Player p, String k) {
        Inventory ed = Bukkit.createInventory(null, 36, color("Editor: " + k));
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

    private void crearNuevoKit(String n) {
        getConfig().set("kits." + n + ".requiere-permiso", false);
        getConfig().set("kits." + n + ".tiempo", 0);
        saveConfig();
    }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    private ItemStack createItem(Material m, String n, String... lore) {
        ItemStack i = new ItemStack(m); ItemMeta mt = i.getItemMeta();
        mt.setDisplayName(color(n)); mt.setLore(Arrays.asList(lore));
        i.setItemMeta(mt); return i;
    }

    // --- PLACEHOLDERS ---
    public class KitsPlaceholders extends PlaceholderExpansion {
        @Override public @NotNull String getIdentifier() { return "akits"; }
        @Override public @NotNull String getAuthor() { return "Adrian"; }
        @Override public @NotNull String getVersion() { return "1.8"; }
        @Override public boolean persist() { return true; }
        @Override public String onPlaceholderRequest(Player p, @NotNull String params) { return null; }
    }
}
