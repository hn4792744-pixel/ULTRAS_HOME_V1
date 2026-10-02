package me.uc.hussein.ultrashomes.gui;

import me.uc.hussein.ultrashomes.UltrasHomesPlugin;
import me.uc.hussein.ultrashomes.model.Home;
import me.uc.hussein.ultrashomes.model.PlayerHomeData;
import me.uc.hussein.ultrashomes.util.LocationUtil;
import me.uc.hussein.ultrashomes.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/** The player's own home list - GUI shown by /home, /homes and /sethome. */
public final class HomesGui extends Menu {
    private final PlayerHomeData data;
    private final int page;
    private final YamlConfiguration cfg;
    private final int limit;
    private final int perPage;
    private final int totalSlots;
    private final int totalPages;

    public HomesGui(UltrasHomesPlugin plugin, Player viewer, PlayerHomeData data, int page) {
        super(plugin, viewer);
        this.data = data;
        this.cfg = plugin.guiConfig().homes();
        this.limit = plugin.limits().effectiveLimit(viewer, data);
        int highest = data.homes.keySet().stream().max(Integer::compareTo).orElse(0);
        this.totalSlots = Math.min(plugin.cfg().maximumHomes(), Math.max(limit, highest));
        this.perPage = GuiManager.perPage(cfg);
        this.totalPages = Math.max(1, (int) Math.ceil(totalSlots / (double) perPage));
        this.page = Math.max(0, Math.min(page, totalPages - 1));
    }

    @Override
    protected Component title() {
        return Text.mm(Text.fill(cfg.getString("title", "ULTRAS HOMES"), Text.map("page", String.valueOf(page + 1))));
    }

    @Override
    protected int size() {
        return Math.max(9, cfg.getInt("rows", 4) * 9);
    }

    @Override
    protected String openSoundKey() {
        return cfg.getString("open-sound");
    }

    @Override
    protected void build() {
        var gui = plugin.gui();
        gui.fillBackground(inventory, cfg);
        List<Integer> bedSlots = cfg.getIntegerList("bed-slots");
        List<Integer> dyeSlots = cfg.getIntegerList("dye-slots");
        int from = page * perPage + 1;

        for (int i = 0; i < perPage && i < bedSlots.size() && i < dyeSlots.size(); i++) {
            int number = from + i;
            if (number > totalSlots) continue;
            renderHome(number, bedSlots.get(i), dyeSlots.get(i));
        }

        // homes used out of the limit - always the live numbers, never hardcoded
        ConfigurationSection info = cfg.getConfigurationSection("info");
        if (info != null) {
            set(info.getInt("slot", -1), gui.item(cfg, "info", Text.map(
                    "current", String.valueOf(data.homes.size()), "limit", String.valueOf(limit),
                    "page", String.valueOf(page + 1), "pages", String.valueOf(totalPages))));
        }

        // Settings (top right) -> personal settings GUI of THIS viewer only
        ConfigurationSection sb = cfg.getConfigurationSection("settings-button");
        if (sb != null && viewer.hasPermission("ultras.homes.setting")) {
            set(sb.getInt("slot", -1), gui.item(cfg, "settings-button", null), e -> {
                gui.playSound(viewer, gui.soundOf(cfg, "settings-button"));
                gui.openSettings(viewer, data, () -> gui.openHomes(viewer, data, page));
            });
        }

        // Previous / Next only. This menu intentionally has no Close button.
        ConfigurationSection nav = cfg.getConfigurationSection("navigation");
        if (nav != null) {
            if (page > 0) {
                set(nav.getInt("previous.slot", -1), gui.item(nav, "previous", Text.map("page", String.valueOf(page))), e -> {
                    gui.playSound(viewer, gui.soundOf(nav, "previous"));
                    gui.openHomes(viewer, data, page - 1);
                });
            }
            if (page < totalPages - 1) {
                set(nav.getInt("next.slot", -1), gui.item(nav, "next", Text.map("page", String.valueOf(page + 2))), e -> {
                    gui.playSound(viewer, gui.soundOf(nav, "next"));
                    gui.openHomes(viewer, data, page + 1);
                });
            }
        }
    }

    private void renderHome(int number, int bedSlot, int dyeSlot) {
        var gui = plugin.gui();
        Home home = data.homes.get(number);
        String homeName = plugin.cfg().homeName(number);
        Map<String, String> ph = Text.map("home_number", String.valueOf(number), "home", homeName);

        if (home != null) {
            ph = Text.map("home_number", String.valueOf(number), "home", home.name(),
                    "world", home.world(), "x", String.valueOf(Math.round(home.x())),
                    "y", String.valueOf(Math.round(home.y())), "z", String.valueOf(Math.round(home.z())));
            ConfigurationSection sec = cfg.getConfigurationSection("home.saved");
            final Map<String, String> fph = ph;
            set(bedSlot, gui.item(sec, "bed", fph), e -> {
                gui.playSound(viewer, gui.soundOf(sec, "bed"));
                startHomeTeleport(home);
            });
            set(dyeSlot, gui.item(sec, "dye", fph), e -> {
                gui.playSound(viewer, gui.soundOf(sec, "dye"));
                plugin.gui().openHomeManage(viewer, data, number, page);
            });
        } else if (number <= limit) {
            ConfigurationSection sec = cfg.getConfigurationSection("home.available");
            final Map<String, String> fph = ph;
            set(bedSlot, gui.item(sec, "bed", fph));
            set(dyeSlot, gui.item(sec, "dye", fph), e -> {
                gui.playSound(viewer, gui.soundOf(sec, "dye"));
                saveHome(number, homeName);
            });
        } else {
            ConfigurationSection sec = cfg.getConfigurationSection("home.locked");
            Map<String, String> lph = Text.map("home_number", String.valueOf(number), "required_limit", String.valueOf(number));
            set(bedSlot, gui.item(sec, "bed", lph));
            set(dyeSlot, gui.item(sec, "dye", lph), e -> gui.playSound(viewer, gui.soundOf(sec, "dye")));
        }
    }

    private void saveHome(int number, String name) {
        Location loc = viewer.getLocation();
        Home home = new Home(number, name, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(), System.currentTimeMillis(), System.currentTimeMillis());
        plugin.homes().setHome(data, number, home);
        if (plugin.cfg().logHomeCreate()) {
            plugin.getLogger().info("[HOME_CREATE] " + viewer.getName() + " saved " + name + " at " + loc.getWorld().getName()
                    + " " + Math.round(loc.getX()) + "," + Math.round(loc.getY()) + "," + Math.round(loc.getZ()));
        }
        plugin.messages().send(viewer, "home.saved", Text.map("home", name,
                "current", String.valueOf(data.homes.size()), "limit", String.valueOf(limit)));
        plugin.gui().playSound(viewer, cfg.getString("save-success-sound"));
        refresh();
    }

    private void startHomeTeleport(Home home) {
        if (plugin.cfg().worldBlacklisted(home.world())) {
            plugin.messages().send(viewer, "teleport.world-blocked");
            return;
        }
        viewer.closeInventory();
        Location dest = LocationUtil.build(home.world(), home.x(), home.y(), home.z(), home.yaw(), home.pitch());
        if (dest == null) {
            plugin.messages().send(viewer, "teleport.invalid-location");
            return;
        }
        if (plugin.cfg().logHomeTeleport()) {
            plugin.getLogger().info("[HOME_TELEPORT] " + viewer.getName() + " -> " + home.name());
        }
        plugin.teleport().start(viewer, dest, home.name(),
                () -> LocationUtil.isValid(home.world(), home.x(), home.y(), home.z()));
    }
}
