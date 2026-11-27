# mc-plugins

Custom plugins for **Classic Prison**, a Paper 1.16.5 (Java 8) prison server.
The main plugin is **PrisonCore**: the ranks & prestiges menu, the warps menu, percentage-based
mine resets and void worlds. Every push to `main` is built by GitHub Actions and published as
the "Latest build" release. See README.md for commands and setup.

## Things to keep in mind when changing these plugins
- Branch `paper-1.21` is the Paper 1.21.4 / Java 21 port (PrisonCore 2.x), published as the "latest-1.21"
  pre-release. `main` is still the 1.16.5 / Java 8 version (1.19.x). The server is moving to 1.21.4.
- Mine names on the server: A-K are `amine`, `bmine` ... `kmine`; L-Z are just the letter: `l`, `m` ... `z`.
  Plus `woodmine` and `pvpmine`. Never rename them or assume `lmine`-`zmine`.
- Special item names in the server's items.yml have no dashes (easier to type in game): armor is
  `winterhelmet`, `winterchestplate`, `winterleggings`, `winterboots` (same for valentines/summer), weapons are
  `frostsword`, `frostaxe`, `frostbow`, `frostscythe`.
- `prison-core/src/main/resources/warps-menu.yml` is the server's own warps config, copied exactly
  (CRLF line endings). Keep it as is in future builds unless asked for a change.
- The `title:` and `lines:` in `prison-core/src/main/resources/scoreboard.yml` are the server's own
  scoreboard layout. Keep them exactly as they are unless asked for a change.
- Never put real keys or tokens (Tebex public token, Tebex secret key, or any other credential)
  in the repo or a build. Config files ship with them empty (`public-token: ""`); they're only
  filled in on the server.
- Commits are authored as `jiggy4413 <118291312+jiggy4413@users.noreply.github.com>`
  (set with `git config user.name` / `git config user.email` in a fresh clone), with Claude as co-author.
- Custom armor plan for the 1.21.4 resource pack (decided Nov 2026, not built yet):
  - ALL custom armor sets are NETHERITE items with their own worn texture (1.21.4 equipment assets),
    not dyed leather. Vanilla leather/chainmail must look normal again.
  - Winter Nova stays the Nova design. Valentines and Summer are NOT Nova anymore: brand-new designs.
  - The Agamemnon (Warlord) black/gold set with the custom helmet is part of the plan; concepts are in
    `resource-pack/concepts/`.
  - Frost weapons keep their textures/models, converted to the 1.21.4 item model format.
