package net.falsetrue.heapwalker.ui;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class Chart extends JPanel {
    private List<Item> data;

    public Chart() {
        setPreferredSize(new Dimension(400, getHeight()));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (data != null) {
            ((Graphics2D) g).setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            int sum = data.stream().mapToInt(Item::getCount).sum();
            int lastAngle = 0;
            int size = Math.min(getWidth(), getHeight());
            int to = data.size() - 1;
            g.setColor(data.get(to).color);
            g.fillArc(0, 0, size, size, 0, 360);
            for (int i = 0; i < to; i++) {
                Item item = data.get(i);
                if (item.count == 0) {
                    continue;
                }
                int angle = item.count * 360 / sum;
                g.setColor(item.color);
                g.fillArc(0, 0, size, size, lastAngle - 1, angle + 1);
                lastAngle += angle;
            }
        }
    }

    public void setData(List<Item> data) {
        this.data = data;
        updateUI();
    }

    public static class Item {
        private String label;
        private int count;
        private Color color;

        public Item(String label, int count, Color color) {
            this.label = label;
            this.count = count;
            this.color = color;
        }

        int getCount() {
            return count;
        }
    }
}
