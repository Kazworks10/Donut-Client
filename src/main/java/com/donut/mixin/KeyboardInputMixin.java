package com.donut.mixin;

import com.donut.pathfinding.MovementInputOverride;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After vanilla computes input from the keyboard, modules driving movement
 * (path executor, builder walker) replace it with their own values.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void donut$applyInputOverride(CallbackInfo ci) {
        if (MovementInputOverride.isActive()) {
            KeyboardInput self = (KeyboardInput) (Object) this;
            self.movementForward = MovementInputOverride.forward();
            self.movementSideways = MovementInputOverride.strafe();
            self.jumping = MovementInputOverride.jump();
            self.sneaking = MovementInputOverride.sneak();
        }
    }
}
