package net.falsetrue.heapwalker;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.jdi.VirtualMachineProxy;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.tools.jdi.ClassTypeImpl;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public class MyPanel extends JPanel {
    private JBLabel countLabel;
    private JBList list;
    private final Project project;
    private volatile BlockingQueue<VirtualMachineProxy> proxyQueue = new LinkedBlockingDeque<>();

    private VirtualMachine getVM(VirtualMachineProxy proxy) {
        try {
            java.lang.reflect.Field field = proxy.getClass().getDeclaredField("l");
            field.setAccessible(true);
            return (VirtualMachine) field.get(proxy);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public MyPanel(Project project) {
        this.project = project;

        countLabel = new JBLabel("Press Magic Button when breakpoint reached");
        add(countLabel);

        DefaultListModel model = JBList.createDefaultListModel();
        list = new JBList(model);
        add(list);

        // outputs current classes
        JButton button = new JButton("Magic button");
        button.addActionListener(e -> {
            model.removeAllElements();
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
                                proxy
                                .allClasses();
                        countLabel.setText(classes.size() + " classes");
                        classes.forEach(o -> {
                            System.out.println(vm.instanceCounts(Collections.singletonList(o))[0]);
                            model.addElement(o.toString());
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
        add(button);
    }
}
