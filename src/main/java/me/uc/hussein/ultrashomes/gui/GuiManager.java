package me.uc.hussein.ultrashomes.gui;

import me.uc.hussein.ultrashomes.UltrasHomesPlugin;
import me.uc.hussein.ultrashomes.model.PlayerHomeData;
import me.uc.hussein.ultrashomes.util.ItemBuilder;
import me.uc.hussein.ultrashomes.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Entry point for every GUI screen plus shared config-driven item building. */
public final class GuiManager {
    private final UltrasHomesPlugin plugin;
    /** "uuid:context" -> expiry millis, used for the two-click delete confirmation in home_manage/admin_homes. */
    private final Map<String, Long> deleteConfirm = new ConcurrentHashMap<>();

    public GuiManager(UltrasHomesPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ item building
    public ItemStack item(ConfigurationSection sec, String path, Map<String, String> ph) {
        if (sec == null) return ItemBuilder.of(Material.BARRIER).name("<red>missing config").build();
        ConfigurationSection s = path.isEmpty() ? sec : sec.getConfigurationSection(path);
        if (s == null) return ItemBuilder.of(Material.BARRIER).name("<red>missing: " + path).build();
        Material m = Material.matchMaterial(s.getString("material", "STONE"));
        if (m == null || !m.isItem()) m = Material.STONE;
        List<String> lore = s.getStringList("lore");
        return ItemBuilder.of(m)
                .name(Text.fill(s.getString("name", ""), ph))
                .lore(lore.stream().map(l -> Text.fill(l, ph)).toList())
                .build();
    }

    public String soundOf(ConfigurationSection sec, String path) {
        if (sec == null) return null;
        ConfigurationSection s = path.isEmpty() ? sec : sec.getConfigurationSection(path);
        return s == null ? null : s.getString("sound");
    }

    /** Plays a GUI sound to this player only - skipped when they turned "Plugin Sounds" OFF. */
    public void playSound(Player p, String soundName) {
        if (soundName == null || soundName.isBlank()) return;
        plugin.settings().play(p, soundName, 0.7f, 1.1f);
    }

    /**
     * Homes shown per page: homes-per-page from the GUI yml, but never more than the bed/dye slots
     * that actually exist (so every home is always reachable on some page). No number is hardcoded.
     */
    public static int perPage(YamlConfiguration cfg) {
        int configured = Math.max(1, cfg.getInt("homes-per-page"));
        int slots = Math.min(cfg.getIntegerList("bed-slots").size(), cfg.getIntegerList("dye-slots").size());
        return slots <= 0 ? configured : Math.max(1, Math.min(configured, slots));
    }

    public void fillBackground(Inventory inv, ConfigurationSection root) {
        ConfigurationSection bg = root.getConfigurationSection("background");
        if (bg == null || !bg.getBoolean("enabled", true)) return;
        Material m = Material.matchMaterial(bg.getString("material", "BLACK_STAINED_GLASS_PANE"));
        if (m == null || !m.isItem()) m = Material.BLACK_STAINED_GLASS_PANE;
        List<Integer> excluded = bg.getIntegerList("exclude-slots");
        ItemStack pane = ItemBuilder.of(m).name(bg.getString("name", " ")).build();
        for (int i = 0; i < inv.getSize(); i++) {
            if (!excluded.contains(i)) inv.setItem(i, pane);
        }
    }

    // ------------------------------------------------------------------ delete confirmation
    /** @return true if this click confirms a pending delete (i.e. perform the delete now). */
    public boolean confirmDelete(Player p, String context) {
        String mapKey = p.getUniqueId() + ":" + context;
        Long until = deleteConfirm.get(mapKey);
        long now = System.currentTimeMillis();
        if (until != null && until > now) {
            deleteConfirm.remove(mapKey);
            return true;
        }
        deleteConfirm.put(mapKey, now + 5000L);
        return false;
    }

    // ------------------------------------------------------------------ navigation entry points
    public void openHomes(Player p, PlayerHomeData data, int page) {
        new HomesGui(plugin, p, data, page).open();
    }

    public void openHomeManage(Player p, PlayerHomeData data, int homeNumber, int returnPage) {
        new HomeManageGui(plugin, p, data, homeNumber, returnPage).open();
    }

    public void openPlayerHomes(Player admin, UUID target, PlayerHomeData data, int page) {
        new PlayerHomesGui(plugin, admin, target, data, page).open();
    }

    /** Settings menu of {@code data}'s owner. {@code back} runs when the Back button is clicked. */
    public void openSettings(Player viewer, PlayerHomeData data, Runnable back) {
        new SettingsGui(plugin, viewer, data, back).open();
    }

    public void openAdminHomeManage(Player admin, UUID target, PlayerHomeData data, int homeNumber, int returnPage) {
        new AdminHomeManageGui(plugin, admin, target, data, homeNumber, returnPage).open();
    }
}
