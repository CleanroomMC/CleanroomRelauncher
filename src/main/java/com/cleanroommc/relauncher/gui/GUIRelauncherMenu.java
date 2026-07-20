package com.cleanroommc.relauncher.gui;

import com.cleanroommc.javautils.api.JavaDistro;
import com.cleanroommc.javautils.api.JavaVersion;
import com.cleanroommc.relauncher.download.CleanroomRelease;
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
    private GuiButton settingsButton;
    private volatile String statusMessage = "Changes take effect the next time Minecraft starts.";

    public GUIRelauncherMenu(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();

        int panelTop = Math.max(54, this.height / 2 - 70);
        this.buttonList.add(new GuiButton(ENABLE_BTN_ID, this.width / 2 - 110, panelTop, 220, 20,
                relauncherStatusText(CONFIG.getRelauncherEnabled())));

        settingsButton = new GuiButton(SETTINGS_BTN_ID, this.width / 2 - 110, panelTop + 28, 220, 20,
                "Configure Relauncher…");
        this.buttonList.add(settingsButton);
        this.buttonList.add(new GuiButton(DONE_BTN_ID, this.width / 2 - 110, panelTop + 84, 220, 20,
                I18n.format("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ENABLE_BTN_ID) {
            boolean newState = !CONFIG.getRelauncherEnabled();
            CONFIG.setRelauncherEnabled(newState);
            CONFIG.save();
            button.displayString = relauncherStatusText(newState);
        } else if (button.id == SETTINGS_BTN_ID) {
            openSettings();
        } else if (button.id == DONE_BTN_ID) {
            this.mc.displayGuiScreen(this.parentScreen);
        }
    }

    private void openSettings() {
        settingsButton.enabled = false;
        settingsButton.displayString = "Loading releases…";
        statusMessage = "Fetching available Cleanroom versions…";

        new Thread(() -> {
            try {
                List<CleanroomRelease> releases = releases();
                if (releases.isEmpty()) {
                    throw new IllegalStateException("No Cleanroom releases are available.");
                }
                CleanroomRelease latestRelease = releases.get(0);

                AtomicReference<String> selectedVersion = new AtomicReference<>(CONFIG.getCleanroomVersion());
                AtomicReference<String> javaPath = new AtomicReference<>(CONFIG.getJavaExecutablePath());
                AtomicReference<String> javaArgs = new AtomicReference<>(CONFIG.getJavaArguments());
                AtomicReference<JavaVersion> javaTarget = new AtomicReference<>(CONFIG.getJavaTarget());
                AtomicReference<JavaDistro> javaVendor = new AtomicReference<>(CONFIG.getJavaVendor());
                AtomicBoolean autoSetup = new AtomicBoolean(CONFIG.getAutoSetup());
                CleanroomRelease resolvedSelected = null;
                if (selectedVersion.get() != null) {
                    for (CleanroomRelease release : releases) {
                        if (release.name.equals(selectedVersion.get())) {
                            resolvedSelected = release;
                            break;
                        }
                    }
                }

                final String currentJavaPath = javaPath.get();
                final String currentJavaArgs = javaArgs.get();
                final JavaDistro currentJavaVendor = javaVendor.get();
                final JavaVersion currentJavaTarget = javaTarget.get();
                final CleanroomRelease currentRelease = resolvedSelected;
                final boolean currentAutoSetup = autoSetup.get();
                SwingUtilities.invokeLater(() -> {
                    try {
                        ConfigGUI gui = ConfigGUI.show(releases, config -> {
                            config.selected = currentRelease;
                            config.javaPath = currentJavaPath;
                            config.targetSelected = currentJavaTarget;
                            config.vendorSelected = currentJavaVendor;
                            config.javaArgs = currentJavaArgs;
                            config.autoSetup = currentAutoSetup;
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
                            CONFIG.setJavaSelectionMode(autoSetup.get(), javaTarget.get(), javaVendor.get());
                            CONFIG.save();
                            statusMessage = "Settings saved. Changes apply on the next launch.";
                        } else {
                            statusMessage = "No changes were saved.";
                        }
                    } catch (RuntimeException exception) {
                        statusMessage = "\u00A7cThe settings window could not be opened. Check the log for details.";
                    } finally {
                        restoreSettingsButton();
                    }
                });
            } catch (RuntimeException exception) {
                statusMessage = "\u00A7cCould not load releases. Check your connection and try again.";
                restoreSettingsButton();
            }
        }, "Cleanroom release lookup").start();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int titleY = Math.max(22, this.height / 2 - 124);
        this.drawCenteredString(this.fontRenderer, "Cleanroom Relauncher", this.width / 2, titleY, 0xFFFFFF);
        this.drawCenteredString(this.fontRenderer, statusMessage, this.width / 2, titleY + 18, 0xA9B8C6);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void restoreSettingsButton() {
        this.mc.addScheduledTask(() -> {
            settingsButton.enabled = true;
            settingsButton.displayString = "Configure Relauncher…";
        });
    }

    private static String relauncherStatusText(boolean enabled) {
        return "Relauncher  " + (enabled ? "\u00A7aEnabled" : "\u00A7cDisabled");
    }

    private static List<CleanroomRelease> releases() {
        try {
            return CleanroomRelease.queryAll();
        } catch (IOException e) {
            throw new RuntimeException("Unable to query Cleanroom's releases and no cached releases found.", e);
        }
    }
}
