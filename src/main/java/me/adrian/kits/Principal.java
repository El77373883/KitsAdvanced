package me.adrian.kits;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.ChatColor;
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

import java.util.*;

public class Principal extends JavaPlugin implements Listener {

    private final Map<UUID, String> esperandoChat = new HashMap<>();
    private List<ItemStack> itemsKitGuardados = new ArrayList<>();
    
    private String nombreKitActivo;
    private String tiempoKitActivo;
    private String categoriaSeleccionada;
    private boolean autoEquiparItems;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cargarDatosDeConfig(); // Cargar todo al encender el server
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("akits") != null) {
            getCommand("akits").setExecutor(new KitsCommand());
        }
    }

    // --- LÓGICA DE CARGA ---
    private void cargarDatosDeConfig() {
        nombreKitActivo = getConfig().getString("kit.nombre", "Kit Inicial");
        tiempoKitActivo = getConfig().getString("kit.tiempo", "1h");
        categoriaSeleccionada = getConfig().getString("kit.categoria", "DEFAULT");
        autoEquiparItems = getConfig().getBoolean("kit.autoequip", true);
        
        // Cargar items (Spigot los guarda como una lista)
        if (getConfig().get("kit.items") != null) {
            itemsKitGuardados = (List<ItemStack>) getConfig().getList("kit.items");
        }
    }

    // --- LÓGICA DE GUARDADO ---
    private void guardarTodo() {
        getConfig().set("kit.nombre", nombreKitActivo);
        getConfig().set("kit.tiempo", tiempoKitActivo);
        getConfig().set("kit.categoria", categoriaSeleccionada);
        getConfig().set("kit.autoequip", autoEquiparItems);
        getConfig().set("kit.items", itemsKitGuardados);
        saveConfig();
    }

    public class KitsCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;

            if (args.length > 0 && args[0].equalsIgnoreCase("panel")) {
                if (!p.hasPermission("kitsadvanced.admin")) {
                    enviarDenegacion(p, "&cNo tienes acceso al Panel.");
                    return true;
                }
                abrirPanelAdmin(p);
                return true;
            }
            abrirMenuCategorias(p);
            return true;
        }
    }

    public void abrirMenuCategorias(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&0Selecciona Categoría"));
        inv.setItem(11, createItem(Material.CHEST, "&a&lKITS GRATUITOS", "&7Kits para todos."));
        inv.setItem(15, createItem(Material.NETHER_STAR, "&6&lKITS PREMIUM", "&e&lEXCLUSIVO &7Solo VIPs."));
        p.openInventory(inv);
    }

    public void abrirMenuKitsGratis(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&2Kits Gratuitos"));
        inv.setItem(13, createItem(Material.CHEST, "&a" + nombreKitActivo, "&7Duración: &e" + tiempoKitActivo));
        p.openInventory(inv);
    }

    public void abrirPanelAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&8&lADMINISTRACIÓN KITS"));
        inv.setItem(10, createItem(Material.CLOCK, "&e&lTIEMPO", "&7Actual: &f" + tiempoKitActivo));
        inv.setItem(11, createItem(Material.ANVIL, "&a&lNOMBRE", "&7Actual: &f" + nombreKitActivo));
        inv.setItem(13, createItem(Material.CHEST, "&6&lEDITAR ÍTEMS", "&7Click para meter cosas."));
        
        String stCat = (categoriaSeleccionada.equals("DEFAULT")) ? "&aGRATIS" : "&6PREMIUM";
        inv.setItem(15, createItem(Material.BOOK, "&d&lCATEGORÍA", "&7Actual: " + stCat));
        inv.setItem(16, createItem(Material.BARRIER, "&4&lRELOAD", "&cRecargar plugin."));
        p.openInventory(inv);
    }

    @EventHandler
    public void alHacerClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.getCurrentItem() == null) return;
        Player p = (Player) e.getWhoClicked();
        String titulo = e.getView().getTitle();

        if (titulo.equals(color("&8&lADMINISTRACIÓN KITS"))) {
            e.setCancelled(true);
            Material m = e.getCurrentItem().getType();
            if (m == Material.CHEST) {
                Inventory editor = Bukkit.createInventory(null, 27, color("&0Editor: Sube tus ítems"));
                for (ItemStack item : itemsKitGuardados) {
                    if (item != null) editor.addItem(item);
                }
                p.openInventory(editor);
            } else if (m == Material.BOOK) {
                categoriaSeleccionada = categoriaSeleccionada.equals("DEFAULT") ? "PREMIUM" : "DEFAULT";
                guardarTodo(); // Guardar cambio de categoría
                abrirPanelAdmin(p);
            } else if (m == Material.CLOCK) {
                p.closeInventory();
                esperandoChat.put(p.getUniqueId(), "TIEMPO");
                p.sendMessage(color("&eEscribe el tiempo:"));
            } else if (m == Material.ANVIL) {
                p.closeInventory();
                esperandoChat.put(p.getUniqueId(), "NOMBRE");
                p.sendMessage(color("&aEscribe el nombre:"));
            } else if (m == Material.BARRIER) {
                reloadConfig();
                cargarDatosDeConfig();
                p.sendMessage(color("&a¡Configuración recargada!"));
                p.closeInventory();
            }
        } 
        else if (titulo.equals(color("&0Selecciona Categoría"))) {
            e.setCancelled(true);
            if (e.getCurrentItem().getType() == Material.CHEST) abrirMenuKitsGratis(p);
            else if (e.getCurrentItem().getType() == Material.NETHER_STAR) {
                if (!p.hasPermission("kitsadvanced.premium")) {
                    enviarDenegacion(p, "&cNo tienes permiso.");
                } else {
                    p.sendMessage(color("&6Abriendo zona Premium..."));
                }
            }
        }
    }

    @EventHandler
    public void alCerrarEditor(InventoryCloseEvent e) {
        if (e.getView().getTitle().equals(color("&0Editor: Sube tus ítems"))) {
            itemsKitGuardados.clear();
            for (ItemStack item : e.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    itemsKitGuardados.add(item);
                }
            }
            guardarTodo(); // <--- AQUÍ SE GUARDA EN EL ARCHIVO CONFIG.YML
            e.getPlayer().sendMessage(color("&a&l¡Sincronizado y Guardado en Config!"));
        }
    }

    @EventHandler
    public void alEscribirChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (esperandoChat.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
            String modo = esperandoChat.get(p.getUniqueId());
            if (modo.equals("NOMBRE")) nombreKitActivo = e.getMessage();
            else tiempoKitActivo = e.getMessage();
            
            guardarTodo(); // Guardar el nombre/tiempo nuevo en la config
            esperandoChat.remove(p.getUniqueId());
            Bukkit.getScheduler().runTask(this, () -> abrirPanelAdmin(p));
        }
    }

    private void enviarDenegacion(Player p, String msg) {
        p.sendMessage(color("&b&lKitsAdvanced &8» " + msg));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 0.5f);
        p.closeInventory();
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            List<String> l = new ArrayList<>();
            for (String line : lore) l.add(color(line));
            meta.setLore(l);
            item.setItemMeta(meta);
        }
        return item;
    }
}
