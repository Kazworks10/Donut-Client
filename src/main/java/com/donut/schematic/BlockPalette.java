package com.donut.schematic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared string-to-id registry for block names. Each parsed schematic maps its
 * local palette indices through one of these; decoding to strings is lazy.
 */
public final class BlockPalette {
    private final Map<String, Integer> ids = new HashMap<>();
    private final List<String> names = new ArrayList<>();

    public BlockPalette() {
        id("minecraft:air"); // 0 is always air
    }

    public synchronized int id(String name) {
        return ids.computeIfAbsent(name, n -> {
            names.add(n);
            return names.size() - 1;
        });
    }

    public synchronized String name(int id) {
        return id >= 0 && id < names.size() ? names.get(id) : "minecraft:air";
    }

    public synchronized int size() {
        return names.size();
    }

    public boolean isAir(int id) {
        return id == 0 || "minecraft:air".equals(name(id));
    }
}
