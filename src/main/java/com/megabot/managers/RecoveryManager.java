package com.megabot.managers;

import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.utilities.Timer;
import org.dreambot.api.wrappers.interactive.Player;

public class RecoveryManager {
    private final Timer stuckTimer = new Timer();
    private Tile lastTile;

    public boolean isPlayerStuck() {
        Player local = org.dreambot.api.methods.interactive.Players.getLocal();
        if (local == null) {
            return false;
        }
        if (lastTile == null) {
            lastTile = local.getTile();
            stuckTimer.reset();
            return false;
        }
        if (!local.getTile().equals(lastTile)) {
            lastTile = local.getTile();
            stuckTimer.reset();
            return false;
        }
        return stuckTimer.elapsed() > 7000;
    }

    public void recoverFromStuck() {
        Logger.log("Detected possible stuck state, performing small random walk.");
        org.dreambot.api.methods.walking.impl.Walking.walkExact(org.dreambot.api.methods.interactive.Players.getLocal().getTile().randomize(2));
    }
}
