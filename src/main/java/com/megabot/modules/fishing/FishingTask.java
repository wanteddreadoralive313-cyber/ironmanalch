package com.megabot.modules.fishing;

import com.megabot.core.BaseTask;
import com.megabot.core.TaskContext;
import com.megabot.core.TaskStatus;
import com.megabot.managers.AntiBanEngine;
import com.megabot.managers.BankingManager;
import com.megabot.managers.InventoryManager;
import com.megabot.managers.NavigationManager;
import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.NPC;

public class FishingTask extends BaseTask {
    private final Area fishingArea;
    private final String spotName;
    private final String actionName;
    private final String fishName;
    private final String toolName;
    private final boolean dropFish;
    private final int targetFish;

    private int caught = 0;

    public FishingTask(Area fishingArea, String spotName, String actionName, String fishName, String toolName, boolean dropFish, int targetFish) {
        super("Fishing " + fishName);
        this.fishingArea = fishingArea;
        this.spotName = spotName;
        this.actionName = actionName;
        this.fishName = fishName;
        this.toolName = toolName;
        this.dropFish = dropFish;
        this.targetFish = targetFish;
    }

    @Override
    public boolean isReady(TaskContext context) {
        return true;
    }

    @Override
    public boolean isComplete(TaskContext context) {
        return caught >= targetFish || context.getSessionManager().shouldEndForToday();
    }

    @Override
    public int onLoop(TaskContext context) {
        AntiBanEngine antiBan = context.getAntiBanEngine();
        NavigationManager navigationManager = context.getNavigationManager();
        InventoryManager inventoryManager = context.getInventoryManager();
        BankingManager bankingManager = context.getBankingManager();

        if (context.getSessionManager().shouldEndForToday()) {
            Logger.log("Daily session limit hit, stopping fishing.");
            setStatus(TaskStatus.COMPLETE);
            return -1;
        }

        if (context.getSessionManager().shouldTakeBreak()) {
            Logger.log("Taking scheduled break while fishing.");
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
            if (dropFish) {
                inventoryManager.dropAllOf(fishName);
            } else {
                bankingManager.depositAllExcept(toolName, fishName);
            }
            return antiBan.getHumanizedSleep(600, 900);
        }

        if (!fishingArea.contains(org.dreambot.api.methods.interactive.Players.getLocal())) {
            navigationManager.goToArea(fishingArea);
            return antiBan.getHumanizedSleep(500, 900);
        }

        NPC fishingSpot = NPCs.closest(npc -> npc != null && spotName.equalsIgnoreCase(npc.getName()) && fishingArea.contains(npc));
        if (fishingSpot == null) {
            Logger.log("No fishing spots visible; idling briefly.");
            antiBan.performRandomAntiBan();
            return antiBan.getHumanizedSleep(700, 1100);
        }

        if (fishingSpot.interact(actionName)) {
            Sleep.sleepUntil(() -> org.dreambot.api.methods.interactive.Players.getLocal().isAnimating(), Calculations.random(1600, 2400));
            Sleep.sleepUntil(() -> !org.dreambot.api.methods.interactive.Players.getLocal().isAnimating(), Calculations.random(4200, 6400));
            caught += Calculations.random(1, 2);
            antiBan.performRandomAntiBan();
        } else {
            antiBan.microDelay();
        }

        return antiBan.getHumanizedSleep(400, 700);
    }
}
