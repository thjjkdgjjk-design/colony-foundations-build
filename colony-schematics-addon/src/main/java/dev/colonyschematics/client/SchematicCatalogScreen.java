package dev.colonyschematics.client;

import dev.colonyschematics.network.PlaceSchematicPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class SchematicCatalogScreen extends Screen {
    private record Entry(String title, Identifier id) { }

    private static final List<Entry> ENTRIES = buildEntries();
    private static final int LIST_TOP = 48;
    private static final int ROW_HEIGHT = 24;

    private int selected = 0;
    private int rotation = 0;
    private String status = "Select a house, rotate it, then place in front of you.";
    private int visibleRows;

    public SchematicCatalogScreen() {
        super(Text.translatable("screen.colony_schematics.catalog"));
    }

    private static List<Entry> buildEntries() {
        List<Entry> entries = new ArrayList<>();
        String[][] styles = {
                {"birch", "Birch"},
                {"darkoak", "Dark Oak"},
                {"defaultwooden", "Wooden"},
                {"stone", "Stone"}
        };
        for (String[] style : styles) {
            for (int tier = 1; tier <= 5; tier++) {
                entries.add(new Entry(style[1] + " — Citizen House " + tier,
                        Identifier.of("colony_schematics",
                                "minecolonies/" + style[0] + "/buildings/citizen/citizen" + tier)));
            }
        }
        return List.copyOf(entries);
    }

    @Override
    protected void init() {
        visibleRows = 10;
        int center = this.width / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Rotate 90°"), button -> {
            rotation = (rotation + 1) % 4;
            status = "Rotation: " + (rotation * 90) + "°";
        }).dimensions(center - 205, this.height - 35, 125, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Place (creative)"), button -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            if (!client.player.getAbilities().creativeMode) {
                status = "Creative mode is required for test placement.";
                return;
            }
            ColonySchematicsClient.requestPlacement(new PlaceSchematicPayload(ENTRIES.get(selected).id(), rotation));
            client.setScreen(null);
        }).dimensions(center - 65, this.height - 35, 135, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button ->
                MinecraftClient.getInstance().setScreen(null))
                .dimensions(center + 85, this.height - 35, 120, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 16, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Bundled MineColonies house schematics — block states only"),
                this.width / 2, 31, 0xB8C8D8);

        int left = 14;
        int columnWidth = (this.width - 34) / 2;
        int maxRows = Math.min(10, Math.max(1, (this.height - LIST_TOP - 52) / ROW_HEIGHT));
        visibleRows = maxRows;

        for (int row = 0; row < maxRows; row++) {
            for (int col = 0; col < 2; col++) {
                int index = row * 2 + col;
                if (index >= ENTRIES.size()) continue;
                int x = left + col * (columnWidth + 6);
                int y = LIST_TOP + row * ROW_HEIGHT;
                int right = Math.min(this.width - 14, x + columnWidth);
                context.fill(x, y, right, y + ROW_HEIGHT - 2,
                        index == selected ? 0xFF345D4B : (index % 2 == 0 ? 0xAA20262C : 0xAA171C22));
                context.drawTextWithShadow(this.textRenderer, Text.literal(ENTRIES.get(index).title()),
                        x + 6, y + 7, index == selected ? 0xFFFFFF : 0xD7DCE2);
            }
        }

        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Selected: " + ENTRIES.get(selected).title() + "   •   Rotation: " + (rotation * 90) + "°"),
                this.width / 2, this.height - 52, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(status),
                this.width / 2, this.height - 15, 0xB8C8D8);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= LIST_TOP && mouseY < LIST_TOP + visibleRows * ROW_HEIGHT
                && mouseX >= 14 && mouseX < this.width - 14) {
            int columnWidth = (this.width - 34) / 2;
            int col = mouseX < 14 + columnWidth ? 0 : 1;
            int row = (int) ((mouseY - LIST_TOP) / ROW_HEIGHT);
            int index = row * 2 + col;
            if (index >= 0 && index < ENTRIES.size()) {
                selected = index;
                status = "Selected " + ENTRIES.get(selected).title();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
