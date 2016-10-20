package net.falsetrue.heapwalker;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.jdi.VirtualMachineProxy;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public class MyPanel extends JPanel {
    private JBLabel countLabel;
    private JBList list;
    private final Project project;
    private volatile BlockingQueue<VirtualMachineProxy> proxyQueue = new LinkedBlockingDeque<>();

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
                        List classes =
                                proxy
                                .allClasses();
                        countLabel.setText(classes.size() + " classes");

                        classes.forEach(o -> {
                            model.addElement(o.toString());
                            System.out.println(o.toString());
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
