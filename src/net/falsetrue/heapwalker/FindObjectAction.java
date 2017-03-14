package net.falsetrue.heapwalker;

import com.intellij.debugger.engine.JavaValue;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.xdebugger.impl.ui.tree.XValueExtendedPresentation;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.Value;
import com.sun.tools.jdi.ArrayReferenceImpl;
import com.sun.tools.jdi.ObjectReferenceImpl;
import org.jetbrains.annotations.NotNull;

/**
 * Created by vladiator on 14.03.17.
 */
public class FindObjectAction extends XDebuggerTreeActionBase {
    @Override
    protected boolean isEnabled(@NotNull XValueNodeImpl node, @NotNull AnActionEvent e) {
        XValueNodeImpl selectedNode = XDebuggerTreeActionBase.getSelectedNode(e.getDataContext());
        if (selectedNode != null && selectedNode.getValueContainer() instanceof JavaValue) {
            Value value = ((JavaValue) selectedNode.getValueContainer()).getDescriptor().getValue();
            return value instanceof ObjectReferenceImpl;
        }
        return false;
    }

    @Override
    protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
        XValueNodeImpl selectedNode = XDebuggerTreeActionBase.getSelectedNode(e.getDataContext());
        if (selectedNode != null && selectedNode.getValueContainer() instanceof JavaValue) {
            Value value = ((JavaValue) selectedNode.getValueContainer()).getDescriptor().getValue();
            if (value instanceof ObjectReferenceImpl) {
                ObjectReferenceImpl objectReference = (ObjectReferenceImpl) value;
                ToolWindowManager.getInstance(e.getProject()).getToolWindow("Memory content").show(() -> {
                    MyStateService.getInstance(e.getProject()).getPanel().showReference(objectReference);
                });
            }
        }
    }
}
