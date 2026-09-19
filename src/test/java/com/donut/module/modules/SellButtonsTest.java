package com.donut.module.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the AutoSell button matcher and id normalization. */
class SellButtonsTest {
    @Test
    void markerInNameMatches() {
        assertTrue(SellButtons.isSellButton("Click to Sell Items", false, false));
    }

    @Test
    void markerInLoreMatches() {
        assertTrue(SellButtons.isSellButton(
                "Sell Menu Click an item below Click to sell items", false, false));
    }

    @Test
    void furnaceTokenNeverMatches() {
        // The sell-item token stacks also live in slot 53 sometimes; they are not the button
        assertFalse(SellButtons.isSellButton("Furnace", true, false));
    }

    @Test
    void emptyStackNeverMatches() {
        assertFalse(SellButtons.isSellButton("", false, true));
    }

    @Test
    void otherItemsDoNotMatch() {
        assertFalse(SellButtons.isSellButton("Diamond Sword A fine blade", false, false));
    }

    @Test
    void partialMarkerDoesNotMatch() {
        assertFalse(SellButtons.isSellButton("click to sell", false, false));
    }

    @Test
    void normalizationStripsPrefixAndCase() {
        assertEquals("furnace", SellButtons.normalizeItemId("  Minecraft:FURNACE "));
        assertEquals("diamond", SellButtons.normalizeItemId("diamond"));
        assertEquals("", SellButtons.normalizeItemId(null));
        assertEquals("", SellButtons.normalizeItemId("   "));
    }
}
