# mc-plugins

Custom plugins for a Paper 1.16.5 prison server. Java 8.

## Download
Every push builds automatically. Get the newest jar from **Releases → Latest build** on the repo page.

## Modules
| Module | Jar | Purpose |
|---|---|---|
| `prison-core` | `PrisonCore-1.17.2.jar` | Ranks & prestiges menu, warps menu, mines with percentage resets, void worlds |
| `stubs/rankup-api` | none | Compile-only copy of Rankup 3.11.3's API. Never installed on the server. |
| `stubs/placeholderapi` | none | Compile-only copy of PlaceholderAPI's expansion API. Never installed on the server. |

## PrisonCore: ranks & prestiges menu
Replaces `/ranks` and `/prestiges` from **Rankup 3.11.3 (by Okx)** with a fully styled menu.
Rankup still does the actual rankups, costs and permissions; clicking the next rank runs `/rankup`
and clicking the next prestige runs `/prestige` for the player.

- **Config:** `plugins/PrisonCore/ranks-menu.yml` (created on first start, every option is commented)
- **Apply config changes:** `/prisoncore reload` (permission `prisoncore.admin`, ops by default)
- **Open the menu:** `/ranks`, `/prestiges`, or `/prisonranks [prestiges]` (permission `prisoncore.ranks`, everyone by default)
- **Formatting:** `&` codes, hex `&#FF8800`, `<gradient:#FF0000:#FFD700>text</gradient>`, `<smallcaps>text</smallcaps>`, and PlaceholderAPI placeholders
- **Per-rank styling:** add the rank's name under `ranks:` in the config to give it its own name, material, glow, texture or lore
- To give `/ranks` back to Rankup, remove `ranks` from `open-commands` and reload

Requires Rankup. If Rankup is missing, PrisonCore leaves `/ranks` alone.

## Mines
PrisonCore resets mines when they're a set percentage mined (default 75%), with an optional timer.
Resets refill a few milliseconds' worth of blocks per tick, one mine at a time, so big mines don't lag.

- **Config:** `plugins/PrisonCore/mines.yml` (mines A-Z included; documentation at the top)
- **Set up a mine:** `/pmine pos1`, `/pmine pos2` (look at opposite corners), then `/pmine setregion <mine>`
- **Copy from MineResetLite:** `/pmine import`, then remove MineResetLite's jar so it stops resetting too
- **Other commands:** `/pmine list`, `info`, `setspawn`, `reset` (permission `prisoncore.mines.admin`)
- **Display:** a boss bar near each mine, plus PlaceholderAPI placeholders like `%prisoncore_mine_a_mined%`

## Warps menu
`/warps` opens a main page (mines, crates, spawn, black market, fishing...). Clicking **Mines** opens a
second page with every mine and its live stats (% mined, status, reset timer).
EssentialsX still does the teleport: clicking a warp runs `/warp <name>` for the player, so its
permissions, delay and cooldowns still apply.

- **Config:** `plugins/PrisonCore/warps-menu.yml`. Pages live under `menus:`; an entry with `open: <page>` opens that page
- **Open the menu:** `/warps`, `/warp` with no name, or `/prisonwarps` (permission `prisoncore.warps`, everyone by default)
- **Locked mines:** shown as iron bars with the rank needed. With Essentials' `per-warp-permission` on,
  a warp is locked until the player has `essentials.warps.<name>`; otherwise until they reach its rank
- **Warp names:** lettered mines find their Essentials warp automatically (`a`, `amine`, `a-mine`, `mine_a`...)
- **Check the setup:** `/prisonwarps check` lists which warp, permission and rank each menu entry uses

## Help
`/help` (and `/?`) shows a short, clickable list of the commands players actually need, instead
of the server's list of every command. Commands with a permission only show to players who have it.

- **Config:** `plugins/PrisonCore/help.yml`
- **Staff:** `/help all` shows the server's full list (permission `prisoncore.help.all`, ops by default)

## Scoreboard
A flicker-free panel on the right of the screen: server name, player, rank, prestige, balance, tokens and a vote reminder.

- **Config:** `plugins/PrisonCore/scoreboard.yml` (placeholders `{player}`, `{rank}`, `{balance}`, `{tokens}`... plus PlaceholderAPI)
- **Hide/show your own:** `/board` (or `/board on`, `/board off`)

## Tokens
A second currency found while mining (2% of blocks by default, more in higher mines).

- **Config:** `plugins/PrisonCore/tokens.yml`; balances are saved in `plugins/PrisonCore/data/tokens.yml`
- **Players:** `/tokens`, `/tokens <player>`, `/tokens pay <player> <amount>`, `/tokens top`
- **Staff/console:** `/tokens give|take|set <player> <amount>` (permission `prisoncore.tokens.admin`)
- **Placeholders:** `%prisoncore_tokens%`, `%prisoncore_tokens_formatted%`, `%prisoncore_tokens_short%`

## Enchants
Right-clicking an enchanting table (or `/enchanter`) opens the Enchanter: pickaxe, sword and armor,
with the player's token and PvP point balances. Pickaxe enchants cost tokens; sword and armor
enchants cost PvP points (1 per kill, no points for killing the same player again within 5 minutes).

- **Pickaxe:** Efficiency (to 10), Fortune, Unbreaking, Mending, plus Token Finder, Explosive (3x3x3 in mines), Haste and Key Finder (crate keys, with a cooldown and daily limit)
- **Sword:** Sharpness (to 6), Fire Aspect, Knockback, Looting, Unbreaking, Mending
- **Armor:** Protection, Fire Protection, Blast Protection (all to 6), Thorns, Feather Falling (boots), Aqua Affinity (helmet), Unbreaking, Mending
- **Special items:** define crate prizes in `plugins/PrisonCore/items.yml` (material, name, lore, enchants) and hand them out with `/prisoncore give <player> <item>`. Use that as a SpecializedCrates command reward so items are built fresh with real enchants.
- **Crate-only** (`in-menu: false`, for special items or `/enchanter apply`): Money Finder, XP Boost, Lifesteal, Bleed, Freeze, Executioner, Thunder, Bounty Hunter, Head Hunter, Dodge (chestplate), Last Stand (leggings), Speed (boots)
- **Config:** `plugins/PrisonCore/enchants.yml`: every level's price is listed (`costs:`), and the number of prices is the max level; PvP point settings in `tokens.yml`
- **Staff:** `/enchanter apply <enchant> <level> [player]` puts any enchant (Token Finder, Explosive, Haste too) on the held item for free, for kits and crate rewards; `/enchanter remove <enchant> [player]` takes one off (permission `prisoncore.enchants.admin`)
- **PvP points:** `/pvppoints [player]`, staff `/pvppoints give|take|set <player> <amount>`, placeholder `%prisoncore_pvppoints%`

## Store menu
`/buy` (and `/store`) opens a decorated store menu in place of Tebex's: a main page with Ranks,
Crate Keys and Perks, each opening its own page. Clicking a package sends a clickable link to it
on the Tebex store, where players pay as usual.

- **Config:** `plugins/PrisonCore/store.yml` (store address, package IDs, icons, border colors)
- **Live prices:** set `tebex.public-token` and give each item its `package:` ID; prices (and sales) load from Tebex every 10 minutes, or now with `/prisonstore refresh`

## Void worlds
PrisonCore includes an empty-world generator (one bedrock block under spawn at 0, 64, 0):
```
/mv create <name> normal -g PrisonCore
```

## Updating PrisonCore without a restart
1. Download the new jar from **Releases → Latest build**.
2. Upload it to the server's `plugins/` folder. Leave the old jar there; it gets deleted for you.
3. Run `/prisoncore reload`. It finds the newer jar, unloads the old version, deletes the old jar and loads the new one.

With no new jar present, `/prisoncore reload` just reloads `ranks-menu.yml`.
If a live update ever fails, restarting the server finishes loading the new version.

## Build locally
In IntelliJ: open the **Maven** tab on the right → `mc-plugins` → **Lifecycle** → double-click **package**.
The jar lands in `prison-core/target/`.
