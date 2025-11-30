package com.example.ironmanalch;

import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.interactive.GameObject;
import org.dreambot.api.methods.magic.Magic;
import org.dreambot.api.methods.magic.Normal;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.widget.Widget;
import org.dreambot.api.methods.widget.WidgetChild;
import org.dreambot.api.methods.widget.Widgets;
import org.dreambot.api.input.Mouse;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.script.impl.AbstractScript;
import org.dreambot.api.utilities.Sleep;

import java.awt.Point;
import java.util.Optional;

@ScriptManifest(name = "Ironman Mith Plate Alcher", description = "Crafts mithril platebodies in Varrock West and high alchs them", author = "ChatGPT", version = 1.0, category = Category.MONEYMAKING)
public class MithrilPlateAlch extends AbstractScript {

    private static final String HAMMER = "Hammer";
    private static final String FIRE_RUNE = "Fire rune";
    private static final String FIRE_STAFF = "Staff of fire";
    private static final String NATURE_RUNE = "Nature rune";
    private static final String MITHRIL_BAR = "Mithril bar";
    private static final String MITHRIL_PLATEBODY = "Mithril platebody";

    private static final Area VARROCK_WEST_BANK = new Area(3180, 3446, 3191, 3433);
    private static final Area VARROCK_WEST_ANVIL = new Area(3185, 3426, 3190, 3423);

    private Point lastClickPoint = new Point(-1, -1);

    @Override
    public void onStart() {
        log("Starting Ironman Mith Plate Alcher");
    }

    @Override
    public int onLoop() {
        if (getLocalPlayer().isMoving() || getLocalPlayer().isAnimating()) {
            return Calculations.random(300, 600);
        }

        if (!hasSmithingSupplies() && !inventoryHasProducts()) {
            return handleBanking();
        }

        if (shouldAlch()) {
            return alchPlates();
        }

        if (hasSmithingSupplies()) {
            return handleSmithing();
        }

        return Calculations.random(400, 800);
    }

    private boolean inventoryHasProducts() {
        return getInventory().contains(MITHRIL_PLATEBODY);
    }

    private boolean hasSmithingSupplies() {
        return getInventory().contains(HAMMER) && getInventory().contains(MITHRIL_BAR);
    }

    private boolean hasAlchSupplies() {
        boolean hasFirePower = getEquipment().contains(item -> item != null && item.getName().toLowerCase().contains("fire"))
                || getInventory().count(FIRE_RUNE) >= 5;
        return getInventory().count(NATURE_RUNE) > 0 && hasFirePower;
    }

    private int handleBanking() {
        if (!VARROCK_WEST_BANK.contains(getLocalPlayer())) {
            Walking.walk(VARROCK_WEST_BANK.getRandomTile());
            prepareAntiBanMouse();
            return Calculations.random(500, 800);
        }

        if (!Bank.isOpen()) {
            GameObject booth = getGameObjects().closest(g -> g != null && g.getName().contains("Bank") && g.hasAction("Bank"));
            if (booth != null && booth.interact("Bank")) {
                prepareAntiBanMouse();
                Sleep.sleepUntil(Bank::isOpen, Calculations.random(1800, 2400));
            }
            return Calculations.random(400, 700);
        }

        Bank.depositAllExcept(item -> item != null && (item.getName().equals(HAMMER)
                || item.getName().equals(NATURE_RUNE)
                || item.getName().equals(FIRE_RUNE)
                || item.getName().toLowerCase().contains("fire")));

        ensureFireAccess();

        if (!getInventory().contains(HAMMER)) {
            Bank.withdraw(HAMMER, 1);
            Sleep.sleepUntil(() -> getInventory().contains(HAMMER), Calculations.random(1200, 1600));
        }

        int freeSlots = getInventory().emptySlotCount();
        if (freeSlots > 0) {
            Bank.withdraw(MITHRIL_BAR, freeSlots);
            Sleep.sleepUntil(() -> getInventory().contains(MITHRIL_BAR), Calculations.random(1200, 1600));
        }

        Bank.close();
        return Calculations.random(400, 700);
    }

    private void ensureFireAccess() {
        if (getEquipment().contains(item -> item != null && item.getName().toLowerCase().contains("fire"))) {
            return;
        }

        if (!getInventory().contains(FIRE_RUNE) || getInventory().count(FIRE_RUNE) < 300) {
            Bank.withdraw(FIRE_RUNE, 500);
            Sleep.sleepUntil(() -> getInventory().contains(FIRE_RUNE), Calculations.random(1000, 1500));
        }

        if (Bank.contains(FIRE_STAFF) && !getInventory().contains(FIRE_STAFF) && !getEquipment().contains(FIRE_STAFF)) {
            Bank.withdraw(FIRE_STAFF, 1);
            Sleep.sleep(600, 900);
        }

        if (getInventory().contains(FIRE_STAFF)) {
            getInventory().get(FIRE_STAFF).interact("Wield");
            prepareAntiBanMouse();
            Sleep.sleepUntil(() -> getEquipment().contains(FIRE_STAFF), 1200);
        }
    }

    private int handleSmithing() {
        if (!VARROCK_WEST_ANVIL.contains(getLocalPlayer())) {
            Walking.walk(VARROCK_WEST_ANVIL.getRandomTile());
            prepareAntiBanMouse();
            return Calculations.random(500, 800);
        }

        if (!getInventory().contains(MITHRIL_BAR)) {
            return Calculations.random(400, 700);
        }

        GameObject anvil = getGameObjects().closest("Anvil");
        if (anvil != null && anvil.interact("Smith")) {
            prepareAntiBanMouse();
            Sleep.sleepUntil(() -> Widgets.getWidget(312) != null, 1200);
            selectMithrilPlatebody();
            Sleep.sleepUntil(() -> !getInventory().contains(MITHRIL_BAR), Calculations.random(22000, 26000));
        }
        return Calculations.random(400, 700);
    }

    private void selectMithrilPlatebody() {
        Widget smithWidget = Widgets.getWidget(312);
        if (smithWidget == null) {
            return;
        }

        Optional<WidgetChild> plateChild = smithWidget.getChildren().stream()
                .filter(c -> c != null && c.getName() != null && c.getName().contains("Mithril platebody"))
                .findFirst();

        plateChild.ifPresent(child -> {
            moveMouseUnique(child.getRectangle());
            child.interact();
        });
    }

    private boolean shouldAlch() {
        return inventoryHasProducts() && hasAlchSupplies();
    }

    private int alchPlates() {
        if (!inventoryHasProducts()) {
            return Calculations.random(400, 700);
        }

        if (!hasAlchSupplies()) {
            return handleBanking();
        }

        getInventory().all(item -> item != null && item.getName().equals(MITHRIL_PLATEBODY)).forEach(item -> {
            prepareAntiBanMouse();
            if (Magic.castSpellOn(Normal.HIGH_LEVEL_ALCHEMY, item)) {
                Sleep.sleepUntil(() -> !item.isValid(), Calculations.random(900, 1200));
            }
        });

        return Calculations.random(300, 600);
    }

    private void prepareAntiBanMouse() {
        Point p;
        do {
            p = new Point(Calculations.random(20, 500), Calculations.random(20, 340));
        } while (p.equals(lastClickPoint));

        Mouse.move(p);
        lastClickPoint = p;
    }

    private void moveMouseUnique(java.awt.Rectangle bounds) {
        Point p;
        do {
            p = new Point(Calculations.random(bounds.x + 2, bounds.x + bounds.width - 2),
                    Calculations.random(bounds.y + 2, bounds.y + bounds.height - 2));
        } while (p.equals(lastClickPoint));
        Mouse.move(p);
        lastClickPoint = p;
    }
}
