package me.uc.hussein.ultrashomes.gui;

import me.uc.hussein.ultrashomes.UltrasHomesPlugin;
import me.uc.hussein.ultrashomes.model.Home;
import me.uc.hussein.ultrashomes.model.PlayerHomeData;
import me.uc.hussein.ultrashomes.util.ItemBuilder;
import me.uc.hussein.ultrashomes.util.LocationUtil;
import me.uc.hussein.ultrashomes.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin view of a target player's homes: /home admin home &lt;player&gt; (also /home_admin tp &lt;player&gt;).
 * The admin can teleport, open the manage screen (teleport / delete), save a home for the player in an
 * empty slot (at the admin's own location), change the player's slot limit and open their settings.
 */
public final class PlayerHomesGui extends Menu {
    private final UUID target;
    private final PlayerHomeData data;
    private final YamlConfiguration cfg;
    private final int perPage;
    private int page;
    private int limit;
    private int totalSlots;
    private int totalPages;

    public PlayerHomesGui(UltrasHomesPlugin plugin, Player admin, UUID target, PlayerHomeData data, int page) {
        super(plugin, admin);
        this.target = target;
        this.data = data;
        this.cfg = plugin.guiConfig().playerHomes();
        this.perPage = GuiManager.perPage(cfg);
        recompute(page);
    }

    /** Limit and page counts can change while the menu is open (admin adds/removes a slot), so they are recomputed on every render. */
    private void recompute(int wantedPage) {
        if (data.limitOverride != null) {
            this.limit = Math.min(data.limitOverride, plugin.cfg().maximumHomes());
        } else {
            Player onlineTarget = Bukkit.getPlayer(target);
            this.limit = onlineTarget != null ? plugin.limits().permissionLimit(onlineTarget) : plugin.cfg().defaultLimit();
        }
        int highest = data.homes.keySet().stream().max(Integer::compareTo).orElse(0);
        this.totalSlots = Math.min(plugin.cfg().maximumHomes(), Math.max(limit, highest));
        this.totalPages = Math.max(1, (int) Math.ceil(totalSlots / (double) perPage));
        this.page = Math.max(0, Math.min(wantedPage, totalPages - 1));
    }

    @Override
    protected Component title() {
        return Text.mm(Text.fill(cfg.getString("title", "ULTRAS HOMES"),
                Text.map("target", data.name, "page", String.valueOf(page + 1))));
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
        recompute(page);
        var gui = plugin.gui();
        gui.fillBackground(inventory, cfg);
        Map<String, String> common = Text.map("target", data.name,
                "current", String.valueOf(data.homes.size()), "limit", String.valueOf(limit));

        ConfigurationSection headSec = cfg.getConfigurationSection("player-head");
        if (headSec != null && plugin.cfg().playerInfoEnabled()) {
            Player online = Bukkit.getPlayer(target);
            Map<String, String> hph = Text.map(
                    "target", data.name,
                    "online_status", plugin.messages().plain(viewer, online != null ? "gui.status-online" : "gui.status-offline"),
                    "world", plugin.cfg().playerInfoShowWorld() && online != null ? online.getWorld().getName() : "-",
                    "x", plugin.cfg().playerInfoShowCoordinates() && online != null ? String.valueOf(Math.round(online.getLocation().getX())) : "-",
                    "y", plugin.cfg().playerInfoShowCoordinates() && online != null ? String.valueOf(Math.round(online.getLocation().getY())) : "-",
                    "z", plugin.cfg().playerInfoShowCoordinates() && online != null ? String.valueOf(Math.round(online.getLocation().getZ())) : "-",
                    "current", String.valueOf(data.homes.size()), "limit", String.valueOf(limit));
            var headItem = ItemBuilder.head(Bukkit.getOfflinePlayer(target))
                    .name(Text.fill(headSec.getString("name", "{target}"), hph))
                    .lore(headSec.getStringList("lore").stream().map(l -> Text.fill(l, hph)).toList())
                    .build();
            set(headSec.getInt("slot", -1), headItem);
        }

        // Settings (top right) -> the target's settings GUI, opened directly (no text command)
        ConfigurationSection sb = cfg.getConfigurationSection("settings-button");
        if (sb != null && allowed("setting")) {
            set(sb.getInt("slot", -1), gui.item(cfg, "settings-button", common), e -> {
                gui.playSound(viewer, gui.soundOf(cfg, "settings-button"));
                gui.openSettings(viewer, data, () -> gui.openPlayerHomes(viewer, target, data, page));
            });
        }

        // slot management: +1 / -1 on the player's limit
        ConfigurationSection add = cfg.getConfigurationSection("limit-add");
        if (add != null) {
            set(add.getInt("slot", -1), gui.item(cfg, "limit-add", common), e -> {
                gui.playSound(viewer, gui.soundOf(cfg, "limit-add"));
                changeLimit(1);
            });
        }
        ConfigurationSection rem = cfg.getConfigurationSection("limit-remove");
        if (rem != null) {
            set(rem.getInt("slot", -1), gui.item(cfg, "limit-remove", common), e -> {
                gui.playSound(viewer, gui.soundOf(cfg, "limit-remove"));
                changeLimit(-1);
            });
        }

        List<Integer> bedSlots = cfg.getIntegerList("bed-slots");
        List<Integer> dyeSlots = cfg.getIntegerList("dye-slots");
        int from = page * perPage + 1;
        for (int i = 0; i < perPage && i < bedSlots.size() && i < dyeSlots.size(); i++) {
            int number = from + i;
            if (number > totalSlots) continue;
            renderHome(number, bedSlots.get(i), dyeSlots.get(i));
        }

        ConfigurationSection nav = cfg.getConfigurationSection("navigation");
        if (nav != null) {
            if (page > 0) {
                set(nav.getInt("previous.slot", -1), gui.item(nav, "previous", Text.map("page", String.valueOf(page))), e -> {
                    gui.playSound(viewer, gui.soundOf(nav, "previous"));
                    plugin.gui().openPlayerHomes(viewer, target, data, page - 1);
                });
            }
            if (page < totalPages - 1) {
                set(nav.getInt("next.slot", -1), gui.item(nav, "next", Text.map("page", String.valueOf(page + 2))), e -> {
                    gui.playSound(viewer, gui.soundOf(nav, "next"));
                    plugin.gui().openPlayerHomes(viewer, target, data, page + 1);
                });
            }
            set(nav.getInt("close.slot", -1), gui.item(nav, "close", null), e -> viewer.closeInventory());
        }
    }

    private boolean allowed(String sub) {
        return viewer.hasPermission("ultras.homes.admin") || viewer.hasPermission("ultras.homes.admin." + sub);
    }

    private void renderHome(int number, int bedSlot, int dyeSlot) {
        var gui = plugin.gui();
        Home home = data.homes.get(number);
        String homeName = plugin.cfg().homeName(number);

        if (home != null) {
            Map<String, String> ph = Text.map("home_number", String.valueOf(number), "home", home.name(), "target", data.name,
                    "world", home.world(), "x", String.valueOf(Math.round(home.x())),
                    "y", String.valueOf(Math.round(home.y())), "z", String.valueOf(Math.round(home.z())));
            ConfigurationSection sec = cfg.getConfigurationSection("home.saved");
            set(bedSlot, gui.item(sec, "bed", ph), e -> {
                gui.playSound(viewer, gui.soundOf(sec, "bed"));
                startTeleport(home);
            });
            set(dyeSlot, gui.item(sec, "dye", ph), e -> {
                gui.playSound(viewer, gui.soundOf(sec, "dye"));
                plugin.gui().openAdminHomeManage(viewer, target, data, number, page);
            });
        } else if (number <= limit) {
            Map<String, String> ph = Text.map("home_number", String.valueOf(number), "target", data.name, "home", homeName);
            ConfigurationSection sec = cfg.getConfigurationSection("home.available");
            set(bedSlot, gui.item(sec, "bed", ph));
            set(dyeSlot, gui.item(sec, "dye", ph), e -> {
                gui.playSound(viewer, gui.soundOf(sec, "dye"));
                saveHomeForTarget(number, homeName);
            });
        } else {
            Map<String, String> ph = Text.map("home_number", String.valueOf(number), "required_limit", String.valueOf(number));
            ConfigurationSection sec = cfg.getConfigurationSection("home.locked");
            set(bedSlot, gui.item(sec, "bed", ph));
            set(dyeSlot, gui.item(sec, "dye", ph));
        }
    }

    /**
     * Homes are numbered slots with the standard name (homes.name-format), exactly like the player's own
     * GUI, so the admin saves the home in the clicked empty slot at the admin's current location.
     */
    private void saveHomeForTarget(int number, String name) {
        if (!allowed("home")) {
            plugin.messages().send(viewer, "general.no-permission");
            return;
        }
        Location loc = viewer.getLocation();
        String world = loc.getWorld().getName();
        if (plugin.cfg().worldBlacklisted(world) && !viewer.hasPermission("ultras.homes.bypass.world")) {
            plugin.messages().send(viewer, "general.world-disabled");
            return;
        }
        Home home = new Home(number, name, world, loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(), System.currentTimeMillis(), System.currentTimeMillis());
        plugin.homes().setHome(data, number, home);
        if (plugin.cfg().logAdminActions()) {
            plugin.getLogger().info("[ADMIN_HOME_CREATE] " + viewer.getName() + " saved " + data.name + "'s " + name + " at " + world
                    + " " + Math.round(loc.getX()) + "," + Math.round(loc.getY()) + "," + Math.round(loc.getZ()));
        }
        plugin.messages().send(viewer, "admin.home-saved", Text.map("target", data.name, "home", name,
                "current", String.valueOf(data.homes.size()), "limit", String.valueOf(limit)));
        plugin.gui().playSound(viewer, cfg.getString("save-success-sound"));
        refresh();
    }

    /** +1 needs ultras.homes.admin.add, -1 needs ultras.homes.admin.set; honours admin.set-limit-behavior like /home_admin set. */
    private void changeLimit(int delta) {
        if (!allowed(delta > 0 ? "add" : "set")) {
            plugin.messages().send(viewer, "general.no-permission");
            return;
        }
        int old = limit;
        int result = plugin.limits().cap(old + delta);
        if (result == old) return;
        data.limitOverride = result;
        plugin.homes().markDirty(data);
        int removed = 0;
        if (delta < 0 && plugin.cfg().deleteExcessOnSet()) {
            removed = plugin.homes().deleteAboveLimit(data, result);
        }
        if (plugin.cfg().logAdminActions()) {
            plugin.getLogger().info("[ADMIN_LIMIT_" + (delta > 0 ? "ADD" : "SET") + "] " + viewer.getName() + " -> " + data.name
                    + " old=" + old + " new=" + result + " (gui)");
        }
        plugin.messages().send(viewer, "admin.limit-changed", Text.map("target", data.name,
                "old", String.valueOf(old), "new", String.valueOf(result)));
        if (removed > 0) {
            plugin.messages().send(viewer, "admin.exceeds-deleted", Text.map("target", data.name, "count", String.valueOf(removed)));
        }
        refresh();
    }

    private void startTeleport(Home h) {
        if (plugin.cfg().worldBlacklisted(h.world()) && !viewer.hasPermission("ultras.homes.bypass.world")) {
            plugin.messages().send(viewer, "teleport.world-blocked");
            return;
        }
        viewer.closeInventory();
        Location dest = LocationUtil.build(h.world(), h.x(), h.y(), h.z(), h.yaw(), h.pitch());
        if (dest == null) {
            plugin.messages().send(viewer, "teleport.invalid-location");
            return;
        }
        if (plugin.cfg().logAdminActions()) {
            plugin.getLogger().info("[ADMIN_TELEPORT] " + viewer.getName() + " -> " + data.name + ":" + h.name());
        }
        plugin.teleport().start(viewer, dest, data.name + ": " + h.name(),
                () -> LocationUtil.isValid(h.world(), h.x(), h.y(), h.z()));
    }
}
