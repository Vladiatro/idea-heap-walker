package net.falsetrue.heapwalker;

import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ThreeState;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XInstanceEvaluator;
import com.intellij.xdebugger.frame.*;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

public class InstanceJavaValue extends XNamedValue {
    private Project project;
    private JavaValue javaValue;
    private Location creationPlace;
    private PsiClass psiClass;
    private ObjectReference objectReference;

    private InstanceJavaValue(JavaValue javaValue,
                              ObjectReference instance,
                                Location creationPlace) {
        super(javaValue.getName());
        this.project = javaValue.getProject();
        this.javaValue = javaValue;
        this.creationPlace = creationPlace;
        objectReference = instance;
    }

    private InstanceJavaValue(Project project,
                              ObjectReference instance,
                              Location creationPlace) {
        super(instance.toString());
        this.project = project;
        this.creationPlace = creationPlace;
        this.objectReference = instance;
    }

    public ObjectReference getObjectReference() {
        return objectReference;
    }

    @Override
    public boolean canNavigateToSource() {
        if (creationPlace == null) {
            return false;
        }
        try {
            creationPlace.sourceName();
            psiClass = JavaPsiFacade.getInstance(project)
                .findClass(creationPlace.declaringType().name(), GlobalSearchScope.everythingScope(project));
            return psiClass != null;
        } catch (AbsentInformationException e) {
            return false;
        }
    }

    @Override
    public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
        if (javaValue == null) {

        } else {
            javaValue.computePresentation(node, place);
        }
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
        if (javaValue != null) {
            javaValue.computeChildren(node);
        }
        super.computeChildren(node);
    }

    @Override
    public void computeSourcePosition(@NotNull XNavigatable navigatable) {
        navigatable.setSourcePosition(new XSourcePosition() {
            @Override
            public int getLine() {
                return creationPlace.lineNumber() - 1;
            }

            @Override
            public int getOffset() {
                return 0;
            }

            @NotNull
            @Override
            public VirtualFile getFile() {
                return psiClass.getContainingFile().getVirtualFile();
            }

            @NotNull
            @Override
            public Navigatable createNavigatable(@NotNull Project project) {
                return new OpenFileDescriptor(project, psiClass.getContainingFile().getVirtualFile(),
                    creationPlace.lineNumber() - 1, 0);
            }
        });
    }

    @Override
    @NotNull
    public ThreeState computeInlineDebuggerData(@NotNull XInlineDebuggerDataCallback callback) {
        if (javaValue != null) {
            return javaValue.computeInlineDebuggerData(callback);
        }
        return super.computeInlineDebuggerData(callback);
    }

    @Override
    @Nullable
    public XValueModifier getModifier() {
        if (javaValue != null) {
            return javaValue.getModifier();
        }
        return super.getModifier();
    }

    @Override
    @NotNull
    public Promise<XExpression> calculateEvaluationExpression() {
        if (javaValue != null) {
            return javaValue.calculateEvaluationExpression();
        }
        return super.calculateEvaluationExpression();
    }

    @Override
    @Nullable
    public XReferrersProvider getReferrersProvider() {
        if (javaValue != null) {
            return javaValue.getReferrersProvider();
        }
        return super.getReferrersProvider();
    }

    @Override
    @Nullable
    public XInstanceEvaluator getInstanceEvaluator() {
        if (javaValue != null) {
            return javaValue.getInstanceEvaluator();
        }
        return super.getInstanceEvaluator();
    }

    public static InstanceJavaValue create(ValueDescriptorImpl valueDescriptor,
                                           EvaluationContextImpl evaluationContext,
                                           NodeManagerImpl nodeManager,
                                           Location creationPlace,
                                           ObjectReference instance) {
        JavaValue javaValue = new JavaValueImpl(valueDescriptor, evaluationContext, nodeManager);
        return new InstanceJavaValue(javaValue, instance, creationPlace);
    }

    public static InstanceJavaValue create(Project project,
                                           ObjectReference instance,
                                           Location creationPlace) {
        return new InstanceJavaValue(project, instance, creationPlace);
    }

    private static class JavaValueImpl extends JavaValue {
        JavaValueImpl(ValueDescriptorImpl valueDescriptor,
                                    EvaluationContextImpl evaluationContext,
                                    NodeManagerImpl nodeManager) {
            super(null, valueDescriptor, evaluationContext, nodeManager, true);
        }
    }
}
