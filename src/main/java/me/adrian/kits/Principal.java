package me.adrian.kits;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class Principal extends JavaPlugin implements Listener {

    private final String version = "1.9.0-HOLOGRAMS";
    private final Map<UUID, String> editandoKit = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("akits").setExecutor(new KitsCommand());
    }

    // --- COMANDOS ---
    public class KitsCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;

            if (args.length > 0) {
                // Comando para crear kit + holograma
                if (args[0].equalsIgnoreCase("create") && args.length > 1 && p.hasPermission("kitsadvanced.admin")) {
                    crearNuevoKit(args[1], p.getLocation());
                    p.sendMessage(color("&a&l✔ &fKit '&e" + args[1] + "&f' creado con un holograma en tu posición."));
                    return true;
                }
                
                if (args[0].equalsIgnoreCase("edit") && args.length > 1 && p.hasPermission("kitsadvanced.admin")) {
                    if (getConfig().contains("kits." + args[1])) abrirOpcionesKit(p, args[1]);
                    return true;
                }
            }
            abrirMenuGlobal(p);
            return true;
        }
    }

    // --- CREAR KIT Y HOLOGRAMA ---
    private void crearNuevoKit(String nombre, Location loc) {
        getConfig().set("kits." + nombre + ".nombre", nombre);
        getConfig().set("kits." + nombre + ".requiere-permiso", false);
        getConfig().set("kits." + nombre + ".items", new ArrayList<ItemStack>());
        saveConfig();

        // Crear el Holograma (ArmorStand invisible)
        crearHolograma(loc, nombre);
    }

    private void crearHolograma(Location loc, String nombreKit) {
        ArmorStand holo = (ArmorStand) loc.getWorld().spawnEntity(loc.add(0, 1, 0), EntityType.ARMOR_STAND);
        holo.setVisible(false);
        holo.setGravity(false);
        holo.setCustomNameVisible(true);
        holo.setBasePlate(false);
        holo.setArms(false);
        
        // El nombre que verán los jugadores
        holo.setCustomName(color("&b&lKIT: &f" + nombreKit + " &7(Click para abrir)"));
        
        // Guardamos que este ArmorStand pertenece a este kit
        holo.addScoreboardTag("kit_hologram");
        holo.addScoreboardTag(nombreKit);
    }

    // --- DETECTAR CLICK EN EL HOLOGRAMA ---
    @EventHandler
    public void alTocarHolograma(PlayerInteractAtEntityEvent e) {
        Entity entidad = e.getRightClicked();
        if (entidad.getScoreboardTags().contains("kit_hologram")) {
            e.setCancelled(true);
            // Buscar el tag con el nombre del kit
            for (String tag : entidad.getScoreboardTags()) {
                if (!tag.equals("kit_hologram")) {
                    darKit(e.getPlayer(), tag);
                    break;
                }
            }
        }
    }

    // --- LÓGICA DE MENÚS (Lo que ya tenías optimizado) ---
    public void abrirMenuGlobal(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&0Kits Disponibles"));
        ConfigurationSection section = getConfig().getConfigurationSection("kits");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                boolean prem = getConfig().getBoolean("kits." + key + ".requiere-permiso", false);
                inv.addItem(createItem(prem ? Material.NETHER_STAR : Material.CHEST, 
                    (prem ? "&6&lPREMIUM &f" : "&a&lGRATIS &f") + key, "&7Click para reclamar."));
            }
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void alHacerClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.getCurrentItem() == null) return;
        Player p = (Player) e.getWhoClicked();
        String titulo = ChatColor.stripColor(e.getView().getTitle());

        if (titulo.equals("Kits Disponibles")) {
            e.setCancelled(true);
            String idReal = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName())
                    .replace("GRATIS ", "").replace("PREMIUM ", "");
            darKit(p, idReal);
        }

        if (titulo.startsWith("Opciones: ")) {
            e.setCancelled(true);
            String idKit = editandoKit.get(p.getUniqueId());
            if (e.getRawSlot() == 11) abrirCofreEditor(p, idKit);
            if (e.getRawSlot() == 13) {
                boolean act = !getConfig().getBoolean("kits." + idKit + ".requiere-permiso", false);
                getConfig().set("kits." + idKit + ".requiere-permiso", act);
                saveConfig();
                abrirOpcionesKit(p, idKit);
            }
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
            e.getPlayer().sendMessage(color("&a&l✔ &fItems de &e" + idKit + " &fguardados."));
        }
    }

    private void darKit(Player p, String idKit) {
        boolean req = getConfig().getBoolean("kits." + idKit + ".requiere-permiso", false);
        if (req && !p.hasPermission("kitsadvanced.kit." + idKit)) {
            p.sendMessage(color("&c&l✘ &cNo tienes permiso."));
            return;
        }
        List<?> items = getConfig().getList("kits." + idKit + ".items");
        if (items != null && !items.isEmpty()) {
            for (Object item : items) if (item instanceof ItemStack) p.getInventory().addItem((ItemStack) item);
            p.sendMessage(color("&a&l✔ &fKit &e" + idKit + " &fentregado."));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
        } else {
            p.sendMessage(color("&c&l✘ &cEste kit está vacío."));
        }
    }

    public void abrirOpcionesKit(Player p, String idKit) {
        editandoKit.put(p.getUniqueId(), idKit);
        Inventory inv = Bukkit.createInventory(null, 27, color("&0Opciones: &l" + idKit));
        inv.setItem(11, createItem(Material.CHEST, "&6&lGESTIONAR ÍTEMS", "&7Mete los ítems aquí.", "&eSe guarda al cerrar."));
        boolean req = getConfig().getBoolean("kits." + idKit + ".requiere-permiso", false);
        inv.setItem(13, createItem(req ? Material.LIME_WOOL : Material.RED_WOOL, 
            req ? "&a&lPERMISO: SI" : "&c&lPERMISO: NO", "&7Click para cambiar."));
        p.openInventory(inv);
    }

    private void abrirCofreEditor(Player p, String idKit) {
        Inventory editor = Bukkit.createInventory(null, 36, color("&0Editor: " + idKit));
        List<?> items = getConfig().getList("kits." + idKit + ".items");
        if (items != null) for (Object item : items) if (item instanceof ItemStack) editor.addItem((ItemStack) item);
        p.openInventory(editor);
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
