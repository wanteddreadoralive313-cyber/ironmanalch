package com.megabot.managers;

import org.dreambot.api.methods.container.impl.bank.BankLocation;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.wrappers.interactive.GameObject;

public class NavigationManager {
    public boolean goToArea(Area area) {
        if (area.contains(org.dreambot.api.methods.interactive.Players.getLocal())) {
            return true;
        }
        Logger.log("Walking to area: " + area);
        return Walking.walk(area.getRandomTile())
                && org.dreambot.api.utilities.Sleep.sleepUntil(() -> area.contains(org.dreambot.api.methods.interactive.Players.getLocal()), 12000);
    }

    public boolean goToBank() {
        Logger.log("Navigating to nearest bank");
        return BankLocation.getNearest().walk()
                && org.dreambot.api.utilities.Sleep.sleepUntil(() -> org.dreambot.api.methods.container.impl.bank.Bank.isOpen(), 10000);
    }

    public boolean interactWithObject(GameObject object, String action) {
        if (object == null) {
            return false;
        }
        Logger.log("Interacting with object " + object.getName() + " using " + action);
        return object.interact(action)
                && org.dreambot.api.utilities.Sleep.sleepUntil(() -> org.dreambot.api.methods.interactive.Players.getLocal().isAnimating(), 3500);
    }
}
