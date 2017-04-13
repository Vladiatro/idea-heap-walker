package net.falsetrue.heapwalker.ui;

import com.intellij.openapi.project.Project;
import net.falsetrue.heapwalker.MyStateService;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class BlackThresholdComboBox extends JComboBox<BlackThresholdComboBox.Item> {
    private static final Item[] ITEMS = {
        new Item("0.09 seconds", 90),
        new Item("0.9 seconds", 900),
        new Item("6 seconds", 6000),
        new Item("18 seconds", 18000),
        new Item("30 seconds", 30000),
        new Item("1 minute", 60000),
        new Item("3 minutes", 180000),
        new Item("5 minutes", 300000),
        new Item("10 minutes", 600000),
        new Item("15 minutes", 900000),
        new Item("30 minutes", 1800000),
        new Item("60 minutes", 3600000),
    };

    private MyStateService service;
    private ChangeListener changeListener;

    public BlackThresholdComboBox(Project project) {
        super(ITEMS);
        service = MyStateService.getInstance(project);
        int currentMilliseconds = service.getBlackAgeMilliseconds();
        for (int i = 0; i < ITEMS.length; i++) {
            if (ITEMS[i].milliseconds == currentMilliseconds) {
                setSelectedIndex(i);
                break;
            }
        }
        addActionListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        Item selectedItem = (Item) getSelectedItem();
        service.setBlackAgeMilliseconds(selectedItem.milliseconds);
        changeListener.onChange(selectedItem.milliseconds);
    }

    public void setChangeListener(ChangeListener changeListener) {
        this.changeListener = changeListener;
    }

    public interface ChangeListener {
        void onChange(int milliseconds);
    }

    public static class Item {
        String name;
        int milliseconds;

        public Item(String name, int milliseconds) {
            this.name = name;
            this.milliseconds = milliseconds;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
