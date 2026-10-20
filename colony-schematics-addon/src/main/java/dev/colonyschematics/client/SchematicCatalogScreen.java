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
    private String status = Text.translatable("screen.colony_schematics.loading").getString();

    public SchematicCatalogScreen() {
        super(Text.translatable("screen.colony_schematics.catalog"));
    }

    @Override
    protected void init() {
        int center = this.width / 2;

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.colony_schematics.previous"), button -> {
            if (page > 0) {
                page--;
                selected = 0;
                requestCurrentPage();
            }
        }).dimensions(14, 38, 90, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.colony_schematics.next"), button -> {
            if (page + 1 < totalPages) {
                page++;
                selected = 0;
                requestCurrentPage();
            }
        }).dimensions(this.width - 104, 38, 90, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.colony_schematics.rotate", rotation * 90), button -> {
            rotation = (rotation + 1) % 4;
            button.setMessage(Text.translatable("screen.colony_schematics.rotate", rotation * 90));
            status = Text.translatable("screen.colony_schematics.rotation_status", rotation * 90).getString();
        }).dimensions(center - 205, this.height - 35, 125, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.colony_schematics.mirror", mirrorLabel()), button -> {
            mirror = (mirror + 1) % 3;
            button.setMessage(Text.translatable("screen.colony_schematics.mirror", mirrorLabel()));
            status = Text.translatable("screen.colony_schematics.mirror_status", mirrorLabel()).getString();
        }).dimensions(center - 65, this.height - 60, 135, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.colony_schematics.place_creative"), button -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            if (!client.player.getAbilities().creativeMode) {
                status = Text.translatable("message.colony_schematics.creative_only").getString();
                return;
            }
            if (entries.isEmpty() || selected < 0 || selected >= entries.size()) {
                status = Text.translatable("screen.colony_schematics.select_first").getString();
                return;
            }
            ColonySchematicsClient.requestPlacement(
                    new PlaceSchematicPayload(entries.get(selected).id(), rotation, mirror));
            status = Text.translatable("screen.colony_schematics.placement_sent").getString();
        }).dimensions(center - 65, this.height - 35, 135, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.colony_schematics.close"), button ->
                MinecraftClient.getInstance().setScreen(null))
                .dimensions(center + 85, this.height - 35, 120, 20).build());

        requestCurrentPage();
    }

    private void requestCurrentPage() {
        status = Text.translatable("screen.colony_schematics.loading").getString();
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
                ? Text.translatable("screen.colony_schematics.no_matches").getString()
                : Text.translatable("screen.colony_schematics.loaded", totalEntries).getString();
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
        String visibleQuery = query.isEmpty() ? Text.translatable("screen.colony_schematics.all_blueprints").getString() : query;
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("screen.colony_schematics.search_line", visibleQuery, totalEntries),
                this.width / 2, 25, 0xB8C8D8);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("screen.colony_schematics.page_line", page + 1, totalPages, rotation * 90, mirrorLabel()),
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
                    Text.translatable("screen.colony_schematics.empty_results"),
                    this.width / 2, LIST_TOP + 30, 0xFFFFFF);
        }

        String selectedLabel = entries.isEmpty() || selected >= entries.size()
                ? Text.translatable("screen.colony_schematics.selected_none").getString()
                : Text.translatable("screen.colony_schematics.selected", entries.get(selected).title()).getString();
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("screen.colony_schematics.selected_line", selectedLabel, rotation * 90, mirrorLabel()),
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
                status = Text.translatable("screen.colony_schematics.selection_status", entries.get(selected).title()).getString();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private String mirrorLabel() {
        String key = switch (mirror) {
            case 1 -> "screen.colony_schematics.mirror_front_back";
            case 2 -> "screen.colony_schematics.mirror_left_right";
            default -> "screen.colony_schematics.mirror_off";
        };
        return Text.translatable(key).getString();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
