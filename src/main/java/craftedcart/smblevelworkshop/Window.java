package craftedcart.smblevelworkshop;

import craftedcart.smblevelworkshop.resource.ResourceManager;
import craftedcart.smblevelworkshop.util.CrashHandler;
import io.github.craftedcart.fluidui.IUIScreen;
import io.github.craftedcart.fluidui.util.PosXY;
import io.github.craftedcart.fluidui.util.UIUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.*;
import org.newdawn.slick.SlickException;

import java.awt.*;
import java.io.IOException;

/**
 * @author CraftedCart
 *         Created on 06/09/2016 (DD/MM/YYYY)
 */
public class Window {

    public static SharedDrawable drawable;
    public static IUIScreen uiScreen;

    public static boolean running = true;

    public static String openGLVersion = "OpenGL hasn't been initialized";

    public static void init() throws LWJGLException, IOException, FontFormatException {
        ResourceManager.preInit();

        Display.setDisplayMode(new org.lwjgl.opengl.DisplayMode(800, 500));
        Display.setResizable(true);
        Display.setTitle(ResourceManager.initResources.getString("smbLevelWorkshop"));
        PixelFormat pxFormat = new PixelFormat().withStencilBits(8);
        Display.create(pxFormat);
        openGLVersion = GL11.glGetString(GL11.GL_VERSION);
        drawable = new SharedDrawable(Display.getDrawable());

        new Thread(() -> {
            try {
                SMBLevelWorkshop.init();
            } catch (LWJGLException | SlickException | FontFormatException | IOException e) {
                e.printStackTrace();
            }
        }, "initThread").start();

        GL11.glClearColor(33f / 256f, 33f / 256f, 33f / 256f, 1);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        Display.setVSyncEnabled(true); //Enable VSync

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler.UNCAUGHT_EXCEPTION_HANDLER); //Set the uncaught exception handler (Create a crash report)

        while (!Display.isCloseRequested() && running) { //Render loop
            try {
                renderLoop();
            } catch (Exception e) {
                CrashHandler.handleCrash(Thread.currentThread(), e, true); //Send it to the crash handler
            }
        }

        SMBLevelWorkshop.onQuit();

    }

    private static void renderLoop() {
        setMatrix();

        UIUtils.calcStuff();

        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        if (uiScreen != null) {
            Mouse.poll();
            while (Mouse.next()) {
                if (Mouse.getEventButtonState()) {
                    uiScreen.onClick(Mouse.getEventButton(), new PosXY(Mouse.getEventX(), Display.getHeight() - Mouse.getEventY()));
                }
            }

            Keyboard.poll();
            while (Keyboard.next()) {
                if (Keyboard.getEventKeyState()) {
                    uiScreen.onKey(Keyboard.getEventKey(), Keyboard.getEventCharacter());
                }
            }

            uiScreen.draw();

        }

        Display.update();
        Display.sync(60); //Cap to 60 FPS
    }

    public static void setMatrix() {
        GL11.glViewport(0, 0, Display.getWidth(), Display.getHeight());
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, Display.getWidth(), Display.getHeight(), 0, 1, -1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    public static void setUIScreen(IUIScreen IUIScreen) {
        Window.uiScreen = IUIScreen;
    }

}
