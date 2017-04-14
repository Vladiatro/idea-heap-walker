package net.falsetrue.heapwalker.ui;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebugProcessListener;
import com.intellij.debugger.engine.SuspendContext;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.jdi.VirtualMachineProxy;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ClickListener;
import com.intellij.ui.JBSplitter;
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
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class MyPanel extends JBSplitter {
    private static final int UPDATE_TIME = 4000;
    private final ClassesTableModel model;

    private JBLabel countLabel;
    private JBTable table;
    private InstancesView instancesView;
    private final Project project;
    private TimeManager timeManager;
    private volatile List<ClassInstance> classInstances;
    private volatile boolean debugActive = false;

    private DebugProcessListener listener;
    private ScheduledFuture<?> updaterHandle;

    private VirtualMachine getVM(DebugProcessImpl debugProcess) throws InterruptedException {
        BlockingQueue<VirtualMachineProxy> proxyQueue = new ArrayBlockingQueue<>(1);
        debugProcess.getManagerThread().invoke(new DebuggerCommandImpl() {
            @Override
            protected void action() throws Exception {
                try {
                    proxyQueue.add(debugProcess.getVirtualMachineProxy());
                } catch (VMDisconnectedException e) {
                    throw new RuntimeException("Can't connect to VirtualMachine");
                }
            }
        });
        VirtualMachineProxy proxy = proxyQueue.take();
        if (proxy == null) {
            return null;
        }
        if (!(proxy instanceof VirtualMachineProxyImpl)) {
            throw new RuntimeException("Can't connect to VirtualMachine");
        }
        return ((VirtualMachineProxyImpl) proxy).getVirtualMachine();
    }

    public MyPanel(Project project, TimeManager timeManager) {
        super(0.3f);
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

        setFirstComponent(tableScroll);
        setSecondComponent(instancesView);
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
            debugProcess.addDebugProcessListener(new DebugProcessListener() {
                @Override
                public void processAttached(DebugProcess process) {
                    VirtualMachine vm;
                    try {
                        vm = getVM(debugProcess);
                        if (vm == null) {
                            return;
                        }
                        instancesView.setVirtualMachine(vm);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        return;
                    }
//                SwingUtilities.invokeLater(() -> {
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
//                });

                    debugActive = true;
                    if (listener != null) {
                        debugProcess.removeDebugProcessListener(listener);
                    }
                    debugProcess.addDebugProcessListener(listener = new DebugProcessListener() {
                        @Override
                        public void paused(SuspendContext suspendContext) {
                            timeManager.pause();
                            if (table.getSelectedRow() != -1 && classInstances.get(table.getSelectedRow()) != null) {
                                handleClassSelection(classInstances.get(table.getSelectedRow()).type);
                            }
                        }

                        @Override
                        public void resumed(SuspendContext suspendContext) {
                            timeManager.resume();
                            if (table.getSelectedRow() != -1 && classInstances.get(table.getSelectedRow()) != null) {
                                handleClassSelection(classInstances.get(table.getSelectedRow()).type);
                            }
                        }
                    });
                    instancesView.setDebugProcess(debugProcess);

                    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
                    updaterHandle = scheduler.scheduleAtFixedRate(() -> {
                        try {
                            List<ReferenceType> classes = vm.allClasses();
                            ReferenceType selected = null;
                            if (table.getSelectedRow() != -1) {
                                selected = classInstances.get(table.getSelectedRow()).type;
                            }
                            long[] counts = vm.instanceCounts(classes);
                            Iterator<ReferenceType> iterator = classes.iterator();
                            classInstances = new ArrayList<>();
                            for (long count : counts) {
                                classInstances.add(new ClassInstance(iterator.next(), count));
                            }
                            Collections.sort(classInstances);
                            model.clear();
                            int index = 0;
                            for (ClassInstance classInstance : classInstances) {
                                model.add(classInstance.type.name(), classInstance.count);
                                if (classInstance.type.equals(selected)) {
                                    int finalIndex = index;
                                    SwingUtilities.invokeLater(() -> table.setRowSelectionInterval(finalIndex, finalIndex));
                                }
                                index++;
                            }
                            SwingUtilities.invokeLater(() -> table.updateUI());
                            String plural = classes.size() % 10 == 1 ? "class" : "classes";
                            countLabel.setText(classes.size() + " loaded " + plural);
                        } catch (VMDisconnectedException e) {
                            updaterHandle.cancel(false);
                        }
                    }, 0, UPDATE_TIME, MILLISECONDS);
                }
            });
        }
    }

    public void debugSessionStop(XDebugSession session) {
        debugActive = false;
        if (updaterHandle != null) {
            updaterHandle.cancel(false);
            SwingUtilities.invokeLater(model::clear);
            instancesView.clear();
        }
    }

    private void handleClassSelection(ReferenceType referenceType) {
        instancesView.update(referenceType, null);
    }

    public void showReference(ObjectReference reference) {
//        synchronized (this) {
            ReferenceType referenceType = reference.referenceType();
            for (int i = 0; i < classInstances.size(); i++) {
                if (classInstances.get(i).type.equals(referenceType)) {
                    table.clearSelection();
                    table.addRowSelectionInterval(i, i);
                    table.scrollRectToVisible(table.getCellRect(i, 0, true));
                    instancesView.update(referenceType, reference);
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
