package net.falsetrue.heapwalker;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.jdi.VirtualMachineProxy;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.openapi.project.Project;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.sun.jdi.*;
import net.falsetrue.heapwalker.breakpoints.CreationMonitoring;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class MyPanel extends BorderLayoutPanel {
    private static final int UPDATE_TIME = 7000;
    private final ClassesTableModel model;

    private JBLabel countLabel;
    private JBTable table;
    private final Project project;
    private volatile List<ClassInstance> classInstances;
    private volatile boolean debugActive = false;
    private CreationMonitoring creationMonitoring;

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

    public MyPanel(Project project) {
        this.project = project;

        countLabel = new JBLabel("");
        addToBottom(countLabel);

        model = new ClassesTableModel();
        table = new JBTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        JScrollPane scroll = ScrollPaneFactory.createScrollPane(table, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

//        add(table);
        addToCenter(scroll);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                table.setSize(e.getComponent().getWidth(), e.getComponent().getHeight());
            }
        });
    }

    public void debugSessionStart(XDebugSession session) {
        XDebugSession debugSession = XDebuggerManager.getInstance(project).getCurrentSession();
        if (debugSession != null) {
            DebugProcessImpl debugProcess = (DebugProcessImpl) DebuggerManager
                .getInstance(project)
                .getDebugProcess(
                    debugSession
                        .getDebugProcess()
                        .getProcessHandler()
                );
            debugActive = true;
            new Thread(() -> {
                VirtualMachine vm;
                try {
                    vm = getVM(debugProcess);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }
                enableInstanceCreationMonitoring2(debugSession, vm);

                SwingUtilities.invokeLater(() -> {
                    (new DoubleClickListener() {
                        protected boolean onDoubleClick(MouseEvent event) {
                            handleClassSelection(debugProcess, vm,
                                classInstances.get(table.getSelectedRow()).type, creationMonitoring.getCreationPlaces());
                            return true;
                        }
                    }).installOn(table);
                });

                while (debugActive) {
                    try {
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
//                        break;
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

    private void enableInstanceCreationMonitoring(VirtualMachine vm) {
//        MethodEntryRequest request = vm.eventRequestManager().createMethodEntryRequest();
//        request.enable();
//        new Thread(() -> {
//            EventQueue eventQueue = vm.eventQueue();
//            while (debugActive) {
//                try {
//                    EventSet events = eventQueue.remove();
//                    EventIterator eventIterator = events.eventIterator();
//                    while (eventIterator.hasNext()) {
//                        Event event = eventIterator.nextEvent();
//                        if (event instanceof MethodEntryEvent) {
//                            MethodEntryEvent entryEvent = (MethodEntryEvent) event;
//                            Method method = entryEvent.method();
//                            if (method.isConstructor()) {
//                                ThreadReference thread = entryEvent.thread();
//                                ObjectReference object = thread.frame(0).thisObject();
//                                if (!creationPlaces.containsKey(object)) {
//                                    Location creationPlace = thread.frame(1).location();
//                                    creationPlaces.put(object, creationPlace);
////                                    System.out.println(creationPlace.sourceName() + ":"
////                                        + creationPlace.lineNumber());
//                                }
//                            }
//                        }
//                    }
//                    events.resume();
//                } catch (VMDisconnectedException e) {
//                    return;
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }
//            }
//        }).start();
    }

    private void handleClassSelection(DebugProcessImpl process,
                                      VirtualMachine virtualMachine,
                                      ReferenceType ref,
                                      Map<ObjectReference, Location> locationMap) {
        new InstancesWindow(process, virtualMachine, ref, locationMap).show();
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
