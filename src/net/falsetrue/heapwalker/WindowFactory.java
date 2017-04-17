package net.falsetrue.heapwalker;

import com.intellij.execution.Executor;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunContentWithExecutorListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerManagerListener;
import net.falsetrue.heapwalker.ui.MyPanel;
import net.falsetrue.heapwalker.util.TimeManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class WindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        MyPanel panel = new MyPanel(project);
        MyStateService.getInstance(project).setPanel(panel);
        Content content = ContentFactory.SERVICE.getInstance().createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);

        MessageBusConnection connection = project.getMessageBus().connect(project);
        connection.subscribe(XDebuggerManager.TOPIC, new XDebuggerManagerListener() {
            @Override
            public void processStarted(@NotNull XDebugProcess xDebugProcess) {
//                SwingUtilities.invokeLater(() -> {
//                    XDebugSession session = xDebugProcess.getSession();
//                    panel.debugSessionStart(session);
//                });
            }

            @Override
            public void processStopped(@NotNull XDebugProcess xDebugProcess) {
                SwingUtilities.invokeLater(() -> {
                    XDebugSession session = xDebugProcess.getSession();
                    panel.debugSessionStop(session);
                });
            }
        });
        connection.subscribe(RunContentManager.TOPIC, new RunContentWithExecutorListener() {
            @Override
            public void contentSelected(@Nullable RunContentDescriptor descriptor, @NotNull Executor executor) {
                panel.updateSession();
            }

            @Override
            public void contentRemoved(@Nullable RunContentDescriptor descriptor, @NotNull Executor executor) {
                panel.updateSession();
            }
        });
    }
}