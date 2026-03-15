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
import java.io.File;
import java.io.IOException;
import java.util.*;

public class Principal extends JavaPlugin implements Listener {
    private static Economy econ = null;
    private final Map<UUID, String> editandoKit = new HashMap<>();
    private final Map<UUID, String> modoChat = new HashMap<>();
    private final Map<UUID, String> categoriaActual = new HashMap<>();

    @Override
    public void onEnable() {
        if (!setupEconomy()) getLogger().severe("Â¡Vault no encontrado! La economÃ­a no funcionarÃ¡.");
        
        saveDefaultConfig();
        cargarConfigPredeterminada();
        
        new File(getDataFolder(), "kits").mkdirs();
        new File(getDataFolder(), "userdata").mkdirs();
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("akits").setExecutor(new KitsCommand());
        
        Bukkit.getConsoleSender().sendMessage(c("&b&lâ¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬"));
        Bukkit.getConsoleSender().sendMessage(c(getPrefix() + "&a&lSISTEMA PROFESIONAL ACTIVADO"));
        Bukkit.getConsoleSender().sendMessage(c(getPrefix() + "&fIdioma: &e" + getConfig().getString("Configuracion.idioma").toUpperCase()));
        Bukkit.getConsoleSender().sendMessage(c("&b&lâ¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬"));
    }

    private void cargarConfigPredeterminada() {
        FileConfiguration config = getConfig();
        config.addDefault("Configuracion.prefix", "&8[&bAdvancedKits&8] ");
        config.addDefault("Configuracion.idioma", "es");
        config.addDefault("Bienvenida.activado", true);
        config.addDefault("Bienvenida.mensaje", Arrays.asList(
            "&b&lâ¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬",
            "{prefix} &fÂ¡Bienvenido, &b{player}&f!",
            "&7Usa &e/akits &7para ver tus kits.",
            "&b&lâ¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬â¬"
        ));
        config.addDefault("Bienvenida.efectos.sonido", true);
        config.addDefault("Bienvenida.efectos.tipo-sonido", "ENTITY_PLAYER_LEVELUP");
        config.addDefault("Bienvenida.efectos.particulas", true);
        config.options().copyDefaults(true);
        saveConfig();
    }

    public String msg(String es, String en) {
        return getConfig().getString("Configuracion.idioma", "es").equalsIgnoreCase("es") ? es : en;
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        return rsp != null && (econ = rsp.getProvider()) != null;
    }

    public String getPrefix() { return getConfig().getString("Configuracion.prefix", "&8[&bAdvancedKits&8] "); }
    public String c(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    @EventHandler
    public void alEntrar(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        FileConfiguration config = getConfig();
        if (!config.getBoolean("Bienvenida.activado", true)) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (String linea : config.getStringList("Bienvenida.mensaje")) {
                p.sendMessage(c(linea.replace("{player}", p.getName()).replace("{prefix}", getPrefix())));
            }
            if (config.getBoolean("Bienvenida.efectos.sonido", true)) {
                p.playSound(p.getLocation(), Sound.valueOf(config.getString("Bienvenida.efectos.tipo-sonido", "ENTITY_PLAYER_LEVELUP")), 1f, 0.8f);
            }
            if (config.getBoolean("Bienvenida.efectos.particulas", true)) {
                p.spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.05);
            }
        }, 25L);
    }

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

            if (a.length == 0) {
                categoriaActual.putIfAbsent(p.getUniqueId(), "GRATIS");
                abrirMenuKits(p);
                return true;
            }

            if (a[0].equalsIgnoreCase("admin")) {
                if (p.hasPermission("kitsadvanced.admin")) abrirPanelAdmin(p);
                else p.sendMessage(c(getPrefix() + "&cNo tienes permiso."));
                return true;
            }

            if (a[0].equalsIgnoreCase("reload")) {
                if (p.hasPermission("kitsadvanced.admin")) {
                    reloadConfig();
                    p.sendMessage(c(getPrefix() + msg("&aÂ¡ConfiguraciÃ³n recargada!", "&aÂ¡Reloaded!")));
                } else p.sendMessage(c(getPrefix() + "&cNo tienes permiso."));
                return true;
            }

            p.sendMessage(c("&b&l" + getPrefix()));
            p.sendMessage(c("&7/akits &8- &fAbrir menÃº de kits."));
            if (p.hasPermission("kitsadvanced.admin")) {
                p.sendMessage(c("&7/akits admin &8- &fPanel de administraciÃ³n."));
                p.sendMessage(c("&7/akits reload &8- &fRecargar configuraciÃ³n."));
            }
            return true;
        }
    }

    public void abrirMenuKits(Player p) {
        String cat = categoriaActual.getOrDefault(p.getUniqueId(), "GRATIS");
        String title = msg("&8&lKITS &7- ", "&8&lKITS &7- ") + (cat.equals("GRATIS") ? c("&fGRATIS") : c("&bPREMIUM"));
        Inventory inv = Bukkit.createInventory(null, 54, title);
        ItemStack vid = createItem(Material.WHITE_STAINED_GLASS_PANE, " ", false);
        for (int i = 0; i < 54; i++) if (i < 9 || i > 44 || i % 9 == 0 || (i + 1) % 9 == 0) inv.setItem(i, vid);
        inv.setItem(49, createItem(Material.NETHER_STAR, msg("&e&lCATEGORÃA", "&e&lCATEGORY"), true, msg("&7Viendo: &f", "&7Viewing: &f") + cat));

        File folder = new File(getDataFolder(), "kits");
        File[] archivos = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (archivos != null) {
            for (File f : archivos) {
                String nombre = f.getName().replace(".yml", "");
                FileConfiguration conf = getKitConfig(nombre);
                if (conf != null && conf.getBoolean("requiere-permiso") == cat.equals("PREMIUM")) {
                    inv.addItem(createItem(Material.getMaterial(conf.getString("icono", "STONE_SWORD")), "&a&lKit: &f" + nombre, false, 
                        msg("&7Precio: &a$", "&7Price: &a$") + conf.getDouble("precio"), 
                        msg("&7Cooldown: &b", "&7Cooldown: &b") + formatTime(conf.getInt("cooldown"))));
                }
            }
        }
        p.openInventory(inv);
    }

    public void abrirPanelAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c(msg("&0&lADMIN &8- Panel Principal", "&0&lADMIN &8- Main Panel")));
        inv.setItem(4, createItem(Material.NETHER_STAR, msg("&b&l[+] CREAR KIT", "&b&l[+] CREATE KIT"), true));
        File folder = new File(getDataFolder(), "kits");
        File[] archivos = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (archivos != null) {
            int slot = 9;
            for (File f : archivos) {
                if (slot > 44) break;
                inv.setItem(slot++, createItem(Material.PAPER, msg("&e&lEDITAR: &f", "&e&lEDIT: &f") + f.getName().replace(".yml", ""), false));
            }
        }
        p.openInventory(inv);
    }

    public void abrirOpcionesKit(Player p, String k) {
        editandoKit.put(p.getUniqueId(), k);
        FileConfiguration cf = getKitConfig(k);
        Inventory inv = Bukkit.createInventory(null, 54, c(msg("&0&lAJUSTES: &8", "&0&lSETTINGS: &8") + k));
        for (int i = 0; i < 54; i++) inv.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, " ", false));
        inv.setItem(10, createItem(Material.CHEST, msg("&6&l1. ITEMS", "&6&l1. ITEMS"), false));
        inv.setItem(11, createItem(Material.SUNFLOWER, msg("&e&l2. PRECIO", "&e&l2. PRICE"), cf.getDouble("precio") > 0, "&7$" + cf.getDouble("precio")));
        inv.setItem(12, createItem(Material.CLOCK, msg("&b&l3. COOLDOWN", "&b&l3. COOLDOWN"), cf.getInt("cooldown") > 0, "&7" + formatTime(cf.getInt("cooldown"))));
        inv.setItem(13, createItem(Material.REDSTONE, msg("&d&l4. CATEGORÃA", "&d&l4. CATEGORY"), cf.getBoolean("requiere-permiso"), "Premium: " + cf.getBoolean("requiere-permiso")));
        inv.setItem(14, createItem(Material.IRON_CHESTPLATE, msg("&f&l5. AUTO-EQUIP", "&f&l5. AUTO-EQUIP"), cf.getBoolean("auto-equip"), "Status: " + cf.getBoolean("auto-equip")));
        inv.setItem(15, createItem(Material.getMaterial(cf.getString("icono", "STONE_SWORD")), msg("&6&l6. ICONO", "&6&l6. ICON"), true));
        inv.setItem(16, createItem(Material.NAME_TAG, msg("&3&l7. PERMISO", "&3&l7. PERMISSION"), !cf.getString("permiso").equals("none"), "Perm: " + cf.getString("permiso")));
        inv.setItem(19, createItem(Material.COMMAND_BLOCK, msg("&5&l8. COMANDO", "&5&l8. COMMAND"), !cf.getString("comando-consola").equals("none"), "Cmd: " + cf.getString("comando-consola")));
        inv.setItem(21, createItem(Material.PAPER, msg("&a&l9. ANUNCIO", "&a&l9. ANNOUNCE"), cf.getBoolean("anuncio")));
        inv.setItem(25, createItem(Material.BARRIER, msg("&4&lELIMINAR", "&4&lDELETE"), false));
        inv.setItem(49, createItem(Material.ARROW, msg("&cÂ« VOLVER", "&cÂ« BACK"), false));
        p.openInventory(inv);
    }

    @EventHandler
    public void alHacerClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.getCurrentItem() == null) return;
        Player p = (Player) e.getWhoClicked();
        String t = ChatColor.stripColor(e.getView().getTitle());
        String k = editandoKit.get(p.getUniqueId());

        if (t.contains("AJUSTES") || t.contains("SETTINGS")) {
            e.setCancelled(true);
            FileConfiguration cf = getKitConfig(k);
            switch (e.getRawSlot()) {
                case 10: abrirCofreEditor(p, k); break;
                case 11: p.closeInventory(); modoChat.put(p.getUniqueId(), "PRECIO"); p.sendMessage(c(getPrefix() + msg("&eEscribe el precio:", "&eType the price:"))); break;
                case 12: p.closeInventory(); modoChat.put(p.getUniqueId(), "COOLDOWN"); p.sendMessage(c(getPrefix() + msg("&bEscribe segundos:", "&bType seconds:"))); break;
                case 13: cf.set("requiere-permiso", !cf.getBoolean("requiere-permiso")); guardarKit(k, cf); abrirOpcionesKit(p, k); break;
                case 14: cf.set("auto-equip", !cf.getBoolean("auto-equip")); guardarKit(k, cf); abrirOpcionesKit(p, k); break;
                case 15: 
                    if (p.getInventory().getItemInMainHand().getType() != Material.AIR) {
                        cf.set("icono", p.getInventory().getItemInMainHand().getType().name());
                        guardarKit(k, cf); p.sendMessage(c(getPrefix() + msg("&aÂ¡Icono cambiado!", "&aÂ¡Icon changed!")));
                    } abrirOpcionesKit(p, k); break;
                case 16: p.closeInventory(); modoChat.put(p.getUniqueId(), "PERMISO"); p.sendMessage(c(getPrefix() + msg("&3Escribe el permiso (o 'none'):", "&3Type permission (or 'none'):"))); break;
                case 19: p.closeInventory(); modoChat.put(p.getUniqueId(), "COMANDO"); p.sendMessage(c(getPrefix() + msg("&5Escribe comando (sin /):", "&5Type command (no /):"))); break;
                case 21: cf.set("anuncio", !cf.getBoolean("anuncio")); guardarKit(k, cf); abrirOpcionesKit(p, k); break;
                case 25: new File(getDataFolder() + "/kits", k + ".yml").delete(); abrirPanelAdmin(p); break;
                case 49: abrirPanelAdmin(p); break;
            }
        } else if (t.contains("ADMIN")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 4) { p.closeInventory(); modoChat.put(p.getUniqueId(), "CREAR"); p.sendMessage(c(getPrefix() + msg("&bNombre del kit:", "&bKit name:"))); }
            else if (e.getCurrentItem().getType() == Material.PAPER) {
                String kitName = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("EDITAR: ", "").replace("EDIT: ", "");
                abrirOpcionesKit(p, kitName);
            }
        } else if (t.contains("KITS -")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 49) { categoriaActual.put(p.getUniqueId(), categoriaActual.get(p.getUniqueId()).equals("GRATIS") ? "PREMIUM" : "GRATIS"); abrirMenuKits(p); }
            else if (e.getRawSlot() < 45) darKit(p, ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("Kit: ", ""));
        } else if (t.contains("Items")) {
            if (e.getRawSlot() >= 36) { e.setCancelled(true); if (e.getRawSlot() <= 43) { guardarItemsEditor(e.getInventory(), k); abrirOpcionesKit(p, k); } else if (e.getRawSlot() == 44) abrirOpcionesKit(p, k); }
        }
    }

    private void darKit(Player p, String k) {
        FileConfiguration cf = getKitConfig(k);
        if (cf == null) return;

        // --- VERIFICACIÃN DE PERMISO ---
        String perm = cf.getString("permiso", "none");
        if (!perm.equalsIgnoreCase("none") && !p.hasPermission(perm)) {
            p.sendMessage(c(getPrefix() + msg("&cNo tienes permiso para este kit.", "&cNo permission.")));
            return;
        }

        File userFile = new File(getDataFolder() + "/userdata", p.getUniqueId() + ".yml");
        FileConfiguration userConf = YamlConfiguration.loadConfiguration(userFile);
        long cd = (userConf.getLong("cd." + k, 0) + (cf.getInt("cooldown") * 1000L)) - System.currentTimeMillis();
        
        if (cd > 0) { p.sendMessage(c(getPrefix() + msg("&cEspera ", "&cWait ") + formatTimeRemaining(cd))); return; }
        if (cf.getDouble("precio") > 0 && econ != null) {
            if (econ.getBalance(p) < cf.getDouble("precio")) { p.sendMessage(c(getPrefix() + msg("&cNo tienes dinero.", "&cNo money."))); return; }
            econ.withdrawPlayer(p, cf.getDouble("precio"));
        }

        List<?> items = cf.getList("items");
        if (items != null) {
            for (Object o : items) {
                if (!(o instanceof ItemStack)) continue;
                ItemStack item = (ItemStack) o;
                if (cf.getBoolean("auto-equip")) {
                    String type = item.getType().name();
                    if (type.endsWith("_HELMET")) p.getInventory().setHelmet(item);
                    else if (type.endsWith("_CHESTPLATE")) p.getInventory().setChestplate(item);
                    else if (type.endsWith("_LEGGINGS")) p.getInventory().setLeggings(item);
                    else if (type.endsWith("_BOOTS")) p.getInventory().setBoots(item);
                    else p.getInventory().addItem(item);
                } else p.getInventory().addItem(item);
            }
            if (cf.getBoolean("requiere-permiso")) lanzarFuegoArtificial(p);
            if (cf.getBoolean("anuncio")) Bukkit.broadcastMessage(c(getPrefix() + msg("&e" + p.getName() + " &fha usado el kit &b" + k, "&e" + p.getName() + " &fused kit &b" + k)));
            String cmd = cf.getString("comando-consola", "none");
            if (!cmd.equals("none")) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
            userConf.set("cd." + k, System.currentTimeMillis());
            try { userConf.save(userFile); } catch (IOException e) {}
            p.sendMessage(c(getPrefix() + msg("&aÂ¡Kit recibido!", "&aÂ¡Kit received!")));
        }
    }

    private void lanzarFuegoArtificial(Player p) {
        Firework fw = p.getWorld().spawn(p.getLocation(), Firework.class);
        FireworkMeta fm = fw.getFireworkMeta();
        fm.addEffect(FireworkEffect.builder().withColor(Color.AQUA, Color.WHITE).with(FireworkEffect.Type.BALL_LARGE).build());
        fm.setPower(1); fw.setFireworkMeta(fm);
    }

    @EventHandler
    public void alChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!modoChat.containsKey(p.getUniqueId())) return;
        e.setCancelled(true);
        String msgInput = e.getMessage().trim();
        
        Bukkit.getScheduler().runTask(this, () -> {
            String modo = modoChat.get(p.getUniqueId());
            String k = editandoKit.get(p.getUniqueId());
            FileConfiguration cf = getKitConfig(k);
            
            try {
                if (modo.equals("CREAR")) {
                    crearKitBase(msgInput.replace(" ", "_"), false);
                    abrirOpcionesKit(p, msgInput.replace(" ", "_"));
                } else {
                    if (modo.equals("PRECIO")) cf.set("precio", Double.parseDouble(msgInput));
                    else if (modo.equals("COOLDOWN")) cf.set("cooldown", Integer.parseInt(msgInput));
                    else if (modo.equals("PERMISO")) cf.set("permiso", msgInput);
                    else if (modo.equals("COMANDO")) cf.set("comando-consola", msgInput);
                    guardarKit(k, cf);
                    abrirOpcionesKit(p, k);
                }
            } catch (Exception ex) {
                p.sendMessage(c(getPrefix() + "&cÂ¡Error! Entrada no vÃ¡lida (Â¿pusiste letras en vez de nÃºmeros?)."));
                if (!modo.equals("CREAR")) abrirOpcionesKit(p, k);
            }
            modoChat.remove(p.getUniqueId());
        });
    }

    private void crearKitBase(String n, boolean esP) {
        FileConfiguration c = new YamlConfiguration();
        c.set("precio", 0.0); c.set("cooldown", 0); c.set("requiere-permiso", esP);
        c.set("icono", "STONE_SWORD"); c.set("permiso", "none"); c.set("comando-consola", "none");
        c.set("auto-equip", false); c.set("anuncio", false); c.set("items", new ArrayList<ItemStack>());
        guardarKit(n, c);
    }

    private String formatTime(int s) { return s <= 0 ? msg("Sin CD", "No CD") : (s / 60) + "m"; }
    private String formatTimeRemaining(long ms) { return ((ms / 1000) / 60) + "m " + ((ms / 1000) % 60) + "s"; }

    private void abrirCofreEditor(Player p, String k) {
        Inventory ed = Bukkit.createInventory(null, 45, c(msg("&0Items de: ", "&0Items of: ") + k));
        FileConfiguration cf = getKitConfig(k);
        List<?> items = cf.getList("items");
        if (items != null) for (Object i : items) if (i instanceof ItemStack) ed.addItem((ItemStack) i);
        for (int i = 36; i < 44; i++) ed.setItem(i, createItem(Material.LIME_STAINED_GLASS_PANE, msg("&a&lGUARDAR", "&a&lSAVE"), true));
        ed.setItem(44, createItem(Material.ARROW, msg("&cVOLVER", "&cBACK"), false));
        p.openInventory(ed);
    }

    private void guardarItemsEditor(Inventory inv, String k) {
        FileConfiguration cf = getKitConfig(k);
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < 36; i++) { ItemStack item = inv.getItem(i); if (item != null && item.getType() != Material.AIR) list.add(item); }
        cf.set("items", list); guardarKit(k, cf);
    }

    private ItemStack createItem(Material m, String n, boolean glint, String... lore) {
        ItemStack i = new ItemStack(m); ItemMeta mt = i.getItemMeta();
        mt.setDisplayName(c(n)); List<String> l = new ArrayList<>();
        for (String s : lore) l.add(c(s)); mt.setLore(l);
        if (glint) { mt.addEnchant(Enchantment.PROTECTION, 1, true); mt.addItemFlags(ItemFlag.HIDE_ENCHANTS); }
        i.setItemMeta(mt); return i;
    }
}
