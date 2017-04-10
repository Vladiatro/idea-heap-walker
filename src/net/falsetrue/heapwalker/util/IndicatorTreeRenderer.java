package net.falsetrue.heapwalker.util;

import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.xdebugger.impl.ui.tree.XValueExtendedPresentation;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import net.falsetrue.heapwalker.InstanceJavaValue;
import net.falsetrue.heapwalker.ui.Chart;

import javax.swing.*;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;

import static net.falsetrue.heapwalker.util.TimeManager.BLACK_AGE;

public class IndicatorTreeRenderer implements TreeCellRenderer {
    private TreeCellRenderer standardRenderer;
    private ObjectTimeMap objectTimeMap;
    private TimeManager timeManager;

    public IndicatorTreeRenderer(TreeCellRenderer standardRenderer,
                                 ObjectTimeMap objectTimeMap,
                                 TimeManager timeManager) {
        this.standardRenderer = standardRenderer;
        this.objectTimeMap = objectTimeMap;
        this.timeManager = timeManager;
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                  boolean expanded, boolean leaf, int row, boolean hasFocus) {

        Component component = standardRenderer.getTreeCellRendererComponent(tree, value, selected, expanded,
            leaf, row, hasFocus);
        TreePath path = tree.getPathForRow(row);
        if (path != null && path.getPathCount() == 2 && value instanceof XValueNodeImpl && timeManager.isPaused()
            && ((XValueNodeImpl) value).getValueContainer() instanceof InstanceJavaValue) {
            InstanceJavaValue javaValue = (InstanceJavaValue) ((XValueNodeImpl) value).getValueContainer();
            long time = objectTimeMap.get(javaValue.getObjectReference());
            Color color;
            if (time == -1) {
                color = Color.BLACK;
            } else {
                time = timeManager.getTime() - time;
                color = getColor((int) (time * 765 / BLACK_AGE));
            }
            YoPanel panel = new YoPanel();
            panel.add(new Indicator(color));
            panel.add(component);
            return panel;
        }
        return component;
    }

    @SuppressWarnings("UseJBColor")
    private Color getColor(int value) {
        if (value < 256) {
            return new Color(value, 255, 0);
        } else if (value < 512) {
            return new Color(255, 511 - value, 0);
        }
        return new Color(Math.max(0, 767 - value), 0, 0);
    }

    private class YoPanel extends JPanel {
        public YoPanel() {
            super(new HorizontalLayout(0, 0));
            getInsets().set(0, 0, 0, 0);
        }
    }

    private class Indicator extends JPanel {
        public Indicator(Color color) {
            JLabel label = new JLabel("    ");
            label.setFont(new Font("Sans-Serif", Font.PLAIN, 10));
            add(label);
            setBackground(color);
        }
    }
}
