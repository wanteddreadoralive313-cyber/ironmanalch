package com.megabot.modules.woodcutting;

import com.megabot.core.BaseTask;
import com.megabot.core.TaskContext;
import com.megabot.core.TaskStatus;
import com.megabot.managers.AntiBanEngine;
import com.megabot.managers.BankingManager;
import com.megabot.managers.InventoryManager;
import com.megabot.managers.NavigationManager;
import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;

public class WoodcuttingTask extends BaseTask {
    private final Area treeArea;
    private final String treeName;
    private final String logName;
    private final String toolName;
    private final boolean powerChop;
    private final int targetLogs;

    private int chopped = 0;

    public WoodcuttingTask(Area treeArea, String treeName, String logName, String toolName, boolean powerChop, int targetLogs) {
        super("Woodcutting " + treeName);
        this.treeArea = treeArea;
        this.treeName = treeName;
        this.logName = logName;
        this.toolName = toolName;
        this.powerChop = powerChop;
        this.targetLogs = targetLogs;
    }

    @Override
    public boolean isReady(TaskContext context) {
        return true;
    }

    @Override
    public boolean isComplete(TaskContext context) {
        return chopped >= targetLogs || context.getSessionManager().shouldEndForToday();
    }

    @Override
    public int onLoop(TaskContext context) {
        AntiBanEngine antiBan = context.getAntiBanEngine();
        NavigationManager navigationManager = context.getNavigationManager();
        InventoryManager inventoryManager = context.getInventoryManager();
        BankingManager bankingManager = context.getBankingManager();

        if (context.getSessionManager().shouldEndForToday()) {
            Logger.log("Daily session limit hit, stopping woodcutting.");
            setStatus(TaskStatus.COMPLETE);
            return -1;
        }

        if (context.getSessionManager().shouldTakeBreak()) {
            Logger.log("Taking scheduled break while woodcutting.");
            org.dreambot.api.methods.input.Mouse.moveMouseOutsideScreen();
            return antiBan.getHumanizedSleep(1200, 2000);
        }

        if (context.getRecoveryManager().isPlayerStuck()) {
            context.getRecoveryManager().recoverFromStuck();
            return antiBan.getHumanizedSleep(500, 800);
        }

        if (!Inventory.contains(toolName)) {
            if (!bankingManager.withdraw(toolName, 1)) {
                Logger.log("Missing tool " + toolName + " and unable to withdraw. Ending task.");
                setStatus(TaskStatus.FAILED);
                return -1;
            }
            return antiBan.getHumanizedSleep(400, 700);
        }

        if (Inventory.isFull()) {
            if (powerChop) {
                inventoryManager.dropAllOf(logName);
            } else {
                bankingManager.depositAllExcept(toolName, logName);
            }
            return antiBan.getHumanizedSleep(600, 900);
        }

        if (!treeArea.contains(org.dreambot.api.methods.interactive.Players.getLocal())) {
            navigationManager.goToArea(treeArea);
            return antiBan.getHumanizedSleep(500, 900);
        }

        GameObject tree = GameObjects.closest(obj -> obj != null && treeName.equalsIgnoreCase(obj.getName()) && treeArea.contains(obj));
        if (tree == null) {
            Logger.log("No trees found, waiting before retry.");
            antiBan.performRandomAntiBan();
            return antiBan.getHumanizedSleep(700, 1100);
        }

        if (tree.interact("Chop down")) {
            Sleep.sleepUntil(() -> org.dreambot.api.methods.interactive.Players.getLocal().isAnimating(), Calculations.random(1800, 2400));
            Sleep.sleepUntil(() -> !org.dreambot.api.methods.interactive.Players.getLocal().isAnimating(), Calculations.random(4200, 6200));
            chopped += Calculations.random(1, 2);
            antiBan.performRandomAntiBan();
        } else {
            antiBan.microDelay();
        }

        return antiBan.getHumanizedSleep(400, 700);
    }
}
