package me.uc.hussein.ultrashomes.gui;

import me.uc.hussein.ultrashomes.UltrasHomesPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Base of every ULTRAS_HOMES GUI screen. The holder identity is what GuiListener uses to route clicks. */
public abstract class Menu implements InventoryHolder {
    protected final UltrasHomesPlugin plugin;
    protected final Player viewer;
    protected Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
    private long lastActionAt;

    protected Menu(UltrasHomesPlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    protected abstract Component title();

    protected abstract int size();

    protected abstract void build();

    protected String openSoundKey() {
        return null;
    }

    public Player viewer() {
        return viewer;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, size(), title());
        render();
        viewer.openInventory(inventory);
        String s = openSoundKey();
        if (s != null) plugin.gui().playSound(viewer, s);
    }

    public void refresh() {
        if (inventory != null) render();
    }

    private void render() {
        actions.clear();
        inventory.clear();
        build();
    }

    protected void set(int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, item);
    }

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        set(slot, item);
        if (slot >= 0 && slot < inventory.getSize()) actions.put(slot, action);
    }

    /** Debounces rapid double clicks (spec #60: no duplicate teleport/save/delete from fast clicking). */
    public void click(InventoryClickEvent e) {
        long now = System.currentTimeMillis();
        if (now - lastActionAt < 250) return;
        Consumer<InventoryClickEvent> a = actions.get(e.getRawSlot());
        if (a != null) {
            lastActionAt = now;
            a.accept(e);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
