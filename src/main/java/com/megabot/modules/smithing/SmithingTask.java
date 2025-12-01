package com.megabot.modules.smithing;

import com.megabot.core.BaseTask;
import com.megabot.core.TaskContext;
import com.megabot.core.TaskStatus;
import com.megabot.managers.AntiBanEngine;
import com.megabot.managers.BankingManager;
import com.megabot.managers.NavigationManager;
import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.widget.helpers.Smithing;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;

public class SmithingTask extends BaseTask {
    private static final String ANVIL_NAME = "Anvil";
    private static final String HAMMER = "Hammer";

    private final Area workArea;
    private final String barName;
    private final String productName;
    private final int barsPerItem;
    private final int targetCount;

    private int produced = 0;

    public SmithingTask(Area workArea, String barName, String productName, int barsPerItem, int targetCount) {
        super("Smithing " + productName);
        this.workArea = workArea;
        this.barName = barName;
        this.productName = productName;
        this.barsPerItem = barsPerItem;
        this.targetCount = targetCount;
    }

    @Override
    public boolean isReady(TaskContext context) {
        return true;
    }

    @Override
    public boolean isComplete(TaskContext context) {
        return produced >= targetCount;
    }

    @Override
    public int onLoop(TaskContext context) {
        AntiBanEngine antiBan = context.getAntiBanEngine();
        NavigationManager navigation = context.getNavigationManager();
        BankingManager bankingManager = context.getBankingManager();

        if (context.getSessionManager().shouldEndForToday()) {
            Logger.log("Daily session limit hit, stopping smithing task.");
            setStatus(TaskStatus.COMPLETE);
            return -1;
        }

        if (context.getSessionManager().shouldTakeBreak()) {
            Logger.log("On break; moving mouse off-screen.");
            org.dreambot.api.methods.input.Mouse.moveMouseOutsideScreen();
            return antiBan.getHumanizedSleep(1200, 2200);
        }

        if (context.getRecoveryManager().isPlayerStuck()) {
            context.getRecoveryManager().recoverFromStuck();
            return Calculations.random(400, 700);
        }

        if (Inventory.isFull() && !hasMaterials()) {
            bankingManager.depositAllExcept(HAMMER, barName);
            return antiBan.getHumanizedSleep(300, 600);
        }

        if (!hasMaterials()) {
            if (!bankingManager.withdraw(barName, barsPerItem * 5)) {
                Logger.log("Unable to withdraw materials for smithing. Ending task.");
                setStatus(TaskStatus.FAILED);
                return -1;
            }
            bankingManager.withdraw(HAMMER, 1);
            return antiBan.getHumanizedSleep(400, 700);
        }

        if (!workArea.contains(org.dreambot.api.methods.interactive.Players.getLocal())) {
            navigation.goToArea(workArea);
            return antiBan.getHumanizedSleep(600, 900);
        }

        if (Smithing.isOpen()) {
            if (Smithing.makeAll(productName)) {
                Logger.log("Started smithing " + productName);
                Sleep.sleepUntil(() -> !Smithing.isOpen(), Calculations.random(600, 900));
                produced += Calculations.random(1, 3); // optimistic tracking when interface closes
                return antiBan.getHumanizedSleep(900, 1400);
            }
            antiBan.microDelay();
            return antiBan.getHumanizedSleep(400, 700);
        }

        GameObject anvil = GameObjects.closest(obj -> obj != null && ANVIL_NAME.equalsIgnoreCase(obj.getName()));
        if (anvil == null) {
            Logger.log("No anvil found in area; retrying.");
            return antiBan.getHumanizedSleep(600, 900);
        }

        if (anvil.interact("Smith")) {
            Sleep.sleepUntil(Smithing::isOpen, Calculations.random(1500, 2800));
        }
        return antiBan.getHumanizedSleep(300, 500);
    }

    private boolean hasMaterials() {
        return Inventory.count(barName) >= barsPerItem && Inventory.contains(HAMMER);
    }
}
