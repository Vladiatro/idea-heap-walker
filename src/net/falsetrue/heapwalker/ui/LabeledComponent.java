package net.falsetrue.heapwalker.ui;

import javax.swing.*;
import java.awt.*;

public class LabeledComponent extends JPanel {
    public LabeledComponent(String label, JComponent component) {
        super(new FlowLayout());
        add(new JLabel(label));
        add(component);
    }
}
