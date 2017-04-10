package net.falsetrue.heapwalker;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import net.falsetrue.heapwalker.ui.MyPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@com.intellij.openapi.components.State(
        name = "regexState",
        storages = {@Storage(
                file = "$WORKSPACE_FILE$"
        )}
)
public class MyStateService implements PersistentStateComponent<MyStateService.State> {
    private MyPanel panel;
    private int blackAgeSeconds = 60;

    @Nullable
    @Override
    public State getState() {
        State state = new State();
        state.setBlackAgeSeconds(blackAgeSeconds);
        return state;
    }

    @Override
    public void loadState(State state) {
        blackAgeSeconds = state.getBlackAgeSeconds();
    }

    public static MyStateService getInstance(@NotNull Project project) {
        return ServiceManager.getService(project, MyStateService.class);
    }

    public int getBlackAgeSeconds() {
        return blackAgeSeconds;
    }

    public void setBlackAgeSeconds(int blackAgeSeconds) {
        this.blackAgeSeconds = blackAgeSeconds;
    }

    public void setPanel(@Nullable MyPanel panel) {
        this.panel = panel;
    }

    public MyPanel getPanel() {
        return panel;
    }

    public static class State {
        private int blackAgeSeconds;

        int getBlackAgeSeconds() {
            return blackAgeSeconds;
        }

        void setBlackAgeSeconds(int blackAgeSeconds) {
            this.blackAgeSeconds = blackAgeSeconds;
        }
    }

}
