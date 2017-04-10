package net.falsetrue.heapwalker.ui;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebugProcessListener;
import com.intellij.debugger.engine.SuspendContext;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerTreeCreator;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeListener;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.Location;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import net.falsetrue.heapwalker.InstanceJavaValue;
import net.falsetrue.heapwalker.InstanceValueDescriptor;
import net.falsetrue.heapwalker.actions.TrackUsageAction;
import net.falsetrue.heapwalker.monitorings.AccessMonitoring;
import net.falsetrue.heapwalker.monitorings.CreationMonitoring;
import net.falsetrue.heapwalker.util.IndicatorTreeRenderer;
import net.falsetrue.heapwalker.util.ObjectTimeMap;
import net.falsetrue.heapwalker.util.TimeManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.debugger.JavaDebuggerEditorsProvider;

import javax.swing.*;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.util.*;
import java.util.List;

import static net.falsetrue.heapwalker.util.TimeManager.BLACK_AGE;

@SuppressWarnings("UseJBColor")
public class InstancesView extends BorderLayoutPanel implements Disposable {
    private static final Color COLOR_0 = new Color(0, 255, 0);
    private static final Color COLOR_1 = new Color(128, 255, 0);
    private static final Color COLOR_2 = new Color(255, 255, 0);
    private static final Color COLOR_3 = new Color(255, 128, 0);
    private static final Color COLOR_4 = new Color(255, 0, 0);
    private static final Color COLOR_5 = new Color(128, 0, 0);
    private static final Color COLOR_6 = new Color(0, 0, 0);

    private final XDebuggerTree instancesTree;
    private final TrackUsageAction trackUsageAction;
    private MyNodeManager myNodeManager;
    private ActionManager myActionManager;
    private Map<ObjectReference, Location> creationPlaces;
    private Project project;
    private XDebugSession debugSession;
    private VirtualMachine virtualMachine;
    private ReferenceType referenceType;
    private DebugProcessImpl debugProcess;
    private MyNodeManager nodeManager;
    private int selected = -1;
    private TimeManager timeManager;
    private ObjectTimeMap objectTimeMap;
    private Chart chart;

    public InstancesView(Project project, TimeManager timeManager) {
        myNodeManager = new MyNodeManager(project);
        this.project = project;
        this.timeManager = timeManager;
        nodeManager = new MyNodeManager(project);
        JavaDebuggerEditorsProvider editorsProvider = new JavaDebuggerEditorsProvider();
        XValueMarkers markers = this.getValueMarkers();
        XDebuggerTreeCreator treeCreator = new XDebuggerTreeCreator(project, editorsProvider,
            null, markers);
        instancesTree = (XDebuggerTree)treeCreator.createTree(this.getTreeRootDescriptor());
        objectTimeMap = new ObjectTimeMap();
        chart = new Chart();
        if (!(instancesTree.getCellRenderer() instanceof IndicatorTreeRenderer)) {
            instancesTree.setCellRenderer(new IndicatorTreeRenderer(instancesTree.getCellRenderer(), objectTimeMap,
                timeManager));
        }
        instancesTree.setRootVisible(false);
        instancesTree.getRoot().setLeaf(false);
        instancesTree.setExpandableItemsEnabled(true);
        JBScrollPane treeScrollPane = new JBScrollPane(instancesTree);
        instancesTree.addTreeListener(new XDebuggerTreeListener() {
            @Override
            public void childrenLoaded(@NotNull XDebuggerTreeNode node, @NotNull List<XValueContainerNode<?>> children, boolean last) {
                if (selected > -1) {
                    instancesTree.clearSelection();
                    instancesTree.addSelectionRow(selected);
                    instancesTree.scrollRowToVisible(selected);
                    instancesTree.requestFocus();
                }
            }
        });

        myActionManager = ActionManager.getInstance();
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        trackUsageAction = new TrackUsageAction();
        actionGroup.add(trackUsageAction);
        ActionToolbar tb = myActionManager.createActionToolbar("InstancesBar", actionGroup, false);
        tb.setTargetComponent(this);
        addToLeft(tb.getComponent());

        JBSplitter splitter = new JBSplitter(0.6f);
        splitter.setFirstComponent(treeScrollPane);
        splitter.setSecondComponent(chart);
        addToCenter(splitter);
    }

    private XValueMarkers<?, ?> getValueMarkers() {
        return debugSession instanceof XDebugSessionImpl ?
            ((XDebugSessionImpl)debugSession).getValueMarkers() : null;
    }

    @NotNull
    private Pair<XValue, String> getTreeRootDescriptor() {
        return Pair.pair(new XValue() {
            public void computeChildren(@NotNull XCompositeNode node) {
                updateInstances(null, false);
            }

            public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
                node.setPresentation(null, "", "", true);
            }
        }, "root");
    }

    private void addChildrenToTree(XValueChildrenList children, boolean last) {
        XDebuggerTreeNode root = instancesTree.getRoot();
        if (root != null) {
            ((XValueNodeImpl)root).addChildren(children, last);
        }
    }

    public void setVirtualMachine(VirtualMachine virtualMachine) {
        this.virtualMachine = virtualMachine;
    }

    public void setDebugProcess(DebugProcessImpl debugProcess) {
        this.debugProcess = debugProcess;
        objectTimeMap.clear();
    }

    public void update(ReferenceType referenceType,
                       Map<ObjectReference, Location> creationPlaces,
                       ObjectReference reference) {
        this.referenceType = referenceType;
        this.creationPlaces = creationPlaces;
        debugSession = debugProcess.getSession().getXDebugSession();
        trackUsageAction.setReferenceType(objectTimeMap, debugSession, referenceType, timeManager);
        if (instancesTree != null && instancesTree.getRoot() != null) {
            SwingUtilities.invokeLater(() -> instancesTree.getRoot().clearChildren());
        }
        updateInstances(reference, true);
    }

    public void clear() {
        SwingUtilities.invokeLater(instancesTree.getRoot()::clearChildren);
    }

    private void updateInstances(ObjectReference reference, boolean recompute) {
        if (virtualMachine == null || debugProcess == null || debugProcess.isDetached()) {
            return;
        }
        if (!recompute && instancesTree.getRoot().getChildCount() > 0) {
            return;
        }
        debugProcess.getManagerThread().invoke(new DebuggerCommandImpl() {
            @Override
            protected void action() throws Exception {
                List<ObjectReference> instances = referenceType.instances(0);
                EvaluationContextImpl evaluationContext = debugProcess.getDebuggerContext().createEvaluationContext();

                selected = -1;
                XValueChildrenList list = new XValueChildrenList();
                if (timeManager.isPaused() && trackUsageAction.isSelected()) {
                    int[] counts = new int[7];
                    List<Chart.Item> chartData = new ArrayList<>(7);
                    for (ObjectReference instance : instances) {
                        long time = objectTimeMap.get(instance);
                        if (time > -1) {
                            time = timeManager.getTime() - objectTimeMap.get(instance);
                            counts[Math.min(6, (int) (time * 6 / BLACK_AGE))]++;
                        } else {
                            counts[6]++;
                        }
                    }
                    chartData.add(new Chart.Item("", counts[0], COLOR_0)); // @todo
                    chartData.add(new Chart.Item("", counts[1], COLOR_1));
                    chartData.add(new Chart.Item("", counts[2], COLOR_2));
                    chartData.add(new Chart.Item("", counts[3], COLOR_3));
                    chartData.add(new Chart.Item("", counts[4], COLOR_4));
                    chartData.add(new Chart.Item("", counts[5], COLOR_5));
                    chartData.add(new Chart.Item("", counts[6], COLOR_6));
                    chart.setData(chartData);
                }
                if (evaluationContext == null) {
                    int i = 0;
                    for (ObjectReference instance : instances) {
                        list.add(InstanceJavaValue.create(project, instance, creationPlaces.get(instance)));
                        if (instance.equals(reference)) {
                            selected = i;
                        }
                        i++;
                    }
                } else {
                    int i = 0;
                    for (ObjectReference instance : instances) {
                        InstanceValueDescriptor valueDescriptor = new InstanceValueDescriptor(project, instance);
                        list.add(InstanceJavaValue.create(valueDescriptor, evaluationContext, nodeManager,
                            creationPlaces.get(instance), instance));
                        if (instance.equals(reference)) {
                            selected = i;
                        }
                        i++;
                    }
                }
                addChildrenToTree(list, false);
            }
        });

    }

    @Override
    public void dispose() {

    }

    private static final class MyNodeManager extends NodeManagerImpl {
        MyNodeManager(Project project) {
            super(project, null);
        }

        public DebuggerTreeNodeImpl createNode(NodeDescriptor descriptor, EvaluationContext evaluationContext) {
            return new DebuggerTreeNodeImpl(null, descriptor);
        }

        public DebuggerTreeNodeImpl createMessageNode(MessageDescriptor descriptor) {
            return new DebuggerTreeNodeImpl(null, descriptor);
        }

        public DebuggerTreeNodeImpl createMessageNode(String message) {
            return new DebuggerTreeNodeImpl(null, new MessageDescriptor(message));
        }
    }
}