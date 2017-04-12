package net.falsetrue.heapwalker.monitorings;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.sun.jdi.Location;
import com.sun.jdi.Method;

import java.util.Iterator;
import java.util.List;

public class CreationInfo {
    private List<Location> stack;
    private long time;
    private Location userCodeLocation;
    private Method method;
    private Project project;
    private boolean userLocationComputed = false;

    public CreationInfo(List<Location> stack, Project project, long time) {
        this.stack = stack;
        this.time = time;
        this.project = project;
    }

    private void setAdditionalInfo() {
        Iterator<Location> iterator = stack.iterator();
        if (iterator.hasNext()) {
            iterator.next();
        }
        ApplicationManager.getApplication().runReadAction(() -> {
            while (iterator.hasNext()) {
                Location location = iterator.next();
                PsiClass psiClass = JavaPsiFacade.getInstance(project)
                    .findClass(location.declaringType().name(), GlobalSearchScope.projectScope(project));
                if (psiClass != null) {
                    synchronized (CreationInfo.this) {
                        userCodeLocation = location;
                        method = location.method();
                    }
                    break;
                }
            }
            synchronized (CreationInfo.this) {
                userLocationComputed = true;
            }
        });
    }

    public List<Location> getStack() {
        return stack;
    }

    public long getTime() {
        return time;
    }

    public Location getUserCodeLocation() {
        if (!userLocationComputed) {
            setAdditionalInfo();
        }
        return userCodeLocation;
    }

    public Method getMethod() {
        if (!userLocationComputed) {
            setAdditionalInfo();
        }
        return method;
    }
}
