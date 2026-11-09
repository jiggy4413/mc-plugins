# mc-plugins

Custom plugins for a Paper 1.16.5 prison server. Java 8.

## Download
Every push builds automatically. Get the newest jar from **Releases → Latest build** on the repo page.

## Modules
| Module | Jar | Purpose |
|---|---|---|
| `prison-core` | `PrisonCore-1.2.0.jar` | Ranks & prestiges menu (more features to come) |
| `stubs/rankup-api` | none | Compile-only copy of Rankup 3.11.3's API. Never installed on the server. |

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

## Updating PrisonCore without a restart
1. Download the new jar from **Releases → Latest build**.
2. Upload it to the server's `plugins/` folder. Leave the old jar there; it gets deleted for you.
3. Run `/prisoncore reload`. It finds the newer jar, unloads the old version, deletes the old jar and loads the new one.

With no new jar present, `/prisoncore reload` just reloads `ranks-menu.yml`.
If a live update ever fails, restarting the server finishes loading the new version.

## Build locally
In IntelliJ: open the **Maven** tab on the right → `mc-plugins` → **Lifecycle** → double-click **package**.
The jar lands in `prison-core/target/`.
