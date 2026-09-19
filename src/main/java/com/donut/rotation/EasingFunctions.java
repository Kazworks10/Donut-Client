package com.donut.rotation;

/**
 * Pure easing functions (no Minecraft imports) so they are unit-testable.
 * All functions map progress t in [0,1] to output in [0,1].
 */
public final class EasingFunctions {
    private EasingFunctions() {
    }

    public enum Mode {
        LINEAR, EASE_IN_CUBIC, EASE_OUT_CUBIC, EASE_IN_OUT_CUBIC,
        EASE_IN_QUART, EASE_OUT_QUART, EASE_IN_OUT_QUART,
        EASE_OUT_BACK, EASE_OUT_ELASTIC, EASE_OUT_BOUNCE,
        EASE_IN_OUT_SINE, SMOOTHSTEP;

        public float apply(float t) {
            return EasingFunctions.apply(this, t);
        }
    }

    public static float apply(Mode mode, float t) {
        float x = Math.clamp(t, 0f, 1f);
        return switch (mode) {
            case LINEAR -> x;
            case EASE_IN_CUBIC -> x * x * x;
            case EASE_OUT_CUBIC -> 1 - (1 - x) * (1 - x) * (1 - x);
            case EASE_IN_OUT_CUBIC -> x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
            case EASE_IN_QUART -> x * x * x * x;
            case EASE_OUT_QUART -> 1 - (1 - x) * (1 - x) * (1 - x) * (1 - x);
            case EASE_IN_OUT_QUART -> x < 0.5f ? 8 * x * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 4) / 2;
            case EASE_OUT_BACK -> {
                float c1 = 1.70158f;
                float c3 = c1 + 1;
                yield 1 + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
            }
            case EASE_OUT_ELASTIC -> {
                if (x == 0 || x == 1) yield x;
                float c4 = (float) (2 * Math.PI / 3);
                yield (float) (Math.pow(2, -10 * x) * Math.sin((x * 10 - 0.75) * c4) + 1);
            }
            case EASE_OUT_BOUNCE -> bounceOut(x);
            case EASE_IN_OUT_SINE -> (float) (-(Math.cos(Math.PI * x) - 1) / 2);
            case SMOOTHSTEP -> x * x * (3 - 2 * x);
        };
    }

    private static float bounceOut(float x) {
        float n1 = 7.5625f;
        float d1 = 2.75f;
        if (x < 1 / d1) return n1 * x * x;
        if (x < 2 / d1) {
            float t = x - 1.5f / d1;
            return n1 * t * t + 0.75f;
        }
        if (x < 2.5f / d1) {
            float t = x - 2.25f / d1;
            return n1 * t * t + 0.9375f;
        }
        float t = x - 2.625f / d1;
        return n1 * t * t + 0.984375f;
    }
}
