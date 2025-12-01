package com.megabot.core;

import org.dreambot.api.utilities.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class TaskEngine {
    private final List<Task> tasks = new ArrayList<>();
    private Task activeTask;

    public void addTask(Task task) {
        tasks.add(task);
    }

    public void addTasks(List<Task> taskList) {
        tasks.addAll(taskList);
    }

    public List<Task> getTasks() {
        return Collections.unmodifiableList(tasks);
    }

    public Task getActiveTask() {
        return activeTask;
    }

    public int tick(TaskContext context) {
        if (activeTask != null && activeTask.isComplete(context)) {
            activeTask.onStop(context);
            Logger.log("Completed task: " + activeTask.getName());
            activeTask = null;
        }

        if (activeTask == null) {
            Optional<Task> next = tasks.stream()
                    .filter(t -> !t.isComplete(context) && t.isReady(context))
                    .findFirst();
            if (next.isPresent()) {
                activeTask = next.get();
                Logger.log("Switching to task: " + activeTask.getName());
                activeTask.onStart(context);
            } else {
                Logger.log("No ready tasks. Idling briefly.");
                return 600;
            }
        }

        try {
            return activeTask.onLoop(context);
        } catch (Exception e) {
            Logger.log("Error in task " + activeTask.getName() + ": " + e.getMessage());
            return 800;
        }
    }
}
