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
    private TimeManager timeManager;
    private XDebugSession debugSession;
    private ObjectMap<CreationInfo> creationPlaces;

    private Set<ReferenceType> trackedTypes = new HashSet<>();

    public CreationMonitoring(@NotNull XDebugSession debugSession, VirtualMachine virtualMachine, TimeManager timeManager) {
        this.debugSession = debugSession;
        this.timeManager = timeManager;
        project = debugSession.getProject();

        creationPlaces = new ObjectMap<>();

        debugProcess = (DebugProcessImpl) DebuggerManager.getInstance(project)
            .getDebugProcess(debugSession.getDebugProcess().getProcessHandler());

        ApplicationManager.getApplication().runReadAction(() -> {
            stubFileName = JavaPsiFacade.getInstance(project).findClass("java.lang.Object",
                GlobalSearchScope.everythingScope(project)).getContainingFile().getName();
        });

        new ClassPrepareResolver().createRequest();
        for (ReferenceType referenceType : virtualMachine.allClasses()) {
            startMonitoring(referenceType);
        }
    }

    private void startMonitoring(ReferenceType type) {
        if (trackedTypes.contains(type)) {
            return;
        }
        trackedTypes.add(type);
        XLineBreakpointImpl<JavaLineBreakpointProperties> bpn = new XLineBreakpointImpl<>(
            new JavaLineBreakpointType(),
            ((XDebuggerManagerImpl) XDebuggerManagerImpl.getInstance(project)).getBreakpointManager(),
            new JavaLineBreakpointProperties(),
            new LineBreakpointState<>());
        bpn.setFileUrl(stubFileName);
        debugProcess.getManagerThread().invoke(new DebuggerCommandImpl() {
            @Override
            protected void action() throws Exception {
                new BreakpointsResolver(project, bpn).createRequestForPreparedClass(debugProcess, type);
            }
        });
    }

    private class ClassPrepareResolver implements ClassPrepareRequestor {
        private List<ClassPrepareRequest> classPrepareRequests = new ArrayList<>();

        void createRequest() {
            debugProcess.getRequestsManager().createClassPrepareRequest(this, "*").enable();
        }

        @Override
        public void processClassPrepare(DebugProcess debuggerProcess, ReferenceType referenceType) {
//            System.out.println("Preparation of " + referenceType);
            startMonitoring(referenceType);
        }
    }

    private class BreakpointsResolver extends LineBreakpoint<JavaLineBreakpointProperties> {
//        private List<BreakpointRequest> constructorRequests = new ArrayList<>();

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
            try {
                ThreadReference thread = event.thread();
                ObjectReference object = thread.frame(0).thisObject();
                creationPlaces.putIfAbsent(object, () -> {
                    try {
                        List<Location> stack = new ArrayList<>(thread.frameCount());
                        for (int i = 0; i < thread.frameCount(); i++) {
                            stack.add(thread.frame(i).location());
                        }
                        return new CreationInfo(stack, project, timeManager.getTime());
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

    public ObjectMap<CreationInfo> getCreationPlaces() {
        return creationPlaces;
    }
}
