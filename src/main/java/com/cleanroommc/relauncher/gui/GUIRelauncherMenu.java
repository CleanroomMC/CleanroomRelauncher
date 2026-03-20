package com.cleanroommc.relauncher.gui;
import com.cleanroommc.relauncher.download.CleanroomRelease;
import com.cleanroommc.relauncher.util.enums.JavaTargetsEnum;
import com.cleanroommc.relauncher.util.enums.VendorsEnum;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import javax.swing.*;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.cleanroommc.relauncher.CleanroomRelauncher.CONFIG;

public class GUIRelauncherMenu extends GuiScreen {

    private final GuiScreen parentScreen;
    private static final int ENABLE_BTN_ID = 1;
    private static final int SETTINGS_BTN_ID = 2;
    private static final int DONE_BTN_ID = 3;

    public GUIRelauncherMenu(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }


    @Override
    public void initGui() {
        this.buttonList.clear();

        String enableText = "Relauncher: " + (CONFIG.getRelauncherEnabled() ? "§aEnabled" : "§cDisabled");
        this.buttonList.add(new GuiButton(ENABLE_BTN_ID, this.width / 2 - 100, this.height / 6 + 24, 200, 20, enableText));

        this.buttonList.add(new GuiButton(SETTINGS_BTN_ID, this.width / 2 - 100, this.height / 6 + 48, 200, 20, "Open Advanced Settings"));

        this.buttonList.add(new GuiButton(DONE_BTN_ID, this.width / 2 - 100, this.height / 6 + 120, 200, 20, I18n.format("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ENABLE_BTN_ID) {
            boolean newState = !CONFIG.getRelauncherEnabled();
            CONFIG.setRelauncherEnabled(newState);
            CONFIG.save();
            button.displayString = "Relauncher: " + (newState ? "§aEnabled" : "§cDisabled");
        } else if (button.id == SETTINGS_BTN_ID) {
            new Thread(() -> {
                List<CleanroomRelease> releases = releases();
                CleanroomRelease latestRelease = releases.get(0);

                AtomicReference<String> selectedVersion = new AtomicReference<>(CONFIG.getCleanroomVersion());
                AtomicReference<String> javaPath = new AtomicReference<>(CONFIG.getJavaExecutablePath());
                AtomicReference<String> javaArgs = new AtomicReference<>(CONFIG.getJavaArguments());
                AtomicReference<JavaTargetsEnum> javaTarget = new AtomicReference<>(CONFIG.getJavaTarget());
                AtomicReference<VendorsEnum> javaVendor = new AtomicReference<>(CONFIG.getJavaVendor());
                AtomicBoolean autoSetup = new AtomicBoolean(CONFIG.getAutoSetup());
                CleanroomRelease resolvedSelected = null;
                if (selectedVersion.get() != null) {
                    for(CleanroomRelease release : releases){
                        if(release.name.equals(selectedVersion.get())){
                            resolvedSelected = release;
                            break;
                        }
                    }
                }
                final String fJavaPath = javaPath.get();
                final String fJavaArgs = javaArgs.get();
                final VendorsEnum fJavaVendor = javaVendor.get();
                final JavaTargetsEnum fJavaTarget = javaTarget.get();
                final CleanroomRelease fSelected = resolvedSelected;
                final boolean fAutoSetup = autoSetup.get();
                SwingUtilities.invokeLater(() -> {
                    ConfigGUI gui = ConfigGUI.show(releases, $ -> {
                        $.selected = fSelected;
                        $.javaPath = fJavaPath;
                        $.targetSelected = fJavaTarget;
                        $.vendorSelected = fJavaVendor;
                        $.javaArgs = fJavaArgs;
                        $.autoSetup = fAutoSetup;
                    });
                    if (gui.selected != null) {
                        selectedVersion.set(gui.selected.name);
                        javaPath.set(gui.javaPath);
                        javaArgs.set(gui.javaArgs);
                        javaTarget.set(gui.targetSelected);
                        javaVendor.set(gui.vendorSelected);
                        autoSetup.set(gui.autoSetup);

                        CONFIG.setCleanroomVersion(selectedVersion.get());
                        CONFIG.setLatestCleanroomVersion(latestRelease.name);
                        CONFIG.setJavaExecutablePath(javaPath.get());
                        CONFIG.setJavaArguments(javaArgs.get());
                        CONFIG.setTargetVendor(javaVendor.get());
                        CONFIG.setTargetJavaVersion(javaTarget.get());
                        CONFIG.setAutoSetup(autoSetup.get());

                        CONFIG.save();
                    }
                        });

            }).start();
        } else if (button.id == DONE_BTN_ID) {
            this.mc.displayGuiScreen(this.parentScreen);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRenderer, "Cleanroom Relauncher Settings (Changes require restart)", this.width / 2, 15, 16777215);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private static List<CleanroomRelease> releases() {
        try {
            return CleanroomRelease.queryAll();
        } catch (IOException e) {
            throw new RuntimeException("Unable to query Cleanroom's releases and no cached releases found.", e);
        }
    }
}