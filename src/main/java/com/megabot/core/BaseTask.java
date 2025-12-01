package com.megabot.core;

import org.dreambot.api.utilities.Timer;

public abstract class BaseTask implements Task {
    private final String name;
    private TaskStatus status = TaskStatus.READY;
    private final Timer runtime = new Timer();

    protected BaseTask(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void onStart(TaskContext context) {
        status = TaskStatus.RUNNING;
        runtime.reset();
    }

    @Override
    public void onStop(TaskContext context) {
        status = TaskStatus.COMPLETE;
    }

    @Override
    public TaskStatus getStatus() {
        return status;
    }

    protected void setStatus(TaskStatus status) {
        this.status = status;
    }

    protected long getRuntime() {
        return runtime.elapsed();
    }
}
