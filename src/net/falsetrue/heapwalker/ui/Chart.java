package net.falsetrue.heapwalker.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.List;

@SuppressWarnings("UseJBColor")
public class Chart extends JPanel {
    private static final int CHART_WIDTH_PERCENT = 94;
    private static final int CHART_MARGIN_PERCENT = (100 - CHART_WIDTH_PERCENT) / 2;

    private List<Item> data;
    private int hovered = -1;
    private int chartSize;

    public Chart() {
        setPreferredSize(new Dimension(400, getHeight()));

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
                    setToolTipText(data.get(newHovered).label + ": " + data.get(newHovered).count);
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
            ((Graphics2D) g).setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            chartSize = Math.min(getWidth(), getHeight());
            g.setColor(data.get(data.size() - 1).color);
            int smallSize = chartSize * CHART_WIDTH_PERCENT / 100;
            int smallStart = chartSize * CHART_MARGIN_PERCENT / 100;
            g.fillArc(smallStart, smallStart, smallSize, smallSize, 0, 360);
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
            int sum = data.stream().mapToInt(Item::getCount).sum();
            int lastAngle = 0;
            for (Item item : data) {
                if (item.count == 0) {
                    continue;
                }
                item.angle = item.count * 360 / sum;
                item.startAngle = lastAngle;
                lastAngle += item.angle;
            }
            data.get(data.size() - 1).angle = 360 - data.get(data.size() - 1).startAngle;
        }
        repaint();
    }

    public static class Item {
        private String label;
        private int count;
        private Color color;
        private int startAngle;
        private int angle;

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
