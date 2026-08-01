package com.cleanroommc.relauncher.gui;

import com.cleanroommc.javautils.api.JavaDistro;
import com.cleanroommc.javautils.api.JavaVersion;
import com.cleanroommc.relauncher.CleanroomRelauncher;
import com.cleanroommc.relauncher.download.CleanroomRelease;
import net.minecraftforge.fml.cleanroomrelauncher.ExitVMBypass;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Startup prompt. Opens on a one-click start screen and keeps the full settings behind
 * an Advanced card, so the common case is a single keystroke.
 */
public class RelauncherGUI extends RelauncherFrame {

    static {
        RelauncherUI.install();
    }

    public static RelauncherGUI show(List<CleanroomRelease> eligibleReleases, Consumer<RelauncherGUI> consumer) {
        Image icon = Toolkit.getDefaultToolkit().getImage(RelauncherGUI.class.getResource("/cleanroom-relauncher.png"));
        RelauncherGUI gui = new RelauncherGUI("Cleanroom Relauncher", icon, eligibleReleases, consumer);
        RelauncherUI.showAndWait(gui);
        return gui;
    }

    public Boolean updateNotification;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private JButton startDefaultButton;
    private JButton advancedDefaultButton;
    private JScrollPane advancedScrollPane;
    private boolean showingAdvanced;

    private RelauncherGUI(String title, Image icon, List<CleanroomRelease> eligibleReleases, Consumer<RelauncherGUI> consumer) {
        super(title, icon);

        this.targetSelected = JavaVersion.parseOrThrow(DEFAULT_JAVA_TARGET);
        this.vendorSelected = JavaDistro.ZULU;

        consumer.accept(this);

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dismissWithoutRelaunch();
            }

            @Override
            public void windowOpened(WindowEvent e) {
                // Otherwise focus lands on the theme toggle and Space flips the theme
                if (startDefaultButton != null) {
                    startDefaultButton.requestFocusInWindow();
                }
            }
        });

        Rectangle rect = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        float scale = prepareScale(rect);

        JPanel startCard = Boolean.TRUE.equals(updateNotification)
                ? createUpdateScreen(eligibleReleases)
                : createStartScreen(eligibleReleases);
        JPanel advancedCard = createAdvancedScreen(eligibleReleases);

        cards.add(startCard, "START");
        cards.add(advancedCard, "ADVANCED");

        this.add(cards);
        RelauncherUI.onEscape(this.getRootPane(), () -> {
            if (showingAdvanced) {
                showStartScreen();
            } else {
                dismissWithoutRelaunch();
            }
        });

        // Floor against the non-scrolling start/update card so buttons/hints stay reachable
        finishWindow(scale, RelauncherUI.dialogSize(rect), startCard::getPreferredSize);
    }

    @Override
    protected String executableSelectedHint() {
        return "Executable selected. Test it before relaunching.";
    }

    private void showAdvancedScreen() {
        if (selected == null && cleanroomReleaseBox != null && cleanroomReleaseBox.getItemCount() > 0) {
            cleanroomReleaseBox.setSelectedIndex(0);
        }
        // Always land on Automatic setup when opening Advanced
        if (javaModeControl != null) {
            javaModeControl.selectLeft();
        }
        showingAdvanced = true;
        cardLayout.show(cards, "ADVANCED");
        // Always open at the top so users don't land mid-form from a prior visit
        if (advancedScrollPane != null) {
            advancedScrollPane.getVerticalScrollBar().setValue(0);
            advancedScrollPane.getViewport().setViewPosition(new Point(0, 0));
        }
        this.getRootPane().setDefaultButton(advancedDefaultButton);
        if (advancedDefaultButton != null) {
            advancedDefaultButton.requestFocusInWindow();
        }
    }

    private void showStartScreen() {
        showingAdvanced = false;
        cardLayout.show(cards, "START");
        this.getRootPane().setDefaultButton(startDefaultButton);
        if (startDefaultButton != null) {
            startDefaultButton.requestFocusInWindow();
        }
    }

    private JPanel createStartScreen(List<CleanroomRelease> releases) {
        JPanel panel = new JPanel(new BorderLayout());
        RelauncherUI.backgroundPanel(panel);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(28, 48, 32, 48));

        JLabel logo = new JLabel(new ImageIcon(windowIcon.getScaledInstance(112, 112, Image.SCALE_SMOOTH)));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        logo.setBorder(new EmptyBorder(0, 0, 20, 0));

        JLabel title = RelauncherUI.title("Ready to launch Cleanroom?");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel subtitle = RelauncherUI.subtitle("We'll pick a compatible Java runtime and finish setup for you.");
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setHorizontalAlignment(SwingConstants.CENTER);
        subtitle.setBorder(new EmptyBorder(6, 0, 18, 0));

        // Card only describes what "Relaunch Now" actually does — not saved Advanced values.
        final String latestName = releases.isEmpty() ? "Latest available" : releases.get(0).name;
        int javaMajor = targetSelected != null ? targetSelected.major() : DEFAULT_JAVA_TARGET;
        JPanel summary = RelauncherUI.summaryCard(
                "Cleanroom", latestName,
                "Java Runtime", "Automatic (" + javaMajor + ")");
        summary.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel summaryNote = RelauncherUI.subtitle("Custom version or Java paths live under Advanced Settings.");
        summaryNote.setFont(summaryNote.getFont().deriveFont(12f));
        summaryNote.setAlignmentX(Component.CENTER_ALIGNMENT);
        summaryNote.setHorizontalAlignment(SwingConstants.CENTER);
        summaryNote.setBorder(new EmptyBorder(10, 12, 0, 12));

        JButton fastRelaunchBtn = new JButton("Relaunch Now");
        RelauncherUI.primary(fastRelaunchBtn);
        RelauncherUI.tooltip(fastRelaunchBtn, "Install the latest Cleanroom with automatic Java setup (Enter)");
        fastRelaunchBtn.addActionListener(e -> {
            autoSetup = true;
            // Match the card: latest release + automatic Java, not leftover Advanced selections.
            selected = releases.isEmpty() ? null : releases.get(0);
            dispose();
        });
        JButton advancedBtn = new JButton("Advanced Settings");
        RelauncherUI.ghost(advancedBtn);
        RelauncherUI.tooltip(advancedBtn, "Choose Cleanroom version, Java runtime, and JVM flags");
        advancedBtn.addActionListener(e -> showAdvancedScreen());

        fastRelaunchBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        advancedBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        RelauncherUI.sizeActionButton(fastRelaunchBtn, 260, 42);
        RelauncherUI.sizeActionButton(advancedBtn, 260, 40);
        startDefaultButton = fastRelaunchBtn;
        this.getRootPane().setDefaultButton(fastRelaunchBtn);

        JLabel hint = RelauncherUI.subtitle("Press Enter to relaunch · Esc to quit");
        hint.setFont(hint.getFont().deriveFont(11f));
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        hint.setHorizontalAlignment(SwingConstants.CENTER);
        hint.setBorder(new EmptyBorder(18, 0, 0, 0));

        content.add(logo);
        content.add(title);
        content.add(subtitle);
        content.add(summary);
        content.add(summaryNote);
        content.add(Box.createRigidArea(new Dimension(0, 18)));
        content.add(fastRelaunchBtn);
        content.add(Box.createRigidArea(new Dimension(0, 10)));
        content.add(advancedBtn);
        content.add(hint);

        panel.add(RelauncherUI.themeToolbar(), BorderLayout.NORTH);
        panel.add(RelauncherUI.centeredScroll(content), BorderLayout.CENTER);

        return panel;
    }

    private JPanel createUpdateScreen(List<CleanroomRelease> releases) {
        JPanel panel = new JPanel(new BorderLayout());
        RelauncherUI.backgroundPanel(panel);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(28, 44, 32, 44));

        JLabel logo = new JLabel(new ImageIcon(windowIcon.getScaledInstance(96, 96, Image.SCALE_SMOOTH)));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        logo.setBorder(new EmptyBorder(0, 0, 18, 0));

        String latestName = releases.get(0).name;
        JLabel title = RelauncherUI.title("Update available");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel subtitle = RelauncherUI.subtitle("Cleanroom " + latestName + " is ready to install.");
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setHorizontalAlignment(SwingConstants.CENTER);
        subtitle.setBorder(new EmptyBorder(6, 0, 14, 0));

        String currentName = selected != null ? selected.name : "Current";
        JPanel transition = RelauncherUI.versionTransition(currentName, latestName);
        transition.setBorder(new EmptyBorder(0, 0, 22, 0));

        JButton fastUpdateBtn = new JButton("Update Now");
        RelauncherUI.primary(fastUpdateBtn);
        RelauncherUI.sizeActionButton(fastUpdateBtn, 160, 40);
        RelauncherUI.tooltip(fastUpdateBtn, "Install " + latestName + " and relaunch");
        fastUpdateBtn.addActionListener(e -> {
            autoSetup = true;
            selected = null;
            dispose();
        });

        JButton skipBtn = new JButton("Keep Current");
        RelauncherUI.sizeActionButton(skipBtn, 150, 40);
        RelauncherUI.tooltip(skipBtn, "Stay on " + currentName + " for now");
        skipBtn.addActionListener(e -> {
            autoSetup = true;
            dispose();
        });

        JButton advancedBtn = new JButton("Advanced Settings");
        RelauncherUI.ghost(advancedBtn);
        RelauncherUI.sizeActionButton(advancedBtn, 200, 38);
        RelauncherUI.tooltip(advancedBtn, "Review version, Java, and JVM flags before updating");
        advancedBtn.addActionListener(e -> showAdvancedScreen());

        startDefaultButton = fastUpdateBtn;
        this.getRootPane().setDefaultButton(fastUpdateBtn);

        JPanel upperBtnBox = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        upperBtnBox.setOpaque(false);
        upperBtnBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        upperBtnBox.add(fastUpdateBtn);
        upperBtnBox.add(skipBtn);

        JPanel lowerBtnBox = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        lowerBtnBox.setOpaque(false);
        lowerBtnBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        lowerBtnBox.setBorder(new EmptyBorder(12, 0, 0, 0));
        lowerBtnBox.add(advancedBtn);

        JLabel hint = RelauncherUI.subtitle("Press Enter to update · Esc to quit");
        hint.setFont(hint.getFont().deriveFont(11f));
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        hint.setHorizontalAlignment(SwingConstants.CENTER);
        hint.setBorder(new EmptyBorder(16, 0, 0, 0));

        content.add(logo);
        content.add(title);
        content.add(subtitle);
        content.add(transition);
        content.add(upperBtnBox);
        content.add(lowerBtnBox);
        content.add(hint);

        panel.add(RelauncherUI.themeToolbar(), BorderLayout.NORTH);
        panel.add(RelauncherUI.centeredScroll(content), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createAdvancedScreen(List<CleanroomRelease> releases) {
        JPanel container = new JPanel(new BorderLayout());
        RelauncherUI.backgroundPanel(container);

        // Advanced always opens in Automatic mode, see showAdvancedScreen()
        JPanel mainContent = settingsColumn(releases, "Advanced Settings",
                "Choose version, Java runtime, and optional JVM flags. Esc to return to the main menu.",
                "Select the release to use.", true);

        advancedScrollPane = RelauncherUI.scrollPane(mainContent);
        container.add(RelauncherUI.themeToolbar(), BorderLayout.NORTH);
        container.add(advancedScrollPane, BorderLayout.CENTER);
        container.add(initializeRelaunchPanel(), BorderLayout.SOUTH);

        return container;
    }

    private JPanel initializeRelaunchPanel() {
        JPanel relaunchButtonPanel = RelauncherUI.footer();

        JButton backButton = new JButton("Back");
        RelauncherUI.tooltip(backButton, "Return to the previous screen (Esc)");
        backButton.addActionListener(e -> showStartScreen());

        JButton relaunchButton = new JButton("Relaunch with Cleanroom");
        RelauncherUI.primary(relaunchButton);
        RelauncherUI.tooltip(relaunchButton, "Validate settings and relaunch (Enter)");
        advancedDefaultButton = relaunchButton;
        relaunchButton.addActionListener(e -> {
            if (selected == null) {
                RelauncherUI.showError(this, "Cleanroom Release Not Selected",
                        "Please select a Cleanroom version in order to relaunch.");
                return;
            }
            if (autoSetup) {
                if (vendorSelected == null) {
                    vendorSelected = JavaDistro.ZULU;
                }
                if (targetSelected == null) {
                    targetSelected = JavaVersion.parseOrThrow(DEFAULT_JAVA_TARGET);
                }
            } else {
                if (javaPath == null || javaPath.trim().isEmpty()) {
                    RelauncherUI.showError(this, "Java Executable Not Selected",
                            "Please provide a valid Java executable in order to relaunch.");
                    return;
                }
                Runnable test = testJavaAndReturn();
                if (test != null) {
                    test.run();
                    return;
                }
                vendorSelected = null;
                targetSelected = null;
            }
            refreshJavaArgs();
            dispose();
        });

        relaunchButtonPanel.add(backButton);
        relaunchButtonPanel.add(relaunchButton);

        return relaunchButtonPanel;
    }

    private void dismissWithoutRelaunch() {
        selected = null;
        dispose();
        CleanroomRelauncher.LOGGER.info("No Cleanroom releases were selected, instance is dismissed.");
        ExitVMBypass.exit(0);
    }

}
