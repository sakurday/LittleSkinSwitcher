package com.littleskin.switcher.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 一个轻量的可滚动列表控件，供账号列表 / 角色列表 / 服务器账号选择共用。
 *
 * 没有用 {@code ObjectSelectionList}：那套泛型与布局约束对几个固定高度的
 * 行来说太重，而且这里每行需要自定义两行文字。
 */
public class ScrollList<T> extends AbstractWidget {
    /** 行高要放得下左侧 16px 的头像和两行文字。 */
    public static final int ROW_HEIGHT = 24;
    private static final int SCROLLBAR_WIDTH = 4;

    public interface RowRenderer<T> {
        /** 画一行。y 是该行顶部，宽度已排除滚动条。 */
        void render(GuiGraphicsExtractor graphics, T item, int x, int y, int rowWidth, boolean selected, boolean hovered);
    }

    private final RowRenderer<T> renderer;
    private final List<T> items = new ArrayList<>();
    private int selectedIndex = -1;
    private int scroll;
    private Consumer<T> onSelectionChanged;

    public ScrollList(int x, int y, int width, int height, Component message, RowRenderer<T> renderer) {
        super(x, y, width, height, message);
        this.renderer = renderer;
    }

    /**
     * 用户点击选中另一行时回调。
     * 只在点击时触发（{@link #setItems} / {@link #setSelected} 这类程序性改动不触发），
     * 免得调用方在回调里回头改选中项造成递归。
     */
    public void setOnSelectionChanged(Consumer<T> callback) {
        this.onSelectionChanged = callback;
    }

    public void setItems(List<T> newItems) {
        T previouslySelected = selected();
        items.clear();
        items.addAll(newItems);
        selectedIndex = previouslySelected == null ? -1 : items.indexOf(previouslySelected);
        clampScroll();
    }

    public void setSelected(T item) {
        selectedIndex = item == null ? -1 : items.indexOf(item);
    }

    public T selected() {
        return selectedIndex >= 0 && selectedIndex < items.size() ? items.get(selectedIndex) : null;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0x80000000);

        int rowWidth = this.width - SCROLLBAR_WIDTH - 2;
        graphics.enableScissor(this.getX() + 1, this.getY() + 1, this.getX() + this.width, this.getY() + this.height - 1);
        try {
            for (int i = 0; i < items.size(); i++) {
                int rowY = this.getY() + i * ROW_HEIGHT - scroll;
                if (rowY + ROW_HEIGHT < this.getY() || rowY > this.getY() + this.height) {
                    continue;
                }
                boolean hovered = mouseX >= this.getX() && mouseX < this.getX() + rowWidth
                        && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
                renderer.render(graphics, items.get(i), this.getX() + 2, rowY, rowWidth, i == selectedIndex, hovered);
            }
        } finally {
            graphics.disableScissor();
        }

        int maxScroll = maxScroll();
        if (maxScroll > 0) {
            int trackHeight = this.height - 4;
            int thumbHeight = Math.max(12, trackHeight * this.height / (items.size() * ROW_HEIGHT));
            int thumbY = this.getY() + 2 + (trackHeight - thumbHeight) * scroll / maxScroll;
            int barX = this.getX() + this.width - SCROLLBAR_WIDTH + 1;
            graphics.fill(barX, this.getY() + 2, barX + SCROLLBAR_WIDTH - 1, this.getY() + this.height - 2, 0x40000000);
            graphics.fill(barX, thumbY, barX + SCROLLBAR_WIDTH - 1, thumbY + thumbHeight, 0xC0FFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.active || !this.visible) {
            return false;
        }
        double mx = event.x();
        double my = event.y();
        if (mx < this.getX() || mx >= this.getX() + this.width || my < this.getY() || my >= this.getY() + this.height) {
            return false;
        }
        int index = (int) ((my - this.getY() + scroll) / ROW_HEIGHT);
        if (index < 0 || index >= items.size()) {
            return false;
        }
        selectedIndex = index;
        playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
        if (this.onSelectionChanged != null) {
            this.onSelectionChanged.accept(items.get(index));
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() <= 0 || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        scroll = Math.clamp(scroll - (int) (Math.signum(scrollY) * ROW_HEIGHT), 0, maxScroll());
        return true;
    }

    private int maxScroll() {
        return Math.max(0, items.size() * ROW_HEIGHT - this.height + 2);
    }

    private void clampScroll() {
        scroll = Math.clamp(scroll, 0, maxScroll());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
