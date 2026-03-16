package me.adrian.kits;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Principal extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private final Map<UUID, String> chatAction = new HashMap<>();
    private final Map<UUID, String> chatTargetKit = new HashMap<>();
    private final Map<UUID, Integer> playerPage = new HashMap<>();
    private final Map<UUID, String> editorKit = new HashMap<>();

    private File kitsFolder;
    private File dataFile;
    private YamlConfiguration dataConfig;

    private Connection connection;

    private final String PREFIX = color("&8[&6AdvancedKits&8] ");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        createDefaultConfigValues();

        kitsFolder = new File(getDataFolder(), "kits");
        if (!kitsFolder.exists()) kitsFolder.mkdirs();

        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        setupSQLite();

        PluginCommand cmd = getCommand("akits");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }

        Bukkit.getPluginManager().registerEvents(this, this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new AKitsPlaceholder(this).register();
            getLogger().info("PlaceholderAPI encontrado.");
        }

        getLogger().info("AdvancedKits habilitado correctamente.");
    }

    @Override
    public void onDisable() {
        saveDataFile();
        closeSQLite();
    }

    private void createDefaultConfigValues() {
        if (!getConfig().contains("settings.page-size")) getConfig().set("settings.page-size", 21);
        if (!getConfig().contains("settings.fill-hidden-kits")) getConfig().set("settings.fill-hidden-kits", false);
        if (!getConfig().contains("settings.default-cooldown-message")) getConfig().set("settings.default-cooldown-message", "&eDebes esperar &f%time% &epara volver a reclamarlo.");
        if (!getConfig().contains("settings.broadcast-format")) getConfig().set("settings.broadcast-format", "&6%player% ha reclamado el kit premium &e%kit%&6!");
        if (!getConfig().contains("settings.prefix")) getConfig().set("settings.prefix", "&8[&6AdvancedKits&8] ");
        saveConfig();
    }

    private String prefix() {
        return color(getConfig().getString("settings.prefix", PREFIX));
    }

    private void setupSQLite() {
        try {
            File db = new File(getDataFolder(), "cooldowns.db");
            if (!db.exists()) db.createNewFile();
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());

            Statement st = connection.createStatement();
            st.executeUpdate("CREATE TABLE IF NOT EXISTS cooldowns (" +
                    "uuid TEXT NOT NULL," +
                    "kit TEXT NOT NULL," +
                    "next_claim BIGINT NOT NULL," +
                    "PRIMARY KEY(uuid, kit)" +
                    ")");
            st.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void closeSQLite() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setCooldown(UUID uuid, String kit, long nextClaim) {
        try {
            PreparedStatement ps = connection.prepareStatement("INSERT OR REPLACE INTO cooldowns(uuid, kit, next_claim) VALUES(?,?,?)");
            ps.setString(1, uuid.toString());
            ps.setString(2, kit.toLowerCase());
            ps.setLong(3, nextClaim);
            ps.executeUpdate();
            ps.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private long getCooldown(UUID uuid, String kit) {
        try {
            PreparedStatement ps = connection.prepareStatement("SELECT next_claim FROM cooldowns WHERE uuid=? AND kit=?");
            ps.setString(1, uuid.toString());
            ps.setString(2, kit.toLowerCase());
            ResultSet rs = ps.executeQuery();
            long result = 0L;
            if (rs.next()) result = rs.getLong("next_claim");
            rs.close();
            ps.close();
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0L;
    }

    private void resetCooldown(UUID uuid, String kit) {
        try {
            PreparedStatement ps = connection.prepareStatement("DELETE FROM cooldowns WHERE uuid=? AND kit=?");
            ps.setString(1, uuid.toString());
            ps.setString(2, kit.toLowerCase());
            ps.executeUpdate();
            ps.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private File getKitFile(String name) {
        return new File(kitsFolder, name.toLowerCase() + ".yml");
    }

    private YamlConfiguration getKitConfig(String name) {
        File file = getKitFile(name);
        if (!file.exists()) return null;
        return YamlConfiguration.loadConfiguration(file);
    }

    private void saveKitConfig(String name, YamlConfiguration cfg) {
        try {
            cfg.save(getKitFile(name));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean kitExists(String name) {
        return getKitFile(name).exists();
    }

    private List<String> getAllKits() {
        List<String> list = new ArrayList<>();
        File[] files = kitsFolder.listFiles();
        if (files == null) return list;
        for (File file : files) {
            if (file.getName().endsWith(".yml")) {
                list.add(file.getName().replace(".yml", ""));
            }
        }
        list.sort(Comparator.comparing(String::toLowerCase));
        return list;
    }

    private void createKit(String name) {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("name", "&e" + name);
        cfg.set("icon", "CHEST");
        cfg.set("slot", -1);
        cfg.set("type", "default");
        cfg.set("permission", "akits.kit." + name.toLowerCase());
        cfg.set("cooldown", 3600);
        cfg.set("auto-equip", false);
        cfg.set("broadcast", false);
        cfg.set("glow", false);
        cfg.set("enabled", true);
        cfg.set("lore", Arrays.asList(
                "&7Tipo: &f%type%",
                "&7Estado: %status%",
                "&7Permiso: &f%permission%",
                "&7Cooldown: &f%cooldown%",
                "",
                "&aClick izquierdo para reclamar",
                "&eClick derecho para preview"
        ));
        cfg.set("items", new ArrayList<>());
        cfg.set("armor", new ArrayList<>());
        cfg.set("offhand", null);
        cfg.set("commands.console", new ArrayList<>());
        cfg.set("commands.player", new ArrayList<>());
        saveKitConfig(name, cfg);
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private List<String> color(List<String> list) {
        List<String> out = new ArrayList<>();
        for (String s : list) out.add(color(s));
        return out;
    }

    private ItemStack makeItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            meta.setLore(color(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeGlowing(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatTime(long seconds) {
        if (seconds <= 0) return "0s";
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (d == 0 && s > 0) sb.append(s).append("s");
        return sb.toString().trim();
    }

    private boolean canUseKit(Player p, String kit) {
        YamlConfiguration cfg = getKitConfig(kit);
        if (cfg == null) return false;
        if (!cfg.getBoolean("enabled", true)) return false;
        if (p.hasPermission("akits.admin")) return true;

        String type = cfg.getString("type", "default").toLowerCase();
        String perm = cfg.getString("permission", "akits.kit." + kit.toLowerCase());

        if (!p.hasPermission("akits.use")) return false;
        if (!p.hasPermission(perm)) return false;
        if (type.equals("premium") && !p.hasPermission("akits.premium")) return false;

        return true;
    }

    private String getStatus(Player p, String kit) {
        YamlConfiguration cfg = getKitConfig(kit);
        if (cfg == null) return "&cNo existe";

        if (!cfg.getBoolean("enabled", true)) return "&cDesactivado";

        if (!canUseKit(p, kit)) return "&cBloqueado";

        long next = getCooldown(p.getUniqueId(), kit);
        long now = System.currentTimeMillis();
        if (next > now) {
            long left = (next - now) / 1000L;
            return "&eCooldown: " + formatTime(left);
        }

        return "&aDisponible";
    }

    private ItemStack buildKitIcon(Player p, String kit) {
        YamlConfiguration cfg = getKitConfig(kit);
        if (cfg == null) return makeItem(Material.BARRIER, "&cKit inválido", Arrays.asList("&7No se pudo cargar"));

        Material mat;
        try {
            mat = Material.valueOf(cfg.getString("icon", "CHEST").toUpperCase());
        } catch (Exception e) {
            mat = Material.CHEST;
        }

        String display = cfg.getString("name", "&e" + kit);
        List<String> lore = cfg.getStringList("lore");
        List<String> out = new ArrayList<>();

        String type = cfg.getString("type", "default");
        String permission = cfg.getString("permission", "akits.kit." + kit.toLowerCase());
        long next = getCooldown(p.getUniqueId(), kit);
        long now = System.currentTimeMillis();
        String cooldown = next > now ? formatTime((next - now) / 1000L) : "Listo";
        String status = getStatus(p, kit);

        for (String line : lore) {
            out.add(line.replace("%type%", type)
                    .replace("%status%", status)
                    .replace("%permission%", permission)
                    .replace("%cooldown%", cooldown)
                    .replace("%kit%", kit));
        }

        ItemStack item = makeItem(mat, display, out);

        if (cfg.getBoolean("glow", false) || type.equalsIgnoreCase("premium")) {
            item = makeGlowing(item);
        }

        return item;
    }

    private void openMainMenu(Player p, int page) {
        List<String> kits = getAllKits();
        int pageSize = getConfig().getInt("settings.page-size", 21);
        int maxPage = Math.max(1, (int) Math.ceil(kits.size() / (double) pageSize));
        if (page < 1) page = 1;
        if (page > maxPage) page = maxPage;
        playerPage.put(p.getUniqueId(), page);

        Inventory inv = Bukkit.createInventory(null, 54, color("&8✦ &6AdvancedKits &8• &7Página " + page));

        ItemStack glass = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", Arrays.asList());
        ItemStack black = makeItem(Material.BLACK_STAINED_GLASS_PANE, " ", Arrays.asList());

        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45) inv.setItem(i, (i % 2 == 0 ? black : glass));
        }

        inv.setItem(4, makeGlowing(makeItem(Material.CHEST, "&6&lAdvancedKits", Arrays.asList("&7Sistema premium de kits"))));
        inv.setItem(45, makeItem(Material.ARROW, "&ePágina anterior", Arrays.asList("&7Ir a la página anterior")));
        inv.setItem(49, makeItem(Material.BOOK, "&eAyuda", Arrays.asList("&7Usa /akits help")));
        inv.setItem(50, makeItem(Material.DIAMOND, "&bPremium Kits", Arrays.asList("&7Kits premium del servidor")));
        inv.setItem(53, makeItem(Material.ARROW, "&ePágina siguiente", Arrays.asList("&7Ir a la página siguiente")));
        if (p.hasPermission("akits.admin")) {
            inv.setItem(48, makeItem(Material.COMPARATOR, "&6Panel Admin", Arrays.asList("&7Abrir administración")));
            inv.setItem(51, makeItem(Material.REDSTONE, "&cRecargar", Arrays.asList("&7Recargar plugin")));
        }

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, kits.size());
        int[] slots = {
                10,11,12,13,14,15,16,
                19,20,21,22,23,24,25,
                28,29,30,31,32,33,34
        };

        int index = 0;
        for (int i = start; i < end && index < slots.length; i++) {
            String kit = kits.get(i);
            YamlConfiguration cfg = getKitConfig(kit);
            if (cfg == null) continue;

            String type = cfg.getString("type", "default").toLowerCase();
            if (type.equals("premium")) {
                inv.setItem(slots[index], buildKitIcon(p, kit));
            } else {
                inv.setItem(slots[index], buildKitIcon(p, kit));
            }
            index++;
        }

        p.openInventory(inv);
        sound(p, Sound.BLOCK_CHEST_OPEN);
    }

    private void openAdminPanel(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&8✦ &6Panel de Kits"));

        ItemStack filler = makeItem(Material.BLACK_STAINED_GLASS_PANE, " ", Arrays.asList());
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45) inv.setItem(i, filler);
        }

        inv.setItem(13, makeGlowing(makeItem(Material.CHEST, "&aCrear Kit", Arrays.asList("&7Click para crear un nuevo kit"))));
        inv.setItem(20, makeItem(Material.BOOK, "&eEditar Kit", Arrays.asList("&7Seleccionar kit para editar")));
        inv.setItem(24, makeItem(Material.BARRIER, "&cEliminar Kit", Arrays.asList("&7Seleccionar kit para borrar")));
        inv.setItem(31, makeItem(Material.ENDER_CHEST, "&bLista de Kits", Arrays.asList("&7Ver todos los kits")));
        inv.setItem(40, makeItem(Material.REDSTONE, "&6Recargar Plugin", Arrays.asList("&7Recargar configuración")));
        inv.setItem(49, makeItem(Material.ARROW, "&eVolver", Arrays.asList("&7Regresar al menú principal")));

        p.openInventory(inv);
        sound(p, Sound.BLOCK_CHEST_OPEN);
    }

    private void openKitSelector(Player p, String mode) {
        List<String> kits = getAllKits();
        Inventory inv = Bukkit.createInventory(null, 54, color("&8Seleccionar kit • " + mode));

        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", Arrays.asList());
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);
        inv.setItem(49, makeItem(Material.ARROW, "&eVolver", Arrays.asList("&7Regresar")));

        int slot = 0;
        for (String kit : kits) {
            if (slot >= 45) break;
            inv.setItem(slot, buildKitIcon(p, kit));
            slot++;
        }

        p.openInventory(inv);
    }

    private void openDeleteConfirm(Player p, String kit) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&cConfirmar eliminación • " + kit));

        for (int i = 0; i < 27; i++) {
            inv.setItem(i, makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", Arrays.asList()));
        }

        inv.setItem(11, makeItem(Material.LIME_WOOL, "&aConfirmar", Arrays.asList("&7Eliminar kit &f" + kit)));
        inv.setItem(13, buildKitIcon(p, kit));
        inv.setItem(15, makeItem(Material.RED_WOOL, "&cCancelar", Arrays.asList("&7No borrar nada")));

        p.openInventory(inv);
    }

    private void openEditor(Player p, String kit) {
        editorKit.put(p.getUniqueId(), kit);
        YamlConfiguration cfg = getKitConfig(kit);
        if (cfg == null) return;

        Inventory inv = Bukkit.createInventory(null, 54, color("&8Editor • &6" + kit));

        ItemStack filler = makeItem(Material.BLACK_STAINED_GLASS_PANE, " ", Arrays.asList());
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        inv.setItem(10, makeItem(Material.NAME_TAG, "&eCambiar nombre visible", Arrays.asList("&7Actual: " + color(cfg.getString("name", "&e" + kit)))));
        inv.setItem(11, makeItem(Material.ITEM_FRAME, "&eCambiar icono", Arrays.asList("&7Actual: &f" + cfg.getString("icon", "CHEST"))));
        inv.setItem(12, makeItem(Material.WRITABLE_BOOK, "&eCambiar lore", Arrays.asList("&7Editar texto del lore")));
        inv.setItem(13, makeItem(Material.CLOCK, "&eCambiar cooldown", Arrays.asList("&7Actual: &f" + cfg.getLong("cooldown", 3600) + "s")));
        inv.setItem(14, makeItem(Material.DIAMOND, "&eCambiar tipo", Arrays.asList("&7Actual: &f" + cfg.getString("type", "default"))));
        inv.setItem(15, makeItem(Material.IRON_CHESTPLATE, "&eAuto-Equip", Arrays.asList(cfg.getBoolean("auto-equip", false) ? "&aActivado" : "&cDesactivado")));
        inv.setItem(16, makeItem(Material.BELL, "&eBroadcast", Arrays.asList(cfg.getBoolean("broadcast", false) ? "&aActivado" : "&cDesactivado")));
        inv.setItem(19, makeItem(Material.CHEST, "&eGuardar inventario", Arrays.asList("&7Guarda inventario, armadura y offhand")));
        inv.setItem(20, makeItem(Material.COMMAND_BLOCK, "&eEditar comandos", Arrays.asList("&7Editar recompensas por chat")));
        inv.setItem(21, makeItem(Material.TRIPWIRE_HOOK, "&eEditar permiso", Arrays.asList("&7Actual: &f" + cfg.getString("permission", "akits.kit." + kit))));
        inv.setItem(22, makeItem(Material.GLOWSTONE_DUST, "&eGlow", Arrays.asList(cfg.getBoolean("glow", false) ? "&aActivado" : "&cDesactivado")));
        inv.setItem(23, makeItem(Material.LIME_DYE, "&eActivar/Desactivar kit", Arrays.asList(cfg.getBoolean("enabled", true) ? "&aActivado" : "&cDesactivado")));
        inv.setItem(24, makeItem(Material.ENDER_CHEST, "&ePreview", Arrays.asList("&7Ver preview del kit")));
        inv.setItem(25, makeItem(Material.BOOKSHELF, "&eLista kits", Arrays.asList("&7Volver a selector")));
        inv.setItem(31, makeItem(Material.EMERALD_BLOCK, "&aGuardar y volver", Arrays.asList("&7Guardar cambios")));
        inv.setItem(49, makeItem(Material.ARROW, "&eVolver", Arrays.asList("&7Regresar")));

        p.openInventory(inv);
    }

    private void openPreview(Player p, String kit) {
        YamlConfiguration cfg = getKitConfig(kit);
        if (cfg == null) return;

        Inventory inv = Bukkit.createInventory(null, 54, color("&8Preview • &6" + kit));

        List<?> items = cfg.getList("items", new ArrayList<>());
        for (int i = 0; i < items.size() && i < 45; i++) {
            Object obj = items.get(i);
            if (obj instanceof ItemStack) inv.setItem(i, (ItemStack) obj);
        }

        inv.setItem(45, makeItem(Material.GREEN_WOOL, "&aReclamar", Arrays.asList("&7Click para reclamar")));
        inv.setItem(49, buildKitIcon(p, kit));
        inv.setItem(53, makeItem(Material.ARROW, "&eVolver", Arrays.asList("&7Regresar")));

        p.openInventory(inv);
    }

    private void claimKit(Player p, String kit) {
        YamlConfiguration cfg = getKitConfig(kit);
        if (cfg == null) {
            msg(p, "&cEse kit no existe.");
            sound(p, Sound.ENTITY_VILLAGER_NO);
            return;
        }

        if (!canUseKit(p, kit)) {
            msg(p, "&cNo tienes permiso para este kit.");
            sound(p, Sound.ENTITY_VILLAGER_NO);
            return;
        }

        long now = System.currentTimeMillis();
        long next = getCooldown(p.getUniqueId(), kit);
        if (next > now) {
            long left = (next - now) / 1000L;
            msg(p, getConfig().getString("settings.default-cooldown-message", "&eDebes esperar &f%time% &epara volver a reclamarlo.")
                    .replace("%time%", formatTime(left)));
            sound(p, Sound.ENTITY_VILLAGER_NO);
            return;
        }

        List<?> items = cfg.getList("items", new ArrayList<>());
        for (Object obj : items) {
            if (obj instanceof ItemStack) {
                p.getInventory().addItem(((ItemStack) obj).clone());
            }
        }

        if (cfg.getBoolean("auto-equip", false)) {
            List<?> armor = cfg.getList("armor", new ArrayList<>());
            if (armor.size() >= 4) {
                if (armor.get(0) instanceof ItemStack) p.getInventory().setHelmet((ItemStack) armor.get(0));
                if (armor.get(1) instanceof ItemStack) p.getInventory().setChestplate((ItemStack) armor.get(1));
                if (armor.get(2) instanceof ItemStack) p.getInventory().setLeggings((ItemStack) armor.get(2));
                if (armor.get(3) instanceof ItemStack) p.getInventory().setBoots((ItemStack) armor.get(3));
            }
            Object off = cfg.get("offhand");
            if (off instanceof ItemStack) {
                p.getInventory().setItemInOffHand((ItemStack) off);
            }
        }

        for (String command : cfg.getStringList("commands.console")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    command.replace("%player%", p.getName()).replace("%kit%", kit));
        }

        for (String command : cfg.getStringList("commands.player")) {
            p.performCommand(command.replace("%player%", p.getName()).replace("%kit%", kit));
        }

        long cooldownSeconds = cfg.getLong("cooldown", 3600);
        setCooldown(p.getUniqueId(), kit, now + (cooldownSeconds * 1000L));

        msg(p, "&aHas reclamado el kit &e" + kit + "&a.");
        sound(p, Sound.ENTITY_PLAYER_LEVELUP);

        if (cfg.getBoolean("broadcast", false)) {
            Bukkit.broadcastMessage(color(getConfig().getString("settings.broadcast-format", "&6%player% ha reclamado el kit premium &e%kit%&6!")
                    .replace("%player%", p.getName())
                    .replace("%kit%", kit)));
        }
    }

    private void saveInventoryToKit(Player p, String kit) {
        YamlConfiguration cfg = getKitConfig(kit);
        if (cfg == null) return;

        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (it != null && it.getType() != Material.AIR) items.add(it.clone());
        }

        List<ItemStack> armor = new ArrayList<>();
        armor.add(p.getInventory().getHelmet());
        armor.add(p.getInventory().getChestplate());
        armor.add(p.getInventory().getLeggings());
        armor.add(p.getInventory().getBoots());

        cfg.set("items", items);
        cfg.set("armor", armor);
        cfg.set("offhand", p.getInventory().getItemInOffHand());

        saveKitConfig(kit, cfg);
        logAdmin(p.getName() + " guardó inventario en kit " + kit);
        msg(p, "&aKit guardado correctamente.");
        sound(p, Sound.BLOCK_ANVIL_USE);
    }

    private void logAdmin(String text) {
        try {
            File file = new File(getDataFolder(), "admin-logs.txt");
            if (!file.exists()) file.createNewFile();
            java.io.FileWriter fw = new java.io.FileWriter(file, true);
            String line = "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()) + "] " + text + System.lineSeparator();
            fw.write(line);
            fw.flush();
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void msg(CommandSender sender, String s) {
        sender.sendMessage(prefix() + color(s));
    }

    private void sound(Player p, Sound sound) {
        try {
            p.playSound(p.getLocation(), sound, 1f, 1f);
        } catch (Exception ignored) {}
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!chatAction.containsKey(p.getUniqueId())) return;

        e.setCancelled(true);

        String action = chatAction.remove(p.getUniqueId());
        String kit = chatTargetKit.remove(p.getUniqueId());
        String msg = e.getMessage();

        Bukkit.getScheduler().runTask(this, () -> {
            if (action.equals("create-kit")) {
                if (kitExists(msg)) {
                    this.msg(p, "&cEse kit ya existe.");
                    sound(p, Sound.ENTITY_VILLAGER_NO);
                    return;
                }
                createKit(msg);
                logAdmin(p.getName() + " creó kit " + msg);
                this.msg(p, "&aKit &e" + msg + " &acreado correctamente.");
                sound(p, Sound.ENTITY_PLAYER_LEVELUP);
                openEditor(p, msg);
                return;
            }

            if (kit == null || !kitExists(kit)) {
                this.msg(p, "&cEl kit ya no existe.");
                return;
            }

            YamlConfiguration cfg = getKitConfig(kit);
            if (cfg == null) return;

            switch (action) {
                case "edit-name":
                    cfg.set("name", msg);
                    saveKitConfig(kit, cfg);
                    logAdmin(p.getName() + " cambió nombre visible de " + kit + " a " + msg);
                    this.msg(p, "&aNombre actualizado.");
                    sound(p, Sound.BLOCK_ANVIL_USE);
                    openEditor(p, kit);
                    break;

                case "edit-icon":
                    try {
                        Material.valueOf(msg.toUpperCase());
                        cfg.set("icon", msg.toUpperCase());
                        saveKitConfig(kit, cfg);
                        logAdmin(p.getName() + " cambió icono de " + kit + " a " + msg.toUpperCase());
                        this.msg(p, "&aIcono actualizado.");
                        sound(p, Sound.BLOCK_ANVIL_USE);
                    } catch (Exception ex) {
                        this.msg(p, "&cMaterial inválido.");
                        sound(p, Sound.ENTITY_VILLAGER_NO);
                    }
                    openEditor(p, kit);
                    break;

                case "edit-lore":
                    cfg.set("lore", Arrays.asList(msg.split("\\|")));
                    saveKitConfig(kit, cfg);
                    logAdmin(p.getName() + " cambió lore de " + kit);
                    this.msg(p, "&aLore actualizado. Usa | para separar líneas.");
                    sound(p, Sound.BLOCK_ANVIL_USE);
                    openEditor(p, kit);
                    break;

                case "edit-cooldown":
                    try {
                        long sec = Long.parseLong(msg);
                        cfg.set("cooldown", sec);
                        saveKitConfig(kit, cfg);
                        logAdmin(p.getName() + " cambió cooldown de " + kit + " a " + sec);
                        this.msg(p, "&aCooldown actualizado.");
                        sound(p, Sound.BLOCK_ANVIL_USE);
                    } catch (Exception ex) {
                        this.msg(p, "&cNúmero inválido.");
                        sound(p, Sound.ENTITY_VILLAGER_NO);
                    }
                    openEditor(p, kit);
                    break;

                case "edit-permission":
                    cfg.set("permission", msg);
                    saveKitConfig(kit, cfg);
                    logAdmin(p.getName() + " cambió permiso de " + kit + " a " + msg);
                    this.msg(p, "&aPermiso actualizado.");
                    sound(p, Sound.BLOCK_ANVIL_USE);
                    openEditor(p, kit);
                    break;

                case "edit-console-commands":
                    cfg.set("commands.console", Arrays.asList(msg.split("\\|")));
                    saveKitConfig(kit, cfg);
                    logAdmin(p.getName() + " cambió console commands de " + kit);
                    this.msg(p, "&aComandos de consola actualizados.");
                    sound(p, Sound.BLOCK_ANVIL_USE);
                    openEditor(p, kit);
                    break;

                case "edit-player-commands":
                    cfg.set("commands.player", Arrays.asList(msg.split("\\|")));
                    saveKitConfig(kit, cfg);
                    logAdmin(p.getName() + " cambió player commands de " + kit);
                    this.msg(p, "&aComandos de jugador actualizados.");
                    sound(p, Sound.BLOCK_ANVIL_USE);
                    openEditor(p, kit);
                    break;
            }
        });
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        String title = e.getView().getTitle();
        ItemStack item = e.getCurrentItem();

        if (item == null || item.getType() == Material.AIR) return;

        if (title.contains("AdvancedKits") || title.contains("Panel de Kits") || title.contains("Seleccionar kit")
                || title.contains("Confirmar eliminación") || title.contains("Editor •") || title.contains("Preview •")) {
            e.setCancelled(true);
        }

        if (title.contains("AdvancedKits")) {
            int slot = e.getRawSlot();

            if (slot == 45) {
                openMainMenu(p, playerPage.getOrDefault(p.getUniqueId(), 1) - 1);
                sound(p, Sound.ITEM_BOOK_PAGE_TURN);
                return;
            }
            if (slot == 53) {
                openMainMenu(p, playerPage.getOrDefault(p.getUniqueId(), 1) + 1);
                sound(p, Sound.ITEM_BOOK_PAGE_TURN);
                return;
            }
            if (slot == 48 && p.hasPermission("akits.admin")) {
                openAdminPanel(p);
                return;
            }
            if (slot == 49) {
                msg(p, "&eUsa /akits help para ver ayuda.");
                sound(p, Sound.UI_BUTTON_CLICK);
                return;
            }
            if (slot == 51 && p.hasPermission("akits.admin")) {
                reloadConfig();
                msg(p, "&aPlugin recargado.");
                sound(p, Sound.BLOCK_ANVIL_USE);
                return;
            }

            String clickedKit = getKitByDisplay(item);
            if (clickedKit != null) {
                if (e.isLeftClick()) {
                    claimKit(p, clickedKit);
                } else if (e.isRightClick()) {
                    openPreview(p, clickedKit);
                }
            }
            return;
        }

        if (title.contains("Panel de Kits")) {
            switch (e.getRawSlot()) {
                case 13:
                    chatAction.put(p.getUniqueId(), "create-kit");
                    chatTargetKit.remove(p.getUniqueId());
                    p.closeInventory();
                    msg(p, "&eEscribe en el chat el nombre del kit que quieres crear.");
                    sound(p, Sound.UI_BUTTON_CLICK);
                    break;
                case 20:
                    openKitSelector(p, "edit");
                    break;
                case 24:
                    openKitSelector(p, "delete");
                    break;
                case 31:
                    openKitSelector(p, "list");
                    break;
                case 40:
                    reloadConfig();
                    msg(p, "&aPlugin recargado.");
                    sound(p, Sound.BLOCK_ANVIL_USE);
                    break;
                case 49:
                    openMainMenu(p, 1);
                    sound(p, Sound.ITEM_BOOK_PAGE_TURN);
                    break;
            }
            return;
        }

        if (title.contains("Seleccionar kit")) {
            if (e.getRawSlot() == 49) {
                openAdminPanel(p);
                return;
            }

            String mode = title.substring(title.lastIndexOf("•") + 1).trim();
            String clickedKit = getKitByDisplay(item);
            if (clickedKit == null) return;

            if (mode.equalsIgnoreCase("edit")) {
                openEditor(p, clickedKit);
            } else if (mode.equalsIgnoreCase("delete")) {
                openDeleteConfirm(p, clickedKit);
            } else {
                openPreview(p, clickedKit);
            }
            return;
        }

        if (title.contains("Confirmar eliminación")) {
            String kit = title.substring(title.lastIndexOf("•") + 1).trim();

            if (e.getRawSlot() == 11) {
                File file = getKitFile(kit);
                if (file.exists() && file.delete()) {
                    logAdmin(p.getName() + " eliminó kit " + kit);
                    msg(p, "&cEl kit fue eliminado.");
                    sound(p, Sound.ENTITY_ITEM_BREAK);
                    openAdminPanel(p);
                } else {
                    msg(p, "&cNo se pudo eliminar.");
                    sound(p, Sound.ENTITY_VILLAGER_NO);
                }
            } else if (e.getRawSlot() == 15) {
                openAdminPanel(p);
                sound(p, Sound.ITEM_BOOK_PAGE_TURN);
            }
            return;
        }

        if (title.contains("Editor •")) {
            String kit = title.substring(title.lastIndexOf("•") + 1).trim();
            YamlConfiguration cfg = getKitConfig(kit);
            if (cfg == null) return;

            switch (e.getRawSlot()) {
                case 10:
                    chatAction.put(p.getUniqueId(), "edit-name");
                    chatTargetKit.put(p.getUniqueId(), kit);
                    p.closeInventory();
                    msg(p, "&eEscribe el nuevo nombre visible en el chat.");
                    break;
                case 11:
                    chatAction.put(p.getUniqueId(), "edit-icon");
                    chatTargetKit.put(p.getUniqueId(), kit);
                    p.closeInventory();
                    msg(p, "&eEscribe el material del icono. Ejemplo: DIAMOND_SWORD");
                    break;
                case 12:
                    chatAction.put(p.getUniqueId(), "edit-lore");
                    chatTargetKit.put(p.getUniqueId(), kit);
                    p.closeInventory();
                    msg(p, "&eEscribe el lore usando | para separar líneas.");
                    break;
                case 13:
                    chatAction.put(p.getUniqueId(), "edit-cooldown");
                    chatTargetKit.put(p.getUniqueId(), kit);
                    p.closeInventory();
                    msg(p, "&eEscribe el cooldown en segundos.");
                    break;
                case 14:
                    String currentType = cfg.getString("type", "default").equalsIgnoreCase("default") ? "premium" : "default";
                    cfg.set("type", currentType);
                    saveKitConfig(kit, cfg);
                    logAdmin(p.getName() + " cambió tipo de " + kit + " a " + currentType);
                    msg(p, "&aTipo cambiado a &e" + currentType + "&a.");
                    sound(p, Sound.BLOCK_ANVIL_USE);
                    openEditor(p, kit);
                    break;
                case 15:
                    cfg.set("auto-equip", !cfg.getBoolean("auto-equip", false));
                    saveKitConfig(kit, cfg);
                    logAdmin(p.getName() + " cambió auto-equip de " + kit);
                    msg(p, "&aAuto-equip actualizado.");
                    sound(p, Sound.BLOCK_ANVIL_USE);
                    openEditor(p, kit);
                    break;
                case 16:
                    cfg.set("broadcast", !cfg.getBoolean("broadcast", false));
                    saveKitConfig(kit, cfg);
                    logAdmin(p.getName() + " cambió broadcast de " + kit);
                    msg(p, "&aBroadcast actualizado.");
                    sound(p, Sound.BLOCK_ANVIL_USE);
                    openEditor(p, kit);
                    break;
                case 19:
                    saveInventoryToKit(p, kit);
                    openEditor(p, kit);
                    break;
                case 20:
                    if (e.isLeftClick()) {
                        chatAction.put(p.getUniqueId(), "edit-console-commands");
                        chatTargetKit.put(p.getUniqueId(), kit);
                        p.closeInventory();
                        msg(p, "&eEscribe comandos de consola separados por |");
                    } else if (e.isRightClick()) {
                        chatAction.put(p.getUniqueId(), "edit-player-commands");
                        chatTargetKit.put(p.getUniqueId(), kit);
                        p.closeInventory();
                        msg(p, "&eEscribe comandos de jugador separados por |");
                    }
                    break;
                case 21:
                    chatAction.put(p.getUniqueId(), "edit-permission");
                    chatTargetKit.put(p.getUniqueId(), kit);
                    p.closeInventory();
                    msg(p, "&eEscribe el nuevo permiso del kit.");
                    break;
                case 22:
                    cfg.set("glow", !cfg.getBoolean("glow", false));
                    saveKitConfig(kit, cfg);
                    logAdmin(p.getName() + " cambió glow de " + kit);
                    msg(p, "&aGlow actualizado.");
                    sound(p, Sound.BLOCK_ANVIL_USE);
                    openEditor(p, kit);
                    break;
                case 23:
                    cfg.set("enabled", !cfg.getBoolean("enabled", true));
                    saveKitConfig(kit, cfg);
                    logAdmin(p.getName() + " cambió enabled de " + kit);
                    msg(p, "&aEstado del kit actualizado.");
                    sound(p, Sound.BLOCK_ANVIL_USE);
                    openEditor(p, kit);
                    break;
                case 24:
                    openPreview(p, kit);
                    break;
                case 25:
                    openKitSelector(p, "edit");
                    break;
                case 31:
                    msg(p, "&aCambios guardados.");
                    sound(p, Sound.BLOCK_ANVIL_USE);
                    openAdminPanel(p);
                    break;
                case 49:
                    openAdminPanel(p);
                    sound(p, Sound.ITEM_BOOK_PAGE_TURN);
                    break;
            }
            return;
        }

        if (title.contains("Preview •")) {
            String kit = title.substring(title.lastIndexOf("•") + 1).trim();

            if (e.getRawSlot() == 45) {
                claimKit(p, kit);
                return;
            }
            if (e.getRawSlot() == 53) {
                openMainMenu(p, playerPage.getOrDefault(p.getUniqueId(), 1));
            }
        }
    }

    private String getKitByDisplay(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getDisplayName() == null) return null;
        String display = ChatColor.stripColor(meta.getDisplayName());

        for (String kit : getAllKits()) {
            YamlConfiguration cfg = getKitConfig(kit);
            if (cfg == null) continue;
            String n = ChatColor.stripColor(color(cfg.getString("name", "&e" + kit)));
            if (n.equalsIgnoreCase(display)) return kit;
        }
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player) && args.length == 0) {
            sender.sendMessage("Usa /akits help");
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Solo jugadores.");
                return true;
            }
            Player p = (Player) sender;
            openMainMenu(p, 1);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "help":
                msg(sender, "&e/akits");
                msg(sender, "&e/akits help");
                msg(sender, "&e/akits list");
                msg(sender, "&e/akits version");
                msg(sender, "&e/akits reload");
                msg(sender, "&e/akits panel");
                msg(sender, "&e/akits create <nombre>");
                msg(sender, "&e/akits edit <nombre>");
                msg(sender, "&e/akits delete <nombre>");
                msg(sender, "&e/akits preview <kit>");
                msg(sender, "&e/akits claim <kit>");
                msg(sender, "&e/akits give <jugador> <kit>");
                msg(sender, "&e/akits resetcooldown <jugador> <kit>");
                return true;

            case "list":
                msg(sender, "&7Kits: &f" + String.join(", ", getAllKits()));
                return true;

            case "version":
            case "vercion":
                msg(sender, "&aAdvancedKits versión: &f" + getDescription().getVersion());
                return true;

            case "reload":
                if (!sender.hasPermission("akits.admin")) {
                    msg(sender, "&cNo tienes permiso.");
                    return true;
                }
                reloadConfig();
                msg(sender, "&aPlugin recargado.");
                return true;

            case "panel":
                if (!(sender instanceof Player)) {
                    msg(sender, "&cSolo jugadores.");
                    return true;
                }
                if (!sender.hasPermission("akits.admin")) {
                    msg(sender, "&cNo tienes permiso.");
                    return true;
                }
                openAdminPanel((Player) sender);
                return true;

            case "create":
                if (!sender.hasPermission("akits.admin")) {
                    msg(sender, "&cNo tienes permiso.");
                    return true;
                }
                if (args.length < 2) {
                    msg(sender, "&cUsa: /akits create <nombre>");
                    return true;
                }
                if (kitExists(args[1])) {
                    msg(sender, "&cEse kit ya existe.");
                    return true;
                }
                createKit(args[1]);
                logAdmin(sender.getName() + " creó kit " + args[1]);
                msg(sender, "&aKit creado: &e" + args[1]);
                return true;

            case "edit":
                if (!(sender instanceof Player)) {
                    msg(sender, "&cSolo jugadores.");
                    return true;
                }
                if (!sender.hasPermission("akits.admin")) {
                    msg(sender, "&cNo tienes permiso.");
                    return true;
                }
                if (args.length < 2 || !kitExists(args[1])) {
                    msg(sender, "&cEse kit no existe.");
                    return true;
                }
                openEditor((Player) sender, args[1]);
                return true;

            case "delete":
                if (!(sender instanceof Player)) {
                    msg(sender, "&cSolo jugadores.");
                    return true;
                }
                if (!sender.hasPermission("akits.admin")) {
                    msg(sender, "&cNo tienes permiso.");
                    return true;
                }
                if (args.length < 2 || !kitExists(args[1])) {
                    msg(sender, "&cEse kit no existe.");
                    return true;
                }
                openDeleteConfirm((Player) sender, args[1]);
                return true;

            case "preview":
                if (!(sender instanceof Player)) {
                    msg(sender, "&cSolo jugadores.");
                    return true;
                }
                if (args.length < 2 || !kitExists(args[1])) {
                    msg(sender, "&cEse kit no existe.");
                    return true;
                }
                openPreview((Player) sender, args[1]);
                return true;

            case "claim":
                if (!(sender instanceof Player)) {
                    msg(sender, "&cSolo jugadores.");
                    return true;
                }
                if (args.length < 2 || !kitExists(args[1])) {
                    msg(sender, "&cEse kit no existe.");
                    return true;
                }
                claimKit((Player) sender, args[1]);
                return true;

            case "give":
                if (!sender.hasPermission("akits.admin")) {
                    msg(sender, "&cNo tienes permiso.");
                    return true;
                }
                if (args.length < 3) {
                    msg(sender, "&cUsa: /akits give <jugador> <kit>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    msg(sender, "&cJugador no encontrado.");
                    return true;
                }
                if (!kitExists(args[2])) {
                    msg(sender, "&cKit no existe.");
                    return true;
                }
                giveKitDirect(target, args[2]);
                msg(sender, "&aKit dado correctamente.");
                msg(target, "&aHas recibido el kit &e" + args[2] + "&a.");
                return true;

            case "resetcooldown":
                if (!sender.hasPermission("akits.admin")) {
                    msg(sender, "&cNo tienes permiso.");
                    return true;
                }
                if (args.length < 3) {
                    msg(sender, "&cUsa: /akits resetcooldown <jugador> <kit>");
                    return true;
                }
                OfflinePlayer off = Bukkit.getOfflinePlayer(args[1]);
                resetCooldown(off.getUniqueId(), args[2]);
                msg(sender, "&aCooldown reiniciado.");
                return true;
        }

        msg(sender, "&cSubcomando desconocido. Usa /akits help");
        return true;
    }

    private void giveKitDirect(Player p, String kit) {
        YamlConfiguration cfg = getKitConfig(kit);
        if (cfg == null) return;

        List<?> items = cfg.getList("items", new ArrayList<>());
        for (Object obj : items) {
            if (obj instanceof ItemStack) p.getInventory().addItem(((ItemStack) obj).clone());
        }

        if (cfg.getBoolean("auto-equip", false)) {
            List<?> armor = cfg.getList("armor", new ArrayList<>());
            if (armor.size() >= 4) {
                if (armor.get(0) instanceof ItemStack) p.getInventory().setHelmet((ItemStack) armor.get(0));
                if (armor.get(1) instanceof ItemStack) p.getInventory().setChestplate((ItemStack) armor.get(1));
                if (armor.get(2) instanceof ItemStack) p.getInventory().setLeggings((ItemStack) armor.get(2));
                if (armor.get(3) instanceof ItemStack) p.getInventory().setBoots((ItemStack) armor.get(3));
            }
            Object off = cfg.get("offhand");
            if (off instanceof ItemStack) p.getInventory().setItemInOffHand((ItemStack) off);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(Arrays.asList("help","list","version","vercion","reload","panel","create","edit","delete","preview","claim","give","resetcooldown"));
        } else if (args.length == 2 && Arrays.asList("edit","delete","preview","claim").contains(args[0].toLowerCase())) {
            out.addAll(getAllKits());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (args.length == 3 && Arrays.asList("give","resetcooldown").contains(args[0].toLowerCase())) {
            out.addAll(getAllKits());
        }
        return out;
    }

    public class AKitsPlaceholder extends PlaceholderExpansion {
        private final Principal plugin;

        public AKitsPlaceholder(Principal plugin) {
            this.plugin = plugin;
        }

        @Override
        public String getIdentifier() {
            return "advancedkits";
        }

        @Override
        public String getAuthor() {
            return "Adrian";
        }

        @Override
        public String getVersion() {
            return plugin.getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onPlaceholderRequest(Player p, String params) {
            if (p == null) return "";

            if (params.startsWith("cooldown_")) {
                String kit = params.substring("cooldown_".length());
                long next = getCooldown(p.getUniqueId(), kit);
                long now = System.currentTimeMillis();
                if (next <= now) return "Listo";
                return formatTime((next - now) / 1000L);
            }

            if (params.startsWith("status_")) {
                String kit = params.substring("status_".length());
                return ChatColor.stripColor(color(getStatus(p, kit)));
            }

            if (params.startsWith("can_claim_")) {
                String kit = params.substring("can_claim_".length());
                long next = getCooldown(p.getUniqueId(), kit);
                return (canUseKit(p, kit) && next <= System.currentTimeMillis()) ? "true" : "false";
            }

            return "";
        }
    }
}
