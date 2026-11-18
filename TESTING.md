# Testing Frame Stash yourself

There are three ways to test, from quickest to most realistic. A test datapack (`docs/testing/frame-stash-test.zip`) builds a labelled test room and fills your inventory, so every check below takes a few seconds.

## 1. Singleplayer in your normal launcher

1. Build the jars: `./gradlew build`. They land in `fabric/build/libs` and `neoforge/build/libs`. Use the file without `-sources`.
2. Install a loader for **Minecraft 1.21.10** in your launcher (CurseForge app, Prism, Modrinth app or the vanilla launcher):
   - **Fabric:** Fabric Loader 0.17.3 or newer, plus [Fabric API](https://modrinth.com/mod/fabric-api) 0.138.4+1.21.10 in `mods`. Then add `frame-stash-fabric-1.21.10-1.0.0.jar`.
   - **NeoForge:** NeoForge 21.10.64 or newer. Then add `frame-stash-neoforge-1.21.10-1.0.0.jar`.
3. Create a new world with **Allow Commands** on. Under **Data Packs**, drag in `docs/testing/frame-stash-test.zip`.
4. In game: `/function framestash_test:help`, then follow the steps it prints.

## 2. Dedicated server with a vanilla client (the mod's main promise)

1. Start a dev server: `./gradlew :fabric:runServer` or `./gradlew :neoforge:runServer`. The EULA is already accepted, and the test datapack is already installed in both dev worlds.
2. Open your normal launcher, pick plain **vanilla 1.21.10** with no mods, then go to **Multiplayer**, **Direct Connection**, `localhost`.
3. In the server console, type `op <your name>` so you can run `/function`.
4. Run `/function framestash_test:setup` and continue with the checklist below.

> The dev servers run with `online-mode=false`, so the dev client (below) can join too. That's fine on your own PC. Don't port-forward a dev server.

You can also join with the dev client: `./gradlew :fabric:runClient`, then Multiplayer, `localhost`.

## 3. Automated tests

```sh
./gradlew build                     # unit tests + in-world GameTests (Fabric)
./gradlew :fabric:test              # planner, config, big-stack and item-totals tests
./gradlew :fabric:runGameTest       # 11 in-world tests with real chests, frames and a mock player
./gradlew :fabric:runClientGameTest # retakes the mod-page screenshots into docs/raw (opens a window)
python docs/tools/make_images.py    # turns docs/raw into the edited images in docs/images
```

## What the test room checks

Stand on flat ground facing north and run `/function framestash_test:setup`. Containers appear 4 blocks in front of you, each with a floating label. Then run `/function framestash_test:items` and `/stash`.

| Spot (left to right) | What should happen after `/stash` |
| --- | --- |
| Test 1: three chests | Stone, iron ingots and oak logs each land in their own chest |
| Test 2: double chest | The frame is on one half, but cobblestone goes into the whole double chest |
| Test 3: barrel and shulker box | The barrel takes redstone. The shulker box takes glowstone dust but **not** the shulker box item, which stays with you |
| Test 4: glow frame | The stone chest from test 1 had one free slot, so 64 stone go there and the other 64 go to the glow-frame chest |
| Test 5: Hopper frame | The catch-all takes rotten flesh and string |
| Safety: three apple chests | The locked chest (left) and the blocked chest with stone on top (middle) stay empty. All apples go right |
| Restock: torch chest | `/restock` fills your 4 hotbar torches to 64 |

Your hotbar (torches and 16 stone) must not move during `/stash`. Remove everything with `/function framestash_test:reset` from the same spot.

## Release checklist (from the roadmap)

Tick these on **both** loaders before uploading.

**Core**
- [ ] Test room checks above all pass
- [ ] `/framestash` opens the menu; Stash and Restock buttons work; icons can't be taken out
- [ ] `/framestash info` lists the test room and shows "skipped: locked" and "skipped: blocked"
- [ ] With `includeHotbar = true` in `config/frame_stash.toml` and `/framestash reload`, Stash also takes hotbar items

**Server and clients**
- [ ] Fabric server + vanilla client: join, `/stash` works, menu works
- [ ] NeoForge server + vanilla client: join, `/stash` works, menu works
- [ ] Two players stash into the same chests at once; nothing is lost or duplicated
- [ ] `commandPermissionLevel = 2` plus reload: a non-op can no longer use `/stash`, and an op still can

**Compatibility**
- [ ] With one claim mod (for example Open Parties and Claims, FTB Chunks or Flan), a claimed chest you don't own shows "skipped: protected"
- [ ] Optional: about 200 item frames within radius 32, and `/stash` causes no visible lag spike

If something fails, copy the server log (`run-server/logs/latest.log` for dev servers) and send it over.
