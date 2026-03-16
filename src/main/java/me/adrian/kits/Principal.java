package me.adrian.kits;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Principal extends JavaPlugin implements Listener {

    private static Economy econ = null;

    private final Map<UUID, String> categoriaActual = new HashMap<>();
    private final Map<UUID, String> editandoKit = new HashMap<>();
    private final Map<UUID, String> pendienteConfirmar = new HashMap<>();
    private final Map<UUID, String> pendienteEliminar = new HashMap<>();

    private final Map<UUID, Boolean> esperandoNombre = new HashMap<>();
    private final Map<UUID, String> esperandoPrecio = new HashMap<>();
    private final Map<UUID, String> esperandoCooldown = new HashMap<>();
    private final Map<UUID, String> esperandoPermiso = new HashMap<>();
    private final Map<UUID, String> esperandoIcono = new HashMap<>();
    private final Map<UUID, String> esperandoComando = new HashMap<>();
    private final Map<UUID, String> esperandoRenombre = new HashMap<>();

    private FileConfiguration lang;
    private FileConfiguration mensajes;

    @Override
    public void onEnable() {
        setupEconomy();
        saveDefaultConfig();

        if (!new File(getDataFolder(), "kits").exists()) {
            new File(getDataFolder(), "kits").mkdirs();
        }

        if (!new File(getDataFolder(), "data").exists()) {
            new File(getDataFolder(), "data").mkdirs();
        }

        cargarArchivosInternos();

        Bukkit.getPluginManager().registerEvents(this, this);

        if (getCommand("akits") != null) {
            getCommand("akits").setExecutor(new KitsCommand());
        }
    }

    private void cargarArchivosInternos() {
        File fLang = new File(getDataFolder(), "lang.yml");
        File fMsgs = new File(getDataFolder(), "mensajes.yml");

        if (!fLang.exists()) {
            YamlConfiguration defLang = new YamlConfiguration();
            defLang.set("menus.principal-titulo", "&#00fbff&lKITS &#7f8c8d| &f{categoria}");
            defLang.set("menus.preview-titulo", "&#00fbff&lVISTA: &f{kit}");
            defLang.set("menus.confirmar-titulo", "&8¿Confirmar compra?");
            defLang.set("menus.admin-titulo", "&0Panel de Kits");
            defLang.set("menus.config-titulo", "&0Configurar Kit: {kit}");
            defLang.set("menus.items-titulo", "&0Editando Items: {kit}");
            defLang.set("menus.eliminar-titulo", "&4¿Eliminar Kit?");
            defLang.set("items.cambiar-categoria", "&#fbff00&lCATEGORÍA: &fClick para cambiar");
            defLang.set("items.kit-nombre", "&#00fbff&lKit: &f&n{nombre}");
            defLang.set("items.kit-lore-precio", "&#00fbff Precio: &a${precio}");
            try {
                defLang.save(fLang);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (!fMsgs.exists()) {
            YamlConfiguration defMsgs = new YamlConfiguration();
            defMsgs.set("mensajes.recibido", "&#00ff88&l✔ &f¡Has recibido el kit &e{kit}&f!");
            defMsgs.set("mensajes.sin-dinero", "&c&l✘ &7No tienes dinero suficiente ($ {precio}).");
            defMsgs.set("mensajes.cooldown", "&c&l✘ &7Espera &e{tiempo}s &7para volver a usarlo.");
            defMsgs.set("mensajes.compra-cancelada", "&cHas cancelado la compra.");
            defMsgs.set("mensajes.kit-eliminado", "&aKit eliminado correctamente.");
            defMsgs.set("mensajes.kit-creado", "&aKit creado correctamente: &e{kit}");
            defMsgs.set("mensajes.nombre-pedido", "&eEscribe en el chat el nombre del nuevo kit.");
            defMsgs.set("mensajes.precio-pedido", "&eEscribe el nuevo precio del kit.");
            defMsgs.set("mensajes.cooldown-pedido", "&eEscribe el cooldown en segundos.");
            defMsgs.set("mensajes.permiso-pedido", "&eEscribe el permiso personalizado del kit. Ejemplo: kits.vip");
            defMsgs.set("mensajes.icono-pedido", "&eEscribe el MATERIAL del icono. Ejemplo: DIAMOND_SWORD");
            defMsgs.set("mensajes.comando-pedido", "&eEscribe el comando para abrir kits. Ejemplo: kit o akits");
            defMsgs.set("mensajes.renombre-pedido", "&eEscribe el nuevo nombre para el kit.");
            defMsgs.set("mensajes.valor-invalido", "&cValor inválido.");
            defMsgs.set("mensajes.icono-invalido", "&cEse material no existe.");
            defMsgs.set("mensajes.kit-renombrado", "&aKit renombrado a &e{kit}");
            defMsgs.set("mensajes.config-actualizada", "&aConfiguración actualizada.");
            defMsgs.set("mensajes.solo-jugador", "&cSolo jugadores.");
            try {
                defMsgs.save(fMsgs);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        lang = YamlConfiguration.loadConfiguration(fLang);
        mensajes = YamlConfiguration.loadConfiguration(fMsgs);
    }

    public String c(String message) {
        if (message == null) return "";
        Pattern pattern = Pattern.compile("&#[a-fA-F0-9]{6}");
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            String color = message.substring(matcher.start(), matcher.end());
            message = message.replace(color, net.md_5.bungee.api.ChatColor.of(color.substring(1)) + "");
            matcher = pattern.matcher(message);
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    public class KitsCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
            if (!(s instanceof Player)) {
                s.sendMessage(c(mensajes.getString("mensajes.solo-jugador")));
                return true;
            }

            Player p = (Player) s;

            if (a.length == 0) {
                categoriaActual.putIfAbsent(p.getUniqueId(), "GRATIS");
                abrirMenuKits(p);
                return true;
            }

            if (a[0].equalsIgnoreCase("panel")) {
                if (!p.hasPermission("kitsadvanced.admin")) {
                    p.sendMessage(c("&cNo tienes permiso."));
                    return true;
                }
                abrirPanelAdmin(p);
                return true;
            }

            return true;
        }
    }

    @EventHandler
    public void alHacerClick(InventoryClickEvent e) {
        if (e.getView().getTitle() == null) return;
        if (!(e.getWhoClicked() instanceof Player)) return;

        Player p = (Player) e.getWhoClicked();
        String titulo = ChatColor.stripColor(e.getView().getTitle());

        if (titulo.equals(ChatColor.stripColor(c(lang.getString("menus.confirmar-titulo"))))) {
            e.setCancelled(true);

            if (e.getRawSlot() == 11) {
                String kitName = pendienteConfirmar.get(p.getUniqueId());
                if (kitName != null) {
                    darKit(p, kitName);
                }
                p.closeInventory();
            } else if (e.getRawSlot() == 15) {
                p.closeInventory();
                p.sendMessage(c(mensajes.getString("mensajes.compra-cancelada")));
            }
            return;
        }

        if (titulo.equals(ChatColor.stripColor(c(lang.getString("menus.eliminar-titulo"))))) {
            e.setCancelled(true);

            if (e.getRawSlot() == 11) {
                String kit = pendienteEliminar.remove(p.getUniqueId());
                if (kit != null) {
                    File f = new File(getDataFolder() + "/kits", kit + ".yml");
                    if (f.exists()) {
                        f.delete();
                    }
                    p.sendMessage(c(mensajes.getString("mensajes.kit-eliminado")));
                }
                p.closeInventory();
                Bukkit.getScheduler().runTask(this, () -> abrirPanelAdmin(p));
            } else if (e.getRawSlot() == 15) {
                p.closeInventory();
                Bukkit.getScheduler().runTask(this, () -> abrirConfigKit(p, editandoKit.get(p.getUniqueId())));
            }
            return;
        }

        if (titulo.contains("|")) {
            e.setCancelled(true);

            if (e.getRawSlot() == 49) {
                String actual = categoriaActual.getOrDefault(p.getUniqueId(), "GRATIS");
                String nueva = actual.equalsIgnoreCase("GRATIS") ? "PREMIUM" : "GRATIS";
                categoriaActual.put(p.getUniqueId(), nueva);
                abrirMenuKits(p);
                return;
            }

            if (e.getRawSlot() < 45 && e.getCurrentItem() != null && e.getCurrentItem().getType() != Material.AIR) {
                if (e.getCurrentItem().getItemMeta() == null) return;
                String display = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
                String kitName = display.replace("Kit: ", "").trim();

                if (e.getClick().isRightClick()) {
                    abrirPreview(p, kitName);
                } else {
                    abrirConfirmacion(p, kitName);
                }
            }
            return;
        }

        if (titulo.equals(ChatColor.stripColor(c(lang.getString("menus.admin-titulo"))))) {
            e.setCancelled(true);

            if (e.getRawSlot() == 11) {
                p.closeInventory();
                esperandoNombre.put(p.getUniqueId(), true);
                p.sendMessage(c(mensajes.getString("mensajes.nombre-pedido")));
                return;
            }

            if (e.getRawSlot() >= 13 && e.getCurrentItem() != null && e.getCurrentItem().getType() != Material.AIR) {
                if (e.getCurrentItem().getItemMeta() == null) return;
                String display = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
                if (display.startsWith("Editar: ")) {
                    String k = display.replace("Editar: ", "").trim();
                    abrirConfigKit(p, k);
                }
            }
            return;
        }

        if (titulo.startsWith("Configurar Kit:")) {
            e.setCancelled(true);

            String k = editandoKit.get(p.getUniqueId());
            if (k == null) return;

            FileConfiguration cf = getKitConfig(k);

            switch (e.getRawSlot()) {
                case 10:
                    abrirEditorDeItems(p, k);
                    return;
                case 11:
                    p.closeInventory();
                    esperandoPrecio.put(p.getUniqueId(), k);
                    p.sendMessage(c(mensajes.getString("mensajes.precio-pedido")));
                    return;
                case 12:
                    p.closeInventory();
                    esperandoCooldown.put(p.getUniqueId(), k);
                    p.sendMessage(c(mensajes.getString("mensajes.cooldown-pedido")));
                    return;
                case 13:
                    cf.set("requiere-permiso", !cf.getBoolean("requiere-permiso", false));
                    guardarKit(k, cf);
                    p.sendMessage(c(mensajes.getString("mensajes.config-actualizada")));
                    abrirConfigKit(p, k);
                    return;
                case 14:
                    p.closeInventory();
                    esperandoPermiso.put(p.getUniqueId(), k);
                    p.sendMessage(c(mensajes.getString("mensajes.permiso-pedido")));
                    return;
                case 15:
                    cf.set("auto-equip", !cf.getBoolean("auto-equip", false));
                    guardarKit(k, cf);
                    p.sendMessage(c(mensajes.getString("mensajes.config-actualizada")));
                    abrirConfigKit(p, k);
                    return;
                case 16:
                    p.closeInventory();
                    esperandoIcono.put(p.getUniqueId(), k);
                    p.sendMessage(c(mensajes.getString("mensajes.icono-pedido")));
                    return;
                case 19:
                    p.closeInventory();
                    esperandoRenombre.put(p.getUniqueId(), k);
                    p.sendMessage(c(mensajes.getString("mensajes.renombre-pedido")));
                    return;
                case 20:
                    boolean premium = cf.getBoolean("premium", false);
                    cf.set("premium", !premium);
                    guardarKit(k, cf);
                    p.sendMessage(c(mensajes.getString("mensajes.config-actualizada")));
                    abrirConfigKit(p, k);
                    return;
                case 21:
                    p.closeInventory();
                    esperandoComando.put(p.getUniqueId(), k);
                    p.sendMessage(c(mensajes.getString("mensajes.comando-pedido")));
                    return;
                case 31:
                    abrirPanelAdmin(p);
                    return;
                case 35:
                    abrirConfirmacionEliminar(p, k);
                    return;
                default:
                    return;
            }
        }

        if (titulo.startsWith("Editando Items:")) {
            if (e.getRawSlot() == 40) {
                e.setCancelled(true);
                String k = editandoKit.get(p.getUniqueId());
                if (k != null) {
                    guardarItemsDelMenu(e.getInventory(), k);
                    abrirConfigKit(p, k);
                }
                return;
            }

            if (e.getRawSlot() >= 36) {
                e.setCancelled(true);
            }
        }

        if (titulo.startsWith("VISTA:")) {
            e.setCancelled(true);
        }
    }

    public void darKit(Player p, String kitName) {
        FileConfiguration cf = getKitConfig(kitName);
        if (cf == null) return;

        String permisoPersonalizado = cf.getString("permiso", "");
        boolean requierePermiso = cf.getBoolean("requiere-permiso", false);

        if (requierePermiso) {
            String permisoFinal = permisoPersonalizado == null || permisoPersonalizado.isEmpty()
                    ? "kitsadvanced.kit." + kitName.toLowerCase()
                    : permisoPersonalizado;

            if (!p.hasPermission(permisoFinal) && !p.hasPermission("kitsadvanced.admin")) {
                p.sendMessage(c("&cNo tienes permiso para usar este kit."));
                return;
            }
        }

        if (checkCooldown(p, kitName, cf.getInt("cooldown", 0))) return;

        double precio = cf.getDouble("precio", 0);
        if (precio > 0 && econ != null) {
            if (econ.getBalance(p) < precio) {
                p.sendMessage(c(mensajes.getString("mensajes.sin-dinero").replace("{precio}", String.valueOf(precio))));
                return;
            }
            econ.withdrawPlayer(p, precio);
        }

        boolean autoEquip = cf.getBoolean("auto-equip", false);
        List<?> items = cf.getList("items");

        if (items != null) {
            for (Object itemObj : items) {
                if (itemObj instanceof ItemStack) {
                    ItemStack item = ((ItemStack) itemObj).clone();
                    if (autoEquip && esArmadura(item.getType())) {
                        equiparOAdd(p, item);
                    } else {
                        if (p.getInventory().firstEmpty() == -1) {
                            p.getWorld().dropItemNaturally(p.getLocation(), item);
                        } else {
                            p.getInventory().addItem(item);
                        }
                    }
                }
            }
        }

        p.sendMessage(c(mensajes.getString("mensajes.recibido").replace("{kit}", kitName)));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }

    private boolean esArmadura(Material m) {
        String n = m.name();
        return n.contains("HELMET") || n.contains("CHESTPLATE") || n.contains("LEGGINGS") || n.contains("BOOTS");
    }

    private void equiparOAdd(Player p, ItemStack item) {
        String n = item.getType().name();
        PlayerInventory inv = p.getInventory();

        if (n.contains("HELMET") && inv.getHelmet() == null) {
            inv.setHelmet(item);
        } else if (n.contains("CHESTPLATE") && inv.getChestplate() == null) {
            inv.setChestplate(item);
        } else if (n.contains("LEGGINGS") && inv.getLeggings() == null) {
            inv.setLeggings(item);
        } else if (n.contains("BOOTS") && inv.getBoots() == null) {
            inv.setBoots(item);
        } else {
            if (inv.firstEmpty() == -1) {
                p.getWorld().dropItemNaturally(p.getLocation(), item);
            } else {
                inv.addItem(item);
            }
        }
    }

    private boolean checkCooldown(Player p, String kit, int segs) {
        if (p.hasPermission("kitsadvanced.admin")) return false;

        File f = new File(getDataFolder(), "data/" + p.getUniqueId() + ".yml");
        FileConfiguration data = YamlConfiguration.loadConfiguration(f);

        long ahora = System.currentTimeMillis();
        long resta = (data.getLong(kit, 0) + (segs * 1000L)) - ahora;

        if (resta > 0) {
            p.sendMessage(c(mensajes.getString("mensajes.cooldown").replace("{tiempo}", String.valueOf(resta / 1000))));
            return true;
        }

        data.set(kit, ahora);
        try {
            data.save(f);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    public void abrirMenuKits(Player p) {
        String cat = categoriaActual.getOrDefault(p.getUniqueId(), "GRATIS");
        Inventory inv = Bukkit.createInventory(null, 54, c(lang.getString("menus.principal-titulo").replace("{categoria}", cat)));

        inv.setItem(49, createItem(Material.NETHER_STAR, lang.getString("items.cambiar-categoria"), true, "&7Actual: &f" + cat));

        File folder = new File(getDataFolder(), "kits");
        File[] kits = folder.listFiles();

        if (kits != null) {
            for (File f : kits) {
                if (!f.getName().endsWith(".yml")) continue;

                String n = f.getName().replace(".yml", "");
                FileConfiguration conf = YamlConfiguration.loadConfiguration(f);

                boolean premium = conf.getBoolean("premium", false);
                boolean mostrar = (cat.equalsIgnoreCase("PREMIUM") && premium) || (cat.equalsIgnoreCase("GRATIS") && !premium);

                if (mostrar) {
                    Material icon = Material.getMaterial(conf.getString("icono", "CHEST"));
                    if (icon == null) icon = Material.CHEST;

                    List<String> lore = new ArrayList<>();
                    lore.add("&7Precio: &a$" + conf.getDouble("precio", 0));
                    lore.add("&7Cooldown: &e" + conf.getInt("cooldown", 0) + "s");
                    lore.add("&7Tipo: " + (premium ? "&6PREMIUM" : "&aGRATIS"));
                    lore.add("&8Click izq: comprar/recibir");
                    lore.add("&8Click der: vista previa");

                    inv.addItem(createItem(icon, "&bKit: " + n, false, lore.toArray(new String[0])));
                }
            }
        }

        p.openInventory(inv);
    }

    public void abrirConfirmacion(Player p, String k) {
        pendienteConfirmar.put(p.getUniqueId(), k);

        Inventory inv = Bukkit.createInventory(null, 27, c(lang.getString("menus.confirmar-titulo")));
        inv.setItem(11, createItem(Material.LIME_DYE, "&a&lCONFIRMAR", true, "&7Recibir/comprar kit", "&f" + k));
        inv.setItem(15, createItem(Material.RED_DYE, "&c&lCANCELAR", false, "&7Cancelar acción"));

        p.openInventory(inv);
    }

    public void abrirPanelAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c(lang.getString("menus.admin-titulo")));

        inv.setItem(11, createItem(Material.CHEST, "&6&lCREAR KIT", true, "&7Click para crear un nuevo kit", "&7Se te pedirá el nombre por chat"));

        File folder = new File(getDataFolder(), "kits");
        File[] files = folder.listFiles();

        if (files != null) {
            int slot = 13;
            for (File f : files) {
                if (!f.getName().endsWith(".yml")) continue;
                if (slot >= 54) break;

                String nombre = f.getName().replace(".yml", "");
                FileConfiguration cf = YamlConfiguration.loadConfiguration(f);

                Material icon = Material.getMaterial(cf.getString("icono", "PAPER"));
                if (icon == null) icon = Material.PAPER;

                inv.setItem(slot, createItem(
                        icon,
                        "&eEditar: " + nombre,
                        false,
                        "&7Precio: &a$" + cf.getDouble("precio", 0),
                        "&7Cooldown: &e" + cf.getInt("cooldown", 0) + "s",
                        "&7Premium: " + (cf.getBoolean("premium", false) ? "&aSí" : "&cNo"),
                        "&8Click para configurar"
                ));
                slot++;
            }
        }

        p.openInventory(inv);
    }

    public void abrirConfigKit(Player p, String k) {
        editandoKit.put(p.getUniqueId(), k);

        FileConfiguration cf = getKitConfig(k);
        Inventory inv = Bukkit.createInventory(null, 45, c(lang.getString("menus.config-titulo").replace("{kit}", k)));

        inv.setItem(10, createItem(Material.CHEST, "&6&l1. Editar Items", true, "&7Abrir editor de items"));
        inv.setItem(11, createItem(Material.SUNFLOWER, "&e&l2. Precio", false, "&7Actual: &a$" + cf.getDouble("precio", 0), "&8Click para cambiar"));
        inv.setItem(12, createItem(Material.CLOCK, "&b&l3. Cooldown", false, "&7Actual: &e" + cf.getInt("cooldown", 0) + "s", "&8Click para cambiar"));
        inv.setItem(13, createItem(Material.IRON_DOOR, "&d&l4. Requiere Permiso", cf.getBoolean("requiere-permiso", false), "&7Estado: " + (cf.getBoolean("requiere-permiso", false) ? "&aON" : "&cOFF")));
        inv.setItem(14, createItem(Material.NAME_TAG, "&5&l5. Permiso Personalizado", false, "&7Actual: &f" + cf.getString("permiso", "No definido"), "&8Click para cambiar"));
        inv.setItem(15, createItem(Material.ARMOR_STAND, "&a&l6. Auto-Equipar", cf.getBoolean("auto-equip", false), "&7Estado: " + (cf.getBoolean("auto-equip", false) ? "&aON" : "&cOFF")));
        inv.setItem(16, createItem(Material.ITEM_FRAME, "&6&l7. Icono", false, "&7Actual: &f" + cf.getString("icono", "CHEST"), "&8Click para cambiar"));

        inv.setItem(19, createItem(Material.ANVIL, "&e&l8. Renombrar Kit", false, "&7Nombre actual: &f" + k, "&8Click para cambiar"));
        inv.setItem(20, createItem(Material.NETHER_STAR, "&c&l9. Tipo Premium", cf.getBoolean("premium", false), "&7Estado: " + (cf.getBoolean("premium", false) ? "&6PREMIUM" : "&aGRATIS")));
        inv.setItem(21, createItem(Material.COMMAND_BLOCK, "&3&l10. Comando Visible", false, "&7Actual: &f" + cf.getString("comando", "akits"), "&8Solo decorativo/configurable"));
        inv.setItem(31, createItem(Material.ARROW, "&cVolver", false, "&7Regresar al panel"));
        inv.setItem(35, createItem(Material.BARRIER, "&4&lELIMINAR KIT", true, "&7Eliminar este kit"));

        p.openInventory(inv);
    }

    public void abrirEditorDeItems(Player p, String k) {
        Inventory inv = Bukkit.createInventory(null, 45, c(lang.getString("menus.items-titulo").replace("{kit}", k)));

        FileConfiguration cf = getKitConfig(k);
        List<?> items = cf.getList("items");

        if (items != null) {
            int s = 0;
            for (Object i : items) {
                if (s >= 36) break;
                if (i instanceof ItemStack) {
                    inv.setItem(s, (ItemStack) i);
                    s++;
                }
            }
        }

        inv.setItem(40, createItem(Material.LIME_DYE, "&a&lGUARDAR ITEMS", true, "&7Guarda los items del kit"));
        p.openInventory(inv);
    }

    public void abrirConfirmacionEliminar(Player p, String k) {
        pendienteEliminar.put(p.getUniqueId(), k);

        Inventory inv = Bukkit.createInventory(null, 27, c(lang.getString("menus.eliminar-titulo")));
        inv.setItem(11, createItem(Material.LIME_DYE, "&a&lSI", true, "&7Eliminar kit &f" + k));
        inv.setItem(15, createItem(Material.RED_DYE, "&c&lNO", false, "&7Cancelar"));

        p.openInventory(inv);
    }

    public void abrirPreview(Player p, String k) {
        Inventory inv = Bukkit.createInventory(null, 45, c(lang.getString("menus.preview-titulo").replace("{kit}", k)));

        List<?> items = getKitConfig(k).getList("items");
        if (items != null) {
            for (Object i : items) {
                if (i instanceof ItemStack) {
                    inv.addItem((ItemStack) i);
                }
            }
        }

        p.openInventory(inv);
    }

    private ItemStack createItem(Material m, String n, boolean glint, String... lore) {
        ItemStack i = new ItemStack(m != null ? m : Material.STONE);
        ItemMeta mt = i.getItemMeta();

        if (mt != null) {
            mt.setDisplayName(c(n));

            if (lore.length > 0) {
                List<String> l = new ArrayList<>();
                for (String s : lore) {
                    l.add(c(s));
                }
                mt.setLore(l);
            }

            if (glint) {
                mt.addEnchant(Enchantment.PROTECTION, 1, true);
                mt.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            i.setItemMeta(mt);
        }

        return i;
    }

    private void guardarItemsDelMenu(Inventory inv, String kit) {
        FileConfiguration cf = getKitConfig(kit);
        List<ItemStack> items = new ArrayList<>();

        for (int i = 0; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                items.add(item);
            }
        }

        cf.set("items", items);
        guardarKit(kit, cf);
    }

    public FileConfiguration getKitConfig(String n) {
        File f = new File(getDataFolder() + "/kits", n + ".yml");
        return YamlConfiguration.loadConfiguration(f);
    }

    public void guardarKit(String n, FileConfiguration conf) {
        try {
            conf.save(new File(getDataFolder() + "/kits", n + ".yml"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void alHablar(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        String msg = e.getMessage().trim();

        if (esperandoNombre.containsKey(uuid)) {
            e.setCancelled(true);
            esperandoNombre.remove(uuid);

            String n = msg.replace(" ", "_");
            crearKitBase(n);

            p.sendMessage(c(mensajes.getString("mensajes.kit-creado").replace("{kit}", n)));

            Bukkit.getScheduler().runTask(this, () -> abrirPanelAdmin(p));
            return;
        }

        if (esperandoPrecio.containsKey(uuid)) {
            e.setCancelled(true);
            String k = esperandoPrecio.remove(uuid);

            try {
                double pr = Double.parseDouble(msg);
                FileConfiguration cf = getKitConfig(k);
                cf.set("precio", pr);
                guardarKit(k, cf);
                p.sendMessage(c("&aPrecio actualizado a &e" + pr));
            } catch (Exception ex) {
                p.sendMessage(c(mensajes.getString("mensajes.valor-invalido")));
            }

            Bukkit.getScheduler().runTask(this, () -> abrirConfigKit(p, k));
            return;
        }

        if (esperandoCooldown.containsKey(uuid)) {
            e.setCancelled(true);
            String k = esperandoCooldown.remove(uuid);

            try {
                int cd = Integer.parseInt(msg);
                FileConfiguration cf = getKitConfig(k);
                cf.set("cooldown", cd);
                guardarKit(k, cf);
                p.sendMessage(c("&aCooldown actualizado a &e" + cd + "s"));
            } catch (Exception ex) {
                p.sendMessage(c(mensajes.getString("mensajes.valor-invalido")));
            }

            Bukkit.getScheduler().runTask(this, () -> abrirConfigKit(p, k));
            return;
        }

        if (esperandoPermiso.containsKey(uuid)) {
            e.setCancelled(true);
            String k = esperandoPermiso.remove(uuid);

            FileConfiguration cf = getKitConfig(k);
            cf.set("permiso", msg);
            guardarKit(k, cf);

            p.sendMessage(c("&aPermiso actualizado."));
            Bukkit.getScheduler().runTask(this, () -> abrirConfigKit(p, k));
            return;
        }

        if (esperandoIcono.containsKey(uuid)) {
            e.setCancelled(true);
            String k = esperandoIcono.remove(uuid);

            Material mat = Material.matchMaterial(msg.toUpperCase());
            if (mat == null) {
                p.sendMessage(c(mensajes.getString("mensajes.icono-invalido")));
                Bukkit.getScheduler().runTask(this, () -> abrirConfigKit(p, k));
                return;
            }

            FileConfiguration cf = getKitConfig(k);
            cf.set("icono", mat.name());
            guardarKit(k, cf);

            p.sendMessage(c("&aIcono actualizado a &e" + mat.name()));
            Bukkit.getScheduler().runTask(this, () -> abrirConfigKit(p, k));
            return;
        }

        if (esperandoComando.containsKey(uuid)) {
            e.setCancelled(true);
            String k = esperandoComando.remove(uuid);

            FileConfiguration cf = getKitConfig(k);
            cf.set("comando", msg.toLowerCase());
            guardarKit(k, cf);

            p.sendMessage(c("&aComando guardado en config: &e" + msg.toLowerCase()));
            Bukkit.getScheduler().runTask(this, () -> abrirConfigKit(p, k));
            return;
        }

        if (esperandoRenombre.containsKey(uuid)) {
            e.setCancelled(true);
            String viejo = esperandoRenombre.remove(uuid);
            String nuevo = msg.replace(" ", "_");

            File viejoFile = new File(getDataFolder() + "/kits", viejo + ".yml");
            File nuevoFile = new File(getDataFolder() + "/kits", nuevo + ".yml");

            if (nuevoFile.exists()) {
                p.sendMessage(c("&cYa existe un kit con ese nombre."));
                Bukkit.getScheduler().runTask(this, () -> abrirConfigKit(p, viejo));
                return;
            }

            boolean ok = viejoFile.renameTo(nuevoFile);

            if (ok) {
                editandoKit.put(uuid, nuevo);
                p.sendMessage(c(mensajes.getString("mensajes.kit-renombrado").replace("{kit}", nuevo)));
                Bukkit.getScheduler().runTask(this, () -> abrirConfigKit(p, nuevo));
            } else {
                p.sendMessage(c("&cNo se pudo renombrar el archivo del kit."));
                Bukkit.getScheduler().runTask(this, () -> abrirConfigKit(p, viejo));
            }
        }
    }

    private void crearKitBase(String n) {
        File f = new File(getDataFolder() + "/kits", n + ".yml");
        FileConfiguration c = YamlConfiguration.loadConfiguration(f);

        c.set("nombre", n);
        c.set("precio", 0.0);
        c.set("cooldown", 3600);
        c.set("requiere-permiso", false);
        c.set("permiso", "kitsadvanced.kit." + n.toLowerCase());
        c.set("auto-equip", false);
        c.set("icono", "CHEST");
        c.set("premium", false);
        c.set("comando", "akits");
        c.set("items", new ArrayList<ItemStack>());

        try {
            c.save(f);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
