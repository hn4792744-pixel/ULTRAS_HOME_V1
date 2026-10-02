package me.uc.hussein.ultrashomes.gui;

import me.uc.hussein.ultrashomes.UltrasHomesPlugin;
import me.uc.hussein.ultrashomes.model.Home;
import me.uc.hussein.ultrashomes.model.PlayerHomeData;
import me.uc.hussein.ultrashomes.util.LocationUtil;
import me.uc.hussein.ultrashomes.util.Text;
import me.uc.hussein.ultrashomes.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/** Admin manage-one-home screen (delete / teleport / back) for a target player's home. */
public final class AdminHomeManageGui extends Menu {
    private final UUID target;
    private final PlayerHomeData data;
    private final int homeNumber;
    private final int returnPage;
    private final YamlConfiguration cfg;

    public AdminHomeManageGui(UltrasHomesPlugin plugin, Player admin, UUID target, PlayerHomeData data, int homeNumber, int returnPage) {
        super(plugin, admin);
        this.target = target;
        this.data = data;
        this.homeNumber = homeNumber;
        this.returnPage = returnPage;
        this.cfg = plugin.guiConfig().adminHomes();
    }

    private Home home() {
        return data.homes.get(homeNumber);
    }

    @Override
    protected Component title() {
        Home h = home();
        return Text.mm(Text.fill(cfg.getString("title", "{target}: {home}"),
                Text.map("target", data.name, "home", h == null ? "" : h.name())));
    }

    @Override
    protected int size() {
        return Math.max(9, cfg.getInt("rows", 3) * 9);
    }

    @Override
    protected void build() {
        Home h = home();
        if (h == null) {
            viewer.closeInventory();
            return;
        }
        var gui = plugin.gui();
        gui.fillBackground(inventory, cfg);
        Map<String, String> ph = Text.map(
                "home", h.name(), "target", data.name, "world", h.world(),
                "x", String.valueOf(Math.round(h.x())), "y", String.valueOf(Math.round(h.y())), "z", String.valueOf(Math.round(h.z())),
                "created_at", TimeUtil.humanize(System.currentTimeMillis() - h.createdAt()) + " ago");

        ConfigurationSection info = cfg.getConfigurationSection("info");
        if (info != null) set(info.getInt("slot", -1), gui.item(cfg, "info", ph));

        ConfigurationSection tp = cfg.getConfigurationSection("teleport");
        if (tp != null) {
            set(tp.getInt("slot", -1), gui.item(cfg, "teleport", ph), e -> {
                gui.playSound(viewer, gui.soundOf(cfg, "teleport"));
                startTeleport(h);
            });
        }

        ConfigurationSection del = cfg.getConfigurationSection("delete");
        if (del != null) {
            set(del.getInt("slot", -1), gui.item(cfg, "delete", ph), e -> {
                gui.playSound(viewer, gui.soundOf(cfg, "delete"));
                if (!del.getBoolean("confirm", true) || gui.confirmDelete(viewer, "admin:" + target + ":" + homeNumber)) {
                    doDelete(h);
                } else {
                    plugin.messages().send(viewer, "gui.delete-confirm");
                }
            });
        }

        ConfigurationSection back = cfg.getConfigurationSection("back");
        if (back != null) {
            set(back.getInt("slot", -1), gui.item(cfg, "back", null), e -> {
                gui.playSound(viewer, gui.soundOf(cfg, "back"));
                plugin.gui().openPlayerHomes(viewer, target, data, returnPage);
            });
        }
    }

    private void doDelete(Home h) {
        plugin.homes().removeHome(data, homeNumber);
        if (plugin.cfg().logAdminActions()) {
            plugin.getLogger().info("[ADMIN_DELETE] " + viewer.getName() + " deleted " + data.name + "'s home " + h.name());
        }
        plugin.messages().send(viewer, "admin.home-deleted", Text.map("target", data.name, "home", h.name()));
        plugin.gui().openPlayerHomes(viewer, target, data, returnPage);
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
        plugin.teleport().start(viewer, dest, data.name + ": " + h.name(), () -> LocationUtil.isValid(h.world(), h.x(), h.y(), h.z()));
    }
}
