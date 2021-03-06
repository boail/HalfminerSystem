# Halfminer Battle
Battle Arena Bukkit/Spigot plugin with multiple battle modes.

Current features
-------
- Full battle arena solution allowing easy addition of new arena battle modes
- Seamless integration into existing Survival PvP worlds
- Optimized for performance, battle tested and generally robust
- Localization configurable, commands are clickable
- **Battle Modes**
  - Global functionality
    - Endless amount of arenas
    - Custom kits per arena
      - Adds item lore to kits to easily identify possibly extracted items from a badly secured arena
      - Can toggle inventory store to disk, to prevent any inventory data loss from ever ocurring
        - Command */hmb openinventory [filename]* allows retrieval of said items by file name
          - Add -r flag to automatically restore while player is online
        - Automatically cleaning up old files
      - Must assign kit first to activate arena, empty kit can be set via */hmb setkit mode arenaname -e*
    - Recovers players completely after fight (position, health/status, inventory if kit was used, potion effects)
      - Sets gamemode to adventure during fight
      - If a player has received non battle drops during battle, the items will be removed during battle and restored after
    - Allows damage dealing even if hit is being cancelled by other plugins, for example due to fighting a clan member
    - Prevents teleporting into arenas via teleport delay glitches (Essentials */tpa*)
      - Prevents teleport of tameable mobs, such as wolves, into arenas
    - Games can be force ended with */hmb forceend*
    - Configurable custom permissions, that will be added during arena stay (useful for bypasses for other plugins)
      - Individual permissions per battle mode
    - Disables while in battle:
      - Hunger loss in duel (*optional*)
      - Item dropping/pickup (dropped items will be deleted if not using own equipment)
      - Command usage
      - Opening of crafting tables, anvils etc.
      - Crafting in general
      - Entering a bed
  - Duel mode
    - Robust queue system
      - Not using a per arena queue, if all arenas are in use the next battle automatically starts once one becomes vacant
      - Kicks player from queue
        - on disconnect
        - when engaging in PvP outside of arena
        - when entering a bed, since it prevents teleportation
      - Cooldown after leaving queue
    - Duelling per request (*/duel playername*) or via automatic matching (*/duel match*)
      - Players can request duel with own equipment, use */duel playername nokit*, must be accepted with additional ``nokit`` parameter
      - When waiting too long for match will broadcast that a player is waiting (*configurable*)
      - Will start duel if a player duel requests a player that is waiting for a match
    - Dynamic arena selection system, only shows vacant arenas
      - Randomly selects player to choose the map
        - Sends message to non selecting player if selecting player chose the *Random* option
    - Kills player if he logs out during battle and ensures that opponent gets the kill counted
    - Shows current arena status with */duel list*
    - Countdown before game start
    - Set maximum game time in config
  - FFA mode
    - Teleport cooldown before teleporting into and out of arena
      - Players can select which arena to enter
    - Custom kit in arena
    - Players in same arena are colored in tablist
      - Use */ffa list* to see all arenas and currently playing players
      - Players glow while in arena (*configurable*)
    - Custom killstreaks
      - Define what should happen at which streak in ``customactions.txt``
        - Supports commands, custom item drops, messaging (see HalfminerSystem ``CustomAction`` functionality)
      - Kill (and death) streaks shown in scoreboard
    - Logging out causes immediate death (or counts kill towards last hitting player)
    - Players get kicked from arena after configurable streak of deaths
      - They will remain banned for configured amount of time
    - Auto respawn in arena while keeping items
      - Players get PvP protected for 5 seconds after (re-)spawning
    - Arena can be left via command */ffa leave*
    - Arena selection screen (only shown when more than one arenas are available) shows players currently in arena
    