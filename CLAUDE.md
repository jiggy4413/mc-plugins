# mc-plugins

Custom plugins for **Classic Prison**, a Paper 1.16.5 (Java 8) prison server.
The main plugin is **PrisonCore**: the ranks & prestiges menu, the warps menu, percentage-based
mine resets and void worlds. Every push to `main` is built by GitHub Actions and published as
the "Latest build" release. See README.md for commands and setup.

## Things to keep in mind when changing these plugins
- Branch `paper-1.21` is the Paper 1.21.4 / Java 21 port (PrisonCore 2.x), published as the "latest-1.21"
  pre-release. In Nov 2026 `main` was fast-forwarded to it (the server owner wanted the work on the default
  branch so it shows in his GitHub contributions), so `main` is now 1.21.4 too and its "Latest build" release
  has the 2.x jars. Push new work to `paper-1.21` and to `main` (they stay identical). The last 1.16.5 / Java 8
  commit is e4214a6 (PrisonCore 1.19.x).
- Mine names on the server: A-K are `amine`, `bmine` ... `kmine`; L-Z are just the letter: `l`, `m` ... `z`.
  Plus `woodmine` and `pvpmine`. Never rename them or assume `lmine`-`zmine`.
- Special item names in the server's items.yml have no dashes (easier to type in game): armor is
  `winterhelmet`, `winterchestplate`, `winterleggings`, `winterboots` (same for goldwinter/thornheart/goldthornheart/cupid/goldcupid/agamemnon/tidecaller; items.yml `set:` ties them into a set whose
  armor enchants only work as a full set), weapons are
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
- The 1.21.4 resource pack is generated: `python3 resource-pack/build_pack.py` -> `resource-pack/dist/ClassicPrison-1.21.4.zip`
  (+ .sha1). Armor set items: `resource-pack/armor-sets-items.yml` (items.yml `item-model` / `equipment` fields,
  PrisonCore 2.3.0+). Follow the server's items.yml layout for new sets: a `# ---- Set Name ---- #` header,
  then helmet/chestplate/leggings/boots, each with material, item-model, equipment, set, show-attributes,
  unbreakable, name, lore (and enchants only for real set bonuses). Two-space indent under `items:`. The old `classic-prison-pack/` folder is the 1.16 pack (still the source of the frost weapon art).
- Custom armor plan for the 1.21.4 resource pack (decided Nov 2026):
  - ALL custom armor sets are NETHERITE items with their own worn texture (1.21.4 equipment assets),
    not dyed leather. Vanilla leather/chainmail must look normal again.
  - Winter Nova stays the Nova design. Valentines and Summer are NOT Nova anymore: brand-new designs:
    **Thornheart** (Valentines, final design) and **Tidecaller** (Summer), in `resource-pack/concepts/new-sets/`.
  - Crate set rotation: Winter Nova until Feb 13 -> Thornheart from Feb 14 -> Agamemnon Mar 1 to May 1
    -> Tidecaller from May 1. Cadence: a new custom set every two months, plus occasional extra-limited
    sets (1-2 weeks, like Thornheart).
  - Concept art rules: chestplate sleeves stop at the shoulder (like vanilla); always fill the bottom faces
    of legs/boots; plates should read as metal armor (no brick seams or knit patterns).
  - The Agamemnon (Warlord) black/gold set with the custom helmet is part of the plan; concepts are in
    `resource-pack/concepts/`.
  - Frost weapons keep their textures/models, converted to the 1.21.4 item model format.
- Valentines collection (decided Nov 2026): two sets, Thornheart (dark) and Cupid (light), each with a gold
  version. Per set: 4 armor + 7 tools (sword, axe, spear (melee, netherite sword), scythe, pickaxe, bow, shield)
  + 3 back blings, so 56 pieces with gold. Items in `resource-pack/valentines-items.yml` (names like `cupidspear`,
  `goldthornheartshield`), tools use `item-model: classicprison:<name>`. Set bonuses on the chestplate:
  Thornheart Thornguard I/II (10%/20% melee damage back), Cupid Absorption I/II (hearts refill 10 s after last hit).
  Gold versions: vanilla gold palette, red roses/hearts stay red, Gold Thornheart plate bright + vines darker
  (`concepts/goldify.py`). Art: `concepts/weapons/` (tools, back blings), `concepts/new-sets/cupid.py` (armor),
  full sheet `concepts/valentines-collection.png`.
- Custom weapon enchants (rule from the server owner): every weapon enchant has 5 levels. Regular crate
  weapons roll level I-IV at random each time they're given (items.yml `enchants: {id: {1: 40, 2: 30, 3: 20,
  4: 10}}`, like the frost weapons); gold versions always get level V.
- Soulbound (PrisonCore 2.9.0, `soulbound.yml`): Soulbound Scrolls (`/prisoncore soulbound <player> [amount]`)
  are clicked onto a weapon/tool/armor piece; that item survives the owner's next death, then loses the
  protection (one save, the server owner's choice). Pack icon `classicprison:soulbound_scroll`.
