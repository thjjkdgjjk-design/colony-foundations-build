# Colony Foundations — Fabric 1.21.5

This repository contains an experimental, clean-room colony-building prototype for Minecraft 1.21.5, targeting Fabric and Java 22.

## Current prototype features
- A craftable Town Hall block.
- A Builder Contract that recruits a vanilla villager as a builder.
- A data-pack driven blueprint loader, including block-state properties.
- A starter cottage blueprint shipped as JSON, plus an in-code fallback if the data file is missing or invalid.
- A persistent build order that places blocks incrementally near the Town Hall.

## Blueprint data format

Put a blueprint at data/<namespace>/colony_blueprints/<name>.json. Its identifier is <namespace>:<name>. Example:

    {
      "format": 1,
      "blocks": [
        { "pos": [0, 0, 0], "block": "minecraft:oak_planks" },
        {
          "pos": [1, 0, 0],
          "block": "minecraft:oak_stairs",
          "properties": { "facing": "east", "half": "bottom", "shape": "straight", "waterlogged": "false" }
        }
      ]
    }

Positions are relative to the blueprint's anchor. The loader currently accepts up to 8,192 blocks and coordinates between -128 and +128. Invalid blueprints are skipped, and the built-in starter cottage remains available as a fallback. Data-pack changes can be picked up by running /reload.

This is an original, small blueprint subsystem designed for Colony Foundations. It is not the official Structurize mod, does not read Structurize's native blueprint files, and is not a complete MineColonies port.

## Build

GitHub Actions builds the project with JDK 22 and Gradle. The resulting JAR is attached to a GitHub prerelease for each successful build.

Target: Minecraft 1.21.5 + Fabric Loader + Fabric API + Java 22. The mod emits Java 21 bytecode for Minecraft compatibility.

Please test on a disposable world first and report the relevant latest.log if a crash occurs.
