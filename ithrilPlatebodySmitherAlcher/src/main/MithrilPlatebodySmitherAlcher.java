package main;

import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.input.Keyboard;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.methods.magic.Magic;
import org.dreambot.api.methods.magic.Normal;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.methods.skills.SkillTracker;
import org.dreambot.api.methods.skills.Skills;
import org.dreambot.api.methods.tabs.Tab;
import org.dreambot.api.methods.tabs.Tabs;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.widget.Widget;
import org.dreambot.api.methods.widget.Widgets;
import org.dreambot.api.methods.widget.helpers.ItemProcessing;
import org.dreambot.api.methods.widget.helpers.Smithing;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.script.listener.PaintListener;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.items.Item;
import org.dreambot.api.wrappers.widgets.WidgetChild;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

@ScriptManifest(
        name = "Smith & Craft Pro",
        description = "Complete all-in-one script: Smithing, Smelting, Crafting, Fletching, Enchanting, Plank Making & Ironman Item Collection with task queue and profiles.",
        author = "Claude",
        version = 6.7,
        category = Category.MISC
)
public class MithrilPlatebodySmitherAlcher extends AbstractScript implements MouseListener {

    // Constants
    private static final String HAMMER = "Hammer";
    private static final String FEATHER = "Feather";
    private static final String NATURE_RUNE = "Nature rune";
    private static final String COINS = "Coins";
    private static final int MAX_FLETCH_AMOUNT = 5000;

    // Settings button for on-the-fly editing
    private static final Rectangle SETTINGS_BUTTON = new Rectangle(5, 255, 100, 25);

    // State enum
    private enum State {
        GUI_WAIT,
        WALKING_TO_BANK,
        BANKING,
        WALKING_TO_STATION,
        PROCESSING,
        WAITING,
        ALCHING,
        COLLECTING,  // For ironman item collection
        TASK_COMPLETE,
        ALL_DONE,
        IDLE
    }

    // Script state
    private State currentState = State.GUI_WAIT;
    private ScriptGUI gui;
    private TaskQueue taskQueue;
    private TaskQueue.Task currentTask;
    private boolean guiComplete = false;

    // Tracking
    private long startTime;
    private int totalItemsProcessed = 0;
    private int totalItemsAlched = 0;
    private int startSmithingXP, startCraftingXP, startMagicXP, startFletchingXP;
    private int totalPlanksMade = 0;
    private int totalCoinsSpent = 0;

    // Activity tracking
    private boolean isProcessing = false;
    private long lastAnimationTime = 0;
    private int itemsAtStart = 0;
    private static final long IDLE_TIMEOUT = 4000;

    // Current locations - will be set based on task selection
    private FurnaceData.FurnaceLocation currentFurnaceLocation = FurnaceData.FurnaceLocation.EDGEVILLE;
    private FurnaceData.AnvilLocation currentAnvilLocation = FurnaceData.AnvilLocation.VARROCK_WEST;
    private SawmillData.SawmillLocation currentSawmillLocation = SawmillData.SawmillLocation.VARROCK_LUMBER_YARD;

    @Override
    public void onStart() {
        startTime = System.currentTimeMillis();
        startSmithingXP = Skills.getExperience(Skill.SMITHING);
        startCraftingXP = Skills.getExperience(Skill.CRAFTING);
        startMagicXP = Skills.getExperience(Skill.MAGIC);
        startFletchingXP = Skills.getExperience(Skill.FLETCHING);

        SkillTracker.start(Skill.SMITHING);
        SkillTracker.start(Skill.CRAFTING);
        SkillTracker.start(Skill.MAGIC);
        SkillTracker.start(Skill.FLETCHING);

        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> gui = new ScriptGUI());
        } catch (Exception e) {
            log("GUI Error: " + e.getMessage());
        }

        log("Waiting for GUI...");
        log("Smith & Craft Pro v6.7 started!");
        log("Click the Settings button to edit tasks on the fly.");
    }

    @Override
    public int onLoop() {
        // Wait for GUI
        if (!guiComplete) {
            if (gui == null || !gui.isStarted()) {
                return 500;
            }

            taskQueue = gui.getTaskQueue();
            currentTask = taskQueue.getCurrentTask();

            if (currentTask != null) {
                log("Starting with " + taskQueue.getTaskCount() + " task(s)");
                log("First task: " + currentTask.getDisplayString());

                // Set location based on task type
                updateLocationsForTask();
            }

            guiComplete = true;
            log("Smith & Craft Pro v6.7 started!");
            log("Script can start from anywhere - will navigate to work location.");
        }

        // Anti-ban
        AntiBanUtil.updateFatigue();

        // Camera movement can happen even while processing (doesn't interrupt actions)
        AntiBanUtil.tryRandomCamera();

        // Other anti-ban only when not processing
        if (!isProcessing) {
            AntiBanUtil.checkForBreaks();
            AntiBanUtil.performRandomAntiBan();
        }

        // Update animation tracking
        if (Players.getLocal().getAnimation() != -1) {
            lastAnimationTime = System.currentTimeMillis();
        }

        // Handle dialogues
        if (Dialogues.canContinue()) {
            AntiBanUtil.microDelay();
            Dialogues.continueDialogue();
            return AntiBanUtil.getHumanizedSleep(200, 400);
        }

        // Check if all tasks done
        if (currentTask == null || taskQueue.allTasksComplete()) {
            log("All tasks complete!");
            return -1;
        }

        // Get state and execute
        currentState = getState();

        switch (currentState) {
            case WALKING_TO_BANK:
                walkToBank();
                break;
            case BANKING:
                handleBanking();
                break;
            case WALKING_TO_STATION:
                walkToStation();
                break;
            case PROCESSING:
                handleProcessing();
                break;
            case WAITING:
                handleWaiting();
                break;
            case ALCHING:
                handleAlching();
                break;
            case COLLECTING:
                handleCollecting();
                break;
            case TASK_COMPLETE:
                handleTaskComplete();
                break;
            case IDLE:
            default:
                break;
        }

        return AntiBanUtil.getHumanizedSleep(150, 350);
    }

    // ==================== STATE DETERMINATION ====================

    private State getState() {
        if (currentTask == null) return State.ALL_DONE;

        if (isProcessing) return State.WAITING;

        // Check if task quantity reached
        if (currentTask.hasReachedGoal()) {
            return State.TASK_COMPLETE;
        }

        // Special handling for GO_TO tasks
        if (currentTask.getType() == TaskQueue.TaskType.GO_TO) {
            LocationData.Location destination = LocationData.Location.findByName(currentTask.getDestinationName());
            if (destination != null) {
                Tile destTile = destination.getTile();
                if (destTile.distance() < 10) {
                    return State.TASK_COMPLETE; // Arrived at destination
                }
            }
            return State.PROCESSING; // Still navigating
        }

        // Special handling for IRON_COLLECTION tasks
        if (currentTask.getType() == TaskQueue.TaskType.IRON_COLLECTION) {
            IronmanCollectorTask collectorTask = currentTask.getCollectorTask();
            if (collectorTask != null) {
                if (collectorTask.isComplete()) {
                    return State.TASK_COMPLETE;
                }
                return State.COLLECTING; // Use a dedicated state for collection
            }
            return State.TASK_COMPLETE; // No collector task set
        }

        // If sawmill interface is open, stay in PROCESSING
        if (currentTask.getType() == TaskQueue.TaskType.MAKE_PLANKS && isSawmillInterfaceOpen()) {
            return State.PROCESSING;
        }

        // Check if should alch
        if (currentTask.shouldAlchAfter() && hasItemsToAlch() && !hasMaterialsForTask()) {
            return State.ALCHING;
        }

        // Check if has materials
        if (hasMaterialsForTask()) {
            if (isAtStation()) {
                return State.PROCESSING;
            } else {
                return State.WALKING_TO_STATION;
            }
        }

        // Need to bank
        if (isAtBank()) {
            return State.BANKING;
        }

        return State.WALKING_TO_BANK;
    }

    private boolean isAtBank() {
        TaskQueue.TaskType type = currentTask.getType();
        Area bankArea = getBankArea();
        return bankArea.contains(Players.getLocal());
    }

    private boolean isAtStation() {
        TaskQueue.TaskType type = currentTask.getType();
        Area stationArea = getStationArea();

        // For sawmill, also check if the operator is nearby
        if (type == TaskQueue.TaskType.MAKE_PLANKS) {
            NPC sawmillOperator = NPCs.closest("Sawmill operator");
            if (sawmillOperator != null && sawmillOperator.distance() < 10) {
                return true;
            }
        }

        return stationArea.contains(Players.getLocal());
    }

    private Area getBankArea() {
        if (currentTask == null) return currentAnvilLocation.getBankArea();

        switch (currentTask.getType()) {
            case SMELT_BAR:
            case CRAFT_JEWELRY:
            case MAKE_CANNONBALLS:
                return currentFurnaceLocation.getBankArea();
            case SMITH_ITEM:
                return currentAnvilLocation.getBankArea();
            case MAKE_PLANKS:
                return currentSawmillLocation.getBankArea();
            default:
                // Fletching/enchanting can be done at any bank
                return currentAnvilLocation.getBankArea();
        }
    }

    private Area getStationArea() {
        if (currentTask == null) return currentAnvilLocation.getAnvilArea();

        switch (currentTask.getType()) {
            case SMELT_BAR:
            case CRAFT_JEWELRY:
            case MAKE_CANNONBALLS:
                return currentFurnaceLocation.getFurnaceArea();
            case SMITH_ITEM:
                return currentAnvilLocation.getAnvilArea();
            case MAKE_PLANKS:
                return currentSawmillLocation.getSawmillArea();
            default:
                // Fletching/enchanting can be done anywhere, use bank area
                return currentAnvilLocation.getBankArea();
        }
    }

    /**
     * Update current anvil/furnace/sawmill locations based on task settings
     */
    private void updateLocationsForTask() {
        if (currentTask == null) return;

        String locationName = currentTask.getLocation();
        if (locationName == null || locationName.isEmpty()) {
            locationName = "West Varrock"; // Default
        }

        TaskQueue.TaskType taskType = currentTask.getType();

        // Check if task requires furnace, anvil, or sawmill
        if (taskType == TaskQueue.TaskType.SMELT_BAR ||
                taskType == TaskQueue.TaskType.CRAFT_JEWELRY ||
                taskType == TaskQueue.TaskType.MAKE_CANNONBALLS) {
            // Furnace task
            currentFurnaceLocation = FurnaceData.getLocationByName(locationName);
            log("Using furnace at: " + currentFurnaceLocation.getName());
        } else if (taskType == TaskQueue.TaskType.SMITH_ITEM) {
            // Anvil task
            currentAnvilLocation = FurnaceData.getAnvilByName(locationName);
            log("Using anvil at: " + currentAnvilLocation.getName());
        } else if (taskType == TaskQueue.TaskType.MAKE_PLANKS) {
            // Sawmill task
            currentSawmillLocation = SawmillData.getSawmillByName(locationName);
            log("Using sawmill at: " + currentSawmillLocation.getName());
        } else {
            // Fletching/enchanting - use default anvil location for banking
            currentAnvilLocation = FurnaceData.getAnvilByName(locationName);
            log("Using bank at: " + currentAnvilLocation.getName());
        }
    }

    // ==================== MATERIALS CHECK ====================

    private boolean hasMaterialsForTask() {
        switch (currentTask.getType()) {
            case SMITH_ITEM:
                return hasSmithingMaterials();
            case SMELT_BAR:
                return hasSmeltingMaterials();
            case CRAFT_JEWELRY:
                return hasJewelryMaterials();
            case MAKE_CANNONBALLS:
                return Inventory.contains("Steel bar");
            case MAKE_PLANKS:
                return hasPlankmakingMaterials();
            case FLETCH_DARTS:
            case FLETCH_BOLTS:
                return hasFletchingMaterials();
            case ENCHANT_BOLTS:
                return hasEnchantMaterials();
            case GO_TO:
                return true; // No materials needed for navigation
            default:
                return false;
        }
    }

    private boolean hasSmithingMaterials() {
        String barName = SmithingData.getBarName(currentTask.getMetalType());
        int barsRequired = SmithingData.getBarsRequired(currentTask.getItemType());
        return Inventory.count(barName) >= barsRequired;
    }

    private boolean hasPlankmakingMaterials() {
        SawmillData.PlankType plankType = SawmillData.getPlankTypeByName(currentTask.getItemType());
        if (plankType == null) return false;

        // Check for logs
        if (!Inventory.contains(plankType.getLogName())) return false;

        // Check for enough coins
        int logCount = Inventory.count(plankType.getLogName());
        int coinsNeeded = plankType.getCoinsNeeded(logCount);
        return Inventory.count(COINS) >= coinsNeeded;
    }

    private boolean hasSmeltingMaterials() {
        FurnaceData.SmeltableBar bar = FurnaceData.getBarByName(currentTask.getItemType());
        if (bar == null) return false;

        String[] ores = bar.getOres();
        int[] amounts = bar.getOreAmounts();

        for (int i = 0; i < ores.length; i++) {
            if (Inventory.count(ores[i]) < amounts[i]) return false;
        }
        return true;
    }

    private boolean hasJewelryMaterials() {
        FurnaceData.JewelryItem item = FurnaceData.getJewelryByName(currentTask.getItemType());
        if (item == null) return false;

        if (!Inventory.contains(item.getBarType())) return false;
        if (!Inventory.contains(item.getMouldRequired())) return false;
        if (item.requiresGem() && !Inventory.contains(item.getGemRequired())) return false;

        return true;
    }

    private boolean hasFletchingMaterials() {
        String materialName = getFletchMaterialName();
        return Inventory.contains(materialName) && Inventory.contains(FEATHER);
    }

    private boolean hasEnchantMaterials() {
        String boltName = currentTask.getItemType();
        return Inventory.contains(boltName);
    }

    private boolean hasItemsToAlch() {
        String productName = getProductName();
        return Inventory.contains(productName);
    }

    // ==================== NAVIGATION ====================

    /**
     * Walk to bank area using DreamBot's web walker
     * Works from anywhere in the game world
     */
    private void walkToBank() {
        Area bankArea = getBankArea();

        // Enable run if we have energy
        if (!Walking.isRunEnabled() && Walking.getRunEnergy() > Calculations.random(15, 40)) {
            Walking.toggleRun();
            Sleep.sleep(Calculations.random(300, 600));
        }

        // Check if already at bank
        if (bankArea.contains(Players.getLocal())) {
            return;
        }

        // Use DreamBot's web walker - handles long distance navigation automatically
        AntiBanUtil.microDelay();

        // Get a random tile in the bank area for variation
        org.dreambot.api.methods.map.Tile destination = bankArea.getRandomTile();

        // Walking.walk() uses the web walker which can navigate across the entire game world
        if (Walking.walk(destination)) {
            // Wait until we arrive or stop moving
            Sleep.sleepUntil(() -> {
                // Check if we're at destination or close enough
                if (bankArea.contains(Players.getLocal())) {
                    return true;
                }
                // Keep waiting if still moving
                return !Players.getLocal().isMoving();
            }, Calculations.random(15000, 20000));
        }

        // If still not at bank after walking, try again with direct tile
        if (!bankArea.contains(Players.getLocal()) && !Players.getLocal().isMoving()) {
            log("Retrying navigation to bank...");
            Walking.walk(bankArea.getCenter());
        }
    }

    /**
     * Walk to workstation (anvil/furnace) using DreamBot's web walker
     * Works from anywhere in the game world
     */
    private void walkToStation() {
        Area stationArea = getStationArea();

        // Enable run if we have energy
        if (!Walking.isRunEnabled() && Walking.getRunEnergy() > Calculations.random(15, 40)) {
            Walking.toggleRun();
            Sleep.sleep(Calculations.random(300, 600));
        }

        // Check if already at station
        if (stationArea.contains(Players.getLocal())) {
            return;
        }

        AntiBanUtil.microDelay();

        // Get destination tile
        org.dreambot.api.methods.map.Tile destination = stationArea.getRandomTile();

        // Use web walker for navigation
        if (Walking.walk(destination)) {
            Sleep.sleepUntil(() -> {
                if (stationArea.contains(Players.getLocal())) {
                    return true;
                }
                return !Players.getLocal().isMoving();
            }, Calculations.random(15000, 20000));
        }

        // Retry if needed
        if (!stationArea.contains(Players.getLocal()) && !Players.getLocal().isMoving()) {
            log("Retrying navigation to station...");
            Walking.walk(stationArea.getCenter());
        }
    }

    /**
     * Check if player should walk (not at destination and not already walking there)
     */
    private boolean shouldWalk(Area destination) {
        if (destination.contains(Players.getLocal())) {
            return false;
        }
        // Only start new walk if not already moving
        return !Players.getLocal().isMoving();
    }

    // ==================== BANKING ====================

    private void handleBanking() {
        if (!Bank.isOpen()) {
            AntiBanUtil.microDelay();
            Bank.open();
            Sleep.sleepUntil(Bank::isOpen, Calculations.random(2000, 3000));
            return;
        }

        switch (currentTask.getType()) {
            case SMITH_ITEM:
                bankForSmithing();
                break;
            case SMELT_BAR:
                bankForSmelting();
                break;
            case CRAFT_JEWELRY:
                bankForJewelry();
                break;
            case MAKE_CANNONBALLS:
                bankForCannonballs();
                break;
            case MAKE_PLANKS:
                bankForPlanks();
                break;
            case FLETCH_DARTS:
            case FLETCH_BOLTS:
                bankForFletching();
                break;
            case ENCHANT_BOLTS:
                bankForEnchanting();
                break;
        }

        Bank.close();
        Sleep.sleepUntil(() -> !Bank.isOpen(), Calculations.random(1000, 2000));
    }

    private void bankForSmithing() {
        String barName = SmithingData.getBarName(currentTask.getMetalType());
        String itemName = SmithingData.getItemName(currentTask.getMetalType(), currentTask.getItemType());

        // Deposit products
        if (Inventory.contains(itemName)) {
            Bank.depositAll(itemName);
            Sleep.sleep(Calculations.random(200, 400));
        }

        // Deposit except essentials
        if (currentTask.shouldAlchAfter()) {
            Bank.depositAllExcept(HAMMER, NATURE_RUNE);
        } else {
            Bank.depositAllExcept(HAMMER);
        }
        Sleep.sleep(Calculations.random(200, 400));

        // Withdraw hammer
        if (!Inventory.contains(HAMMER)) {
            if (!Bank.contains(HAMMER)) {
                log("No hammer! Stopping.");
                stop();
                return;
            }
            Bank.withdraw(HAMMER, 1);
            Sleep.sleep(Calculations.random(200, 400));
        }

        // Withdraw ALL nature runes if alching (we need them for the whole session)
        if (currentTask.shouldAlchAfter()) {
            if (Bank.contains(NATURE_RUNE)) {
                Bank.withdrawAll(NATURE_RUNE);
                Sleep.sleep(Calculations.random(200, 400));
            } else if (!Inventory.contains(NATURE_RUNE)) {
                // No nature runes in bank or inventory - stop script
                log("No Nature runes available! Cannot alch. Stopping script.");
                stop();
                return;
            }
        }

        // Calculate and withdraw bars
        int barsPerItem = SmithingData.getBarsRequired(currentTask.getItemType());
        int slots = currentTask.shouldAlchAfter() ? 26 : 27;
        int barsToWithdraw = (slots / barsPerItem) * barsPerItem;

        if (!Bank.contains(barName)) {
            log("No " + barName + "s! Task complete or stopping.");
            currentTask.setCompleted(true);
            return;
        }

        Bank.withdraw(barName, barsToWithdraw);
        Sleep.sleep(Calculations.random(200, 400));
    }

    private void bankForSmelting() {
        FurnaceData.SmeltableBar bar = FurnaceData.getBarByName(currentTask.getItemType());
        if (bar == null) {
            log("Error: Unknown bar type: " + currentTask.getItemType());
            return;
        }

        // Deposit everything first to have clean inventory
        Bank.depositAllItems();
        Sleep.sleep(Calculations.random(300, 500));

        // Get ore requirements
        String[] ores = bar.getOres();
        int[] amounts = bar.getOreAmounts();

        // Calculate total ores needed per bar
        int totalOresPerBar = 0;
        for (int amount : amounts) {
            totalOresPerBar += amount;
        }

        // Calculate maximum bars we can make with 28 inventory slots
        int maxBarsPerTrip = 28 / totalOresPerBar;

        log("=== Smelting " + bar.getBarName() + " ===");
        log("Ores per bar: " + totalOresPerBar + " | Max bars per trip: " + maxBarsPerTrip);

        // Check how many bars we can actually make based on bank contents
        int barsCanMake = maxBarsPerTrip;
        for (int i = 0; i < ores.length; i++) {
            int bankCount = Bank.count(ores[i]);
            int neededPerTrip = maxBarsPerTrip * amounts[i];
            int possibleBars = bankCount / amounts[i];

            log(ores[i] + ": Bank has " + bankCount + ", need " + neededPerTrip + " for full trip (" + amounts[i] + " per bar)");

            if (possibleBars < barsCanMake) {
                barsCanMake = possibleBars;
            }

            if (bankCount < amounts[i]) {
                log("Not enough " + ores[i] + "! Task complete.");
                currentTask.setCompleted(true);
                return;
            }
        }

        // Limit to what fits in inventory
        if (barsCanMake > maxBarsPerTrip) {
            barsCanMake = maxBarsPerTrip;
        }

        if (barsCanMake <= 0) {
            log("Cannot make any bars! Task complete.");
            currentTask.setCompleted(true);
            return;
        }

        log("Will smelt " + barsCanMake + " bars this trip");

        // Withdraw the correct amounts
        for (int i = 0; i < ores.length; i++) {
            int withdrawAmount = barsCanMake * amounts[i];
            log("Withdrawing " + withdrawAmount + " " + ores[i]);
            Bank.withdraw(ores[i], withdrawAmount);
            Sleep.sleep(Calculations.random(200, 400));
        }

        // Verify we got the ores
        Sleep.sleep(Calculations.random(100, 200));
        int totalOresWithdrawn = 0;
        for (String ore : ores) {
            totalOresWithdrawn += Inventory.count(ore);
        }
        log("Total ores in inventory: " + totalOresWithdrawn);
    }

    private void bankForJewelry() {
        FurnaceData.JewelryItem item = FurnaceData.getJewelryByName(currentTask.getItemType());
        if (item == null) return;

        // Deposit products
        Bank.depositAll(item.getItemName());
        Sleep.sleep(Calculations.random(200, 400));

        // Keep mould
        Bank.depositAllExcept(item.getMouldRequired());
        Sleep.sleep(Calculations.random(200, 400));

        // Withdraw mould if needed
        if (!Inventory.contains(item.getMouldRequired())) {
            if (!Bank.contains(item.getMouldRequired())) {
                log("No " + item.getMouldRequired() + "! Stopping.");
                stop();
                return;
            }
            Bank.withdraw(item.getMouldRequired(), 1);
            Sleep.sleep(Calculations.random(200, 400));
        }

        // Withdraw bars
        int barSlots = item.requiresGem() ? 13 : 27;
        if (!Bank.contains(item.getBarType())) {
            log("No " + item.getBarType() + "s! Task complete.");
            currentTask.setCompleted(true);
            return;
        }
        Bank.withdraw(item.getBarType(), barSlots);
        Sleep.sleep(Calculations.random(200, 400));

        // Withdraw gems if needed
        if (item.requiresGem()) {
            String gem = item.getGemRequired();
            if (!Bank.contains(gem)) {
                log("No " + gem + "s! Task complete.");
                currentTask.setCompleted(true);
                return;
            }
            Bank.withdraw(gem, barSlots);
            Sleep.sleep(Calculations.random(200, 400));
        }
    }

    private void bankForCannonballs() {
        // Deposit any cannonballs
        if (Inventory.contains("Cannonball")) {
            Bank.depositAll("Cannonball");
            Sleep.sleep(Calculations.random(200, 400));
        }

        // Keep ammo mould, deposit everything else
        Bank.depositAllExcept("Ammo mould");
        Sleep.sleep(Calculations.random(200, 400));

        // Withdraw ammo mould if needed
        if (!Inventory.contains("Ammo mould")) {
            if (!Bank.contains("Ammo mould")) {
                log("No ammo mould found! Stopping.");
                stop();
                return;
            }
            Bank.withdraw("Ammo mould", 1);
            Sleep.sleep(Calculations.random(200, 400));
        }

        // Cannonballs require STEEL bars specifically
        if (!Bank.contains("Steel bar")) {
            log("No steel bars found! Cannonballs require steel bars. Task complete.");
            currentTask.setCompleted(true);
            return;
        }

        // Withdraw steel bars (27 slots with mould taking 1)
        Bank.withdraw("Steel bar", 27);
        Sleep.sleep(Calculations.random(200, 400));
    }

    private void bankForPlanks() {
        SawmillData.PlankType plankType = SawmillData.getPlankTypeByName(currentTask.getItemType());
        if (plankType == null) {
            log("Error: Unknown plank type: " + currentTask.getItemType());
            return;
        }

        String logName = plankType.getLogName();
        String plankName = plankType.getPlankName();
        int costPerPlank = plankType.getCostPerPlank();

        log("=== Banking for " + plankName + " ===");
        log("Log type: " + logName + " | Cost: " + costPerPlank + " coins each");

        // Deposit any planks we've made
        if (Inventory.contains(plankName)) {
            int plankCount = Inventory.count(plankName);
            totalPlanksMade += plankCount;
            Bank.depositAll(plankName);
            Sleep.sleep(Calculations.random(200, 400));
        }

        // Deposit everything except coins
        Bank.depositAllExcept(COINS);
        Sleep.sleep(Calculations.random(200, 400));

        // Check if we have logs
        if (!Bank.contains(logName)) {
            log("No " + logName + " in bank! Task complete.");
            currentTask.setCompleted(true);
            return;
        }

        // Calculate how many logs we can process
        // Need 1 slot for coins, so max 27 logs per trip
        int logsInBank = Bank.count(logName);
        int logsToWithdraw = Math.min(logsInBank, SawmillData.MAX_LOGS_PER_TRIP);
        int coinsNeeded = logsToWithdraw * costPerPlank;

        // Check if we have enough coins
        int totalCoins = Bank.count(COINS) + Inventory.count(COINS);
        if (totalCoins < coinsNeeded) {
            // Calculate how many logs we can afford
            logsToWithdraw = totalCoins / costPerPlank;
            if (logsToWithdraw <= 0) {
                log("Not enough coins! Need " + costPerPlank + " per plank. Task complete.");
                currentTask.setCompleted(true);
                return;
            }
            coinsNeeded = logsToWithdraw * costPerPlank;
            log("Limited by coins - can only make " + logsToWithdraw + " planks");
        }

        // Withdraw coins if we don't have enough in inventory
        int coinsInInventory = Inventory.count(COINS);
        if (coinsInInventory < coinsNeeded) {
            int coinsToWithdraw = coinsNeeded - coinsInInventory;
            // Withdraw a bit extra for safety
            coinsToWithdraw = Math.min(coinsToWithdraw + 1000, Bank.count(COINS));
            Bank.withdraw(COINS, coinsToWithdraw);
            Sleep.sleep(Calculations.random(200, 400));
        }

        // Withdraw logs
        log("Withdrawing " + logsToWithdraw + " " + logName + " (cost: " + coinsNeeded + " coins)");
        Bank.withdraw(logName, logsToWithdraw);
        Sleep.sleep(Calculations.random(200, 400));

        // Track coins spent
        totalCoinsSpent += coinsNeeded;
    }

    private void bankForFletching() {
        String materialName = getFletchMaterialName();
        String productName = getProductName();

        // Deposit products
        if (Inventory.contains(productName)) {
            Bank.depositAll(productName);
            Sleep.sleep(Calculations.random(200, 400));
        }

        // Withdraw materials
        if (!Bank.contains(materialName)) {
            log("No " + materialName + "! Task complete.");
            currentTask.setCompleted(true);
            return;
        }

        int amount = Math.min(Bank.count(materialName), MAX_FLETCH_AMOUNT);
        Bank.withdraw(materialName, amount);
        Sleep.sleep(Calculations.random(200, 400));

        // Withdraw feathers
        int materialsInInv = Inventory.count(materialName);
        if (!Bank.contains(FEATHER)) {
            log("No feathers! Task complete.");
            currentTask.setCompleted(true);
            return;
        }
        Bank.withdraw(FEATHER, Math.min(Bank.count(FEATHER), materialsInInv));
        Sleep.sleep(Calculations.random(200, 400));
    }

    private void bankForEnchanting() {
        String boltName = currentTask.getItemType();
        String enchantedName = boltName.replace(" bolts", " bolts (e)");

        // Deposit enchanted bolts
        Bank.depositAll(enchantedName);
        Sleep.sleep(Calculations.random(200, 400));

        // Keep runes
        Bank.depositAllExcept(item -> item.getName().contains("rune"));
        Sleep.sleep(Calculations.random(200, 400));

        // Withdraw bolts
        if (!Bank.contains(boltName)) {
            log("No " + boltName + "! Task complete.");
            currentTask.setCompleted(true);
            return;
        }
        Bank.withdrawAll(boltName);
        Sleep.sleep(Calculations.random(200, 400));
    }

    // ==================== PROCESSING ====================

    private void handleProcessing() {
        switch (currentTask.getType()) {
            case SMITH_ITEM:
                processSmithing();
                break;
            case SMELT_BAR:
                processSmelting();
                break;
            case CRAFT_JEWELRY:
            case MAKE_CANNONBALLS:
                processFurnace();
                break;
            case MAKE_PLANKS:
                processPlanks();
                break;
            case FLETCH_DARTS:
            case FLETCH_BOLTS:
                processFletching();
                break;
            case ENCHANT_BOLTS:
                processEnchanting();
                break;
            case GO_TO:
                processGoTo();
                break;
        }
    }

    private void processSmithing() {
        String itemName = SmithingData.getItemName(currentTask.getMetalType(), currentTask.getItemType());
        String barName = SmithingData.getBarName(currentTask.getMetalType());

        if (Smithing.isOpen()) {
            itemsAtStart = Inventory.count(barName);
            AntiBanUtil.microDelay();

            log("Smithing interface open. Making " + itemName + "...");

            // Method 1: Try Smithing.makeAll with item name
            if (Smithing.makeAll(itemName)) {
                log("Started smithing via makeAll(itemName)");
                isProcessing = true;
                lastAnimationTime = System.currentTimeMillis();
                Sleep.sleep(Calculations.random(600, 1000));
                return;
            }

            // Method 2: Try with just the item type (e.g., "Platebody")
            String simpleItemName = currentTask.getItemType();
            if (Smithing.makeAll(simpleItemName)) {
                log("Started smithing via makeAll(simpleItemName)");
                isProcessing = true;
                lastAnimationTime = System.currentTimeMillis();
                Sleep.sleep(Calculations.random(600, 1000));
                return;
            }

            // Method 3: Press Space (universal confirm)
            log("makeAll failed, pressing Space to confirm...");
            Sleep.sleep(Calculations.random(200, 400));
            Keyboard.type(" ", false);
            isProcessing = true;
            lastAnimationTime = System.currentTimeMillis();
            Sleep.sleep(Calculations.random(600, 1000));
            return;
        }

        GameObject anvil = GameObjects.closest("Anvil");
        if (anvil != null) {
            AntiBanUtil.microDelay();
            log("Clicking anvil...");
            anvil.interact("Smith");
            Sleep.sleepUntil(Smithing::isOpen, Calculations.random(3000, 5000));
        }
    }

    private void processSmelting() {
        FurnaceData.SmeltableBar bar = FurnaceData.getBarByName(currentTask.getItemType());
        if (bar == null) {
            log("Error: Unknown bar type: " + currentTask.getItemType());
            return;
        }

        // Check if the Make-X interface is open (smelting uses this)
        if (ItemProcessing.isOpen()) {
            itemsAtStart = Inventory.count(bar.getPrimaryOre());
            AntiBanUtil.microDelay();

            String barName = bar.getBarName();
            log("Interface open. Attempting to smelt " + barName + "...");

            // Method 1: Try ItemProcessing.makeAll with bar name
            if (ItemProcessing.makeAll(barName)) {
                log("Started smelting via makeAll(barName)");
                isProcessing = true;
                lastAnimationTime = System.currentTimeMillis();
                Sleep.sleep(Calculations.random(600, 1000));
                return;
            }

            // Method 2: Try with just the metal type (e.g., "Adamantite")
            String metalName = barName.replace(" bar", "");
            if (ItemProcessing.makeAll(metalName)) {
                log("Started smelting via makeAll(metalName)");
                isProcessing = true;
                lastAnimationTime = System.currentTimeMillis();
                Sleep.sleep(Calculations.random(600, 1000));
                return;
            }

            // Method 3: Press Space (universal confirm in OSRS Make-X interface)
            log("makeAll failed, pressing Space to confirm...");
            Sleep.sleep(Calculations.random(200, 400));
            Keyboard.type(" ", false);
            isProcessing = true;
            lastAnimationTime = System.currentTimeMillis();
            Sleep.sleep(Calculations.random(600, 1000));
            return;
        }

        // Use furnace to open interface
        GameObject furnace = GameObjects.closest("Furnace");
        if (furnace != null) {
            AntiBanUtil.microDelay();
            log("Clicking furnace...");
            furnace.interact("Smelt");
            Sleep.sleepUntil(ItemProcessing::isOpen, Calculations.random(3000, 5000));
        }
    }

    private void processFurnace() {
        // Generic furnace processing for jewelry/cannonballs
        if (ItemProcessing.isOpen()) {
            String itemName = currentTask.getItemType();
            itemsAtStart = Inventory.count(getInputItemName());
            AntiBanUtil.microDelay();

            log("Furnace interface open. Making " + itemName + "...");

            // For cannonballs, item might be named differently
            String searchName = itemName;
            if (itemName.contains("Cannonball")) {
                searchName = "Cannonball";
            }

            // Method 1: Try ItemProcessing.makeAll
            if (ItemProcessing.makeAll(searchName)) {
                log("Started via makeAll");
                isProcessing = true;
                lastAnimationTime = System.currentTimeMillis();
                Sleep.sleep(Calculations.random(600, 1000));
                return;
            }

            // Method 2: Press Space (universal confirm)
            log("makeAll failed, pressing Space to confirm...");
            Sleep.sleep(Calculations.random(200, 400));
            Keyboard.type(" ", false);
            isProcessing = true;
            lastAnimationTime = System.currentTimeMillis();
            Sleep.sleep(Calculations.random(600, 1000));
            return;
        }

        GameObject furnace = GameObjects.closest("Furnace");
        if (furnace != null) {
            AntiBanUtil.microDelay();
            log("Clicking furnace...");
            furnace.interact("Smelt");
            Sleep.sleepUntil(ItemProcessing::isOpen, Calculations.random(3000, 5000));
        }
    }

    private void processPlanks() {
        // First, verify what task we have
        String taskItemType = currentTask.getItemType();
        log("=== PLANK MAKING v6.6 ===");
        log("Task item type from currentTask.getItemType(): '" + taskItemType + "'");

        SawmillData.PlankType plankType = SawmillData.getPlankTypeByName(taskItemType);
        if (plankType == null) {
            log("ERROR: Could not resolve plank type from: '" + taskItemType + "'");
            log("Available plank types:");
            for (SawmillData.PlankType pt : SawmillData.PlankType.values()) {
                log("  - " + pt.name() + " = '" + pt.getPlankName() + "'");
            }
            return;
        }

        String logName = plankType.getLogName();
        String plankName = plankType.getPlankName();
        int logsInInventory = Inventory.count(logName);

        log("Resolved plank type: " + plankType.name());
        log("Log name to look for: '" + logName + "'");
        log("Plank name to make: '" + plankName + "'");
        log("Logs in inventory: " + logsInInventory);

        // Check if we still have the correct logs
        if (logsInInventory == 0) {
            log("No " + logName + " found in inventory - need to bank");
            isProcessing = false;
            return;
        }

        // Keep isProcessing true to prevent walking away
        isProcessing = true;

        // Wait if player is moving
        if (Players.getLocal().isMoving()) {
            Sleep.sleep(Calculations.random(200, 400));
            return;
        }

        // Check if sawmill interface is open using Widgets.getWidget()
        Widget sawmillWidget = Widgets.getWidget(270);
        boolean interfaceOpen = sawmillWidget != null && sawmillWidget.isVisible();

        if (interfaceOpen) {
            log("Sawmill interface (Widget 270) is OPEN");

            // CRITICAL: Find the correct plank button by searching ALL widgets for matching text
            WidgetChild targetWidget = findPlankWidgetByText(plankType);

            if (targetWidget != null) {
                log(">>> FOUND widget for '" + plankName + "' - attempting click...");

                // Try to interact with the widget
                if (targetWidget.interact()) {
                    log(">>> CLICKED widget successfully!");

                    // Wait for conversion
                    final int logsBefore = logsInInventory;
                    final String finalLogName = logName;
                    final String finalPlankName = plankName;

                    boolean success = Sleep.sleepUntil(() -> {
                        int logsNow = Inventory.count(finalLogName);
                        int planksNow = Inventory.count(finalPlankName);
                        return logsNow < logsBefore || planksNow > 0;
                    }, Calculations.random(3000, 5000));

                    if (success) {
                        int logsRemaining = Inventory.count(logName);
                        int planksMade = logsBefore - logsRemaining;
                        if (planksMade > 0) {
                            totalPlanksMade += planksMade;
                            totalItemsProcessed += planksMade;
                            log("SUCCESS! Made " + planksMade + " " + plankName + "! Total: " + totalPlanksMade);
                        }
                    } else {
                        log("Conversion did not complete - will retry");
                    }

                    isProcessing = false;
                    return;
                } else {
                    log(">>> FAILED to click widget - will retry");
                }
            } else {
                log(">>> Could NOT find widget for '" + plankName + "'");
                // Dump all widgets to help debug
                dumpAllSawmillWidgets();
            }

            // Close interface and retry
            Tabs.open(Tab.INVENTORY);
            Sleep.sleep(Calculations.random(600, 1000));
            isProcessing = false;
            return;
        }

        // Interface not open - interact with sawmill operator
        NPC sawmillOperator = NPCs.closest("Sawmill operator");

        if (sawmillOperator == null) {
            log("Cannot find Sawmill operator!");
            isProcessing = false;
            return;
        }

        if (!sawmillOperator.isOnScreen()) {
            log("Walking to sawmill operator...");
            Walking.walk(sawmillOperator.getTile());
            Sleep.sleepUntil(() -> sawmillOperator.isOnScreen() || !Players.getLocal().isMoving(),
                    Calculations.random(4000, 6000));
            return;
        }

        log("Clicking 'Buy-plank' on Sawmill operator...");
        if (sawmillOperator.interact("Buy-plank")) {
            log("Waiting for interface to open...");
            boolean opened = Sleep.sleepUntil(() -> {
                Widget w = Widgets.getWidget(270);
                return w != null && w.isVisible();
            }, Calculations.random(4000, 6000));

            if (opened) {
                log("Interface opened!");
            } else {
                log("Interface did not open - will retry");
            }
        } else {
            log("Failed to click Buy-plank");
            Sleep.sleep(Calculations.random(500, 1000));
        }
    }

    /**
     * Find the correct plank widget by searching for text match
     * Searches Widget 270 children for matching text
     */
    private WidgetChild findPlankWidgetByText(SawmillData.PlankType plankType) {
        // The sawmill interface uses short names like "Wood", "Oak", "Teak", "Mahogany"
        // NOT "Plank", "Oak plank", etc.
        // Widget names look like: <col=ff9040>Oak - 250gp</col>

        String searchTerm;
        switch (plankType) {
            case PLANK:
                searchTerm = "wood";  // Regular plank shows as "Wood - 100gp"
                break;
            case OAK_PLANK:
                searchTerm = "oak";
                break;
            case TEAK_PLANK:
                searchTerm = "teak";
                break;
            case MAHOGANY_PLANK:
                searchTerm = "mahogany";
                break;
            default:
                searchTerm = plankType.getPlankName().toLowerCase();
                break;
        }

        log("Searching for widget with name containing: '" + searchTerm + "'");

        // Get parent widget 270
        Widget parent = Widgets.getWidget(270);
        if (parent == null || !parent.isVisible()) {
            log("  Widget 270 not visible!");
            return null;
        }

        java.util.List<WidgetChild> children = parent.getChildren();
        if (children == null || children.isEmpty()) {
            log("  No children found!");
            return null;
        }

        log("  Found " + children.size() + " children in widget 270");

        // Search for widget with matching name and "Make" action
        for (int i = 0; i < children.size(); i++) {
            WidgetChild child = children.get(i);
            if (child == null || !child.isVisible()) continue;

            String name = child.getName();
            if (name == null || name.isEmpty()) continue;

            // Strip color tags: <col=ff9040>Oak - 250gp</col> -> oak - 250gp
            String nameLower = name.toLowerCase().replaceAll("<[^>]*>", "").trim();

            // Check if this widget's name starts with our search term
            // e.g. "oak - 250gp" starts with "oak"
            if (nameLower.startsWith(searchTerm)) {
                log("  MATCH at child " + i + ": name='" + name + "' (cleaned: '" + nameLower + "')");

                // Check if this one has the "Make" action
                String[] actions = child.getActions();
                if (actions != null) {
                    for (String action : actions) {
                        if (action != null && action.equalsIgnoreCase("Make")) {
                            log("    Has 'Make' action - returning this widget");
                            return child;
                        }
                    }
                }
            }
        }

        log("  Could not find matching widget!");
        return null;
    }

    /**
     * Dump all widgets in the sawmill interface for debugging
     */
    private void dumpAllSawmillWidgets() {
        log("=== DUMPING ALL WIDGETS IN 270 ===");
        Widget parent = Widgets.getWidget(270);
        if (parent == null) {
            log("Widget 270 is NULL");
            return;
        }

        java.util.List<WidgetChild> children = parent.getChildren();
        if (children == null) {
            log("No children");
            return;
        }

        for (int i = 0; i < Math.min(children.size(), 30); i++) {
            WidgetChild child = children.get(i);
            if (child == null) continue;

            String text = child.getText();
            String name = child.getName();
            String[] actions = child.getActions();
            boolean visible = child.isVisible();

            StringBuilder sb = new StringBuilder();
            sb.append("  [").append(i).append("] ");
            sb.append("visible=").append(visible);
            if (text != null && !text.isEmpty()) sb.append(" text='").append(text).append("'");
            if (name != null && !name.isEmpty()) sb.append(" name='").append(name).append("'");
            if (actions != null && actions.length > 0) {
                sb.append(" actions=[");
                for (String a : actions) {
                    if (a != null && !a.isEmpty()) sb.append(a).append(",");
                }
                sb.append("]");
            }

            if (visible || (text != null && !text.isEmpty()) || (name != null && !name.isEmpty())) {
                log(sb.toString());
            }
        }
        log("=== END DUMP ===");
    }

    // ==================== GO TO NAVIGATION ====================

    /**
     * Process GO_TO navigation task
     * Uses DreamBot's WebWalker with configurable settings for teleports/shortcuts
     */
    private void processGoTo() {
        String destName = currentTask.getDestinationName();
        if (destName == null || destName.isEmpty()) {
            log("GO_TO: No destination set!");
            currentTask.setCompleted(true);
            return;
        }

        LocationData.Location destination = LocationData.Location.findByName(destName);
        if (destination == null) {
            log("GO_TO: Unknown destination: " + destName);
            currentTask.setCompleted(true);
            return;
        }

        Tile destTile = destination.getTile();
        double distance = destTile.distance();

        log("=== GO_TO NAVIGATION ===");
        log("Destination: " + destName);
        log("Tile: " + destTile.toString());
        log("Distance: " + String.format("%.1f", distance));
        log("Use Teleports: " + currentTask.shouldUseTeleports());
        log("Use Shortcuts: " + currentTask.shouldUseShortcuts());

        // Check if we've arrived
        if (distance < 10) {
            log(">>> ARRIVED at " + destName + "!");
            currentTask.setCompleted(true);
            return;
        }

        // Configure WebFinder settings based on task preferences
        configureNavigationSettings();

        // Check if we should walk
        if (Walking.shouldWalk()) {
            log("Walking to " + destName + "...");

            // Use DreamBot's built-in webwalker
            if (Walking.walk(destTile)) {
                log("Walk step successful, distance remaining: " + String.format("%.1f", destTile.distance()));
                Sleep.sleep(Calculations.random(600, 1200));
            } else {
                log("Walk failed, retrying...");
                Sleep.sleep(Calculations.random(300, 600));
            }
        } else {
            // Already walking, wait a bit
            Sleep.sleep(Calculations.random(200, 400));
        }

        // Check if run should be enabled
        if (!Walking.isRunEnabled() && Walking.getRunEnergy() > 30) {
            Walking.toggleRun();
            log("Enabled running");
        }
    }

    /**
     * Configure WebFinder settings based on current task preferences
     */
    private void configureNavigationSettings() {
        org.dreambot.api.methods.walking.pathfinding.impl.web.WebFinder webFinder =
                org.dreambot.api.methods.walking.pathfinding.impl.web.WebFinder.getWebFinder();

        if (webFinder == null) {
            log("WebFinder not available!");
            return;
        }

        // Configure teleport usage
        if (currentTask.shouldUseTeleports()) {
            webFinder.enableInventoryTeleports();
            webFinder.enableEquipmentTeleports();
            log("  Teleports: ENABLED");
        } else {
            webFinder.disableInventoryTeleports();
            webFinder.disableEquipmentTeleports();
            log("  Teleports: DISABLED");
        }

        // Configure shortcut usage (agility nodes)
        if (currentTask.shouldUseShortcuts()) {
            webFinder.enableWebNodeType(org.dreambot.api.methods.walking.web.node.WebNodeType.AGILITY_NODE);
            log("  Agility Shortcuts: ENABLED");
        } else {
            webFinder.disableWebNodeType(org.dreambot.api.methods.walking.web.node.WebNodeType.AGILITY_NODE);
            log("  Agility Shortcuts: DISABLED");
        }
    }

    /**
     * Check if sawmill interface is open
     */
    private boolean isSawmillInterfaceOpen() {
        Widget widget = Widgets.getWidget(270);
        return widget != null && widget.isVisible();
    }

    private void processFletching() {
        String materialName = getFletchMaterialName();
        String productName = getProductName();

        if (ItemProcessing.isOpen()) {
            itemsAtStart = Inventory.count(materialName);
            AntiBanUtil.microDelay();

            log("Fletching interface open. Making " + productName + "...");

            // Method 1: Try ItemProcessing.makeAll
            if (ItemProcessing.makeAll(productName)) {
                log("Started fletching via makeAll");
                isProcessing = true;
                lastAnimationTime = System.currentTimeMillis();
                Sleep.sleep(Calculations.random(600, 1000));
                return;
            }

            // Method 2: Press Space (universal confirm)
            log("makeAll failed, pressing Space to confirm...");
            Sleep.sleep(Calculations.random(200, 400));
            Keyboard.type(" ", false);
            isProcessing = true;
            lastAnimationTime = System.currentTimeMillis();
            Sleep.sleep(Calculations.random(600, 1000));
            return;
        }

        // Use feather on material
        if (Inventory.interact(FEATHER, "Use")) {
            Sleep.sleep(Calculations.random(200, 400));
            Inventory.interact(materialName, "Use");
            Sleep.sleepUntil(ItemProcessing::isOpen, Calculations.random(2000, 3000));
        }
    }

    private void processEnchanting() {
        // Check if enchant interface is open
        WidgetChild enchantWidget = Widgets.get(80, 0);
        if (enchantWidget != null && enchantWidget.isVisible()) {
            itemsAtStart = Inventory.count(currentTask.getItemType());
            AntiBanUtil.microDelay();

            // Click enchant option
            clickEnchantOption();
            isProcessing = true;
            lastAnimationTime = System.currentTimeMillis();
            log("Enchanting bolts...");
            Sleep.sleep(Calculations.random(600, 1000));
            return;
        }

        // Cast enchant spell
        if (!Tabs.isOpen(Tab.MAGIC)) {
            Tabs.open(Tab.MAGIC);
            Sleep.sleep(Calculations.random(300, 500));
            return;
        }

        AntiBanUtil.microDelay();
        Magic.castSpell(Normal.ENCHANT_CROSSBOW_BOLT);
        Sleep.sleepUntil(() -> {
            WidgetChild w = Widgets.get(80, 0);
            return w != null && w.isVisible();
        }, Calculations.random(2000, 3000));
    }

    // ==================== WAITING ====================

    private void handleWaiting() {
        boolean isAnimating = Players.getLocal().getAnimation() != -1;

        if (isAnimating) {
            lastAnimationTime = System.currentTimeMillis();
            return;
        }

        // Check if idle too long
        if (System.currentTimeMillis() - lastAnimationTime > IDLE_TIMEOUT || !hasMaterialsForTask()) {
            // Calculate items made
            int itemsMade = calculateItemsMade();
            totalItemsProcessed += itemsMade;
            currentTask.addItemsCompleted(itemsMade);

            log("Processed " + itemsMade + " items. Total: " + totalItemsProcessed);
            isProcessing = false;
        }
    }

    private int calculateItemsMade() {
        String inputName = getInputItemName();
        int inputsUsed = itemsAtStart - Inventory.count(inputName);
        int outputPerInput = getOutputPerInput();
        return Math.max(0, inputsUsed * outputPerInput);
    }

    // ==================== ALCHING ====================

    private void handleAlching() {
        String productName = getProductName();

        // Check if we have nature runes - if not, stop script
        if (!Inventory.contains(NATURE_RUNE)) {
            log("Out of Nature runes! Stopping script.");
            stop();
            return;
        }

        if (!Inventory.contains(productName)) {
            return;
        }

        if (!Tabs.isOpen(Tab.MAGIC)) {
            Tabs.open(Tab.MAGIC);
            Sleep.sleep(Calculations.random(200, 400));
            return;
        }

        AntiBanUtil.microDelay();

        if (Magic.castSpell(Normal.HIGH_LEVEL_ALCHEMY)) {
            Sleep.sleep(Calculations.random(80, 150));
            if (Inventory.interact(productName, "Cast")) {
                totalItemsAlched++;
                AntiBanUtil.repetitiveActionDelay();
            }
        }
    }

    // ==================== IRONMAN COLLECTION ====================

    private void handleCollecting() {
        if (currentTask == null || currentTask.getType() != TaskQueue.TaskType.IRON_COLLECTION) {
            return;
        }

        IronmanCollectorTask collectorTask = currentTask.getCollectorTask();
        if (collectorTask == null) {
            log("No collector task assigned!");
            currentTask.setCompleted(true);
            return;
        }

        // Check if already complete
        if (collectorTask.isComplete()) {
            log("Iron collection complete! Collected " + collectorTask.getTotalItemsCollected() + " items.");
            currentTask.setCompleted(true);
            return;
        }

        // Execute one loop of the collector
        int sleepTime = collectorTask.execute();

        if (sleepTime == -1) {
            // Collection complete
            log("Iron collection complete! Collected " + collectorTask.getTotalItemsCollected() + " items.");
            currentTask.setCompleted(true);
        } else {
            // Update progress tracking
            int collected = collectorTask.getTotalItemsCollected();
            currentTask.addItemsCompleted(Math.max(0, collected - currentTask.getItemsCompleted()));

            // Sleep for the returned time (but don't hold up the script loop too long)
            if (sleepTime > 0) {
                Sleep.sleep(Math.min(sleepTime, 600));
            }
        }
    }

    // ==================== TASK MANAGEMENT ====================

    private void handleTaskComplete() {
        log("Task complete: " + currentTask.getDisplayString());
        currentTask.setCompleted(true);

        if (taskQueue.nextTask()) {
            currentTask = taskQueue.getCurrentTask();
            log("Moving to next task: " + currentTask.getDisplayString());

            // Update location for new task
            updateLocationsForTask();
        } else {
            currentTask = null;
            log("All tasks completed!");
        }
    }

    // ==================== HELPER METHODS ====================

    private String getInputItemName() {
        switch (currentTask.getType()) {
            case SMITH_ITEM:
                return SmithingData.getBarName(currentTask.getMetalType());
            case SMELT_BAR:
                FurnaceData.SmeltableBar bar = FurnaceData.getBarByName(currentTask.getItemType());
                return bar != null ? bar.getPrimaryOre() : "";
            case CRAFT_JEWELRY:
                FurnaceData.JewelryItem item = FurnaceData.getJewelryByName(currentTask.getItemType());
                return item != null ? item.getBarType() : "";
            case MAKE_CANNONBALLS:
                return "Steel bar";
            case FLETCH_DARTS:
            case FLETCH_BOLTS:
                return getFletchMaterialName();
            case ENCHANT_BOLTS:
                return currentTask.getItemType();
            default:
                return "";
        }
    }

    private String getProductName() {
        switch (currentTask.getType()) {
            case SMITH_ITEM:
                return SmithingData.getItemName(currentTask.getMetalType(), currentTask.getItemType());
            case SMELT_BAR:
                FurnaceData.SmeltableBar bar = FurnaceData.getBarByName(currentTask.getItemType());
                return bar != null ? bar.getBarName() : "";
            case CRAFT_JEWELRY:
                return currentTask.getItemType();
            case MAKE_CANNONBALLS:
                return "Cannonball";
            case FLETCH_DARTS:
                return currentTask.getItemType().replace(" dart tip", " dart");
            case FLETCH_BOLTS:
                return currentTask.getItemType().replace(" bolts (unf)", " bolts");
            case ENCHANT_BOLTS:
                return currentTask.getItemType().replace(" bolts", " bolts (e)");
            default:
                return "";
        }
    }

    private String getFletchMaterialName() {
        if (currentTask.getType() == TaskQueue.TaskType.FLETCH_DARTS) {
            return currentTask.getItemType().replace(" dart", " dart tip");
        } else {
            return currentTask.getItemType().replace(" bolts", " bolts (unf)");
        }
    }

    private int getOutputPerInput() {
        switch (currentTask.getType()) {
            case SMITH_ITEM:
                return SmithingData.getQuantityProduced(currentTask.getItemType());
            case MAKE_CANNONBALLS:
                return 4;
            default:
                return 1;
        }
    }

    private boolean isSmeltingInterfaceOpen() {
        WidgetChild widget = Widgets.get(270, 0);
        return widget != null && widget.isVisible();
    }

    private void clickSmeltOption(FurnaceData.SmeltableBar bar) {
        // Widget 270 contains smelting options
        // Bar indices: Bronze=0, Iron=1, Silver=2, Steel=3, Gold=4, Mithril=5, Adamant=6, Runite=7
        int index = bar.ordinal();
        WidgetChild option = Widgets.get(270, 14 + index);
        if (option != null && option.isVisible()) {
            option.interact();
        }
    }

    private void clickEnchantOption() {
        String boltType = currentTask.getItemType().replace(" bolts", "");
        int childId;

        switch (boltType) {
            case "Opal": childId = 14; break;
            case "Sapphire": childId = 20; break;
            case "Jade": childId = 26; break;
            case "Pearl": childId = 32; break;
            case "Emerald": childId = 38; break;
            case "Topaz": childId = 44; break;
            case "Ruby": childId = 50; break;
            case "Diamond": childId = 56; break;
            case "Dragonstone": childId = 62; break;
            case "Onyx": childId = 68; break;
            default: childId = 14;
        }

        WidgetChild option = Widgets.get(80, childId);
        if (option != null && option.isVisible()) {
            option.interact();
        }
    }

    // ==================== PAINT ====================

    @Override
    public void onPaint(Graphics g) {
        if (!guiComplete) {
            g.setColor(Color.WHITE);
            g.drawString("Waiting for GUI...", 10, 30);
            return;
        }

        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background - made larger for TTL, ironman collection, and settings button
        g2d.setColor(new Color(0, 0, 0, 180));
        g2d.fillRoundRect(5, 5, 230, 280, 10, 10);
        g2d.setColor(new Color(255, 152, 0));
        g2d.drawRoundRect(5, 5, 230, 280, 10, 10);

        int y = 25;

        // Title
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.setColor(Color.ORANGE);
        g2d.drawString("Smith & Craft Pro v6.7", 15, y);
        y += 20;

        // Runtime
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        g2d.setColor(Color.WHITE);
        long runtime = System.currentTimeMillis() - startTime;
        g2d.drawString("Runtime: " + formatTime(runtime), 15, y);
        y += 16;

        // Current task
        g2d.setColor(Color.CYAN);
        String taskStr = currentTask != null ? currentTask.getType().getDisplayName() : "None";
        g2d.drawString("Task: " + taskStr, 15, y);
        y += 16;

        // Ironman Collection specific display
        if (currentTask != null && currentTask.getType() == TaskQueue.TaskType.IRON_COLLECTION) {
            IronmanCollectorTask collectorTask = currentTask.getCollectorTask();
            if (collectorTask != null) {
                g2d.setColor(new Color(0, 150, 136)); // Teal for ironman
                g2d.drawString(collectorTask.getProgressString(), 15, y);
                y += 16;
                g2d.setColor(Color.WHITE);
                g2d.drawString("Collected: " + collectorTask.getTotalItemsCollected() + " | Hops: " + collectorTask.getWorldHopCount(), 15, y);
                y += 16;
            }
        }

        // Queue progress
        g2d.setColor(Color.WHITE);
        g2d.drawString(taskQueue != null ? taskQueue.getProgressSummary() : "", 15, y);
        y += 16;

        // Items processed
        g2d.drawString("Processed: " + totalItemsProcessed, 15, y);
        y += 16;

        // Planks made (if applicable)
        if (totalPlanksMade > 0) {
            g2d.setColor(new Color(139, 90, 43));  // Brown color for wood
            g2d.drawString("Planks: " + totalPlanksMade + " | GP: " + formatNumber(totalCoinsSpent), 15, y);
            y += 16;
        }

        // Alched
        if (totalItemsAlched > 0) {
            g2d.setColor(Color.WHITE);
            g2d.drawString("Alched: " + totalItemsAlched, 15, y);
            y += 16;
        }

        // XP gained and Time to Level
        int smithXP = Skills.getExperience(Skill.SMITHING) - startSmithingXP;
        int craftXP = Skills.getExperience(Skill.CRAFTING) - startCraftingXP;
        int magicXP = Skills.getExperience(Skill.MAGIC) - startMagicXP;
        int fletchXP = Skills.getExperience(Skill.FLETCHING) - startFletchingXP;

        g2d.setColor(Color.YELLOW);

        // Determine primary skill and show TTL
        Skill primarySkill = null;
        int primaryXP = 0;

        if (smithXP > craftXP && smithXP > fletchXP) {
            primarySkill = Skill.SMITHING;
            primaryXP = smithXP;
            g2d.drawString("Smith XP: " + formatNumber(smithXP), 15, y);
        } else if (craftXP > smithXP && craftXP > fletchXP) {
            primarySkill = Skill.CRAFTING;
            primaryXP = craftXP;
            g2d.drawString("Craft XP: " + formatNumber(craftXP), 15, y);
        } else if (fletchXP > 0) {
            primarySkill = Skill.FLETCHING;
            primaryXP = fletchXP;
            g2d.drawString("Fletch XP: " + formatNumber(fletchXP), 15, y);
        }
        y += 16;

        // Time to Level
        if (primarySkill != null && primaryXP > 0) {
            g2d.setColor(Color.GREEN);
            long ttl = SkillTracker.getTimeToLevel(primarySkill);
            g2d.drawString("TTL: " + formatTime(ttl), 15, y);
            y += 16;
        }

        // Magic XP (separate since it's from alching)
        if (magicXP > 0) {
            g2d.setColor(Color.CYAN);
            g2d.drawString("Magic XP: " + formatNumber(magicXP), 15, y);
            y += 16;
        }

        // Fatigue bar
        y += 5;
        drawFatigueBar(g2d, 15, y);

        // Settings button (for on-the-fly editing)
        drawSettingsButton(g2d);
    }

    private void drawFatigueBar(Graphics2D g2d, int x, int y) {
        double fatigue = AntiBanUtil.getFatigueLevel();
        int barWidth = 100;
        int filledWidth = (int) (barWidth * fatigue);

        g2d.setColor(Color.LIGHT_GRAY);
        g2d.drawString("Fatigue:", x, y);

        g2d.setColor(Color.DARK_GRAY);
        g2d.fillRect(x + 55, y - 10, barWidth, 12);

        if (fatigue < 0.3) g2d.setColor(Color.GREEN);
        else if (fatigue < 0.6) g2d.setColor(Color.YELLOW);
        else g2d.setColor(Color.RED);

        g2d.fillRect(x + 55, y - 10, filledWidth, 12);
        g2d.setColor(Color.WHITE);
        g2d.drawRect(x + 55, y - 10, barWidth, 12);
    }

    /**
     * Draw the settings button on the paint overlay
     */
    private void drawSettingsButton(Graphics2D g2d) {
        // Button background
        g2d.setColor(new Color(0, 150, 136));
        g2d.fillRoundRect(SETTINGS_BUTTON.x, SETTINGS_BUTTON.y,
                SETTINGS_BUTTON.width, SETTINGS_BUTTON.height, 5, 5);

        // Button border
        g2d.setColor(new Color(0, 180, 160));
        g2d.drawRoundRect(SETTINGS_BUTTON.x, SETTINGS_BUTTON.y,
                SETTINGS_BUTTON.width, SETTINGS_BUTTON.height, 5, 5);

        // Button text
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 11));
        FontMetrics fm = g2d.getFontMetrics();
        String text = "⚙ Settings";
        int textX = SETTINGS_BUTTON.x + (SETTINGS_BUTTON.width - fm.stringWidth(text)) / 2;
        int textY = SETTINGS_BUTTON.y + ((SETTINGS_BUTTON.height - fm.getHeight()) / 2) + fm.getAscent();
        g2d.drawString(text, textX, textY);
    }

    private String formatTime(long ms) {
        long sec = ms / 1000;
        return String.format("%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60);
    }

    private String formatNumber(int num) {
        if (num >= 1000000) return String.format("%.1fM", num / 1000000.0);
        if (num >= 1000) return String.format("%.1fK", num / 1000.0);
        return String.valueOf(num);
    }

    @Override
    public void onExit() {
        log("=== Session Summary ===");
        log("Total processed: " + totalItemsProcessed);
        log("Total alched: " + totalItemsAlched);
        log("Session: " + AntiBanUtil.getSessionMinutes() + " minutes");

        // Dispose GUI on exit
        if (gui != null) {
            gui.dispose();
        }
    }

    // ==================== MOUSE LISTENER ====================

    @Override
    public void mouseClicked(MouseEvent e) {
        if (guiComplete && gui != null) {
            Point p = e.getPoint();
            if (SETTINGS_BUTTON.contains(p)) {
                // Toggle GUI visibility
                gui.toggleVisibility();
                e.consume();
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {}

    @Override
    public void mouseReleased(MouseEvent e) {}

    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {}
}
