package com.cleanroommc.relauncher.gui;

import com.cleanroommc.javautils.api.JavaDistro;
import com.cleanroommc.javautils.api.JavaVersion;
import com.cleanroommc.relauncher.CleanroomRelauncher;
import com.cleanroommc.relauncher.download.CleanroomRelease;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.function.Consumer;

/** In-game settings window. Edits the saved configuration without relaunching anything. */
public class ConfigGUI extends RelauncherFrame {

    static {
        RelauncherUI.install();
    }

    public static ConfigGUI show(List<CleanroomRelease> eligibleReleases, Consumer<ConfigGUI> consumer) {
        Image icon = Toolkit.getDefaultToolkit().getImage(ConfigGUI.class.getResource("/cleanroom-relauncher.png"));
        ConfigGUI gui = new ConfigGUI("Cleanroom Configuration", icon, eligibleReleases, consumer);
        RelauncherUI.showAndWait(gui);
        return gui;
    }

    private ConfigGUI(String title, Image icon, List<CleanroomRelease> eligibleReleases, Consumer<ConfigGUI> consumer) {
        super(title, icon);

        consumer.accept(this);
        if (selected == null && !eligibleReleases.isEmpty()) {
            selected = eligibleReleases.get(0);
        }
        if (autoSetup) {
            if (targetSelected == null) {
                targetSelected = JavaVersion.parseOrThrow(DEFAULT_JAVA_TARGET);
            }
            if (vendorSelected == null) {
                vendorSelected = JavaDistro.ZULU;
            }
        }

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                discardAndClose();
            }

            @Override
            public void windowOpened(WindowEvent e) {
                // Otherwise focus lands on the theme toggle
                JButton defaultButton = ConfigGUI.this.getRootPane().getDefaultButton();
                if (defaultButton != null) {
                    defaultButton.requestFocusInWindow();
                }
            }
        });

        Rectangle rect = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        float scale = prepareScale(rect);

        this.add(configScreen(eligibleReleases));
        this.revalidate();
        RelauncherUI.onEscape(this.getRootPane(), this::discardAndClose);

        finishWindow(scale, RelauncherUI.dialogSize(rect), this::getPreferredSize);
    }

    @Override
    protected String executableSelectedHint() {
        return "Executable selected. Test it before saving.";
    }

    private JPanel configScreen(List<CleanroomRelease> releases) {
        JPanel container = new JPanel(new BorderLayout());
        RelauncherUI.backgroundPanel(container);

        JPanel mainContent = settingsColumn(releases, "Cleanroom Settings",
                "Changes apply on the next launch. Esc discards without saving.",
                "Select the release to use on the next launch.", autoSetup);

        container.add(RelauncherUI.themeToolbar(), BorderLayout.NORTH);
        container.add(RelauncherUI.scrollPane(mainContent), BorderLayout.CENTER);
        container.add(initializeSavePanel(), BorderLayout.SOUTH);

        return container;
    }

    private JPanel initializeSavePanel() {
        JPanel configButtonPanel = RelauncherUI.footer();

        JButton configSaveButton = new JButton("Save Settings");
        RelauncherUI.primary(configSaveButton);
        configSaveButton.setToolTipText("Save and apply on next launch (Enter)");
        this.getRootPane().setDefaultButton(configSaveButton);
        configSaveButton.addActionListener(e -> {
            if (selected == null) {
                RelauncherUI.showError(this, "Cleanroom Release Not Selected", "Please select a Cleanroom version before saving.");
                return;
            }
            if (!autoSetup && (javaPath == null || javaPath.trim().isEmpty())) {
                RelauncherUI.showError(this, "Java Executable Not Selected", "Please provide a valid Java executable before saving.");
                return;
            }
            if (autoSetup && (targetSelected == null || vendorSelected == null)) {
                RelauncherUI.showError(this, "Java Target/Vendor Not Selected", "Please select a valid Java target and vendor before saving.");
                return;
            }
            if (!autoSetup) {
                Runnable test = testJavaAndReturn();
                if (test != null) {
                    test.run();
                    return;
                }
            }
            refreshJavaArgs();
            if (!autoSetup) {
                vendorSelected = null;
                targetSelected = null;
            }
            dispose();
        });

        JButton configCancelButton = new JButton("Discard Changes");
        RelauncherUI.ghost(configCancelButton);
        configCancelButton.setToolTipText("Close without saving (Esc)");
        configCancelButton.addActionListener(e -> discardAndClose());

        configButtonPanel.add(configCancelButton);
        configButtonPanel.add(configSaveButton);

        return configButtonPanel;
    }

    private void discardAndClose() {
        selected = null;
        dispose();
        CleanroomRelauncher.LOGGER.info("ConfigurationChange button was cancelled.");
    }

}
