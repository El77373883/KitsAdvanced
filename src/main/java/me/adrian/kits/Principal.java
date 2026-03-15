package me.adrian.kits;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Principal extends JavaPlugin implements Listener {
    private static Economy econ = null;
    private final Map<UUID, String> categoriaActual = new HashMap<>();
    private FileConfiguration lang;
    private FileConfiguration mensajes;

    @Override
    public void onEnable() {
        setupEconomy();
        
        // Configuración inicial de Discord
        getConfig().addDefault("discord.activado", false);
        getConfig().addDefault("discord.webhook-url", "TU_URL_AQUI");
        getConfig().options().copyDefaults(true);
        saveConfig();

        // Crear carpetas necesarias
        new File(getDataFolder(), "kits/GRATIS").mkdirs();
        new File(getDataFolder(), "kits/PREMIUM").mkdirs();
        new File(getDataFolder(), "data").mkdirs(); 
        
        cargarArchivosInternos();
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("akits").setExecutor(new KitsCommand());
        
        getLogger().info("§aAdvancedKits: Sistema de Logs y Discord Webhooks cargado con éxito.");
    }

    private void cargarArchivosInternos() {
        File fLang = new File(getDataFolder(), "lang.yml");
        File fMsgs = new File(getDataFolder(), "mensajes.yml");
        if (!fLang.exists()) {
            YamlConfiguration defLang = new YamlConfiguration();
            defLang.set("menus.principal-titulo", "&#00fbff&lKITS &#7f8c8d| &f{categoria}");
            defLang.set("items.cambiar-categoria", "&#fbff00&lCATEGORÍAS &7(Click)");
            defLang.set("items.kit-nombre", "&#00fbff&lKit: &f&n{nombre}");
            defLang.set("items.kit-lore-precio", "&#00fbff Precio: &a${precio}");
            try { defLang.save(fLang); } catch (IOException e) { e.printStackTrace(); }
        }
        if (!fMsgs.exists()) {
            YamlConfiguration defMsgs = new YamlConfiguration();
            defMsgs.set("mensajes.recibido-actionbar", "&#00ff88&l✔ &fKit entregado con éxito");
            defMsgs.set("mensajes.sin-dinero", "&c&l✘ &7No tienes dinero suficiente ($ {precio}).");
            defMsgs.set("mensajes.cooldown", "&c&l✘ &7Espera &e{tiempo} &7para volver a usarlo.");
            try { defMsgs.save(fMsgs); } catch (IOException e) { e.printStackTrace(); }
        }
        lang = YamlConfiguration.loadConfiguration(fLang);
        mensajes = YamlConfiguration.loadConfiguration(fMsgs);
    }

    // --- SISTEMA DE DISCORD (ASÍNCRONO) ---
    public void enviarADiscord(String contenido) {
        if (!getConfig().getBoolean("discord.activado")) return;
        String webhookURL = getConfig().getString("discord.webhook-url");
        if (webhookURL == null || webhookURL.equals("TU_URL_AQUI")) return;

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                URL url = new URL(webhookURL);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.addRequestProperty("Content-Type", "application/json");
                con.addRequestProperty("User-Agent", "Java-Discord-Webhook");
                con.setDoOutput(true);
                con.setRequestMethod("POST");

                // Payload simple en JSON
                String json = "{\"content\": \"" + contenido + "\"}";

                try (OutputStream os = con.getOutputStream()) {
                    os.write(json.getBytes());
                }
                con.getInputStream().close();
            } catch (Exception e) {
                getLogger().warning("No se pudo conectar con Discord: " + e.getMessage());
            }
        });
    }

    // --- SISTEMA DE LOGS EN ARCHIVO ---
    public void registrarLog(String texto) {
        try {
            File logFile = new File(getDataFolder(), "logs.txt");
            if (!logFile.exists()) logFile.createNewFile();
            
            String fecha = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date());
            PrintWriter pw = new PrintWriter(new FileWriter(logFile, true));
            pw.println("[" + fecha + "] " + texto);
            pw.flush();
            pw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- PROCESO DE ENTREGA DE KIT ---
    public void darKit(Player p, String kitName) {
        String cat = categoriaActual.getOrDefault(p.getUniqueId(), "GRATIS");
        FileConfiguration cf = getKitConfig(cat + "/" + kitName);
        
        // Validar Cooldown
        if (checkCooldown(p, kitName, cf.getInt("cooldown", 3600))) return;

        // Validar Dinero
        double precio = cf.getDouble("precio", 0);
        if (econ != null && precio > 0) {
            if (econ.getBalance(p) < precio) {
                p.sendMessage(c(mensajes.getString("mensajes.sin-dinero").replace("{precio}", String.valueOf(precio))));
                return;
            }
            econ.withdrawPlayer(p, precio);
        }

        // REGISTROS (Log y Discord)
        String info = "Jugador: " + p.getName() + " | Kit: " + kitName + " | Precio: $" + precio;
        registrarLog("[RECLAMO] " + info);
        enviarADiscord("**:package: KIT RECLAMADO**\\n" + info);

        // Ejecutar Comandos
        cf.getStringList("comandos").forEach(cmd -> 
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName())));

        // Entregar Items
        List<?> items = cf.getList("items");
        if (items != null) {
            for (Object i : items) {
                if (i instanceof ItemStack) {
                    if (p.getInventory().firstEmpty() == -1) p.getWorld().dropItemNaturally(p.getLocation(), (ItemStack) i);
                    else p.getInventory().addItem((ItemStack) i);
                }
            }
        }

        // Efectos Visuales
        p.sendTitle(c("&#00ff88&l¡KIT RECIBIDO!"), c("&fHas obtenido &e" + kitName), 10, 40, 10);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.5f);
        p.spawnParticle(Particle.TOTEM, p.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.1);
    }

    // --- UTILIDADES ---
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
        return rsp != null && (econ = rsp.getProvider()) != null;
    }

    private String formatTime(int seconds) {
        if (seconds < 60) return seconds + "s";
        int mins = seconds / 60;
        int restSecs = seconds % 60;
        if (mins < 60) return mins + "m " + restSecs + "s";
        int hours = mins / 60;
        int restMins = mins % 60;
        return hours + "h " + restMins + "m";
    }

    private boolean checkCooldown(Player p, String kitName, int cooldownSecs) {
        if (p.hasPermission("akits.admin.bypass")) return false;
        File f = new File(getDataFolder(), "data/" + p.getUniqueId() + ".yml");
        FileConfiguration data = YamlConfiguration.loadConfiguration(f);
        long ahora = System.currentTimeMillis();
        long ultimoUso = data.getLong("cooldowns." + kitName, 0);
        long restante = (ultimoUso + (cooldownSecs * 1000L)) - ahora;

        if (restante > 0) {
            p.sendMessage(c(mensajes.getString("mensajes.cooldown").replace("{tiempo}", formatTime((int) (restante / 1000)))));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return true;
        }
        data.set("cooldowns." + kitName, ahora);
        try { data.save(f); } catch (IOException e) { e.printStackTrace(); }
        return false;
    }

    // --- COMANDOS Y MENÚS ---
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
            if (a[0].equalsIgnoreCase("panel") && p.hasPermission("kitsadvanced.admin")) {
                abrirPanelAdmin(p);
            }
            return true;
        }
    }

    @EventHandler
    public void alHacerClick(InventoryClickEvent e) {
        if (e.getView().getTitle() == null) return;
        Player p = (Player) e.getWhoClicked();
        String t = ChatColor.stripColor(e.getView().getTitle());

        if (t.contains("|")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 49) abrirSelectorCategorias(p);
            else if (e.getRawSlot() < 45 && e.getCurrentItem() != null && e.getCurrentItem().getType() != Material.AIR) {
                String kitName = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("Kit: ", "");
                darKit(p, kitName);
            }
        }
        if (t.equals("Seleccionar Categoría")) {
            e.setCancelled(true);
            if (e.getCurrentItem() != null && e.getCurrentItem().getType() != Material.AIR) {
                categoriaActual.put(p.getUniqueId(), ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()));
                abrirMenuKits(p);
            }
        }
    }

    public void abrirMenuKits(Player p) {
        String cat = categoriaActual.getOrDefault(p.getUniqueId(), "GRATIS");
        Inventory inv = Bukkit.createInventory(null, 54, c(lang.getString("menus.principal-titulo").replace("{categoria}", cat)));
        ItemStack frame = createItem(Material.CYAN_STAINED_GLASS_PANE, " ", false);
        for (int i = 0; i < 54; i++) if (i < 9 || i > 44 || i % 9 == 0 || (i + 1) % 9 == 0) inv.setItem(i, frame);
        inv.setItem(49, createItem(Material.NETHER_STAR, c(lang.getString("items.cambiar-categoria")), true));

        File catFolder = new File(getDataFolder(), "kits/" + cat);
        if (catFolder.exists()) {
            File[] kits = catFolder.listFiles();
            if (kits != null) {
                for (File f : kits) {
                    if (!f.getName().endsWith(".yml")) continue;
                    FileConfiguration conf = YamlConfiguration.loadConfiguration(f);
                    inv.addItem(createItem(Material.getMaterial(conf.getString("icono", "CHEST")), 
                        c(lang.getString("items.kit-nombre").replace("{nombre}", f.getName().replace(".yml", ""))), false, 
                        c(lang.getString("items.kit-lore-precio").replace("{precio}", String.valueOf(conf.getDouble("precio")))),
                        "&7Espera: &f" + formatTime(conf.getInt("cooldown")), " ", "&eClick para reclamar."));
                }
            }
        }
        p.openInventory(inv);
    }

    public void abrirSelectorCategorias(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, c("&0Seleccionar Categoría"));
        File folder = new File(getDataFolder(), "kits");
        File[] cats = folder.listFiles(File::isDirectory);
        if (cats != null) {
            for (File f : cats) inv.addItem(createItem(Material.CHEST, "&e&l" + f.getName(), true, "&7Click para entrar."));
        }
        p.openInventory(inv);
    }

    public void abrirPanelAdmin(Player p) {
        String cat = categoriaActual.getOrDefault(p.getUniqueId(), "GRATIS");
        Inventory inv = Bukkit.createInventory(null, 54, c("&0&lADMIN &8- &7" + cat));
        File catFolder = new File(getDataFolder(), "kits/" + cat);
        File[] archivos = catFolder.listFiles();
        if (archivos != null) {
            int slot = 9;
            for (File f : archivos) {
                if (slot > 44) break;
                inv.setItem(slot++, createItem(Material.PAPER, "&eEditar: &f" + f.getName().replace(".yml", ""), false));
            }
        }
        p.openInventory(inv);
    }

    public FileConfiguration getKitConfig(String path) {
        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "kits/" + path + ".yml"));
    }

    private ItemStack createItem(Material m, String n, boolean glint, String... lore) {
        ItemStack i = new ItemStack(m != null ? m : Material.STONE);
        ItemMeta mt = i.getItemMeta();
        if (mt != null) {
            mt.setDisplayName(c(n));
            List<String> l = new ArrayList<>();
            for (String s : lore) l.add(c(s)); 
            mt.setLore(l);
            if (glint) { mt.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 1, true); mt.addItemFlags(ItemFlag.HIDE_ENCHANTS); }
            i.setItemMeta(mt);
        }
        return i;
    }
}
