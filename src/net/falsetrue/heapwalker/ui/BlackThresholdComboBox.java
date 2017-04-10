package net.falsetrue.heapwalker.ui;

import com.intellij.openapi.project.Project;
import net.falsetrue.heapwalker.MyStateService;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class BlackThresholdComboBox extends JComboBox<BlackThresholdComboBox.Item> {
    private static final Item[] ITEMS = {
        new Item("30 seconds", 30),
        new Item("1 minute", 60),
        new Item("3 minutes", 180),
        new Item("5 minutes", 300),
        new Item("10 minutes", 600),
        new Item("15 minutes", 900),
        new Item("30 minutes", 1800),
        new Item("60 minutes", 3600),
    };

    private MyStateService service;
    private ChangeListener changeListener;

    public BlackThresholdComboBox(Project project) {
        super(ITEMS);
        service = MyStateService.getInstance(project);
        int currentSeconds = service.getBlackAgeSeconds();
        for (int i = 0; i < ITEMS.length; i++) {
            if (ITEMS[i].seconds == currentSeconds) {
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
        service.setBlackAgeSeconds(selectedItem.seconds);
        changeListener.onChange(selectedItem.seconds);
    }

    public void setChangeListener(ChangeListener changeListener) {
        this.changeListener = changeListener;
    }

    public interface ChangeListener {
        void onChange(int seconds);
    }

    public static class Item {
        String name;
        int seconds;

        public Item(String name, int seconds) {
            this.name = name;
            this.seconds = seconds;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
