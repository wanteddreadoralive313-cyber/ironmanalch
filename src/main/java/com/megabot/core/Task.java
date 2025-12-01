package com.megabot.core;

public interface Task {
    String getName();

    /** Called once when the task becomes active. */
    void onStart(TaskContext context);

    /**
     * Main loop for the task. Should return a humanized delay before the next execution.
     */
    int onLoop(TaskContext context);

    /** Called when the task completes or is stopped. */
    void onStop(TaskContext context);

    /** Whether the task is ready to start given current conditions. */
    boolean isReady(TaskContext context);

    /** Whether the task has met its completion goal. */
    boolean isComplete(TaskContext context);

    TaskStatus getStatus();
}
