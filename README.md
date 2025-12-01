# OSRS Mega AIO DreamBot

Initial build of a modular all-in-one DreamBot framework following the project plan in `docs/OSRS_Mega_AIO_DreamBot_Plan.md`. The script centers around a task engine, anti-ban behaviors, and shared managers so activity modules can plug in cleanly.

## Components
- **Task Engine** – Simple lifecycle that rotates through ready tasks and stops when goals are met.
- **Managers** – Anti-ban, navigation, banking, session/break handling, and recovery utilities to keep interactions human-like.
- **Modules** – Example smithing module that forges platebodies and an idle/break task to demonstrate rotation.

## Entry Script
`MegaAioScript` wires the managers together and seeds the task list (mithril platebodies in Varrock West, then a timed idle). Update the task list to point at different locations or modules as they come online.

## Notes
- All interactions rely on DreamBot APIs (`Smithing`, `Bank`, `Walking`, etc.) and the anti-ban engine for reaction times and camera/mouse behaviors.
- Session planning enforces micro-breaks and daily runtime limits so tasks stop safely.

Future work will add additional skilling modules, requirement checking, and persistence for per-account profiles.
