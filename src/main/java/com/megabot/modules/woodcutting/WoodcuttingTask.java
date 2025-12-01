package com.megabot.modules.woodcutting;

import com.megabot.core.BaseTask;
import com.megabot.core.TaskContext;
import com.megabot.core.TaskStatus;
import com.megabot.managers.AntiBanEngine;
import com.megabot.managers.BankingManager;
import com.megabot.managers.NavigationManager;
import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;

public class WoodcuttingTask extends BaseTask {
    private final Area treeArea;
    private final String treeName;
    private final String axeName;
    private final int targetLogs;

    private int chopped;

    public WoodcuttingTask(Area treeArea, String treeName, String axeName, int targetLogs) {
        super("Woodcutting " + treeName);
        this.treeArea = treeArea;
        this.treeName = treeName;
        this.axeName = axeName;
        this.targetLogs = targetLogs;
    }

    @Override
    public boolean isReady(TaskContext context) {
        return true;
    }

    @Override
    public boolean isComplete(TaskContext context) {
        return chopped >= targetLogs;
    }

    @Override
    public int onLoop(TaskContext context) {
        AntiBanEngine antiBan = context.getAntiBanEngine();
        NavigationManager navigation = context.getNavigationManager();
        BankingManager bankingManager = context.getBankingManager();

        if (context.getSessionManager().shouldEndForToday()) {
            Logger.log("Session limit reached; ending woodcutting task.");
            setStatus(TaskStatus.COMPLETE);
            return -1;
        }

        if (context.getSessionManager().shouldTakeBreak()) {
            Logger.log("Woodcutting break triggered.");
            org.dreambot.api.methods.input.Mouse.moveMouseOutsideScreen();
            return antiBan.getHumanizedSleep(1500, 2500);
        }

        if (context.getRecoveryManager().isPlayerStuck()) {
            context.getRecoveryManager().recoverFromStuck();
            return Calculations.random(400, 700);
        }

        if (!Inventory.contains(axeName)) {
            Logger.log("Missing axe; attempting to withdraw " + axeName);
            if (!bankingManager.withdraw(axeName, 1)) {
                Logger.log("Unable to obtain axe. Marking task failed.");
                setStatus(TaskStatus.FAILED);
                return -1;
            }
            return antiBan.getHumanizedSleep(500, 900);
        }

        if (Inventory.isFull()) {
            bankingManager.depositAllExcept(axeName);
            return antiBan.getHumanizedSleep(600, 1000);
        }

        if (!treeArea.contains(Players.getLocal())) {
            navigation.goToArea(treeArea);
            return antiBan.getHumanizedSleep(700, 1100);
        }

        GameObject tree = GameObjects.closest(obj -> obj != null && treeName.equalsIgnoreCase(obj.getName()) && treeArea.contains(obj));
        if (tree == null) {
            antiBan.performRandomAntiBan();
            return Calculations.random(500, 900);
        }

        if (tree.interact("Chop down")) {
            Sleep.sleepUntil(() -> Players.getLocal().isAnimating(), Calculations.random(1200, 1800));
            Sleep.sleepUntil(() -> !Players.getLocal().isAnimating() || Inventory.isFull(), Calculations.random(5500, 7600));
            chopped += Calculations.random(1, 3);
            antiBan.performRandomAntiBan();
            return antiBan.getHumanizedSleep(400, 700);
        }

        antiBan.microDelay();
        return antiBan.getHumanizedSleep(300, 600);
    }
}
