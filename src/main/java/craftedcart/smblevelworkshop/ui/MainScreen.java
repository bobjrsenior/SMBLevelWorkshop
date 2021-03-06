package craftedcart.smblevelworkshop.ui;

import craftedcart.smblevelworkshop.SMBLWSettings;
import craftedcart.smblevelworkshop.Window;
import craftedcart.smblevelworkshop.asset.*;
import craftedcart.smblevelworkshop.level.ClientLevelData;
import craftedcart.smblevelworkshop.level.LevelData;
import craftedcart.smblevelworkshop.project.ProjectManager;
import craftedcart.smblevelworkshop.resource.LangManager;
import craftedcart.smblevelworkshop.resource.ResourceManager;
import craftedcart.smblevelworkshop.resource.ResourceShaderProgram;
import craftedcart.smblevelworkshop.resource.model.OBJLoader;
import craftedcart.smblevelworkshop.resource.model.OBJObject;
import craftedcart.smblevelworkshop.resource.model.ResourceModel;
import craftedcart.smblevelworkshop.ui.component.FPSOverlay;
import craftedcart.smblevelworkshop.ui.component.InputOverlay;
import craftedcart.smblevelworkshop.ui.component.ItemButton;
import craftedcart.smblevelworkshop.ui.component.OutlinerObject;
import craftedcart.smblevelworkshop.ui.component.timeline.Timeline;
import craftedcart.smblevelworkshop.ui.component.transform.*;
import craftedcart.smblevelworkshop.undo.*;
import craftedcart.smblevelworkshop.util.*;
import craftedcart.smblevelworkshop.util.MathUtils;
import craftedcart.smbworkshopexporter.*;
import craftedcart.smbworkshopexporter.placeables.*;
import io.github.craftedcart.fluidui.FluidUIScreen;
import io.github.craftedcart.fluidui.IUIScreen;
import io.github.craftedcart.fluidui.component.Button;
import io.github.craftedcart.fluidui.component.*;
import io.github.craftedcart.fluidui.component.Component;
import io.github.craftedcart.fluidui.component.Image;
import io.github.craftedcart.fluidui.component.Label;
import io.github.craftedcart.fluidui.component.Panel;
import io.github.craftedcart.fluidui.component.TextField;
import io.github.craftedcart.fluidui.plugin.AbstractComponentPlugin;
import io.github.craftedcart.fluidui.plugin.PluginSmoothAnimateAnchor;
import io.github.craftedcart.fluidui.uiaction.UIAction;
import io.github.craftedcart.fluidui.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.glu.GLU;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * @author CraftedCart
 * Created on 02/04/2016 (DD/MM/YYYY)
 */
public class MainScreen extends FluidUIScreen {

    private static final double TIMELINE_HEIGHT = 136;

    //Camera
    @NotNull private PosXYZ cameraPos = new PosXYZ(5, 5, 5);
    @NotNull private PosXY cameraRot = new PosXY(-45, 35);

    //UI
    private final Image modeCursor = new Image();
    private final Component mainUI = new Component();
    private final Label modeLabel = new Label();
    private final Label modeDirectionLabel = new Label();
    private final Panel notifPanel = new Panel();
    private final Timeline timeline = new Timeline(this);
    private final Panel onScreenCameraControlsPanel = new Panel();
    private final FPSOverlay fpsOverlay = new FPSOverlay();

    private EnumObjectMode objectMode = EnumObjectMode.PLACEABLE_EDIT;

    //UI: Left Panel
    public final Panel addPlaceablePanel = new Panel();
    public final Panel outlinerPlaceablesPanel = new Panel();
    public final ListBox outlinerPlaceablesListBox = new ListBox();
    public final Panel outlinerObjectsPanel = new Panel();
    public final ListBox outlinerObjectsListBox = new ListBox();

    private final TextButton importObjButton = new TextButton();
    private final TextButton importConfigButton = new TextButton();
    private final TextButton exportButton = new TextButton();
    private final TextButton settingsButton = new TextButton();
    private final TextButton projectSettingsButton = new TextButton();

    //UI: Placeable Properties
    private final ListBox propertiesPlaceablesListBox = new ListBox();
    private final ListBox propertiesObjectsListBox = new ListBox();

    private final PlaceableScaleTextFields placeableScaleTextFields = new PlaceableScaleTextFields(this, null);
    private final PlaceableRotationTextFields placeableRotationTextFields = new PlaceableRotationTextFields(this, placeableScaleTextFields.getFirstTextField());
    private final PlaceablePositionTextFields placeablePositionTextFields = new PlaceablePositionTextFields(this, placeableRotationTextFields.getFirstTextField());

    private final TextButton typeButton = new TextButton();
    @Nullable private List<String> typeList = null;

    private final TextButton placeableItemGroupButton = new TextButton();

    //UI: Object Properties
    private final TextButton objectItemGroupButton = new TextButton();

    //UI: Text Fields
    private Set<TextField> textFields;

    //UI: Input Overlay
    private final InputOverlay inputOverlay = new InputOverlay();

    //Undo
    @NotNull private List<UndoCommand> undoCommandList = new ArrayList<>();
    @NotNull private List<UndoCommand> redoCommandList = new ArrayList<>();

    private boolean preventRendering = false; //Used when unloading textures and VBOs
    private boolean isLoadingProject = false; //Just in case disabling the button is too slow

    //Notifications
    private int notificationID = 0;

    //Mouse & Scroll Wheel Delta (For snapping)
    private double deltaX = 0;

    //Locks
    private final Object outlinerPlaceablesListBoxLock = new Object();
    private final Object outlinerObjectsListBoxLock = new Object();
    private final Object renderingLock = new Object();

    private final Set<UIAction> nextFrameActions = new HashSet<>();

    public MainScreen() {
        init();
    }

    private void init() {

        if (textFields == null) {
            textFields = new HashSet<>();
        }

        try {
            Window.drawable.makeCurrent();
        } catch (LWJGLException e) {
            e.printStackTrace();
        }

        //Defined at class level
        modeCursor.setOnInitAction(() -> {
            modeCursor.setTopLeftPos(-16, -16);
            modeCursor.setBottomRightPos(16, 16);
            modeCursor.setTopLeftAnchor(0.5, 0.5);
            modeCursor.setBottomRightAnchor(0.5, 0.5);
            modeCursor.setVisible(false);
            modeCursor.setTexture(ResourceManager.getTexture("image/modeEditCursor").getTexture());
            modeCursor.setColor(UIColor.matGreen());
        });
        addChildComponent("modeCursor", modeCursor);

        //Defined at class level
        mainUI.setOnInitAction(() -> {
            mainUI.setTopLeftPos(0, 0);
            mainUI.setBottomRightPos(0, 0);
            mainUI.setTopLeftAnchor(0, 0);
            mainUI.setBottomRightAnchor(1, 1);
            mainUI.setTheme(new DefaultUITheme());
        });
        addChildComponent("mainUI", mainUI);

        //<editor-fold desc="Bottom Panel">
        final Panel bottomPanel = new Panel();
        bottomPanel.setOnInitAction(() -> {
            bottomPanel.setBackgroundColor(UIColor.matGrey900(0.75));
            bottomPanel.setTopLeftPos(260, -50 - TIMELINE_HEIGHT);
            bottomPanel.setBottomRightPos(-260, -4 - TIMELINE_HEIGHT);
            bottomPanel.setTopLeftAnchor(0, 1);
            bottomPanel.setBottomRightAnchor(1, 1);
        });
        mainUI.addChildComponent("bottomPanel", bottomPanel);
        //</editor-fold>

        //<editor-fold desc="ProjectManager.getCurrentProject().mode Label">
        //Defined at class level
        modeLabel.setOnInitAction(() -> {
            modeLabel.setTopLeftPos(4, 0);
            modeLabel.setBottomRightPos(-4, 24);
            modeLabel.setTopLeftAnchor(0, 0);
            modeLabel.setBottomRightAnchor(1, 0);
            modeLabel.setTextColor(UIColor.matWhite());
        });
        bottomPanel.addChildComponent("modeLabel", modeLabel);
        //</editor-fold>

        //<editor-fold desc="ProjectManager.getCurrentProject().mode Direction Label">
        //Defined at class level
        modeDirectionLabel.setOnInitAction(() -> {
            modeDirectionLabel.setTopLeftPos(4, 24);
            modeDirectionLabel.setBottomRightPos(-4, 48);
            modeDirectionLabel.setTopLeftAnchor(0, 0);
            modeDirectionLabel.setBottomRightAnchor(1, 0);
        });
        bottomPanel.addChildComponent("modeDirectionLabel", modeDirectionLabel);
        //</editor-fold>

        final Panel leftPanel = new Panel();
        leftPanel.setOnInitAction(() -> {
            leftPanel.setBackgroundColor(UIColor.matGrey900(0.75));
            leftPanel.setTopLeftPos(0, 0);
            leftPanel.setBottomRightPos(256, 0 - TIMELINE_HEIGHT);
            leftPanel.setTopLeftAnchor(0, 0);
            leftPanel.setBottomRightAnchor(0, 1);
        });
        mainUI.addChildComponent("leftPanel", leftPanel);

        //<editor-fold desc="Placeables / Objects mode buttons">
        final TextButton outlinerPlaceablesTabButton = new TextButton();
        final TextButton outlinerObjectsTabButton = new TextButton();

        //Defined above
        outlinerPlaceablesTabButton.setOnInitAction(() -> {
            outlinerPlaceablesTabButton.setTopLeftPos(0, 0);
            outlinerPlaceablesTabButton.setBottomRightPos(-1, 24);
            outlinerPlaceablesTabButton.setTopLeftAnchor(0, 0);
            outlinerPlaceablesTabButton.setBottomRightAnchor(0.5, 0);
            outlinerPlaceablesTabButton.setText(LangManager.getItem("placeables"));
            outlinerPlaceablesTabButton.setBackgroundIdleColor(UIColor.matBlue900());
        });
        outlinerPlaceablesTabButton.setOnLMBAction(() -> {
            outlinerPlaceablesTabButton.setBackgroundIdleColor(UIColor.matBlue900());
            outlinerObjectsTabButton.setBackgroundIdleColor(UIColor.matBlue());

            addPlaceablePanel.setVisible(true);
            outlinerPlaceablesPanel.setVisible(true);
            outlinerObjectsPanel.setVisible(false);

            propertiesPlaceablesListBox.setVisible(true);
            propertiesObjectsListBox.setVisible(false);

            if (ProjectManager.getCurrentProject() != null && ProjectManager.getCurrentClientLevelData() != null) {
                ProjectManager.getCurrentClientLevelData().clearSelectedObjects();
            }

            objectMode = EnumObjectMode.PLACEABLE_EDIT;
        });
        leftPanel.addChildComponent("outlinerPlaceablesTabButton", outlinerPlaceablesTabButton);

        //Defined above
        outlinerObjectsTabButton.setOnInitAction(() -> {
            outlinerObjectsTabButton.setTopLeftPos(1, 0);
            outlinerObjectsTabButton.setBottomRightPos(0, 24);
            outlinerObjectsTabButton.setTopLeftAnchor(0.5, 0);
            outlinerObjectsTabButton.setBottomRightAnchor(1, 0);
            outlinerObjectsTabButton.setText(LangManager.getItem("objects"));
            outlinerObjectsTabButton.setBackgroundIdleColor(UIColor.matBlue());
        });
        outlinerObjectsTabButton.setOnLMBAction(() -> {
            outlinerPlaceablesTabButton.setBackgroundIdleColor(UIColor.matBlue());
            outlinerObjectsTabButton.setBackgroundIdleColor(UIColor.matBlue900());

            addPlaceablePanel.setVisible(false);
            outlinerPlaceablesPanel.setVisible(false);
            outlinerObjectsPanel.setVisible(true);

            propertiesPlaceablesListBox.setVisible(false);
            propertiesObjectsListBox.setVisible(true);

            if (ProjectManager.getCurrentProject() != null && ProjectManager.getCurrentClientLevelData() != null) {
                ProjectManager.getCurrentClientLevelData().clearSelectedPlaceables();
            }

            objectMode = EnumObjectMode.OBJECT_EDIT;
        });
        leftPanel.addChildComponent("outlinerObjectsTabButton", outlinerObjectsTabButton);
        //</editor-fold>

        //Defined at class level
        addPlaceablePanel.setOnInitAction(() -> {
            addPlaceablePanel.setTopLeftPos(0, 24);
            addPlaceablePanel.setBottomRightPos(0, 168);
            addPlaceablePanel.setTopLeftAnchor(0, 0);
            addPlaceablePanel.setBottomRightAnchor(1, 0);
            addPlaceablePanel.setBackgroundColor(UIColor.transparent());
        });
        leftPanel.addChildComponent("addPlaceablePanel", addPlaceablePanel);

        final Label addPlaceableLabel = new Label();
        addPlaceableLabel.setOnInitAction(() -> {
            addPlaceableLabel.setText(LangManager.getItem("addPlaceable"));
            addPlaceableLabel.setHorizontalAlign(EnumHAlignment.centre);
            addPlaceableLabel.setVerticalAlign(EnumVAlignment.centre);
            addPlaceableLabel.setTopLeftPos(4, 0);
            addPlaceableLabel.setBottomRightPos(-4, 24);
            addPlaceableLabel.setTopLeftAnchor(0, 0);
            addPlaceableLabel.setBottomRightAnchor(1, 0);
        });
        addPlaceablePanel.addChildComponent("addPlaceableLabel", addPlaceableLabel);

        final ListBox addPlaceableListBox = new ListBox();
        addPlaceableListBox.setOnInitAction(() -> {
            addPlaceableListBox.setBackgroundColor(UIColor.transparent());
            addPlaceableListBox.setTopLeftPos(0, 24);
            addPlaceableListBox.setBottomRightPos(0, 0);
            addPlaceableListBox.setTopLeftAnchor(0, 0);
            addPlaceableListBox.setBottomRightAnchor(1, 1);
            addPlaceableListBox.setCanScroll(false);
        });
        addPlaceablePanel.addChildComponent("addPlaceableListBox", addPlaceableListBox);

        //<editor-fold desc="Add placeable buttons">
        for (IAsset asset : AssetManager.getAvaliableAssets()) {
            final TextButton placeableButton = new TextButton();
            placeableButton.setOnInitAction(() -> {
                placeableButton.setText(LangManager.getItem(asset.getName()));
                placeableButton.setTopLeftPos(0, 0);
                placeableButton.setBottomRightPos(0, 18);
            });
            placeableButton.setOnLMBAction(() -> addPlaceable(new Placeable(asset.getCopy())));
            addPlaceableListBox.addChildComponent(asset.getName() + "AddPlaceableButton", placeableButton);
        }
        //</editor-fold>

        //<editor-fold desc="Outliner placeables panel">
        //Defined at class level
        outlinerPlaceablesPanel.setOnInitAction(() -> {
            outlinerPlaceablesPanel.setTopLeftPos(0, 168);
            outlinerPlaceablesPanel.setBottomRightPos(0, 0);
            outlinerPlaceablesPanel.setTopLeftAnchor(0, 0);
            outlinerPlaceablesPanel.setBottomRightAnchor(1, 1);
            outlinerPlaceablesPanel.setBackgroundColor(UIColor.transparent());
        });
        leftPanel.addChildComponent("outlinerPlaceablesPanel", outlinerPlaceablesPanel);

        final Label outlinerPlaceablesLabel = new Label();
        outlinerPlaceablesLabel.setOnInitAction(() -> {
            outlinerPlaceablesLabel.setText(LangManager.getItem("outliner"));
            outlinerPlaceablesLabel.setHorizontalAlign(EnumHAlignment.centre);
            outlinerPlaceablesLabel.setVerticalAlign(EnumVAlignment.centre);
            outlinerPlaceablesLabel.setTopLeftPos(4, 0);
            outlinerPlaceablesLabel.setBottomRightPos(-4, 24);
            outlinerPlaceablesLabel.setTopLeftAnchor(0, 0);
            outlinerPlaceablesLabel.setBottomRightAnchor(1, 0);
        });
        outlinerPlaceablesPanel.addChildComponent("outlinerPlaceablesLabel", outlinerPlaceablesLabel);

        //Defined at class level
        outlinerPlaceablesListBox.setOnInitAction(() -> {
            outlinerPlaceablesListBox.setBackgroundColor(UIColor.transparent());
            outlinerPlaceablesListBox.setTopLeftPos(0, 24);
            outlinerPlaceablesListBox.setBottomRightPos(0, 0);
            outlinerPlaceablesListBox.setTopLeftAnchor(0, 0);
            outlinerPlaceablesListBox.setBottomRightAnchor(1, 1);
        });
        outlinerPlaceablesPanel.addChildComponent("outlinerPlaceablesListBox", outlinerPlaceablesListBox);
        //</editor-fold>

        //<editor-fold desc="Outliner objects panel">
        //Defined at class level
        outlinerObjectsPanel.setOnInitAction(() -> {
            outlinerObjectsPanel.setTopLeftPos(0, 24);
            outlinerObjectsPanel.setBottomRightPos(0, 0);
            outlinerObjectsPanel.setTopLeftAnchor(0, 0);
            outlinerObjectsPanel.setBottomRightAnchor(1, 1);
            outlinerObjectsPanel.setBackgroundColor(UIColor.transparent());
            outlinerObjectsPanel.setVisible(false); //Hidden by default
        });
        leftPanel.addChildComponent("outlinerObjectsPanel", outlinerObjectsPanel);

        final Label outlinerObjectsLabel = new Label();
        outlinerObjectsLabel.setOnInitAction(() -> {
            outlinerObjectsLabel.setText(LangManager.getItem("outliner"));
            outlinerObjectsLabel.setHorizontalAlign(EnumHAlignment.centre);
            outlinerObjectsLabel.setVerticalAlign(EnumVAlignment.centre);
            outlinerObjectsLabel.setTopLeftPos(4, 0);
            outlinerObjectsLabel.setBottomRightPos(-4, 24);
            outlinerObjectsLabel.setTopLeftAnchor(0, 0);
            outlinerObjectsLabel.setBottomRightAnchor(1, 0);
        });
        outlinerObjectsPanel.addChildComponent("outlinerObjectsLabel", outlinerObjectsLabel);

        //Defined at class level
        outlinerObjectsListBox.setOnInitAction(() -> {
            outlinerObjectsListBox.setBackgroundColor(UIColor.transparent());
            outlinerObjectsListBox.setTopLeftPos(0, 24);
//            outlinerObjectsListBox.setBottomRightPos(0, -24); //TODO: When external backgrounds are figured out, uncomment this
            outlinerObjectsListBox.setBottomRightPos(0, 0);
            outlinerObjectsListBox.setTopLeftAnchor(0, 0);
            outlinerObjectsListBox.setBottomRightAnchor(1, 1);
        });
        outlinerObjectsPanel.addChildComponent("outlinerObjectsListBox", outlinerObjectsListBox);
        //</editor-fold>

//        final TextField addExternalBackgroundObjectTextField = new TextField();
//        addExternalBackgroundObjectTextField.setOnInitAction(() -> {
//            addExternalBackgroundObjectTextField.setTopLeftPos(0, -24);
//            addExternalBackgroundObjectTextField.setBottomRightPos(0, 0);
//            addExternalBackgroundObjectTextField.setTopLeftAnchor(0, 1);
//            addExternalBackgroundObjectTextField.setBottomRightAnchor(1, 1);
//            addExternalBackgroundObjectTextField.setPlaceholder(LangManager.getItem("addExternalBackgroundObject"));
//            addExternalBackgroundObjectTextField.setBackgroundColor(UIColor.transparent());
//        });
//        addExternalBackgroundObjectTextField.setOnReturnAction(() -> {
//            if (ProjectManager.getCurrentProject() != null && ProjectManager.getCurrentClientLevelData() != null) {
//                if (!Objects.equals(addExternalBackgroundObjectTextField.value, "")) {
//                    if (!ProjectManager.getCurrentLevelData().isObjectBackgroundExternal(addExternalBackgroundObjectTextField.text) &&
//                            !ProjectManager.getCurrentLevelData().getModel().hasObject(addExternalBackgroundObjectTextField.text)) {
//                        ProjectManager.getCurrentLevelData().addBackgroundExternalObject(addExternalBackgroundObjectTextField.text);
//
//                        outlinerObjectsListBox.addChildComponent(getOutlinerExternalBackgroundObjectComponent(addExternalBackgroundObjectTextField.text));
//                    } else {
//                        sendNotif(LangManager.getItem("alreadyObject"), UIColor.matRed());
//                    }
//
//                    addExternalBackgroundObjectTextField.setValue(""); //Clear text field
//                } else {
//                    //Text field is blank
//                    sendNotif(LangManager.getItem("noObjectSpecified"), UIColor.matRed());
//                }
//            } else {
//                sendNotif(LangManager.getItem("noLevelLoaded"), UIColor.matRed());
//            }
//        });
//        outlinerObjectsPanel.addChildComponent("addExternalBackgroundObjectTextField", addExternalBackgroundObjectTextField);
//        textFields.add(addExternalBackgroundObjectTextField);

        final Panel rightPanel = new Panel();
        rightPanel.setOnInitAction(() -> {
            rightPanel.setBackgroundColor(UIColor.matGrey900(0.75));
            rightPanel.setTopLeftPos(-256, 0);
            rightPanel.setBottomRightPos(0, 0 - TIMELINE_HEIGHT);
            rightPanel.setTopLeftAnchor(1, 0);
            rightPanel.setBottomRightAnchor(1, 1);
        });
        mainUI.addChildComponent("rightPanel", rightPanel);

        final ListBox actionsListBox = new ListBox();
        actionsListBox.setOnInitAction(() -> {
            actionsListBox.setTopLeftPos(0, 0);
            actionsListBox.setBottomRightPos(0, 130);
            actionsListBox.setTopLeftAnchor(0, 0);
            actionsListBox.setBottomRightAnchor(1, 0);
            actionsListBox.setBackgroundColor(UIColor.transparent());
        });
        rightPanel.addChildComponent("actionsListBox", actionsListBox);

        //<editor-fold desc="ImportObj TextButton">
        //Defined at class level
        importObjButton.setOnInitAction(() -> {
            importObjButton.setText(LangManager.getItem("importObj"));
            importObjButton.setTopLeftPos(0, 0);
            importObjButton.setBottomRightPos(0, 24);
        });
        importObjButton.setOnLMBAction(this::importObj);
        actionsListBox.addChildComponent("importObjButton", importObjButton);
        //</editor-fold>

        //<editor-fold desc="ImportConfig TextButton">
        //Defined at class level
        importConfigButton.setOnInitAction(() -> {
            importConfigButton.setText(LangManager.getItem("importConfig"));
            importConfigButton.setTopLeftPos(0, 0);
            importConfigButton.setBottomRightPos(0, 24);
        });
        importConfigButton.setOnLMBAction(this::importConfig);
        actionsListBox.addChildComponent("importConfigButton", importConfigButton);
        //</editor-fold>

        //<editor-fold desc="Export TextButton">
        //Defined at class level
        exportButton.setOnInitAction(() -> {
            exportButton.setText(LangManager.getItem("export"));
            exportButton.setTopLeftPos(0, 0);
            exportButton.setBottomRightPos(0, 24);
        });
        exportButton.setOnLMBAction(this::export);
        actionsListBox.addChildComponent("exportButton", exportButton);
        //</editor-fold>

        //<editor-fold desc="Settings TextButton">
        //Defined at class level
        settingsButton.setOnInitAction(() -> {
            settingsButton.setText(LangManager.getItem("settings"));
            settingsButton.setTopLeftPos(0, 0);
            settingsButton.setBottomRightPos(0, 24);
        });
        settingsButton.setOnLMBAction(this::showSettings);
        actionsListBox.addChildComponent("settingsButton", settingsButton);
        //</editor-fold>

        //<editor-fold desc="Project Settings TextButton">
        //Defined at class level
        projectSettingsButton.setOnInitAction(() -> {
            projectSettingsButton.setText(LangManager.getItem("projectSettings"));
            projectSettingsButton.setTopLeftPos(0, 0);
            projectSettingsButton.setBottomRightPos(0, 24);
        });
        projectSettingsButton.setOnLMBAction(this::showProjectSettings);
        actionsListBox.addChildComponent("projectSettingsButton", projectSettingsButton);
        //</editor-fold>

        //<editor-fold desc="Placeable Properties">
        //Defined at class level
        propertiesPlaceablesListBox.setOnInitAction(() -> {
            propertiesPlaceablesListBox.setTopLeftPos(0, 130);
            propertiesPlaceablesListBox.setBottomRightPos(0, 0);
            propertiesPlaceablesListBox.setTopLeftAnchor(0, 0);
            propertiesPlaceablesListBox.setBottomRightAnchor(1, 1);
            propertiesPlaceablesListBox.setBackgroundColor(UIColor.transparent());
        });
        rightPanel.addChildComponent("propertiesPlaceablesListBox", propertiesPlaceablesListBox);

        final Label placeablesPropertiesLabel = new Label();
        placeablesPropertiesLabel.setOnInitAction(() -> {
            placeablesPropertiesLabel.setText(LangManager.getItem("properties"));
            placeablesPropertiesLabel.setHorizontalAlign(EnumHAlignment.centre);
            placeablesPropertiesLabel.setVerticalAlign(EnumVAlignment.centre);
            placeablesPropertiesLabel.setTopLeftPos(0, 0);
            placeablesPropertiesLabel.setBottomRightPos(0, 28);
            placeablesPropertiesLabel.setTopLeftAnchor(0, 0);
            placeablesPropertiesLabel.setBottomRightAnchor(1, 0);
        });
        propertiesPlaceablesListBox.addChildComponent("placeablesPropertiesLabel", placeablesPropertiesLabel);

        final Label placeablePositionLabel = new Label();
        placeablePositionLabel.setOnInitAction(() -> {
            placeablePositionLabel.setText(LangManager.getItem("position"));
            placeablePositionLabel.setVerticalAlign(EnumVAlignment.centre);
            placeablePositionLabel.setTopLeftPos(0, 0);
            placeablePositionLabel.setBottomRightPos(0, 24);
        });
        propertiesPlaceablesListBox.addChildComponent("placeablePositionLabel", placeablePositionLabel);

        //Defined at class level
        placeablePositionTextFields.setOnInitAction(() -> {
            placeablePositionTextFields.setTopLeftPos(0, 0);
            placeablePositionTextFields.setBottomRightPos(0, 76);
        });
        propertiesPlaceablesListBox.addChildComponent("placeablePositionTextFields", placeablePositionTextFields);

        final Label rotationLabel = new Label();
        rotationLabel.setOnInitAction(() -> {
            rotationLabel.setText(LangManager.getItem("rotation"));
            rotationLabel.setVerticalAlign(EnumVAlignment.centre);
            rotationLabel.setTopLeftPos(0, 0);
            rotationLabel.setBottomRightPos(0, 24);
        });
        propertiesPlaceablesListBox.addChildComponent("rotationLabel", rotationLabel);

        //Defined at class level
        placeableRotationTextFields.setOnInitAction(() -> {
            placeableRotationTextFields.setTopLeftPos(0, 0);
            placeableRotationTextFields.setBottomRightPos(0, 76);
        });
        propertiesPlaceablesListBox.addChildComponent("placeableRotationTextFields", placeableRotationTextFields);

        final Label scaleLabel = new Label();
        scaleLabel.setOnInitAction(() -> {
            scaleLabel.setText(LangManager.getItem("scale"));
            scaleLabel.setVerticalAlign(EnumVAlignment.centre);
            scaleLabel.setTopLeftPos(0, 0);
            scaleLabel.setBottomRightPos(0, 24);
        });
        propertiesPlaceablesListBox.addChildComponent("scaleLabel", scaleLabel);

        //Defined at class level
        placeableScaleTextFields.setOnInitAction(() -> {
            placeableScaleTextFields.setTopLeftPos(0, 0);
            placeableScaleTextFields.setBottomRightPos(0, 76);
        });
        propertiesPlaceablesListBox.addChildComponent("placeableScaleTextFields", placeableScaleTextFields);

        final Label typeLabel = new Label();
        typeLabel.setOnInitAction(() -> {
            typeLabel.setText(LangManager.getItem("type"));
            typeLabel.setVerticalAlign(EnumVAlignment.centre);
            typeLabel.setTopLeftPos(0, 0);
            typeLabel.setBottomRightPos(0, 24);
        });
        propertiesPlaceablesListBox.addChildComponent("typeLabel", typeLabel);

        //Defined at class level
        typeButton.setOnInitAction(() -> {
            typeButton.setText(LangManager.getItem("noTypes"));
            typeButton.setEnabled(false);
            typeButton.setTopLeftPos(0, 0);
            typeButton.setBottomRightPos(0, 24);
        });
        typeButton.setOnLMBAction(() -> {
            assert mousePos != null;
            setOverlayUiScreen(getTypeSelectorOverlayScreen(mousePos.y));
        });
        propertiesPlaceablesListBox.addChildComponent("typeButton", typeButton);

        final Label placeableItemGroupLabel = new Label();
        placeableItemGroupLabel.setOnInitAction(() -> {
            placeableItemGroupLabel.setText(LangManager.getItem("itemGroup"));
            placeableItemGroupLabel.setVerticalAlign(EnumVAlignment.centre);
            placeableItemGroupLabel.setTopLeftPos(0, 0);
            placeableItemGroupLabel.setBottomRightPos(0, 24);
        });
        propertiesPlaceablesListBox.addChildComponent("itemGroupLabel", placeableItemGroupLabel);

        //Defined at class level
        placeableItemGroupButton.setOnInitAction(() -> {
            placeableItemGroupButton.setText(LangManager.getItem("nothingSelected"));
            placeableItemGroupButton.setEnabled(false);
            placeableItemGroupButton.setTopLeftPos(0, 0);
            placeableItemGroupButton.setBottomRightPos(0, 24);
        });
        placeableItemGroupButton.setOnLMBAction(() -> {
            assert mousePos != null;
            setOverlayUiScreen(getItemGroupSelectorOverlayScreen(mousePos.x, mousePos.y));
        });
        propertiesPlaceablesListBox.addChildComponent("placeableItemGroupButton", placeableItemGroupButton);
        //</editor-fold>

        //<editor-fold desc="Object Properties">
        //Defined at class level
        propertiesObjectsListBox.setOnInitAction(() -> {
            propertiesObjectsListBox.setTopLeftPos(0, 130);
            propertiesObjectsListBox.setBottomRightPos(0, 0);
            propertiesObjectsListBox.setTopLeftAnchor(0, 0);
            propertiesObjectsListBox.setBottomRightAnchor(1, 1);
            propertiesObjectsListBox.setBackgroundColor(UIColor.transparent());
            propertiesObjectsListBox.setVisible(false);
        });
        rightPanel.addChildComponent("propertiesObjectsListBox", propertiesObjectsListBox);

        final Label objectsPropertiesLabel = new Label();
        objectsPropertiesLabel.setOnInitAction(() -> {
            objectsPropertiesLabel.setText(LangManager.getItem("properties"));
            objectsPropertiesLabel.setHorizontalAlign(EnumHAlignment.centre);
            objectsPropertiesLabel.setVerticalAlign(EnumVAlignment.centre);
            objectsPropertiesLabel.setTopLeftPos(0, 0);
            objectsPropertiesLabel.setBottomRightPos(0, 28);
            objectsPropertiesLabel.setTopLeftAnchor(0, 0);
            objectsPropertiesLabel.setBottomRightAnchor(1, 0);
        });
        propertiesObjectsListBox.addChildComponent("objectsPropertiesLabel", objectsPropertiesLabel);

        final Label objectItemGroupLabel = new Label();
        objectItemGroupLabel.setOnInitAction(() -> {
            objectItemGroupLabel.setText(LangManager.getItem("itemGroup"));
            objectItemGroupLabel.setVerticalAlign(EnumVAlignment.centre);
            objectItemGroupLabel.setTopLeftPos(0, 0);
            objectItemGroupLabel.setBottomRightPos(0, 24);
        });
        propertiesObjectsListBox.addChildComponent("objectItemGroupLabel", objectItemGroupLabel);

        //Defined at class level
        objectItemGroupButton.setOnInitAction(() -> {
            objectItemGroupButton.setText(LangManager.getItem("nothingSelected"));
            objectItemGroupButton.setEnabled(false);
            objectItemGroupButton.setTopLeftPos(0, 0);
            objectItemGroupButton.setBottomRightPos(0, 24);
        });
        objectItemGroupButton.setOnLMBAction(() -> {
            assert mousePos != null;
            setOverlayUiScreen(getItemGroupSelectorOverlayScreen(mousePos.x, mousePos.y));
        });
        propertiesObjectsListBox.addChildComponent("objectItemGroupButton", objectItemGroupButton);
        //</editor-fold>

        //Defined at class level
        notifPanel.setOnInitAction(() -> {
            notifPanel.setTopLeftPos(0, 0);
            notifPanel.setBottomRightPos(0, 0);
            notifPanel.setTopLeftAnchor(0, 0);
            notifPanel.setBottomRightAnchor(1, 1);
            notifPanel.setBackgroundColor(UIColor.transparent());
        });
        mainUI.addChildComponent("notifPanel", notifPanel);

        //Defined at class level
        timeline.setOnInitAction(() -> {
            timeline.setTopLeftPos(0, -TIMELINE_HEIGHT);
            timeline.setBottomRightPos(0, 0);
            timeline.setTopLeftAnchor(0, 1);
            timeline.setBottomRightAnchor(1, 1);
            timeline.setBackgroundColor(UIColor.matGrey900(0.75));
        });
        mainUI.addChildComponent("timeline", timeline);

        //<editor-fold desc="On screen camera controls">
        //On screen camera controls - For those with difficulty controlling the camera with MMB / WASDQE
        //Declared at class level
        onScreenCameraControlsPanel.setOnInitAction(() -> {
            onScreenCameraControlsPanel.setBackgroundColor(UIColor.matGrey900(0.75));
            onScreenCameraControlsPanel.setTopLeftAnchor(0.5, 1);
            onScreenCameraControlsPanel.setBottomRightAnchor(0.5, 1);
            onScreenCameraControlsPanel.setTopLeftPos(-36, -72 -TIMELINE_HEIGHT - 52);
            onScreenCameraControlsPanel.setBottomRightPos(36, -TIMELINE_HEIGHT - 52);
            onScreenCameraControlsPanel.setVisible(false);
        });
        mainUI.addChildComponent("onScreenCameraControlsPanel", onScreenCameraControlsPanel);

        final Button cameraPanDownButton = new Button();
        cameraPanDownButton.setOnInitAction(() -> {
            cameraPanDownButton.setTopLeftAnchor(0, 0);
            cameraPanDownButton.setBottomRightAnchor(0, 0);
            cameraPanDownButton.setTopLeftPos(0, 0);
            cameraPanDownButton.setBottomRightPos(24, 24);
            cameraPanDownButton.setTexture(ResourceManager.getTexture("image/arrowRightDown").getTexture());
        });
        cameraPanDownButton.addPlugin(new AbstractComponentPlugin() {
            @Override
            public void onPostDraw() {
                if (linkedComponent.mouseOver && Mouse.isButtonDown(0)) {
                    double speed = Window.isShiftDown() ? SMBLWSettings.cameraSprintSpeedMultiplier * SMBLWSettings.cameraSpeed : SMBLWSettings.cameraSpeed;
                    cameraPos = cameraPos.add(0, -UIUtils.getDelta() * speed, 0);
                }
            }
        });
        onScreenCameraControlsPanel.addChildComponent("cameraPanDownButton", cameraPanDownButton);

        final Button cameraPanLeftButton = new Button();
        cameraPanLeftButton.setOnInitAction(() -> {
            cameraPanLeftButton.setTopLeftAnchor(0, 0);
            cameraPanLeftButton.setBottomRightAnchor(0, 0);
            cameraPanLeftButton.setTopLeftPos(0, 24);
            cameraPanLeftButton.setBottomRightPos(24, 48);
            cameraPanLeftButton.setTexture(ResourceManager.getTexture("image/arrowLeft").getTexture());
        });
        cameraPanLeftButton.addPlugin(new AbstractComponentPlugin() {
            @Override
            public void onPostDraw() {
                if (linkedComponent.mouseOver && Mouse.isButtonDown(0)) {
                    double speed = Window.isShiftDown() ? SMBLWSettings.cameraSprintSpeedMultiplier * SMBLWSettings.cameraSpeed : SMBLWSettings.cameraSpeed;
                    PosXYZ rightVector = new PosXYZ(Math.sin(Math.toRadians(cameraRot.x + 90)), 0, -Math.cos(Math.toRadians(cameraRot.x + 90)));
                    cameraPos = cameraPos.subtract(rightVector.multiply(UIUtils.getDelta()).multiply(speed));
                }
            }
        });
        onScreenCameraControlsPanel.addChildComponent("cameraPanLeftButton", cameraPanLeftButton);

        final Button cameraPanForwardButton = new Button();
        cameraPanForwardButton.setOnInitAction(() -> {
            cameraPanForwardButton.setTopLeftAnchor(0, 0);
            cameraPanForwardButton.setBottomRightAnchor(0, 0);
            cameraPanForwardButton.setTopLeftPos(24, 0);
            cameraPanForwardButton.setBottomRightPos(48, 24);
            cameraPanForwardButton.setTexture(ResourceManager.getTexture("image/arrowUp").getTexture());
        });
        cameraPanForwardButton.addPlugin(new AbstractComponentPlugin() {
            @Override
            public void onPostDraw() {
                if (linkedComponent.mouseOver && Mouse.isButtonDown(0)) {
                    double speed = Window.isShiftDown() ? SMBLWSettings.cameraSprintSpeedMultiplier * SMBLWSettings.cameraSpeed : SMBLWSettings.cameraSpeed;
                    PosXYZ forwardVector = new PosXYZ(Math.sin(Math.toRadians(cameraRot.x)), 0, -Math.cos(Math.toRadians(cameraRot.x)));
                    cameraPos = cameraPos.add(forwardVector.multiply(UIUtils.getDelta()).multiply(speed));
                }
            }
        });
        onScreenCameraControlsPanel.addChildComponent("cameraPanForwardButton", cameraPanForwardButton);

        final Image cameraPanIcon = new Image();
        cameraPanIcon.setOnInitAction(() -> {
            cameraPanIcon.setTexture(ResourceManager.getTexture("image/arrowAll").getTexture());
            cameraPanIcon.setTopLeftAnchor(0, 0);
            cameraPanIcon.setBottomRightAnchor(0, 0);
            cameraPanIcon.setTopLeftPos(24, 24);
            cameraPanIcon.setBottomRightPos(48, 48);
        });
        onScreenCameraControlsPanel.addChildComponent("cameraPanIcon", cameraPanIcon);

        final Button cameraPanBackwardsButton = new Button();
        cameraPanBackwardsButton.setOnInitAction(() -> {
            cameraPanBackwardsButton.setTopLeftAnchor(0, 0);
            cameraPanBackwardsButton.setBottomRightAnchor(0, 0);
            cameraPanBackwardsButton.setTopLeftPos(24, 48);
            cameraPanBackwardsButton.setBottomRightPos(48, 72);
            cameraPanBackwardsButton.setTexture(ResourceManager.getTexture("image/arrowDown").getTexture());
        });
        cameraPanBackwardsButton.addPlugin(new AbstractComponentPlugin() {
            @Override
            public void onPostDraw() {
                if (linkedComponent.mouseOver && Mouse.isButtonDown(0)) {
                    double speed = Window.isShiftDown() ? SMBLWSettings.cameraSprintSpeedMultiplier * SMBLWSettings.cameraSpeed : SMBLWSettings.cameraSpeed;
                    PosXYZ forwardVector = new PosXYZ(Math.sin(Math.toRadians(cameraRot.x)), 0, -Math.cos(Math.toRadians(cameraRot.x)));
                    cameraPos = cameraPos.subtract(forwardVector.multiply(UIUtils.getDelta()).multiply(speed));
                }
            }
        });
        onScreenCameraControlsPanel.addChildComponent("cameraPanBackwardsButton", cameraPanBackwardsButton);

        final Button cameraPanUpButton = new Button();
        cameraPanUpButton.setOnInitAction(() -> {
            cameraPanUpButton.setTopLeftAnchor(0, 0);
            cameraPanUpButton.setBottomRightAnchor(0, 0);
            cameraPanUpButton.setTopLeftPos(48, 0);
            cameraPanUpButton.setBottomRightPos(72, 24);
            cameraPanUpButton.setTexture(ResourceManager.getTexture("image/arrowLeftUp").getTexture());
        });
        cameraPanUpButton.addPlugin(new AbstractComponentPlugin() {
            @Override
            public void onPostDraw() {
                if (linkedComponent.mouseOver && Mouse.isButtonDown(0)) {
                    double speed = Window.isShiftDown() ? SMBLWSettings.cameraSprintSpeedMultiplier * SMBLWSettings.cameraSpeed : SMBLWSettings.cameraSpeed;
                    cameraPos = cameraPos.add(0, UIUtils.getDelta() * speed, 0);
                }
            }
        });
        onScreenCameraControlsPanel.addChildComponent("cameraPanUpButton", cameraPanUpButton);

        final Button cameraPanRightButton = new Button();
        cameraPanRightButton.setOnInitAction(() -> {
            cameraPanRightButton.setTopLeftAnchor(0, 0);
            cameraPanRightButton.setBottomRightAnchor(0, 0);
            cameraPanRightButton.setTopLeftPos(48, 24);
            cameraPanRightButton.setBottomRightPos(72, 48);
            cameraPanRightButton.setTexture(ResourceManager.getTexture("image/arrowRight").getTexture());
        });
        cameraPanRightButton.addPlugin(new AbstractComponentPlugin() {
            @Override
            public void onPostDraw() {
                if (linkedComponent.mouseOver && Mouse.isButtonDown(0)) {
                    double speed = Window.isShiftDown() ? SMBLWSettings.cameraSprintSpeedMultiplier * SMBLWSettings.cameraSpeed : SMBLWSettings.cameraSpeed;
                    PosXYZ rightVector = new PosXYZ(Math.sin(Math.toRadians(cameraRot.x + 90)), 0, -Math.cos(Math.toRadians(cameraRot.x + 90)));
                    cameraPos = cameraPos.add(rightVector.multiply(UIUtils.getDelta()).multiply(speed));
                }
            }
        });
        onScreenCameraControlsPanel.addChildComponent("cameraPanRightButton", cameraPanRightButton);
        //</editor-fold>

        //Defined at class level
        inputOverlay.setOnInitAction(() -> {
            inputOverlay.setTopLeftPos(256, 0);
            inputOverlay.setBottomRightPos(-256, -TIMELINE_HEIGHT);
            inputOverlay.setTopLeftAnchor(0, 0);
            inputOverlay.setBottomRightAnchor(1, 1);
            inputOverlay.setBackgroundColor(UIColor.transparent());
        });
        mainUI.addChildComponent("inputOverlay", inputOverlay);

        //Defined at class level
        fpsOverlay.setOnInitAction(() -> {
            fpsOverlay.setTopLeftPos(260, 4);
            fpsOverlay.setBottomRightPos(516, 28);
            fpsOverlay.setTopLeftAnchor(0, 0);
            fpsOverlay.setBottomRightAnchor(0, 0);
            fpsOverlay.setBackgroundColor(UIColor.matGrey900(0.75));
            fpsOverlay.setVisible(false);
        });
        mainUI.addChildComponent("fpsOverlay", fpsOverlay);

        Window.logOpenGLError("After MainScreen.init()");

        try {
            Window.drawable.releaseContext();
        } catch (LWJGLException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void preDraw() {
        super.preDraw();

        Window.logOpenGLError("After MainScreen.super.preDraw()");

        //Run next frame actions
        nextFrameActions.forEach(UIAction::execute);
        nextFrameActions.clear();

        //Show on screen camera controls if setting enabled
        onScreenCameraControlsPanel.setVisible(SMBLWSettings.showOnScreenCameraControls);
        //Show on screen input if setting enabled
        inputOverlay.setVisible(SMBLWSettings.showOnScreenInput);
        //Show FPS if setting enabled
        fpsOverlay.setVisible(SMBLWSettings.showFPSOverlay);

        if (ProjectManager.getCurrentClientLevelData() != null) {
            ProjectManager.getCurrentClientLevelData().update((float) UIUtils.getDelta());
        }

        if (Mouse.isButtonDown(2)) { //If MMB down
            //<editor-fold desc="Rotate camera on MMB & Move camera with MMB & WASDQE">
            cameraRot = cameraRot.add(UIUtils.getMouseDelta().toPosXY());

            //X
            if (cameraRot.x >= 360) {
                cameraRot.x -= 360;
            } else if (cameraRot.x <= -360) {
                cameraRot.x += 360;
            }

            //Y
            if (cameraRot.y > 90) {
                cameraRot.y = 90;
            } else if (cameraRot.y < -90) {
                cameraRot.y = -90;
            }

            PosXYZ forwardVector = new PosXYZ(Math.sin(Math.toRadians(cameraRot.x)), 0, -Math.cos(Math.toRadians(cameraRot.x)));
            PosXYZ rightVector = new PosXYZ(Math.sin(Math.toRadians(cameraRot.x + 90)), 0, -Math.cos(Math.toRadians(cameraRot.x + 90)));

            double speed = Window.isShiftDown() ? SMBLWSettings.cameraSprintSpeedMultiplier * SMBLWSettings.cameraSpeed : SMBLWSettings.cameraSpeed;

            if (Keyboard.isKeyDown(Keyboard.KEY_Q)) { //Q: Go Down
                cameraPos = cameraPos.add(0, -UIUtils.getDelta() * speed, 0);
            }

            if (Keyboard.isKeyDown(Keyboard.KEY_E)) { //E: Go Up
                cameraPos = cameraPos.add(0, UIUtils.getDelta() * speed, 0);
            }

            if (Keyboard.isKeyDown(Keyboard.KEY_W)) { //W: Go Forwards
                cameraPos = cameraPos.add(forwardVector.multiply(UIUtils.getDelta()).multiply(speed));
            }

            if (Keyboard.isKeyDown(Keyboard.KEY_S)) { //S: Go Backwards
                cameraPos = cameraPos.subtract(forwardVector.multiply(UIUtils.getDelta()).multiply(speed));
            }

            if (Keyboard.isKeyDown(Keyboard.KEY_D)) { //D: Go Right
                cameraPos = cameraPos.add(rightVector.multiply(UIUtils.getDelta()).multiply(speed));
            }

            if (Keyboard.isKeyDown(Keyboard.KEY_A)) { //A: Go Left
                cameraPos = cameraPos.subtract(rightVector.multiply(UIUtils.getDelta()).multiply(speed));
            }
            //</editor-fold>
        } else if (ProjectManager.getCurrentClientLevelData() != null) {
            if (ProjectManager.getCurrentProject().mode == EnumActionMode.GRAB_PLACEABLE) {
                //<editor-fold desc="Grab">

                for (String key : ProjectManager.getCurrentClientLevelData().getSelectedPlaceables()) {
                    Placeable placeable = ProjectManager.getCurrentLevelData().getPlaceable(key);

                    if ((ProjectManager.getCurrentProject().modeDirection.equals(new PosXYZ(1, 0, 0)) && placeable.getAsset().canGrabX()) ||
                            (ProjectManager.getCurrentProject().modeDirection.equals(new PosXYZ(0, 1, 0)) && placeable.getAsset().canGrabY()) ||
                            (ProjectManager.getCurrentProject().modeDirection.equals(new PosXYZ(0, 0, 1)) && placeable.getAsset().canGrabZ()) ||
                            (placeable.getAsset().canGrabX() && placeable.getAsset().canGrabY()) && placeable.getAsset().canGrabZ()) { //If can grab in selected direction
                        if (Window.isAltDown()) { //Snap with Alt
                            if (Window.isShiftDown()) {
                                deltaX += UIUtils.getMouseDelta().x * SMBLWSettings.modeMouseShiftSensitivity;
                                deltaX += UIUtils.getMouseDWheel() * SMBLWSettings.modeMouseWheelShiftSensitivity;
                            } else {
                                deltaX += UIUtils.getMouseDelta().x * SMBLWSettings.modeMouseSensitivity;
                                deltaX += UIUtils.getMouseDWheel() * SMBLWSettings.modeMouseWheelSensitivity;
                            }

                            if (deltaX >= SMBLWSettings.grabSnap || deltaX <= -SMBLWSettings.grabSnap) {
                                placeable.setPosition(placeable.getPosition().add(ProjectManager.getCurrentProject().modeDirection.multiply(
                                        SMBLWSettings.grabSnap * Math.round(deltaX / SMBLWSettings.grabSnap))));

                                deltaX = deltaX % SMBLWSettings.grabSnap;
                            }
                        } else if (Window.isShiftDown()) { //Precise movement with Shift
                            placeable.setPosition(placeable.getPosition().add(ProjectManager.getCurrentProject().modeDirection.multiply(UIUtils.getMouseDelta().x * SMBLWSettings.modeMouseShiftSensitivity)));
                            placeable.setPosition(placeable.getPosition().add(ProjectManager.getCurrentProject().modeDirection.multiply(UIUtils.getMouseDWheel() * SMBLWSettings.modeMouseWheelShiftSensitivity)));
                        } else {
                            placeable.setPosition(placeable.getPosition().add(ProjectManager.getCurrentProject().modeDirection.multiply(UIUtils.getMouseDelta().x * SMBLWSettings.modeMouseSensitivity)));
                            placeable.setPosition(placeable.getPosition().add(ProjectManager.getCurrentProject().modeDirection.multiply(UIUtils.getMouseDWheel() * SMBLWSettings.modeMouseWheelSensitivity)));
                        }
                    }
                }
                //</editor-fold>
            } else if (ProjectManager.getCurrentProject().mode == EnumActionMode.ROTATE_PLACEABLE) {
                //<editor-fold desc="Rotate">
                for (String key : ProjectManager.getCurrentClientLevelData().getSelectedPlaceables()) {
                    Placeable placeable = ProjectManager.getCurrentLevelData().getPlaceable(key);

                    if (placeable.getAsset().canRotate()) { //If can rotate
                        if (Window.isAltDown()) { //Snap with Alt
                            if (Window.isShiftDown()) {
                                deltaX += UIUtils.getMouseDelta().x * SMBLWSettings.modeMouseShiftSensitivity;
                                deltaX += UIUtils.getMouseDWheel() * SMBLWSettings.modeMouseWheelShiftSensitivity;
                            } else {
                                deltaX += UIUtils.getMouseDelta().x * SMBLWSettings.modeMouseSensitivity;
                                deltaX += UIUtils.getMouseDWheel() * SMBLWSettings.modeMouseWheelSensitivity;
                            }

                            if (deltaX >= SMBLWSettings.rotationSnap || deltaX <= -SMBLWSettings.rotationSnap) {
                                placeable.setRotation(placeable.getRotation().add(ProjectManager.getCurrentProject().modeDirection.multiply(
                                        SMBLWSettings.rotationSnap * Math.round(deltaX / SMBLWSettings.rotationSnap))));

                                deltaX = deltaX % SMBLWSettings.rotationSnap;
                            }
                        } else if (Window.isShiftDown()) { //Precise movement with Shift
                            placeable.setRotation(MathUtils.normalizeRotation(placeable.getRotation()
                                    .add(ProjectManager.getCurrentProject().modeDirection.multiply(UIUtils.getMouseDelta().x * SMBLWSettings.modeMouseShiftSensitivity))));
                            placeable.setRotation(MathUtils.normalizeRotation(placeable.getRotation()
                                    .add(ProjectManager.getCurrentProject().modeDirection.multiply(UIUtils.getMouseDWheel() * SMBLWSettings.modeMouseWheelShiftSensitivity))));
                        } else {
                            placeable.setRotation(MathUtils.normalizeRotation(placeable.getRotation()
                                    .add(ProjectManager.getCurrentProject().modeDirection.multiply(UIUtils.getMouseDelta().x * SMBLWSettings.modeMouseSensitivity))));
                            placeable.setRotation(MathUtils.normalizeRotation(placeable.getRotation()
                                    .add(ProjectManager.getCurrentProject().modeDirection.multiply(UIUtils.getMouseDWheel() * SMBLWSettings.modeMouseWheelSensitivity))));
                        }
                    }
                }
                //</editor-fold>
            } else if (ProjectManager.getCurrentProject().mode == EnumActionMode.SCALE_PLACEABLE) {
                //<editor-fold desc="Scale">
                for (String key : ProjectManager.getCurrentClientLevelData().getSelectedPlaceables()) {
                    Placeable placeable = ProjectManager.getCurrentLevelData().getPlaceable(key);

                    if (placeable.getAsset().canScale()) { //If can scale
                        if (Window.isAltDown()) { //Snap with Alt
                            if (Window.isShiftDown()) {
                                deltaX += UIUtils.getMouseDelta().x * SMBLWSettings.modeMouseShiftSensitivity;
                                deltaX += UIUtils.getMouseDWheel() * SMBLWSettings.modeMouseWheelShiftSensitivity;
                            } else {
                                deltaX += UIUtils.getMouseDelta().x * SMBLWSettings.modeMouseSensitivity;
                                deltaX += UIUtils.getMouseDWheel() * SMBLWSettings.modeMouseWheelSensitivity;
                            }

                            if (deltaX >= SMBLWSettings.scaleSnap || deltaX <= -SMBLWSettings.scaleSnap) {
                                placeable.setScale(placeable.getScale().add(ProjectManager.getCurrentProject().modeDirection.multiply(
                                        SMBLWSettings.scaleSnap * Math.round(deltaX / SMBLWSettings.scaleSnap))));

                                deltaX = deltaX % SMBLWSettings.scaleSnap;
                            }
                        } else if (Window.isShiftDown()) { //Precise movement with shift
                            placeable.setScale(placeable.getScale().add(ProjectManager.getCurrentProject().modeDirection.multiply(UIUtils.getMouseDelta().x * SMBLWSettings.modeMouseShiftSensitivity)));
                            placeable.setScale(placeable.getScale().add(ProjectManager.getCurrentProject().modeDirection.multiply(UIUtils.getMouseDWheel() * SMBLWSettings.modeMouseWheelShiftSensitivity)));
                        } else {
                            placeable.setScale(placeable.getScale().add(ProjectManager.getCurrentProject().modeDirection.multiply(UIUtils.getMouseDelta().x * SMBLWSettings.modeMouseSensitivity)));
                            placeable.setScale(placeable.getScale().add(ProjectManager.getCurrentProject().modeDirection.multiply(UIUtils.getMouseDWheel() * SMBLWSettings.modeMouseWheelSensitivity)));
                        }
                    }
                }
                //</editor-fold>
            } else if (ProjectManager.getCurrentProject().mode == EnumActionMode.GRAB_KEYFRAME) {
                //TODO: Grab keyframes
//                //<editor-fold desc="Grab">
//                for (Map.Entry<String, BufferedAnimData> entry : ProjectManager.getCurrentClientLevelData().getAnimDataBufferMap().entrySet()) {
//                    BufferedAnimData bad = entry.getValue();
//
//                    if (Window.isAltDown()) { //Snap with Alt
//                        float snapToTranslation = Window.isShiftDown() ? SMBLWSettings.animSnapShift : SMBLWSettings.animSnap; //Precise movement with shift
//                        bad.setSnapToTranslation(snapToTranslation);
//                    } else {
//                        bad.setSnapToTranslation(null);
//                    }
//
//                    if (Window.isShiftDown()) { //Precise movement with Shift
//                        float newTranslation = (float) (bad.getKeyframeBufferTranslation() + UIUtils.getMouseDelta().x * SMBLWSettings.modeKeyframeMouseShiftSensitivity +
//                                UIUtils.getMouseDWheel() * SMBLWSettings.modeKeyframeMouseWheelShiftSensitivity);
//                        bad.setKeyframeBufferTranslation(newTranslation);
//                    } else {
//                        float newTranslation = (float) (bad.getKeyframeBufferTranslation() + UIUtils.getMouseDelta().x * SMBLWSettings.modeKeyframeMouseSensitivity +
//                                UIUtils.getMouseDWheel() * SMBLWSettings.modeKeyframeMouseWheelSensitivity);
//                        bad.setKeyframeBufferTranslation(newTranslation);
//                    }
//                }
//                //</editor-fold>
            } else if (ProjectManager.getCurrentProject().mode == EnumActionMode.SCALE_KEYFRAME) {
                //TODO: Scale keyframes
            }

            if (ProjectManager.getCurrentProject().mode != EnumActionMode.NONE) {
                updatePropertiesPlaceablesPanel();
            }
        }


        //<editor-fold desc="Set ProjectManager.getCurrentProject().mode Label">
        String modeStringKey;
        switch (ProjectManager.getCurrentProject().mode) {
            case NONE:
                modeStringKey = "none";
                break;
            case GRAB_PLACEABLE:
                modeStringKey = "grabPlaceable";
                break;
            case ROTATE_PLACEABLE:
                modeStringKey = "rotatePlaceable";
                break;
            case SCALE_PLACEABLE:
                modeStringKey = "scalePlaceable";
                break;
            case GRAB_KEYFRAME:
                modeStringKey = "grabKeyframe";
                break;
            case SCALE_KEYFRAME:
                modeStringKey = "scaleKeyframe";
                break;
            default:
                //This shouldn't happen
                modeStringKey = "invalid";
                break;
        }

        modeLabel.setText(String.format(LangManager.getItem("modeLabelFormat"), LangManager.getItem(modeStringKey)));
        //</editor-fold>

        //<editor-fold desc="ProjectManager.getCurrentProject().mode Direction Label">
        String modeDirectionString;
        if (ProjectManager.getCurrentProject().modeDirection.equals(new PosXYZ(1, 0, 0))) {
            modeDirectionString = LangManager.getItem("axisX");
        } else if (ProjectManager.getCurrentProject().modeDirection.equals(new PosXYZ(0, 1, 0))) {
            modeDirectionString = LangManager.getItem("axisY");
        } else if (ProjectManager.getCurrentProject().modeDirection.equals(new PosXYZ(0, 0, 1))) {
            modeDirectionString = LangManager.getItem("axisZ");
        } else if (ProjectManager.getCurrentProject().modeDirection.equals(new PosXYZ(1, 1, 1))) {
            modeDirectionString = LangManager.getItem("axisUniform");
        } else {
            modeDirectionString = String.format("%.2f, %.2f, %.2f", ProjectManager.getCurrentProject().modeDirection.x, ProjectManager.getCurrentProject().modeDirection.y, ProjectManager.getCurrentProject().modeDirection.z);
        }

        modeDirectionLabel.setText(String.format(LangManager.getItem("modeDirectionLabelFormat"), modeDirectionString));
        //</editor-fold>

        if (ProjectManager.getCurrentProject().mode == EnumActionMode.NONE) {
            modeCursor.setVisible(false);
        } else {
            modeCursor.setVisible(true);
        }

        if (Mouse.isButtonDown(2) ||
                ProjectManager.getCurrentProject().mode != EnumActionMode.NONE) {
            if (!Mouse.isGrabbed()) {
                Mouse.setGrabbed(true);
            }
        } else {
            if (Mouse.isGrabbed()) {
                Mouse.setGrabbed(false);
            }
        }

        Window.logOpenGLError("After MainScreen.preDraw()");

    }

    @Override
    public void draw() {
        Window.logOpenGLError("Before MainScreen.draw()");

        topLeftPos = new PosXY(0, 0);
        topLeftPx = new PosXY(0, 0);
        bottomRightPos = new PosXY(Display.getWidth(), Display.getHeight());
        bottomRightPx = new PosXY(Display.getWidth(), Display.getHeight());

        if (overlayUiScreen == null) {
            getParentMousePos();
        } else {
            mousePos = null;
        }

        preDraw();
        if (!preventRendering) {
            drawViewport();
        }
        postDraw();

        if (overlayUiScreen != null) {
            overlayUiScreen.draw();
        }

        Window.logOpenGLError("After MainScreen.draw()");
    }

    private void drawViewport() { //TODO: Draw per item group
        synchronized (renderingLock) {
            Window.logOpenGLError("Before MainScreen.drawViewport()");

            GL11.glEnable(GL11.GL_DEPTH_TEST);

            //<editor-fold desc="Setup the matrix">
            GL11.glViewport(0, 0, Display.getWidth(), Display.getHeight());
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GLU.gluPerspective(90, Display.getWidth() / (float) Display.getHeight(), 0.5f, 1000f);
            //</editor-fold>

            GL11.glPushMatrix();

            Window.logOpenGLError("After MainScreen.drawViewport() - Matrix setup");

            GL11.glRotated(cameraRot.y, 1, 0, 0);
            GL11.glRotated(cameraRot.x, 0, 1, 0);

            GL11.glTranslated(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            GL11.glLineWidth(2);

            //<editor-fold desc="Draw X line">
            UIColor.matRed().bindColor();
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3d(-10000, 0, 0);
            GL11.glVertex3d(10000, 0, 0);
            GL11.glEnd();
            //</editor-fold>

            //<editor-fold desc="Draw Z line">
            UIColor.matBlue().bindColor();
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3d(0, 0, -10000);
            GL11.glVertex3d(0, 0, 10000);
            GL11.glEnd();
            //</editor-fold>

            Window.logOpenGLError("After MainScreen.drawViewport() - Drawing global X & Z lines");

            GL11.glLineWidth(4);

            UIColor.pureWhite().bindColor();

            if (ProjectManager.getCurrentClientLevelData() != null) {
                //<editor-fold desc="Draw model with wireframes">
                GL11.glEnable(GL11.GL_DEPTH_TEST);

                ResourceShaderProgram currentShaderProgram = getCurrentShader();
                boolean useTextures = isCurrentShaderTextured();

                if (!SMBLWSettings.showTextures) {
                    UIColor.pureWhite().bindColor();
                }


                float time = ProjectManager.getCurrentClientLevelData().getTimelinePos();

                GL20.glUseProgram(currentShaderProgram.getProgramID());
                for (ResourceModel model : ProjectManager.getCurrentLevelData().getModels()) {
                    for (OBJObject object : model.scene.getObjectList()){
                        GL11.glPushMatrix();

                        //Transform at current time
                        transformObjectAtTime(object.name, time);

                        if (!ProjectManager.getCurrentClientLevelData().isObjectHidden(object.name)) {
                            model.drawModelObject(currentShaderProgram, useTextures, object.name);
                        }

                        GL11.glPopMatrix();
                    }
                }
                GL20.glUseProgram(0);

                Window.logOpenGLError("After MainScreen.drawViewport() - Drawing model filled");

                if (SMBLWSettings.showAllWireframes) {

                    for (ResourceModel model : ProjectManager.getCurrentLevelData().getModels()) {
                        for (OBJObject object : model.scene.getObjectList()) {
                            GL11.glPushMatrix();

                            transformObjectAtTime(object.name);

                            if (!ProjectManager.getCurrentClientLevelData().isObjectHidden(object.name)) {
                                GL11.glColor4f(0, 0, 0, 1);
                                model.drawModelObjectWireframe(null, false, object.name);

                                GL11.glDisable(GL11.GL_DEPTH_TEST);

                                GL11.glColor4f(0, 0, 0, 0.01f);
                                model.drawModelObjectWireframe(null, false, object.name);

                                GL11.glEnable(GL11.GL_DEPTH_TEST);
                            }

                            GL11.glPopMatrix();
                        }
                    }
                }
                Window.logOpenGLError("After MainScreen.drawViewport() - Drawing model wireframes");

                //</editor-fold>

                //<editor-fold desc="Draw placeables">
                List<DepthSortedPlaceable> depthSortedMap = new ArrayList<>();

                synchronized (ProjectManager.getCurrentLevelData().getPlacedObjects()) {

                    for (Map.Entry<String, Placeable> placeableEntry : ProjectManager.getCurrentLevelData().getPlacedObjects().entrySet()) {
                        Placeable placeable = placeableEntry.getValue();

                        double distance;

                        if (placeable.getAsset() instanceof AssetFalloutY) {
//                            distance = getDistance(cameraPos, new PosXYZ(cameraPos.x, placeable.getPosition().y, cameraPos.z));
                            distance = 0; //Always render the fallout plane last
                        } else {
                            distance = getDistance(cameraPos, placeable.getPosition());
                        }

                        depthSortedMap.add(new DepthSortedPlaceable(distance, placeableEntry));
                    }

                }

                Collections.sort(depthSortedMap, new DepthComparator());

                for (DepthSortedPlaceable placeableEntry : depthSortedMap) {
                    String name = placeableEntry.entry.getKey();
                    Placeable placeable = placeableEntry.entry.getValue();
                    boolean isSelected = ProjectManager.getCurrentClientLevelData().isPlaceableSelected(name);

                    drawPlaceable(placeable, isSelected);

                }
                //</editor-fold>

                //<editor-fold desc="Draw selected stuff">
                drawSelectedPlaceables(ProjectManager.getCurrentClientLevelData().getSelectedPlaceables(),
                        ProjectManager.getCurrentLevelData());

                //<editor-fold desc="Draw selected objects">
                UIColor.matBlue().bindColor();
                UIUtils.drawWithStencilOutside(
                        () -> {
                            for (String name : ProjectManager.getCurrentClientLevelData().getSelectedObjects()) {
                                GL11.glPushMatrix();

                                transformObjectAtTime(name);

                                if (!ProjectManager.getCurrentClientLevelData().isObjectHidden(name)) {
                                    for (ResourceModel model : ProjectManager.getCurrentLevelData().getModels()) {
                                        if (model.hasObject(name)) {
                                            model.drawModelObject(null, false, name);
                                        }
                                    }
                                }

                                GL11.glPopMatrix();
                            }
                        },
                        () -> {
                            GL11.glDisable(GL11.GL_DEPTH_TEST);
                            for (String name : ProjectManager.getCurrentClientLevelData().getSelectedObjects()) {
                                GL11.glPushMatrix();

                                transformObjectAtTime(name);

                                if (!ProjectManager.getCurrentClientLevelData().isObjectHidden(name)) {
                                    for (ResourceModel model : ProjectManager.getCurrentLevelData().getModels()) {
                                        if (model.hasObject(name)) {
                                            model.drawModelObjectWireframe(null, false, name);
                                        }
                                    }
                                }

                                GL11.glPopMatrix();
                            }
                            GL11.glEnable(GL11.GL_DEPTH_TEST);
                        });

                Window.logOpenGLError("After MainScreen.drawViewport() - Drawing model selection wireframes");
                //</editor-fold>
                //</editor-fold>

            }

            GL11.glPopMatrix();

            GL11.glColor3f(1, 1, 1);
            GL11.glDisable(GL11.GL_DEPTH_TEST);

            Window.setMatrix();

            Window.logOpenGLError("After MainScreen.drawViewport()");
        }
    }

    private void drawPlaceable(Placeable placeable, boolean isSelected) {
        ResourceModel model = placeable.getAsset().getModel();

        GL11.glPushMatrix();

        GL11.glTranslated(placeable.getPosition().x, placeable.getPosition().y, placeable.getPosition().z);
        GL11.glRotated(placeable.getRotation().z, 0, 0, 1);
        GL11.glRotated(placeable.getRotation().y, 0, 1, 0);
        GL11.glRotated(placeable.getRotation().x, 1, 0, 0);

        GL11.glEnable(GL11.GL_DEPTH_TEST);

        GL11.glScaled(placeable.getScale().x, placeable.getScale().y, placeable.getScale().z);
        GL20.glUseProgram(placeable.getAsset().getShaderProgram().getProgramID());
        model.drawModel(placeable.getAsset().getShaderProgram(), placeable.getAsset().isShaderTextured(), placeable.getAsset().getColor());
        GL20.glUseProgram(0);

        Window.logOpenGLError("After MainScreen.drawPlaceable() - Drawing placeable " + name + " filled");

        //<editor-fold desc="Draw blue wireframe and direction line if selected, else draw orange wireframe">
        GL11.glLineWidth(2);

        if (isSelected) {
            if (ProjectManager.getCurrentProject().mode.isPlaceableMode()) {
                GL11.glPushMatrix();

                GL11.glRotated(-placeable.getRotation().x, 1, 0, 0);
                GL11.glRotated(-placeable.getRotation().y, 0, 1, 0);
                GL11.glRotated(-placeable.getRotation().z, 0, 0, 1);

                if (ProjectManager.getCurrentProject().modeDirection.equals(new PosXYZ(1, 0, 0))) {
                    //<editor-fold desc="Draw X line">
                    UIColor.matRed(0.75).bindColor();
                    GL11.glBegin(GL11.GL_LINES);
                    GL11.glVertex3d(-10000, 0, 0);
                    GL11.glVertex3d(10000, 0, 0);
                    GL11.glEnd();
                    //</editor-fold>
                } else if (ProjectManager.getCurrentProject().modeDirection.equals(new PosXYZ(0, 1, 0))) {
                    //<editor-fold desc="Draw Y line">
                    UIColor.matGreen(0.75).bindColor();
                    GL11.glBegin(GL11.GL_LINES);
                    GL11.glVertex3d(0, -10000, 0);
                    GL11.glVertex3d(0, 10000, 0);
                    GL11.glEnd();
                    //</editor-fold>
                } else if (ProjectManager.getCurrentProject().modeDirection.equals(new PosXYZ(0, 0, 1))) {
                    //<editor-fold desc="Draw Z line">
                    UIColor.matBlue(0.75).bindColor();
                    GL11.glBegin(GL11.GL_LINES);
                    GL11.glVertex3d(0, 0, -10000);
                    GL11.glVertex3d(0, 0, 10000);
                    GL11.glEnd();
                    //</editor-fold>
                }

                GL11.glPopMatrix();
            }

            UIColor.matBlue().bindColor();
        } else {
            UIColor.matOrange().bindColor();
        }

        if (SMBLWSettings.showAllWireframes) {
            model.drawModelWireframe(null, false);
        }
        //</editor-fold>

        Window.logOpenGLError("After MainScreen.drawPlaceable() - Drawing placeable " + name + " wireframe (Depth test on)");

        GL11.glDisable(GL11.GL_DEPTH_TEST);

//        //<editor-fold desc="Draw blue wireframe if selected, else draw orange wireframe (Ignores depth test - Is semi transparent)">
//        if (isSelected) {
//            UIColor.matBlue(0.05).bindColor();
//        } else {
//            UIColor.matOrange(0.02).bindColor();
//        }
//
//        if (SMBLWSettings.showAllWireframes || isSelected) {
//            model.drawModelWireframe(null, false);
//        }
//        //</editor-fold>

        Window.logOpenGLError("After MainScreen.drawPlaceable() - Drawing placeable " + name + " wireframe (Depth test off)");

        GL11.glPopMatrix();
    }

    private void drawSelectedPlaceables(Collection<String> placeables, LevelData levelData) {
        UIColor.matBlue().bindColor();
        UIUtils.drawWithStencilOutside(
                () -> {
                    for (String name : placeables) {
                        Placeable placeable = levelData.getPlaceable(name);

                        GL11.glPushMatrix();

                        GL11.glTranslated(placeable.getPosition().x, placeable.getPosition().y, placeable.getPosition().z);
                        GL11.glRotated(placeable.getRotation().z, 0, 0, 1);
                        GL11.glRotated(placeable.getRotation().y, 0, 1, 0);
                        GL11.glRotated(placeable.getRotation().x, 1, 0, 0);
                        GL11.glScaled(placeable.getScale().x, placeable.getScale().y, placeable.getScale().z);

                        placeable.getAsset().getModel().drawModel(null, false);

                        GL11.glPopMatrix();
                    }
                },
                () -> {
                    GL11.glLineWidth(4);
                    GL11.glDisable(GL11.GL_DEPTH_TEST);
                    for (String name : placeables) {
                        Placeable placeable = levelData.getPlaceable(name);

                        GL11.glPushMatrix();

                        GL11.glTranslated(placeable.getPosition().x, placeable.getPosition().y, placeable.getPosition().z);
                        GL11.glRotated(placeable.getRotation().z, 0, 0, 1);
                        GL11.glRotated(placeable.getRotation().y, 0, 1, 0);
                        GL11.glRotated(placeable.getRotation().x, 1, 0, 0);
                        GL11.glScaled(placeable.getScale().x, placeable.getScale().y, placeable.getScale().z);

                        placeable.getAsset().getModel().drawModelWireframe(null, false);

                        GL11.glPopMatrix();
                    }
                    GL11.glEnable(GL11.GL_DEPTH_TEST);
                });
    }

    @Override
    public void onClick(int button, PosXY mousePos) {
        if (button == 0 && ProjectManager.getCurrentProject().mode != EnumActionMode.NONE) { //LMB: Confirm action
            confirmModeAction();
        } else if (button == 1 && ProjectManager.getCurrentProject().mode != EnumActionMode.NONE) { //RMB: Discard action
            discardModeAction();
        } else {
            super.onClick(button, mousePos);
        }
    }

    @Override
    public void onKeyDown(int key, char keyChar) {
        inputOverlay.onKeyDownManual(key, keyChar);

        if (overlayUiScreen != null) {
            overlayUiScreen.onKeyDown(key, keyChar);
        } else {

            boolean isTextFieldSelected = false;
            for (TextField textField : textFields) {
                if (textField.isSelected) {
                    isTextFieldSelected = true;
                    break;
                }
            }

            if (!isTextFieldSelected) {
                if (!Mouse.isButtonDown(2)) { //If MMB not down
                    if (key == Keyboard.KEY_F1) { //F1 to hide / show the ui
                        mainUI.setVisible(!mainUI.isVisible());

                    } else if (ProjectManager.getCurrentClientLevelData() != null) {
                        if (ProjectManager.getCurrentProject().mode == EnumActionMode.NONE) {
                            if (key == Keyboard.KEY_G) { //G: Grab
                                //TODO: Timeline keyframes
                                if (timeline.mouseOver) { //Grab keyframes when the cursor is over the timeline
//                                    addUndoCommand(new UndoModifyKeyframes(ProjectManager.getCurrentClientLevelData(), this,
//                                            ProjectManager.getCurrentLevelData().getObjectAnimDataMap()));
//                                    moveSelectedKeyframesToBuffer(false);
//                                    ProjectManager.getCurrentProject().mode = EnumActionMode.GRAB_KEYFRAME;
                                } else {
                                    addUndoCommand(new UndoAssetTransform(ProjectManager.getCurrentClientLevelData(), ProjectManager.getCurrentClientLevelData().getSelectedPlaceables()));
                                    ProjectManager.getCurrentProject().mode = EnumActionMode.GRAB_PLACEABLE;
                                }
                            } else if (key == Keyboard.KEY_R) { //R: Rotate
                                if (!timeline.mouseOver) { //Do nothing if the cursor is over the timeline
                                    addUndoCommand(new UndoAssetTransform(ProjectManager.getCurrentClientLevelData(), ProjectManager.getCurrentClientLevelData().getSelectedPlaceables()));
                                    ProjectManager.getCurrentProject().mode = EnumActionMode.ROTATE_PLACEABLE;
                                }
                            } else if (key == Keyboard.KEY_S) { //S: Scale
                                if (timeline.mouseOver) { //Scale keyframes when the cursor is over the timeline
//                                    addUndoCommand(new UndoModifyKeyframes(ProjectManager.getCurrentClientLevelData(), this,
//                                            ProjectManager.getCurrentLevelData().getObjectAnimDataMap()));
//                                    moveSelectedKeyframesToBuffer(false);
//                                    ProjectManager.getCurrentProject().mode = EnumActionMode.SCALE_KEYFRAME;
                                } else {
                                    addUndoCommand(new UndoAssetTransform(ProjectManager.getCurrentClientLevelData(), ProjectManager.getCurrentClientLevelData().getSelectedPlaceables()));
                                    ProjectManager.getCurrentProject().mode = EnumActionMode.SCALE_PLACEABLE;
                                }

                            } else if (key == Keyboard.KEY_Z) { //Ctrl / Cmd Z: Undo - Ctrl / Cmd Shift Z: Redo
                                if (Window.isCtrlOrCmdDown()) {
                                    if (Window.isShiftDown()) {
                                        //Redo
                                        redo();
                                    } else {
                                        //Undo
                                        undo();
                                    }
                                }
                            } else if (key == Keyboard.KEY_Y) {
                                if (Window.isCtrlOrCmdDown()) {
                                    redo();
                                }

                            } else if (key == Keyboard.KEY_DELETE) { //Delete: Remove placeables / Remove keyframes if mouse over timeline
                                if (timeline.mouseOver) {
                                    //TODO: Keyframe deletion
//                                    if (
//                                            //Pos
//                                            ProjectManager.getCurrentClientLevelData().getSelectedPosXKeyframes().size() > 0 ||
//                                            ProjectManager.getCurrentClientLevelData().getSelectedPosYKeyframes().size() > 0 ||
//                                            ProjectManager.getCurrentClientLevelData().getSelectedPosZKeyframes().size() > 0 ||
//                                            //Rot
//                                            ProjectManager.getCurrentClientLevelData().getSelectedRotXKeyframes().size() > 0 ||
//                                            ProjectManager.getCurrentClientLevelData().getSelectedRotYKeyframes().size() > 0 ||
//                                            ProjectManager.getCurrentClientLevelData().getSelectedRotZKeyframes().size() > 0
//                                            ) {
//
//                                        addUndoCommand(new UndoModifyKeyframes(ProjectManager.getCurrentClientLevelData(), this,
//                                                ProjectManager.getCurrentLevelData().getObjectAnimDataMap()));
//
//                                        int keyframesRemoved = 0;
//
//                                        //Pos
//                                        for (KeyframeEntry entry : ProjectManager.getCurrentClientLevelData().getSelectedPosXKeyframes()) {
//                                            ProjectManager.getCurrentLevelData().getObjectAnimData(entry.getObjectName()).removePosXFrame(entry.getTime());
//                                            keyframesRemoved++;
//                                        }
//
//                                        for (KeyframeEntry entry : ProjectManager.getCurrentClientLevelData().getSelectedPosYKeyframes()) {
//                                            ProjectManager.getCurrentLevelData().getObjectAnimData(entry.getObjectName()).removePosYFrame(entry.getTime());
//                                            keyframesRemoved++;
//                                        }
//
//                                        for (KeyframeEntry entry : ProjectManager.getCurrentClientLevelData().getSelectedPosZKeyframes()) {
//                                            ProjectManager.getCurrentLevelData().getObjectAnimData(entry.getObjectName()).removePosZFrame(entry.getTime());
//                                            keyframesRemoved++;
//                                        }
//
//                                        //Rot
//                                        for (KeyframeEntry entry : ProjectManager.getCurrentClientLevelData().getSelectedRotXKeyframes()) {
//                                            ProjectManager.getCurrentLevelData().getObjectAnimData(entry.getObjectName()).removeRotXFrame(entry.getTime());
//                                            keyframesRemoved++;
//                                        }
//
//                                        for (KeyframeEntry entry : ProjectManager.getCurrentClientLevelData().getSelectedRotYKeyframes()) {
//                                            ProjectManager.getCurrentLevelData().getObjectAnimData(entry.getObjectName()).removeRotYFrame(entry.getTime());
//                                            keyframesRemoved++;
//                                        }
//
//                                        for (KeyframeEntry entry : ProjectManager.getCurrentClientLevelData().getSelectedRotZKeyframes()) {
//                                            ProjectManager.getCurrentLevelData().getObjectAnimData(entry.getObjectName()).removeRotZFrame(entry.getTime());
//                                            keyframesRemoved++;
//                                        }
//
//                                        ProjectManager.getCurrentClientLevelData().clearSelectedKeyframes();
//
//                                        if (keyframesRemoved > 1) {
//                                            sendNotif(String.format(LangManager.getItem("keyframeRemovedPlural"), keyframesRemoved));
//                                        } else {
//                                            sendNotif(LangManager.getItem("keyframeRemoved"));
//                                        }
//
//                                    } else {
//                                        sendNotif(LangManager.getItem("nothingSelected"));
//                                    }

                                } else {
                                    if (ProjectManager.getCurrentClientLevelData().getSelectedPlaceables().size() > 0) {

                                        List<String> toDelete = new ArrayList<>();

                                        for (String name : new HashSet<>(ProjectManager.getCurrentClientLevelData().getSelectedPlaceables())) {
                                            if (!(ProjectManager.getCurrentLevelData().getPlaceable(name).getAsset() instanceof AssetStartPos) && //Don't delete the start pos
                                                    !(ProjectManager.getCurrentLevelData().getPlaceable(name).getAsset() instanceof AssetFalloutY)) { //Don't delete the fallout Y
                                                toDelete.add(name);
                                            }
                                        }

                                        removePlaceables(toDelete);

                                        if (toDelete.size() > 1) {
                                            sendNotif(String.format(LangManager.getItem("placeableRemovedPlural"), toDelete.size()));
                                        } else {
                                            sendNotif(LangManager.getItem("placeableRemoved"));
                                        }

                                    } else {
                                        sendNotif(LangManager.getItem("nothingSelected"));
                                    }
                                }

                            } else if (key == Keyboard.KEY_D && Window.isCtrlOrCmdDown()) { //Ctrl / Cmd D: Duplicate

                                if (timeline.mouseOver) { //Duplicate keyframes when the cursor is over the timeline
                                    //TODO: Duplicate keyframes
                                    //TODO: Undo command
//                                    moveSelectedKeyframesToBuffer(true);
//                                    ProjectManager.getCurrentProject().mode = EnumActionMode.GRAB_KEYFRAME;

                                } else {
                                    Set<String> selectedPlaceables = new HashSet<>(ProjectManager.getCurrentClientLevelData().getSelectedPlaceables());
                                    ProjectManager.getCurrentClientLevelData().clearSelectedPlaceables();

                                    Map<String, Placeable> newPlaceables = new HashMap<>();

                                    int duplicated = 0;

                                    for (String name : selectedPlaceables) {
                                        Placeable placeable = ProjectManager.getCurrentLevelData().getPlaceable(name);
                                        if (!(placeable.getAsset() instanceof AssetStartPos) &&
                                                !(placeable.getAsset() instanceof AssetFalloutY)) { //If the placeable isn't the start pos or fallout Y

                                            duplicated++;

                                            Placeable newPlaceable = placeable.getCopy();
                                            String newPlaceableName = ProjectManager.getCurrentLevelData().addPlaceable(newPlaceable);
                                            newPlaceables.put(newPlaceableName, newPlaceable);

                                            synchronized (outlinerPlaceablesListBoxLock) {
                                                outlinerPlaceablesListBox.addChildComponent(getOutlinerPlaceableComponent(newPlaceableName));
                                            }

                                            updateOutlinerPlaceablesPanel();

                                            ProjectManager.getCurrentClientLevelData().addSelectedPlaceable(newPlaceableName); //Select duplicated placeables
                                        }
                                    }

                                    if (duplicated > 0) {
                                        addUndoCommand(new UndoAddPlaceable(ProjectManager.getCurrentClientLevelData(), this, new ArrayList<>(newPlaceables.keySet()), new ArrayList<>(newPlaceables.values())));

                                        //Grab after duplicating
                                        addUndoCommand(new UndoAssetTransform(ProjectManager.getCurrentClientLevelData(), ProjectManager.getCurrentClientLevelData().getSelectedPlaceables()));
                                        ProjectManager.getCurrentProject().mode = EnumActionMode.GRAB_PLACEABLE;
                                    }
                                }

                            } else if (key == Keyboard.KEY_SPACE) { //Spacebar: Play / pause animation
                                if (ProjectManager.getCurrentClientLevelData().getPlaybackSpeed() != 0) {
                                    ProjectManager.getCurrentClientLevelData().setPlaybackSpeed(0.0f);
                                } else {
                                    ProjectManager.getCurrentClientLevelData().setPlaybackSpeed(1.0f);
                                }

                            } else if (key == Keyboard.KEY_J) { //J: Rewind animation
                                if (Window.isAltDown()) { //Snap when holding alt
                                    float currentTime = ProjectManager.getCurrentClientLevelData().getTimelinePosSeconds();
                                    float snapTo = Window.isShiftDown() ? SMBLWSettings.animSnapShift : SMBLWSettings.animSnap;
                                    float newTime = currentTime - snapTo;
                                    float roundMultiplier = 1.0f / snapTo;
                                    float newTimeSnapped = Math.round(newTime * roundMultiplier) / roundMultiplier;

                                    ProjectManager.getCurrentClientLevelData().setTimelinePosSeconds(newTimeSnapped);

                                } else {
                                    float currentSpeed = ProjectManager.getCurrentClientLevelData().getPlaybackSpeed();

                                    if (currentSpeed > 1) ProjectManager.getCurrentClientLevelData().setPlaybackSpeed(currentSpeed * 0.5f);
                                    else if (currentSpeed < 0) ProjectManager.getCurrentClientLevelData().setPlaybackSpeed(currentSpeed * 2.0f);
                                    else if (currentSpeed == 0) ProjectManager.getCurrentClientLevelData().setPlaybackSpeed(-1.0f);
                                    else if (currentSpeed > 0) ProjectManager.getCurrentClientLevelData().setPlaybackSpeed(0.0f);
                                }

                            } else if (key == Keyboard.KEY_K) { //K: Stop animation
                                ProjectManager.getCurrentClientLevelData().setPlaybackSpeed(0.0f);

                            } else if (key == Keyboard.KEY_L) { //L: Forward animation

                                if (Window.isAltDown()) { //Snap when holding alt
                                    float currentTime = ProjectManager.getCurrentClientLevelData().getTimelinePosSeconds();
                                    float snapTo = Window.isShiftDown() ? SMBLWSettings.animSnapShift : SMBLWSettings.animSnap;
                                    float newTime = currentTime + snapTo;
                                    float roundMultiplier = 1.0f / snapTo;
                                    float newTimeSnapped = Math.round(newTime * roundMultiplier) / roundMultiplier;

                                    ProjectManager.getCurrentClientLevelData().setTimelinePosSeconds(newTimeSnapped);

                                } else {
                                    float currentSpeed = ProjectManager.getCurrentClientLevelData().getPlaybackSpeed();

                                    if (currentSpeed < -1) ProjectManager.getCurrentClientLevelData().setPlaybackSpeed(currentSpeed * 0.5f);
                                    else if (currentSpeed > 0) ProjectManager.getCurrentClientLevelData().setPlaybackSpeed(currentSpeed * 2.0f);
                                    else if (currentSpeed == 0) ProjectManager.getCurrentClientLevelData().setPlaybackSpeed(1.0f);
                                    else if (currentSpeed < 0) ProjectManager.getCurrentClientLevelData().setPlaybackSpeed(0.0f);
                                }

                            } else {
                                super.onKeyDown(key, keyChar);
                            }

                        } else if (key == Keyboard.KEY_X) { //X Axis
                            ProjectManager.getCurrentProject().modeDirection = new PosXYZ(1, 0, 0);
                            modeCursor.setColor(UIColor.matRed());
                        } else if (key == Keyboard.KEY_Y) { //Y Axis
                            ProjectManager.getCurrentProject().modeDirection = new PosXYZ(0, 1, 0);
                            modeCursor.setColor(UIColor.matGreen());
                        } else if (key == Keyboard.KEY_Z) { //Z Axis
                            ProjectManager.getCurrentProject().modeDirection = new PosXYZ(0, 0, 1);
                            modeCursor.setColor(UIColor.matBlue());
                        } else if (key == Keyboard.KEY_U) { //XYZ (Uniform)
                            ProjectManager.getCurrentProject().modeDirection = new PosXYZ(1, 1, 1);
                            modeCursor.setColor(UIColor.matWhite());

                        } else if (key == Keyboard.KEY_ESCAPE) {
                            discardModeAction();
                        } else if (key == Keyboard.KEY_RETURN) {
                            confirmModeAction();

                        } else {
                            super.onKeyDown(key, keyChar);
                        }
                    } else {
                        super.onKeyDown(key, keyChar);
                    }
                }
            } else {
                super.onKeyDown(key, keyChar);

                if (key == Keyboard.KEY_ESCAPE) { //Deselect text fields on escape
                    for (TextField textField : textFields) {
                        textField.setSelected(false);
                    }
                }
            }
        }

    }

    @Override
    public void onKeyReleased(int key, char keyChar) {
        inputOverlay.onKeyReleasedManual(key, keyChar);

        super.onKeyReleased(key, keyChar);
    }

    private void confirmModeAction() {
        ProjectManager.getCurrentProject().mode = EnumActionMode.NONE;
        assert ProjectManager.getCurrentClientLevelData() != null;
        commitBufferedKeyframes();
        updatePropertiesPlaceablesPanel();
        deltaX = 0; //Reset deltaX when no ProjectManager.getCurrentProject().mode is active
    }

    private void discardModeAction() {
        ProjectManager.getCurrentProject().mode = EnumActionMode.NONE;
        undo();
        discardBufferedKeyframes();
        deltaX = 0; //Reset deltaX when no ProjectManager.getCurrentProject().mode is active
    }

    public void sendNotif(String message) {
        sendNotif(message, UIColor.matGrey900());
    }

    public void sendNotif(String message, UIColor color) {
        for (Map.Entry<String, Component> entry : notifPanel.childComponents.entrySet()) {
            assert entry.getValue().plugins.get(1) instanceof NotificationPlugin;
            ((NotificationPlugin) entry.getValue().plugins.get(1)).time = 1.5;
        }

        final Panel panel = new Panel();
        panel.setOnInitAction(() -> {
            panel.setTopLeftPos(260, -80 - TIMELINE_HEIGHT);
            panel.setBottomRightPos(-260, -56 - TIMELINE_HEIGHT);
            panel.setTopLeftAnchor(0, 1.2);
            panel.setBottomRightAnchor(1, 1.2);
            panel.setBackgroundColor(color.alpha(0.75));
        });
        PluginSmoothAnimateAnchor animateAnchor = new PluginSmoothAnimateAnchor();
        panel.addPlugin(animateAnchor);
        panel.addPlugin(new NotificationPlugin(animateAnchor));
        notifPanel.addChildComponent("notificationPanel" + String.valueOf(notificationID), panel);

        final Label label = new Label();
        label.setOnInitAction(() -> {
            label.setTopLeftPos(4, 0);
            label.setBottomRightPos(-4, 0);
            label.setTopLeftAnchor(0, 0);
            label.setBottomRightAnchor(1, 1);
            label.setText(message);
        });
        panel.addChildComponent("label", label);

        notificationID++;
    }

    private void addPlaceable(Placeable placeable) {
        if (ProjectManager.getCurrentClientLevelData() != null) {
            String name = ProjectManager.getCurrentLevelData().addPlaceable(placeable);
            ProjectManager.getCurrentClientLevelData().clearSelectedPlaceables();
            ProjectManager.getCurrentClientLevelData().addSelectedPlaceable(name);

            addUndoCommand(new UndoAddPlaceable(ProjectManager.getCurrentClientLevelData(), this, Collections.singletonList(name), Collections.singletonList(placeable)));

            synchronized (outlinerPlaceablesListBoxLock) {
                outlinerPlaceablesListBox.addChildComponent(getOutlinerPlaceableComponent(name));
            }

            updateOutlinerPlaceablesPanel();
        } else {
            sendNotif(LangManager.getItem("noLevelLoaded"), UIColor.matRed());
        }
    }

    private void removePlaceable(String name) {
        if (ProjectManager.getCurrentClientLevelData() != null) {

            addUndoCommand(new UndoRemovePlaceable(ProjectManager.getCurrentClientLevelData(), this, Collections.singletonList(name), Collections.singletonList(ProjectManager.getCurrentLevelData().getPlaceable(name))));

            ProjectManager.getCurrentClientLevelData().removeSelectedPlaceable(name);
            ProjectManager.getCurrentLevelData().removePlaceable(name);

            synchronized (outlinerPlaceablesListBoxLock) {
                outlinerPlaceablesListBox.removeChildComponent(name + "OutlinerPlaceable");
            }
        } else {
            sendNotif(LangManager.getItem("noLevelLoaded"), UIColor.matRed());
        }
    }

    private void removePlaceables(List<String> names) {
        if (ProjectManager.getCurrentClientLevelData() != null) {

            List<Placeable> placeables = new ArrayList<>();
            for (String name : names) {
                placeables.add(ProjectManager.getCurrentLevelData().getPlaceable(name));
            }

            assert ProjectManager.getCurrentClientLevelData() != null;
            addUndoCommand(new UndoRemovePlaceable(ProjectManager.getCurrentClientLevelData(), this, names, placeables));

            for (String name : names) {
                ProjectManager.getCurrentClientLevelData().removeSelectedPlaceable(name);
                ProjectManager.getCurrentLevelData().removePlaceable(name);

                synchronized (outlinerPlaceablesListBoxLock) {
                    outlinerPlaceablesListBox.removeChildComponent(name + "OutlinerPlaceable");
                }
            }
        } else {
            sendNotif(LangManager.getItem("noLevelLoaded"), UIColor.matRed());
        }
    }

    private void newLevelData(Set<ExternalModel> externalModels, boolean replace) throws IOException {
        synchronized (renderingLock) {
            try {
                preventRendering = true;

                Window.drawable.makeCurrent();

                GL11.glFinish();

                if (!replace) {
                    //Clear outliner list box
                    synchronized (outlinerPlaceablesListBoxLock) {
                        outlinerPlaceablesListBox.clearChildComponents();
                    }

                    //Reset undo history
                    undoCommandList.clear();
                    redoCommandList.clear();
                }

                if (ProjectManager.getCurrentClientLevelData() != null) {
                    //Unload textures and VBOs
                    ProjectManager.getCurrentLevelData().unloadAllModels();
                }

                if (!replace) {
                    ProjectManager.getCurrentProject().clientLevelData = new ClientLevelData();
                    ProjectManager.getCurrentClientLevelData().setOnSelectedPlaceablesChanged(this::onSelectedPlaceablesChanged);
                    ProjectManager.getCurrentClientLevelData().setOnSelectedObjectsChanged(this::onSelectedObjectsChanged);
                    ProjectManager.getCurrentClientLevelData().setOnSelectedExternalBackgroundObjectsChanged(this::onSelectedExternalBackgroundObjectsChanged);
                    ProjectManager.getCurrentClientLevelData().setOnTimelinePosChanged(this::onTimelinePosChanged);
                }

                ProjectManager.getCurrentLevelData().clearModelObjSources();

                for (ExternalModel extModel : externalModels) {
                    ResourceModel model = OBJLoader.loadModel(extModel.file.getPath());
                    ProjectManager.getCurrentLevelData().addModel(model);
                    ProjectManager.getCurrentLevelData().addModelObjSource(extModel.file);
                }

                synchronized (ProjectManager.getCurrentLevelData().getPlacedObjects()) {

                    Placeable startPosPlaceable;
                    String startPosPlaceableName = null;
                    Placeable falloutYPlaceable;
                    String falloutYPlaceableName = null;
                    if (!replace) {
                        startPosPlaceable = new Placeable(new AssetStartPos());
                        startPosPlaceable.setPosition(new PosXYZ(0, 1, 0));
                        startPosPlaceableName = ProjectManager.getCurrentLevelData().addPlaceable(
                                LangManager.getItem("assetStartPos"), startPosPlaceable, "STAGE_RESERVED");

                        if (objectMode == EnumObjectMode.PLACEABLE_EDIT) {
                            ProjectManager.getCurrentClientLevelData().addSelectedPlaceable(startPosPlaceableName);
                        }

                        falloutYPlaceable = new Placeable(new AssetFalloutY());
                        falloutYPlaceable.setPosition(new PosXYZ(0, -10, 0));
                        falloutYPlaceableName = ProjectManager.getCurrentLevelData().addPlaceable(
                                LangManager.getItem("assetFalloutY"), falloutYPlaceable, "STAGE_RESERVED");

                    }

                    Window.drawable.makeCurrent();

                    if (!replace) {
                        synchronized (outlinerPlaceablesListBoxLock) {
                            outlinerPlaceablesListBox.addChildComponent(getOutlinerPlaceableComponent(startPosPlaceableName));
                            outlinerPlaceablesListBox.addChildComponent(getOutlinerPlaceableComponent(falloutYPlaceableName));
                        }

                        updateOutlinerPlaceablesPanel();
                    }

                }

                if (replace) {
                    //Remove selected objects if they no longer exist in the new OBJ
                    for (String name : ProjectManager.getCurrentClientLevelData().getSelectedObjects()) {
                        for (ResourceModel model : ProjectManager.getCurrentLevelData().getModels()) {
                            if (!model.hasObject(name)) {
                                ProjectManager.getCurrentClientLevelData().removeSelectedObject(name);
                            }
                        }
                    }

                    //Remove objects if they no longer exist in the new OBJ
                    Set<String> allObjectNames = new HashSet<>();
                    for (Map.Entry<String, WSItemGroup> entry : ProjectManager.getCurrentLevelData().getItemGroupMap().entrySet()) {
                        allObjectNames.addAll(entry.getValue().getObjectNames());
                    }
                    for (ResourceModel model : ProjectManager.getCurrentLevelData().getModels()) {
                        for (OBJObject object : model.scene.getObjectList()) {
                            if (!allObjectNames.contains(object.name)) {
                                ProjectManager.getCurrentLevelData().removeObject(name);
                            }
                        }
                    }

                } else {
                    ProjectManager.getCurrentClientLevelData().clearSelectedObjects();
                    //TODO: Update timeline
//                    timeline.setObjectAnimDataMap(ProjectManager.getCurrentLevelData().getObjectAnimDataMap());
                }

                if (!OBJLoader.isLastObjTriangulated) {
                    setOverlayUiScreen(new DialogOverlayUIScreen(LangManager.getItem("warning"), LangManager.getItem("notTriangulated")));
                }

                synchronized (outlinerObjectsListBoxLock) {
                    outlinerObjectsListBox.clearChildComponents();

                    ProjectManager.getCurrentClientLevelData().clearHiddenObjects(); //Unhide all objects

                    for (ResourceModel model : ProjectManager.getCurrentLevelData().getModels()) {
                        for (OBJObject object : model.scene.getObjectList()) {
                            outlinerObjectsListBox.addChildComponent(getOutlinerObjectComponent(object.name));
                        }
                    }
                }

                if (replace) {
                    //Replace all external background objects in the objects outliner that were removed
                    for (String name : ProjectManager.getCurrentLevelData().getBackgroundExternalObjects()) {
                        outlinerObjectsListBox.addChildComponent(getOutlinerExternalBackgroundObjectComponent(name));
                    }

                    //Delete all specified level models that no longer exist, and add new level models to the first item group
                    Set<String> existingModels = new HashSet<>();
                    for (Map.Entry<String, WSItemGroup> entry : ProjectManager.getCurrentLevelData().getItemGroupMap().entrySet()) {
                        WSItemGroup itemGroup = entry.getValue();

                        Set<String> removedObjects = new HashSet<>();
                        for (ResourceModel model : ProjectManager.getCurrentLevelData().getModels()) {
                            for (String name : itemGroup.getObjectNames())
                            if (!model.hasObject(name)) {
                                removedObjects.add(name);
                            }
                        }
                        itemGroup.removeObjects(removedObjects);

                        existingModels.addAll(itemGroup.getObjectNames());
                    }
                    existingModels.addAll(ProjectManager.getCurrentLevelData().getBackgroundObjects());

                    //Fetch a list of all new models
                    Set<String> newModels = new HashSet<>();
                    for (ResourceModel model : ProjectManager.getCurrentLevelData().getModels()) {
                        for (OBJObject obj : model.scene.getObjectList()) {
                            if (!existingModels.contains(obj.name)) {
                                //It's new
                                newModels.add(obj.name);
                            }
                        }
                    }

                    //Add the new models to the first item group
                    ProjectManager.getCurrentLevelData().getFirstItemGroup().addObjects(newModels);
                } else {
                    //Not replacing - Add everything to the first item group
                    for (ResourceModel model : ProjectManager.getCurrentLevelData().getModels()) {
                        for (OBJObject obj : model.scene.getObjectList()) {
                            ProjectManager.getCurrentLevelData().getFirstItemGroup().addObject(obj.name);
                        }
                    }
                }

                //Update the timeline
                timeline.updatePercent(ProjectManager.getCurrentClientLevelData().getTimelinePos());
                timeline.updateMaxAndLeadInTime(ProjectManager.getCurrentLevelData().getMaxTime(),
                        ProjectManager.getCurrentLevelData().getLeadInTime());

                GL11.glFlush();

                Window.drawable.releaseContext();
            } catch (LWJGLException e) {
                LogHelper.error(getClass(), e);
            }

            preventRendering = false;
        }
    }

    public void addUndoCommand(UndoCommand undoCommand) {
        undoCommandList.add(undoCommand);
        redoCommandList.clear();
    }

    private void undo() {
        if (undoCommandList.size() > 0) {
            redoCommandList.add(undoCommandList.get(undoCommandList.size() - 1).getRedoCommand());
            undoCommandList.get(undoCommandList.size() - 1).undo();
            sendNotif(undoCommandList.get(undoCommandList.size() - 1).getUndoMessage());
            undoCommandList.remove(undoCommandList.size() - 1);

            updatePropertiesPlaceablesPanel();
            updatePropertiesObjectsPanel();
            updateOutlinerPlaceablesPanel();
            updateOutlinerObjectsPanel();
        }

    }

    private void redo() {
        if (redoCommandList.size() > 0) {
            undoCommandList.add(redoCommandList.get(redoCommandList.size() - 1).getRedoCommand());
            redoCommandList.get(redoCommandList.size() - 1).undo();
            sendNotif(redoCommandList.get(redoCommandList.size() - 1).getRedoMessage());
            redoCommandList.remove(redoCommandList.size() - 1);

            updatePropertiesPlaceablesPanel();
            updatePropertiesObjectsPanel();
            updateOutlinerPlaceablesPanel();
            updateOutlinerObjectsPanel();
        }
    }

    public Component getOutlinerPlaceableComponent(String name) {
        final ItemButton placeableButton = new ItemButton();
        placeableButton.setOnInitAction(() -> {
            placeableButton.setTopLeftPos(0, 0);
            placeableButton.setBottomRightPos(0, 18);
            placeableButton.setId(name);
            placeableButton.setItemGroupCol(ProjectManager.getCurrentLevelData().getPlaceableItemGroup(name).getColor());
        });
        placeableButton.setOnLMBAction(() -> {
            assert ProjectManager.getCurrentClientLevelData() != null;

            if (Window.isShiftDown()) { //Toggle selection on shift
                ProjectManager.getCurrentClientLevelData().toggleSelectedPlaceable(name);
            } else {
                ProjectManager.getCurrentClientLevelData().clearSelectedExternalBackgroundObjects();
                ProjectManager.getCurrentClientLevelData().clearSelectedPlaceables();
                ProjectManager.getCurrentClientLevelData().addSelectedPlaceable(name);
            }
        });
        placeableButton.setName(name + "OutlinerPlaceable");

        return placeableButton;
    }

    public Component getOutlinerObjectComponent(String name) {
        final OutlinerObject outlinerObject = new OutlinerObject(name);
        outlinerObject.setOnInitAction(() -> {
            outlinerObject.setTopLeftPos(0, 0);
            outlinerObject.setBottomRightPos(0, 18);
        });

        return outlinerObject;
    }

    public Component getOutlinerExternalBackgroundObjectComponent(String name) {
        final TextButton objectButton = new TextButton();
        objectButton.setOnInitAction(() -> {
            objectButton.setTopLeftPos(0, 0);
            objectButton.setBottomRightPos(0, 18);
            objectButton.setText(name);
            objectButton.setBackgroundIdleColor(UIColor.matPink());
        });
        objectButton.setOnLMBAction(() -> {
            assert ProjectManager.getCurrentClientLevelData() != null;

            if (Window.isShiftDown()) { //Toggle selection on shift
                ProjectManager.getCurrentClientLevelData().toggleSelectedExternalBackgroundObject(name);
            } else {
                ProjectManager.getCurrentClientLevelData().clearSelectedExternalBackgroundObjects();
                ProjectManager.getCurrentClientLevelData().clearSelectedObjects();
                ProjectManager.getCurrentClientLevelData().addSelectedExternalBackgroundObject(name);
            }
        });
        objectButton.setName(name + "OutlinerObject");

        return objectButton;
    }

    private void importObj() {
        if (!isLoadingProject) {
            new Thread(() -> {
                isLoadingProject = true;
                importObjButton.setEnabled(false);
                importConfigButton.setEnabled(false);
                exportButton.setEnabled(false);
                settingsButton.setEnabled(false);
                projectSettingsButton.setEnabled(false);
                FileDialog fd = new FileDialog((Frame) null);
                fd.setMode(FileDialog.LOAD);
                fd.setFilenameFilter((dir, filename) -> filename.toUpperCase().endsWith(".OBJ"));
                fd.setVisible(true);

                File[] files = fd.getFiles();
                if (files != null && files.length > 0) {
                    File file = files[0];
                    LogHelper.info(getClass(), "Opening file: " + file.getAbsolutePath());

                    try {
                        if (ProjectManager.getCurrentClientLevelData() != null) {
                            AskReplaceObjOverlayUIScreen dialog = new AskReplaceObjOverlayUIScreen();
                            setOverlayUiScreen(dialog);
                            boolean shouldRepalce = dialog.waitForShouldReplaceResponse();
                            newLevelData(Collections.singleton(new ExternalModel(file, ModelType.OBJ)), shouldRepalce);
                        } else {
                            newLevelData(Collections.singleton(new ExternalModel(file, ModelType.OBJ)), false);
                        }

                        updateOutlinerObjectsPanel();
                    } catch (IOException e) {
                        LogHelper.error(getClass(), "Failed to open file");
                        LogHelper.error(getClass(), e);
                    }
                }
                projectSettingsButton.setEnabled(true);
                settingsButton.setEnabled(true);
                exportButton.setEnabled(true);
                importConfigButton.setEnabled(true);
                importObjButton.setEnabled(true);
                isLoadingProject = false;
            }, "ObjFileOpenThread").start();
        } else {
            LogHelper.warn(getClass(), "Tried importing OBJ when already importing OBJ");
        }
    }

    private void export() {
        if (!isLoadingProject) {
            if (ProjectManager.getCurrentClientLevelData() != null) {
                setOverlayUiScreen(new ExportOverlayUIScreen());
            } else {
                sendNotif(LangManager.getItem("noLevelLoaded"), UIColor.matRed());
            }
        }
    }

    private void onSelectedPlaceablesChanged() {
        updatePropertiesPlaceablesPanel();
        updateOutlinerPlaceablesPanel();
    }

    public void updatePropertiesPlaceablesPanel() {
        double posAvgX = 0;
        boolean canGrabX = false;
        double posAvgY = 0;
        boolean canGrabY = false;
        double posAvgZ = 0;
        boolean canGrabZ = false;

        double rotAvgX = 0;
        double rotAvgY = 0;
        double rotAvgZ = 0;
        boolean canRotate = false;

        double sclAvgX = 0;
        double sclAvgY = 0;
        double sclAvgZ = 0;
        boolean canScale = false;

        assert ProjectManager.getCurrentClientLevelData() != null;

        Class<?> selectedIAsset;
        if (ProjectManager.getCurrentClientLevelData().getSelectedPlaceables().size() > 0) {
            selectedIAsset = ProjectManager.getCurrentLevelData().getPlaceable(ProjectManager.getCurrentClientLevelData().getSelectedPlaceables().iterator().next()).getAsset().getClass();
        } else {
            selectedIAsset = null;
        }

        for (String name : ProjectManager.getCurrentClientLevelData().getSelectedPlaceables()) {
            Placeable placeable = ProjectManager.getCurrentLevelData().getPlaceable(name);

            if (placeable.getAsset().canGrabX()) {
                canGrabX = true;
                posAvgX += placeable.getPosition().x;
            }
            if (placeable.getAsset().canGrabY()) {
                canGrabY = true;
                posAvgY += placeable.getPosition().y;
            }
            if (placeable.getAsset().canGrabZ()) {
                canGrabZ = true;
                posAvgZ += placeable.getPosition().z;
            }

            if (placeable.getAsset().canRotate()) {
                canRotate = true;

                rotAvgX += placeable.getRotation().x;
                rotAvgY += placeable.getRotation().y;
                rotAvgZ += placeable.getRotation().z;
            }

            if (placeable.getAsset().canScale()) {
                canScale = true;

                sclAvgX += placeable.getScale().x;
                sclAvgY += placeable.getScale().y;
                sclAvgZ += placeable.getScale().z;
            } else {
                sclAvgX += 1;
                sclAvgY += 1;
                sclAvgZ += 1;
            }

            if (selectedIAsset != null && !placeable.getAsset().getClass().isAssignableFrom(selectedIAsset)) {
                selectedIAsset = null;
            }
        }

        int selectedCount = ProjectManager.getCurrentClientLevelData().getSelectedPlaceables().size();

        if (selectedCount != 0) {
            posAvgX = posAvgX / (double) selectedCount;
            posAvgY = posAvgY / (double) selectedCount;
            posAvgZ = posAvgZ / (double) selectedCount;

            if (canGrabX) {
                placeablePositionTextFields.setXEnabled(true);
            } else {
                placeablePositionTextFields.setXEnabled(false);
                placeablePositionTextFields.setXValue(0);
            }
            if (canGrabY) {
                placeablePositionTextFields.setYEnabled(true);
            } else {
                placeablePositionTextFields.setYEnabled(false);
                placeablePositionTextFields.setYValue(0);
            }
            if (canGrabZ) {
                placeablePositionTextFields.setZEnabled(true);
            } else {
                placeablePositionTextFields.setZEnabled(false);
                placeablePositionTextFields.setZValue(0);
            }

            placeablePositionTextFields.setXValue(posAvgX);
            placeablePositionTextFields.setYValue(posAvgY);
            placeablePositionTextFields.setZValue(posAvgZ);
        } else {
            placeablePositionTextFields.setXEnabled(false);
            placeablePositionTextFields.setYEnabled(false);
            placeablePositionTextFields.setZEnabled(false);

            placeablePositionTextFields.setXValue(0);
            placeablePositionTextFields.setYValue(0);
            placeablePositionTextFields.setZValue(0);
        }

        if (selectedCount != 0 && canRotate) {
            rotAvgX = rotAvgX / (double) selectedCount;
            rotAvgY = rotAvgY / (double) selectedCount;
            rotAvgZ = rotAvgZ / (double) selectedCount;

            placeableRotationTextFields.setXEnabled(true);
            placeableRotationTextFields.setYEnabled(true);
            placeableRotationTextFields.setZEnabled(true);

            placeableRotationTextFields.setXValue(rotAvgX);
            placeableRotationTextFields.setYValue(rotAvgY);
            placeableRotationTextFields.setZValue(rotAvgZ);
        } else {
            placeableRotationTextFields.setXEnabled(false);
            placeableRotationTextFields.setYEnabled(false);
            placeableRotationTextFields.setZEnabled(false);

            placeableRotationTextFields.setXValue(0);
            placeableRotationTextFields.setYValue(0);
            placeableRotationTextFields.setZValue(0);
        }

        if (selectedCount != 0 && canScale) {
            sclAvgX = sclAvgX / (double) selectedCount;
            sclAvgY = sclAvgY / (double) selectedCount;
            sclAvgZ = sclAvgZ / (double) selectedCount;

            placeableScaleTextFields.setXEnabled(true);
            placeableScaleTextFields.setYEnabled(true);
            placeableScaleTextFields.setZEnabled(true);

            placeableScaleTextFields.setXValue(sclAvgX);
            placeableScaleTextFields.setYValue(sclAvgY);
            placeableScaleTextFields.setZValue(sclAvgZ);
        } else {
            placeableScaleTextFields.setXEnabled(false);
            placeableScaleTextFields.setYEnabled(false);
            placeableScaleTextFields.setZEnabled(false);

            placeableScaleTextFields.setXValue(1);
            placeableScaleTextFields.setYValue(1);
            placeableScaleTextFields.setZValue(1);
        }

        if (selectedCount > 0) {
            boolean stageReservedOnly = true;
            for (String name : ProjectManager.getCurrentClientLevelData().getSelectedPlaceables()) {
                String igName = ProjectManager.getCurrentLevelData().getPlaceableItemGroupName(name);
                if (!Objects.equals(igName, "STAGE_RESERVED")) {
                    stageReservedOnly = false;
                    break;
                }
            }

            placeableItemGroupButton.setEnabled(!stageReservedOnly);

            String commonItemGroup = null; //The name of an item group if all selected placeables have belong to the same item group, or null if the don't

            for (String name : ProjectManager.getCurrentClientLevelData().getSelectedPlaceables()) {
                String igName = ProjectManager.getCurrentLevelData().getPlaceableItemGroupName(name);
                if (commonItemGroup == null) {
                    commonItemGroup = igName;
                }

                if (!Objects.equals(commonItemGroup, igName)) {
                    commonItemGroup = null;
                    break;
                }
            }

            if (commonItemGroup != null) {
                placeableItemGroupButton.setText(commonItemGroup);
            } else {
                placeableItemGroupButton.setText("...");
            }
        } else {
            placeableItemGroupButton.setEnabled(false);
            placeableItemGroupButton.setText(LangManager.getItem("nothingSelected"));
        }

        if (selectedIAsset != null) {
            String[] types = ProjectManager.getCurrentLevelData().getPlaceable(ProjectManager.getCurrentClientLevelData().getSelectedPlaceables().iterator().next()).getAsset().getValidTypes();

            if (types != null) {
                typeList = Arrays.asList(types);
                typeButton.setText(LangManager.getItem(ProjectManager.getCurrentLevelData().getPlaceable(ProjectManager.getCurrentClientLevelData().getSelectedPlaceables().iterator().next()).getAsset().getType()));
                typeButton.setEnabled(true);
            } else {
                typeList = null;
                typeButton.setText(LangManager.getItem("noTypes"));
                typeButton.setEnabled(false);
            }
        } else {
            typeList = null;
            typeButton.setText(LangManager.getItem("noTypes"));
            typeButton.setEnabled(false);
        }

    }

    private void onSelectedObjectsChanged() {
        updatePropertiesObjectsPanel();
        updateOutlinerObjectsPanel();
        timeline.setSelectedObjects(ProjectManager.getCurrentClientLevelData().getSelectedObjects());
    }

    public void updatePropertiesObjectsPanel() {
        int selectedCount = ProjectManager.getCurrentClientLevelData().getSelectedObjects().size();
        if (selectedCount > 0) {
            boolean stageReservedOnly = true;
            for (String name : ProjectManager.getCurrentClientLevelData().getSelectedObjects()) {
                String igName = ProjectManager.getCurrentLevelData().getObjectItemGroupName(name);
                if (!Objects.equals(igName, "STAGE_RESERVED")) {
                    stageReservedOnly = false;
                    break;
                }
            }

            objectItemGroupButton.setEnabled(!stageReservedOnly);

            String commonItemGroup = null; //The name of an item group if all selected objects have belong to the same item group, or null if the don't

            for (String name : ProjectManager.getCurrentClientLevelData().getSelectedObjects()) {
                String igName = ProjectManager.getCurrentLevelData().getObjectItemGroupName(name);
                if (commonItemGroup == null) {
                    commonItemGroup = igName;
                }

                if (!Objects.equals(commonItemGroup, igName)) {
                    commonItemGroup = null;
                    break;
                }
            }

            if (commonItemGroup != null) {
                objectItemGroupButton.setText(commonItemGroup);
            } else {
                objectItemGroupButton.setText("...");
            }
        } else {
            objectItemGroupButton.setEnabled(false);
            objectItemGroupButton.setText(LangManager.getItem("nothingSelected"));
        }
    }

    private void updateOutlinerPlaceablesPanel() {
        if (ProjectManager.getCurrentClientLevelData() != null) {
            //<editor-fold desc="Darken selected placeables in the outliner">
            synchronized (outlinerPlaceablesListBoxLock) {
                for (Map.Entry<String, Component> entry : outlinerPlaceablesListBox.childComponents.entrySet()) {
                    ItemButton button = (ItemButton) entry.getValue();

                    button.setItemGroupCol(ProjectManager.getCurrentLevelData().getPlaceableItemGroup(button.getId()).getColor());

                    if (ProjectManager.getCurrentClientLevelData().isPlaceableSelected(button.getId())) {
//                        button.setBackgroundIdleColor(UIColor.matBlue900());
                        button.setSelected(true);
                    } else {
//                        button.setBackgroundIdleColor(UIColor.matBlue());
                        button.setSelected(false);
                    }
                }
            }
            //</editor-fold>
        }
    }

    private void updateOutlinerObjectsPanel() {
        //<editor-fold desc="Darken selected object in the outliner">
        synchronized (outlinerObjectsListBoxLock) {
            for (Map.Entry<String, Component> entry : outlinerObjectsListBox.childComponents.entrySet()) {
                assert entry.getValue() instanceof OutlinerObject;
                OutlinerObject outlinerObject = (OutlinerObject) entry.getValue();

                outlinerObject.setButtonColor(getOutlinerObjectColor(outlinerObject.getObjectName()));
            }
        }
        //</editor-fold>
    }

    private void onSelectedExternalBackgroundObjectsChanged() {
        updatePropertiesObjectsPanel();
        updateOutlinerObjectsPanel();
    }

    @Deprecated
    private UIColor getOutlinerObjectColor(String name) {
        if (ProjectManager.getCurrentClientLevelData().isObjectSelected(name)) {
            return UIColor.matBlue900();
        } else {
            return UIColor.matBlue();
        }
//        if (ProjectManager.getCurrentLevelData().isObjectBackground(name)) {
//            if (ProjectManager.getCurrentClientLevelData().isObjectSelected(name)) {
//                return UIColor.matPurple900();
//            } else {
//                return UIColor.matPurple();
//            }
//        } else if (ProjectManager.getCurrentLevelData().isObjectBackgroundExternal(name)) {
//            if (ProjectManager.getCurrentClientLevelData().isExternalBackgroundObjectSelected(name)) {
//                return UIColor.matPink900();
//            } else {
//                return UIColor.matPink();
//            }
//        } else if (ProjectManager.getCurrentLevelData().doesObjectHaveAnimData(name)) {
//            if (ProjectManager.getCurrentClientLevelData().isObjectSelected(name)) {
//                return UIColor.matGreen900();
//            } else {
//                return UIColor.matGreen();
//            }
//        } else {
//            if (ProjectManager.getCurrentClientLevelData().isObjectSelected(name)) {
//                return UIColor.matBlue900();
//            } else {
//                return UIColor.matBlue();
//            }
//
    }

    private IUIScreen getTypeSelectorOverlayScreen(double mouseY) {
        final double mousePercentY = mouseY / Display.getHeight();

        return new TypeSelectorOverlayScreen(mousePercentY, typeList);
    }

    private IUIScreen getItemGroupSelectorOverlayScreen(double mouseX, double mouseY) {
        final double mousePercentY = mouseY / Display.getHeight();

        return new ItemGroupSelectorOverlayScreen(mousePercentY, ProjectManager.getCurrentLevelData().getItemGroupMap());
    }

    public void setTypeForSelectedPlaceables(String type) {
        boolean changed = false;

        assert ProjectManager.getCurrentClientLevelData() != null;
        for (String name : ProjectManager.getCurrentClientLevelData().getSelectedPlaceables()) {
            Placeable placeable = ProjectManager.getCurrentLevelData().getPlaceable(name);

            if (!Objects.equals(placeable.getAsset().getType(), type)) {
                changed = true;
            }
        }

        if (changed) {
            addUndoCommand(new UndoAssetTypeChange(ProjectManager.getCurrentClientLevelData(), ProjectManager.getCurrentClientLevelData().getSelectedPlaceables()));
        }

        for (String name : ProjectManager.getCurrentClientLevelData().getSelectedPlaceables()) {
            Placeable placeable = ProjectManager.getCurrentLevelData().getPlaceable(name);

            placeable.getAsset().setType(type);
        }

        updatePropertiesPlaceablesPanel();
    }

    public void setItemGroupForSelectedPlaceables(String itemGroup) {
        boolean changed = false;

        assert ProjectManager.getCurrentClientLevelData() != null;
        for (String name : ProjectManager.getCurrentClientLevelData().getSelectedPlaceables()) {
            if (!Objects.equals(ProjectManager.getCurrentLevelData().getPlaceableItemGroupName(name), itemGroup)) {
                changed = true;
            }
        }

        if (changed) {
            addUndoCommand(new UndoPlaceableItemGroupChange(ProjectManager.getCurrentClientLevelData(), ProjectManager.getCurrentClientLevelData().getSelectedPlaceables()));
        }

        for (String name : ProjectManager.getCurrentClientLevelData().getSelectedPlaceables()) {
            Placeable placeable = ProjectManager.getCurrentLevelData().getPlaceable(name);

            if (placeable.getAsset() instanceof AssetStartPos || placeable.getAsset() instanceof AssetFalloutY) continue; //Start pos and fallout Y cannot change item groups

            ProjectManager.getCurrentLevelData().changePlaceableItemGroup(name, itemGroup);
        }

        updateOutlinerPlaceablesPanel();
        updatePropertiesPlaceablesPanel();
    }

    public void setItemGroupForSelectedObjects(String itemGroup) {
        boolean changed = false;

        assert ProjectManager.getCurrentClientLevelData() != null;
        for (String name : ProjectManager.getCurrentClientLevelData().getSelectedObjects()) {
            if (!Objects.equals(ProjectManager.getCurrentLevelData().getObjectItemGroupName(name), itemGroup)) {
                changed = true;
            }
        }

        if (changed) {
            addUndoCommand(new UndoObjectItemGroupChange(ProjectManager.getCurrentClientLevelData(), ProjectManager.getCurrentClientLevelData().getSelectedObjects()));
        }

        for (String name : ProjectManager.getCurrentClientLevelData().getSelectedObjects()) {
            ProjectManager.getCurrentLevelData().changeObjectItemGroup(name, itemGroup);
        }

        updateOutlinerObjectsPanel();
        updatePropertiesObjectsPanel();
    }

    private void showSettings() {
        setOverlayUiScreen(new SettingsOverlayUIScreen());
    }

    private void showProjectSettings() {
        if (ProjectManager.getCurrentProject() != null && ProjectManager.getCurrentClientLevelData() != null) {
            setOverlayUiScreen(new ProjectSettingsOverlayUIScreen());
        } else {
            sendNotif(LangManager.getItem("noLevelLoaded"), UIColor.matRed());
        }
    }

    private ResourceShaderProgram getCurrentShader() {
        if (SMBLWSettings.showTextures) {
            if (SMBLWSettings.isUnlit) {
                return ResourceManager.getShaderProgram("texUnlitShaderProgram");
            } else {
                return ResourceManager.getShaderProgram("texShaderProgram");
            }
        } else {
            if (SMBLWSettings.isUnlit) {
                return ResourceManager.getShaderProgram("colUnlitShaderProgram");
            } else {
                return ResourceManager.getShaderProgram("colShaderProgram");
            }
        }
    }

    private boolean isCurrentShaderTextured() {
        return SMBLWSettings.showTextures;
    }

    private double getDistance(PosXYZ p1, PosXYZ p2) {
        return Math.sqrt(Math.pow(p2.x - p1.x, 2) + Math.pow(p2.y - p1.y, 2) + Math.pow(p2.z - p1.z, 2));
    }

    private void importConfig() {
        boolean xmlOnly;

        //Only allow XML configs when no OBJ loaded
        if (ProjectManager.getCurrentProject() != null && ProjectManager.getCurrentClientLevelData() != null) {
            xmlOnly = false;
        } else {
            xmlOnly = true;
//            sendNotif(LangManager.getItem("noLevelLoaded"), UIColor.matRed());
        }

        if (!isLoadingProject) {
            new Thread(() -> {
                isLoadingProject = true;
                importObjButton.setEnabled(false);
                importConfigButton.setEnabled(false);
                exportButton.setEnabled(false);
                settingsButton.setEnabled(false);
                projectSettingsButton.setEnabled(false);
                FileDialog fd = new FileDialog((Frame) null);
                fd.setMode(FileDialog.LOAD);
                if (xmlOnly) {
                    fd.setFilenameFilter((dir, filename) -> filename.toUpperCase().endsWith(".XML"));
                } else {
                    fd.setFilenameFilter((dir, filename) -> filename.toUpperCase().endsWith(".TXT") || filename.toUpperCase().endsWith(".XML"));
                }
                fd.setVisible(true);

                File[] files = fd.getFiles();
                if (files != null && files.length > 0) {
                    File file = files[0];
                    LogHelper.info(getClass(), "Opening file: " + file.getAbsolutePath());

                    try {
                        ConfigData configData = new ConfigData();
                        if (file.getName().toUpperCase().endsWith(".XML")) {
                            //Assume XML config file
                            XMLConfigParser.parseConfig(configData, file);

                            if (ProjectManager.getCurrentClientLevelData() != null) {
                                newLevelData(configData.models, true);
                            } else {
                                newLevelData(configData.models, false); //No client level data - Don't replace as there's nothing to replace
                            }


                        } else {
                            //Assume smbcnv config file
                            SMBCnvConfigParser.parseConfig(configData, file);
                        }

                        ClientLevelData cld = ProjectManager.getCurrentClientLevelData();
                        LevelData ld = cld.getLevelData();

                        synchronized (ProjectManager.getCurrentLevelData().getPlacedObjects()) {

                            //TODO: Replace with clear item groups instead

                            cld.clearSelectedPlaceables();
                            ld.clearPlacedObjects();
                            ld.clearBackgroundObjects();
                            outlinerPlaceablesListBox.clearChildComponents();

                            cld.clearSelectedKeyframes();

                            //Add start pos
                            if (configData.startList.size() > 0) {
                                Start start = configData.startList.entrySet().iterator().next().getValue();
                                Placeable startPlaceable = new Placeable(new AssetStartPos());
                                startPlaceable.setPosition(new PosXYZ(start.pos.x, start.pos.y, start.pos.z));
                                startPlaceable.setRotation(new PosXYZ(start.rot.x, start.rot.y, start.rot.z));
                                String name = ld.addPlaceable(LangManager.getItem("assetStartPos"), startPlaceable, "STAGE_RESERVED");
                                outlinerPlaceablesListBox.addChildComponent(getOutlinerPlaceableComponent(name));
                            } else {
                                //No start found - use default start
                                Placeable startPlaceable = new Placeable(new AssetStartPos());
                                startPlaceable.setPosition(new PosXYZ(0, 1, 0));
                                String name = ld.addPlaceable(LangManager.getItem("assetStartPos"), startPlaceable, "STAGE_RESERVED");
                                outlinerPlaceablesListBox.addChildComponent(getOutlinerPlaceableComponent(name));
                            }

                            //Add fallout y
                            Placeable falloutPlaceable = new Placeable(new AssetFalloutY());
                            falloutPlaceable.setPosition(new PosXYZ(0, configData.falloutPlane, 0));
                            String falloutName = ld.addPlaceable(LangManager.getItem("assetFalloutY"), falloutPlaceable, "STAGE_RESERVED");
                            outlinerPlaceablesListBox.addChildComponent(getOutlinerPlaceableComponent(falloutName));

                            //Add goals
                            for (Map.Entry<String, Goal> entry : configData.getFirstItemGroup().goalList.entrySet()) { //TODO: Forced to use static item group
                                Goal goal = entry.getValue();
                                Placeable goalPlaceable = new Placeable(new AssetGoal());
                                goalPlaceable.setPosition(new PosXYZ(goal.pos));
                                goalPlaceable.setRotation(new PosXYZ(goal.rot));

                                String type = "blueGoal";
                                //Type 0 = blueGoal
                                if (goal.type == Goal.EnumGoalType.GREEN) {
                                    type = "greenGoal";
                                } else if (goal.type == Goal.EnumGoalType.RED) {
                                    type = "redGoal";
                                }

                                goalPlaceable.getAsset().setType(type);
                                String name = ld.addPlaceable(entry.getKey(), goalPlaceable);
                                outlinerPlaceablesListBox.addChildComponent(getOutlinerPlaceableComponent(name));
                            }

                            //Add bumpers
                            for (Map.Entry<String, Bumper> entry : configData.getFirstItemGroup().bumperList.entrySet()) { //TODO: Forced to use static item group
                                Bumper bumper = entry.getValue();
                                Placeable bumperPlaceable = new Placeable(new AssetBumper());
                                bumperPlaceable.setPosition(new PosXYZ(bumper.pos));
                                bumperPlaceable.setRotation(new PosXYZ(bumper.rot));
                                bumperPlaceable.setScale(new PosXYZ(bumper.scl));

                                String name = ld.addPlaceable(entry.getKey(), bumperPlaceable);
                                outlinerPlaceablesListBox.addChildComponent(getOutlinerPlaceableComponent(name));
                            }

                            //Add jamabars
                            for (Map.Entry<String, Jamabar> entry : configData.getFirstItemGroup().jamabarList.entrySet()) { //TODO: Forced to use static item group
                                Jamabar jamabar = entry.getValue();
                                Placeable jamabarPlaceable = new Placeable(new AssetJamabar());
                                jamabarPlaceable.setPosition(new PosXYZ(jamabar.pos));
                                jamabarPlaceable.setRotation(new PosXYZ(jamabar.rot));
                                jamabarPlaceable.setScale(new PosXYZ(jamabar.scl));

                                String name = ld.addPlaceable(entry.getKey(), jamabarPlaceable);
                                outlinerPlaceablesListBox.addChildComponent(getOutlinerPlaceableComponent(name));
                            }

                            //Add bananas
                            for (Map.Entry<String, Banana> entry : configData.getFirstItemGroup().bananaList.entrySet()) { //TODO: Forced to use static item group
                                Banana banana = entry.getValue();
                                Placeable bananaPlaceable = new Placeable(new AssetBanana());
                                bananaPlaceable.setPosition(new PosXYZ(banana.pos));

                                String type = "singleBanana";
                                //Type 0 = singleBanana
                                if (banana.type == Banana.EnumBananaType.BUNCH) {
                                    type = "bunchBanana";
                                }

                                bananaPlaceable.getAsset().setType(type);
                                String name = ld.addPlaceable(entry.getKey(), bananaPlaceable);
                                outlinerPlaceablesListBox.addChildComponent(getOutlinerPlaceableComponent(name));
                            }

                            //TODO: Add wormholes and fallout volumes

                            //Mark background objects
                            for (String name : configData.backgroundList) {
                                for (ResourceModel model : ld.getModels()) {
                                    if (model.hasObject(name)) {
                                        ld.changeObjectItemGroup(name, "BACKGROUND_RESERVED");
                                    }
                                }
                            }

                            //Set max time
                            ld.setMaxTime(configData.maxTime);
                            ld.setLeadInTime(configData.leadInTime);

                            //Add anim data //TODO: Revamp for item groups
//                                for (Map.Entry<String, ConfigAnimData> entry : configData.animDataMap.entrySet()) {
//                                    if (ld.getModel().hasObject(entry.getValue().getObjectName())) {
//                                        ld.setAnimData(entry.getValue().getObjectName(), new AnimData(entry.getValue()));
//                                    }
//                                }

                        }

                        updateOutlinerPlaceablesPanel();
                        updateOutlinerObjectsPanel();
                    } catch (IOException e) {
                        LogHelper.error(getClass(), "Failed to open file");
                        LogHelper.error(getClass(), e);
                    } catch (SAXException | ParserConfigurationException e) {
                        LogHelper.error(getClass(), "Failed to parse XML file");
                        LogHelper.error(getClass(), e);
                    }
                }
                projectSettingsButton.setEnabled(true);
                settingsButton.setEnabled(true);
                exportButton.setEnabled(true);
                importConfigButton.setEnabled(true);
                importObjButton.setEnabled(true);
                isLoadingProject = false;
            }, "ConfigFileOpenThread").start();
        } else {
            LogHelper.warn(getClass(), "Tried importing OBJ when already importing OBJ");
        }
    }

    private void onTimelinePosChanged(Float percent) {
        timeline.updatePercent(percent);
//        if (SMBLWSettings.autoUpdateProperties) {
//            assert ProjectManager.getCurrentClientLevelData() != null;
//            if (ProjectManager.getCurrentClientLevelData().getTimelinePos() != percent) {
//                ProjectManager.getCurrentClientLevelData().clearCurrentFrameObjectAnimData();
//            }
//
//            updatePropertiesObjectsPanel();
//        } else {
//            for (Map.Entry<String, AnimData> entry : ProjectManager.getCurrentClientLevelData().getCurrentFrameObjectAnimDataMap().entrySet()) {
//                entry.getValue().moveFirstFrame(percent);
//            }
//        }
    }

    public void addTextField(TextField textField) {
        if (textFields == null) {
            textFields = new HashSet<>();
        }

        textFields.add(textField);
    }

    private void transformObjectAtTime(String name, float time) {
        //TODO
//        ITransformable transform = ProjectManager.getCurrentClientLevelData().getObjectNamedTransform(name, time);
//        PosXYZ translate = transform.getPosition();
//        PosXYZ rotate = transform.getRotation();
//        GL11.glTranslated(translate.x, translate.y, translate.z);
//        GL11.glRotated(rotate.z, 0, 0, 1);
//        GL11.glRotated(rotate.y, 0, 1, 0);
//        GL11.glRotated(rotate.x, 1, 0, 0);
    }

    private void transformObjectAtTime(String name) {
        transformObjectAtTime(name, ProjectManager.getCurrentClientLevelData().getTimelinePos());
    }

    private void addNextFrameAction(UIAction action) {
        nextFrameActions.add(action);
    }

    @Deprecated
    private void moveSelectedKeyframesToBuffer(boolean makeCopy) {
    }

    @Deprecated
    private void commitBufferedKeyframes() {
    }

    @Deprecated
    private void discardBufferedKeyframes() {

    }

}
