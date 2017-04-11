package net.falsetrue.heapwalker.monitorings;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType;
import com.intellij.debugger.ui.breakpoints.LineBreakpoint;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.breakpoints.LineBreakpointState;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import com.sun.jdi.*;
import com.sun.jdi.event.AccessWatchpointEvent;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.request.AccessWatchpointRequest;
import net.falsetrue.heapwalker.util.map.ObjectTimeMap;
import net.falsetrue.heapwalker.util.TimeManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.debugger.breakpoints.properties.JavaFieldBreakpointProperties;
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties;

public class AccessMonitoring {
    private String stubFileName;
    private DebugProcessImpl debugProcess;
    private Project project;
    private TimeManager timeManager;
    private ReferenceType referenceType;
    private ObjectTimeMap objectTimeMap;
    private boolean enabled;
    private boolean started;

    public AccessMonitoring(@NotNull XDebugSession debugSession, ReferenceType type,
                            TimeManager timeManager, ObjectTimeMap objectTimeMap) {
        this.referenceType = type;
        project = debugSession.getProject();
        this.timeManager = timeManager;
        this.objectTimeMap = objectTimeMap;

        debugProcess = (DebugProcessImpl) DebuggerManager.getInstance(project)
            .getDebugProcess(debugSession.getDebugProcess().getProcessHandler());

        ApplicationManager.getApplication().runReadAction(() -> {
            stubFileName = JavaPsiFacade.getInstance(project).findClass("java.lang.Object",
                GlobalSearchScope.everythingScope(project)).getContainingFile().getName();
        });
    }

    private void startMonitoring() {
        XLineBreakpointImpl<JavaLineBreakpointProperties> bpn = new XLineBreakpointImpl<>(
            new JavaLineBreakpointType(),
            ((XDebuggerManagerImpl) XDebuggerManagerImpl.getInstance(project)).getBreakpointManager(),
            new JavaLineBreakpointProperties(),
            new LineBreakpointState<>());
        bpn.setFileUrl(stubFileName);
        debugProcess.getManagerThread().invoke(new DebuggerCommandImpl() {
            @Override
            protected void action() throws Exception {
                new BreakpointsResolver(project, bpn).createRequestForPreparedClass(debugProcess, referenceType);
            }
        });
        enabled = true;
    }

    private class BreakpointsResolver extends LineBreakpoint<JavaFieldBreakpointProperties> {
        BreakpointsResolver(Project project, XBreakpoint xBreakpoint) {
            super(project, xBreakpoint);
        }

        protected void createRequestForPreparedClass(DebugProcessImpl debugProcess, ReferenceType classType) {
            classType.fields().forEach((field) -> {
                AccessWatchpointRequest request = debugProcess.getRequestsManager()
                    .createAccessWatchpointRequest(this, field);
                request.enable();
            });
        }

        public boolean processLocatableEvent(SuspendContextCommandImpl action, LocatableEvent event) throws EventProcessingException {
            if (enabled) {
                objectTimeMap.put(((AccessWatchpointEvent)event).object(), timeManager.getTime());
            }
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled && !started) {
            startMonitoring();
            started = true;
        }
    }
}
