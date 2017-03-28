package net.falsetrue.heapwalker;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import net.falsetrue.heapwalker.util.NameUtils;

public class InstanceValueDescriptor extends ValueDescriptorImpl {
    public InstanceValueDescriptor(Project project, Value value) {
        super(project, value);
    }

    public String calcValueName() {
        ObjectReference ref = (ObjectReference)this.getValue();
        if(ref instanceof ArrayReference) {
            ArrayReference arrayReference = (ArrayReference)ref;
            return NameUtils.getArrayUniqueName(arrayReference);
        } else {
            return NameUtils.getUniqueName(ref);
        }
    }

    public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
        return this.getValue();
    }

    public boolean isShowIdLabel() {
        return false;
    }

    public PsiExpression getDescriptorEvaluation(DebuggerContext debuggerContext) throws EvaluateException {
        PsiElementFactory elementFactory = JavaPsiFacade.getInstance(this.myProject).getElementFactory();
        ObjectReference ref = (ObjectReference)this.getValue();
        String name = NameUtils.getUniqueName(ref).replace("@", "");
        String presentation = String.format("%s_DebugLabel", name);
        return elementFactory.createExpressionFromText(presentation, ContextUtil.getContextElement(debuggerContext));
    }
}
