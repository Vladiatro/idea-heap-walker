package net.falsetrue.heapwalker;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.jdi.VirtualMachineProxy;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public class MyPanel extends BorderLayoutPanel {
    private JBLabel countLabel;
    private JBTable table;
    private final Project project;
    private volatile BlockingQueue<VirtualMachineProxy> proxyQueue = new LinkedBlockingDeque<>();

    private VirtualMachine getVM(VirtualMachineProxy proxy) {
        try {
            java.lang.reflect.Field[] fields = proxy.getClass().getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                if (field.get(proxy) instanceof VirtualMachine) {
                    return (VirtualMachine) field.get(proxy);
                }
            }
            throw new RuntimeException("Can't connect to VirtualMachine");
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public MyPanel(Project project) {
        this.project = project;

        countLabel = new JBLabel("Press Magic Button when breakpoint reached");
        addToBottom(countLabel);

        ClassesTableModel model = new ClassesTableModel();
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

        // outputs current classes
        JButton button = new JButton("Magic button");
        button.addActionListener(e -> {
            model.clear();
            XDebugSession debugSession = XDebuggerManager.getInstance(project).getCurrentSession();
            if (debugSession != null) {
                DebugProcessImpl debugProcess = (DebugProcessImpl) DebuggerManager
                        .getInstance(project)
                        .getDebugProcess(
                                debugSession
                                        .getDebugProcess()
                                        .getProcessHandler()
                        );
                new SwingWorker<Void, Void>() {
                    private VirtualMachineProxy proxy;

                    @Override
                    protected Void doInBackground() throws Exception {
                        proxy = proxyQueue.take();
                        return null;
                    }

                    @Override
                    protected void done() {
                        VirtualMachine vm = getVM(proxy);
                        List<ReferenceType> classes =
                                vm
                                .allClasses();
                        List<ClassInstance> classInstances = new ArrayList<>();
                        countLabel.setText(classes.size() + " classes");
                        classes.forEach(o -> {
                            long count = vm.instanceCounts(Collections.singletonList(o))[0];
                            classInstances.add(new ClassInstance(o, count));
//                            System.out.println(o.name() + "-" + vm.instanceCounts(Collections.singletonList(o))[0]);
                        });
                        Collections.sort(classInstances);
                        classInstances.forEach(classInstance -> {
                            System.out.println(classInstance);
                            model.add(classInstance.type.name(), classInstance.count);
                        });
                        table.updateUI();

                        vm.allThreads().forEach(threadReference -> {
                            System.out.println("\nThread " + threadReference.name());
                            try {
                                List<StackFrame> frames = threadReference.frames();
                                frames.forEach(frame -> {
                                    System.out.println(" " + frame.location());
                                    try {
                                        frame
                                            .visibleVariables()
                                            .forEach(
                                                variable ->
                                                    System.out.println("  "
                                                        + variable.typeName()
                                                        + " " + variable.name()
                                                        + " = " + frame.getValue(variable))
                                            );
                                    } catch (AbsentInformationException e1) {
                                        System.out.println("  n/a");
                                    }
                                });
                            } catch (IncompatibleThreadStateException e1) {
                                System.out.println(" n/a    ");
                            }
                        });
                    }
                }.execute();
                debugProcess.getManagerThread().invoke(new DebuggerCommandImpl() {
                    @Override
                    protected void action() throws Exception {
                        proxyQueue.add(debugProcess.getVirtualMachineProxy());
                    }
                });
            }
        });
        addToBottom(button);
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
