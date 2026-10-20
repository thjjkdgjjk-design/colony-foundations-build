package dev.colonyfoundations.client;

import dev.colonyfoundations.network.SetProjectPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** In-game schematic catalog and project controls. */
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
        left = width / 2 - 158;
        top = height / 2 - 112;

        int start = page * PAGE_SIZE;
        int end = Math.min(ENTRIES.size(), start + PAGE_SIZE);
        for (int i = start; i < end; i++) {
            final int index = i;
            String label = (selected == i ? "▶ " : "") + ENTRIES.get(i).label();
            addDrawableChild(ButtonWidget.builder(Text.literal(label), button -> {
                selected = index;
                clearAndInit();
            }).dimensions(left + 12, top + 32 + (i - start) * 19, 292, 18).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("◀"), button -> {
            page = Math.max(0, page - 1);
            clearAndInit();
        }).dimensions(left + 12, top + 130, 35, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("▶"), button -> {
            page = Math.min((ENTRIES.size() - 1) / PAGE_SIZE, page + 1);
            clearAndInit();
        }).dimensions(left + 269, top + 130, 35, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Поворот: " + rotation * 90 + "°"), button -> {
            rotation = (rotation + 1) % 4;
            clearAndInit();
        }).dimensions(left + 12, top + 154, 145, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Начать строительство"), button -> {
            send(1);
            close();
        }).dimensions(left + 163, top + 154, 141, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Пауза / продолжить"), button -> send(2))
                .dimensions(left + 12, top + 179, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), button -> close())
                .dimensions(left + 163, top + 179, 141, 20).build());
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
        context.fill(left, top, left + 316, top + 207, 0xE6111720);
        context.drawBorder(left, top, 316, 207, 0xFF6F879D);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 8, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Выбери схему и закажи строительство"),
                width / 2, top + 20, 0xFFB9C9D8);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Страница " + (page + 1) + " / " + ((ENTRIES.size() + PAGE_SIZE - 1) / PAGE_SIZE)),
                width / 2, top + 136, 0xFFB9C9D8);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
