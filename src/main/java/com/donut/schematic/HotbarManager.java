package com.donut.schematic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Keeps the right block item selected in the hotbar during placement.
 * Selection is pure hotbar scanning; refills swap a matching stack from the
 * main inventory into the current hotbar slot via a vanilla-style SWAP click
 * on the player screen handler (the same packets the inventory UI sends).
 */
public final class HotbarManager {
    private HotbarManager() {
    }

    /**
     * Ensures an item whose registry id matches {@code blockName} is selected.
     * Tries the hotbar first, then main-inventory refill. Returns false when
     * no matching item exists anywhere in the inventory.
     */
    public static boolean ensureSelected(MinecraftClient client, String blockName) {
        ClientPlayerEntity player = client.player;
        if (player == null) return false;

        int hotbarSlot = findInHotbar(player, blockName);
        if (hotbarSlot >= 0) {
            player.getInventory().selectedSlot = hotbarSlot;
            return true;
        }
        if (findInMain(player, blockName) >= 0) {
            return refillFromMain(client, player, blockName);
        }
        return false;
    }

    public static int findInHotbar(ClientPlayerEntity player, String blockName) {
        for (int i = 0; i < 9; i++) {
            if (matches(player.getInventory().getStack(i), blockName)) return i;
        }
        return -1;
    }

    public static int findInMain(ClientPlayerEntity player, String blockName) {
        for (int i = 9; i < 36; i++) {
            if (matches(player.getInventory().getStack(i), blockName)) return i;
        }
        return -1;
    }

    public static boolean matches(ItemStack stack, String blockName) {
        if (stack == null || stack.isEmpty()) return false;
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        return itemId.toString().equals(blockName);
    }

    /** SWAP-clicks the found main-inventory slot with the current hotbar slot. */
    private static boolean refillFromMain(MinecraftClient client, ClientPlayerEntity player, String blockName) {
        if (client.interactionManager == null) return false;
        int mainSlot = findInMain(player, blockName);
        if (mainSlot < 0) return false;
        int hotbarSlot = MathHelper.clamp(player.getInventory().selectedSlot, 0, 8);
        // PlayerScreenHandler: main inventory is slots 9..35, hotbar is 36..44.
        // clickSlot(syncId, slot, button, ...): button encodes the hotbar slot for SWAP.
        client.interactionManager.clickSlot(player.playerScreenHandler.syncId, mainSlot,
                hotbarSlot, SlotActionType.SWAP, player);
        return findInHotbar(player, blockName) >= 0;
    }

    /** Best-effort trash filter: true for stacks the builder may drop when full. */
    public static boolean isTrash(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item == Items.COBBLESTONE || item == Items.DIRT || item == Items.GRAVEL
                || item == Items.SAND || item == Items.NETHERRACK || item == Items.DIORITE
                || item == Items.GRANITE || item == Items.ANDESITE || item == Items.FLINT;
    }
}
