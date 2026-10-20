package dev.colonyschematics.client;

import dev.colonyschematics.network.PlaceSchematicPayload;
import dev.colonyschematics.network.RequestSchematicCatalogPayload;
import dev.colonyschematics.network.SchematicCatalogPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SchematicCatalogScreen extends Screen {
    private static final int LIST_TOP = 64;
    private static final int ROW_HEIGHT = 24;

    private List<SchematicCatalogPayload.Entry> entries = List.of();
    private int selected = 0;
    private int rotation = 0;
    private int mirror = 0;
    private int page = 0;
    private int totalPages = 1;
    private int totalEntries = 0;
    private int visibleRows = 10;
    private String query = "";
    private String status = "Loading blueprints from the server and enabled datapacks…";

    public SchematicCatalogScreen() {
        super(Text.translatable("screen.colony_schematics.catalog"));
    }

    @Override
    protected void init() {
        int center = this.width / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("◀ Previous"), button -> {
            if (page > 0) {
                page--;
                selected = 0;
                requestCurrentPage();
            }
        }).dimensions(14, 38, 90, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Next ▶"), button -> {
            if (page + 1 < totalPages) {
                page++;
                selected = 0;
                requestCurrentPage();
            }
        }).dimensions(this.width - 104, 38, 90, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Rotate 90°"), button -> {
            rotation = (rotation + 1) % 4;
            status = "Rotation: " + (rotation * 90) + "°";
        }).dimensions(center - 205, this.height - 35, 125, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal(mirrorLabel()), button -> {
            mirror = (mirror + 1) % 3;
            button.setMessage(Text.literal(mirrorLabel()));
            status = "Mirror: " + mirrorLabel();
        }).dimensions(center - 65, this.height - 60, 135, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Place (creative)"), button -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            if (!client.player.getAbilities().creativeMode) {
                status = "Creative mode is required for test placement.";
                return;
            }
            if (entries.isEmpty() || selected < 0 || selected >= entries.size()) {
                status = "Select a schematic first.";
                return;
            }
            ColonySchematicsClient.requestPlacement(
                    new PlaceSchematicPayload(entries.get(selected).id(), rotation, mirror));
            status = "Placement request sent to server.";
        }).dimensions(center - 65, this.height - 35, 135, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button ->
                MinecraftClient.getInstance().setScreen(null))
                .dimensions(center + 85, this.height - 35, 120, 20).build());

        requestCurrentPage();
    }

    private void requestCurrentPage() {
        status = "Loading catalog…";
        ColonySchematicsClient.requestCatalog(new RequestSchematicCatalogPayload(page, query));
    }

    public void updateCatalog(SchematicCatalogPayload payload) {
        String expectedQuery = query.strip().toLowerCase(Locale.ROOT);
        if (payload.page() != page || !payload.query().equals(expectedQuery)) {
            return; // Ignore responses from earlier keystrokes or page selections.
        }
        entries = List.copyOf(new ArrayList<>(payload.entries()));
        totalPages = Math.max(1, payload.totalPages());
        totalEntries = Math.max(0, payload.totalEntries());
        if (selected >= entries.size()) selected = Math.max(0, entries.size() - 1);
        status = entries.isEmpty()
                ? "No blueprints match this search. Backspace clears the search."
                : "Loaded " + totalEntries + " blueprints. Type to search; Enter refreshes.";
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!Character.isISOControl(chr) && query.length() < 128) {
            query += chr;
            page = 0;
            selected = 0;
            requestCurrentPage();
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 259 && !query.isEmpty()) { // Backspace
            query = query.substring(0, query.length() - 1);
            page = 0;
            selected = 0;
            requestCurrentPage();
            return true;
        }
        if (keyCode == 257 || keyCode == 335) { // Enter / keypad Enter
            requestCurrentPage();
            return true;
        }
        if (keyCode == 268) { // Home: reset the filter
            query = "";
            page = 0;
            selected = 0;
            requestCurrentPage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);
        String visibleQuery = query.isEmpty() ? "(all blueprints)" : query;
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Search: " + visibleQuery + "  •  " + totalEntries + " blueprints"),
                this.width / 2, 25, 0xB8C8D8);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Page " + (page + 1) + " / " + totalPages + "   |   Rotate: " + (rotation * 90) + "°   |   Mirror: " + mirrorLabel()),
                this.width / 2, 43, 0xFFFFFF);

        int left = 14;
        int columnWidth = (this.width - 34) / 2;
        int maxRows = Math.min(10, Math.max(1, (this.height - LIST_TOP - 62) / ROW_HEIGHT));
        visibleRows = maxRows;

        for (int row = 0; row < maxRows; row++) {
            for (int col = 0; col < 2; col++) {
                int index = row * 2 + col;
                if (index >= entries.size()) continue;
                int x = left + col * (columnWidth + 6);
                int y = LIST_TOP + row * ROW_HEIGHT;
                int right = Math.min(this.width - 14, x + columnWidth);
                context.fill(x, y, right, y + ROW_HEIGHT - 2,
                        index == selected ? 0xFF345D4B : (index % 2 == 0 ? 0xAA20262C : 0xAA171C22));
                context.drawTextWithShadow(this.textRenderer,
                        Text.literal(entries.get(index).title()), x + 6, y + 7,
                        index == selected ? 0xFFFFFF : 0xD7DCE2);
            }
        }

        if (entries.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("No results or waiting for server catalog…"),
                    this.width / 2, LIST_TOP + 30, 0xFFFFFF);
        }

        String selectedLabel = entries.isEmpty() || selected >= entries.size()
                ? "Selected: none"
                : "Selected: " + entries.get(selected).title();
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(selectedLabel + "   •   Rotation: " + (rotation * 90) + "°   •   Mirror: " + mirrorLabel()),
                this.width / 2, this.height - 82, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(status),
                this.width / 2, this.height - 13, 0xB8C8D8);
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
            if (index >= 0 && index < entries.size()) {
                selected = index;
                status = "Selected " + entries.get(selected).title();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private String mirrorLabel() {
        return switch (mirror) {
            case 1 -> "Front/Back";
            case 2 -> "Left/Right";
            default -> "Off";
        };
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
