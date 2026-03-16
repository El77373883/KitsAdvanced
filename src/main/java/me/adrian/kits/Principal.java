package tu.paquete;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Principal extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private final Map<String, KitData> kits = new LinkedHashMap<>();
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private final Set<UUID> hideLocked = new HashSet<>();

    private final Map<UUID, ChatAction> chatActions = new HashMap<>();
    private final Map<UUID, String> editingKitByPlayer = new HashMap<>();

    private Connection connection;

    private final String PREFIX = color("&8[&6AdvancedKits&8] ");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        createFolders();
        setupSQLite();
        createTables();
        loadKits();

        PluginCommand cmd = getCommand("akits");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }

        Bukkit.getPluginManager().registerEvents(this, this);

        getLogger().info("AdvancedKits enabled.");
    }

    @Override
    public void onDisable() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (Exception ignored) {}
    }

    private void createFolders() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        File kitsFolder = new File(getDataFolder(), "kits");
        if (!kitsFolder.exists()) kitsFolder.mkdirs();
        File logsFolder = new File(getDataFolder(), "logs");
        if (!logsFolder.exists()) logsFolder.mkdirs();
    }

    private void setupSQLite() {
        try {
            File db = new File(getDataFolder(), "data.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void createTables() {
        try (PreparedStatement ps = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS cooldowns (" +
                        "uuid TEXT NOT NULL," +
                        "kit TEXT NOT NULL," +
                        "end_time BIGINT NOT NULL," +
                        "PRIMARY KEY(uuid, kit))"
        )) {
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadKits() {
        kits.clear();
        File folder = new File(getDataFolder(), "kits");
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            String id = file.getName().replace(".yml", "").toLowerCase();

            KitData kit = new KitData(id);
            kit.setDisplayName(cfg.getString("display-name", capitalize(id)));
            kit.setType(cfg.getString("type", "default"));
            kit.setPermission(cfg.getString("permission", "akits.kit." + id));
            kit.setCooldownSeconds(cfg.getLong("settings.cooldown-seconds", 0));
            kit.setAutoEquip(cfg.getBoolean("settings.auto-equip", false));
            kit.setBroadcast(cfg.getBoolean("settings.broadcast", false));
            kit.setEnabled(cfg.getBoolean("settings.enabled", true));
            kit.setGlow(cfg.getBoolean("settings.glow", false));
            kit.setPriority(cfg.getInt("settings.priority", 0));
            kit.setSlot(cfg.getInt("settings.slot", -1));

            Material icon = Material.CHEST;
            try {
                icon = Material.valueOf(cfg.getString("icon", "CHEST").toUpperCase());
            } catch (Exception ignored) {}
            kit.setIcon(icon);

            kit.setLore(cfg.getStringList("lore"));
            kit.setConsoleCommands(cfg.getStringList("rewards.console-commands"));
            kit.setPlayerCommands(cfg.getStringList("rewards.player-commands"));
            kit.setMoney(cfg.getDouble("rewards.money", 0.0));
            kit.setPoints(cfg.getInt("rewards.points", 0));

            List<ItemStack> inventory = castItemList(cfg.getList("inventory.items"));
            List<ItemStack> armor = castItemList(cfg.getList("inventory.armor"));
            ItemStack offhand = cfg.getItemStack("inventory.offhand");

            kit.setInventoryItems(inventory);
            kit.setArmorItems(armor);
            kit.setOffhand(offhand);

            kits.put(id, kit);
        }

        kits.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> e.getValue().getPriority()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    @SuppressWarnings("unchecked")
    private List<ItemStack> castItemList(List<?> raw) {
        List<ItemStack> out = new ArrayList<>();
        if (raw == null) return out;
        for (Object o : raw) {
            if (o instanceof ItemStack item) out.add(item);
            else out.add(null);
        }
        return out;
    }

    private void saveKitFile(KitData kit) {
        try {
            File file = new File(getDataFolder(), "kits/" + kit.getName().toLowerCase() + ".yml");
            YamlConfiguration cfg = new YamlConfiguration();

            cfg.set("display-name", kit.getDisplayName());
            cfg.set("type", kit.getType());
            cfg.set("permission", kit.getPermission());
            cfg.set("icon", kit.getIcon().name());
            cfg.set("lore", kit.getLore());

            cfg.set("settings.cooldown-seconds", kit.getCooldownSeconds());
            cfg.set("settings.auto-equip", kit.isAutoEquip());
            cfg.set("settings.broadcast", kit.isBroadcast());
            cfg.set("settings.enabled", kit.isEnabled());
            cfg.set("settings.glow", kit.isGlow());
            cfg.set("settings.priority", kit.getPriority());
            cfg.set("settings.slot", kit.getSlot());

            cfg.set("inventory.items", kit.getInventoryItems());
            cfg.set("inventory.armor", kit.getArmorItems());
            cfg.set("inventory.offhand", kit.getOffhand());

            cfg.set("rewards.console-commands", kit.getConsoleCommands());
            cfg.set("rewards.player-commands", kit.getPlayerCommands());
            cfg.set("rewards.money", kit.getMoney());
            cfg.set("rewards.points", kit.getPoints());

            cfg.save(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deleteKitFile(String name) {
        File file = new File(getDataFolder(), "kits/" + name.toLowerCase() + ".yml");
        if (file.exists()) file.delete();
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        ChatAction action = chatActions.get(player.getUniqueId());
        if (action == null) return;

        e.setCancelled(true);

        String msg = e.getMessage();
        String kitName = editingKitByPlayer.get(player.getUniqueId());
        KitData kit = kitName == null ? null : kits.get(kitName.toLowerCase());

        Bukkit.getScheduler().runTask(this, () -> {
            if (action == ChatAction.CREATE_KIT) {
                String id = msg.toLowerCase().replace(" ", "_");
                if (kits.containsKey(id)) {
                    player.sendMessage(PREFIX + color("&cEse kit ya existe."));
                    soundError(player);
                    clearChatAction(player);
                    return;
                }

                KitData newKit = new KitData(id);
                newKit.setDisplayName(capitalize(id));
                newKit.setPermission("akits.kit." + id);
                newKit.setType("default");
                newKit.setIcon(Material.CHEST);
                newKit.setLore(Arrays.asList(
                        "&7Kit &f" + capitalize(id),
                        "&7Tipo: &edefault",
                        "",
                        "&aClick izquierdo: reclamar",
                        "&eClick derecho: preview"
                ));
                kits.put(id, newKit);
                saveKitFile(newKit);
                logAdmin(player.getName() + " creó el kit " + id);
                player.sendMessage(PREFIX + color("&aKit creado: &e" + id));
                soundCreate(player);
                clearChatAction(player);
                openAdminPanel(player, 1);
                return;
            }

            if (kit == null) {
                player.sendMessage(PREFIX + color("&cKit no encontrado."));
                clearChatAction(player);
                return;
            }

            switch (action) {
                case EDIT_DISPLAY_NAME -> {
                    kit.setDisplayName(msg);
                    saveKitFile(kit);
                    logAdmin(player.getName() + " cambió display-name del kit " + kit.getName() + " a " + msg);
                    player.sendMessage(PREFIX + color("&aNombre visible actualizado."));
                }
                case EDIT_PERMISSION -> {
                    kit.setPermission(msg);
                    saveKitFile(kit);
                    logAdmin(player.getName() + " cambió permiso del kit " + kit.getName() + " a " + msg);
                    player.sendMessage(PREFIX + color("&aPermiso actualizado."));
                }
                case EDIT_LORE -> {
                    kit.setLore(Arrays.stream(msg.split("\\|")).map(String::trim).collect(Collectors.toList()));
                    saveKitFile(kit);
                    logAdmin(player.getName() + " cambió lore del kit " + kit.getName());
                    player.sendMessage(PREFIX + color("&aLore actualizado. Usa | para separar líneas."));
                }
                case EDIT_COOLDOWN -> {
                    try {
                        long seconds = Long.parseLong(msg);
                        kit.setCooldownSeconds(seconds);
                        saveKitFile(kit);
                        logAdmin(player.getName() + " cambió cooldown del kit " + kit.getName() + " a " + seconds + " segundos");
                        player.sendMessage(PREFIX + color("&aCooldown actualizado a &e" + formatTime(seconds)));
                    } catch (Exception ex) {
                        player.sendMessage(PREFIX + color("&cDebes poner solo segundos. Ejemplo: 3600"));
                        soundError(player);
                    }
                }
                case EDIT_ICON -> {
                    try {
                        Material mat = Material.valueOf(msg.toUpperCase());
                        kit.setIcon(mat);
                        saveKitFile(kit);
                        logAdmin(player.getName() + " cambió icono del kit " + kit.getName() + " a " + mat.name());
                        player.sendMessage(PREFIX + color("&aIcono actualizado."));
                    } catch (Exception ex) {
                        player.sendMessage(PREFIX + color("&cMaterial inválido."));
                        soundError(player);
                    }
                }
                case EDIT_CONSOLE_COMMANDS -> {
                    kit.setConsoleCommands(Arrays.stream(msg.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()));
                    saveKitFile(kit);
                    logAdmin(player.getName() + " editó comandos consola del kit " + kit.getName());
                    player.sendMessage(PREFIX + color("&aComandos de consola actualizados."));
                }
                case EDIT_PLAYER_COMMANDS -> {
                    kit.setPlayerCommands(Arrays.stream(msg.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()));
                    saveKitFile(kit);
                    logAdmin(player.getName() + " editó comandos jugador del kit " + kit.getName());
                    player.sendMessage(PREFIX + color("&aComandos de jugador actualizados."));
                }
            }

            clearChatAction(player);
            openKitEditor(player, kit);
        });
    }

    private void clearChatAction(Player player) {
        chatActions.remove(player.getUniqueId());
        editingKitByPlayer.remove(player.getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores.");
            return true;
        }

        if (args.length == 0) {
            if (!player.hasPermission("akits.use")) {
                player.sendMessage(PREFIX + color("&cNo tienes permiso."));
                return true;
            }
            openMainMenu(player, playerPages.getOrDefault(player.getUniqueId(), 1));
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "help" -> {
                sendHelp(player);
            }
            case "list" -> {
                player.sendMessage(color("&8&m----------------------------"));
                player.sendMessage(color("&6AdvancedKits &7- Lista de kits"));
                for (KitData kit : kits.values()) {
                    player.sendMessage(color("&e- &f" + kit.getName() + " &7(" + kit.getType() + ")"));
                }
                player.sendMessage(color("&8&m----------------------------"));
            }
            case "version" -> {
                player.sendMessage(PREFIX + color("&fVersión: &e" + getDescription().getVersion()));
            }
            case "reload" -> {
                if (!player.hasPermission("akits.admin")) {
                    player.sendMessage(PREFIX + color("&cNo tienes permiso."));
                    return true;
                }
                reloadConfig();
                loadKits();
                player.sendMessage(PREFIX + color("&aPlugin recargado correctamente."));
                soundSave(player);
            }
            case "panel" -> {
                if (!player.hasPermission("akits.admin")) {
                    player.sendMessage(PREFIX + color("&cNo tienes permiso."));
                    return true;
                }
                openAdminPanel(player, 1);
            }
            case "create" -> {
                if (!player.hasPermission("akits.admin")) {
                    player.sendMessage(PREFIX + color("&cNo tienes permiso."));
                    return true;
                }
                if (args.length >= 2) {
                    String id = args[1].toLowerCase();
                    if (kits.containsKey(id)) {
                        player.sendMessage(PREFIX + color("&cEse kit ya existe."));
                        return true;
                    }
                    KitData newKit = new KitData(id);
                    newKit.setDisplayName(capitalize(id));
                    newKit.setPermission("akits.kit." + id);
                    newKit.setType("default");
                    newKit.setIcon(Material.CHEST);
                    kits.put(id, newKit);
                    saveKitFile(newKit);
                    logAdmin(player.getName() + " creó el kit " + id + " con comando");
                    player.sendMessage(PREFIX + color("&aKit creado: &e" + id));
                    soundCreate(player);
                } else {
                    chatActions.put(player.getUniqueId(), ChatAction.CREATE_KIT);
                    player.sendMessage(PREFIX + color("&eEscribe en el chat el nombre del kit."));
                }
            }
            case "edit" -> {
                if (!player.hasPermission("akits.admin")) {
                    player.sendMessage(PREFIX + color("&cNo tienes permiso."));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(PREFIX + color("&cUsa: /akits edit <nombre>"));
                    return true;
                }
                KitData kit = kits.get(args[1].toLowerCase());
                if (kit == null) {
                    player.sendMessage(PREFIX + color("&cKit no encontrado."));
                    return true;
                }
                openKitEditor(player, kit);
            }
            case "delete" -> {
                if (!player.hasPermission("akits.admin")) {
                    player.sendMessage(PREFIX + color("&cNo tienes permiso."));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(PREFIX + color("&cUsa: /akits delete <nombre>"));
                    return true;
                }
                KitData kit = kits.get(args[1].toLowerCase());
                if (kit == null) {
                    player.sendMessage(PREFIX + color("&cKit no encontrado."));
                    return true;
                }
                openDeleteConfirm(player, kit);
            }
            case "preview" -> {
                if (args.length < 2) {
                    player.sendMessage(PREFIX + color("&cUsa: /akits preview <kit>"));
                    return true;
                }
                KitData kit = kits.get(args[1].toLowerCase());
                if (kit == null) {
                    player.sendMessage(PREFIX + color("&cKit no encontrado."));
                    return true;
                }
                openPreviewMenu(player, kit);
            }
            case "claim" -> {
                if (args.length < 2) {
                    player.sendMessage(PREFIX + color("&cUsa: /akits claim <kit>"));
                    return true;
                }
                KitData kit = kits.get(args[1].toLowerCase());
                if (kit == null) {
                    player.sendMessage(PREFIX + color("&cKit no encontrado."));
                    return true;
                }
                claimKit(player, kit);
            }
            case "give" -> {
                if (!player.hasPermission("akits.admin")) {
                    player.sendMessage(PREFIX + color("&cNo tienes permiso."));
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(PREFIX + color("&cUsa: /akits give <jugador> <kit>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(PREFIX + color("&cJugador no encontrado."));
                    return true;
                }
                KitData kit = kits.get(args[2].toLowerCase());
                if (kit == null) {
                    player.sendMessage(PREFIX + color("&cKit no encontrado."));
                    return true;
                }
                giveKit(target, kit, false);
                player.sendMessage(PREFIX + color("&aKit enviado a &e" + target.getName()));
                target.sendMessage(PREFIX + color("&aHas recibido el kit &e" + kit.getDisplayName()));
            }
            case "resetcooldown" -> {
                if (!player.hasPermission("akits.admin")) {
                    player.sendMessage(PREFIX + color("&cNo tienes permiso."));
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(PREFIX + color("&cUsa: /akits resetcooldown <jugador> <kit>"));
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                String kit = args[2].toLowerCase();
                resetCooldown(target.getUniqueId(), kit);
                player.sendMessage(PREFIX + color("&aCooldown reiniciado."));
            }
            default -> player.sendMessage(PREFIX + color("&cSubcomando no válido. Usa /akits help"));
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(color("&8&m--------------------------------"));
        player.sendMessage(color("&6&lAdvancedKits &7Ayuda"));
        player.sendMessage(color("&e/akits"));
        player.sendMessage(color("&e/akits help"));
        player.sendMessage(color("&e/akits list"));
        player.sendMessage(color("&e/akits version"));
        player.sendMessage(color("&e/akits preview <kit>"));
        player.sendMessage(color("&e/akits claim <kit>"));
        if (player.hasPermission("akits.admin")) {
            player.sendMessage(color("&cAdmin:"));
            player.sendMessage(color("&e/akits panel"));
            player.sendMessage(color("&e/akits reload"));
            player.sendMessage(color("&e/akits create <nombre>"));
            player.sendMessage(color("&e/akits edit <nombre>"));
            player.sendMessage(color("&e/akits delete <nombre>"));
            player.sendMessage(color("&e/akits give <jugador> <kit>"));
            player.sendMessage(color("&e/akits resetcooldown <jugador> <kit>"));
        }
        player.sendMessage(color("&8&m--------------------------------"));
    }

    private void openMainMenu(Player player, int page) {
        soundOpen(player);
        List<KitData> visible = new ArrayList<>(kits.values());

        if (hideLocked.contains(player.getUniqueId())) {
            visible = visible.stream().filter(k -> canAccess(player, k)).collect(Collectors.toList());
        }

        visible.sort(Comparator
                .comparingInt((KitData k) -> k.getSlot() == -1 ? Integer.MAX_VALUE : k.getSlot())
                .thenComparingInt(KitData::getPriority)
                .thenComparing(KitData::getName));

        int pageSize = 21;
        int maxPage = Math.max(1, (int) Math.ceil(visible.size() / (double) pageSize));
        if (page < 1) page = 1;
        if (page > maxPage) page = maxPage;
        playerPages.put(player.getUniqueId(), page);

        Inventory inv = Bukkit.createInventory(null, 54, color("&8✦ &6AdvancedKits &8| Página " + page));

        decorateMain(inv);

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, visible.size());
        List<Integer> slots = Arrays.asList(10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34);

        int index = 0;
        for (int i = start; i < end && index < slots.size(); i++) {
            inv.setItem(slots.get(index++), buildKitIcon(player, visible.get(i)));
        }

        inv.setItem(45, createSimpleItem(Material.ARROW, "&ePágina anterior", "&7Ve a la página anterior"));
        inv.setItem(49, pluginLogo());
        inv.setItem(53, createSimpleItem(Material.ARROW, "&ePágina siguiente", "&7Ve a la página siguiente"));
        inv.setItem(46, createSimpleItem(Material.BOOK, "&bAyuda", "&7Ver comandos y ayuda"));
        inv.setItem(47, createSimpleItem(Material.DIAMOND, "&dPremium Kits", "&7Kits premium del servidor"));
        inv.setItem(51, createSimpleItem(hideLocked.contains(player.getUniqueId()) ? Material.LIME_DYE : Material.GRAY_DYE,
                hideLocked.contains(player.getUniqueId()) ? "&aOcultar bloqueados: ON" : "&cOcultar bloqueados: OFF",
                "&7Click para cambiar"));
        inv.setItem(52, createSimpleItem(Material.HOPPER, "&aReclamar todo disponible", "&7Reclama todos los kits que puedas usar"));

        if (player.hasPermission("akits.admin")) {
            inv.setItem(50, createSimpleItem(Material.REDSTONE, "&cPanel admin", "&7Abrir panel de administración"));
            inv.setItem(48, createSimpleItem(Material.REPEATER, "&6Recargar plugin", "&7Recarga config y kits"));
        }

        player.openInventory(inv);

        new BukkitRunnable() {
            int step = 0;
            final int[] border = {0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,37,38,39,40,41,42,43,44};

            @Override
            public void run() {
                if (!player.getOpenInventory().getTitle().contains("AdvancedKits")) {
                    cancel();
                    return;
                }
                if (step >= border.length) {
                    cancel();
                    return;
                }
                ItemStack pane = createGlass(step % 2 == 0 ? Material.BLACK_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE, " ");
                inv.setItem(border[step], pane);
                player.updateInventory();
                step++;
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void decorateMain(Inventory inv) {
        for (int i = 0; i < 54; i++) {
            if (inv.getItem(i) == null && (i < 9 || i > 44 || i % 9 == 0 || i % 9 == 8)) {
                inv.setItem(i, createGlass(Material.GRAY_STAINED_GLASS_PANE, " "));
            }
        }
        inv.setItem(4, pluginLogo());
    }

    private ItemStack pluginLogo() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color("&6&lAdvancedKits"));
        meta.setLore(Arrays.asList(
                color("&7Sistema profesional de kits"),
                color("&7Default y Premium"),
                color(""),
                color("&eClick para refrescar vista")
        ));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildKitIcon(Player player, KitData kit) {
        Material mat = kit.getIcon();
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        long remaining = getRemainingCooldown(player.getUniqueId(), kit.getName());
        boolean canAccess = canAccess(player, kit);
        boolean canClaim = canClaim(player, kit);

        String state;
        if (!canAccess) state = "&cBloqueado";
        else if (remaining > 0) state = "&eEn cooldown";
        else state = "&aDisponible";

        String typeColor = kit.getType().equalsIgnoreCase("premium") ? "&d" : "&f";

        meta.setDisplayName(color(typeColor + kit.getDisplayName()));

        List<String> lore = new ArrayList<>();
        if (kit.getLore() != null && !kit.getLore().isEmpty()) {
            for (String line : kit.getLore()) lore.add(color(line));
        } else {
            lore.add(color("&7Kit profesional"));
        }

        lore.add("");
        lore.add(color("&7Estado: " + state));
        lore.add(color("&7Tipo: " + typeColor + kit.getType()));
        lore.add(color("&7Permiso: &f" + kit.getPermission()));
        lore.add(color("&7Cooldown: &f" + (remaining > 0 ? formatTime(remaining) : "Disponible")));
        lore.add(color("&7Acceso: " + (canAccess ? "&aSí" : "&cNo")));
        lore.add("");
        lore.add(color("&aClick izquierdo: reclamar"));
        lore.add(color("&eClick derecho: preview"));

        meta.setLore(lore);

        if (kit.isGlow() || kit.getType().equalsIgnoreCase("premium")) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.setEnchantmentGlintOverride(true);
        }

        item.setItemMeta(meta);
        return item;
    }

    private void openPreviewMenu(Player player, KitData kit) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&8Preview: &6" + kit.getDisplayName()));

        for (int i = 0; i < 54; i++) {
            inv.setItem(i, createGlass(Material.BLACK_STAINED_GLASS_PANE, " "));
        }

        List<ItemStack> items = kit.getInventoryItems();
        List<ItemStack> armor = kit.getArmorItems();

        for (int i = 0; i < Math.min(27, items.size()); i++) {
            ItemStack it = items.get(i);
            if (it != null) inv.setItem(10 + (i % 7) + ((i / 7) * 9), it.clone());
        }

        if (armor.size() > 0 && armor.get(0) != null) inv.setItem(37, armor.get(0).clone());
        if (armor.size() > 1 && armor.get(1) != null) inv.setItem(38, armor.get(1).clone());
        if (armor.size() > 2 && armor.get(2) != null) inv.setItem(39, armor.get(2).clone());
        if (armor.size() > 3 && armor.get(3) != null) inv.setItem(40, armor.get(3).clone());
        if (kit.getOffhand() != null) inv.setItem(41, kit.getOffhand().clone());

        long remaining = getRemainingCooldown(player.getUniqueId(), kit.getName());

        ItemStack info = new ItemStack(kit.getIcon());
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName(color("&6" + kit.getDisplayName()));
        meta.setLore(Arrays.asList(
                color("&7Tipo: &f" + kit.getType()),
                color("&7Permiso: &f" + kit.getPermission()),
                color("&7Bloqueado: " + (canAccess(player, kit) ? "&aNo" : "&cSí")),
                color("&7Cooldown: &f" + (remaining > 0 ? formatTime(remaining) : "Disponible")),
                color(""),
                color("&aClick izquierdo en el botón verde para reclamar"),
                color("&cRegresa con la flecha")
        ));
        info.setItemMeta(meta);

        inv.setItem(49, info);
        inv.setItem(45, createSimpleItem(Material.LIME_CONCRETE, "&aReclamar kit", "&7Reclama este kit"));
        inv.setItem(53, createSimpleItem(Material.ARROW, "&cRegresar", "&7Volver al menú"));

        player.openInventory(inv);
    }

    private void openAdminPanel(Player player, int page) {
        List<KitData> all = new ArrayList<>(kits.values());
        int pageSize = 21;
        int maxPage = Math.max(1, (int) Math.ceil(all.size() / (double) pageSize));
        if (page < 1) page = 1;
        if (page > maxPage) page = maxPage;

        Inventory inv = Bukkit.createInventory(null, 54, color("&8Panel Admin &7| Página " + page));

        for (int i = 0; i < 54; i++) {
            inv.setItem(i, createGlass(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        inv.setItem(49, createSimpleItem(Material.CHEST, "&6Crear kit", "&7Click para crear un kit nuevo"));
        inv.setItem(45, createSimpleItem(Material.ARROW, "&ePágina anterior", "&7Ir atrás"));
        inv.setItem(53, createSimpleItem(Material.ARROW, "&ePágina siguiente", "&7Ir adelante"));
        inv.setItem(47, createSimpleItem(Material.REPEATER, "&6Recargar", "&7Recargar plugin"));
        inv.setItem(51, createSimpleItem(Material.BARRIER, "&cCerrar", "&7Cerrar menú"));

        List<Integer> slots = Arrays.asList(10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34);
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, all.size());
        int index = 0;
        for (int i = start; i < end && index < slots.size(); i++) {
            KitData kit = all.get(i);
            ItemStack icon = new ItemStack(kit.getIcon());
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(color("&6" + kit.getDisplayName()));
            meta.setLore(Arrays.asList(
                    color("&7ID: &f" + kit.getName()),
                    color("&7Tipo: &f" + kit.getType()),
                    color("&7Cooldown: &f" + formatTime(kit.getCooldownSeconds())),
                    color("&7Prioridad: &f" + kit.getPriority()),
                    color(""),
                    color("&aClick izquierdo: editar"),
                    color("&cClick derecho: borrar")
            ));
            if (kit.isGlow()) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.setEnchantmentGlintOverride(true);
            }
            icon.setItemMeta(meta);
            inv.setItem(slots.get(index++), icon);
        }

        player.openInventory(inv);
    }

    private void openKitEditor(Player player, KitData kit) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&8Editar Kit: &6" + kit.getName()));

        for (int i = 0; i < 54; i++) inv.setItem(i, createGlass(Material.BLACK_STAINED_GLASS_PANE, " "));

        inv.setItem(10, createSimpleItem(Material.NAME_TAG, "&eNombre visible", "&7Actual: &f" + kit.getDisplayName(), "&7Click para editar"));
        inv.setItem(11, createSimpleItem(kit.getIcon(), "&eIcono", "&7Actual: &f" + kit.getIcon().name(), "&7Click para editar material"));
        inv.setItem(12, createSimpleItem(Material.BOOK, "&eLore", "&7Editar lore con chat", "&7Usa | para separar líneas"));
        inv.setItem(13, createSimpleItem(Material.CLOCK, "&eCooldown", "&7Actual: &f" + formatTime(kit.getCooldownSeconds()), "&7Click para editar en segundos"));
        inv.setItem(14, createSimpleItem(kit.getType().equalsIgnoreCase("premium") ? Material.DIAMOND : Material.IRON_INGOT,
                "&eTipo", "&7Actual: &f" + kit.getType(), "&7Click para alternar"));
        inv.setItem(15, createSimpleItem(kit.isAutoEquip() ? Material.LIME_DYE : Material.GRAY_DYE,
                "&eAuto-Equip", "&7Estado: " + (kit.isAutoEquip() ? "&aON" : "&cOFF"), "&7Click para alternar"));
        inv.setItem(16, createSimpleItem(kit.isBroadcast() ? Material.LIME_DYE : Material.GRAY_DYE,
                "&eBroadcast", "&7Estado: " + (kit.isBroadcast() ? "&aON" : "&cOFF"), "&7Click para alternar"));

        inv.setItem(19, createSimpleItem(Material.CHEST, "&eGuardar inventario", "&7Sube tu inventario al kit"));
        inv.setItem(20, createSimpleItem(Material.COMMAND_BLOCK, "&eComandos consola", "&7Editar rewards de consola"));
        inv.setItem(21, createSimpleItem(Material.REPEATING_COMMAND_BLOCK, "&eComandos jugador", "&7Editar rewards del jugador"));
        inv.setItem(22, createSimpleItem(Material.TRIPWIRE_HOOK, "&ePermiso", "&7Actual: &f" + kit.getPermission(), "&7Click para editar"));
        inv.setItem(23, createSimpleItem(kit.isGlow() ? Material.ENCHANTED_BOOK : Material.BOOK,
                "&eGlow", "&7Estado: " + (kit.isGlow() ? "&aON" : "&cOFF"), "&7Click para alternar"));
        inv.setItem(24, createSimpleItem(Material.HOPPER, "&ePrioridad", "&7Actual: &f" + kit.getPriority(), "&7Izq +1 | Der -1"));
        inv.setItem(25, createSimpleItem(Material.ITEM_FRAME, "&eSlot manual", "&7Actual: &f" + kit.getSlot(), "&7Izq +1 | Der -1 | Shift reset"));

        inv.setItem(31, createSimpleItem(Material.ENDER_CHEST, "&bPreview", "&7Ver preview del kit"));
        inv.setItem(32, createSimpleItem(Material.BARRIER, "&cEliminar kit", "&7Abrir confirmación"));
        inv.setItem(49, createSimpleItem(Material.ARROW, "&cRegresar", "&7Volver al panel admin"));

        player.openInventory(inv);
    }

    private void openDeleteConfirm(Player player, KitData kit) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&8Eliminar: &c" + kit.getName()));
        for (int i = 0; i < 27; i++) inv.setItem(i, createGlass(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(11, createSimpleItem(Material.LIME_CONCRETE, "&aConfirmar", "&7Eliminar kit definitivamente"));
        inv.setItem(15, createSimpleItem(Material.RED_CONCRETE, "&cCancelar", "&7No eliminar"));
        player.openInventory(inv);
    }

    private void openInventoryUploader(Player player, KitData kit) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&8Inventario Kit: &6" + kit.getName()));

        for (int i = 0; i < 45; i++) inv.setItem(i, null);
        for (int i = 45; i < 54; i++) inv.setItem(i, createGlass(Material.GRAY_STAINED_GLASS_PANE, " "));

        List<ItemStack> items = kit.getInventoryItems();
        for (int i = 0; i < Math.min(36, items.size()); i++) {
            ItemStack item = items.get(i);
            if (item != null) inv.setItem(i, item.clone());
        }

        List<ItemStack> armor = kit.getArmorItems();
        if (armor.size() > 0 && armor.get(0) != null) inv.setItem(36, armor.get(0).clone());
        if (armor.size() > 1 && armor.get(1) != null) inv.setItem(37, armor.get(1).clone());
        if (armor.size() > 2 && armor.get(2) != null) inv.setItem(38, armor.get(2).clone());
        if (armor.size() > 3 && armor.get(3) != null) inv.setItem(39, armor.get(3).clone());
        if (kit.getOffhand() != null) inv.setItem(40, kit.getOffhand().clone());

        inv.setItem(45, createSimpleItem(Material.HOPPER, "&aSubir mi inventario", "&7Copia tu inventario actual"));
        inv.setItem(49, createSimpleItem(Material.LIME_CONCRETE, "&aGuardar", "&7Guardar inventario del kit"));
        inv.setItem(53, createSimpleItem(Material.ARROW, "&cRegresar", "&7Volver al editor"));

        player.openInventory(inv);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {}

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        String title = e.getView().getTitle();
        int slot = e.getRawSlot();

        if (title.equals(color("&8✦ &6AdvancedKits &8| Página " + playerPages.getOrDefault(player.getUniqueId(), 1))) || title.contains("AdvancedKits")) {
            e.setCancelled(true);

            if (slot == 45) {
                openMainMenu(player, playerPages.getOrDefault(player.getUniqueId(), 1) - 1);
                soundBack(player);
                return;
            }
            if (slot == 53) {
                openMainMenu(player, playerPages.getOrDefault(player.getUniqueId(), 1) + 1);
                soundBack(player);
                return;
            }
            if (slot == 46) {
                sendHelp(player);
                return;
            }
            if (slot == 47) {
                player.sendMessage(PREFIX + color("&dAquí irían tus kits premium."));
                return;
            }
            if (slot == 48 && player.hasPermission("akits.admin")) {
                reloadConfig();
                loadKits();
                player.sendMessage(PREFIX + color("&aPlugin recargado."));
                soundSave(player);
                return;
            }
            if (slot == 50 && player.hasPermission("akits.admin")) {
                openAdminPanel(player, 1);
                return;
            }
            if (slot == 51) {
                if (hideLocked.contains(player.getUniqueId())) hideLocked.remove(player.getUniqueId());
                else hideLocked.add(player.getUniqueId());
                openMainMenu(player, playerPages.getOrDefault(player.getUniqueId(), 1));
                return;
            }
            if (slot == 52) {
                int claimed = 0;
                for (KitData kit : kits.values()) {
                    if (canClaim(player, kit)) {
                        claimKit(player, kit);
                        claimed++;
                    }
                }
                player.sendMessage(PREFIX + color("&aKits reclamados: &e" + claimed));
                return;
            }

            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            KitData kit = findKitByDisplay(clicked);
            if (kit == null) return;

            if (e.getClick().isRightClick()) openPreviewMenu(player, kit);
            else claimKit(player, kit);
            return;
        }

        if (title.startsWith(color("&8Preview: &6"))) {
            e.setCancelled(true);
            String stripped = ChatColor.stripColor(title.replace(color("&8Preview: &6"), ""));
            KitData kit = findKit(stripped);
            if (kit == null) return;

            if (slot == 45) {
                claimKit(player, kit);
                return;
            }
            if (slot == 53) {
                openMainMenu(player, playerPages.getOrDefault(player.getUniqueId(), 1));
                soundBack(player);
            }
            return;
        }

        if (title.startsWith(color("&8Panel Admin"))) {
            e.setCancelled(true);

            if (slot == 49) {
                chatActions.put(player.getUniqueId(), ChatAction.CREATE_KIT);
                player.sendMessage(PREFIX + color("&eEscribe en el chat el nombre del kit."));
                player.closeInventory();
                return;
            }
            if (slot == 45) {
                openAdminPanel(player, 1);
                return;
            }
            if (slot == 53) {
                openAdminPanel(player, 2);
                return;
            }
            if (slot == 47) {
                reloadConfig();
                loadKits();
                player.sendMessage(PREFIX + color("&aPlugin recargado."));
                return;
            }
            if (slot == 51) {
                player.closeInventory();
                return;
            }

            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            KitData kit = findKitByDisplay(clicked);
            if (kit == null) return;

            if (e.getClick().isRightClick()) openDeleteConfirm(player, kit);
            else openKitEditor(player, kit);
            return;
        }

        if (title.startsWith(color("&8Editar Kit: &6"))) {
            e.setCancelled(true);
            String stripped = ChatColor.stripColor(title.replace(color("&8Editar Kit: &6"), ""));
            KitData kit = findKit(stripped);
            if (kit == null) return;

            switch (slot) {
                case 10 -> startChatEdit(player, kit, ChatAction.EDIT_DISPLAY_NAME, "&eEscribe el nuevo nombre visible.");
                case 11 -> startChatEdit(player, kit, ChatAction.EDIT_ICON, "&eEscribe el material. Ej: DIAMOND_SWORD");
                case 12 -> startChatEdit(player, kit, ChatAction.EDIT_LORE, "&eEscribe el lore usando | para separar líneas.");
                case 13 -> startChatEdit(player, kit, ChatAction.EDIT_COOLDOWN, "&eEscribe el cooldown en segundos. Ej: 3600");
                case 14 -> {
                    kit.setType(kit.getType().equalsIgnoreCase("default") ? "premium" : "default");
                    saveKitFile(kit);
                    openKitEditor(player, kit);
                }
                case 15 -> {
                    kit.setAutoEquip(!kit.isAutoEquip());
                    saveKitFile(kit);
                    openKitEditor(player, kit);
                }
                case 16 -> {
                    kit.setBroadcast(!kit.isBroadcast());
                    saveKitFile(kit);
                    openKitEditor(player, kit);
                }
                case 19 -> openInventoryUploader(player, kit);
                case 20 -> startChatEdit(player, kit, ChatAction.EDIT_CONSOLE_COMMANDS, "&eEscribe comandos consola separados por |");
                case 21 -> startChatEdit(player, kit, ChatAction.EDIT_PLAYER_COMMANDS, "&eEscribe comandos jugador separados por |");
                case 22 -> startChatEdit(player, kit, ChatAction.EDIT_PERMISSION, "&eEscribe el nuevo permiso.");
                case 23 -> {
                    kit.setGlow(!kit.isGlow());
                    saveKitFile(kit);
                    openKitEditor(player, kit);
                }
                case 24 -> {
                    if (e.getClick().isRightClick()) kit.setPriority(Math.max(0, kit.getPriority() - 1));
                    else kit.setPriority(kit.getPriority() + 1);
                    saveKitFile(kit);
                    openKitEditor(player, kit);
                }
                case 25 -> {
                    if (e.isShiftClick()) kit.setSlot(-1);
                    else if (e.getClick().isRightClick()) kit.setSlot(Math.max(-1, kit.getSlot() - 1));
                    else kit.setSlot(kit.getSlot() + 1);
                    saveKitFile(kit);
                    openKitEditor(player, kit);
                }
                case 31 -> openPreviewMenu(player, kit);
                case 32 -> openDeleteConfirm(player, kit);
                case 49 -> openAdminPanel(player, 1);
            }
            return;
        }

        if (title.startsWith(color("&8Eliminar: &c"))) {
            e.setCancelled(true);
            String stripped = ChatColor.stripColor(title.replace(color("&8Eliminar: &c"), ""));
            KitData kit = findKit(stripped);
            if (kit == null) return;

            if (slot == 11) {
                kits.remove(kit.getName().toLowerCase());
                deleteKitFile(kit.getName());
                logAdmin(player.getName() + " eliminó el kit " + kit.getName());
                player.sendMessage(PREFIX + color("&cKit eliminado."));
                soundDelete(player);
                openAdminPanel(player, 1);
            } else if (slot == 15) {
                openKitEditor(player, kit);
            }
            return;
        }

        if (title.startsWith(color("&8Inventario Kit: &6"))) {
            String stripped = ChatColor.stripColor(title.replace(color("&8Inventario Kit: &6"), ""));
            KitData kit = findKit(stripped);
            if (kit == null) return;

            if (slot >= 45) {
                e.setCancelled(true);

                if (slot == 45) {
                    PlayerInventory pInv = player.getInventory();

                    List<ItemStack> items = new ArrayList<>();
                    for (int i = 0; i < 36; i++) {
                        ItemStack item = pInv.getItem(i);
                        e.getInventory().setItem(i, item == null ? null : item.clone());
                        items.add(item == null ? null : item.clone());
                    }

                    e.getInventory().setItem(36, pInv.getHelmet() == null ? null : pInv.getHelmet().clone());
                    e.getInventory().setItem(37, pInv.getChestplate() == null ? null : pInv.getChestplate().clone());
                    e.getInventory().setItem(38, pInv.getLeggings() == null ? null : pInv.getLeggings().clone());
                    e.getInventory().setItem(39, pInv.getBoots() == null ? null : pInv.getBoots().clone());
                    e.getInventory().setItem(40, pInv.getItemInOffHand() == null ? null : pInv.getItemInOffHand().clone());

                    player.sendMessage(PREFIX + color("&aInventario cargado al editor."));
                    soundSave(player);
                    return;
                }

                if (slot == 49) {
                    List<ItemStack> items = new ArrayList<>();
                    for (int i = 0; i < 36; i++) {
                        ItemStack item = e.getInventory().getItem(i);
                        items.add(item == null ? null : item.clone());
                    }

                    List<ItemStack> armor = new ArrayList<>();
                    armor.add(cloneItem(e.getInventory().getItem(36))); // helmet
                    armor.add(cloneItem(e.getInventory().getItem(37))); // chest
                    armor.add(cloneItem(e.getInventory().getItem(38))); // legs
                    armor.add(cloneItem(e.getInventory().getItem(39))); // boots

                    ItemStack offhand = cloneItem(e.getInventory().getItem(40));

                    kit.setInventoryItems(items);
                    kit.setArmorItems(armor);
                    kit.setOffhand(offhand);
                    saveKitFile(kit);
                    logAdmin(player.getName() + " guardó inventario del kit " + kit.getName());
                    player.sendMessage(PREFIX + color("&aInventario del kit guardado correctamente."));
                    soundSave(player);
                    openKitEditor(player, kit);
                    return;
                }

                if (slot == 53) {
                    openKitEditor(player, kit);
                    return;
                }
            }
            return;
        }
    }

    private void startChatEdit(Player player, KitData kit, ChatAction action, String message) {
        chatActions.put(player.getUniqueId(), action);
        editingKitByPlayer.put(player.getUniqueId(), kit.getName());
        player.closeInventory();
        player.sendMessage(PREFIX + color(message));
    }

    private void claimKit(Player player, KitData kit) {
        if (!player.hasPermission("akits.use")) {
            player.sendMessage(PREFIX + color("&cNo tienes permiso para usar kits."));
            soundError(player);
            return;
        }

        if (!canAccess(player, kit)) {
            player.sendMessage(PREFIX + color("&cNo tienes permiso para este kit."));
            soundError(player);
            return;
        }

        long remaining = getRemainingCooldown(player.getUniqueId(), kit.getName());
        if (remaining > 0) {
            player.sendMessage(PREFIX + color("&eDebes esperar &f" + formatTime(remaining) + " &epara volver a reclamarlo."));
            soundError(player);
            return;
        }

        giveKit(player, kit, true);
        setCooldown(player.getUniqueId(), kit.getName(), System.currentTimeMillis() + (kit.getCooldownSeconds() * 1000));

        player.sendMessage(PREFIX + color("&aHas reclamado el kit &e" + kit.getDisplayName() + "&a."));
        soundClaim(player);

        if (kit.isBroadcast() && kit.getType().equalsIgnoreCase("premium")) {
            Bukkit.broadcastMessage(color("&6" + player.getName() + " ha reclamado el kit premium &e" + kit.getDisplayName() + "&6!"));
        }
    }

    private void giveKit(Player player, KitData kit, boolean executeRewards) {
        List<ItemStack> items = kit.getInventoryItems() == null ? new ArrayList<>() : kit.getInventoryItems();
        List<ItemStack> armor = kit.getArmorItems() == null ? new ArrayList<>() : kit.getArmorItems();

        if (kit.isAutoEquip()) {
            if (armor.size() > 0 && armor.get(0) != null && isEmpty(player.getInventory().getHelmet()))
                player.getInventory().setHelmet(armor.get(0).clone());
            else if (armor.size() > 0 && armor.get(0) != null) giveOrDrop(player, armor.get(0).clone());

            if (armor.size() > 1 && armor.get(1) != null && isEmpty(player.getInventory().getChestplate()))
                player.getInventory().setChestplate(armor.get(1).clone());
            else if (armor.size() > 1 && armor.get(1) != null) giveOrDrop(player, armor.get(1).clone());

            if (armor.size() > 2 && armor.get(2) != null && isEmpty(player.getInventory().getLeggings()))
                player.getInventory().setLeggings(armor.get(2).clone());
            else if (armor.size() > 2 && armor.get(2) != null) giveOrDrop(player, armor.get(2).clone());

            if (armor.size() > 3 && armor.get(3) != null && isEmpty(player.getInventory().getBoots()))
                player.getInventory().setBoots(armor.get(3).clone());
            else if (armor.size() > 3 && armor.get(3) != null) giveOrDrop(player, armor.get(3).clone());

            if (kit.getOffhand() != null) {
                if (isEmpty(player.getInventory().getItemInOffHand()))
                    player.getInventory().setItemInOffHand(kit.getOffhand().clone());
                else giveOrDrop(player, kit.getOffhand().clone());
            }
        } else {
            for (ItemStack a : armor) {
                if (a != null) giveOrDrop(player, a.clone());
            }
            if (kit.getOffhand() != null) giveOrDrop(player, kit.getOffhand().clone());
        }

        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;

            if (kit.isAutoEquip()) {
                Material type = item.getType();
                if (isWeapon(type) && isEmpty(player.getInventory().getItemInMainHand())) {
                    player.getInventory().setItemInMainHand(item.clone());
                    continue;
                }
            }

            giveOrDrop(player, item.clone());
        }

        if (executeRewards) {
            for (String cmd : kit.getConsoleCommands()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        cmd.replace("%player%", player.getName()));
            }

            for (String cmd : kit.getPlayerCommands()) {
                player.performCommand(cmd.replace("%player%", player.getName()));
            }
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> left = player.getInventory().addItem(item);
        for (ItemStack remain : left.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), remain);
        }
    }

    private boolean isWeapon(Material type) {
        String n = type.name();
        return n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_PICKAXE");
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    private boolean canAccess(Player player, KitData kit) {
        if (!kit.isEnabled()) return false;
        if (kit.getType().equalsIgnoreCase("premium") && !player.hasPermission("akits.premium") && !player.hasPermission(kit.getPermission()))
            return false;
        return player.hasPermission(kit.getPermission()) || player.hasPermission("akits.admin") || player.hasPermission("akits.premium") && kit.getType().equalsIgnoreCase("premium");
    }

    private boolean canClaim(Player player, KitData kit) {
        return canAccess(player, kit) && getRemainingCooldown(player.getUniqueId(), kit.getName()) <= 0;
    }

    private long getRemainingCooldown(UUID uuid, String kit) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT end_time FROM cooldowns WHERE uuid=? AND kit=?"
        )) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit.toLowerCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                long end = rs.getLong("end_time");
                long remaining = (end - System.currentTimeMillis()) / 1000;
                return Math.max(0, remaining);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private void setCooldown(UUID uuid, String kit, long endTime) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO cooldowns(uuid, kit, end_time) VALUES(?,?,?)"
        )) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit.toLowerCase());
            ps.setLong(3, endTime);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void resetCooldown(UUID uuid, String kit) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM cooldowns WHERE uuid=? AND kit=?"
        )) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit.toLowerCase());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void logAdmin(String text) {
        try {
            File file = new File(getDataFolder(), "logs/admin.log");
            if (!file.exists()) file.createNewFile();

            java.nio.file.Files.writeString(
                    file.toPath(),
                    "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "] " + text + System.lineSeparator(),
                    java.nio.file.StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ItemStack createSimpleItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        if (loreLines != null && loreLines.length > 0) {
            meta.setLore(Arrays.stream(loreLines).map(this::color).collect(Collectors.toList()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGlass(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private String formatTime(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (secs > 0 || sb.length() == 0) sb.append(secs).append("s");
        return sb.toString().trim();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private KitData findKit(String text) {
        for (KitData kit : kits.values()) {
            if (kit.getName().equalsIgnoreCase(text)) return kit;
            if (ChatColor.stripColor(color(kit.getDisplayName())).equalsIgnoreCase(ChatColor.stripColor(text))) return kit;
        }
        return null;
    }

    private KitData findKitByDisplay(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return null;
        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        for (KitData kit : kits.values()) {
            if (ChatColor.stripColor(color(kit.getDisplayName())).equalsIgnoreCase(name)
                    || kit.getName().equalsIgnoreCase(name)) {
                return kit;
            }
        }
        return null;
    }

    private void soundClaim(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    private void soundError(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }

    private void soundCreate(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1.1f);
    }

    private void soundDelete(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.8f);
    }

    private void soundSave(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.4f);
    }

    private void soundOpen(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1f);
    }

    private void soundBack(Player p) {
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.1f);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("help","list","version","reload","panel","create","edit","delete","preview","claim","give","resetcooldown")
                    .stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && Arrays.asList("edit","delete","preview","claim").contains(args[0].toLowerCase())) {
            return kits.keySet().stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return kits.keySet().stream().filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("resetcooldown")) {
            return kits.keySet().stream().filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    enum ChatAction {
        CREATE_KIT,
        EDIT_DISPLAY_NAME,
        EDIT_ICON,
        EDIT_LORE,
        EDIT_COOLDOWN,
        EDIT_PERMISSION,
        EDIT_CONSOLE_COMMANDS,
        EDIT_PLAYER_COMMANDS
    }

    static class KitData {
        private final String name;
        private String displayName = "Kit";
        private String type = "default";
        private String permission = "akits.kit.default";
        private Material icon = Material.CHEST;
        private List<String> lore = new ArrayList<>();
        private long cooldownSeconds = 0;
        private boolean autoEquip = false;
        private boolean broadcast = false;
        private boolean enabled = true;
        private boolean glow = false;
        private int priority = 0;
        private int slot = -1;

        private List<ItemStack> inventoryItems = new ArrayList<>();
        private List<ItemStack> armorItems = new ArrayList<>();
        private ItemStack offhand;

        private List<String> consoleCommands = new ArrayList<>();
        private List<String> playerCommands = new ArrayList<>();
        private double money = 0.0;
        private int points = 0;

        public KitData(String name) {
            this.name = name;
        }

        public String getName() { return name; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getPermission() { return permission; }
        public void setPermission(String permission) { this.permission = permission; }
        public Material getIcon() { return icon; }
        public void setIcon(Material icon) { this.icon = icon; }
        public List<String> getLore() { return lore; }
        public void setLore(List<String> lore) { this.lore = lore; }
        public long getCooldownSeconds() { return cooldownSeconds; }
        public void setCooldownSeconds(long cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; }
        public boolean isAutoEquip() { return autoEquip; }
        public void setAutoEquip(boolean autoEquip) { this.autoEquip = autoEquip; }
        public boolean isBroadcast() { return broadcast; }
        public void setBroadcast(boolean broadcast) { this.broadcast = broadcast; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isGlow() { return glow; }
        public void setGlow(boolean glow) { this.glow = glow; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        public int getSlot() { return slot; }
        public void setSlot(int slot) { this.slot = slot; }
        public List<ItemStack> getInventoryItems() { return inventoryItems; }
        public void setInventoryItems(List<ItemStack> inventoryItems) { this.inventoryItems = inventoryItems; }
        public List<ItemStack> getArmorItems() { return armorItems; }
        public void setArmorItems(List<ItemStack> armorItems) { this.armorItems = armorItems; }
        public ItemStack getOffhand() { return offhand; }
        public void setOffhand(ItemStack offhand) { this.offhand = offhand; }
        public List<String> getConsoleCommands() { return consoleCommands; }
        public void setConsoleCommands(List<String> consoleCommands) { this.consoleCommands = consoleCommands; }
        public List<String> getPlayerCommands() { return playerCommands; }
        public void setPlayerCommands(List<String> playerCommands) { this.playerCommands = playerCommands; }
        public double getMoney() { return money; }
        public void setMoney(double money) { this.money = money; }
        public int getPoints() { return points; }
        public void setPoints(int points) { this.points = points; }
    }
}
