package me.adrian.kits;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;

import java.util.*;

public class Principal extends JavaPlugin implements Listener {

    private final Map<UUID, String> esperandoChat = new HashMap<>();
    private List<ItemStack> itemsKitGuardados = new ArrayList<>();
    
    // Estados del Plugin
    private String nombreKitActivo = "Kit Inicial";
    private String tiempoKitActivo = "1h";
    private String categoriaSeleccionada = "DEFAULT";
    private boolean autoEquiparItems = true;
    private boolean permisosRequeridos = true;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("akits").setExecutor(new KitsCommand());
    }

    public class KitsCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;

            if (args.length > 0 && args[0].equalsIgnoreCase("panel")) {
                if (!p.hasPermission("kitsadvanced.admin")) {
                    enviarDenegacion(p, "&cNo tienes acceso al Panel Administrativo.");
                    return true;
                }
                abrirPanelAdmin(p);
                return true;
            }
            abrirMenuCategorias(p);
            return true;
        }
    }

    // --- MENÚS DE USUARIO ---
    public void abrirMenuCategorias(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&0Selecciona Categoría"));
        inv.setItem(11, createItem(Material.CHEST, "&a&lKITS GRATUITOS", "&7Acceso para todos los usuarios."));
        inv.setItem(15, createItem(Material.NETHER_STAR, "&6&lKITS PREMIUM", "&e&lEXCLUSIVO &7Requiere rango o dinero."));
        p.openInventory(inv);
    }

    public void abrirMenuKitsGratis(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&2Kits Gratuitos"));
        inv.setItem(13, createItem(Material.CHEST, "&a" + nombreKitActivo, "&7Duración: &e" + tiempoKitActivo, "", "&bClick Izquierdo: &fReclamar", "&bClick Derecho: &fVista Previa"));
        p.openInventory(inv);
    }

    // --- PANEL DE ADMINISTRACIÓN ---
    public void abrirPanelAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&8&lADMINISTRACIÓN KITS"));

        inv.setItem(10, createItem(Material.CLOCK, "&e&lTIEMPO", "&7Actual: &f" + tiempoKitActivo, "&bClick para editar en chat."));
        inv.setItem(11, createItem(Material.ANVIL, "&a&lNOMBRE", "&7Actual: &f" + nombreKitActivo, "&bClick para editar en chat."));
        
        // EDITOR VISUAL
        inv.setItem(13, createItem(Material.CHEST, "&6&lSUBIR ÍTEMS AL KIT", "&7Haz click para abrir el editor.", "&7Lo que metas aquí será el kit.", "", "&e¡Sin configurar códigos!"));

        String stEquip = autoEquiparItems ? "&aON" : "&cOFF";
        inv.setItem(14, createItem(Material.DIAMOND_CHESTPLATE, "&b&lAUTO-EQUIPAR", "&7Estado: " + stEquip));

        String stCat = (categoriaSeleccionada.equals("DEFAULT")) ? "&aGRATIS" : "&6PREMIUM";
        inv.setItem(15, createItem(Material.BOOK, "&d&lCATEGORÍA DEL KIT", "&7Actual: " + stCat, "&eClick para cambiar."));

        inv.setItem(16, createItem(Material.BARRIER, "&4&lRELOAD CONFIG", "&cClick para recargar."));

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
                for (ItemStack item : itemsKitGuardados) editor.addItem(item);
                p.openInventory(editor);
            } else if (m == Material.BOOK) {
                categoriaSeleccionada = categoriaSeleccionada.equals("DEFAULT") ? "PREMIUM" : "DEFAULT";
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
                abrirPanelAdmin(p);
            } else if (m == Material.CLOCK) {
                p.closeInventory();
                esperandoChat.put(p.getUniqueId(), "TIEMPO");
                p.sendMessage(color("&eEscribe el tiempo (ej: 1d 5h):"));
            } else if (m == Material.ANVIL) {
                p.closeInventory();
                esperandoChat.put(p.getUniqueId(), "NOMBRE");
                p.sendMessage(color("&aEscribe el nuevo nombre:"));
            }
        } 
        else if (titulo.equals(color("&0Selecciona Categoría"))) {
            e.setCancelled(true);
            if (e.getCurrentItem().getType() == Material.CHEST) abrirMenuKitsGratis(p);
            else if (e.getCurrentItem().getType() == Material.NETHER_STAR) {
                if (!p.hasPermission("kitsadvanced.premium")) {
                    enviarDenegacion(p, "&cNo tienes dinero o rango para entrar aquí.");
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
                if (item != null && item.getType() != Material.AIR) itemsKitGuardados.add(item);
            }
            e.getPlayer().sendMessage(color("&a&l¡SINCRONIZADO! &fLos ítems se han guardado en el kit."));
            e.getPlayer().getPointOfView(); // Trick to play sound
            ((Player) e.getPlayer()).playSound(e.getPlayer().getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
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
            esperandoChat.remove(p.getUniqueId());
            p.sendMessage(color("&a¡Cambio aplicado con éxito!"));
            Bukkit.getScheduler().runTask(this, () -> abrirPanelAdmin(p));
        }
    }

    private void enviarDenegacion(Player p, String msg) {
        p.sendMessage(color("&b&lKitsAdvanced &8» " + msg));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 0.5f);
        p.closeInventory();
    }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        List<String> l = new ArrayList<>();
        for (String line : lore) l.add(color(line));
        meta.setLore(l);
        item.setItemMeta(meta);
        return item;
    }
}
