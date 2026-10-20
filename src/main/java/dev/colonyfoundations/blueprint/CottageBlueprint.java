package dev.colonyfoundations.blueprint;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Fallback starter cottage remains available if data-pack resources fail to load. */
public final class CottageBlueprint {
    private static final Identifier COTTAGE_ID = Identifier.of("colony_foundations", "cottage");
    private static final BlueprintDefinition BUILT_IN_COTTAGE =
            new BlueprintDefinition(COTTAGE_ID, "Стартовый коттедж", 7, 8, 7, createCottage());

    private CottageBlueprint() { }

    public static BlueprintDefinition defaultDefinition() {
        return BlueprintRegistry.getDefinition(COTTAGE_ID).orElse(BUILT_IN_COTTAGE);
    }

    public static List<Placement> cottage() {
        return defaultDefinition().placements();
    }

    public record Placement(BlockPos offset, BlockState state) { }

    private static List<Placement> createCottage() {
        List<Placement> plan = new ArrayList<>();
        BlockState planks = Blocks.OAK_PLANKS.getDefaultState();
        BlockState logs = Blocks.OAK_LOG.getDefaultState();
        BlockState glass = Blocks.GLASS_PANE.getDefaultState();

        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) add(plan, x, 1, z, planks);
        for (int y = 2; y <= 4; y++) {
            for (int x = 0; x < 7; x++) {
                add(plan, x, y, 0, wallState(x, y, 0, logs, planks, glass));
                if (!(x == 3 && (y == 2 || y == 3))) add(plan, x, y, 6, wallState(x, y, 6, logs, planks, glass));
            }
            for (int z = 1; z < 6; z++) {
                add(plan, 0, y, z, wallState(0, y, z, logs, planks, glass));
                add(plan, 6, y, z, wallState(6, y, z, logs, planks, glass));
            }
        }
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) add(plan, x, 5, z, planks);
        for (int x = 1; x < 6; x++) for (int z = 1; z < 6; z++) add(plan, x, 6, z, planks);
        for (int x = 2; x < 5; x++) for (int z = 2; z < 5; z++) add(plan, x, 7, z, planks);
        return List.copyOf(plan);
    }

    private static BlockState wallState(int x, int y, int z, BlockState logs, BlockState planks, BlockState glass) {
        if ((x == 0 || x == 6) && (z == 0 || z == 6)) return logs;
        boolean frontOrBackWindow = y == 3 && (z == 0 || z == 6) && (x == 2 || x == 4);
        boolean sideWindow = y == 3 && (x == 0 || x == 6) && (z == 2 || z == 4);
        return frontOrBackWindow || sideWindow ? glass : planks;
    }

    private static void add(List<Placement> plan, int x, int y, int z, BlockState state) {
        plan.add(new Placement(new BlockPos(x, y, z), state));
    }
}
