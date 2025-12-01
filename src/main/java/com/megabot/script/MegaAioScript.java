package com.megabot.script;

import com.megabot.core.TaskContext;
import com.megabot.core.TaskEngine;
import com.megabot.managers.AntiBanEngine;
import com.megabot.managers.BankingManager;
import com.megabot.managers.NavigationManager;
import com.megabot.managers.RecoveryManager;
import com.megabot.managers.SessionManager;
import com.megabot.modules.smithing.SmithingTask;
import com.megabot.modules.utility.IdleTask;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.utilities.Logger;

@ScriptManifest(name = "Mega AIO", description = "Modular all-in-one DreamBot framework", author = "Claude", version = 0.1, category = Category.MISC)
public class MegaAioScript extends AbstractScript {
    private TaskEngine taskEngine;
    private TaskContext context;

    @Override
    public void onStart() {
        Logger.log("Mega AIO starting up...");
        AntiBanEngine antiBanEngine = new AntiBanEngine(AntiBanEngine.BehaviorProfile.balanced());
        NavigationManager navigationManager = new NavigationManager();
        BankingManager bankingManager = new BankingManager(navigationManager);
        SessionManager sessionManager = new SessionManager(new SessionManager.SessionPlan(
                1000L * 60 * 120, // 2 hours daily window
                1000L * 60 * 4,
                1000L * 60 * 8,
                1000L * 20,
                1000L * 40
        ));
        RecoveryManager recoveryManager = new RecoveryManager();

        taskEngine = new TaskEngine();
        context = new TaskContext(this, antiBanEngine, navigationManager, bankingManager, sessionManager, recoveryManager);
        sessionManager.startSession();

        Area varrockAnvil = new Area(3184, 3427, 3194, 3415);
        taskEngine.addTask(new SmithingTask(varrockAnvil, "Mithril bar", "Platebody", 5, 100));
        taskEngine.addTask(new IdleTask(1000L * 60 * 5));

        Logger.log("Configured " + taskEngine.getTasks().size() + " task(s)");
    }

    @Override
    public int onLoop() {
        AntiBanEngine antiBanEngine = context.getAntiBanEngine();
        antiBanEngine.updateFatigue();
        antiBanEngine.tryRandomCamera();

        if (context.getSessionManager().shouldTakeBreak()) {
            return antiBanEngine.getHumanizedSleep(1200, 2000);
        }

        if (context.getRecoveryManager().isPlayerStuck()) {
            context.getRecoveryManager().recoverFromStuck();
        }

        int delay = taskEngine.tick(context);
        return Math.max(delay, 300);
    }
}
