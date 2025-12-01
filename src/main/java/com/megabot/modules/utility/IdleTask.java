package com.megabot.modules.utility;

import com.megabot.core.BaseTask;
import com.megabot.core.TaskContext;
import com.megabot.core.TaskStatus;
import com.megabot.managers.AntiBanEngine;
import org.dreambot.api.utilities.Logger;

public class IdleTask extends BaseTask {
    private final long durationMs;
    private long start;

    public IdleTask(long durationMs) {
        super("Idle / Break");
        this.durationMs = durationMs;
    }

    @Override
    public void onStart(TaskContext context) {
        super.onStart(context);
        start = System.currentTimeMillis();
        Logger.log("Starting idle task for " + durationMs + "ms");
    }

    @Override
    public int onLoop(TaskContext context) {
        AntiBanEngine antiBan = context.getAntiBanEngine();
        if (System.currentTimeMillis() - start > durationMs) {
            setStatus(TaskStatus.COMPLETE);
            Logger.log("Idle task finished");
            return -1;
        }
        antiBan.performRandomAntiBan();
        return antiBan.getHumanizedSleep(600, 1200);
    }

    @Override
    public void onStop(TaskContext context) {
        super.onStop(context);
    }

    @Override
    public boolean isReady(TaskContext context) {
        return true;
    }

    @Override
    public boolean isComplete(TaskContext context) {
        return getStatus() == TaskStatus.COMPLETE;
    }
}
