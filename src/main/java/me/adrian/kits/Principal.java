package me.adrian.kits;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class Principal extends JavaPlugin implements Listener, TabExecutor {

    private File kitsFolder;
    private File messagesFile;
    private YamlConfiguration messages;
    private Connection connection;

    private final Map<UUID, String> deleteConfirm = new HashMap<>();
    private final Map<UUID, String> chatEditingIcon = new HashMap<>();

    private NamespacedKey actionKey;
    private NamespacedKey kitKey;
    private NamespacedKey pageKey;
    private NamespacedKey categoryKey;

    private final int MENU_SIZE = 54;
    private final int[] KIT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final int PREVIOUS_SLOT = 45;
    private final int INFO_SLOT = 49;
    private final int NEXT_SLOT = 53;
    private final int NORMAL_SLOT = 46;
    private final int PREMIUM_SLOT = 47;
    private final int EVENTO_SLOT = 51;
    private final int DONADOR_SLOT = 52;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        kitsFolder = new File(getDataFolder(), "kits");
        if (!kitsFolder.exists()) kitsFolder.mkdirs();

        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResourceSafe("messages.yml");
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);

        actionKey = new NamespacedKey(this, "ak_action");
        kitKey = new NamespacedKey(this, "ak_kit");
        pageKey = new NamespacedKey(this, "ak_page");
        categoryKey = new NamespacedKey(this, "ak_category");

        setupDatabase();

        if (getCommand("akits") != null) {
            getCommand("akits").setExecutor(this);
            getCommand("akits").setTabCompleter(this);
        }

        Bukkit.getPluginManager().registerEvents(this, this);

        getLogger().info("AdvancedKits / Principal.java cargado correctamente.");
    }

    @Override
    public void onDisable() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (Exception ignored) {
        }
    }

    private void saveResourceSafe(String path) {
        try {
            saveResource(path, false);
        } catch (Exception ignored) {
        }
    }

    private void setupDatabase() {
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            File dbFile = new File(getDataFolder(), "data.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            Statement st = connection.createStatement();
            st.executeUpdate("CREATE TABLE IF NOT EXISTS cooldowns (" +
                    "uuid TEXT NOT NULL," +
                    "kit TEXT NOT NULL," +
                    "next_claim BIGINT NOT NULL," +
                    "PRIMARY KEY (uuid, kit)" +
                    ");");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS admin_logs (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "admin TEXT NOT NULL," +
                    "action TEXT NOT NULL," +
                    "kit TEXT NOT NULL," +
                    "time BIGINT NOT NULL" +
                    ");");
            st.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void logAdmin(String admin, String action, String kit) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO admin_logs(admin, action, kit, time) VALUES(?,?,?,?)"
            );
            ps.setString(1, admin);
            ps.setString(2, action);
            ps.setString(3, kit);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
            ps.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private long getNextClaim(UUID uuid, String kit) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT next_claim FROM cooldowns WHERE uuid=? AND kit=?"
            );
            ps.setString(1, uuid.toString());
            ps.setString(2, kit.toLowerCase());
            ResultSet rs = ps.executeQuery();
            long value = 0L;
            if (rs.next()) value = rs.getLong("next_claim");
            rs.close();
            ps.close();
            return value;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0L;
    }

    private void setNextClaim(UUID uuid, String kit, long next) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO cooldowns(uuid, kit, next_claim) VALUES(?,?,?)"
            );
            ps.setString(1, uuid.toString());
            ps.setString(2, kit.toLowerCase());
            ps.setLong(3, next);
            ps.executeUpdate();
            ps.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void resetCooldown(UUID uuid, String kit) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM cooldowns WHERE uuid=? AND kit=?"
            );
            ps.setString(1, uuid.toString());
            ps.setString(2, kit.toLowerCase());
            ps.executeUpdate();
            ps.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private List<String> color(List<String> list) {
        return list.stream().map(this::color).collect(Collectors.toList());
    }

    private File getKitFile(String name) {
        return new File(kitsFolder, name.toLowerCase() + ".yml");
    }

    private boolean kitExists(String name) {
        return getKitFile(name).exists();
    }

    private YamlConfiguration getKitConfig(String name) {
        return YamlConfiguration.loadConfiguration(getKitFile(name));
    }

    private void saveKitConfig(String name, YamlConfiguration cfg) {
        try {
            cfg.save(getKitFile(name));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<String> getKitNames() {
        List<String> list = new ArrayList<>();
        File[] files = kitsFolder.listFiles();
        if (files == null) return list;
        for (File file : files) {
            if (file.getName().endsWith(".yml")) {
                list.add(file.getName().replace(".yml", ""));
            }
        }
        list.sort(String::compareToIgnoreCase);
        return list;
    }

    private ItemStack createItem(Material material, String name, List<String> lore, boolean glow) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore != null) meta.setLore(color(lore));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            if (glow) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            }
            item.setItemMeta(meta);
        }
        if (glow) {
            ItemMeta meta2 = item.getItemMeta();
            if (meta2 != null) {
                meta2.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta2);
            }
        }
        return item;
    }

    private ItemStack createButton(Material material, String name, List<String> lore, String action, String kit, Integer page, String category, boolean glow) {
        ItemStack item = createItem(material, name, lore, glow);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (action != null) pdc.set(actionKey, PersistentDataType.STRING, action);
            if (kit != null) pdc.set(kitKey, PersistentDataType.STRING, kit);
            if (page != null) pdc.set(pageKey, PersistentDataType.INTEGER, page);
            if (category != null) pdc.set(categoryKey, PersistentDataType.STRING, category);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatTime(long ms) {
        if (ms <= 0) return "0s";
        long total = ms / 1000L;
        long days = total / 86400;
        long hours = (total % 86400) / 3600;
        long mins = (total % 3600) / 60;
        long secs = total % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (mins > 0) sb.append(mins).append("m ");
        if (secs > 0 && days == 0) sb.append(secs).append("s");
        return sb.toString().trim();
    }

    private String getCategory(String kit) {
        YamlConfiguration cfg = getKitConfig(kit);
        return cfg.getString("category", "NORMAL").toUpperCase();
    }

    private long getCooldown(String kit) {
        YamlConfiguration cfg = getKitConfig(kit);
        return cfg.getLong("cooldown", 0L);
    }

    private boolean canUseKit(Player player, String kit) {
        if (player.hasPermission("akits.admin")) return true;
        if (!player.hasPermission("akits.use")) return false;
        if (!player.hasPermission("akits.kit." + kit.toLowerCase())) return false;

        String category = getCategory(kit);
        if (category.equalsIgnoreCase("PREMIUM") && !player.hasPermission("akits.premium")) return false;
        return true;
    }

    private boolean isUnlocked(Player player, String kit) {
        return canUseKit(player, kit);
    }

    private void createDefaultKit(String name, Player creator) {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("name", "&b" + name);
        cfg.set("material", "CHEST");
        cfg.set("slot", 10);
        cfg.set("category", "NORMAL");
        cfg.set("cooldown", 3600);
        cfg.set("broadcast", false);
        cfg.set("glow", true);
        cfg.set("permission", "akits.kit." + name.toLowerCase());
        cfg.set("lore", Arrays.asList(
                "&7Kit: &f" + name,
                "&7Categoría: &fNORMAL",
                "&7Click izquierdo: &aReclamar",
                "&7Click derecho: &bPreview"
        ));
        cfg.set("items", new ArrayList<>());
        cfg.set("commands", new ArrayList<>());
        saveKitConfig(name, cfg);
        logAdmin(creator.getName(), "CREATE", name);
    }

    private void giveKit(Player player, String kit) {
        YamlConfiguration cfg = getKitConfig(kit);

        List<?> rawItems = cfg.getList("items", new ArrayList<>());
        for (Object obj : rawItems) {
            if (obj instanceof ItemStack item) {
                player.getInventory().addItem(item);
            }
        }

        List<String> commands = cfg.getStringList("commands");
        for (String cmd : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    cmd.replace("%player%", player.getName()));
        }
    }

    private boolean claimKit(Player player, String kit, boolean ignoreCooldown) {
        if (!kitExists(kit)) {
            player.sendMessage(color("&cEse kit no existe."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }

        if (!canUseKit(player, kit)) {
            player.sendMessage(color("&cNo tienes permiso para usar este kit."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }

        long next = getNextClaim(player.getUniqueId(), kit);
        long now = System.currentTimeMillis();

        if (!ignoreCooldown && next > now && !player.hasPermission("akits.admin")) {
            long left = next - now;
            player.sendMessage(color("&cNo puedes reclamar este kit aún. &7Te faltan &f" + formatTime(left)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            return false;
        }

        giveKit(player, kit);

        long cooldown = getCooldown(kit);
        setNextClaim(player.getUniqueId(), kit, now + (cooldown * 1000L));

        player.sendMessage(color("&aHas reclamado el kit &f" + kit + "&a."));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);

        YamlConfiguration cfg = getKitConfig(kit);
        boolean broadcast = cfg.getBoolean("broadcast", false);
        String category = cfg.getString("category", "NORMAL").toUpperCase();

        if (broadcast || category.equals("PREMIUM") || category.equals("EVENTO") || category.equals("DONADOR")) {
            Bukkit.broadcastMessage(color("&8[&bAdvancedKits&8] &f" + player.getName() + " &aha reclamado el kit &b" + kit));
        }

        return true;
    }

    private void openMainMenu(Player player, int page, String categoryFilter) {
        Inventory inv = Bukkit.createInventory((InventoryHolder) null, MENU_SIZE,
                color("&8✦ &bPanel de Kits &8| &7" + categoryFilter + " &8| &fPágina " + page));

        fillBorders(inv);

        inv.setItem(INFO_SLOT, createButton(
                Material.NETHER_STAR,
                "&b&lAdvancedKits",
                Arrays.asList(
                        "&7Sistema premium de kits",
                        "&7Izquierdo: &aReclamar",
                        "&7Derecho: &bPreview",
                        "&7Categoría actual: &f" + categoryFilter
                ),
                "info", null, page, categoryFilter, true
        ));

        inv.setItem(PREVIOUS_SLOT, createButton(
                Material.ARROW,
                "&cPágina anterior",
                Collections.singletonList("&7Ir a la página anterior"),
                "previous_page", null, page, categoryFilter, false
        ));

        inv.setItem(NEXT_SLOT, createButton(
                Material.ARROW,
                "&aPágina siguiente",
                Collections.singletonList("&7Ir a la página siguiente"),
                "next_page", null, page, categoryFilter, false
        ));

        inv.setItem(NORMAL_SLOT, createButton(
                Material.CHEST,
                "&eKits Normales",
                Collections.singletonList("&7Ver categoría NORMAL"),
                "filter", null, 1, "NORMAL", false
        ));

        inv.setItem(PREMIUM_SLOT, createButton(
                Material.DIAMOND,
                "&bPremium Kits",
                Collections.singletonList("&7Ver categoría PREMIUM"),
                "filter", null, 1, "PREMIUM", true
        ));

        inv.setItem(EVENTO_SLOT, createButton(
                Material.FIREWORK_STAR,
                "&dKits Evento",
                Collections.singletonList("&7Ver categoría EVENTO"),
                "filter", null, 1, "EVENTO", true
        ));

        inv.setItem(DONADOR_SLOT, createButton(
                Material.EMERALD,
                "&aKits Donador",
                Collections.singletonList("&7Ver categoría DONADOR"),
                "filter", null, 1, "DONADOR", true
        ));

        List<String> kits = getKitNames().stream()
                .filter(k -> categoryFilter.equalsIgnoreCase("TODOS") || getCategory(k).equalsIgnoreCase(categoryFilter))
                .collect(Collectors.toList());

        int perPage = KIT_SLOTS.length;
        int maxPages = Math.max(1, (int) Math.ceil((double) kits.size() / perPage));
        if (page < 1) page = 1;
        if (page > maxPages) page = maxPages;

        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, kits.size());
        List<String> sub = kits.subList(start, end);

        for (int i = 0; i < sub.size(); i++) {
            String kit = sub.get(i);
            YamlConfiguration cfg = getKitConfig(kit);

            Material mat;
            try {
                mat = Material.valueOf(cfg.getString("material", "CHEST").toUpperCase());
            } catch (Exception e) {
                mat = Material.CHEST;
            }

            String display = color(cfg.getString("name", "&b" + kit));
            boolean glow = cfg.getBoolean("glow", true);

            boolean unlocked = isUnlocked(player, kit);
            long next = getNextClaim(player.getUniqueId(), kit);
            long now = System.currentTimeMillis();
            boolean cooldown = next > now;
            String remaining = cooldown ? formatTime(next - now) : "&aDisponible";

            List<String> lore = new ArrayList<>();
            lore.add("&7Nombre: &f" + kit);
            lore.add("&7Estado: " + (unlocked ? "&aDesbloqueado" : "&cBloqueado"));
            lore.add("&7Categoría: &f" + getCategory(kit));
            lore.add("&7Permiso: &f" + cfg.getString("permission", "akits.kit." + kit));
            lore.add("&7Cooldown: " + (cooldown ? "&c" + remaining : "&aListo"));
            lore.add(" ");
            lore.add("&eClick izquierdo: &aReclamar");
            lore.add("&eClick derecho: &bPreview");

            if (!unlocked) {
                lore.add(" ");
                lore.add("&cNo puedes reclamar este kit.");
            }

            ItemStack item = createButton(mat, display, lore, "kit", kit, page, categoryFilter, glow);
            inv.setItem(KIT_SLOTS[i], item);
        }

        player.openInventory(inv);
    }

    private void openPreview(Player player, String kit) {
        if (!kitExists(kit)) {
            player.sendMessage(color("&cEse kit no existe."));
            return;
        }

        Inventory inv = Bukkit.createInventory((InventoryHolder) null, 54, color("&8Preview Kit: &b" + kit));
        fillBorders(inv);

        YamlConfiguration cfg = getKitConfig(kit);
        List<?> rawItems = cfg.getList("items", new ArrayList<>());
        int slot = 10;

        for (Object obj : rawItems) {
            if (obj instanceof ItemStack item) {
                while (slot < 44 && isBorder(slot)) slot++;
                if (slot >= 44) break;
                inv.setItem(slot, item);
                slot++;
            }
        }

        inv.setItem(45, createButton(Material.ARROW, "&cVolver", Collections.singletonList("&7Regresar al panel"), "back_main", kit, 1, "TODOS", false));
        inv.setItem(49, createButton(Material.CHEST, "&aReclamar kit", Arrays.asList("&7Kit: &f" + kit, "&7Click para reclamar"), "claim_preview", kit, 1, "TODOS", true));
        inv.setItem(53, createButton(Material.BARRIER, "&cCerrar", Collections.singletonList("&7Cerrar menú"), "close", kit, 1, "TODOS", false));

        player.openInventory(inv);
    }

    private void openAdminEditor(Player player, String kit) {
        if (!kitExists(kit)) {
            player.sendMessage(color("&cEse kit no existe."));
            return;
        }

        Inventory inv = Bukkit.createInventory((InventoryHolder) null, 54, color("&8Configurar Kit: &b" + kit));
        fillBorders(inv);

        inv.setItem(20, createButton(Material.NAME_TAG, "&eCambiar icono", Arrays.asList("&7Escribe un material en el chat", "&7Ejemplo: DIAMOND"), "edit_icon", kit, 1, "TODOS", false));
        inv.setItem(22, createButton(Material.CHEST, "&aEditar items", Arrays.asList("&7Tu inventario actual se guardará", "&7como items del kit"), "save_inventory_items", kit, 1, "TODOS", true));
        inv.setItem(24, createButton(Material.CLOCK, "&bCambiar cooldown", Arrays.asList("&7Edita el archivo yml", "&7o mejora esto después"), "cooldown_info", kit, 1, "TODOS", false));
        inv.setItem(30, createButton(Material.EMERALD, "&aCambiar a NORMAL", Collections.singletonList("&7Cambiar categoría"), "category_normal", kit, 1, "TODOS", false));
        inv.setItem(31, createButton(Material.DIAMOND, "&bCambiar a PREMIUM", Collections.singletonList("&7Cambiar categoría"), "category_premium", kit, 1, "TODOS", true));
        inv.setItem(32, createButton(Material.FIREWORK_STAR, "&dCambiar a EVENTO", Collections.singletonList("&7Cambiar categoría"), "category_evento", kit, 1, "TODOS", true));
        inv.setItem(33, createButton(Material.GOLD_INGOT, "&6Cambiar a DONADOR", Collections.singletonList("&7Cambiar categoría"), "category_donador", kit, 1, "TODOS", true));
        inv.setItem(49, createButton(Material.BARRIER, "&cEliminar kit", Arrays.asList("&7Se pedirá confirmación", "&cNo se borra al instante"), "delete_request", kit, 1, "TODOS", false));
        inv.setItem(45, createButton(Material.ARROW, "&cVolver", Collections.singletonList("&7Regresar al panel"), "back_main", kit, 1, "TODOS", false));

        player.openInventory(inv);
    }

    private void fillBorders(Inventory inv) {
        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null, false);
        for (int i = 0; i < inv.getSize(); i++) {
            if (isBorder(i)) inv.setItem(i, border);
        }
    }

    private boolean isBorder(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        return row == 0 || row == 5 || col == 0 || col == 8;
    }

    private String getAction(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }

    private String getKitFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(kitKey, PersistentDataType.STRING);
    }

    private Integer getPageFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(pageKey, PersistentDataType.INTEGER);
    }

    private String getCategoryFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(categoryKey, PersistentDataType.STRING);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        HumanEntity clicker = e.getWhoClicked();
        if (!(clicker instanceof Player player)) return;
        if (e.getCurrentItem() == null) return;

        String title = e.getView().getTitle();
        if (!ChatColor.stripColor(title).contains("Panel de Kits")
                && !ChatColor.stripColor(title).contains("Preview Kit:")
                && !ChatColor.stripColor(title).contains("Configurar Kit:")) {
            return;
        }

        e.setCancelled(true);

        ItemStack item = e.getCurrentItem();
        String action = getAction(item);
        String kit = getKitFromItem(item);
        Integer page = getPageFromItem(item);
        String category = getCategoryFromItem(item);

        if (action == null) {
            if (ChatColor.stripColor(title).contains("Panel de Kits") && kit != null) {
                if (e.isLeftClick()) {
                    claimKit(player, kit, false);
                } else if (e.isRightClick()) {
                    openPreview(player, kit);
                }
            }
            return;
        }

        switch (action.toLowerCase()) {
            case "previous_page" -> openMainMenu(player, (page == null ? 1 : page) - 1, category == null ? "TODOS" : category);
            case "next_page" -> openMainMenu(player, (page == null ? 1 : page) + 1, category == null ? "TODOS" : category);
            case "filter" -> openMainMenu(player, 1, category == null ? "TODOS" : category);
            case "kit" -> {
                if (kit == null) return;
                if (e.isLeftClick()) {
                    claimKit(player, kit, false);
                } else if (e.isRightClick()) {
                    openPreview(player, kit);
                }
            }
            case "back_main" -> openMainMenu(player, 1, "TODOS");
            case "claim_preview" -> {
                if (kit != null) claimKit(player, kit, false);
            }
            case "close" -> player.closeInventory();
            case "edit_icon" -> {
                if (kit == null) return;
                chatEditingIcon.put(player.getUniqueId(), kit);
                player.closeInventory();
                player.sendMessage(color("&eEscribe en el chat el material nuevo para el kit &f" + kit + "&e. Ejemplo: &fDIAMOND"));
            }
            case "save_inventory_items" -> {
                if (kit == null) return;
                YamlConfiguration cfg = getKitConfig(kit);
                List<ItemStack> items = new ArrayList<>();
                PlayerInventory inv = player.getInventory();
                for (ItemStack content : inv.getContents()) {
                    if (content != null && content.getType() != Material.AIR) {
                        items.add(content.clone());
                    }
                }
                cfg.set("items", items);
                saveKitConfig(kit, cfg);
                logAdmin(player.getName(), "SAVE_ITEMS", kit);
                player.sendMessage(color("&aLos items de tu inventario fueron guardados en el kit &f" + kit));
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.2f);
            }
            case "category_normal" -> changeCategory(player, kit, "NORMAL");
            case "category_premium" -> changeCategory(player, kit, "PREMIUM");
            case "category_evento" -> changeCategory(player, kit, "EVENTO");
            case "category_donador" -> changeCategory(player, kit, "DONADOR");
            case "delete_request" -> {
                if (kit == null) return;
                deleteConfirm.put(player.getUniqueId(), kit);
                player.closeInventory();
                player.sendMessage(color("&cEscribe &fconfirmar " + kit + " &cpara borrarlo."));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            }
            case "cooldown_info" -> player.sendMessage(color("&ePor ahora el cooldown se ajusta en el archivo yml del kit."));
        }
    }

    private void changeCategory(Player player, String kit, String category) {
        if (kit == null) return;
        YamlConfiguration cfg = getKitConfig(kit);
        cfg.set("category", category.toUpperCase());
        saveKitConfig(kit, cfg);
        logAdmin(player.getName(), "CATEGORY_" + category.toUpperCase(), kit);
        player.sendMessage(color("&aCategoría del kit &f" + kit + " &acambiada a &f" + category));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        openAdminEditor(player, kit);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();

        if (chatEditingIcon.containsKey(player.getUniqueId())) {
            e.setCancelled(true);
            String kit = chatEditingIcon.remove(player.getUniqueId());
            String materialName = e.getMessage().trim().toUpperCase();

            Bukkit.getScheduler().runTask(this, () -> {
                try {
                    Material mat = Material.valueOf(materialName);
                    YamlConfiguration cfg = getKitConfig(kit);
                    cfg.set("material", mat.name());
                    saveKitConfig(kit, cfg);
                    logAdmin(player.getName(), "EDIT_ICON", kit);
                    player.sendMessage(color("&aIcono del kit &f" + kit + " &acambiado a &f" + mat.name()));
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.3f);
                    openAdminEditor(player, kit);
                } catch (Exception ex) {
                    player.sendMessage(color("&cMaterial inválido. Intenta algo como: &fDIAMOND"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
            });
            return;
        }

        if (deleteConfirm.containsKey(player.getUniqueId())) {
            String kit = deleteConfirm.get(player.getUniqueId());
            if (e.getMessage().equalsIgnoreCase("confirmar " + kit)) {
                e.setCancelled(true);
                deleteConfirm.remove(player.getUniqueId());

                Bukkit.getScheduler().runTask(this, () -> {
                    File file = getKitFile(kit);
                    if (file.exists() && file.delete()) {
                        logAdmin(player.getName(), "DELETE", kit);
                        player.sendMessage(color("&aKit &f" + kit + " &aeliminado correctamente."));
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.8f);
                    } else {
                        player.sendMessage(color("&cNo se pudo eliminar el kit."));
                    }
                });
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores.");
            return true;
        }

        if (args.length == 0) {
            openMainMenu(player, 1, "TODOS");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "panel" -> {
                openMainMenu(player, 1, "TODOS");
                return true;
            }
            case "help" -> {
                player.sendMessage(color("&8&m--------------------------------"));
                player.sendMessage(color("&b&lAdvancedKits &7- Ayuda"));
                player.sendMessage(color("&f/akits &7- abrir panel"));
                player.sendMessage(color("&f/akits panel &7- abrir panel"));
                player.sendMessage(color("&f/akits list &7- listar kits"));
                player.sendMessage(color("&f/akits version &7- ver versión"));
                player.sendMessage(color("&f/akits preview <kit> &7- ver preview"));
                player.sendMessage(color("&f/akits claim <kit> &7- reclamar kit"));
                if (player.hasPermission("akits.admin")) {
                    player.sendMessage(color("&f/akits create <kit>"));
                    player.sendMessage(color("&f/akits delete <kit>"));
                    player.sendMessage(color("&f/akits edit <kit>"));
                    player.sendMessage(color("&f/akits reload"));
                    player.sendMessage(color("&f/akits give <jugador> <kit>"));
                    player.sendMessage(color("&f/akits resetcooldown <jugador> <kit>"));
                }
                player.sendMessage(color("&8&m--------------------------------"));
                return true;
            }
            case "list" -> {
                List<String> kits = getKitNames();
                player.sendMessage(color("&bKits disponibles: &f" + (kits.isEmpty() ? "ninguno" : String.join(", ", kits))));
                return true;
            }
            case "version", "vercion" -> {
                player.sendMessage(color("&bAdvancedKits &fversión &a" + getDescription().getVersion()));
                return true;
            }
            case "reload" -> {
                if (!player.hasPermission("akits.admin")) {
                    player.sendMessage(color("&cNo tienes permiso."));
                    return true;
                }
                reloadConfig();
                messages = YamlConfiguration.loadConfiguration(messagesFile);
                player.sendMessage(color("&aConfiguración recargada."));
                return true;
            }
            case "create" -> {
                if (!player.hasPermission("akits.admin")) {
                    player.sendMessage(color("&cNo tienes permiso."));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(color("&cUsa: /akits create <kit>"));
                    return true;
                }
                String kit = args[1].toLowerCase();
                if (kitExists(kit)) {
                    player.sendMessage(color("&cEse kit ya existe."));
                    return true;
                }
                createDefaultKit(kit, player);
                player.sendMessage(color("&aKit &f" + kit + " &acreado."));
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
                return true;
            }
            case "delete" -> {
                if (!player.hasPermission("akits.admin")) {
                    player.sendMessage(color("&cNo tienes permiso."));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(color("&cUsa: /akits delete <kit>"));
                    return true;
                }
                String kit = args[1].toLowerCase();
                if (!kitExists(kit)) {
                    player.sendMessage(color("&cEse kit no existe."));
                    return true;
                }
                deleteConfirm.put(player.getUniqueId(), kit);
                player.sendMessage(color("&cEscribe &fconfirmar " + kit + " &cen el chat para eliminarlo."));
                return true;
            }
            case "edit" -> {
                if (!player.hasPermission("akits.admin")) {
                    player.sendMessage(color("&cNo tienes permiso."));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(color("&cUsa: /akits edit <kit>"));
                    return true;
                }
                String kit = args[1].toLowerCase();
                if (!kitExists(kit)) {
                    player.sendMessage(color("&cEse kit no existe."));
                    return true;
                }
                openAdminEditor(player, kit);
                return true;
            }
            case "preview" -> {
                if (args.length < 2) {
                    player.sendMessage(color("&cUsa: /akits preview <kit>"));
                    return true;
                }
                String kit = args[1].toLowerCase();
                openPreview(player, kit);
                return true;
            }
            case "claim" -> {
                if (args.length < 2) {
                    player.sendMessage(color("&cUsa: /akits claim <kit>"));
                    return true;
                }
                String kit = args[1].toLowerCase();
                claimKit(player, kit, false);
                return true;
            }
            case "give" -> {
                if (!player.hasPermission("akits.admin")) {
                    player.sendMessage(color("&cNo tienes permiso."));
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(color("&cUsa: /akits give <jugador> <kit>"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(color("&cJugador no encontrado."));
                    return true;
                }
                String kit = args[2].toLowerCase();
                if (!kitExists(kit)) {
                    player.sendMessage(color("&cEse kit no existe."));
                    return true;
                }
                giveKit(target, kit);
                player.sendMessage(color("&aLe diste el kit &f" + kit + " &aa &f" + target.getName()));
                target.sendMessage(color("&aHas recibido el kit &f" + kit + " &apor un administrador."));
                return true;
            }
            case "resetcooldown" -> {
                if (!player.hasPermission("akits.admin")) {
                    player.sendMessage(color("&cNo tienes permiso."));
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(color("&cUsa: /akits resetcooldown <jugador> <kit>"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(color("&cJugador no encontrado."));
                    return true;
                }
                String kit = args[2].toLowerCase();
                resetCooldown(target.getUniqueId(), kit);
                player.sendMessage(color("&aCooldown reiniciado para &f" + target.getName() + " &aen kit &f" + kit));
                return true;
            }
        }

        player.sendMessage(color("&cSubcomando no reconocido. Usa /akits help"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> sub = Arrays.asList("panel", "help", "list", "version", "vercion", "preview", "claim", "reload", "create", "delete", "edit", "give", "resetcooldown");
            return sub.stream().filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (Arrays.asList("preview", "claim", "edit", "delete").contains(args[0].toLowerCase())) {
                return getKitNames().stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("resetcooldown")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("resetcooldown")) {
                return getKitNames().stream().filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}
