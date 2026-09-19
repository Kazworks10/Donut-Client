package com.donut.module.modules;

import com.donut.module.Category;
import com.donut.module.Module;
import com.donut.module.settings.NumberSetting;
import com.donut.module.settings.StringSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Locale;

/**
 * Donut SMP auto-sell: runs the server's "/sell" menu loop — open, shift-click
 * the sell-item stacks in, click the sell button, wait for the sale, repeat.
 * Faithful port of the reference implementation (5-phase state machine with
 * sync-id/cursor/deadline validation); slot clicks go through the vanilla
 * interaction manager. The sell item defaults to the server's furnace token.
 */
public final class AutoSell extends Module {
    private enum Phase { OPEN, WAIT_MENU, TRANSFER, WAIT_TRANSFER, WAIT_SALE }

    private final NumberSetting loopDelay = new NumberSetting("Loop Delay", 10, 1, 100, 1, 0,
            "Ticks between sell-loop steps");
    private final StringSetting sellItem = new StringSetting("Sell Item", "furnace",
            "Item id to sell (no minecraft: prefix needed)");

    private Phase phase = Phase.OPEN;
    private Item item = Items.FURNACE;
    private int ticks;
    private int nextAction;
    private int deadline;
    private int syncId = -1;
    private int stagedBefore;
    private int expectedTransfer;
    private boolean reusingMenu;

    public AutoSell() {
        super("AutoSell", "Runs the Donut SMP /sell menu loop for you", Category.DONUT_SMP);
        settings.add(loopDelay);
        settings.add(sellItem);
        sellItem.onChanged(v -> resolveItem());
    }

    @Override
    protected void init() {
        listenTick();
        resolveItem();
    }

    private void resolveItem() {
        String raw = SellButtons.normalizeItemId(sellItem.get());
        Item resolved = Registries.ITEM.get(Identifier.of("minecraft", raw));
        if (resolved == Items.AIR) {
            item = null;
            return;
        }
        item = resolved;
    }

    @Override
    protected void onEnable() {
        phase = Phase.OPEN;
        ticks = nextAction = 0;
        deadline = 200;
        syncId = -1;
        reusingMenu = false;
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.getNetworkHandler() == null) {
            restartLoop();
            return;
        }
        if (item == null) return;

        ticks++;
        HandledScreen<?> screen = client.currentScreen instanceof HandledScreen<?> hs ? hs : null;
        GenericContainerScreenHandler menu = sellMenu(screen);

        switch (phase) {
            case OPEN -> {
                if (menu != null) {
                    phase = Phase.WAIT_MENU;
                    deadline = ticks + 200;
                } else if (screen == null && ticks >= nextAction && holdsSellItem(client)) {
                    client.getNetworkHandler().sendChatCommand("sell");
                    phase = Phase.WAIT_MENU;
                    deadline = ticks + 200;
                }
            }
            case WAIT_MENU -> {
                if (menu == null) {
                    if (ticks > deadline) retry();
                } else if (ticks >= nextAction) {
                    if (!isSellButton(menu.getSlot(53).getStack())) {
                        if (ticks > deadline) retry();
                        return;
                    }
                    syncId = menu.syncId;
                    phase = Phase.TRANSFER;
                    nextAction = ticks + 1;
                }
            }
            case TRANSFER -> {
                if (menu == null || menu.syncId != syncId || !client.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    retry();
                    return;
                }
                if (ticks < nextAction) return;

                int staged = stagedCount(menu);
                if (staged < 0) { // non-sell-item stack present in the container
                    retry();
                    return;
                }

                int batch = 0;
                for (int i = 0; i < menu.slots.size(); i++) {
                    Slot slot = menu.slots.get(i);
                    if (slot.inventory == client.player.getInventory() && slot.getStack().isOf(item)) {
                        batch += slot.getStack().getCount();
                    }
                }

                if (batch > 0) {
                    stagedBefore = staged;
                    expectedTransfer = batch;
                    phase = Phase.WAIT_TRANSFER;
                    deadline = ticks + 100;
                    nextAction = ticks + 1;
                    for (int i = 0; i < menu.slots.size(); i++) {
                        Slot slot = menu.slots.get(i);
                        if (slot.inventory == client.player.getInventory() && slot.getStack().isOf(item)) {
                            click(client, menu, slot.id, SlotActionType.QUICK_MOVE);
                        }
                    }
                } else if (staged == 0) {
                    nextAction = ticks + 1; // nothing to sell; idle-wait for items
                } else {
                    phase = Phase.WAIT_SALE;
                    deadline = ticks + 200;
                    nextAction = ticks + 1;
                    click(client, menu, menu.getSlot(53).id, SlotActionType.PICKUP);
                }
            }
            case WAIT_TRANSFER -> {
                if (menu == null || menu.syncId != syncId) {
                    retry();
                    return;
                }
                if (ticks >= nextAction) {
                    int staged = stagedCount(menu);
                    if (staged < 0 || staged < stagedBefore + expectedTransfer) {
                        if (ticks > deadline) retry();
                        return;
                    }
                    phase = Phase.TRANSFER;
                    nextAction = ticks + 1;
                }
            }
            case WAIT_SALE -> {
                if (menu == null) {
                    if (ticks >= deadline) restartLoop();
                    return;
                }
                if (!client.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    retry();
                    return;
                }
                if (ticks >= nextAction) {
                    if (stagedCount(menu) == 0) {
                        restartLoop();          // sale complete; reopen
                        phase = Phase.WAIT_MENU;
                        reusingMenu = true;
                        deadline = ticks + 200;
                    } else if (ticks > deadline) {
                        retry();
                    }
                }
            }
        }
    }

    /** The open 6-row container titled "Sell", or null. */
    private static GenericContainerScreenHandler sellMenu(HandledScreen<?> screen) {
        if (screen == null) return null;
        if (!(screen.getScreenHandler() instanceof GenericContainerScreenHandler h) || h.getRows() != 6) return null;
        return screen.getTitle().getString().strip().equalsIgnoreCase("Sell") ? h : null;
    }

    private boolean holdsSellItem(MinecraftClient client) {
        for (int i = 0; i < 36; i++) {
            if (client.player.getInventory().getStack(i).isOf(item)) return true;
        }
        return false;
    }

    /** Total sell-item count staged in container slots 0..52, or -1 if another item sits there. */
    private int stagedCount(GenericContainerScreenHandler menu) {
        int total = 0;
        for (int i = 0; i < 53; i++) {
            ItemStack stack = menu.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            if (item == null || !stack.isOf(item)) return -1;
            total += stack.getCount();
        }
        return total;
    }

    private boolean isSellItemStack(ItemStack stack) {
        return item != null && stack.isOf(item);
    }

    /** Slot 53 name+lore contains the sell button marker text. */
    private boolean isSellButton(ItemStack stack) {
        String label = stack.getName().getString();
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text line : lore.lines()) label += " " + line.getString();
        }
        return SellButtons.isSellButton(label, item != null && stack.isOf(item), stack.isEmpty());
    }

    private void click(MinecraftClient client, GenericContainerScreenHandler menu, int slotId, SlotActionType type) {
        if (client.interactionManager != null) {
            client.interactionManager.clickSlot(menu.syncId, slotId, 0, type, client.player);
        }
    }

    private void restartLoop() {
        phase = Phase.OPEN;
        reusingMenu = false;
        syncId = -1;
        expectedTransfer = stagedBefore = 0;
        nextAction = ticks + loopDelay.intValue();
    }

    /** Cooldown back to the loop start, reusing an already-open sell menu. */
    private void retry() {
        restartLoop();
        MinecraftClient client = MinecraftClient.getInstance();
        if (sellMenu(client.currentScreen instanceof HandledScreen<?> hs ? hs : null) != null) {
            phase = Phase.WAIT_MENU;
            reusingMenu = true;
        }
        nextAction = ticks + Math.max(20, loopDelay.intValue());
        deadline = nextAction + 200;
    }

    public String statusLine() {
        return switch (phase) {
            case OPEN -> isEnabled() ? "waiting to open /sell" : null;
            case WAIT_MENU -> "waiting for sell menu";
            case TRANSFER -> "staging items";
            case WAIT_TRANSFER -> "transferring...";
            case WAIT_SALE -> "selling...";
        };
    }
}
