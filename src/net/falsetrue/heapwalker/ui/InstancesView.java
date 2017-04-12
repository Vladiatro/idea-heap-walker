package net.falsetrue.heapwalker.ui;

import com.intellij.debugger.engine.DebugProcessImpl;
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
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.*;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerTreeCreator;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeListener;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.*;
import net.falsetrue.heapwalker.InstanceJavaValue;
import net.falsetrue.heapwalker.InstanceValueDescriptor;
import net.falsetrue.heapwalker.MyStateService;
import net.falsetrue.heapwalker.actions.TrackUsageAction;
import net.falsetrue.heapwalker.monitorings.CreationInfo;
import net.falsetrue.heapwalker.util.IndicatorTreeRenderer;
import net.falsetrue.heapwalker.util.NameUtils;
import net.falsetrue.heapwalker.util.map.ObjectMap;
import net.falsetrue.heapwalker.util.map.ObjectTimeMap;
import net.falsetrue.heapwalker.util.TimeManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.debugger.JavaDebuggerEditorsProvider;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings("UseJBColor")
public class InstancesView extends BorderLayoutPanel implements Disposable {
    private static final int INSTANCES_LIMIT = 10000;

    private static final Color[] USAGE_COLORS = {
        new Color(0, 255, 0),
        new Color(128, 255, 0),
        new Color(255, 255, 0),
        new Color(255, 128, 0),
        new Color(255, 0, 0),
        new Color(128, 0, 0),
        new Color(0, 0, 0),
    };

    private final XDebuggerTree instancesTree;
    private final TrackUsageAction trackUsageAction;
    private MyNodeManager myNodeManager;
    private ActionManager myActionManager;
    private ObjectMap<CreationInfo> creationPlaces;
    private Project project;
    private XDebugSession debugSession;
    private VirtualMachine virtualMachine;
    private ReferenceType referenceType;
    private DebugProcessImpl debugProcess;
    private MyNodeManager nodeManager;
    private int selected = -1;
    private TimeManager timeManager;
    private ObjectTimeMap objectTimeMap;
    private Chart<Integer> usageChart;
    private Chart<Itemable> creationPlacesChart;
    private FrameList frameList;
    private GroupType groupType = GroupType.LINE;
    private List<Predicate<ObjectReference>> referenceFilters = new ArrayList<>();
    private List<Chart<?>> filterCharts = new ArrayList<>();
    private Predicate<ObjectReference> usageFilter;
    private Predicate<ObjectReference> creationPlaceFilter;
    private Predicate<ObjectReference> creationTimeFilter;

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
                frameList.setData(creationPlaces.get(javaValue.getObjectReference()).getStack());
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
        insertCreationPlacesChart(tabs);
        insertUsageChart(tabs);
        tabs.setSelectedIndex(0);
    }

    private void insertCreationStackPanel(JBTabbedPane tabs) {
        frameList = new FrameList(project);
        JBScrollPane scrollPane = new JBScrollPane(frameList);
        tabs.insertTab("Stack", null, scrollPane, "Stack frame on object creation", 0);
    }

    private void insertCreationPlacesChart(JBTabbedPane tabs) {
        creationPlacesChart = new Chart<>();
        creationPlacesChart.setItemSelectedListener((object, position) -> {
            if (creationPlaceFilter != null) {
                clearFilters(creationPlaceFilter);
            }
            if (position == -1) {
                creationPlaceFilter = null;
                fullUpdateInstances();
                return;
            }
            creationPlaceFilter = reference -> object.check(reference, creationPlaces);
            referenceFilters.add(creationPlaceFilter);
            filterCharts.add(creationPlacesChart);
            fullUpdateInstances();
        });
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        JComboBox<GroupType> comboBox = new ComboBox<>(GroupType.values());
        comboBox.addActionListener(e -> {
            groupType = (GroupType) comboBox.getSelectedItem();
            creationPlacesChart.unselect();
            updateCreationPlacesChart();
        });
        panel.add(new LabeledComponent("Group by: ", comboBox), BorderLayout.NORTH);
        panel.add(creationPlacesChart, BorderLayout.CENTER);
        tabs.insertTab("Creation", null, panel, "Creation places chart", 1);
    }

    private void insertUsageChart(JBTabbedPane tabs) {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        BlackThresholdComboBox comboBox = new BlackThresholdComboBox(project);
        comboBox.setChangeListener((blackAge1) -> {
            usageChart.unselect();
            updateUsageChart(blackAge1);
        });
        usageChart = new Chart<>();
        usageChart.setNullObject(-1);
        usageChart.setItemSelectedListener((object, position) -> {
            int blackAge = MyStateService.getInstance(project).getBlackAgeSeconds() * 1000;
            if (usageFilter != null) {
                clearFilters(usageFilter);
            }
            if (position == -1) {
                usageFilter = null;
                fullUpdateInstances();
                return;
            }
            usageFilter = reference -> {
                long time = objectTimeMap.get(reference);
                if (time > -1) {
                    time = timeManager.getTime() - time;
                    return object == Math.min(6, (int) (time * 6 / blackAge));
                }
                return object == 6;
            };
            referenceFilters.add(usageFilter);
            filterCharts.add(usageChart);
            fullUpdateInstances();
        });
        panel.add(new LabeledComponent("Black zone: ", comboBox), BorderLayout.NORTH);
        panel.add(usageChart, BorderLayout.CENTER);
        tabs.insertTab("Usage", null, panel, "Usage statistics", 2);
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
                       ObjectMap<CreationInfo> creationPlaces,
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

    private void fullUpdateInstances() {
        if (instancesTree != null && instancesTree.getRoot() != null) {
            SwingUtilities.invokeLater(() -> instancesTree.getRoot().clearChildren());
        }
        updateInstances(null, true);
    }

    private void updateUsageChart(int blackAge) {
        if (virtualMachine == null || debugProcess == null || debugProcess.isDetached() || referenceType == null) {
            return;
        }
        debugProcess.getManagerThread().invoke(new DebuggerCommandImpl() {
            @Override
            protected void action() throws Exception {
                List<ObjectReference> instances = referenceType.instances(0);
                updateUsageChart(instances, blackAge);
            }
        });
    }

    private void updateUsageChart(List<ObjectReference> instances, int blackAge) {
        if (timeManager.isPaused() && trackUsageAction.isSelected()) {
            blackAge *= 1000;
            int[] counts = new int[7];
            in: for (ObjectReference instance : instances) {
                for (Predicate<ObjectReference> filter : referenceFilters) {
                    if (filter == usageFilter) {
                        break;
                    }
                    if (!filter.test(instance)) {
                        continue in;
                    }
                }

                long time = objectTimeMap.get(instance);
                if (time > -1) {
                    time = timeManager.getTime() - time;
                    counts[Math.min(6, (int) (time * 6 / blackAge))]++;
                } else {
                    counts[6]++;
                }
            }
            fillUsageChart(counts);
        } else {
            usageChart.clear();
        }
    }

    private void updateCreationPlacesChart() {
        if (virtualMachine == null || debugProcess == null || debugProcess.isDetached() || referenceType == null) {
            return;
        }
        debugProcess.getManagerThread().invoke(new DebuggerCommandImpl() {
            @Override
            protected void action() throws Exception {
                List<ObjectReference> instances = referenceType.instances(0);
                updateCreationPlacesChart(instances);
            }
        });
    }

    private void updateCreationPlacesChart(List<ObjectReference> instances) {
        creationPlacesChart.setData(instances.stream()
            .filter(reference -> {
                for (Predicate<ObjectReference> filter : referenceFilters) {
                    if (filter == creationPlaceFilter) {
                        return true;
                    }
                    if (!filter.test(reference)) {
                        return false;
                    }
                }
                return true;
            })
            .map((reference) -> {
                CreationInfo creationInfo = creationPlaces.get(reference);
                return creationInfo == null ? null : creationInfo.getUserCodeLocation();
            })
            .collect(Collectors.groupingBy(groupType.getSupplier(), Collectors.reducing(0, e -> 1, Integer::sum)))
            .entrySet()
            .stream()
            .map(entry -> entry.getKey().getItem(creationPlacesChart, entry.getValue()))
            .collect(Collectors.toCollection(ArrayList::new)));
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
                updateUsageChart(instances, MyStateService.getInstance(project).getBlackAgeSeconds());
                updateCreationPlacesChart(instances);
                int i = 0;
                Iterator<ObjectReference> iterator;
                if (evaluationContext == null) {
                    it: for (iterator = instances.iterator(); iterator.hasNext(); ) {
                        ObjectReference instance = iterator.next();
                        for (Predicate<ObjectReference> filter : referenceFilters) {
                            if (!filter.test(instance)) {
                                continue it;
                            }
                        }
                        list.add(InstanceJavaValue.create(project, instance));
                        if (instance.equals(reference)) {
                            selected = i;
                        }
                        i++;
                        if (i > INSTANCES_LIMIT) {
                            break;
                        }
                    }
                } else {
                    it: for (iterator = instances.iterator(); iterator.hasNext(); ) {
                        ObjectReference instance = iterator.next();
                        for (Predicate<ObjectReference> filter : referenceFilters) {
                            if (!filter.test(instance)) {
                                continue it;
                            }
                        }
                        InstanceValueDescriptor valueDescriptor = new InstanceValueDescriptor(project, instance);
                        list.add(InstanceJavaValue.create(valueDescriptor, evaluationContext, nodeManager, instance));
                        if (instance.equals(reference)) {
                            selected = i;
                        }
                        i++;
                        if (i > INSTANCES_LIMIT) {
                            break;
                        }
                    }
                }
                appendReferenceIfNeeded(reference, iterator, list, i);
                addChildrenToTree(list, false);
            }
        });
    }

    private void appendReferenceIfNeeded(ObjectReference reference, Iterator<ObjectReference> iterator,
                                         XValueChildrenList list, int i) {
        if (reference != null && selected == -1) {
            while (iterator.hasNext()) {
                ObjectReference instance = iterator.next();
                if (instance.equals(reference)) {
                    list.add(InstanceJavaValue.create(project, instance));
                    selected = i;
                }
            }
        }
    }

    private void fillUsageChart(int[] counts) {
        int blackAge = MyStateService.getInstance(project).getBlackAgeSeconds();
        List<Chart<Integer>.Item> items = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String label;
            int first = blackAge * i / 6;
            int second = blackAge * (i + 1) / 6;
            if (first == 0) {
                if (second < 60) {
                    label = "&lt;" + seconds(second);
                } else {
                    label = "&lt;" + minsSecs(second);
                }
            } else if (first < 60 && second < 60) {
                label = first + "-" + second + " seconds";
            } else if (first % 60 == 0 && second % 60 == 0) {
                label = (first / 60) + "-" + (second / 60) + " minutes";
            } else {
                label = minsSecs(first) + " - " + minsSecs(second);
            }
            items.add(usageChart.newItem(i, label, counts[i], USAGE_COLORS[i]));
        }
        String label;
        if (blackAge < 60) {
            label = ">" + seconds(blackAge) + " or n/a";
        } else if (blackAge % 60 == 0) {
            label = ">" + minutes(blackAge) + " or n/a";
        } else {
            label = ">" + minsSecs(blackAge) + " or n/a";
        }
        items.add(usageChart.newItem(6, label, counts[6], USAGE_COLORS[6]));
        usageChart.setData(items);
    }

    private void clearFilters(Predicate<ObjectReference> from) {
        for (int i = referenceFilters.size() - 1; i >= 0; i--) {
            Predicate<ObjectReference> removed = referenceFilters.remove(referenceFilters.size() - 1);
            Chart<?> chart = filterCharts.remove(referenceFilters.size());
            if (removed == from) {
                break;
            }
            chart.unselectWithoutListener();
        }
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

    private static class CheatLocation implements Itemable {
        private final Location location;
        private int hashCode;

        private CheatLocation(Location location) {
            this.location = location;
            if (location == null) {
                hashCode = 0;
            } else {
                hashCode = location.declaringType().hashCode() + location.lineNumber() * 31;
            }
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            return locationEquals(((CheatLocation) obj).location);
        }

        private boolean locationEquals(Location l) {
            if (location == null && l == null) {
                return true;
            }
            if (location == null || l == null) {
                return false;
            }
            return l.declaringType().equals(location.declaringType())
                && l.lineNumber() == location.lineNumber();
        }

        @Override
        public boolean check(ObjectReference reference, ObjectMap<CreationInfo> objectMap) {
            return locationEquals(objectMap.get(reference).getUserCodeLocation());
        }

        @Override
        public Chart<Itemable>.Item getItem(Chart<Itemable> chart, int count) {
            if (location == null) {
                return chart.newItem(this, "n/a", count, JBColor.BLACK);
            }
            return chart.newItem(this, NameUtils.locationToString(location), count);
        }
    }

    private static class CheatMethod implements Itemable {
        private final Method method;
        private int hashCode;

        private CheatMethod(Location location) {
            if (location == null || location.method() == null) {
                hashCode = 0;
                method = null;
            } else {
                method = location.method();
                hashCode = method.hashCode();
            }
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            return methodEquals(((CheatMethod) obj).method);
        }

        private boolean methodEquals(Method m) {
            if (method == null && m == null) {
                return true;
            }
            if (method == null || m == null) {
                return false;
            }
            return method.equals(m);
        }

        @Override
        public boolean check(ObjectReference reference, ObjectMap<CreationInfo> objectMap) {
            return methodEquals(objectMap.get(reference).getMethod());
        }

        @Override
        public Chart<Itemable>.Item getItem(Chart<Itemable> chart, int count) {
            if (method == null) {
                return chart.newItem(this, "n/a", count, JBColor.BLACK);
            }
            return chart.newItem(this,
                method.declaringType().name() + "." + method.name() + "()", count);
        }
    }

    private interface Itemable {
        boolean check(ObjectReference reference, ObjectMap<CreationInfo> objectMap);

        Chart<Itemable>.Item getItem(Chart<Itemable> chart, int count);
    }

    private enum GroupType {
        LINE(CheatLocation::new, "Line"), METHOD(CheatMethod::new, "Method");

        private Function<Location, Itemable> supplier;
        private String name;

        GroupType(Function<Location, Itemable> supplier, String name) {
            this.supplier = supplier;
            this.name = name;
        }

        Function<Location, Itemable> getSupplier() {
            return supplier;
        }

        public String toString() {
            return name;
        }
    }
}