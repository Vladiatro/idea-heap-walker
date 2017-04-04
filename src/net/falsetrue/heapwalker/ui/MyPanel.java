package net.falsetrue.heapwalker.ui;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebugProcessListener;
import com.intellij.debugger.engine.SuspendContext;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.jdi.VirtualMachineProxy;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ClickListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.*;
import net.falsetrue.heapwalker.monitorings.CreationMonitoring;
import net.falsetrue.heapwalker.util.TimeManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class MyPanel extends BorderLayoutPanel {
    private static final int UPDATE_TIME = 4000;
    private final ClassesTableModel model;

    private JBLabel countLabel;
    private JBTable table;
    private InstancesView instancesView;
    private final Project project;
    private TimeManager timeManager;
    private volatile List<ClassInstance> classInstances;
    private volatile boolean debugActive = false;
    private CreationMonitoring creationMonitoring;

    private DebugProcessListener listener;

    private VirtualMachine getVM(DebugProcessImpl debugProcess) throws InterruptedException {
        BlockingQueue<VirtualMachineProxy> proxyQueue = new ArrayBlockingQueue<>(1);
        debugProcess.getManagerThread().invoke(new DebuggerCommandImpl() {
            @Override
            protected void action() throws Exception {
                proxyQueue.add(debugProcess.getVirtualMachineProxy());
            }
        });
        VirtualMachineProxy proxy = proxyQueue.take();
        if (!(proxy instanceof VirtualMachineProxyImpl)) {
            throw new RuntimeException("Can't connect to VirtualMachine");
        }
        return ((VirtualMachineProxyImpl) proxy).getVirtualMachine();
    }

    public MyPanel(Project project, TimeManager timeManager) {
        this.project = project;
        this.timeManager = timeManager;

        countLabel = new JBLabel("");
//        addToBottom(countLabel);

        model = new ClassesTableModel();
        table = new JBTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getColumnModel().getColumn(0).setMaxWidth(900);
//        XDebuggerManager.getInstance(project).getCurrentSession().getUI().
        table.getColumnModel().getColumn(1).setMaxWidth(300);
        JScrollPane tableScroll = ScrollPaneFactory.createScrollPane(table, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        instancesView = new InstancesView(project, timeManager);
//        JScrollPane instancesScroll = ScrollPaneFactory.createScrollPane(instancesView,
//            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        addToLeft(tableScroll);
        addToCenter(instancesView);
    }

    public void debugSessionStart(XDebugSession debugSession) {
        if (debugSession != null) {
            DebugProcessImpl debugProcess = (DebugProcessImpl) DebuggerManager
                .getInstance(project)
                .getDebugProcess(
                    debugSession
                        .getDebugProcess()
                        .getProcessHandler()
                );
            debugActive = true;
            if (listener != null) {
                debugProcess.removeDebugProcessListener(listener);
            }
            debugProcess.addDebugProcessListener(listener = new DebugProcessListener() {
                @Override
                public void paused(SuspendContext suspendContext) {
                    timeManager.pause();
                    handleClassSelection(classInstances.get(table.getSelectedRow()).type);
                }

                @Override
                public void resumed(SuspendContext suspendContext) {
                    timeManager.resume();
                    handleClassSelection(classInstances.get(table.getSelectedRow()).type);
                }
            });
            instancesView.setDebugProcess(debugProcess);
            new Thread(() -> {
                VirtualMachine vm;
                try {
                    vm = getVM(debugProcess);
                    instancesView.setVirtualMachine(vm);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }
                enableInstanceCreationMonitoring2(debugSession, vm);

                SwingUtilities.invokeLater(() -> {
                    (new ClickListener() {
                        @Override
                        public boolean onClick(@NotNull MouseEvent event, int clickCount) {
                            if (clickCount == 1) {
                                handleClassSelection(classInstances.get(table.getSelectedRow()).type);
                                return true;
                            }
                            return false;
                        }
                    }).installOn(table);
                });

                while (debugActive) {
                    try {
//                        synchronized (MyPanel.this) {
                            List<ReferenceType> classes = vm.allClasses();
                            long[] counts = vm.instanceCounts(classes);
                            Iterator<ReferenceType> iterator = classes.iterator();
                            classInstances = new ArrayList<>();
                            for (long count : counts) {
                                classInstances.add(new ClassInstance(iterator.next(), count));
                            }
                            Collections.sort(classInstances);
                            model.clear();
                            classInstances.forEach(classInstance -> {
                                model.add(classInstance.type.name(), classInstance.count);
                            });
                            table.updateUI();
                            String plural = classes.size() % 10 == 1 ? "class" : "classes";
                            countLabel.setText(classes.size() + " loaded " + plural);
                            Thread.sleep(UPDATE_TIME);
//                        }
                    } catch (VMDisconnectedException e) {
                        break;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break;
                    }
                }
            }).start();
        }
    }

    public void debugSessionStop(XDebugSession session) {
        debugActive = false;
    }

    private void enableInstanceCreationMonitoring2(XDebugSession debugSession, VirtualMachine vm) {
        creationMonitoring = new CreationMonitoring(debugSession, vm);
    }

    private void handleClassSelection(ReferenceType referenceType) {
        instancesView.update(referenceType, creationMonitoring.getCreationPlaces(), null);
    }

    public void showReference(ObjectReference reference) {
//        synchronized (this) {
            ReferenceType referenceType = reference.referenceType();
            for (int i = 0; i < classInstances.size(); i++) {
                if (classInstances.get(i).type.equals(referenceType)) {
                    table.clearSelection();
                    table.addRowSelectionInterval(i, i);
                    table.scrollRectToVisible(table.getCellRect(i, 0, true));
                    instancesView.update(referenceType, creationMonitoring.getCreationPlaces(), reference);
                    break;
                }
            }
//        }
    }

    class ClassInstance implements Comparable<ClassInstance> {
        ReferenceType type;
        long count;

        ClassInstance(ReferenceType type, long count) {
            this.type = type;
            this.count = count;
        }

        @Override
        public int compareTo(@NotNull ClassInstance o) {
            return Long.compare(o.count, count);
        }

        @Override
        public String toString() {
            return type.name() + " - " + count;
        }
    }
}
