package net.falsetrue.heapwalker.monitorings;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.requests.ClassPrepareRequestor;
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
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import net.falsetrue.heapwalker.ProfileSession;
import net.falsetrue.heapwalker.util.TimeManager;
import net.falsetrue.heapwalker.util.map.ObjectMap;
import net.falsetrue.heapwalker.util.map.ObjectTimeMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties;

import java.util.*;

public class CreationMonitoring {
    private String stubFileName;
    private DebugProcessImpl debugProcess;
    private Project project;
    private final ReferenceType referenceType;
    private final ProfileSession profileSession;
    private boolean enabled;
    private boolean started;

    public CreationMonitoring(ReferenceType referenceType,
                              ProfileSession profileSession) {
        this.referenceType = referenceType;
        this.profileSession = profileSession;
        project = profileSession.getDebugSession().getProject();

        debugProcess = (DebugProcessImpl) DebuggerManager.getInstance(project)
            .getDebugProcess(profileSession.getDebugProcess().getProcessHandler());

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
    }

    private class BreakpointsResolver extends LineBreakpoint<JavaLineBreakpointProperties> {
        BreakpointsResolver(Project project, XBreakpoint xBreakpoint) {
            super(project, xBreakpoint);
        }

        protected void createRequestForPreparedClass(DebugProcessImpl debugProcess, ReferenceType classType) {
            classType.methods().stream().filter(Method::isConstructor).forEach((cons) -> {
                Location loc = cons.location();
                BreakpointRequest breakpointRequest = debugProcess.getRequestsManager()
                    .createBreakpointRequest(this, loc);
                breakpointRequest.enable();
            });
        }

        public boolean processLocatableEvent(SuspendContextCommandImpl action, LocatableEvent event) throws EventProcessingException {
            synchronized (CreationMonitoring.this) {
                if (!enabled) {
                    return false;
                }
            }
            try {
                ThreadReference thread = event.thread();
                ObjectReference object = thread.frame(0).thisObject();
                profileSession.getCreationPlaces().putIfAbsent(object, () -> {
                    try {
                        List<Location> stack = new ArrayList<>(thread.frameCount());
                        for (int i = 0; i < thread.frameCount(); i++) {
                            stack.add(thread.frame(i).location());
                        }
                        return new CreationInfo(stack, project, profileSession.getTimeManager().getTime());
                    } catch (IncompatibleThreadStateException e) {
                        e.printStackTrace();
                        return null;
                    }
                });
            } catch (IncompatibleThreadStateException e) {
                e.printStackTrace();
            }
            return false;
        }
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!started) {
            started = true;
            startMonitoring();
        }
    }
}
