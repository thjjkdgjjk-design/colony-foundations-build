package dev.colonyfoundations.client;

import dev.colonyfoundations.blueprint.BlueprintDefinition;
import dev.colonyfoundations.blueprint.CottageBlueprint;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Draws a lightweight 3D wireframe preview while the schematic catalog is open. */
public final class BlueprintPreviewRenderer {
    private static final int MAX_PREVIEW_BLOCKS = 1800;

    private BlueprintPreviewRenderer() { }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(BlueprintPreviewRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof BlueprintCatalogScreen screen)) return;
        if (context.world() == null || context.matrixStack() == null || context.consumers() == null) return;

        BlueprintDefinition definition = ColonyFoundationsClient.preview(screen.previewBlueprintId());
        if (definition == null) return;

        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        Vec3d camera = context.camera().getPos();
        BlockPos origin = screen.previewHallPos().add(5, -1, -3);

        matrices.push();
        matrices.translate(origin.getX() - camera.x, origin.getY() - camera.y, origin.getZ() - camera.z);
        int limit = Math.min(MAX_PREVIEW_BLOCKS, definition.placements().size());
        for (int i = 0; i < limit; i++) {
            CottageBlueprint.Placement placement = definition.placements().get(i);
            BlockPos offset = rotate(placement.offset(), definition, screen.previewRotation())
                    .subtract(rotate(definition.primaryOffset(), definition, screen.previewRotation()));
            BlockPos target = origin.add(offset);
            BlockState existing = context.world().getBlockState(target);
            boolean clear = (existing.isAir() || existing.isReplaceable()) && existing.getFluidState().isEmpty();

            if (clear) {
                drawWireCube(matrices, lines, offset.getX(), offset.getY(), offset.getZ(),
                        0.2f, 0.95f, 0.85f, 0.8f);
            } else {
                drawWireCube(matrices, lines, offset.getX(), offset.getY(), offset.getZ(),
                        1.0f, 0.22f, 0.18f, 0.9f);
            }
        }
        matrices.pop();
    }

    private static void drawWireCube(MatrixStack matrices, VertexConsumer consumer,
                                     int x, int y, int z, float red, float green, float blue, float alpha) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float x0 = x + 0.04f, y0 = y + 0.04f, z0 = z + 0.04f;
        float x1 = x + 0.96f, y1 = y + 0.96f, z1 = z + 0.96f;

        line(consumer, matrix, x0, y0, z0, x1, y0, z0, red, green, blue, alpha);
        line(consumer, matrix, x1, y0, z0, x1, y0, z1, red, green, blue, alpha);
        line(consumer, matrix, x1, y0, z1, x0, y0, z1, red, green, blue, alpha);
        line(consumer, matrix, x0, y0, z1, x0, y0, z0, red, green, blue, alpha);

        line(consumer, matrix, x0, y1, z0, x1, y1, z0, red, green, blue, alpha);
        line(consumer, matrix, x1, y1, z0, x1, y1, z1, red, green, blue, alpha);
        line(consumer, matrix, x1, y1, z1, x0, y1, z1, red, green, blue, alpha);
        line(consumer, matrix, x0, y1, z1, x0, y1, z0, red, green, blue, alpha);

        line(consumer, matrix, x0, y0, z0, x0, y1, z0, red, green, blue, alpha);
        line(consumer, matrix, x1, y0, z0, x1, y1, z0, red, green, blue, alpha);
        line(consumer, matrix, x1, y0, z1, x1, y1, z1, red, green, blue, alpha);
        line(consumer, matrix, x0, y0, z1, x0, y1, z1, red, green, blue, alpha);
    }

    private static void line(VertexConsumer consumer, Matrix4f matrix,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float red, float green, float blue, float alpha) {
        consumer.vertex(matrix, x0, y0, z0).color(red, green, blue, alpha).normal(0.0f, 1.0f, 0.0f);
        consumer.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha).normal(0.0f, 1.0f, 0.0f);
    }

    private static BlockPos rotate(BlockPos source, BlueprintDefinition definition, int turns) {
        int x = source.getX(), y = source.getY(), z = source.getZ();
        return switch (Math.floorMod(turns, 4)) {
            case 1 -> new BlockPos(definition.sizeZ() - 1 - z, y, x);
            case 2 -> new BlockPos(definition.sizeX() - 1 - x, y, definition.sizeZ() - 1 - z);
            case 3 -> new BlockPos(z, y, definition.sizeX() - 1 - x);
            default -> source;
        };
    }
}
