package com.megabot.managers;

import org.dreambot.api.utilities.Logger;
import org.dreambot.api.utilities.Timer;

public class SessionManager {
    private final SessionPlan plan;
    private final Timer sessionTimer = new Timer();
    private final Timer breakTimer = new Timer();
    private boolean onBreak = false;

    public SessionManager(SessionPlan plan) {
        this.plan = plan;
    }

    public void startSession() {
        sessionTimer.reset();
        breakTimer.reset();
        onBreak = false;
    }

    public boolean shouldEndForToday() {
        return sessionTimer.elapsed() > plan.getMaxDailyMillis();
    }

    public boolean shouldTakeBreak() {
        if (onBreak) {
            return breakTimer.elapsed() < plan.getCurrentBreakDuration();
        }

        if (sessionTimer.elapsed() > plan.getNextBreakAt()) {
            Logger.log("Entering scheduled break.");
            onBreak = true;
            plan.rollNextBreak();
            breakTimer.reset();
            return true;
        }
        return false;
    }

    public void endBreakIfReady() {
        if (onBreak && breakTimer.elapsed() > plan.getCurrentBreakDuration()) {
            onBreak = false;
            Logger.log("Break complete; resuming tasks.");
            sessionTimer.reset();
            breakTimer.reset();
        }
    }

    public boolean isOnBreak() {
        return onBreak;
    }

    public SessionPlan getPlan() {
        return plan;
    }

    public static class SessionPlan {
        private final long maxDailyMillis;
        private final long breakMinMillis;
        private final long breakMaxMillis;
        private final long sessionMinMillis;
        private final long sessionMaxMillis;
        private long nextBreakAt;
        private long currentBreakDuration;

        public SessionPlan(long maxDailyMillis, long breakMinMillis, long breakMaxMillis,
                           long sessionMinMillis, long sessionMaxMillis) {
            this.maxDailyMillis = maxDailyMillis;
            this.breakMinMillis = breakMinMillis;
            this.breakMaxMillis = breakMaxMillis;
            this.sessionMinMillis = sessionMinMillis;
            this.sessionMaxMillis = sessionMaxMillis;
            rollNextBreak();
        }

        public void rollNextBreak() {
            currentBreakDuration = randomBetween(breakMinMillis, breakMaxMillis);
            nextBreakAt = randomBetween(sessionMinMillis, sessionMaxMillis);
        }

        private long randomBetween(long min, long max) {
            return min + Math.round(Math.random() * (max - min));
        }

        public long getMaxDailyMillis() {
            return maxDailyMillis;
        }

        public long getNextBreakAt() {
            return nextBreakAt;
        }

        public long getCurrentBreakDuration() {
            return currentBreakDuration;
        }
    }
}
