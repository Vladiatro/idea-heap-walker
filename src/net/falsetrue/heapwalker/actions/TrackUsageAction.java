package net.falsetrue.heapwalker.actions;

import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.execution.ExecutionBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.ArrayType;
import com.sun.jdi.ReferenceType;
import net.falsetrue.heapwalker.monitorings.AccessMonitoring;
import net.falsetrue.heapwalker.monitorings.CreationMonitoring;
import net.falsetrue.heapwalker.util.ObjectTimeMap;
import net.falsetrue.heapwalker.util.TimeManager;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class TrackUsageAction extends ToggleAction {
    private boolean mySelected = false;
    private AccessMonitoring currentMonitoring;
    private Map<ReferenceType, AccessMonitoring> monitorings = new HashMap<>();
    private boolean enabled;

    public TrackUsageAction() {
        Presentation templatePresentation = getTemplatePresentation();
        templatePresentation.setIcon(AllIcons.Actions.StartDebugger);
        templatePresentation.setText("Track usage");
        templatePresentation.setDescription(null);
    }

    public void setReferenceType(ObjectTimeMap objectTimeMap,
                                 XDebugSession debugSession,
                                 ReferenceType referenceType,
                                 TimeManager timeManager) {
        currentMonitoring = monitorings.computeIfAbsent(referenceType,
            k -> new AccessMonitoring(debugSession, referenceType, timeManager, objectTimeMap));
        mySelected = currentMonitoring.isEnabled();
        enabled = !(referenceType instanceof ArrayType);
    }

    public boolean isSelected() {
        return mySelected;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
        return mySelected;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
        mySelected = state;
        currentMonitoring.setEnabled(mySelected);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        final Presentation presentation = e.getPresentation();
        presentation.setEnabled(enabled);
    }
}
