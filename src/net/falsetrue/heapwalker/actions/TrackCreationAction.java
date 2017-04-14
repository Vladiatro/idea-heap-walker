package net.falsetrue.heapwalker.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.ArrayType;
import com.sun.jdi.ReferenceType;
import net.falsetrue.heapwalker.monitorings.AccessMonitoring;
import net.falsetrue.heapwalker.monitorings.CreationInfo;
import net.falsetrue.heapwalker.monitorings.CreationMonitoring;
import net.falsetrue.heapwalker.util.TimeManager;
import net.falsetrue.heapwalker.util.map.ObjectMap;
import net.falsetrue.heapwalker.util.map.ObjectTimeMap;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class TrackCreationAction extends ToggleAction {
    private boolean mySelected = false;
    private CreationMonitoring currentMonitoring;
    private Map<ReferenceType, CreationMonitoring> monitorings = new HashMap<>();
    private boolean enabled;

    public TrackCreationAction() {
        Presentation templatePresentation = getTemplatePresentation();
        templatePresentation.setIcon(AllIcons.Actions.New);
        templatePresentation.setText("Track creation");
        templatePresentation.setDescription(null);
    }

    public void setReferenceType(ObjectMap<CreationInfo> creationPlaces,
                                 XDebugSession debugSession,
                                 ReferenceType referenceType,
                                 TimeManager timeManager) {
        currentMonitoring = monitorings.computeIfAbsent(referenceType,
            k -> new CreationMonitoring(debugSession, referenceType, timeManager, creationPlaces));
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
