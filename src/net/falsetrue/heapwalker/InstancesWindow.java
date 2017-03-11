package net.falsetrue.heapwalker;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerTreeCreator;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.Location;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.JavaDebuggerEditorsProvider;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class InstancesWindow extends DialogWrapper {
    private Project project;
    private Map<ObjectReference, Location> creationPlaces;
    private XDebugSession debugSession;
    private DebugProcessImpl debugProcess;
    private VirtualMachine virtualMachine;
    private ReferenceType referenceType;
    private InstancesView instancesView;
    private MyNodeManager nodeManager;

    private boolean debuggerTaskCompleted = false;;

    public InstancesWindow(DebugProcessImpl debugProcess,
                           VirtualMachine virtualMachine,
                           ReferenceType referenceType,
                           Map<ObjectReference, Location> creationPlaces) {
        super(debugProcess.getSession().getProject(), false);
        this.debugProcess = debugProcess;
        this.virtualMachine = virtualMachine;
        this.referenceType = referenceType;
        this.project = debugProcess.getSession().getProject();
        this.creationPlaces = creationPlaces;
        nodeManager = new MyNodeManager(project);
        debugSession = debugProcess.getSession().getXDebugSession();
        debugSession.addSessionListener(new XDebugSessionListener() {
            public void sessionStopped() {
                SwingUtilities.invokeLater(() -> {
                    InstancesWindow.this.close(0);
                });
            }
        }, this.myDisposable);
        this.setModal(false);
        this.init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        instancesView = new InstancesView();
        instancesView.setPreferredSize(new JBDimension(700, 400));
        return instancesView;
    }

    private void updateInstances() {
        debuggerTaskCompleted = false;
        debugProcess.getManagerThread().invoke(new DebuggerCommandImpl() {
            @Override
            protected void action() throws Exception {
                List<ObjectReference> instances = referenceType.instances(0);
                EvaluationContextImpl evaluationContext = debugProcess.getDebuggerContext().createEvaluationContext();

                XValueChildrenList list = new XValueChildrenList();
                if (evaluationContext == null) {
                    for (ObjectReference instance : instances) {
                        list.add(InstanceJavaValue.create(project, instance, creationPlaces.get(instance)));
                    }
                } else {
                    for (ObjectReference instance : instances) {
                        InstanceValueDescriptor valueDescriptor = new InstanceValueDescriptor(project, instance);
                        list.add(InstanceJavaValue.create(valueDescriptor, evaluationContext, nodeManager,
                            creationPlaces.get(instance)));
                    }
                }
                instancesView.addChildrenToTree(list, false);
            }
        });

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

    public class InstancesView extends JBPanel implements Disposable {
        private final XDebuggerTree instancesTree;
        private MyNodeManager myNodeManager;

        public InstancesView() {
            super(new BorderLayout(0, JBUI.scale(5)));
            this.myNodeManager = new MyNodeManager(project);
            JavaDebuggerEditorsProvider editorsProvider = new JavaDebuggerEditorsProvider();
            XValueMarkers markers = this.getValueMarkers();
            XDebuggerTreeCreator treeCreator = new XDebuggerTreeCreator(project, editorsProvider,
                null, markers);
            instancesTree = (XDebuggerTree)treeCreator.createTree(this.getTreeRootDescriptor());
            instancesTree.setRootVisible(false);
            instancesTree.getRoot().setLeaf(false);
            instancesTree.setExpandableItemsEnabled(true);
            JBScrollPane treeScrollPane = new JBScrollPane(instancesTree);
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
                    updateInstances();
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

        @Override
        public void dispose() {

        }
    }
}
