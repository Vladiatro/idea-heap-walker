package net.falsetrue.heapwalker;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.debugger.JavaDebuggerEditorsProvider;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class InstancesView extends JBPanel implements Disposable {
    private final XDebuggerTree instancesTree;
    private MyNodeManager myNodeManager;
    private Map<ObjectReference, Location> creationPlaces;
    private Project project;
    private XDebugSession debugSession;
    private VirtualMachine virtualMachine;
    private ReferenceType referenceType;
    private DebugProcessImpl debugProcess;
    private MyNodeManager nodeManager;
    private int selected = -1;

    public InstancesView(Project project) {
        super(new BorderLayout(0, 0));
        this.myNodeManager = new MyNodeManager(project);
        this.project = project;
        nodeManager = new MyNodeManager(project);
        JavaDebuggerEditorsProvider editorsProvider = new JavaDebuggerEditorsProvider();
        XValueMarkers markers = this.getValueMarkers();
        XDebuggerTreeCreator treeCreator = new XDebuggerTreeCreator(project, editorsProvider,
            null, markers);
        instancesTree = (XDebuggerTree)treeCreator.createTree(this.getTreeRootDescriptor());
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
        this.add(treeScrollPane, "Center");
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
    }

    public void update(ReferenceType referenceType,
                       Map<ObjectReference, Location> creationPlaces,
                       ObjectReference reference) {
        this.referenceType = referenceType;
        this.creationPlaces = creationPlaces;
        debugSession = debugProcess.getSession().getXDebugSession();
        if (instancesTree != null && instancesTree.getRoot() != null) {
            instancesTree.getRoot().clearChildren();
        }
        updateInstances(reference, true);
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
                            creationPlaces.get(instance)));
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