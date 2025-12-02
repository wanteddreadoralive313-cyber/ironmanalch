package com.megabot.managers;

import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.container.impl.bank.BankLocation;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.magic.Magic;
import org.dreambot.api.methods.magic.Normal;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;

public class NavigationManager {
    private static final int MAX_ATTEMPTS = 3;
    private static final int BASE_BACKOFF = 900;
    private final RecoveryManager recoveryManager;

    public NavigationManager() {
        this(null);
    }

    public NavigationManager(RecoveryManager recoveryManager) {
        this.recoveryManager = recoveryManager;
        if (this.recoveryManager != null) {
            this.recoveryManager.setOnStuckDetected(() -> Logger.log("Stuck detected while navigating; attempting recovery."));
            this.recoveryManager.setOnRecovery(() -> Logger.log("Movement resumed after recovery."));
        }
    }

    public boolean goToBank() {
        BankLocation nearest = BankLocation.getNearest();
        if (nearest == null) {
            Logger.log("No bank location available.");
            return false;
        }

        Area bankArea = nearest.getArea();
        Logger.log("Navigating to bank: " + nearest.getName());

        if (tryTeleportPreference(bankArea)) {
            return true;
        }

        boolean reached = walkToAreaWithRetries(bankArea);
        if (reached && !Bank.isOpen()) {
            Bank.open();
        }
        return reached;
    }

    public boolean goToArea(Area area) {
        if (area == null) {
            return false;
        }
        if (area.contains(Players.getLocal())) {
            return true;
        }

        Logger.log("Travelling to area: " + area);
        if (tryTeleportPreference(area)) {
            return true;
        }
        return walkToAreaWithRetries(area);
    }

    public boolean goToTaskLocation(String taskName, Area area) {
        Logger.log("Heading to task location for " + taskName);
        return goToArea(area);
    }

    public boolean interactWithObject(GameObject object, String action) {
        if (object == null) {
            return false;
        }
        Logger.log("Interacting with object " + object.getName() + " using " + action);
        return object.interact(action)
                && Sleep.sleepUntil(() -> Players.getLocal().isAnimating(), 3500);
    }

    private boolean walkToAreaWithRetries(Area area) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Logger.log("Walking attempt " + attempt + " to " + area);
            Tile target = area.getRandomTile();
            if (Walking.walk(target)) {
                Sleep.sleepUntil(() -> area.contains(Players.getLocal()), Calculations.random(6500, 9000));
            }

            if (area.contains(Players.getLocal())) {
                return true;
            }

            handleStuckRecovery();
            Sleep.sleep(Calculations.random(BASE_BACKOFF * attempt, BASE_BACKOFF * attempt + 400));
        }
        Logger.log("Failed to reach area after retries: " + area);
        return false;
    }

    private void handleStuckRecovery() {
        if (recoveryManager != null && recoveryManager.isPlayerStuck()) {
            recoveryManager.recoverFromStuck();
        }
    }

    private boolean tryTeleportPreference(Area targetArea) {
        if (targetArea == null || !Magic.canCast(Normal.HOME_TELEPORT)) {
            return false;
        }

        Tile local = Players.getLocal().getTile();
        if (local != null && local.distance(targetArea.getCenter()) < 100) {
            return false;
        }

        Logger.log("Attempting teleport before walking...");
        if (Magic.castSpell(Normal.HOME_TELEPORT)) {
            boolean teleported = Sleep.sleepUntil(
                    () -> targetArea.contains(Players.getLocal()) || Players.getLocal().isInCombat(),
                    Calculations.random(9000, 11000)
            );
            if (teleported && targetArea.contains(Players.getLocal())) {
                return true;
            }
        }
        return false;
    }
}
