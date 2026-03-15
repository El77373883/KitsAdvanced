package me.adrian.kits;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Principal extends JavaPlugin implements Listener {

    private static Economy econ = null;
    private final Map<UUID, String> editandoKit = new HashMap<>();
    private final Map<UUID, String> modoChat = new HashMap<>();
    private final Map<UUID, String> categoriaActual = new HashMap<>();

    @Override
    public void onEnable() {
        // 1. Inicialización de Carpetas
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        File kitsDir = new File(getDataFolder(), "kits");
        File userDir = new File(getDataFolder(), "userdata");
        if (!kitsDir.exists()) kitsDir.mkdirs();
        if (!userDir.exists()) userDir.mkdirs();

        // 2. Configuración Base y Bienvenida
        saveDefaultConfig();
        cargarConfiguracionGlobal();
        
        // 3. Dependencias (Vault y Placeholders)
        setupEconomy();
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new KitPlaceholders(this).register();
        }

        // 4. Registro de Comandos y Eventos
        KitsCommand cmdHandler = new KitsCommand();
        getCommand("akits").setExecutor(cmdHandler);
        getCommand("akits").setTabCompleter(cmdHandler);
        Bukkit.getPluginManager().registerEvents(this, this);

        // 5. Verificación de Kit Diario
        if (getKitConfig("diario") == null) {
            crearKitBase("diario", false);
        }
        
        getLogger().info(c("&b&lAdvancedKits > &aMotor cargado con éxito."));
    }

    private void cargarConfiguracionGlobal() {
        FileConfiguration config = getConfig();
        config.addDefault("Mensajes.Bienvenida.activado", true);
        config.addDefault("Mensajes.Bienvenida.texto", Arrays.asList(
            "&b&l&m▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
            "&f¡Hola &b{player}&f, bienvenido!",
            "&7Disfruta de nuestros kits con &e/akits",
            "&b&l&m▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
        ));
        config.addDefault("Efectos.AlEntrar.sonido", "ENTITY_PLAYER_LEVELUP");
        config.addDefault("Efectos.AlEntrar.particulas", true);
        config.options().copyDefaults(true);
        saveConfig();
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
    }

    public String c(String m) {
        return ChatColor.translateAlternateColorCodes('&', m);
    }

    // --- MANEJO DE EVENTOS DE JUGADOR ---
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!getConfig().getBoolean("Mensajes.Bienvenida.activado")) return;

        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (String s : getConfig().getStringList("Mensajes.Bienvenida.texto")) {
                p.sendMessage(c(s.replace("{player}", p.getName())));
            }
            try {
                String soundName = getConfig().getString("Efectos.AlEntrar.sonido");
                p.playSound(p.getLocation(), Sound.valueOf(soundName), 1.0f, 1.0f);
            } catch (Exception ex) {
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        }, 20L);
    }

    // --- COMANDOS PRINCIPALES ---
    public class KitsCommand implements CommandExecutor, TabCompleter {
        @Override
        public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
            if (!(s instanceof Player)) return true;
            Player p = (Player) s;

            if (args.length == 0) {
                categoriaActual.putIfAbsent(p.getUniqueId(), "GRATIS");
                abrirMenuKits(p);
                return true;
            }

            String sub = args[0].toLowerCase();
            if (sub.equals("admin") || sub.equals("panel")) {
                if (p.hasPermission("akits.admin")) abrirPanelAdmin(p);
                else p.sendMessage(c("&cNo tienes permiso."));
            } 
            else if (sub.equals("create") && args.length > 1) {
                if (!p.hasPermission("akits.admin")) return true;
                boolean esPremium = args.length > 2 && args[2].equalsIgnoreCase("premium");
                crearKitBase(args[1], esPremium);
                p.sendMessage(c("&aKit &f" + args[1] + " &acreado como &f" + (esPremium ? "PREMIUM (Diamante)" : "GRATIS (Piedra)")));
            }
            else if (sub.equals("reload")) {
                if (!p.hasPermission("akits.admin")) return true;
                reloadConfig();
                p.sendMessage(c("&aPlugin recargado."));
            }
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
            if (args.length == 1) return Arrays.asList("admin", "create", "reload", "diario");
            if (args.length == 3 && args[0].equalsIgnoreCase("create")) return Arrays.asList("default", "premium");
            return null;
        }
    }

    // --- PANELES DE ADMINISTRACIÓN (LOS 11 BOTONES) ---
    public void abrirOpcionesKit(Player p, String k) {
        editandoKit.put(p.getUniqueId(), k);
        FileConfiguration cf = getKitConfig(k);
        Inventory inv = Bukkit.createInventory(null, 45, c("&8Config: &1" + k));

        // Botones de Configuración
        inv.setItem(10, createItem(Material.CHEST, "&b1. Editar Contenido", false, "&7Añade o quita los items del kit."));
        inv.setItem(11, createItem(Material.GOLD_INGOT, "&e2. Precio Actual: &a$" + cf.getDouble("precio"), false, "&7Haz click para cambiar el precio en chat."));
        inv.setItem(12, createItem(Material.CLOCK, "&63. Cooldown: &f" + cf.getInt("cooldown") + "s", false, "&7Tiempo de espera entre reclamos."));
        inv.setItem(13, createItem(Material.REDSTONE, "&d4. Categoría Premium: &f" + cf.getBoolean("premium"), cf.getBoolean("premium"), "&7Cambia entre Gratis/Premium. &c(Reinicia items)"));
        inv.setItem(14, createItem(Material.ITEM_FRAME, "&f5. Icono Actual: &e" + cf.getString("icono"), false, "&7Pon un item en tu mano y click aquí."));
        inv.setItem(15, createItem(Material.PAPER, "&a6. Mensaje de Reclamo", false, "&7Mensaje que recibe el jugador al obtener el kit."));
        inv.setItem(16, createItem(Material.IRON_DOOR, "&37. Permiso Requerido", false, "&7Define el permiso necesario para usar este kit."));
        inv.setItem(20, createItem(Material.LEVER, "&e8. Estado: " + (cf.getBoolean("enabled") ? "&aACTIVADO" : "&cDESACTIVADO"), false, "&7Activa o desactiva el kit globalmente."));
        inv.setItem(21, createItem(Material.COMMAND_BLOCK, "&59. Comando de Consola", false, "&7Ejecuta un comando al reclamar. &7Usa {player}"));
        inv.setItem(22, createItem(Material.IRON_CHESTPLATE, "&b10. Auto-Equipar: &f" + cf.getBoolean("auto-equip"), cf.getBoolean("auto-equip"), "&7¿Equipar armadura automáticamente?"));
        inv.setItem(23, createItem(Material.FIREWORK_ROCKET, "&f11. Efectos Visuales: &f" + cf.getBoolean("efectos"), cf.getBoolean("efectos"), "&7Lanza fuegos artificiales al reclamar."));
        
        inv.setItem(40, createItem(Material.BARRIER, "&4&lBORRAR KIT", true, "&c¡Esta acción no se puede deshacer!"));

        p.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.getCurrentItem() == null) return;
        Player p = (Player) e.getWhoClicked();
        String title = ChatColor.stripColor(e.getView().getTitle());

        if (title.contains("Config:")) {
            e.setCancelled(true);
            String kit = editandoKit.get(p.getUniqueId());
            FileConfiguration cf = getKitConfig(kit);
            int slot = e.getRawSlot();

            if (slot == 10) abrirEditorItems(p, kit);
            else if (slot == 11) { p.closeInventory(); modoChat.put(p.getUniqueId(), kit + ":PRECIO"); p.sendMessage(c("&eEscribe el nuevo &6PRECIO &e(Ej: 500):")); }
            else if (slot == 12) { p.closeInventory(); modoChat.put(p.getUniqueId(), kit + ":COOLDOWN"); p.sendMessage(c("&eEscribe el &6COOLDOWN &een segundos:")); }
            else if (slot == 13) {
                boolean val = !cf.getBoolean("premium");
                cf.set("premium", val);
                List<ItemStack> items = new ArrayList<>();
                items.add(new ItemStack(val ? Material.DIAMOND_SWORD : Material.STONE_SWORD));
                cf.set("items", items);
                guardarKit(kit, cf); abrirOpcionesKit(p, kit);
            }
            else if (slot == 14) {
                Material hand = p.getInventory().getItemInMainHand().getType();
                if (hand != Material.AIR) { cf.set("icono", hand.name()); guardarKit(kit, cf); abrirOpcionesKit(p, kit); }
            }
            else if (slot == 15) { p.closeInventory(); modoChat.put(p.getUniqueId(), kit + ":MSG"); p.sendMessage(c("&eEscribe el &6MENSAJE &ede reclamo:")); }
            else if (slot == 16) { p.closeInventory(); modoChat.put(p.getUniqueId(), kit + ":PERM"); p.sendMessage(c("&eEscribe el &6PERMISO &enecesario:")); }
            else if (slot == 20) { cf.set("enabled", !cf.getBoolean("enabled")); guardarKit(kit, cf); abrirOpcionesKit(p, kit); }
            else if (slot == 21) { p.closeInventory(); modoChat.put(p.getUniqueId(), kit + ":CMD"); p.sendMessage(c("&eEscribe el &6COMANDO &esin barra (/):")); }
            else if (slot == 22) { cf.set("auto-equip", !cf.getBoolean("auto-equip")); guardarKit(kit, cf); abrirOpcionesKit(p, kit); }
            else if (slot == 23) { cf.set("efectos", !cf.getBoolean("efectos")); guardarKit(kit, cf); abrirOpcionesKit(p, kit); }
            else if (slot == 40) { new File(getDataFolder() + "/kits", kit + ".yml").delete(); p.closeInventory(); p.sendMessage(c("&cKit eliminado.")); }
        }
        else if (title.startsWith("Kits:")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 49) {
                categoriaActual.put(p.getUniqueId(), categoriaActual.get(p.getUniqueId()).equals("GRATIS") ? "PREMIUM" : "GRATIS");
                abrirMenuKits(p);
            } else if (e.getRawSlot() < 45 && e.getCurrentItem().getType() != Material.AIR) {
                reclamarKit(p, ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("Kit: ", ""));
            }
        }
        else if (title.contains("Editando Items:")) {
            if (e.getRawSlot() >= 45) {
                e.setCancelled(true);
                if (e.getRawSlot() == 49) {
                    guardarItemsEditor(e.getInventory(), editandoKit.get(p.getUniqueId()));
                    abrirOpcionesKit(p, editandoKit.get(p.getUniqueId()));
                }
            }
        }
    }

    @EventHandler
    public void onChatInput(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!modoChat.containsKey(p.getUniqueId())) return;
        
        e.setCancelled(true);
        String msg = e.getMessage();
        String[] data = modoChat.remove(p.getUniqueId()).split(":");
        String kit = data[0];
        String tipo = data[1];
        FileConfiguration cf = getKitConfig(kit);

        Bukkit.getScheduler().runTask(this, () -> {
            try {
                if (tipo.equals("PRECIO")) cf.set("precio", Double.parseDouble(msg));
                else if (tipo.equals("COOLDOWN")) cf.set("cooldown", Integer.parseInt(msg));
                else if (tipo.equals("MSG")) cf.set("mensaje", msg);
                else if (tipo.equals("PERM")) cf.set("permiso", msg);
                else if (tipo.equals("CMD")) cf.set("comando", msg);
                
                guardarKit(kit, cf);
                p.sendMessage(c("&a¡Propiedad &f" + tipo + " &aactualizada!"));
                abrirOpcionesKit(p, kit);
            } catch (Exception ex) {
                p.sendMessage(c("&cError: Valor inválido."));
                abrirOpcionesKit(p, kit);
            }
        });
    }

    // --- LÓGICA CORE DE RECLAMO ---
    private void reclamarKit(Player p, String k) {
        FileConfiguration cf = getKitConfig(k);
        if (cf == null || !cf.getBoolean("enabled", true)) { p.sendMessage(c("&cKit no disponible.")); return; }
        
        // Permiso
        if (!p.hasPermission(cf.getString("permiso"))) {
            p.sendMessage(c("&cNo tienes permiso para este kit."));
            return;
        }

        // Cooldown
        File uf = new File(getDataFolder() + "/userdata", p.getUniqueId() + ".yml");
        FileConfiguration uc = YamlConfiguration.loadConfiguration(uf);
        long last = uc.getLong(k, 0);
        long cooldownMillis = cf.getInt("cooldown") * 1000L;
        if (System.currentTimeMillis() - last < cooldownMillis) {
            long remaining = (cooldownMillis - (System.currentTimeMillis() - last)) / 1000;
            p.sendMessage(c("&cEspera &f" + remaining + "s &cpara volver a usarlo."));
            return;
        }

        // Economía
        double precio = cf.getDouble("precio");
        if (precio > 0 && econ != null) {
            if (econ.getBalance(p) < precio) {
                p.sendMessage(c("&cNo tienes suficiente dinero ($" + precio + ")."));
                return;
            }
            econ.withdrawPlayer(p, precio);
        }

        // Entrega
        List<?> items = cf.getList("items");
        if (items != null) {
            for (Object obj : items) {
                if (obj instanceof ItemStack) {
                    ItemStack is = (ItemStack) obj;
                    if (cf.getBoolean("auto-equip")) equiparAuto(p, is);
                    else p.getInventory().addItem(is);
                }
            }
        }

        // Comando Consola
        String cmd = cf.getString("comando", "none");
        if (!cmd.equalsIgnoreCase("none")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
        }

        // Efectos
        if (cf.getBoolean("efectos")) lanzarFuego(p);

        // Guardar uso
        uc.set(k, System.currentTimeMillis());
        try { uc.save(uf); } catch (IOException ex) {}
        p.sendMessage(c(cf.getString("mensaje")));
    }

    private void equiparAuto(Player p, ItemStack i) {
        String type = i.getType().name();
        if (type.endsWith("_HELMET")) p.getInventory().setHelmet(i);
        else if (type.endsWith("_CHESTPLATE")) p.getInventory().setChestplate(i);
        else if (type.endsWith("_LEGGINGS")) p.getInventory().setLeggings(i);
        else if (type.endsWith("_BOOTS")) p.getInventory().setBoots(i);
        else p.getInventory().addItem(i);
    }

    private void lanzarFuego(Player p) {
        Firework fw = p.getWorld().spawn(p.getLocation(), Firework.class);
        FireworkMeta fm = fw.getFireworkMeta();
        fm.addEffect(FireworkEffect.builder().withColor(Color.AQUA, Color.WHITE).withFade(Color.BLUE).with(FireworkEffect.Type.BALL_LARGE).trail(true).build());
        fm.setPower(1);
        fw.setFireworkMeta(fm);
    }

    // --- MÉTODOS DE SOPORTE (GUI Y FILES) ---
    public void abrirMenuKits(Player p) {
        String cat = categoriaActual.getOrDefault(p.getUniqueId(), "GRATIS");
        Inventory inv = Bukkit.createInventory(null, 54, c("&8Kits: &1" + cat));
        inv.setItem(49, createItem(Material.NETHER_STAR, "&eCategoría Actual: " + cat, true, "&7Haz click para cambiar de categoría."));
        
        File[] files = new File(getDataFolder(), "kits").listFiles();
        if (files != null) {
            for (File f : files) {
                FileConfiguration cf = YamlConfiguration.loadConfiguration(f);
                if (cf.getBoolean("premium") == cat.equals("PREMIUM") && cf.getBoolean("enabled")) {
                    String n = f.getName().replace(".yml", "");
                    inv.addItem(createItem(Material.valueOf(cf.getString("icono", "CHEST")), "&6Kit: &f" + n, false, "&7Click para reclamar."));
                }
            }
        }
        p.openInventory(inv);
    }

    public void abrirPanelAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c("&4&lPANEL ADMINISTRATIVO"));
        File[] files = new File(getDataFolder(), "kits").listFiles();
        if (files != null) {
            for (File f : files) {
                inv.addItem(createItem(Material.PAPER, "&eEditar: &f" + f.getName().replace(".yml", ""), false, "&7Configura este kit."));
            }
        }
        p.openInventory(inv);
    }

    private void abrirEditorItems(Player p, String k) {
        Inventory inv = Bukkit.createInventory(null, 54, c("&0Editando Items: " + k));
        List<?> items = getKitConfig(k).getList("items");
        if (items != null) {
            for (Object i : items) if (i instanceof ItemStack) inv.addItem((ItemStack) i);
        }
        for (int i = 45; i < 54; i++) inv.setItem(i, createItem(Material.LIME_STAINED_GLASS_PANE, "&a&lGUARDAR", true));
        p.openInventory(inv);
    }

    private void guardarItemsEditor(Inventory inv, String k) {
        FileConfiguration cf = getKitConfig(k);
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            if (inv.getItem(i) != null) list.add(inv.getItem(i));
        }
        cf.set("items", list);
        guardarKit(k, cf);
    }

    private void crearKitBase(String n, boolean p) {
        FileConfiguration cf = new YamlConfiguration();
        cf.set("premium", p);
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(p ? Material.DIAMOND_SWORD : Material.STONE_SWORD));
        cf.set("items", items);
        cf.set("precio", 0.0);
        cf.set("cooldown", 3600);
        cf.set("icono", p ? "DIAMOND_SWORD" : "STONE_SWORD");
        cf.set("mensaje", "&a¡Has recibido tu kit!");
        cf.set("permiso", p ? "akits.premium" : "akits.user");
        cf.set("enabled", true);
        cf.set("comando", "none");
        cf.set("auto-equip", false);
        cf.set("efectos", true);
        guardarKit(n, cf);
    }

    public FileConfiguration getKitConfig(String n) {
        File f = new File(getDataFolder() + "/kits", n + ".yml");
        return f.exists() ? YamlConfiguration.loadConfiguration(f) : null;
    }

    public void guardarKit(String n, FileConfiguration cf) {
        try { cf.save(new File(getDataFolder() + "/kits", n + ".yml")); } catch (IOException e) {}
    }

    private ItemStack createItem(Material m, String n, boolean glint, String... lore) {
        ItemStack i = new ItemStack(m); ItemMeta mt = i.getItemMeta();
        mt.setDisplayName(c(n));
        if (lore.length > 0) mt.setLore(Arrays.stream(lore).map(this::c).collect(Collectors.toList()));
        if (glint) { mt.addEnchant(Enchantment.DURABILITY, 1, true); mt.addItemFlags(ItemFlag.HIDE_ENCHANTS); }
        i.setItemMeta(mt); return i;
    }

    public class KitPlaceholders extends PlaceholderExpansion {
        private Principal pl; public KitPlaceholders(Principal pl) { this.pl = pl; }
        @Override public String getIdentifier() { return "akits"; }
        @Override public String getAuthor() { return "Adrian"; }
        @Override public String getVersion() { return "1.0"; }
        @Override public boolean persist() { return true; }
        @Override public String onPlaceholderRequest(Player p, String id) { 
            if (id.equals("total")) return String.valueOf(new File(getDataFolder(), "kits").listFiles().length);
            return null;
        }
    }
}
