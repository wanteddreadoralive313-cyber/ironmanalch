package com.megabot.core;

import com.megabot.managers.AntiBanEngine;
import com.megabot.managers.BankingManager;
import com.megabot.managers.InventoryManager;
import com.megabot.managers.NavigationManager;
import com.megabot.managers.RecoveryManager;
import com.megabot.managers.SessionManager;
import org.dreambot.api.script.AbstractScript;

/**
 * Shared context passed to tasks. Keeps references to managers and script utilities.
 */
public class TaskContext {
    private final AbstractScript script;
    private final AntiBanEngine antiBanEngine;
    private final NavigationManager navigationManager;
    private final BankingManager bankingManager;
    private final InventoryManager inventoryManager;
    private final SessionManager sessionManager;
    private final RecoveryManager recoveryManager;

    public TaskContext(
            AbstractScript script,
            AntiBanEngine antiBanEngine,
            NavigationManager navigationManager,
            BankingManager bankingManager,
            InventoryManager inventoryManager,
            SessionManager sessionManager,
            RecoveryManager recoveryManager
    ) {
        this.script = script;
        this.antiBanEngine = antiBanEngine;
        this.navigationManager = navigationManager;
        this.bankingManager = bankingManager;
        this.inventoryManager = inventoryManager;
        this.sessionManager = sessionManager;
        this.recoveryManager = recoveryManager;
    }

    public AbstractScript getScript() {
        return script;
    }

    public AntiBanEngine getAntiBanEngine() {
        return antiBanEngine;
    }

    public NavigationManager getNavigationManager() {
        return navigationManager;
    }

    public BankingManager getBankingManager() {
        return bankingManager;
    }

    public InventoryManager getInventoryManager() {
        return inventoryManager;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public RecoveryManager getRecoveryManager() {
        return recoveryManager;
    }
}
