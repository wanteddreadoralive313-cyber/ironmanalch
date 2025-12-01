# OSRS Mega All-in-One DreamBot Script – Project Plan

## 1. Overview

### Vision
Build a modular, extensible, all-in-one OSRS bot framework on DreamBot that prioritizes ban safety over efficiency, supports all major activities, and delivers consistent behavior via unified engines.

### Goals
- **Ban safety first:** human-like timing, mistakes, breaks, and variety.
- **Modular architecture:** clean separation between core engines and activity modules.
- **Account-centric behavior:** per-account profiles, risk, schedules, and task queues.
- **Maintainable codebase:** clear interfaces, logging, and testing hooks.

### Out of Scope (initially)
- Multi-client orchestration.
- Highly reactive PvP/PvM bossing.
- Custom AI pathfinding beyond DreamBot.

## 2. Architecture

### Layers
1. **MegaScript (entry point)**
2. **Core Engines:** TaskEngine, AntiBanEngine, ProfileEngine, SessionManager, RecoveryManager
3. **Shared Systems:** NavigationManager, BankingManager, Inventory/Gear Manager, GrandExchangeManager (later), RequirementChecker
4. **Task Modules:** Woodcutting, Fishing, Mining, Smithing, Fletching, Crafting, Firemaking, Cooking, Agility, Combat, Slayer, Questing, Moneymaking, Utility

## 3. Core Engines & Systems

### 3.1 Task Model & TaskEngine
- `Task` interface with lifecycle (`onStart`, `tick`, `onStop`), readiness/completion checks, goal/risk metadata, and status enum.
- TaskEngine responsibilities:
  - Manage per-account task queue and dependencies.
  - Select next task based on readiness, risk, and variety rules.
  - Enforce rotation (e.g., break/idle tasks after long sessions).
  - Handle transitions with logging.

### 3.2 AntiBanEngine
- **BehaviorProfile** per account (reaction times, camera/mouse habits, session and crowd sensitivity).
- **Action API** wrapping all interactions (`interactWith` object/NPC, inventory clicks, `humanSleep`).
- **Timed human behaviors:** periodic camera moves, XP tab checks, mouse-offscreen idles, hovering, misclicks.
- Configurable via profile presets (Cautious, Normal, Aggressive).

### 3.3 SessionManager
- Per-account SessionPlan: daily max runtime, continuous session ranges, break durations, active hours.
- Randomized session/break timings; can pause tasks or log out.
- Hooks for `shouldTakeBreak` / `shouldEndForToday` in script loop.

### 3.4 NavigationManager
- High-level travel API (`goToBank`, `goToArea`, `goToTaskLocation`).
- Teleport preference with fallback walking.
- Retry/backoff and stuck escalation to RecoveryManager.

### 3.5 Banking & Inventory/Gear
- `TaskRequirements` to describe needed items/quantities.
- BankingManager withdraws/deposits with slight quantity randomness.
- InventoryManager utilities (counts, clean-up with human delays).
- GearManager presets (Melee/Ranged/Magic) for combat tasks.

### 3.6 GrandExchangeManager (Phase 3+)
- Buy/sell with price buffers and randomized bids.
- Asynchronous handling: tasks can `PAUSE` while waiting for orders; TaskEngine can switch to low-intensity tasks.

### 3.7 RecoveryManager
- Death detection (respawn tiles, HP resets) and recovery (walk-back if safe, otherwise fail/complete task).
- Stuck detection (no tile change, repeated path failures) and recovery (re-path, hop, teleport home, or stop).

## 4. Task Modules

### Module Pattern
- Each module provides Task implementations (e.g., `WoodcuttingTask` with config for target level/quantity/time, location, log types, risk profile).
- Modules use NavigationManager, BankingManager, AntiBanEngine; no direct DreamBot sleep/mouse/camera calls.

### Recommended Rollout
1. **Phase 1:** Framework integration; wrap existing smith/allch logic into `SmithingTask` via TaskEngine.
2. **Phase 2:** AFK-ish skills – Woodcutting, Fishing, Mining, Cooking, Firemaking.
3. **Phase 3:** Production skills – generalized Smithing, Fletching, Crafting; add GrandExchangeManager.
4. **Phase 4:** Utility & progression – QuestModule (few safe quests), TravelUnlock, Diary basics.
5. **Phase 5:** Combat & Slayer – safe training spots; Slayer tasks.
6. **Phase 6:** Optimization & polish – profile tuning, GUI improvements, expanded content.

## 5. Data & Configuration
- JSON/YAML per-account: BehaviorProfile, SessionPlan, task queue, enabled modules, max risk.
- Global data: location definitions (banks, task areas), item tables.
- Example account profile: includes behavior profile name, session plan, max risk, enabled modules, and ordered task queue.

## 6. Logging & Testing
- Logging: session start/end, tasks and durations, XP gains, actions/hour, anti-ban metrics (reaction times, misclicks, camera moves, breaks, hops), deaths/stuck events.
- Testing strategy:
  - Unit-level for requirements/goal logic and path/selection helpers.
  - In-client functional shakedowns (10–30 minutes per module) for obvious loops/stuck states.
  - Soak tests within safe durations to validate RecoveryManager and break logic.
  - Staged rollout with beta flags per new module.

## 7. Coding Standards
- Descriptive names; modules kept small via helpers.
- All interactions go through AntiBanEngine and shared managers (no direct DreamBot sleeps/clicks/camera).
- Clear stop conditions; failures should not loop indefinitely.
- Maintain script version and simple changelog; include version in logs.

## 8. Acceptance Criteria (MVP Platform)
- MegaScript runs through TaskEngine.
- AntiBanEngine used for all interactions with configurable profiles.
- SessionManager enforces breaks and daily limits.
- NavigationManager/BankingManager handle all travel/banking.
- At least three integrated modules (e.g., Smithing, Woodcutting, Fishing) with level/quantity goals and safe stops.
- Logging includes task durations, XP, anti-ban metrics.
- RecoveryManager covers death/stuck scenarios.

## 9. Next Steps
- Provide class diagrams and pseudo-code for one module (e.g., Woodcutting) as a template for future modules.
