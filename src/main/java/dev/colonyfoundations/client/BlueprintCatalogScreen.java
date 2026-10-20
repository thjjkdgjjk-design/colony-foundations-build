package dev.colonyfoundations.client;

import dev.colonyfoundations.blueprint.BlueprintDefinition;
import dev.colonyfoundations.blueprint.CottageBlueprint;
import dev.colonyfoundations.network.RequestProjectStatusPayload;
import dev.colonyfoundations.network.SetProjectPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** In-game schematic catalog, 2D preview, material summary and live colony status. */
public final class BlueprintCatalogScreen extends Screen {
    private record Entry(String label, Identifier id) { }
    private static final List<Entry> ENTRIES = makeEntries();
    private static final int PAGE_SIZE = 5;

    private final BlockPos hallPos;
    private int selected;
    private int page;
    private int rotation;
    private int left;
    private int top;
    private int statusPollTicks;
    private String serverStatus = "Статус загружается...";

    public BlueprintCatalogScreen(BlockPos hallPos) {
        super(Text.literal("Строительство колонии"));
        this.hallPos = hallPos.toImmutable();
    }

    private static List<Entry> makeEntries() {
        List<Entry> result = new ArrayList<>();
        result.add(new Entry("Стартовый коттедж", Identifier.of("colony_foundations", "cottage")));
        addStyle(result, "birch", "Берёза");
        addStyle(result, "darkoak", "Тёмный дуб");
        addStyle(result, "defaultwooden", "Дерево");
        addStyle(result, "stone", "Камень");
        return List.copyOf(result);
    }

    private static void addStyle(List<Entry> result, String style, String title) {
        for (int tier = 1; tier <= 5; tier++) {
            result.add(new Entry(title + " — дом жителя " + tier,
                    Identifier.of("colony_foundations",
                            "minecolonies/" + style + "/buildings/citizen/citizen" + tier)));
        }
    }

    @Override
    protected void init() {
        super.init();
        left = width / 2 - 220;
        top = height / 2 - 122;

        int start = page * PAGE_SIZE;
        int end = Math.min(ENTRIES.size(), start + PAGE_SIZE);
        for (int i = start; i < end; i++) {
            final int index = i;
            String label = (selected == i ? "▶ " : "") + ENTRIES.get(i).label();
            addDrawableChild(ButtonWidget.builder(Text.literal(label), button -> {
                selected = index;
                clearAndInit();
            }).dimensions(left + 10, top + 34 + (i - start) * 18, 190, 18).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("◀"), button -> {
            page = Math.max(0, page - 1);
            clearAndInit();
        }).dimensions(left + 10, top + 128, 32, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("▶"), button -> {
            page = Math.min((ENTRIES.size() - 1) / PAGE_SIZE, page + 1);
            clearAndInit();
        }).dimensions(left + 168, top + 128, 32, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Поворот: " + rotation * 90 + "°"), button -> {
            rotation = (rotation + 1) % 4;
            clearAndInit();
        }).dimensions(left + 10, top + 153, 190, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Начать строительство"), button -> {
            send(1);
        }).dimensions(left + 212, top + 153, 214, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Снабдить из инвентаря"), button -> send(3))
                .dimensions(left + 10, top + 207, 140, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Пауза / продолжить"), button -> send(2))
                .dimensions(left + 156, top + 207, 140, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), button -> close())
                .dimensions(left + 302, top + 207, 124, 20).build());

        requestStatus();
    }

    @Override
    public void tick() {
        super.tick();
        if (++statusPollTicks >= 20) {
            statusPollTicks = 0;
            requestStatus();
        }
    }

    private void requestStatus() {
        if (client != null && client.getNetworkHandler() != null) {
            ClientPlayNetworking.send(new RequestProjectStatusPayload(hallPos));
        }
    }

    public void updateStatus(String statusText) {
        serverStatus = statusText == null ? "Статус недоступен" : statusText;
    }

    public BlockPos previewHallPos() {
        return hallPos;
    }

    public Identifier previewBlueprintId() {
        return ENTRIES.get(selected).id();
    }

    public int previewRotation() {
        return rotation;
    }

    private void send(int action) {
        if (client != null && client.getNetworkHandler() != null) {
            ClientPlayNetworking.send(new SetProjectPayload(
                    hallPos, ENTRIES.get(selected).id(), rotation, action));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.fill(left, top, left + 440, top + 242, 0xE6111720);
        context.drawBorder(left, top, 440, 242, 0xFF6F879D);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 8, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("СХЕМЫ"), left + 12, top + 23, 0xFFB9C9D8);
        context.drawTextWithShadow(textRenderer, Text.literal("ПРЕДПРОСМОТР И МАТЕРИАЛЫ"),
                left + 214, top + 23, 0xFFB9C9D8);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Страница " + (page + 1) + " / " + ((ENTRIES.size() + PAGE_SIZE - 1) / PAGE_SIZE)),
                left + 105, top + 135, 0xFFB9C9D8);

        Entry entry = ENTRIES.get(selected);
        BlueprintDefinition definition = ColonyFoundationsClient.preview(entry.id());
        if (definition == null && entry.id().getPath().equals("cottage")) {
            definition = CottageBlueprint.defaultDefinition();
        }

        int previewLeft = left + 214;
        context.fill(previewLeft, top + 34, left + 427, top + 145, 0xFF202A33);
        context.drawBorder(previewLeft, top + 34, 213, 111, 0xFF536879);
        context.drawTextWithShadow(textRenderer, Text.literal(entry.label()), previewLeft + 6, top + 40, 0xFFFFFFFF);

        if (definition != null) {
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Размер: " + definition.sizeX() + " × " + definition.sizeY() + " × " + definition.sizeZ()),
                    previewLeft + 6, top + 53, 0xFFD6E2EA);
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Блоков: " + definition.placements().size()),
                    previewLeft + 6, top + 64, 0xFFD6E2EA);
            drawFacade(context, definition, previewLeft + 6, top + 78, 198, 62);
            drawMaterials(context, definition, previewLeft + 6, top + 151);
        } else {
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Схему нельзя прочитать"), previewLeft + 6, top + 60, 0xFFFF7777);
        }

        String[] lines = serverStatus.split("\\n");
        for (int i = 0; i < Math.min(3, lines.length); i++) {
            String line = lines[i];
            if (line.length() > 26) line = line.substring(0, 25) + "…";
            context.drawTextWithShadow(textRenderer, Text.literal(line), left + 12, top + 181 + i * 10,
                    i == 0 ? 0xFF9CE6B0 : 0xFFD6E2EA);
        }
        context.drawTextWithShadow(textRenderer,
                Text.literal("Статус колонии"), left + 12, top + 167, 0xFFB9C9D8);
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawFacade(DrawContext context, BlueprintDefinition definition, int x, int y, int maxWidth, int maxHeight) {
        if (definition.sizeX() <= 0 || definition.sizeY() <= 0) return;
        int cell = Math.max(1, Math.min(5, Math.min(maxWidth / definition.sizeX(), maxHeight / definition.sizeY())));
        int drawWidth = definition.sizeX() * cell;
        int drawHeight = definition.sizeY() * cell;
        int startX = x + Math.max(0, (maxWidth - drawWidth) / 2);
        int startY = y + Math.max(0, (maxHeight - drawHeight) / 2);

        Map<Long, Integer> facade = new HashMap<>();
        for (var placement : definition.placements()) {
            int bx = placement.offset().getX();
            int by = placement.offset().getY();
            long key = (((long) bx) << 32) | (by & 0xFFFFFFFFL);
            facade.put(key, blockColor(placement.state()));
        }
        for (Map.Entry<Long, Integer> cellEntry : facade.entrySet()) {
            int bx = (int) (cellEntry.getKey() >> 32);
            int by = (int) (long) cellEntry.getKey();
            int px = startX + bx * cell;
            int py = startY + (definition.sizeY() - 1 - by) * cell;
            context.fill(px, py, px + Math.max(1, cell - 1), py + Math.max(1, cell - 1), cellEntry.getValue());
        }
    }

    private static int blockColor(BlockState state) {
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        if (path.contains("glass")) return 0xFF79BCD5;
        if (path.contains("leaves")) return 0xFF588D4B;
        if (path.contains("log") || path.contains("stem")) return 0xFF795235;
        if (path.contains("planks") || path.contains("wood")) return 0xFFB58C55;
        if (path.contains("stone") || path.contains("cobble") || path.contains("brick")) return 0xFF828B94;
        if (path.contains("roof") || path.contains("stair")) return 0xFF9A7154;
        return 0xFFCCD3D8;
    }

    private void drawMaterials(DrawContext context, BlueprintDefinition definition, int x, int y) {
        Map<Identifier, Integer> counts = new HashMap<>();
        for (var placement : definition.placements()) {
            Identifier blockId = Registries.BLOCK.getId(placement.state().getBlock());
            counts.merge(blockId, 1, Integer::sum);
        }
        List<Map.Entry<Identifier, Integer>> ordered = new ArrayList<>(counts.entrySet());
        ordered.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        context.drawTextWithShadow(textRenderer, Text.literal("Нужно всего (по схеме):"), x, y, 0xFFFFFFFF);
        int line = 0;
        for (Map.Entry<Identifier, Integer> item : ordered) {
            if (line >= 4) break;
            String name = item.getKey().getPath().replace('_', ' ');
            if (name.length() > 22) name = name.substring(0, 22);
            context.drawTextWithShadow(textRenderer,
                    Text.literal(name + " × " + item.getValue()), x, y + 11 + line * 10, 0xFFD6E2EA);
            line++;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
