package me.adrian.kits;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Principal extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private final Map<UUID, ChatInput> chatInputMap = new HashMap<>();
    private final Map<UUID, String> deletingConfirm = new HashMap<>();
    private final Map<UUID, String> editingKit = new HashMap<>();
    private final Map<UUID, Integer> currentPage = new HashMap<>();
    private final Map<UUID, String> previewingKit = new HashMap<>();

    private Connection connection;
    private File kitsFolder;

    private static final String PREFIX = "§8[§6AdvancedKits§8] §r";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupDefaultConfig();

        kitsFolder = new File(getDataFolder(), "kits");
        if (!kitsFolder.exists()) kitsFolder.mkdirs();

        setupSQLite();
        createCooldownTable();

        getServer().getPluginManager().registerEvents(this, this);

        PluginCommand cmd = getCommand("akits");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new AdvancedKitsExpansion(this).register();
            getLogger().info("PlaceholderAPI detectado. Placeholder expansion registrada.");
        }

        getLogger().info("AdvancedKits habilitado correctamente.");
    }

    @Override
    public void onDisable() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (Exception ignored) {
        }
    }

    private void setupDefaultConfig() {
        FileConfiguration c = getConfig();

        c.addDefault("menu.title", "§8✦ §6AdvancedKits §8✦");
        c.addDefault("menu.size", 54);
        c.addDefault("menu.show-locked-kits", true);
        c.addDefault("menu.premium-slot", 49);
        c.addDefault("menu.info-slot", 50);
        c.addDefault("menu.help-slot", 48);
        c.addDefault("menu.reload-slot", 53);

        c.addDefault("sounds.open-menu", "BLOCK_CHEST_OPEN");
        c.addDefault("sounds.claim", "ENTITY_PLAYER_LEVELUP");
        c.addDefault("sounds.error", "ENTITY_VILLAGER_NO");
        c.addDefault("sounds.save", "BLOCK_ANVIL_USE");
        c.addDefault("sounds.delete", "ENTITY_ITEM_BREAK");
        c.addDefault("sounds.back", "ITEM_BOOK_PAGE_TURN");

        c.addDefault("database.sqlite-file", "cooldowns.db");

        c.options().copyDefaults(true);
        saveConfig();
    }

    private void setupSQLite() {
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            File dbFile = new File(getDataFolder(), getConfig().getString("database.sqlite-file", "cooldowns.db"));
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);
        } catch (Exception e) {
            getLogger().severe("No se pudo iniciar SQLite.");
            e.printStackTrace();
        }
    }

    private void createCooldownTable() {
        String sql = "CREATE TABLE IF NOT EXISTS kit_cooldowns (" +
                "uuid TEXT NOT NULL," +
                "kit TEXT NOT NULL," +
                "next_claim BIGINT NOT NULL," +
                "PRIMARY KEY(uuid, kit)" +
                ");";
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setCooldown(UUID uuid, String kit, long nextClaim) {
        String sql = "INSERT OR REPLACE INTO kit_cooldowns(uuid, kit, next_claim) VALUES(?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit.toLowerCase());
            ps.setLong(3, nextClaim);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private long getCooldown(UUID uuid, String kit) {
        String sql = "SELECT next_claim FROM kit_cooldowns WHERE uuid=? AND kit=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("next_claim");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0L;
    }

    private void resetCooldown(UUID uuid, String kit) {
        String sql = "DELETE FROM kit_cooldowns WHERE uuid=? AND kit=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit.toLowerCase());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void logAdmin(String action) {
        try {
            File logs = new File(getDataFolder(), "logs");
            if (!logs.exists()) logs.mkdirs();

            File file = new File(logs, "admin.log");
            if (!file.exists()) file.createNewFile();

            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());

            try (FileWriter fw = new FileWriter(file, true)) {
                fw.write("[" + time + "] " + action + System.lineSeparator());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sound(Player p, String path) {
        try {
            String s = getConfig().getString(path, "UI_BUTTON_CLICK");
            p.playSound(p.getLocation(), Sound.valueOf(s), 1f, 1f);
        } catch (Exception ignored) {
        }
    }

    private String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    private List<String> color(List<String> s) {
        return s == null ? new ArrayList<>() : s.stream().map(this::color).collect(Collectors.toList());
    }

    private ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(color(name));
            im.setLore(color(lore));
            it.setItemMeta(im);
        }
        return it;
    }

    private ItemStack glow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack skull(String owner, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            try {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
            } catch (Exception ignored) {}
            meta.setDisplayName(color(name));
            meta.setLore(color(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<String> getKitNames() {
        List<String> names = new ArrayList<>();
        if (!kitsFolder.exists()) kitsFolder.mkdirs();
        File[] files = kitsFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                names.add(f.getName().replace(".yml", ""));
            }
        }
        Collections.sort(names);
        return names;
    }

    private File getKitFile(String name) {
        return new File(kitsFolder, name.toLowerCase() + ".yml");
    }

    private FileConfiguration getKitConfig(String name) {
        return YamlConfiguration.loadConfiguration(getKitFile(name));
    }

    private void saveKitConfig(String name, FileConfiguration cfg) {
        try {
            cfg.save(getKitFile(name));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean kitExists(String name) {
        return getKitFile(name).exists();
    }

    private void createKit(String name) {
        File file = getKitFile(name);
        if (file.exists()) return;

        FileConfiguration y = YamlConfiguration.loadConfiguration(file);
        y.set("name", "&6" + name);
        y.set("type", "DEFAULT");
        y.set("permission", "akits.kit." + name.toLowerCase());
        y.set("premium-permission", "akits.premium");
        y.set("icon", "CHEST");
        y.set("slot", -1);
        y.set("cooldown", 3600);
        y.set("auto-equip", false);
        y.set("broadcast", false);
        y.set("enabled", true);
        y.set("glow", false);
        y.set("lore", Arrays.asList(
                "&7Haz click izquierdo para reclamar.",
                "&7Haz click derecho para preview."
        ));
        y.set("items", new ArrayList<>());
        y.set("armor", new ArrayList<>());
        y.set("offhand", null);
        y.set("commands.console", new ArrayList<>());
        y.set("commands.player", new ArrayList<>());
        saveKitConfig(name, y);
    }

    private String getDisplayName(String kit) {
        return color(getKitConfig(kit).getString("name", "&6" + kit));
    }

    private String getType(String kit) {
        return getKitConfig(kit).getString("type", "DEFAULT").toUpperCase();
    }

    private boolean isPremium(String kit) {
        return getType(kit).equalsIgnoreCase("PREMIUM");
    }

    private boolean canUseKit(Player p, String kit) {
        FileConfiguration cfg = getKitConfig(kit);
        if (!cfg.getBoolean("enabled", true)) return false;
        if (p.hasPermission("akits.admin")) return true;

        String perm = cfg.getString("permission", "akits.kit." + kit.toLowerCase());
        if (!p.hasPermission("akits.use")) return false;
        if (!p.hasPermission(perm)) return false;

        if (isPremium(kit) && !p.hasPermission(cfg.getString("premium-permission", "akits.premium"))) {
            return false;
        }
        return true;
    }

    private String getStatus(Player p, String kit) {
        if (!kitExists(kit)) return "INEXISTENTE";
        FileConfiguration cfg = getKitConfig(kit);
        if (!cfg.getBoolean("enabled", true)) return "DESACTIVADO";

        if (!p.hasPermission("akits.use")) return "BLOQUEADO";
        String perm = cfg.getString("permission", "akits.kit." + kit.toLowerCase());
        if (!p.hasPermission(perm)) return "BLOQUEADO";
        if (isPremium(kit) && !p.hasPermission(cfg.getString("premium-permission", "akits.premium"))) return "PREMIUM";

        long cd = getCooldown(p.getUniqueId(), kit);
        if (cd > System.currentTimeMillis()) return "COOLDOWN";
        return "DISPONIBLE";
    }

    private String formatTime(long ms) {
        if (ms <= 0) return "0s";
        long sec = ms / 1000;
        long d = sec / 86400;
        long h = (sec % 86400) / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;

        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (d == 0 && s > 0) sb.append(s).append("s");
        return sb.toString().trim();
    }

    private Material parseMaterial(String m) {
        try {
            return Material.valueOf(m.toUpperCase());
        } catch (Exception e) {
            return Material.CHEST;
        }
    }

    private ItemStack getKitIcon(Player p, String kit) {
        FileConfiguration cfg = getKitConfig(kit);
        Material mat = parseMaterial(cfg.getString("icon", "CHEST"));
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            String status = getStatus(p, kit);
            String stateColor = "§a";
            String stateText = "Disponible";

            if (status.equals("BLOQUEADO")) { stateColor = "§c"; stateText = "Bloqueado"; }
            else if (status.equals("COOLDOWN")) { stateColor = "§e"; stateText = "En cooldown"; }
            else if (status.equals("PREMIUM")) { stateColor = "§5"; stateText = "Premium"; }
            else if (status.equals("DESACTIVADO")) { stateColor = "§8"; stateText = "Desactivado"; }

            List<String> lore = new ArrayList<>();
            lore.add("§7Nombre: §f" + ChatColor.stripColor(getDisplayName(kit)));
            lore.add("§7Tipo: " + (isPremium(kit) ? "§dPremium" : "§aDefault"));
            lore.add("§7Estado: " + stateColor + stateText);
            lore.add("§7Permiso: §f" + cfg.getString("permission", "akits.kit." + kit.toLowerCase()));

            long cd = getCooldown(p.getUniqueId(), kit);
            if (cd > System.currentTimeMillis()) {
                lore.add("§7Cooldown: §e" + formatTime(cd - System.currentTimeMillis()));
            } else {
                lore.add("§7Cooldown: §aListo");
            }

            lore.add(" ");
            lore.add("§eClick izquierdo §7→ reclamar");
            lore.add("§eClick derecho §7→ preview");

            im.setDisplayName(getDisplayName(kit));
            im.setLore(lore);
            it.setItemMeta(im);
        }

        if (cfg.getBoolean("glow", false) || isPremium(kit)) glow(it);
        return it;
    }

    private void openMainMenu(Player p, int page) {
        sound(p, "sounds.open-menu");
        currentPage.put(p.getUniqueId(), page);

        Inventory inv = Bukkit.createInventory(null, 54, color(getConfig().getString("menu.title", "§8✦ §6AdvancedKits §8✦")));

        ItemStack border = item(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        ItemStack border2 = item(Material.BLACK_STAINED_GLASS_PANE, " ", Collections.emptyList());

        for (int i = 0; i < 54; i++) inv.setItem(i, (i % 2 == 0 ? border : border2));

        inv.setItem(4, glow(item(Material.NETHER_STAR, "&6&lAdvancedKits", Arrays.asList(
                "&7Sistema profesional de kits",
                "&7Default y Premium"
        ))));

        inv.setItem(48, skull("MHF_Question", "&eAyuda", Arrays.asList(
                "&7Comandos disponibles",
                "&e/akits help"
        )));

        inv.setItem(49, glow(item(Material.DIAMOND, "&b&lPremium Kits", Arrays.asList(
                "&7Aquí verás kits premium",
                "&7si tienes acceso."
        ))));

        inv.setItem(50, skull("MHF_Chest", "&aInformación", Arrays.asList(
                "&7Left click: reclamar",
                "&7Right click: preview",
                "&7Página actual: &f" + (page + 1)
        )));

        if (p.hasPermission("akits.admin")) {
            inv.setItem(53, item(Material.REDSTONE, "&cRecargar", Arrays.asList(
                    "&7Recarga configuración y kits."
            )));
            inv.setItem(45, item(Material.CHEST, "&6Panel Admin", Arrays.asList(
                    "&7Abrir panel de administración."
            )));
        }

        List<String> kits = getKitNames();
        boolean showLocked = getConfig().getBoolean("menu.show-locked-kits", true);

        List<String> visible = new ArrayList<>();
        for (String kit : kits) {
            if (showLocked || canUseKit(p, kit) || p.hasPermission("akits.admin")) visible.add(kit);
        }

        int[] slots = {
                10,11,12,13,14,15,16,
                19,20,21,22,23,24,25,
                28,29,30,31,32,33,34
        };

        int perPage = slots.length;
        int start = page * perPage;
        int end = Math.min(start + perPage, visible.size());

        for (int i = start; i < end; i++) {
            inv.setItem(slots[i - start], getKitIcon(p, visible.get(i)));
        }

        if (page > 0) {
            inv.setItem(46, item(Material.ARROW, "&ePágina anterior", Arrays.asList("&7Ir atrás")));
        }
        if (end < visible.size()) {
            inv.setItem(52, item(Material.ARROW, "&ePágina siguiente", Arrays.asList("&7Ir adelante")));
        }

        p.openInventory(inv);
    }

    private void openAdminPanel(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8✦ §cAdmin Panel §8✦");

        ItemStack fill = item(Material.BLACK_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < 54; i++) inv.setItem(i, fill);

        inv.setItem(10, glow(item(Material.CHEST, "&aCrear kit", Arrays.asList(
                "&7Crea un nuevo kit por chat."
        ))));
        inv.setItem(12, item(Material.BOOK, "&eEditar kit", Arrays.asList(
                "&7Selecciona un kit para editar."
        )));
        inv.setItem(14, item(Material.BARRIER, "&cEliminar kit", Arrays.asList(
                "&7Selecciona un kit para borrar."
        )));
        inv.setItem(16, item(Material.HOPPER, "&bLista de kits", Arrays.asList(
                "&7Ver todos los kits."
        )));
        inv.setItem(31, item(Material.ARROW, "&7Volver", Arrays.asList(
                "&7Regresar al menú principal."
        )));

        p.openInventory(inv);
    }

    private void openKitSelector(Player p, String mode) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8Seleccionar kit: " + mode);

        ItemStack fill = item(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < 54; i++) inv.setItem(i, fill);

        int slot = 10;
        for (String kit : getKitNames()) {
            if (slot >= 44) break;
            inv.setItem(slot++, getKitIcon(p, kit));
        }

        inv.setItem(49, item(Material.ARROW, "&7Volver", Collections.emptyList()));
        p.openInventory(inv);
    }

    private void openEditMenu(Player p, String kit) {
        editingKit.put(p.getUniqueId(), kit);

        Inventory inv = Bukkit.createInventory(null, 54, "§8Editar kit: §6" + kit);

        ItemStack fill = item(Material.BLACK_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < 54; i++) inv.setItem(i, fill);

        inv.setItem(4, getKitIcon(p, kit));

        inv.setItem(10, item(Material.NAME_TAG, "&eCambiar nombre visible", Arrays.asList("&7Por chat")));
        inv.setItem(11, item(Material.ITEM_FRAME, "&eCambiar icono", Arrays.asList("&7Escribe un material válido")));
        inv.setItem(12, item(Material.WRITABLE_BOOK, "&eCambiar lore", Arrays.asList("&7Usa | para separar líneas")));
        inv.setItem(13, item(Material.CLOCK, "&eCambiar cooldown", Arrays.asList("&7En segundos")));
        inv.setItem(14, item(Material.DIAMOND, "&eCambiar tipo", Arrays.asList("&7DEFAULT o PREMIUM")));
        inv.setItem(15, item(Material.IRON_SWORD, "&eAuto-equip", Arrays.asList("&7Activar o desactivar")));
        inv.setItem(16, item(Material.BELL, "&eBroadcast", Arrays.asList("&7Activar o desactivar")));

        inv.setItem(19, item(Material.CHEST, "&aGuardar inventario", Arrays.asList(
                "&7Guarda inventario actual, armadura",
                "&7y offhand del admin."
        )));
        inv.setItem(20, item(Material.COMMAND_BLOCK, "&bEditar comandos consola", Arrays.asList("&7Usa ; para separar")));
        inv.setItem(21, item(Material.REPEATING_COMMAND_BLOCK, "&bEditar comandos jugador", Arrays.asList("&7Usa ; para separar")));
        inv.setItem(22, item(Material.TRIPWIRE_HOOK, "&bEditar permiso", Arrays.asList("&7Por chat")));
        inv.setItem(23, item(Material.LIME_DYE, "&aActivar/Desactivar kit", Arrays.asList("&7Toggle enabled")));
        inv.setItem(24, item(Material.ENCHANTED_BOOK, "&dGlow item", Arrays.asList("&7Toggle visual")));
        inv.setItem(25, item(Material.HOPPER, "&eCambiar slot", Arrays.asList("&7Número o -1 automático")));

        inv.setItem(31, item(Material.ENDER_CHEST, "&6Preview", Arrays.asList("&7Ver contenido del kit")));
        inv.setItem(32, item(Material.REDSTONE, "&cBorrar kit", Arrays.asList("&7Abrir confirmación")));
        inv.setItem(49, item(Material.ARROW, "&7Volver", Collections.emptyList()));

        p.openInventory(inv);
    }

    private void openDeleteConfirm(Player p, String kit) {
        deletingConfirm.put(p.getUniqueId(), kit);

        Inventory inv = Bukkit.createInventory(null, 27, "§8Confirmar borrado");

        ItemStack fill = item(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < 27; i++) inv.setItem(i, fill);

        inv.setItem(11, item(Material.LIME_WOOL, "&aConfirmar", Arrays.asList("&7Borrar kit §f" + kit)));
        inv.setItem(13, getKitIcon(p, kit));
        inv.setItem(15, item(Material.RED_WOOL, "&cCancelar", Arrays.asList("&7No borrar")));

        p.openInventory(inv);
    }

    private void openPreview(Player p, String kit) {
        previewingKit.put(p.getUniqueId(), kit);
        FileConfiguration cfg = getKitConfig(kit);

        Inventory inv = Bukkit.createInventory(null, 54, "§8Preview: §6" + kit);

        ItemStack fill = item(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < 54; i++) inv.setItem(i, fill);

        List<?> items = cfg.getList("items", new ArrayList<>());
        for (int i = 0; i < Math.min(36, items.size()); i++) {
            Object o = items.get(i);
            if (o instanceof ItemStack) inv.setItem(i, (ItemStack) o);
        }

        List<?> armor = cfg.getList("armor", new ArrayList<>());
        for (int i = 0; i < Math.min(4, armor.size()); i++) {
            Object o = armor.get(i);
            if (o instanceof ItemStack) inv.setItem(45 + i, (ItemStack) o);
        }

        Object off = cfg.get("offhand");
        if (off instanceof ItemStack) inv.setItem(49, (ItemStack) off);

        long cd = getCooldown(p.getUniqueId(), kit);
        String st = getStatus(p, kit);

        inv.setItem(50, item(Material.PAPER, "&eInformación", Arrays.asList(
                "&7Kit: §f" + kit,
                "&7Tipo: " + (isPremium(kit) ? "§dPremium" : "§aDefault"),
                "&7Estado: §f" + st,
                "&7Cooldown: " + (cd > System.currentTimeMillis() ? "§e" + formatTime(cd - System.currentTimeMillis()) : "§aListo")
        )));
        inv.setItem(52, item(Material.LIME_DYE, "&aReclamar", Arrays.asList("&7Click para reclamar")));
        inv.setItem(53, item(Material.ARROW, "&7Volver", Collections.emptyList()));

        p.openInventory(inv);
    }

    private void giveKit(Player p, String kit, boolean ignoreCooldown) {
        if (!kitExists(kit)) {
            p.sendMessage(PREFIX + "§cEse kit no existe.");
            sound(p, "sounds.error");
            return;
        }

        FileConfiguration cfg = getKitConfig(kit);

        if (!ignoreCooldown) {
            if (!canUseKit(p, kit)) {
                p.sendMessage(PREFIX + "§cNo tienes permiso para este kit.");
                sound(p, "sounds.error");
                return;
            }

            long next = getCooldown(p.getUniqueId(), kit);
            if (next > System.currentTimeMillis()) {
                p.sendMessage(PREFIX + "§eDebes esperar §f" + formatTime(next - System.currentTimeMillis()) + " §epara volver a reclamarlo.");
                sound(p, "sounds.error");
                return;
            }
        }

        List<ItemStack> content = new ArrayList<>();
        List<?> rawItems = cfg.getList("items", new ArrayList<>());
        for (Object o : rawItems) if (o instanceof ItemStack) content.add((ItemStack) o);

        boolean autoEquip = cfg.getBoolean("auto-equip", false);

        if (autoEquip) {
            List<?> rawArmor = cfg.getList("armor", new ArrayList<>());
            ItemStack[] armor = new ItemStack[4];
            for (int i = 0; i < Math.min(4, rawArmor.size()); i++) {
                Object o = rawArmor.get(i);
                if (o instanceof ItemStack) armor[i] = (ItemStack) o;
            }
            p.getInventory().setArmorContents(armor);

            Object off = cfg.get("offhand");
            if (off instanceof ItemStack) p.getInventory().setItemInOffHand((ItemStack) off);
        } else {
            List<?> rawArmor = cfg.getList("armor", new ArrayList<>());
            for (Object o : rawArmor) if (o instanceof ItemStack) p.getInventory().addItem((ItemStack) o);

            Object off = cfg.get("offhand");
            if (off instanceof ItemStack) p.getInventory().addItem((ItemStack) off);
        }

        for (ItemStack item : content) {
            if (item != null) p.getInventory().addItem(item);
        }

        for (String cmd : cfg.getStringList("commands.console")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
        }
        for (String cmd : cfg.getStringList("commands.player")) {
            p.performCommand(cmd.replace("%player%", p.getName()));
        }

        long cooldownSeconds = cfg.getLong("cooldown", 3600);
        setCooldown(p.getUniqueId(), kit, System.currentTimeMillis() + (cooldownSeconds * 1000L));

        if (cfg.getBoolean("broadcast", false)) {
            Bukkit.broadcastMessage(color("&6" + p.getName() + " ha reclamado el kit " + cfg.getString("name", kit) + "&6!"));
        }

        p.sendMessage(PREFIX + "§aHas reclamado el kit §e" + ChatColor.stripColor(getDisplayName(kit)) + "§a.");
        sound(p, "sounds.claim");
    }

    private void savePlayerInventoryToKit(Player p, String kit) {
        FileConfiguration cfg = getKitConfig(kit);

        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            items.add(p.getInventory().getItem(i));
        }

        List<ItemStack> armor = new ArrayList<>(Arrays.asList(p.getInventory().getArmorContents()));
        ItemStack offhand = p.getInventory().getItemInOffHand();

        cfg.set("items", items);
        cfg.set("armor", armor);
        cfg.set("offhand", offhand);

        saveKitConfig(kit, cfg);
    }

    private void askChat(Player p, ChatInputType type, String kit) {
        chatInputMap.put(p.getUniqueId(), new ChatInput(type, kit));
        p.closeInventory();
        p.sendMessage(PREFIX + "§eEscribe en el chat. Escribe §ccancelar §epara salir.");
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!chatInputMap.containsKey(p.getUniqueId())) return;

        e.setCancelled(true);

        String msg = e.getMessage();
        if (msg.equalsIgnoreCase("cancelar")) {
            chatInputMap.remove(p.getUniqueId());
            Bukkit.getScheduler().runTask(this, () -> {
                p.sendMessage(PREFIX + "§cAcción cancelada.");
                sound(p, "sounds.back");
                openMainMenu(p, currentPage.getOrDefault(p.getUniqueId(), 0));
            });
            return;
        }

        ChatInput input = chatInputMap.remove(p.getUniqueId());

        Bukkit.getScheduler().runTask(this, () -> {
            try {
                switch (input.type) {
                    case CREATE_KIT -> {
                        String kit = msg.toLowerCase().replace(" ", "_");
                        if (kitExists(kit)) {
                            p.sendMessage(PREFIX + "§cEse kit ya existe.");
                            sound(p, "sounds.error");
                            return;
                        }
                        createKit(kit);
                        p.sendMessage(PREFIX + "§aKit creado: §e" + kit);
                        logAdmin(p.getName() + " creó el kit " + kit);
                        sound(p, "sounds.save");
                        openEditMenu(p, kit);
                    }
                    case EDIT_NAME -> {
                        FileConfiguration cfg = getKitConfig(input.kit);
                        cfg.set("name", msg);
                        saveKitConfig(input.kit, cfg);
                        p.sendMessage(PREFIX + "§aNombre actualizado.");
                        logAdmin(p.getName() + " cambió nombre visible de " + input.kit + " a " + msg);
                        sound(p, "sounds.save");
                        openEditMenu(p, input.kit);
                    }
                    case EDIT_ICON -> {
                        Material m = parseMaterial(msg);
                        FileConfiguration cfg = getKitConfig(input.kit);
                        cfg.set("icon", m.name());
                        saveKitConfig(input.kit, cfg);
                        p.sendMessage(PREFIX + "§aIcono actualizado a §e" + m.name());
                        logAdmin(p.getName() + " cambió icono de " + input.kit + " a " + m.name());
                        sound(p, "sounds.save");
                        openEditMenu(p, input.kit);
                    }
                    case EDIT_LORE -> {
                        FileConfiguration cfg = getKitConfig(input.kit);
                        cfg.set("lore", Arrays.stream(msg.split("\\|")).map(String::trim).collect(Collectors.toList()));
                        saveKitConfig(input.kit, cfg);
                        p.sendMessage(PREFIX + "§aLore actualizado.");
                        logAdmin(p.getName() + " cambió lore de " + input.kit);
                        sound(p, "sounds.save");
                        openEditMenu(p, input.kit);
                    }
                    case EDIT_COOLDOWN -> {
                        long sec = Long.parseLong(msg);
                        FileConfiguration cfg = getKitConfig(input.kit);
                        cfg.set("cooldown", sec);
                        saveKitConfig(input.kit, cfg);
                        p.sendMessage(PREFIX + "§aCooldown actualizado a §e" + sec + "s");
                        logAdmin(p.getName() + " cambió cooldown de " + input.kit + " a " + sec);
                        sound(p, "sounds.save");
                        openEditMenu(p, input.kit);
                    }
                    case EDIT_TYPE -> {
                        String type = msg.equalsIgnoreCase("premium") ? "PREMIUM" : "DEFAULT";
                        FileConfiguration cfg = getKitConfig(input.kit);
                        cfg.set("type", type);
                        saveKitConfig(input.kit, cfg);
                        p.sendMessage(PREFIX + "§aTipo actualizado a §e" + type);
                        logAdmin(p.getName() + " cambió tipo de " + input.kit + " a " + type);
                        sound(p, "sounds.save");
                        openEditMenu(p, input.kit);
                    }
                    case EDIT_PERMISSION -> {
                        FileConfiguration cfg = getKitConfig(input.kit);
                        cfg.set("permission", msg);
                        saveKitConfig(input.kit, cfg);
                        p.sendMessage(PREFIX + "§aPermiso actualizado.");
                        logAdmin(p.getName() + " cambió permiso de " + input.kit + " a " + msg);
                        sound(p, "sounds.save");
                        openEditMenu(p, input.kit);
                    }
                    case EDIT_CONSOLE_COMMANDS -> {
                        FileConfiguration cfg = getKitConfig(input.kit);
                        cfg.set("commands.console", Arrays.stream(msg.split(";")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()));
                        saveKitConfig(input.kit, cfg);
                        p.sendMessage(PREFIX + "§aComandos de consola actualizados.");
                        logAdmin(p.getName() + " cambió comandos consola de " + input.kit);
                        sound(p, "sounds.save");
                        openEditMenu(p, input.kit);
                    }
                    case EDIT_PLAYER_COMMANDS -> {
                        FileConfiguration cfg = getKitConfig(input.kit);
                        cfg.set("commands.player", Arrays.stream(msg.split(";")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()));
                        saveKitConfig(input.kit, cfg);
                        p.sendMessage(PREFIX + "§aComandos de jugador actualizados.");
                        logAdmin(p.getName() + " cambió comandos jugador de " + input.kit);
                        sound(p, "sounds.save");
                        openEditMenu(p, input.kit);
                    }
                    case EDIT_SLOT -> {
                        int slot = Integer.parseInt(msg);
                        FileConfiguration cfg = getKitConfig(input.kit);
                        cfg.set("slot", slot);
                        saveKitConfig(input.kit, cfg);
                        p.sendMessage(PREFIX + "§aSlot actualizado.");
                        logAdmin(p.getName() + " cambió slot de " + input.kit + " a " + slot);
                        sound(p, "sounds.save");
                        openEditMenu(p, input.kit);
                    }
                }
            } catch (Exception ex) {
                p.sendMessage(PREFIX + "§cDato inválido.");
                sound(p, "sounds.error");
                openEditMenu(p, input.kit);
            }
        });
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getCurrentItem() == null) return;

        String title = e.getView().getTitle();
        ItemStack clicked = e.getCurrentItem();
        String name = clicked.hasItemMeta() && clicked.getItemMeta() != null && clicked.getItemMeta().hasDisplayName()
                ? ChatColor.stripColor(clicked.getItemMeta().getDisplayName()) : "";

        if (title.contains("AdvancedKits") || title.contains("Admin Panel") || title.contains("Seleccionar kit")
                || title.contains("Editar kit") || title.contains("Confirmar borrado") || title.contains("Preview:")) {
            e.setCancelled(true);
        }

        if (title.equals(color(getConfig().getString("menu.title", "§8✦ §6AdvancedKits §8✦")))) {
            if (e.getSlot() == 46) {
                openMainMenu(p, Math.max(0, currentPage.getOrDefault(p.getUniqueId(), 0) - 1));
                return;
            }
            if (e.getSlot() == 52) {
                openMainMenu(p, currentPage.getOrDefault(p.getUniqueId(), 0) + 1);
                return;
            }
            if (e.getSlot() == 45 && p.hasPermission("akits.admin")) {
                openAdminPanel(p);
                return;
            }
            if (e.getSlot() == 48) {
                p.closeInventory();
                p.sendMessage(color("&8&m--------------------------------"));
                p.sendMessage(color("&6/akits"));
                p.sendMessage(color("&6/akits help"));
                p.sendMessage(color("&6/akits list"));
                p.sendMessage(color("&6/akits version"));
                if (p.hasPermission("akits.admin")) {
                    p.sendMessage(color("&c/akits reload"));
                    p.sendMessage(color("&c/akits panel"));
                    p.sendMessage(color("&c/akits create <nombre>"));
                    p.sendMessage(color("&c/akits edit <nombre>"));
                    p.sendMessage(color("&c/akits delete <nombre>"));
                    p.sendMessage(color("&c/akits give <jugador> <kit>"));
                    p.sendMessage(color("&c/akits resetcooldown <jugador> <kit>"));
                }
                p.sendMessage(color("&6/akits preview <kit>"));
                p.sendMessage(color("&6/akits claim <kit>"));
                p.sendMessage(color("&8&m--------------------------------"));
                return;
            }
            if (e.getSlot() == 53 && p.hasPermission("akits.admin")) {
                reloadConfig();
                setupDefaultConfig();
                p.sendMessage(PREFIX + "§aPlugin recargado.");
                sound(p, "sounds.save");
                return;
            }

            for (String kit : getKitNames()) {
                if (ChatColor.stripColor(getDisplayName(kit)).equalsIgnoreCase(name)) {
                    if (e.isLeftClick()) giveKit(p, kit, false);
                    else if (e.isRightClick()) openPreview(p, kit);
                    return;
                }
            }
        }

        if (title.equals("§8✦ §cAdmin Panel §8✦")) {
            switch (e.getSlot()) {
                case 10 -> askChat(p, ChatInputType.CREATE_KIT, null);
                case 12 -> openKitSelector(p, "edit");
                case 14 -> openKitSelector(p, "delete");
                case 16 -> {
                    p.closeInventory();
                    p.sendMessage(PREFIX + "§eKits: §f" + String.join(", ", getKitNames()));
                }
                case 31 -> openMainMenu(p, 0);
            }
            return;
        }

        if (title.startsWith("§8Seleccionar kit: ")) {
            String mode = title.replace("§8Seleccionar kit: ", "");
            if (e.getSlot() == 49) {
                openAdminPanel(p);
                return;
            }

            for (String kit : getKitNames()) {
                if (ChatColor.stripColor(getDisplayName(kit)).equalsIgnoreCase(name)) {
                    if (mode.equalsIgnoreCase("edit")) openEditMenu(p, kit);
                    else if (mode.equalsIgnoreCase("delete")) openDeleteConfirm(p, kit);
                    return;
                }
            }
        }

        if (title.startsWith("§8Editar kit: §6")) {
            String kit = editingKit.get(p.getUniqueId());
            if (kit == null) return;

            switch (e.getSlot()) {
                case 10 -> askChat(p, ChatInputType.EDIT_NAME, kit);
                case 11 -> askChat(p, ChatInputType.EDIT_ICON, kit);
                case 12 -> askChat(p, ChatInputType.EDIT_LORE, kit);
                case 13 -> askChat(p, ChatInputType.EDIT_COOLDOWN, kit);
                case 14 -> askChat(p, ChatInputType.EDIT_TYPE, kit);
                case 15 -> {
                    FileConfiguration cfg = getKitConfig(kit);
                    boolean v = !cfg.getBoolean("auto-equip", false);
                    cfg.set("auto-equip", v);
                    saveKitConfig(kit, cfg);
                    p.sendMessage(PREFIX + "§aAuto-equip: " + (v ? "§aactivado" : "§cdesactivado"));
                    logAdmin(p.getName() + " cambió auto-equip de " + kit + " a " + v);
                    sound(p, "sounds.save");
                    openEditMenu(p, kit);
                }
                case 16 -> {
                    FileConfiguration cfg = getKitConfig(kit);
                    boolean v = !cfg.getBoolean("broadcast", false);
                    cfg.set("broadcast", v);
                    saveKitConfig(kit, cfg);
                    p.sendMessage(PREFIX + "§aBroadcast: " + (v ? "§aactivado" : "§cdesactivado"));
                    logAdmin(p.getName() + " cambió broadcast de " + kit + " a " + v);
                    sound(p, "sounds.save");
                    openEditMenu(p, kit);
                }
                case 19 -> {
                    savePlayerInventoryToKit(p, kit);
                    p.sendMessage(PREFIX + "§aInventario guardado en el kit.");
                    logAdmin(p.getName() + " guardó inventario en " + kit);
                    sound(p, "sounds.save");
                    openEditMenu(p, kit);
                }
                case 20 -> askChat(p, ChatInputType.EDIT_CONSOLE_COMMANDS, kit);
                case 21 -> askChat(p, ChatInputType.EDIT_PLAYER_COMMANDS, kit);
                case 22 -> askChat(p, ChatInputType.EDIT_PERMISSION, kit);
                case 23 -> {
                    FileConfiguration cfg = getKitConfig(kit);
                    boolean v = !cfg.getBoolean("enabled", true);
                    cfg.set("enabled", v);
                    saveKitConfig(kit, cfg);
                    p.sendMessage(PREFIX + "§aEnabled: " + (v ? "§atrue" : "§cfalse"));
                    logAdmin(p.getName() + " cambió enabled de " + kit + " a " + v);
                    sound(p, "sounds.save");
                    openEditMenu(p, kit);
                }
                case 24 -> {
                    FileConfiguration cfg = getKitConfig(kit);
                    boolean v = !cfg.getBoolean("glow", false);
                    cfg.set("glow", v);
                    saveKitConfig(kit, cfg);
                    p.sendMessage(PREFIX + "§aGlow: " + (v ? "§aactivado" : "§cdesactivado"));
                    logAdmin(p.getName() + " cambió glow de " + kit + " a " + v);
                    sound(p, "sounds.save");
                    openEditMenu(p, kit);
                }
                case 25 -> askChat(p, ChatInputType.EDIT_SLOT, kit);
                case 31 -> openPreview(p, kit);
                case 32 -> openDeleteConfirm(p, kit);
                case 49 -> openAdminPanel(p);
            }
            return;
        }

        if (title.equals("§8Confirmar borrado")) {
            String kit = deletingConfirm.get(p.getUniqueId());
            if (kit == null) return;

            if (e.getSlot() == 11) {
                File f = getKitFile(kit);
                if (f.exists() && f.delete()) {
                    p.sendMessage(PREFIX + "§cEl kit fue eliminado.");
                    logAdmin(p.getName() + " eliminó el kit " + kit);
                    sound(p, "sounds.delete");
                } else {
                    p.sendMessage(PREFIX + "§cNo se pudo eliminar el kit.");
                    sound(p, "sounds.error");
                }
                deletingConfirm.remove(p.getUniqueId());
                openAdminPanel(p);
                return;
            }
            if (e.getSlot() == 15) {
                deletingConfirm.remove(p.getUniqueId());
                sound(p, "sounds.back");
                openAdminPanel(p);
            }
            return;
        }

        if (title.startsWith("§8Preview: §6")) {
            String kit = previewingKit.get(p.getUniqueId());
            if (kit == null) return;

            if (e.getSlot() == 52) {
                giveKit(p, kit, false);
                return;
            }
            if (e.getSlot() == 53) {
                if (p.hasPermission("akits.admin") && editingKit.containsKey(p.getUniqueId()) && editingKit.get(p.getUniqueId()).equalsIgnoreCase(kit)) {
                    openEditMenu(p, kit);
                } else {
                    openMainMenu(p, currentPage.getOrDefault(p.getUniqueId(), 0));
                }
            }
        }
    }

    @EventHandler
    public void onInvClose(InventoryCloseEvent e) {
        HumanEntity h = e.getPlayer();
        if (!(h instanceof Player p)) return;
        previewingKit.remove(p.getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Solo jugadores.");
            return true;
        }

        if (args.length == 0) {
            openMainMenu(p, 0);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help" -> {
                p.sendMessage(color("&8&m--------------------------------"));
                p.sendMessage(color("&6/akits"));
                p.sendMessage(color("&6/akits help"));
                p.sendMessage(color("&6/akits list"));
                p.sendMessage(color("&6/akits version"));
                p.sendMessage(color("&6/akits preview <kit>"));
                p.sendMessage(color("&6/akits claim <kit>"));
                if (p.hasPermission("akits.admin")) {
                    p.sendMessage(color("&c/akits reload"));
                    p.sendMessage(color("&c/akits panel"));
                    p.sendMessage(color("&c/akits create <nombre>"));
                    p.sendMessage(color("&c/akits edit <nombre>"));
                    p.sendMessage(color("&c/akits delete <nombre>"));
                    p.sendMessage(color("&c/akits give <jugador> <kit>"));
                    p.sendMessage(color("&c/akits resetcooldown <jugador> <kit>"));
                }
                p.sendMessage(color("&8&m--------------------------------"));
            }
            case "list" -> p.sendMessage(PREFIX + "§eKits: §f" + String.join(", ", getKitNames()));
            case "version", "vercion" -> p.sendMessage(PREFIX + "§aAdvancedKits §fversión §e1.0");
            case "reload" -> {
                if (!p.hasPermission("akits.admin")) {
                    p.sendMessage(PREFIX + "§cNo tienes permiso.");
                    return true;
                }
                reloadConfig();
                setupDefaultConfig();
                p.sendMessage(PREFIX + "§aPlugin recargado.");
            }
            case "panel" -> {
                if (!p.hasPermission("akits.admin")) {
                    p.sendMessage(PREFIX + "§cNo tienes permiso.");
                    return true;
                }
                openAdminPanel(p);
            }
            case "create" -> {
                if (!p.hasPermission("akits.admin")) {
                    p.sendMessage(PREFIX + "§cNo tienes permiso.");
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage(PREFIX + "§cUsa: /akits create <nombre>");
                    return true;
                }
                String kit = args[1].toLowerCase();
                if (kitExists(kit)) {
                    p.sendMessage(PREFIX + "§cEse kit ya existe.");
                    return true;
                }
                createKit(kit);
                p.sendMessage(PREFIX + "§aKit creado: §e" + kit);
                logAdmin(p.getName() + " creó el kit " + kit + " con comando");
            }
            case "edit" -> {
                if (!p.hasPermission("akits.admin")) {
                    p.sendMessage(PREFIX + "§cNo tienes permiso.");
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage(PREFIX + "§cUsa: /akits edit <nombre>");
                    return true;
                }
                if (!kitExists(args[1])) {
                    p.sendMessage(PREFIX + "§cEse kit no existe.");
                    return true;
                }
                openEditMenu(p, args[1].toLowerCase());
            }
            case "delete" -> {
                if (!p.hasPermission("akits.admin")) {
                    p.sendMessage(PREFIX + "§cNo tienes permiso.");
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage(PREFIX + "§cUsa: /akits delete <nombre>");
                    return true;
                }
                if (!kitExists(args[1])) {
                    p.sendMessage(PREFIX + "§cEse kit no existe.");
                    return true;
                }
                openDeleteConfirm(p, args[1].toLowerCase());
            }
            case "preview" -> {
                if (args.length < 2) {
                    p.sendMessage(PREFIX + "§cUsa: /akits preview <kit>");
                    return true;
                }
                if (!kitExists(args[1])) {
                    p.sendMessage(PREFIX + "§cEse kit no existe.");
                    return true;
                }
                openPreview(p, args[1].toLowerCase());
            }
            case "claim" -> {
                if (args.length < 2) {
                    p.sendMessage(PREFIX + "§cUsa: /akits claim <kit>");
                    return true;
                }
                giveKit(p, args[1].toLowerCase(), false);
            }
            case "give" -> {
                if (!p.hasPermission("akits.admin")) {
                    p.sendMessage(PREFIX + "§cNo tienes permiso.");
                    return true;
                }
                if (args.length < 3) {
                    p.sendMessage(PREFIX + "§cUsa: /akits give <jugador> <kit>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    p.sendMessage(PREFIX + "§cJugador offline.");
                    return true;
                }
                if (!kitExists(args[2])) {
                    p.sendMessage(PREFIX + "§cEse kit no existe.");
                    return true;
                }
                giveKit(target, args[2].toLowerCase(), true);
                p.sendMessage(PREFIX + "§aKit entregado a §e" + target.getName());
                logAdmin(p.getName() + " dio kit " + args[2].toLowerCase() + " a " + target.getName());
            }
            case "resetcooldown" -> {
                if (!p.hasPermission("akits.admin")) {
                    p.sendMessage(PREFIX + "§cNo tienes permiso.");
                    return true;
                }
                if (args.length < 3) {
                    p.sendMessage(PREFIX + "§cUsa: /akits resetcooldown <jugador> <kit>");
                    return true;
                }
                OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
                if (!kitExists(args[2])) {
                    p.sendMessage(PREFIX + "§cEse kit no existe.");
                    return true;
                }
                resetCooldown(t.getUniqueId(), args[2].toLowerCase());
                p.sendMessage(PREFIX + "§aCooldown reiniciado.");
                logAdmin(p.getName() + " reseteó cooldown de " + args[2].toLowerCase() + " a " + args[1]);
            }
            default -> p.sendMessage(PREFIX + "§cSubcomando no encontrado. Usa /akits help");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> list = new ArrayList<>();

        if (args.length == 1) {
            list.addAll(Arrays.asList("help", "list", "version", "vercion", "preview", "claim"));
            if (sender.hasPermission("akits.admin")) {
                list.addAll(Arrays.asList("reload", "panel", "create", "edit", "delete", "give", "resetcooldown"));
            }
            return list.stream().filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 2 && Arrays.asList("preview", "claim", "edit", "delete").contains(args[0].toLowerCase())) {
            return getKitNames().stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("resetcooldown"))) {
            return getKitNames().stream().filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        }

        return list;
    }

    private enum ChatInputType {
        CREATE_KIT,
        EDIT_NAME,
        EDIT_ICON,
        EDIT_LORE,
        EDIT_COOLDOWN,
        EDIT_TYPE,
        EDIT_PERMISSION,
        EDIT_CONSOLE_COMMANDS,
        EDIT_PLAYER_COMMANDS,
        EDIT_SLOT
    }

    private static class ChatInput {
        private final ChatInputType type;
        private final String kit;

        public ChatInput(ChatInputType type, String kit) {
            this.type = type;
            this.kit = kit;
        }
    }

    private static class AdvancedKitsExpansion extends PlaceholderExpansion {

        private final Principal plugin;

        public AdvancedKitsExpansion(Principal plugin) {
            this.plugin = plugin;
        }

        @Override
        public @org.jetbrains.annotations.NotNull String getIdentifier() {
            return "advancedkits";
        }

        @Override
        public @org.jetbrains.annotations.NotNull String getAuthor() {
            return "Adrian + ChatGPT";
        }

        @Override
        public @org.jetbrains.annotations.NotNull String getVersion() {
            return "1.0";
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onPlaceholderRequest(Player player, @org.jetbrains.annotations.NotNull String params) {
            if (player == null) return "";

            if (params.startsWith("cooldown_")) {
                String kit = params.substring("cooldown_".length()).toLowerCase();
                long cd = plugin.getCooldown(player.getUniqueId(), kit);
                if (cd <= System.currentTimeMillis()) return "Listo";
                return plugin.formatTime(cd - System.currentTimeMillis());
            }

            if (params.startsWith("status_")) {
                String kit = params.substring("status_".length()).toLowerCase();
                return plugin.getStatus(player, kit);
            }

            if (params.startsWith("can_claim_")) {
                String kit = params.substring("can_claim_".length()).toLowerCase();
                boolean can = plugin.canUseKit(player, kit) && plugin.getCooldown(player.getUniqueId(), kit) <= System.currentTimeMillis();
                return can ? "yes" : "no";
            }

            return null;
        }
    }
}
