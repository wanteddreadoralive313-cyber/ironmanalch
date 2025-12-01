package com.megabot.managers;

import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.utilities.Sleep;

public class InventoryManager {
    public boolean hasItem(String name, int minCount) {
        return Inventory.count(name) >= minCount;
    }

    public boolean dropAllOf(String name) {
        if (!Inventory.contains(name)) {
            return true;
        }
        Logger.log("Dropping all of: " + name);
        boolean result = Inventory.dropAll(name);
        Sleep.sleep(Calculations.random(400, 700));
        return result;
    }

    public boolean dropAllExcept(String... keepNames) {
        Logger.log("Dropping all except protected items.");
        boolean result = Inventory.dropAllExcept(keepNames);
        Sleep.sleep(Calculations.random(400, 700));
        return result;
    }
}
