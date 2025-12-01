package com.megabot.managers;

import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.utilities.Sleep;

public class BankingManager {
    private final NavigationManager navigationManager;

    public BankingManager(NavigationManager navigationManager) {
        this.navigationManager = navigationManager;
    }

    public boolean openBank() {
        if (Bank.isOpen()) {
            return true;
        }

        navigationManager.goToBank();
        return Bank.open();
    }

    public boolean withdraw(String name, int amount) {
        if (!openBank()) {
            return false;
        }
        if (Inventory.count(name) >= amount) {
            return true;
        }
        Logger.log("Withdrawing " + amount + " x " + name);
        boolean result = Bank.withdraw(name, amount + Calculations.random(-2, 2));
        Sleep.sleep(Calculations.random(400, 700));
        return result;
    }

    public boolean depositAllExcept(String... names) {
        if (!openBank()) {
            return false;
        }
        Logger.log("Depositing inventory, keeping essentials.");
        boolean result = Bank.depositAllExcept(names);
        Sleep.sleep(Calculations.random(400, 700));
        return result;
    }
}
