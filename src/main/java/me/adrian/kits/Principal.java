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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class Principal extends JavaPlugin implements Listener {

    private final String version = "1.3.0-FINAL";
    private final Map<UUID, String> editandoKit = new HashMap<>();
    private final Map<UUID, String> esperandoChat = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("akits").setExecutor(new KitsCommand());
        enviarBannerConsola();
    }

    private void enviarBannerConsola() {
        CommandSender c = Bukkit.getConsoleSender();
        c.sendMessage(color("&b&l&m▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        c.sendMessage(color("&b   _  _____ _____  _____ "));
        c.sendMessage(color("&b  | |/ /_ _|_   _|/ ____|"));
        c.sendMessage(color("&b  | ' / | |  | | | (___  "));
        c.sendMessage(color("&b  |  <  | |  | |  \\___ \\ "));
        c.sendMessage(color("&b  | . \\_| |_ | |  ____) |"));
        c.sendMessage(color("&b  |_|\\_\\____||_| |_____/ "));
        c.sendMessage("");
        c.sendMessage(color("&f  Estado: &a¡Inteligencia Cargada! &e" + version));
        c.sendMessage(color("&f  Funciones: &bPermisos Automáticos &f+ &bCofre Inteligente"));
        c.sendMessage(color("&b&l&m▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
    }

    public class KitsCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (args.length > 0) {
                if (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("listcommand")) {
                    enviarAyuda(sender);
                    return true;
                }
                if (args[0].equalsIgnoreCase("list")) {
                    enviarListaKits(sender);
                    return true;
                }
                if (args[0].equalsIgnoreCase("version")) {
                    sender.sendMessage(color("&b&lKitsAdvanced &8» &fVersión: &e" + version));
                    return true;
                }

                if (!(sender instanceof Player)) return true;
                Player p = (Player) sender;

                if (args[0].equalsIgnoreCase("panel") && p.hasPermission("kitsadvanced.admin")) {
                    abrirListaKitsAdmin(p);
                    return true;
                }
                if (args[0].equalsIgnoreCase("create") && args.length > 1 && p.hasPermission("kitsadvanced.admin")) {
                    crearNuevoKit(args[1]);
                    p.sendMessage(color("&a&l✔ &fKit '&e" + args[1] + "&f' creado satisfactoriamente."));
                    return true;
                }
                if (args[0].equalsIgnoreCase("reload") && p.hasPermission("kitsadvanced.admin")) {
                    reloadConfig();
                    p.sendMessage(color("&b&l[SISTEMA] &a¡Configuración actualizada y recargada!"));
                    return true;
                }
            }
            if (sender instanceof Player) abrirMenuCategorias((Player) sender);
            return true;
        }
    }

    // --- PANEL DE ADMINISTRACIÓN ---
    public void abrirListaKitsAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 45, color("&8&lPANEL &7» &0Gestión de Kits"));
        ConfigurationSection section = getConfig().getConfigurationSection("kits");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                inv.addItem(createItem(Material.CHEST, "&eEditar: &f" + key, "&7Click para entrar a la configuración."));
            }
        }
        inv.setItem(40, createItem(Material.EMERALD, "&a&l+ CREAR KIT NUEVO", "&7Haz click para añadir un kit rápido."));
        p.openInventory(inv);
    }

    public void abrirOpcionesKit(Player p, String idKit) {
        editandoKit.put(p.getUniqueId(), idKit);
        Inventory inv = Bukkit.createInventory(null, 27, color("&0Kit: &l" + idKit));

        inv.setItem(4, createItem(Material.PAPER, "&b&lINFO DEL KIT", "&fID: &e" + idKit, "&7Usa los botones para modificar."));
        inv.setItem(10, createItem(Material.CLOCK, "&e&lTIEMPO (COOLDOWN)", "&7Actual: &f" + getConfig().getString("kits." + idKit + ".tiempo"), "&bClick para cambiar en chat."));
        inv.setItem(11, createItem(Material.CHEST, "&6&lSUBIR ÍTEMS", "&7Arrastra ítems de tu inventario", "&7al cofre que se abrirá."));

        // Ítem Inteligente de Permiso
        boolean req = getConfig().getBoolean("kits." + idKit + ".requiere-permiso", false);
        if (req) {
            inv.setItem(13, createItem(Material.LIME_WOOL, "&a&lPERMISO: ACTIVADO &7(✔)", "&7Permiso: &e" + "kitsadvanced.kit." + idKit, "", "&cClick para Desactivar."));
        } else {
            inv.setItem(13, createItem(Material.RED_WOOL, "&c&lPERMISO: DESACTIVADO &7(✘)", "&7Acceso público para todos.", "", "&aClick para Activar."));
        }

        inv.setItem(15, createItem(Material.BOOK, "&d&lCATEGORÍA", "&7Actual: &f" + getConfig().getString("kits." + idKit + ".categoria"), "&eClick para cambiar Gratis/VIP."));
        inv.setItem(22, createItem(Material.ARROW, "&c⬅ VOLVER"));
        p.openInventory(inv);
    }

    @EventHandler
    public void alHacerClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.getCurrentItem() == null) return;
        Player p = (Player) e.getWhoClicked();
        String titulo = ChatColor.stripColor(e.getView().getTitle());

        if (titulo.contains("Kits") || titulo.contains("Categoría") || titulo.contains("Kit:") || titulo.contains("PANEL")) {
            e.setCancelled(true);
            
            if (titulo.contains("Gratuitos") || titulo.contains("Premium")) {
                darKit(p, ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()));
                return;
            }

            if (titulo.contains("PANEL") || titulo.contains("ADMIN")) {
                if (e.getCurrentItem().getType() == Material.CHEST) {
                    String id = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("Editar: ", "");
                    abrirOpcionesKit(p, id);
                } else if (e.getCurrentItem().getType() == Material.EMERALD) pedirNombreKit(p);
            }

            if (titulo.startsWith("Kit: ")) {
                String idKit = editandoKit.get(p.getUniqueId());
                Material m = e.getCurrentItem().getType();
                if (m == Material.CLOCK) {
                    p.closeInventory();
                    esperandoChat.put(p.getUniqueId(), "TIEMPO");
                    p.sendMessage(color("&e&lTIEMPO &8» &fEscribe el cooldown (ej: 2h, 1d, 30m):"));
                } else if (m == Material.CHEST) abrirCofreEditor(p, idKit);
                else if (m == Material.LIME_WOOL || m == Material.RED_WOOL) {
                    boolean act = !getConfig().getBoolean("kits." + idKit + ".requiere-permiso", false);
                    getConfig().set("kits." + idKit + ".requiere-permiso", act);
                    saveConfig();
                    if (act) p.sendMessage(color("&a&l✔ &aPermiso ACTIVADO para &f" + idKit));
                    else p.sendMessage(color("&c&l✘ &cPermiso DESACTIVADO para &f" + idKit));
                    abrirOpcionesKit(p, idKit);
                } else if (m == Material.BOOK) {
                    String cat = getConfig().getString("kits." + idKit + ".categoria").equals("DEFAULT") ? "PREMIUM" : "DEFAULT";
                    getConfig().set("kits." + idKit + ".categoria", cat);
                    saveConfig();
                    abrirOpcionesKit(p, idKit);
                } else if (m == Material.ARROW) abrirListaKitsAdmin(p);
            }
        }
    }

    @EventHandler
    public void alCerrarEditor(InventoryCloseEvent e) {
        String titulo = ChatColor.stripColor(e.getView().getTitle());
        if (titulo.startsWith("Editor: ")) {
            String idKit = titulo.replace("Editor: ", "");
            List<ItemStack> contenido = new ArrayList<>();
            for (ItemStack item : e.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR) contenido.add(item);
            }
            getConfig().set("kits." + idKit + ".items", contenido);
            saveConfig();
            e.getPlayer().sendMessage(color("&a&l✔ &f¡Items subidos y guardados para el kit &e" + idKit + "&f!"));
        }
    }

    @EventHandler
    public void alEscribir(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!esperandoChat.containsKey(p.getUniqueId())) return;
        e.setCancelled(true);
        String modo = esperandoChat.get(p.getUniqueId());
        Bukkit.getScheduler().runTask(this, () -> {
            if (modo.equals("TIEMPO")) {
                String id = editandoKit.get(p.getUniqueId());
                getConfig().set("kits." + id + ".tiempo", e.getMessage());
                saveConfig();
                p.sendMessage(color("&a&l✔ &fCooldown establecido en: &e" + e.getMessage()));
                abrirOpcionesKit(p, id);
            } else if (modo.equals("NUEVO_KIT")) {
                crearNuevoKit(e.getMessage());
                p.sendMessage(color("&a&l✔ &fKit &e" + e.getMessage() + " &fcreado."));
                abrirListaKitsAdmin(p);
            }
            esperandoChat.remove(p.getUniqueId());
        });
    }

    // --- LÓGICA DE ENTREGA ---
    private void darKit(Player p, String idKit) {
        if (getConfig().getBoolean("kits." + idKit + ".requiere-permiso") && !p.hasPermission("kitsadvanced.kit." + idKit)) {
            p.sendMessage(color("&c&l✘ &cNo tienes el permiso: &e" + "kitsadvanced.kit." + idKit));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return;
        }
        List<?> items = getConfig().getList("kits." + idKit + ".items");
        if (items != null) {
            for (Object item : items) if (item instanceof ItemStack) p.getInventory().addItem((ItemStack) item);
            p.sendMessage(color("&a&l✔ &fHas reclamado el kit &e" + idKit));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
            p.closeInventory();
        }
    }

    private void enviarAyuda(CommandSender s) {
        s.sendMessage(color("&b&l&m▬▬▬▬▬▬▬▬&r &b&lAYUDA KITS ADVANCED &b&l&m▬▬▬▬▬▬▬▬"));
        s.sendMessage(color("&e/akits &8- &7Abrir menú de categorías."));
        s.sendMessage(color("&e/akits list &8- &7Ver nombres de kits creados."));
        if (s.hasPermission("kitsadvanced.admin")) {
            s.sendMessage(color("&c/akits panel &8- &7Panel de control profesional."));
            s.sendMessage(color("&c/akits create <id> &8- &7Crear kit al instante."));
            s.sendMessage(color("&c/akits reload &8- &7Actualizar configuración."));
        }
        s.sendMessage(color("&b&l&m▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
    }

    private void enviarListaKits(CommandSender s) {
        ConfigurationSection sec = getConfig().getConfigurationSection("kits");
        String l = (sec == null) ? "&cVacío" : String.join("&7, &f", sec.getKeys(false));
        s.sendMessage(color("&b&lKits registrados: &f" + l));
    }

    private void abrirCofreEditor(Player p, String idKit) {
        Inventory editor = Bukkit.createInventory(null, 36, color("&0Editor: " + idKit));
        List<?> items = getConfig().getList("kits." + idKit + ".items");
        if (items != null) for (Object item : items) if (item instanceof ItemStack) editor.addItem((ItemStack) item);
        p.openInventory(editor);
    }

    private void crearNuevoKit(String n) {
        getConfig().set("kits." + n + ".nombre", n);
        getConfig().set("kits." + n + ".categoria", "DEFAULT");
        getConfig().set("kits." + n + ".tiempo", "1h");
        getConfig().set("kits." + n + ".requiere-permiso", false);
        getConfig().set("kits." + n + ".items", new ArrayList<ItemStack>());
        saveConfig();
    }

    private void pedirNombreKit(Player p) {
        p.closeInventory();
        esperandoChat.put(p.getUniqueId(), "NUEVO_KIT");
        p.sendMessage(color("&a&lKITS &8» &fEscribe el nombre para el nuevo kit:"));
    }

    public void abrirMenuCategorias(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&0Selecciona Categoría"));
        inv.setItem(11, createItem(Material.CHEST, "&a&lKITS GRATUITOS", "&7Kits de acceso común."));
        inv.setItem(15, createItem(Material.NETHER_STAR, "&6&lKITS PREMIUM", "&7Kits para usuarios VIP."));
        p.openInventory(inv);
    }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    private ItemStack createItem(Material mat, String n, String... lore) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(color(n));
        m.setLore(Arrays.asList(lore));
        i.setItemMeta(m);
        return i;
    }
}
