package net.falsetrue.heapwalker.ui;

import com.intellij.ui.JBColor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Arc2D;
import java.awt.geom.GeneralPath;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("UseJBColor")
public class Chart<T> extends JPanel {
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
    private static final int SELECTED_LINE_WEIGHT = 6;

    private List<Item> data;
    private ItemSelectedListener<T> itemSelectedListener = (object, position) -> {};
    private T nullObject = null;
    private int hovered = -1;
    private int selected = -1;
    private int chartSize;
    private int sum;

    public Chart() {
        super(new GridLayout(1, 1));

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int newHovered = getItem(e);
                if (newHovered != hovered) {
                    if (newHovered != -1) {
                        Item item = data.get(newHovered);
                        setToolTipText(item.label + ": " + item.count +
                            " (" + (item.count * 100 / sum) + "%)");
                    } else {
                        setToolTipText(null);
                    }
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

            @Override
            public void mouseClicked(MouseEvent e) {
                int newSelected = getItem(e);
                selected = newSelected == selected ? -1 : newSelected;
                itemSelectedListener.onSelect((selected == -1) ? nullObject : data.get(selected).object, selected);
                updateUI();
            }
        });
    }

    public void setItemSelectedListener(ItemSelectedListener<T> itemSelectedListener) {
        this.itemSelectedListener = itemSelectedListener;
    }

    public void setNullObject(T nullObject) {
        this.nullObject = nullObject;
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
            g2d.setStroke(new BasicStroke(SELECTED_LINE_WEIGHT));
            g2d.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            chartSize = Math.min(getWidth(), getHeight());
            int smallSize = (chartSize - 2 * SELECTED_LINE_WEIGHT) * CHART_WIDTH_PERCENT / 100;
            int bigSize = chartSize - 2 * SELECTED_LINE_WEIGHT;
            int smallStart = (chartSize - 2 * SELECTED_LINE_WEIGHT) * CHART_MARGIN_PERCENT / 100;
            for (int i = 0; i < data.size(); i++) {
                Item item = data.get(i);
                if (item.count == 0) {
                    continue;
                }
                g.setColor(item.color);
                if (selected != i) {
                    if (hovered == i) {
                        g.fillArc(SELECTED_LINE_WEIGHT, SELECTED_LINE_WEIGHT, bigSize, bigSize,
                            item.startAngle, item.angle);
                    } else {
                        g.fillArc(smallStart + SELECTED_LINE_WEIGHT, smallStart + SELECTED_LINE_WEIGHT,
                            smallSize, smallSize, item.startAngle, item.angle);
                    }
                }
            }
            if (selected > -1) {
                Item item = data.get(selected);
                if (item.count != 0) {
                    g.setColor(item.color);
                    g.fillArc(SELECTED_LINE_WEIGHT, SELECTED_LINE_WEIGHT, bigSize, bigSize,
                        item.startAngle, item.angle);
                    g.setColor(JBColor.BLACK);
                    g2d.draw(new Arc2D.Float(SELECTED_LINE_WEIGHT, SELECTED_LINE_WEIGHT, bigSize, bigSize,
                        item.startAngle, item.angle, Arc2D.PIE));
                }
            }
        }
    }

    public void clear(boolean silently) {
        if (data == null) {
            return;
        }
        data = null;
        if (!silently) {
            itemSelectedListener.onSelect(nullObject, -1);
        }
        repaint();
    }

    public void clear() {
        clear(false);
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

    private int getItem(MouseEvent e) {
        if (data == null) {
            return -1;
        }
        int x = e.getX() - chartSize / 2;
        int y = e.getY() - chartSize / 2;
        if (x * x + y * y <= chartSize * chartSize / 4) {
            int angle = (int) (180 * Math.atan2(-y, x) / Math.PI);
            if (angle < 0) {
                angle = 360 + angle;
            }
            for (int i = 0; i < data.size(); i++) {
                if (data.get(i).startAngle + data.get(i).angle > angle) {
                    return i;
                }
            }
            return data.size() - 1;
        }
        return -1;
    }

    public interface ItemSelectedListener<T> {
        void onSelect(T object, int position);
    }

    public Item newItem(T object, String label, int count) {
        return new Item(object, label, count);
    }

    public Item newItem(T object, String label, int count, Color color) {
        return new Item(object, label, count, color);
    }

    public void unselectWithoutListener() {
        selected = -1;
        updateUI();
    }

    public void unselect() {
        selected = -1;
        itemSelectedListener.onSelect(nullObject, -1);
        updateUI();
    }

    public class Item {
        private T object;
        private String label;
        private int count;
        private Color color;
        private int startAngle;
        private int angle;

        Item(T object, String label, int count) {
            this.object = object;
            this.label = label;
            this.count = count;
        }

        Item(T object, String label, int count, Color color) {
            this.object = object;
            this.label = label;
            this.count = count;
            this.color = color;
        }

        int getCount() {
            return count;
        }
    }
}
