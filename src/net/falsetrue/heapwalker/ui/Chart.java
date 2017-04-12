package net.falsetrue.heapwalker.ui;

import com.intellij.ui.JBColor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.List;

@SuppressWarnings("UseJBColor")
public class Chart extends JPanel {
    private static final Color[] INSERTED_COLORS = {
        JBColor.RED,
        JBColor.YELLOW,
        JBColor.ORANGE,
        JBColor.CYAN,
        JBColor.GREEN,
        JBColor.BLUE,
        JBColor.PINK,
    };

    private static final int CHART_WIDTH_PERCENT = 96;
    private static final int CHART_MARGIN_PERCENT = (100 - CHART_WIDTH_PERCENT) / 2;

    private List<Item> data;
    private int hovered = -1;
    private int chartSize;
    private int sum;

    public Chart() {
        super(new GridLayout(1, 1));

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int x = e.getX() - chartSize / 2;
                int y = e.getY() - chartSize / 2;
                int newHovered = -1;
                setToolTipText(null);
                if (x * x + y * y <= chartSize * chartSize) {
                    int angle = (int) (180 * Math.atan2(-y, x) / Math.PI);
                    if (angle < 0) {
                        angle = 360 + angle;
                    }
                    newHovered = -1;
                    for (int i = 0; i < data.size(); i++) {
                        if (data.get(i).startAngle + data.get(i).angle > angle) {
                            newHovered = i;
                            break;
                        }
                    }
                    if (newHovered == -1) {
                        newHovered = data.size() - 1;
                    }
                    Item item = data.get(newHovered);
                    setToolTipText(item.label + ": " + item.count +
                                    " (" + (item.count * 100 / sum) + "%)");
                }
                if (newHovered != hovered) {
                    hovered = newHovered;
                    updateUI();
                }
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                hovered = -1;
                setToolTipText(null);
                updateUI();
            }
        });
    }

    @Override
    public void setSize(Dimension d) {
        super.setSize(d);
        chartSize = (int) Math.min(d.getWidth(), d.getHeight());
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
        chartSize = Math.min(width, height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (data != null) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            chartSize = Math.min(getWidth(), getHeight());
            int smallSize = chartSize * CHART_WIDTH_PERCENT / 100;
            int smallStart = chartSize * CHART_MARGIN_PERCENT / 100;
            for (int i = 0; i < data.size(); i++) {
                Item item = data.get(i);
                if (item.count == 0) {
                    continue;
                }
                g.setColor(item.color);
                if (hovered == i) {
                    g.fillArc(0, 0, chartSize, chartSize, item.startAngle, item.angle);
                } else {
                    g.fillArc(smallStart, smallStart, smallSize, smallSize, item.startAngle, item.angle);
                }
            }
        }
    }

    public void clear() {
        data = null;
        repaint();
    }

    public void setData(List<Item> data) {
        this.data = data;
        if (data.size() > 0) {
            sum = data.stream().mapToInt(Item::getCount).sum();
            int currentSum = 0;
            int colorPos = 0;
            for (Item item : data) {
                if (item.count == 0) {
                    continue;
                }
                int nextAngle = (currentSum + item.count) * 360 / sum;
                item.startAngle = currentSum * 360 / sum;
                item.angle = nextAngle - item.startAngle;
                currentSum += item.count;
                if (item.color == null) {
                    if (colorPos < INSERTED_COLORS.length) {
                        item.color = INSERTED_COLORS[colorPos++];
                    } else {
                        item.color = new Color(item.label.hashCode(), false);
                    }
                }
            }
        }
        repaint();
    }

    public static class Item {
        private String label;
        private int count;
        private Color color;
        private int startAngle;
        private int angle;

        public Item(String label, int count) {
            this.label = label;
            this.count = count;
        }

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
