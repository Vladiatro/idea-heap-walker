package net.falsetrue.heapwalker.ui;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.components.JBList;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.frame.XDebuggerFramesList;
import com.sun.jdi.Location;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class FrameList extends JBList<FrameList.Item> {
    private List<Location> data;

    public FrameList(@NotNull Project project) {
        super(project);

        setModel(new DefaultListModel<>());

        addListSelectionListener(e -> {
            if (data == null
                || getSelectedIndex() < 0
                || getSelectedIndex() >= data.size()) {
                return;
            }
            Location location = data.get(getSelectedIndex());
            PsiClass psiClass = JavaPsiFacade.getInstance(project)
                .findClass(location.declaringType().name(), GlobalSearchScope.everythingScope(project));
            if (psiClass != null) {
                new OpenFileDescriptor(project, psiClass.getContainingFile().getVirtualFile(),
                    location.lineNumber() - 1, 0).navigate(true);
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
            }
        });
    }

    public void setData(List<Location> data) {
        this.data = data;
        DefaultListModel<Item> model = (DefaultListModel<Item>) getModel();
        model.clear();
        if (data != null) {
            for (Location datum : data) {
                model.addElement(new Item(datum));
            }
        }
    }

    class Item extends XStackFrame {
        private Location location;

        public Item(Location location) {
            this.location = location;
        }

        @Override
        public String toString() {
            return String.format("%s:%d, %s", location.method().name(), location.lineNumber(),
                location.declaringType().name());
        }
    }
}
