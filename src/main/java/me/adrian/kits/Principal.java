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
import org.bukkit.scheduler.BukkitRunnable;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class Principal extends JavaPlugin implements Listener {
    private final Map<UUID, String> editandoKit = new HashMap<>();
    private final Map<UUID, String> modoChat = new HashMap<>();
    private final Map<UUID, Integer> paginaActual = new HashMap<>();
    private final Map<UUID, Map<String, Long>> kitsTemporalesActivos = new HashMap<>();

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdir();
        File carpetaKits = new File(getDataFolder(), "kits");
        if (!carpetaKits.exists()) carpetaKits.mkdirs();
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("akits").setExecutor(new KitsCommand());
        iniciarTaskExpiracion();
    }

    private String c(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    public FileConfiguration getKitConfig(String n) {
        File f = new File(getDataFolder() + "/kits", n + ".yml");
        return f.exists() ? YamlConfiguration.loadConfiguration(f) : null;
    }

    public void guardarKit(String n, FileConfiguration conf) {
        try { conf.save(new File(getDataFolder() + "/kits", n + ".yml")); } catch (IOException e) { e.printStackTrace(); }
    }

    public class KitsCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender s, Command cmd, String l, String[] a) {
            if (!(s instanceof Player)) return true;
            Player p = (Player) s;
            if (a.length == 0) { abrirMenuKits(p); return true; }
            if (!p.hasPermission("kitsadvanced.admin")) return true;
            if (a[0].equalsIgnoreCase("panel")) { paginaActual.put(p.getUniqueId(), 0); abrirPanelAdmin(p); return true; }
            return true;
        }
    }

    public void abrirMenuKits(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c("&0&lMENÚ DE KITS"));
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i > 44 || i % 9 == 0 || (i + 1) % 9 == 0) 
                inv.setItem(i, createItem(Material.CYAN_STAINED_GLASS_PANE, " ", false));
        }
        File[] archivos = new File(getDataFolder(), "kits").listFiles((dir, name) -> name.endsWith(".yml"));
        if (archivos != null) {
            for (File f : archivos) inv.addItem(createKitIcon(f.getName().replace(".yml", "")));
        }
        p.openInventory(inv);
    }

    private ItemStack createKitIcon(String k) {
        FileConfiguration conf = getKitConfig(k);
        Material icono = Material.valueOf(conf.getString("icono", "CHEST"));
        boolean pre = conf.getBoolean("requiere-permiso");
        return createItem(icono, "&e&lKit: &f" + k, pre, "&7Precio: &a$" + conf.getDouble("precio"), "&e▶ Click para reclamar");
    }

    public void abrirPanelAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c("&0Panel: Gestionar Kits"));
        File[] archivos = new File(getDataFolder(), "kits").listFiles((dir, name) -> name.endsWith(".yml"));
        int pg = paginaActual.getOrDefault(p.getUniqueId(), 0);

        if (archivos == null || archivos.length == 0) {
            inv.setItem(22, createItem(Material.CHEST, "&b&lCREAR TU PRIMER KIT", true, "&7No tienes kits aún.", "&e▶ Click para empezar"));
        } else {
            inv.setItem(0, createItem(Material.TRAPPED_CHEST, "&b&l[+] CREAR NUEVO KIT", true, "&7Haz click para añadir otro kit"));
            int start = pg * 44;
            for (int i = 0; i < 44 && (start + i) < archivos.length; i++) {
                String nombre = archivos[start + i].getName().replace(".yml", "");
                inv.setItem(i + 1, createItem(Material.PAPER, "&eEditar: &f" + nombre, false, "&7Click para abrir las 9 opciones"));
            }
            if (archivos.length > (pg + 1) * 44) inv.setItem(53, createItem(Material.ARROW, "&aPágina Siguiente", false));
            if (pg > 0) inv.setItem(45, createItem(Material.ARROW, "&cPágina Anterior", false));
        }
        p.openInventory(inv);
    }

    public void abrirOpcionesKit(Player p, String k) {
        editandoKit.put(p.getUniqueId(), k);
        FileConfiguration conf = getKitConfig(k);
        Inventory inv = Bukkit.createInventory(null, 45, c("&0Ajustes: &8" + k));
        for (int i = 36; i < 45; i++) inv.setItem(i, createItem(Material.YELLOW_STAINED_GLASS_PANE, " ", false));

        boolean pre = conf.getBoolean("requiere-permiso");
        boolean cool = conf.getBoolean("cooldown-activado");
        boolean temp = conf.getBoolean("es-temporal");

        inv.setItem(10, createItem(Material.TRAPPED_CHEST, "&6&l1. Editar Ítems", false));
        inv.setItem(11, createItem(Material.REDSTONE, "&e&l2. Permiso Automático", pre, "&7advanced.kits." + k.toLowerCase()));
        inv.setItem(12, createItem(Material.SUNFLOWER, "&e&l3. Precio", false, "&7Actual: &a$" + conf.getDouble("precio")));
        inv.setItem(13, createItem(Material.CLOCK, "&b&l4. Cooldown", cool, "&7Tiempo: &e" + conf.getInt("cooldown-segundos") + "s"));
        inv.setItem(14, createItem(Material.SOUL_SAND, "&b&l5. Kit Temporal", temp, "&7Duración: &e" + conf.getString("duracion-temp"), "&8(Derecho: Tiempo)"));
        inv.setItem(20, createItem(Material.PAINTING, "&d&l6. Cambiar Icono", false, "&7Actual: &f" + conf.getString("icono")));
        inv.setItem(21, createItem(Material.NAME_TAG, "&f&l7. Modificar Nombre", false));
        inv.setItem(22, createItem(Material.EMERALD, "&a&l8. Tipo: " + (pre ? "&dPREMIUM" : "&aGRATIS"), pre));
        inv.setItem(24, createItem(Material.BARRIER, "&4&l9. ELIMINAR KIT", false));

        inv.setItem(40, createItem(Material.ARROW, "&c« Volver", false));
        p.openInventory(inv);
    }

    @EventHandler
    public void alHacerClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.getCurrentItem() == null) return;
        Player p = (Player) e.getWhoClicked();
        String t = ChatColor.stripColor(e.getView().getTitle());
        String k = editandoKit.get(p.getUniqueId());

        if (t.equals("Panel: Gestionar Kits")) {
            e.setCancelled(true);
            Material m = e.getCurrentItem().getType();
            if (m == Material.CHEST || m == Material.TRAPPED_CHEST) {
                p.closeInventory(); modoChat.put(p.getUniqueId(), "CREAR");
                p.sendMessage(c("&b&l[+] &f¿Nombre del kit?"));
            } else if (m == Material.PAPER) {
                abrirOpcionesKit(p, ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("Editar: ", ""));
            } else if (e.getRawSlot() == 53) { paginaActual.put(p.getUniqueId(), paginaActual.get(p.getUniqueId()) + 1); abrirPanelAdmin(p); }
            else if (e.getRawSlot() == 45) { paginaActual.put(p.getUniqueId(), paginaActual.get(p.getUniqueId()) - 1); abrirPanelAdmin(p); }
        } else if (t.startsWith("Ajustes:")) {
            e.setCancelled(true);
            FileConfiguration cf = getKitConfig(k);
            if (e.getRawSlot() == 10) abrirCofreEditor(p, k);
            if (e.getRawSlot() == 11 || e.getRawSlot() == 22) { toggle(p, cf, "requiere-permiso", k); abrirOpcionesKit(p, k); }
            if (e.getRawSlot() == 12) { p.closeInventory(); modoChat.put(p.getUniqueId(), "PRECIO"); }
            if (e.getRawSlot() == 13) { p.closeInventory(); modoChat.put(p.getUniqueId(), "COOLDOWN"); }
            if (e.getRawSlot() == 14) {
                if (e.getClick() == ClickType.RIGHT) { p.closeInventory(); modoChat.put(p.getUniqueId(), "TEMP_TIME"); }
                else { toggle(p, cf, "es-temporal", k); abrirOpcionesKit(p, k); }
            }
            if (e.getRawSlot() == 20) { p.closeInventory(); modoChat.put(p.getUniqueId(), "ICONO"); }
            if (e.getRawSlot() == 21) { p.closeInventory(); modoChat.put(p.getUniqueId(), "NOMBRE"); }
            if (e.getRawSlot() == 24) { new File(getDataFolder() + "/kits", k + ".yml").delete(); abrirPanelAdmin(p); }
            if (e.getRawSlot() == 40) abrirPanelAdmin(p);
        } else if (t.equals("MENÚ DE KITS")) {
            e.setCancelled(true);
            darKit(p, ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("Kit: ", ""));
        }
    }

    @EventHandler
    public void alChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!modoChat.containsKey(p.getUniqueId())) return;
        e.setCancelled(true);
        String msg = e.getMessage();
        String modo = modoChat.get(p.getUniqueId());
        String k = editandoKit.get(p.getUniqueId());

        Bukkit.getScheduler().runTask(this, () -> {
            try {
                if (modo.equals("CREAR")) { crearKitBase(msg); abrirOpcionesKit(p, msg); }
                else {
                    FileConfiguration cf = getKitConfig(k);
                    if (modo.equals("PRECIO")) cf.set("precio", Double.parseDouble(msg));
                    if (modo.equals("COOLDOWN")) { cf.set("cooldown-segundos", Integer.parseInt(msg)); cf.set("cooldown-activado", true); }
                    if (modo.equals("TEMP_TIME")) { cf.set("duracion-temp", msg); cf.set("es-temporal", true); }
                    if (modo.equals("ICONO")) cf.set("icono", Material.valueOf(msg.toUpperCase()).name());
                    if (modo.equals("NOMBRE")) {
                        new File(getDataFolder() + "/kits", k + ".yml").renameTo(new File(getDataFolder() + "/kits", msg + ".yml"));
                        abrirOpcionesKit(p, msg);
                    } else { guardarKit(k, cf); abrirOpcionesKit(p, k); }
                }
                modoChat.remove(p.getUniqueId());
            } catch (Exception ex) { p.sendMessage(c("&c&l✘ &7Error.")); modoChat.remove(p.getUniqueId()); }
        });
    }

    private void toggle(Player p, FileConfiguration cf, String path, String k) {
        boolean v = !cf.getBoolean(path); cf.set(path, v); guardarKit(k, cf);
        p.sendMessage(c(v ? "&a&l✔ &fActivado" : "&c&l✘ &fDesactivado"));
        p.playSound(p.getLocation(), v ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BASS, 1, 2);
    }

    private void darKit(Player p, String k) {
        FileConfiguration cf = getKitConfig(k);
        if (cf == null) return;
        if (cf.getBoolean("requiere-permiso") && !p.hasPermission("advanced.kits." + k.toLowerCase())) {
            p.sendMessage(c("&c&l✘ &7Sin permiso.")); return;
        }
        for (Object o : cf.getList("items")) p.getInventory().addItem((ItemStack) o);
        p.sendMessage(c("&a&l✔ &fKit recibido."));
        if (cf.getBoolean("es-temporal")) {
            long time = parseTime(cf.getString("duracion-temp", "1h"));
            Map<String, Long> m = kitsTemporalesActivos.getOrDefault(p.getUniqueId(), new HashMap<>());
            m.put(k, System.currentTimeMillis() + time);
            kitsTemporalesActivos.put(p.getUniqueId(), m);
        }
    }

    private long parseTime(String s) {
        s = s.toLowerCase();
        if (s.endsWith("d")) return Long.parseLong(s.replace("d","")) * 86400000L;
        if (s.endsWith("h")) return Long.parseLong(s.replace("h","")) * 3600000L;
        if (s.endsWith("m")) return Long.parseLong(s.replace("m","")) * 60000L;
        return Long.parseLong(s.replace("s","")) * 1000L;
    }

    private void iniciarTaskExpiracion() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long n = System.currentTimeMillis();
                for (UUID id : new HashSet<>(kitsTemporalesActivos.keySet())) {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null) continue;
                    Map<String, Long> m = kitsTemporalesActivos.get(id);
                    for (String kn : new HashSet<>(m.keySet())) {
                        if (n >= m.get(kn)) {
                            for (Object i : getKitConfig(kn).getList("items")) p.getInventory().removeItem((ItemStack) i);
                            p.sendMessage(c("&c&l[!] &7El kit &f" + kn + " &7ha expirado."));
                            m.remove(kn);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    private void crearKitBase(String n) {
        File f = new File(getDataFolder() + "/kits", n + ".yml");
        FileConfiguration c = YamlConfiguration.loadConfiguration(f);
        c.set("requiere-permiso", false); c.set("precio", 0.0); c.set("cooldown-segundos", 60);
        c.set("cooldown-activado", true); c.set("icono", "CHEST"); c.set("es-temporal", false);
        c.set("duracion-temp", "1h"); c.set("items", new ArrayList<ItemStack>());
        try { c.save(f); } catch (IOException e) {}
    }

    private void abrirCofreEditor(Player p, String k) {
        Inventory ed = Bukkit.createInventory(null, 36, c("&0Items de: " + k));
        List<?> items = getKitConfig(k).getList("items");
        if (items != null) for (Object i : items) ed.addItem((ItemStack) i);
        p.openInventory(ed);
    }

    @EventHandler
    public void alCerrar(InventoryCloseEvent e) {
        if (ChatColor.stripColor(e.getView().getTitle()).startsWith("Items de: ")) {
            String k = ChatColor.stripColor(e.getView().getTitle()).replace("Items de: ", "");
            FileConfiguration cf = getKitConfig(k);
            List<ItemStack> list = new ArrayList<>();
            for (ItemStack i : e.getInventory().getContents()) if (i != null && i.getType() != Material.AIR) list.add(i);
            cf.set("items", list); guardarKit(k, cf);
        }
    }

    private ItemStack createItem(Material m, String n, boolean g, String... lore) {
        ItemStack i = new ItemStack(m); ItemMeta mt = i.getItemMeta();
        if (mt == null) return i;
        mt.setDisplayName(c(n)); List<String> l = new ArrayList<>();
        for (String s : lore) l.add(c(s)); mt.setLore(l);
        if (g) { mt.addEnchant(Enchantment.ARROW_INFINITE, 1, true); mt.addItemFlags(ItemFlag.HIDE_ENCHANTS); }
        i.setItemMeta(mt); return i;
    }
}
