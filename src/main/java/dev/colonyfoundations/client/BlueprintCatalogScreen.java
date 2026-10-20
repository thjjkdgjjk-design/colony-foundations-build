package dev.colonyfoundations.client;

import dev.colonyfoundations.blueprint.BlueprintDefinition;
import dev.colonyfoundations.blueprint.CottageBlueprint;
import dev.colonyfoundations.network.BlueprintCatalogPayload;
import dev.colonyfoundations.network.RequestBlueprintCatalogPayload;
import dev.colonyfoundations.network.RequestProjectStatusPayload;
import dev.colonyfoundations.network.SetProjectPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
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

/** In-game blueprint catalog, preview, material summary and live project controls. */
public final class BlueprintCatalogScreen extends Screen {
    private record Entry(Identifier id, String label, String sizeSummary, int blockCount) { }
    private static final int PAGE_SIZE = BlueprintCatalogPayload.PAGE_SIZE;
    private static final Identifier COTTAGE_ID = Identifier.of("colony_foundations", "cottage");

    private static Entry fallbackCottage() {
        BlueprintDefinition definition = CottageBlueprint.defaultDefinition();
        return new Entry(COTTAGE_ID, "Стартовый коттедж",
                definition.sizeX() + " × " + definition.sizeY() + " × " + definition.sizeZ(),
                definition.placements().size());
    }

    private final BlockPos hallPos;
    private List<Entry> entries = List.of(fallbackCottage());
    private int selected;
    private int page;
    private int totalPages = 1;
    private int totalEntries = 1;
    private int rotation;
    private int left;
    private int top;
    private int statusPollTicks;
    private boolean siteTouched;
    private int siteOffsetX = 5;
    private int siteOffsetZ = -3;
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
        top = height / 2 - 142;

        for (int i = 0; i < entries.size(); i++) {
            final int index = i;
            String label = (selected == i ? "▶ " : "") + entries.get(i).label();
            addDrawableChild(ButtonWidget.builder(Text.literal(label), button -> {
                selected = index;
                clearAndInit();
            }).dimensions(left + 10, top + 34 + i * 18, 190, 18).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("◀"), button -> {
            if (page > 0) {
                page--;
                selected = 0;
                clearAndInit();
            }
        }).dimensions(left + 10, top + 128, 32, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("▶"), button -> {
            if (page + 1 < totalPages) {
                page++;
                selected = 0;
                clearAndInit();
            }
        }).dimensions(left + 168, top + 128, 32, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("←"), button -> moveSite(-2, 0))
                .dimensions(left + 10, top + 164, 40, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("→"), button -> moveSite(2, 0))
                .dimensions(left + 54, top + 164, 40, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("↑"), button -> moveSite(0, -2))
                .dimensions(left + 98, top + 164, 40, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("↓"), button -> moveSite(0, 2))
                .dimensions(left + 142, top + 164, 40, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Поворот: " + rotation * 90 + "°"), button -> {
            rotation = (rotation + 1) % 4;
            clearAndInit();
        }).dimensions(left + 10, top + 188, 190, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Строить"), button -> send(1))
                .dimensions(left + 10, top + 260, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Снабдить"), button -> send(3))
                .dimensions(left + 116, top + 260, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Пауза"), button -> send(2))
                .dimensions(left + 222, top + 260, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), button -> close())
                .dimensions(left + 328, top + 260, 102, 20).build());

        requestStatus();
        requestCatalog();
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

    private void requestCatalog() {
        if (client != null && client.getNetworkHandler() != null) {
            ClientPlayNetworking.send(new RequestBlueprintCatalogPayload(hallPos, page));
        }
    }

    public void updateCatalog(BlueprintCatalogPayload payload) {
        if (!hallPos.equals(payload.hallPos()) || payload.page() != page) return;
        List<Entry> updated = payload.entries().stream()
                .map(entry -> new Entry(entry.id(), entry.title(), entry.sizeSummary(), entry.blockCount()))
                .toList();
        if (updated.isEmpty()) updated = List.of(fallbackCottage());
        boolean changed = !entries.equals(updated)
                || totalPages != payload.totalPages() || totalEntries != payload.totalEntries();
        entries = List.copyOf(updated);
        totalPages = Math.max(1, payload.totalPages());
        totalEntries = Math.max(0, payload.totalEntries());
        if (selected >= entries.size()) selected = Math.max(0, entries.size() - 1);
        if (changed && client != null) clearAndInit();
    }

    private void moveSite(int dx, int dz) {
        siteOffsetX = Math.max(-64, Math.min(64, siteOffsetX + dx));
        siteOffsetZ = Math.max(-64, Math.min(64, siteOffsetZ + dz));
        siteTouched = true;
        clearAndInit();
    }

    public void updateStatus(String statusText, int serverOffsetX, int serverOffsetZ) {
        serverStatus = statusText == null ? "Статус недоступен" : statusText;
        if (!siteTouched) {
            siteOffsetX = serverOffsetX;
            siteOffsetZ = serverOffsetZ;
        }
    }

    public BlockPos previewHallPos() {
        return hallPos;
    }

    public Identifier previewBlueprintId() {
        return entries.get(Math.max(0, Math.min(selected, entries.size() - 1))).id();
    }

    public int previewRotation() {
        return rotation;
    }

    public int previewSiteOffsetX() {
        return siteOffsetX;
    }

    public int previewSiteOffsetZ() {
        return siteOffsetZ;
    }

    private void send(int action) {
        if (client != null && client.getNetworkHandler() != null) {
            ClientPlayNetworking.send(new SetProjectPayload(
                    hallPos, entries.get(Math.max(0, Math.min(selected, entries.size() - 1))).id(),
                    rotation, siteOffsetX, siteOffsetZ, action));
            if (action == 1 || action == 3) siteTouched = false;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.fill(left, top, left + 440, top + 284, 0xE6111720);
        context.drawBorder(left, top, 440, 284, 0xFF6F879D);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 8, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("СХЕМЫ"), left + 12, top + 23, 0xFFB9C9D8);
        context.drawTextWithShadow(textRenderer, Text.literal("ПРЕДПРОСМОТР И МАТЕРИАЛЫ"),
                left + 214, top + 23, 0xFFB9C9D8);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Страница " + (page + 1) + " / " + totalPages + " · схем: " + totalEntries),
                left + 105, top + 135, 0xFFB9C9D8);
        context.drawTextWithShadow(textRenderer,
                Text.literal("Площадка: " + siteOffsetX + ", " + siteOffsetZ), left + 12, top + 151, 0xFFB9C9D8);

        Entry entry = entries.get(Math.max(0, Math.min(selected, entries.size() - 1)));
        BlueprintDefinition definition = COTTAGE_ID.equals(entry.id())
                ? CottageBlueprint.defaultDefinition() : null;

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
                    Text.literal("Размер: " + entry.sizeSummary()), previewLeft + 6, top + 53, 0xFFD6E2EA);
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Блоков: " + entry.blockCount()), previewLeft + 6, top + 64, 0xFFD6E2EA);
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Нет точной клиентской геометрии"), previewLeft + 6, top + 82, 0xFFFFCC88);
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Предпросмотр доступен для коттеджа"), previewLeft + 6, top + 94, 0xFFD6E2EA);
        }

        context.drawTextWithShadow(textRenderer, Text.literal("Статус колонии"), left + 12, top + 212, 0xFFB9C9D8);
        String[] lines = serverStatus.split("\\n");
        for (int i = 0; i < Math.min(3, lines.length); i++) {
            String line = lines[i];
            if (line.length() > 26) line = line.substring(0, 25) + "…";
            context.drawTextWithShadow(textRenderer, Text.literal(line), left + 12, top + 225 + i * 10,
                    i == 0 ? 0xFF9CE6B0 : 0xFFD6E2EA);
        }
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
