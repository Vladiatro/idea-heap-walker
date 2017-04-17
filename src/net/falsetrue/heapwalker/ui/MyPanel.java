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
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.sun.jdi.*;
import net.falsetrue.heapwalker.ProfileSession;
import net.falsetrue.heapwalker.monitorings.CreationMonitoring;
import net.falsetrue.heapwalker.util.TimeManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    private volatile List<ClassInstance> classInstances;

    private ScheduledFuture<?> updaterHandle;

    private Map<XDebugSession, ProfileSession> profileSessions = new HashMap<>();
    private ProfileSession currentSession;
    private String preservableReferenceType;

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

    public MyPanel(Project project) {
        super(0.3f);
        this.project = project;

        countLabel = new JBLabel("");
//        addToBottom(countLabel);

        model = new ClassesTableModel();
        table = new JBTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getColumnModel().getColumn(0).setMaxWidth(900);
        table.getColumnModel().getColumn(1).setMaxWidth(300);

        SpeedSearchBase<JBTable> speedSearch = new SpeedSearchBase<JBTable>(table) {
            @Override
            protected int getSelectedIndex() {
                return table.getSelectedRow();
            }

            @Override
            protected Object[] getAllElements() {
                synchronized (model) {
                    final int count = model.getRowCount();
                    Object[] elements = new Object[count];
                    for (int idx = 0; idx < count; idx++) {
                        elements[idx] = model.getValueAt(idx, 0);
                    }
                    return elements;
                }
            }

            @Nullable
            @Override
            protected String getElementText(Object element) {
                return (String) element;
            }

            @Override
            protected void selectElement(Object element, String selectedText) {
                final int count = model.getRowCount();
                for (int row = 0; row < count; row++) {
                    if (element.equals(model.getValueAt(row, 0))) {
                        final int viewRow = table.convertRowIndexToView(row);
                        table.getSelectionModel().setSelectionInterval(viewRow, viewRow);
                        TableUtil.scrollSelectionToVisible(table);
                        handleClassSelection(classInstances.get(table.getSelectedRow()).type);
                        break;
                    }
                }
            }
        };
        speedSearch.setComparator(new SpeedSearchComparator(false));

        JScrollPane tableScroll = ScrollPaneFactory.createScrollPane(table, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        instancesView = new InstancesView(project);

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
            if (debugProcess.isAttached()) {
                debugProcess.getManagerThread().invoke(new DebuggerCommandImpl() {
                    @Override
                    protected void action() throws Exception {
                        attachProcess(debugSession, debugProcess);
                    }
                });
            }
            debugProcess.addDebugProcessListener(new DebugProcessListener() {
                @Override
                public void processAttached(DebugProcess process) {
                    attachProcess(debugSession, debugProcess);
                }

                @Override
                public void paused(SuspendContext suspendContext) {
                    if (currentSession != null) {
                        currentSession.getTimeManager().pause();
                    }
                    if (table.getSelectedRow() != -1 && classInstances.get(table.getSelectedRow()) != null) {
                        handleClassSelection(classInstances.get(table.getSelectedRow()).type);
                    }
                }

                @Override
                public void resumed(SuspendContext suspendContext) {
                    if (currentSession != null) {
                        currentSession.getTimeManager().resume();
                    }
                    if (table.getSelectedRow() != -1 && classInstances.get(table.getSelectedRow()) != null) {
                        handleClassSelection(classInstances.get(table.getSelectedRow()).type);
                    }
                }
            });
        }
    }

    private void attachProcess(XDebugSession debugSession, DebugProcessImpl debugProcess) {
        VirtualMachine vm;
        try {
            vm = getVM(debugProcess);
            if (vm == null) {
                return;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            return;
        }
        ProfileSession profileSession = new ProfileSession(debugProcess, vm);
        profileSessions.put(debugSession, profileSession);
        (new ClickListener() {
            @Override
            public boolean onClick(@NotNull MouseEvent event, int clickCount) {
                if (clickCount == 1 && table.getSelectedRow() != -1
                    && classInstances.get(table.getSelectedRow()) != null) {
                    handleClassSelection(classInstances.get(table.getSelectedRow()).type);
                    return true;
                }
                return false;
            }
        }).installOn(table);
        updateSession();
        profileSession.getTimeManager().start();
    }

    public void updateSession() {
        if (project.isDisposed()) {
            return;
        }
        XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
        if (!profileSessions.containsKey(session)) {
            debugSessionStart(session);
        } else if (currentSession == profileSessions.get(session)) {
            return;
        }
        currentSession = profileSessions.get(session);
        if (currentSession == null) {
            instancesView.clear();
            return;
        }
        instancesView.setProfileSession(currentSession);
        if (updaterHandle != null) {
            updaterHandle.cancel(true);
        }
        if (table.getSelectedRow() != -1) {
            preservableReferenceType = classInstances.get(table.getSelectedRow()).type.name();
        }
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        updaterHandle = scheduler.scheduleAtFixedRate(() -> {
            try {
                List<ReferenceType> classes = currentSession.getVirtualMachine().allClasses();
                ReferenceType selected = null;
                if (preservableReferenceType != null) {
                    List<ReferenceType> types = currentSession.getVirtualMachine().classesByName(preservableReferenceType);
                    if (types.size() > 0) {
                        selected = types.get(0);
                    }
                } else if (table.getSelectedRow() != -1) {
                    selected = classInstances.get(table.getSelectedRow()).type;
                }
                long[] counts = currentSession.getVirtualMachine().instanceCounts(classes);
                Iterator<ReferenceType> iterator = classes.iterator();
                classInstances = new ArrayList<>();
                for (long count : counts) {
                    classInstances.add(new ClassInstance(iterator.next(), count));
                }
                Collections.sort(classInstances);
                int selectedIndex = 0;
                synchronized (model) {
                    model.clear();
                    int index = 0;
                    for (ClassInstance classInstance : classInstances) {
                        model.add(classInstance.type.name(), classInstance.count);
                        if (classInstance.type.equals(selected)) {
                            final int finalIndex = selectedIndex = index;
                            SwingUtilities.invokeLater(() -> {
                                table.setRowSelectionInterval(finalIndex,
                                    finalIndex);
                                table.scrollRectToVisible(table.getCellRect(finalIndex, 0, true));
                            });
                        }
                        index++;
                    }
                }
                SwingUtilities.invokeLater(() -> table.updateUI());
                String plural = classes.size() % 10 == 1 ? "class" : "classes";
                countLabel.setText(classes.size() + " loaded " + plural);
                if (preservableReferenceType != null) {
                    instancesView.update(classInstances.get(selectedIndex).type, null);
                    preservableReferenceType = null;
                }
            } catch (VMDisconnectedException e) {
//                               updaterHandle.cancel(false);
            }
        }, 0, UPDATE_TIME, MILLISECONDS);
        if (table.getSelectedRow() < 0 || table.getSelectedRow() >= classInstances.size()) {
            instancesView.clear();
        }
    }

    public void debugSessionStop(XDebugSession session) {
        profileSessions.remove(session);
        if (profileSessions.size() == 0 && updaterHandle != null) {
            updaterHandle.cancel(false);
            updaterHandle = null;
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
                    table.setRowSelectionInterval(i, i);
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
