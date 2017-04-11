package net.falsetrue.heapwalker.ui;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.*;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerTreeCreator;
import com.intellij.xdebugger.impl.frame.XDebuggerFramesList;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeListener;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.Location;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import net.falsetrue.heapwalker.InstanceJavaValue;
import net.falsetrue.heapwalker.InstanceValueDescriptor;
import net.falsetrue.heapwalker.MyStateService;
import net.falsetrue.heapwalker.actions.TrackUsageAction;
import net.falsetrue.heapwalker.util.IndicatorTreeRenderer;
import net.falsetrue.heapwalker.util.map.ObjectMap;
import net.falsetrue.heapwalker.util.map.ObjectTimeMap;
import net.falsetrue.heapwalker.util.TimeManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.debugger.JavaDebuggerEditorsProvider;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

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
    private ObjectMap<List<Location>> creationPlaces;
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
    private FrameList frameList;

    public InstancesView(Project project, TimeManager timeManager) {
        myNodeManager = new MyNodeManager(project);
        this.project = project;
        this.timeManager = timeManager;
        nodeManager = new MyNodeManager(project);
        JavaDebuggerEditorsProvider editorsProvider = new JavaDebuggerEditorsProvider();
        XValueMarkers markers = this.getValueMarkers();
        XDebuggerTreeCreator treeCreator = new XDebuggerTreeCreator(project, editorsProvider,
            null, markers);
        instancesTree = (XDebuggerTree)treeCreator.createTree(getTreeRootDescriptor());
        objectTimeMap = new ObjectTimeMap();
        if (!(instancesTree.getCellRenderer() instanceof IndicatorTreeRenderer)) {
            instancesTree.setCellRenderer(new IndicatorTreeRenderer(project,
                instancesTree.getCellRenderer(), objectTimeMap, timeManager));
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
        instancesTree.addTreeSelectionListener(e -> {
            if (e.getPath().getPathCount() > 1 && e.getPath().getPathComponent(1) instanceof XValueNodeImpl) {
                InstanceJavaValue javaValue = (InstanceJavaValue) ((XValueNodeImpl) e.getPath().getPathComponent(1))
                    .getValueContainer();
                frameList.setData(creationPlaces.get(javaValue.getObjectReference()));
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

        JBTabbedPane tabs = new JBTabbedPane();
        splitter.setSecondComponent(tabs);
        addToCenter(splitter);
        insertCreationStackPanel(tabs);
        insertUsageChart(tabs);
    }

    private void insertUsageChart(JBTabbedPane tabs) {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        BlackThresholdComboBox comboBox = new BlackThresholdComboBox(project);
        comboBox.setChangeListener(this::updateChart);
        chart = new Chart();
        panel.add(comboBox, BorderLayout.NORTH);
        panel.add(chart, BorderLayout.CENTER);
        tabs.insertTab("Usage", null, panel, "Usage statistics", 0);
    }

    private void insertCreationStackPanel(JBTabbedPane tabs) {
        frameList = new FrameList(project);
        JBScrollPane scrollPane = new JBScrollPane(frameList);
        tabs.insertTab("Stack", null, scrollPane, "Stack frame on object creation", 0);
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
                       ObjectMap<List<Location>> creationPlaces,
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

    private void updateChart(int blackAge) {
        if (virtualMachine == null || debugProcess == null || debugProcess.isDetached() || referenceType == null) {
            return;
        }
        debugProcess.getManagerThread().invoke(new DebuggerCommandImpl() {
            @Override
            protected void action() throws Exception {
                List<ObjectReference> instances = referenceType.instances(0);
                updateChart(instances, blackAge);
            }
        });
    }

    private void updateChart(List<ObjectReference> instances, int blackAge) {
        if (timeManager.isPaused() && trackUsageAction.isSelected()) {
            blackAge *= 1000;
            int[] counts = new int[7];
            List<Chart.Item> chartData = new ArrayList<>(7);
            for (ObjectReference instance : instances) {
                long time = objectTimeMap.get(instance);
                if (time > -1) {
                    time = timeManager.getTime() - objectTimeMap.get(instance);
                    counts[Math.min(6, (int) (time * 6 / blackAge))]++;
                } else {
                    counts[6]++;
                }
            }
            String[] labels = createLabels();
            chartData.add(new Chart.Item(labels[0], counts[0], COLOR_0));
            chartData.add(new Chart.Item(labels[1], counts[1], COLOR_1));
            chartData.add(new Chart.Item(labels[2], counts[2], COLOR_2));
            chartData.add(new Chart.Item(labels[3], counts[3], COLOR_3));
            chartData.add(new Chart.Item(labels[4], counts[4], COLOR_4));
            chartData.add(new Chart.Item(labels[5], counts[5], COLOR_5));
            chartData.add(new Chart.Item(labels[6], counts[6], COLOR_6));
            chart.setData(chartData);
        } else {
            chart.clear();
        }
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
                updateChart(instances, MyStateService.getInstance(project).getBlackAgeSeconds());
                if (evaluationContext == null) {
                    int i = 0;
                    for (ObjectReference instance : instances) {
                        list.add(InstanceJavaValue.create(project, instance));
                        if (instance.equals(reference)) {
                            selected = i;
                        }
                        i++;
                    }
                } else {
                    int i = 0;
                    for (ObjectReference instance : instances) {
                        InstanceValueDescriptor valueDescriptor = new InstanceValueDescriptor(project, instance);
                        list.add(InstanceJavaValue.create(valueDescriptor, evaluationContext, nodeManager, instance));
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

    private String[] createLabels() {
        int blackAge = MyStateService.getInstance(project).getBlackAgeSeconds();
        String[] result = new String[7];
        for (int i = 0; i < 6; i++) {
            int first = blackAge * i / 6;
            int second = blackAge * (i + 1) / 6;
            if (first == 0) {
                if (second < 60) {
                    result[i] = "&lt;" + seconds(second);
                } else {
                    result[i] = "&lt;" + minsSecs(second);
                }
            } else if (first < 60 && second < 60) {
                result[i] = first + "-" + second + " seconds";
            } else if (first % 60 == 0 && second % 60 == 0) {
                result[i] = (first / 60) + "-" + (second / 60) + " minutes";
            } else {
                result[i] = minsSecs(first) + " - " + minsSecs(second);
            }
        }
        if (blackAge < 60) {
            result[6] = ">" + seconds(blackAge) + " or n/a";
        } else if (blackAge % 60 == 0) {
            result[6] = ">" + minutes(blackAge) + " or n/a";
        } else {
            result[6] = ">" + minsSecs(blackAge) + " or n/a";
        }
        return result;
    }

    private String seconds(int seconds) {
        if (seconds % 10 == 1) {
            return seconds + " second";
        }
        return seconds + " seconds";
    }

    private String minutes(int seconds) {
        int minutes = seconds / 60;
        if (minutes % 10 == 1) {
            return minutes + " minute";
        }
        return minutes + " minutes";
    }

    private String minsSecs(int seconds) {
        StringBuilder builder = new StringBuilder();
        if (seconds >= 60) {
            builder.append(seconds / 60).append(" min");
            if (seconds % 60 != 0) {
                builder.append(" ");
            }
        }
        if (seconds % 60 != 0) {
            builder.append(seconds % 60).append(" sec");
        }
        return builder.toString();
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