package net.falsetrue.heapwalker;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import net.falsetrue.heapwalker.monitorings.CreationInfo;
import net.falsetrue.heapwalker.util.TimeManager;
import net.falsetrue.heapwalker.util.map.ObjectMap;
import net.falsetrue.heapwalker.util.map.ObjectTimeMap;

public class ProfileSession {
    private DebugProcessImpl debugProcess;
    private XDebugSession debugSession;
    private TimeManager timeManager;
    private ObjectTimeMap objectTimeMap;
    private ObjectMap<CreationInfo> creationPlaces;
    private VirtualMachine virtualMachine;

    public ProfileSession(DebugProcessImpl debugProcess, VirtualMachine virtualMachine) {
        this.debugProcess = debugProcess;
        this.debugSession = debugProcess.getSession().getXDebugSession();
        this.virtualMachine = virtualMachine;
        timeManager = new TimeManager();
        objectTimeMap = new ObjectTimeMap();
        creationPlaces = new ObjectMap<>();
    }

    public TimeManager getTimeManager() {
        return timeManager;
    }

    public ObjectTimeMap getObjectTimeMap() {
        return objectTimeMap;
    }

    public ObjectMap<CreationInfo> getCreationPlaces() {
        return creationPlaces;
    }

    public DebugProcessImpl getDebugProcess() {
        return debugProcess;
    }

    public XDebugSession getDebugSession() {
        return debugSession;
    }

    public VirtualMachine getVirtualMachine() {
        return virtualMachine;
    }
}
