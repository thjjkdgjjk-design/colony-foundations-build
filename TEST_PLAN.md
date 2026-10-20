# Test plan

1. Build in GitHub Actions; inspect compilation failures before downloading anything.
2. Start Minecraft 1.21.5 with Fabric Loader and Fabric API in a fresh test instance.
3. Create a disposable Creative world.
4. Place the Town Hall on a clear flat surface.
5. Use Builder Contract on a vanilla villager; verify a custom name and builder tag appear.
6. Right-click Town Hall to start a cottage project; observe whether the villager navigates to the nearby work spot.
7. Verify blocks are placed incrementally and occupied cells are skipped.
8. Save/reload and verify the order progress persists.
9. Shift-right-click Town Hall and confirm pause/resume behavior.
10. Test multiplayer separately; this prototype is not yet verified for multiplayer edge cases.
