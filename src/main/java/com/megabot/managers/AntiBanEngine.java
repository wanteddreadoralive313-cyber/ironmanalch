package com.megabot.managers;

import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.camera.Camera;
import org.dreambot.api.methods.input.Mouse;
import org.dreambot.api.methods.tabs.Tab;
import org.dreambot.api.methods.tabs.Tabs;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.utilities.Sleep;
import java.util.EnumSet;

public class AntiBanEngine {
    private final BehaviorProfile profile;
    private long lastAction = System.currentTimeMillis();
    private long fatigue = 0;

    public AntiBanEngine(BehaviorProfile profile) {
        this.profile = profile;
    }

    public void updateFatigue() {
        long now = System.currentTimeMillis();
        long delta = now - lastAction;
        fatigue = Math.max(0, fatigue - delta / 2);
        lastAction = now;
    }

    public void tryRandomCamera() {
        if (Calculations.random(0, 100) < profile.getCameraMovementChance()) {
            Camera.rotateTo(Calculations.random(0, 360), Calculations.random(30, 90));
            humanSleep(profile.randomReaction());
        }
    }

    public void checkForBreaks() {
        if (fatigue > profile.getFatigueThreshold()) {
            Logger.log("Taking micro break for anti-ban.");
            Mouse.moveMouseOutsideScreen();
            humanSleep(Calculations.random(profile.getBreakMinMs(), profile.getBreakMaxMs()));
            fatigue = 0;
        }
    }

    public void performRandomAntiBan() {
        int roll = Calculations.random(0, 100);
        EnumSet<AntiBanAction> actions = EnumSet.allOf(AntiBanAction.class);
        for (AntiBanAction action : actions) {
            if (roll < action.chance && profile.isActionEnabled(action)) {
                action.perform();
                break;
            }
        }
    }

    public void microDelay() {
        Sleep.sleep(profile.randomReaction());
    }

    public int getHumanizedSleep(int min, int max) {
        return Calculations.random(min, max) + profile.randomJitter();
    }

    public void humanSleep(int duration) {
        Sleep.sleep(duration + profile.randomJitter());
    }

    public BehaviorProfile getProfile() {
        return profile;
    }

    public enum AntiBanAction {
        CHECK_XP(15) {
            @Override
            void perform() {
                Tabs.openWithMouse(Tab.STATS);
                Sleep.sleep(Calculations.random(500, 900));
                Tabs.openWithMouse(Tab.INVENTORY);
            }
        },
        RANDOM_MOUSE_OFF(10) {
            @Override
            void perform() {
                Mouse.moveMouseOutsideScreen();
                Sleep.sleep(Calculations.random(800, 1400));
            }
        },
        MOVE_CAMERA(20) {
            @Override
            void perform() {
                Camera.rotateTo(Calculations.random(0, 360), Calculations.random(30, 90));
                Sleep.sleep(Calculations.random(400, 900));
            }
        };

        private final int chance;

        AntiBanAction(int chance) {
            this.chance = chance;
        }

        abstract void perform();
    }

    public static class BehaviorProfile {
        private final int reactionMin;
        private final int reactionMax;
        private final int fatigueThreshold;
        private final int breakMinMs;
        private final int breakMaxMs;
        private final int cameraMovementChance;
        private final EnumSet<AntiBanAction> enabledActions;

        public BehaviorProfile(int reactionMin, int reactionMax, int fatigueThreshold, int breakMinMs, int breakMaxMs,
                               int cameraMovementChance, EnumSet<AntiBanAction> enabledActions) {
            this.reactionMin = reactionMin;
            this.reactionMax = reactionMax;
            this.fatigueThreshold = fatigueThreshold;
            this.breakMinMs = breakMinMs;
            this.breakMaxMs = breakMaxMs;
            this.cameraMovementChance = cameraMovementChance;
            this.enabledActions = enabledActions;
        }

        public int randomReaction() {
            return Calculations.random(reactionMin, reactionMax);
        }

        public int randomJitter() {
            return Calculations.random(-50, 50);
        }

        public int getFatigueThreshold() {
            return fatigueThreshold;
        }

        public int getBreakMinMs() {
            return breakMinMs;
        }

        public int getBreakMaxMs() {
            return breakMaxMs;
        }

        public int getCameraMovementChance() {
            return cameraMovementChance;
        }

        public boolean isActionEnabled(AntiBanAction action) {
            return enabledActions.contains(action);
        }

        public static BehaviorProfile cautious() {
            return new BehaviorProfile(450, 900, 9000, 1500, 3500, 35, EnumSet.allOf(AntiBanAction.class));
        }

        public static BehaviorProfile balanced() {
            return new BehaviorProfile(300, 650, 7000, 1200, 2500, 25, EnumSet.allOf(AntiBanAction.class));
        }

        public static BehaviorProfile aggressive() {
            return new BehaviorProfile(200, 500, 6000, 800, 1900, 15, EnumSet.of(AntiBanAction.MOVE_CAMERA, AntiBanAction.CHECK_XP));
        }
    }
}
