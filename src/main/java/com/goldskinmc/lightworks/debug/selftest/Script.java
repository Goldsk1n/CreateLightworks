package com.goldskinmc.lightworks.debug.selftest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** One self-test scenario: steps run one after another, at most one waiting step per server tick. */
final class Script {
    /** Called once per tick with the ticks spent in this step so far; returns true when done. */
    interface Step {
        boolean tick(Stage stage, int ticks);
    }

    /** How long to wait for the client to take a shot before moving on without it. */
    private static final int SHOT_TIMEOUT_TICKS = 200;

    final String name;
    private final List<Step> steps = new ArrayList<>();
    private int index;
    private int ticks;

    Script(String name) {
        this.name = name;
    }

    Script run(Consumer<Stage> action) {
        steps.add((stage, t) -> {
            action.accept(stage);
            return true;
        });
        return this;
    }

    Script waitTicks(int count) {
        steps.add((stage, t) -> t >= count);
        return this;
    }

    /** Calls {@code action} on {@code count} consecutive ticks with the index 0..count-1. */
    Script repeat(int count, BiConsumer<Stage, Integer> action) {
        steps.add((stage, t) -> {
            if (t >= count) {
                return true;
            }
            action.accept(stage, t);
            return false;
        });
        return this;
    }

    Script waitUntil(Predicate<Stage> condition, int timeoutTicks, String what) {
        steps.add((stage, t) -> {
            if (condition.test(stage)) {
                return true;
            }
            if (t >= timeoutTicks) {
                stage.log("%s: gave up waiting for %s after %d ticks", name, what, t);
                return true;
            }
            return false;
        });
        return this;
    }

    /** Asks the client for a screenshot and waits until it is taken. */
    Script shot(String shotName) {
        int[] id = new int[1];
        steps.add((stage, t) -> {
            if (t == 0) {
                // A dedicated server's client runs in another process and never sees the request
                if (stage.level.getServer().isDedicatedServer()) {
                    return true;
                }
                id[0] = SelfTestBridge.requestShot(shotName);
                return false;
            }
            if (SelfTestBridge.shotDone >= id[0]) {
                return true;
            }
            if (t >= SHOT_TIMEOUT_TICKS) {
                stage.log("shot %s skipped: no client answered", shotName);
                SelfTestBridge.shotDone = id[0];
                return true;
            }
            return false;
        });
        return this;
    }

    /** Runs as many steps as finish this tick; returns true once every step is done. */
    boolean tick(Stage stage) {
        while (index < steps.size()) {
            if (!steps.get(index).tick(stage, ticks++)) {
                return false;
            }
            index++;
            ticks = 0;
        }
        return true;
    }
}
