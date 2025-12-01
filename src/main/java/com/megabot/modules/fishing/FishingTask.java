package com.megabot.modules.fishing;

import com.megabot.core.BaseTask;
import com.megabot.core.TaskContext;
import com.megabot.core.TaskStatus;
import com.megabot.managers.AntiBanEngine;
import com.megabot.managers.BankingManager;
import com.megabot.managers.NavigationManager;
import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.NPC;

public class FishingTask extends BaseTask {
    private final Area fishingArea;
    private final String spotName;
    private final String interaction;
    private final String toolName;
    private final int targetFish;

    private int caught;

    public FishingTask(Area fishingArea, String spotName, String interaction, String toolName, int targetFish) {
        super("Fishing " + spotName);
        this.fishingArea = fishingArea;
        this.spotName = spotName;
        this.interaction = interaction;
        this.toolName = toolName;
        this.targetFish = targetFish;
    }

    @Override
    public boolean isReady(TaskContext context) {
        return true;
    }

    @Override
    public boolean isComplete(TaskContext context) {
        return caught >= targetFish;
    }

    @Override
    public int onLoop(TaskContext context) {
        AntiBanEngine antiBan = context.getAntiBanEngine();
        NavigationManager navigation = context.getNavigationManager();
        BankingManager bankingManager = context.getBankingManager();

        if (context.getSessionManager().shouldEndForToday()) {
            Logger.log("Session limit reached; ending fishing task.");
            setStatus(TaskStatus.COMPLETE);
            return -1;
        }

        if (context.getSessionManager().shouldTakeBreak()) {
            Logger.log("Fishing break triggered.");
            org.dreambot.api.methods.input.Mouse.moveMouseOutsideScreen();
            return antiBan.getHumanizedSleep(1500, 2400);
        }

        if (context.getRecoveryManager().isPlayerStuck()) {
            context.getRecoveryManager().recoverFromStuck();
            return Calculations.random(400, 700);
        }

        if (!Inventory.contains(toolName)) {
            Logger.log("Missing fishing tool; attempting to withdraw " + toolName);
            if (!bankingManager.withdraw(toolName, 1)) {
                Logger.log("Unable to obtain fishing tool. Marking task failed.");
                setStatus(TaskStatus.FAILED);
                return -1;
            }
            return antiBan.getHumanizedSleep(500, 900);
        }

        if (Inventory.isFull()) {
            bankingManager.depositAllExcept(toolName);
            return antiBan.getHumanizedSleep(600, 1000);
        }

        if (!fishingArea.contains(Players.getLocal())) {
            navigation.goToArea(fishingArea);
            return antiBan.getHumanizedSleep(700, 1100);
        }

        NPC spot = NPCs.closest(npc -> npc != null && spotName.equalsIgnoreCase(npc.getName()) && fishingArea.contains(npc));
        if (spot == null) {
            antiBan.performRandomAntiBan();
            return Calculations.random(500, 900);
        }

        if (spot.interact(interaction)) {
            Sleep.sleepUntil(() -> Players.getLocal().isAnimating(), Calculations.random(1200, 1800));
            Sleep.sleepUntil(() -> !Players.getLocal().isAnimating() || Inventory.isFull(), Calculations.random(6500, 8800));
            caught += Calculations.random(1, 4);
            antiBan.performRandomAntiBan();
            return antiBan.getHumanizedSleep(400, 700);
        }

        antiBan.microDelay();
        return antiBan.getHumanizedSleep(300, 600);
    }
}
