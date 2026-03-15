package me.adrian.kits;

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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class Principal extends JavaPlugin implements Listener {

    private final String version = "2.0.0-CLEAN";
    private final Map<UUID, String> editandoKit = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("akits").setExecutor(new KitsCommand());
        
        Bukkit.getConsoleSender().sendMessage(color("&b&l&m▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        Bukkit.getConsoleSender().sendMessage(color("&b   SISTEMA DE KITS: VERSIÓN LIMPIA CARGADA"));
        Bukkit.getConsoleSender().sendMessage(color("&f         Soporte Total: &eJava &f& &eBedrock"));
        Bukkit.getConsoleSender().sendMessage(color("&b&l&m▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
    }

    public class KitsCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;

            if (args.length > 0) {
                // /akits edit <nombre> - Acceso directo
                if (args[0].equalsIgnoreCase("edit") && args.length > 1 && p.hasPermission("kitsadvanced.admin")) {
                    if (getConfig().contains("kits." + args[1])) {
                        abrirOpcionesKit(p, args[1]);
                    } else {
                        p.sendMessage(color("&c&l✘ &cEl kit '&e" + args[1] + "&c' no existe."));
                    }
                    return true;
                }
                // /akits create <nombre>
                if (args[0].equalsIgnoreCase("create") && args.length > 1 && p.hasPermission("kitsadvanced.admin")) {
                    crearNuevoKit(args[1]);
                    p.sendMessage(color("&a&l✔ &fKit '&e" + args[1] + "&f' creado e inyectado al menú principal."));
                    return true;
                }
                // /akits panel
                if (args[0].equalsIgnoreCase("panel") && p.hasPermission("kitsadvanced.admin")) {
                    abrirListaKitsAdmin(p);
                    return true;
                }
            }
            
            // Menú principal dinámico
            abrirMenuGlobal(p);
            return true;
        }
    }

    // --- MENÚ PARA USUARIOS (TODOS AFUERA) ---
    public void abrirMenuGlobal(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&0Kits Disponibles"));
        ConfigurationSection section = getConfig().getConfigurationSection("kits");

        if (section != null) {
            for (String key : section.getKeys(false)) {
                boolean esPremium = getConfig().getBoolean("kits." + key + ".requiere-permiso", false);
                Material icono = esPremium ? Material.NETHER_STAR : Material.CHEST;
                String display = esPremium ? "&6&lPREMIUM &f" + key : "&a&lGRATIS &f" + key;
                
                inv.addItem(createItem(icono, display, 
                    "&7Tipo: " + (esPremium ? "&6Premium" : "&aGratis"),
                    "&7Click para reclamar."));
            }
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void alHacerClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.getCurrentItem() == null) return;
        Player p = (Player) e.getWhoClicked();
        String titulo = ChatColor.stripColor(e.getView().getTitle());

        // Menú de Reclamo
        if (titulo.equals("Kits Disponibles")) {
            e.setCancelled(true);
            String display = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
            String idReal = display.replace("GRATIS ", "").replace("PREMIUM ", "");
            darKit(p, idReal);
        }

        // Panel de Configuración
        if (titulo.startsWith("Configurar: ")) {
            e.setCancelled(true);
            String idKit = editandoKit.get(p.getUniqueId());
            if (e.getRawSlot() == 11) abrirCofreEditor(p, idKit);
            if (e.getRawSlot() == 13) {
                boolean act = !getConfig().getBoolean("kits." + idKit + ".requiere-permiso", false);
                getConfig().set("kits." + idKit + ".requiere-permiso", act);
                saveConfig();
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
                abrirOpcionesKit(p, idKit);
            }
            if (e.getRawSlot() == 22) abrirListaKitsAdmin(p);
        }
    }

    @EventHandler
    public void alCerrar(InventoryCloseEvent e) {
        String titulo = ChatColor.stripColor(e.getView().getTitle());
        if (titulo.startsWith("Editor: ")) {
            String idKit = titulo.replace("Editor: ", "");
            List<ItemStack> contenido = new ArrayList<>();
            for (ItemStack item : e.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR) contenido.add(item);
            }
            getConfig().set("kits." + idKit + ".items", contenido);
            saveConfig();
            e.getPlayer().sendMessage(color("&a&l✔ &fLos ítems de &e" + idKit + " &fse han guardado automáticamente."));
        }
    }

    private void darKit(Player p, String idKit) {
        boolean req = getConfig().getBoolean("kits." + idKit + ".requiere-permiso", false);
        if (req && !p.hasPermission("kitsadvanced.kit." + idKit)) {
            p.sendMessage(color("&c&l✘ &cNo tienes el permiso: &e" + "kitsadvanced.kit." + idKit));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return;
        }

        List<?> items = getConfig().getList("kits." + idKit + ".items");
        if (items == null || items.isEmpty()) {
            p.sendMessage(color("&c&l✘ &cEste kit no tiene ítems configurados."));
            return;
        }

        for (Object item : items) {
            if (item instanceof ItemStack) p.getInventory().addItem((ItemStack) item);
        }
        p.sendMessage(color("&a&l✔ &f¡Recibiste el kit &e" + idKit + "&f!"));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
        p.closeInventory();
    }

    public void abrirOpcionesKit(Player p, String idKit) {
        editandoKit.put(p.getUniqueId(), idKit);
        Inventory inv = Bukkit.createInventory(null, 27, color("&0Configurar: &l" + idKit));
        inv.setItem(11, createItem(Material.CHEST, "&6&lGESTIONAR ÍTEMS", "&7Click para editar contenido.", "&eSe guarda al cerrar."));
        
        boolean req = getConfig().getBoolean("kits." + idKit + ".requiere-permiso", false);
        inv.setItem(13, createItem(req ? Material.LIME_WOOL : Material.RED_WOOL, 
            req ? "&a&lPERMISO: ACTIVADO" : "&c&lPERMISO: DESACTIVADO", "&7Click para alternar acceso."));
            
        inv.setItem(22, createItem(Material.ARROW, "&cVolver al Panel"));
        p.openInventory(inv);
    }

    private void abrirCofreEditor(Player p, String idKit) {
        Inventory editor = Bukkit.createInventory(null, 36, color("&0Editor: " + idKit));
        List<?> items = getConfig().getList("kits." + idKit + ".items");
        if (items != null) for (Object item : items) if (item instanceof ItemStack) editor.addItem((ItemStack) item);
        p.openInventory(editor);
    }

    public void abrirListaKitsAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&8&lPANEL &7» &0Admin"));
        ConfigurationSection section = getConfig().getConfigurationSection("kits");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                inv.addItem(createItem(Material.CHEST, "&eEditar: &f" + key));
            }
        }
        p.openInventory(inv);
    }

    private void crearNuevoKit(String n) {
        getConfig().set("kits." + n + ".nombre", n);
        getConfig().set("kits." + n + ".requiere-permiso", false);
        getConfig().set("kits." + n + ".items", new ArrayList<ItemStack>());
        saveConfig();
    }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }
}
