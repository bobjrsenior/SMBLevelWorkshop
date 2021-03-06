package craftedcart.smblevelworkshop.ui;

import craftedcart.smblevelworkshop.resource.LangManager;
import craftedcart.smbworkshopexporter.util.LogHelper;
import io.github.craftedcart.fluidui.FluidUIScreen;
import io.github.craftedcart.fluidui.FontCache;
import io.github.craftedcart.fluidui.component.Label;
import io.github.craftedcart.fluidui.component.Panel;
import io.github.craftedcart.fluidui.component.TextButton;
import io.github.craftedcart.fluidui.plugin.PluginSmoothAnimateAnchor;
import io.github.craftedcart.fluidui.plugin.PluginSmoothAnimatePanelBackgroundColor;
import io.github.craftedcart.fluidui.util.UIColor;

/**
 * @author CraftedCart
 *         Created on 02/10/2016 (DD/MM/YYYY)
 */
public class AskReplaceObjOverlayUIScreen extends FluidUIScreen {

    private final Object syncObject = new Object();
    private boolean shouldReplace;

    public AskReplaceObjOverlayUIScreen() {
        init();
    }

    public boolean waitForShouldReplaceResponse() {
        synchronized(syncObject) {
            try {
                //Calling wait() will block this thread until another thread calls sendNotif() on the object
                syncObject.wait();
            } catch (InterruptedException e) {
                //Happens if something interrupts this thread
                LogHelper.error(getClass(), e);
            }
        }

        return shouldReplace;
    }

    private void init() {

        final Panel backgroundPanel = new Panel();
        backgroundPanel.setOnInitAction(() -> {
            backgroundPanel.setTheme(new DialogUITheme());

            backgroundPanel.setTopLeftPos(0, 0);
            backgroundPanel.setBottomRightPos(0, 0);
            backgroundPanel.setTopLeftAnchor(0, 0);
            backgroundPanel.setBottomRightAnchor(1, 1);
            backgroundPanel.setBackgroundColor(UIColor.pureBlack(0));


            PluginSmoothAnimatePanelBackgroundColor backgroundPanelAnimColor = new PluginSmoothAnimatePanelBackgroundColor();
            backgroundPanelAnimColor.setTargetBackgroundColor(UIColor.pureBlack(0.75));
            backgroundPanel.addPlugin(backgroundPanelAnimColor);
        });
        addChildComponent("backgroundPanel", backgroundPanel);

        final Panel mainPanel = new Panel();
        mainPanel.setOnInitAction(() -> {
            mainPanel.setTopLeftPos(-256, -128);
            mainPanel.setBottomRightPos(256, 128);
            mainPanel.setTopLeftAnchor(0.5, 1.5);
            mainPanel.setBottomRightAnchor(0.5, 1.5);

            PluginSmoothAnimateAnchor mainPanelAnimAnchor = new PluginSmoothAnimateAnchor();
            mainPanelAnimAnchor.setTargetTopLeftAnchor(0.5, 0.5);
            mainPanelAnimAnchor.setTargetBottomRightAnchor(0.5, 0.5);
            mainPanel.addPlugin(mainPanelAnimAnchor);
        });
        backgroundPanel.addChildComponent("mainPanel", mainPanel);

        final Label titleLabel = new Label();
        titleLabel.setOnInitAction(() -> {
            titleLabel.setTopLeftPos(24, 24);
            titleLabel.setBottomRightPos(-24, 72);
            titleLabel.setTopLeftAnchor(0, 0);
            titleLabel.setBottomRightAnchor(1, 0);
            titleLabel.setTextColor(UIColor.matGrey900());
            titleLabel.setText(LangManager.getItem("importObj"));
            titleLabel.setFont(FontCache.getUnicodeFont("Roboto-Regular", 24));
        });
        mainPanel.addChildComponent("titleLabel", titleLabel);

        final Label label = new Label();
        label.setOnInitAction(() -> {
            label.setTopLeftPos(24, 72);
            label.setBottomRightPos(-24, -72);
            label.setTopLeftAnchor(0, 0);
            label.setBottomRightAnchor(1, 1);
            label.setTextColor(UIColor.matGrey900());
            label.setText(LangManager.getItem("askReplaceCurrentModel"));
        });
        mainPanel.addChildComponent("label", label);

        final TextButton replaceButton = new TextButton();
        replaceButton.setOnInitAction(() -> {
            replaceButton.setTopLeftPos(-152, -48);
            replaceButton.setBottomRightPos(-24, -24);
            replaceButton.setTopLeftAnchor(1, 1);
            replaceButton.setBottomRightAnchor(1, 1);
            replaceButton.setText(LangManager.getItem("replace"));
        });
        replaceButton.setOnLMBAction(() -> {
            shouldReplace = true;
            synchronized(syncObject) {
                syncObject.notify();
            }
            assert parentComponent instanceof FluidUIScreen;
            ((FluidUIScreen) parentComponent).setOverlayUiScreen(null); //Hide on replace
        });
        mainPanel.addChildComponent("replaceButton", replaceButton);

        final TextButton newProjectButton = new TextButton();
        newProjectButton.setOnInitAction(() -> {
            newProjectButton.setTopLeftPos(-284, -48);
            newProjectButton.setBottomRightPos(-156, -24);
            newProjectButton.setTopLeftAnchor(1, 1);
            newProjectButton.setBottomRightAnchor(1, 1);
            newProjectButton.setText(LangManager.getItem("newProject"));
        });
        newProjectButton.setOnLMBAction(() -> {
            shouldReplace = false;
            synchronized(syncObject) {
                syncObject.notify();
            }
            assert parentComponent instanceof FluidUIScreen;
            ((FluidUIScreen) parentComponent).setOverlayUiScreen(null); //Hide on new project
        });
        mainPanel.addChildComponent("newProjectButton", newProjectButton);

    }

}

