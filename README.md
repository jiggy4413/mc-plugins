# mc-plugins

Custom plugins for a Paper 1.16.5 prison server. Java 8.

## Modules
| Module | Jar | Purpose |
|---|---|---|
| `prison-core` | `PrisonCore-1.0.0.jar` | Starter plugin; will hold shared code (GUIs, config, hooks) |

Planned: custom `/ranks` GUI, ore golem spawners, custom crate tools and weapons.

## Build
In IntelliJ: open the **Maven** tab on the right → `mc-plugins` → **Lifecycle** → double-click **package**.

Jars land in each module's `target/` folder, e.g. `prison-core/target/PrisonCore-1.0.0.jar`.
Upload the jar to the server's `plugins/` folder and restart.
