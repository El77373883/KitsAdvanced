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
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class Principal extends JavaPlugin implements Listener {

    private final Map<UUID, String> editandoKit = new HashMap<>();
    private final Map<UUID, String> esperandoNombre = new HashMap<>();
    private final Map<UUID, String> esperandoPrecio = new HashMap<>();
    private final Map<UUID, String> esperandoCooldown = new HashMap<>();

    @Override
    public void onEnable() {
        File f = new File(getDataFolder(), "kits");
        if (!f.exists()) f.mkdirs();
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("akits").setExecutor(new KitsCommand());
    }

    public String c(String m) { return ChatColor.translateAlternateColorCodes('&', m); }

    // --- PANEL DE AJUSTES (LAS 10 OPCIONES) ---
    public void abrirEditorKit(Player p, String id) {
        editandoKit.put(p.getUniqueId(), id);
        FileConfiguration cf = getKitConfig(id);
        Inventory inv = Bukkit.createInventory(null, 54, c("&8Ajustes: " + id));

        // 1. Ítems
        inv.setItem(10, createItem(Material.CHEST, "&61. Editar ítems", true, "&7Configura el contenido del kit."));
        
        // 2. Permiso
        boolean perm = cf.getBoolean("permiso_status");
        inv.setItem(11, createItem(Material.REDSTONE, "&e2. Permiso Requerido", perm, "&7¿Necesita permiso para usarse?", "", perm ? "&a&lESTADO: ACTIVADO" : "&c&lESTADO: DESACTIVADO"));
        
        // 3. Precio
        double precio = cf.getDouble("precio");
        inv.setItem(12, createItem(Material.SUNFLOWER, "&e3. Precio", precio > 0, "&7Costo actual: &6$" + precio, "", precio > 0 ? "&a&lESTADO: ACTIVADO" : "&c&lESTADO: GRATIS"));
        
        // 4. Cooldown
        long cool = cf.getLong("cooldown");
        inv.setItem(13, createItem(Material.CLOCK, "&b4. Cooldown", cool > 0, "&7Espera: &3" + cool + "s", "", cool > 0 ? "&a&lESTADO: ACTIVADO" : "&c&lESTADO: DESACTIVADO"));
        
        // 5. Kit Temporal
        boolean temp = cf.getBoolean("temporal");
        inv.setItem(14, createItem(Material.SOUL_SAND, "&35. Kit Temporal", temp, "&7¿Es de un solo uso?", "", temp ? "&a&lESTADO: ACTIVADO" : "&c&lESTADO: DESACTIVADO"));
        
        // 6. Icono
        inv.setItem(15, createItem(Material.ITEM_FRAME, "&d6. Cambiar Icono", false, "&7Usa el ítem de tu mano.", "", "&e&lCLICK PARA CAMBIAR"));
        
        // 7. Nombre Visual
        inv.setItem(16, createItem(Material.NAME_TAG, "&f7. Nombre Visual", true, "&7Actual: " + cf.getString("nombre_visual"), "", "&e&lCLICK PARA EDITAR"));
        
        // 8. Premium
        boolean prem = cf.getBoolean("premium");
        inv.setItem(19, createItem(Material.EMERALD, "&a8. PREMIUM", prem, "&7¿Es un kit para VIPs?", "", prem ? "&b&lESTADO: PREMIUM" : "&a&lESTADO: NORMAL"));
        
        // 9. Auto-Equipar
        boolean auto = cf.getBoolean("auto_equipar");
        inv.setItem(20, createItem(Material.CHAINMAIL_CHESTPLATE, "&b9. Auto-Equipar", auto, "&7¿Poner armadura sola?", "", auto ? "&a&lESTADO: ACTIVADO" : "&c&lESTADO: DESACTIVADO"));
        
        // 10. Eliminar
        inv.setItem(21, createItem(Material.BARRIER, "&c&l10. ELIMINAR KIT", false, "&4¡Borrado permanente!", "", "&4&lCLICK PARA BORRAR"));

        inv.setItem(49, createItem(Material.ARROW, "&c« Volver", false));
        p.openInventory(inv);
    }

    @EventHandler
    public void alClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.getCurrentItem() == null) return;
        Player p = (Player) e.getWhoClicked();
        String t = e.getView().getTitle();

        if (t.contains("Ajustes:")) {
            e.setCancelled(true);
            String id = editandoKit.get(p.getUniqueId());
            File f = new File(getDataFolder(), "kits/" + id + ".yml");
            FileConfiguration cf = YamlConfiguration.loadConfiguration(f);

            switch (e.getRawSlot()) {
                case 11: cf.set("permiso_status", !cf.getBoolean("permiso_status")); break;
                case 12: p.closeInventory(); esperandoPrecio.put(p.getUniqueId(), id); p.sendMessage(c("&6&lPRECIO &8» &fEscribe el precio en el chat:")); return;
                case 13: p.closeInventory(); esperandoCooldown.put(p.getUniqueId(), id); p.sendMessage(c("&b&lCOOLDOWN &8» &fEscribe los segundos:")); return;
                case 14: cf.set("temporal", !cf.getBoolean("temporal")); break;
                case 16: p.closeInventory(); esperandoNombre.put(p.getUniqueId(), id); p.sendMessage(c("&f&lNOMBRE &8» &fEscribe el nombre visual:")); return;
                case 19: cf.set("premium", !cf.getBoolean("premium")); break;
                case 20: cf.set("auto_equipar", !cf.getBoolean("auto_equipar")); break;
                case 21: f.delete(); p.closeInventory(); p.sendMessage(c("&cKit eliminado.")); return;
                case 49: p.closeInventory(); return;
            }
            saveKit(f, cf);
            abrirEditorKit(p, id);
        }
    }

    @EventHandler
    public void alChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (esperandoNombre.containsKey(p.getUniqueId())) {
            actualizarDato(p, e, "nombre_visual", esperandoNombre, false);
        } else if (esperandoPrecio.containsKey(p.getUniqueId())) {
            actualizarDato(p, e, "precio", esperandoPrecio, true);
        } else if (esperandoCooldown.containsKey(p.getUniqueId())) {
            actualizarDato(p, e, "cooldown", esperandoCooldown, true);
        }
    }

    private void actualizarDato(Player p, AsyncPlayerChatEvent e, String ruta, Map<UUID, String> mapa, boolean esNumero) {
        e.setCancelled(true);
        String id = mapa.remove(p.getUniqueId());
        File f = new File(getDataFolder(), "kits/" + id + ".yml");
        FileConfiguration cf = YamlConfiguration.loadConfiguration(f);
        
        if (esNumero) {
            try { cf.set(ruta, Double.parseDouble(e.getMessage())); } 
            catch (Exception ex) { p.sendMessage(c("&cError: Escribe un número válido.")); }
        } else {
            cf.set(ruta, e.getMessage());
        }
        
        saveKit(f, cf);
        p.sendMessage(c("&a&lACTUALIZADO &8» &f" + ruta + " guardado."));
        Bukkit.getScheduler().runTask(this, () -> abrirEditorKit(p, id));
    }

    public class KitsCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender s, Command cmd, String l, String[] a) {
            if (s instanceof Player) abrirEditorKit((Player) s, a.length > 0 ? a[0] : "kit1");
            return true;
        }
    }

    private void saveKit(File f, FileConfiguration c) { try { c.save(f); } catch (IOException e) {} }
    private FileConfiguration getKitConfig(String k) { return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "kits/" + k + ".yml")); }

    private ItemStack createItem(Material m, String n, boolean glint, String... lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta mt = i.getItemMeta();
        mt.setDisplayName(c(n));
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(c(s));
        mt.setLore(l);
        if (glint) mt.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, true);
        i.setItemMeta(mt);
        return i;
    }
}
