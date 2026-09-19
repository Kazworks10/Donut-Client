package com.donut.module.modules;

import com.donut.mixin.SimpleOptionAccessor;
import com.donut.module.Category;
import com.donut.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;

/**
 * Full brightness while enabled; the user's real gamma is restored on disable.
 * Uses an accessor mixin into {@link SimpleOption} so the gamma override works
 * even though vanilla clamps the slider — no reflection at runtime.
 */
public final class FullBright extends Module {
    public static final double GAMMA = 10.0;

    private double previousGamma = 1.0;
    private boolean applied;

    public FullBright() {
        super("FullBright", "Full brightness, restores your gamma on disable", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        MinecraftClient client = MinecraftClient.getInstance();
        previousGamma = client.options.getGamma().getValue();
        if (Math.abs(previousGamma - GAMMA) < 1e-4) previousGamma = 1.0;
        setGamma(GAMMA);
        applied = true;
    }

    @Override
    protected void onDisable() {
        if (applied) setGamma(previousGamma);
        applied = false;
    }

    private static void setGamma(double value) {
        SimpleOption<Double> opt = MinecraftClient.getInstance().options.getGamma();
        ((SimpleOptionAccessor) (Object) opt).donut$setValue(value);
    }
}
