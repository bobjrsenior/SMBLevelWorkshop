package craftedcart.smblevelworkshop.level;

import io.github.craftedcart.fluidui.uiaction.UIAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author CraftedCart
 *         Created on 10/09/2016 (DD/MM/YYYY)
 */
public class ClientLevelData {

    @NotNull private LevelData levelData = new LevelData();

    private Set<String> selectedPlaceables = new HashSet<>();
    @Nullable private UIAction onSelectedPlaceablesChanged;

    private Set<String> selectedObjects = new HashSet<>();
    @Nullable private UIAction onSelectedObjectsChanged;

    private Set<String> backgroundObjects = new HashSet<>();

    public void setLevelData(@NotNull LevelData levelData) {
        this.levelData = levelData;
    }

    @NotNull
    public LevelData getLevelData() {
        return levelData;
    }

    public void addSelectedPlaceable(String name) {
        if (!selectedPlaceables.contains(name)) {
            selectedPlaceables.add(name);
            if (onSelectedPlaceablesChanged != null) {
                onSelectedPlaceablesChanged.execute();
            }
        }
    }

    public void removeSelectedPlaceable(String name) {
        if (selectedPlaceables.contains(name)) {
            selectedPlaceables.remove(name);
            if (onSelectedPlaceablesChanged != null) {
                onSelectedPlaceablesChanged.execute();
            }
        }
    }

    public boolean isPlaceableSelected(String name) {
        return selectedPlaceables.contains(name);
    }

    public void toggleSelectedPlaceable(String name) {
        if (isPlaceableSelected(name)) {
            removeSelectedPlaceable(name);
        } else {
            addSelectedPlaceable(name);
        }
        if (onSelectedPlaceablesChanged != null) {
            onSelectedPlaceablesChanged.execute();
        }
    }

    public void clearSelectedPlaceables() {
        if (selectedPlaceables.size() != 0) {
            selectedPlaceables.clear();
            if (onSelectedPlaceablesChanged != null) {
                onSelectedPlaceablesChanged.execute();
            }
        }
    }

    public Set<String> getSelectedPlaceables() {
        return selectedPlaceables;
    }

    public void setOnSelectedPlaceablesChanged(@Nullable UIAction onSelectedPlaceablesChanged) {
        this.onSelectedPlaceablesChanged = onSelectedPlaceablesChanged;
    }

    public void addSelectedObject(String name) {
        if (!selectedObjects.contains(name)) {
            selectedObjects.add(name);
            if (onSelectedObjectsChanged != null) {
                onSelectedObjectsChanged.execute();
            }
        }
    }

    public void removeSelectedObject(String name) {
        if (selectedObjects.contains(name)) {
            selectedObjects.remove(name);
            if (onSelectedObjectsChanged != null) {
                onSelectedObjectsChanged.execute();
            }
        }
    }

    public boolean isObjectSelected(String name) {
        return selectedObjects.contains(name);
    }

    public void toggleSelectedObject(String name) {
        if (isObjectSelected(name)) {
            removeSelectedObject(name);
        } else {
            addSelectedObject(name);
        }
        if (onSelectedObjectsChanged != null) {
            onSelectedObjectsChanged.execute();
        }
    }

    public void clearSelectedObjects() {
        if (selectedObjects.size() != 0) {
            selectedObjects.clear();
            if (onSelectedObjectsChanged != null) {
                onSelectedObjectsChanged.execute();
            }
        }
    }

    public Set<String> getSelectedObjects() {
        return selectedObjects;
    }

    public void setOnSelectedObjectsChanged(@Nullable UIAction onSelectedObjectsChanged) {
        this.onSelectedObjectsChanged = onSelectedObjectsChanged;
    }

    public void addBackgroundObject(String name) {
        backgroundObjects.add(name);
    }

    public void removeBackgroundObject(String name) {
        if (backgroundObjects.contains(name)) {
            backgroundObjects.remove(name);
        }
    }

    public boolean isObjectBackground(String name) {
        return backgroundObjects.contains(name);
    }

    public void toggleBackgroundObject(String name) {
        if (isObjectBackground(name)) {
            removeBackgroundObject(name);
        } else {
            addBackgroundObject(name);
        }
    }

    public void clearBackgroundObjects() {
        backgroundObjects.clear();
    }

    public Set<String> getBackgroundObjects() {
        return backgroundObjects;
    }
    
}
