package com.donut.module.modules;

import com.donut.module.Category;
import com.donut.module.Module;
import com.donut.module.settings.BooleanSetting;
import com.donut.schematic.HotbarManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Small quality-of-life module: throws filler blocks when the inventory fills
 * up, so long mining/building sessions don't stall. Pure client-side inventory
 * actions (drop via the vanilla stack drop path).
 */
public final class InventoryManager extends Module {
    private final BooleanSetting dropTrash = new BooleanSetting("Drop Trash When Full", true,
            "Throw cobblestone/dirt/gravel etc. when inventory is full");

    public InventoryManager() {
        super("InventoryManager", "Keeps your inventory from clogging up", Category.PLAYER);
        settings.add(dropTrash);
    }

    @Override
    protected void init() {
        listenTick();
    }

    @Override
    protected void onTick() {
        if (!dropTrash.get()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        if (player.getInventory().getEmptySlot() == -1) {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = player.getInventory().getStack(i);
                if (HotbarManager.isTrash(stack)) {
                    player.getInventory().selectedSlot = i;
                    player.dropSelectedItem(true);
                    return; // one item per tick
                }
            }
        }
    }
}
