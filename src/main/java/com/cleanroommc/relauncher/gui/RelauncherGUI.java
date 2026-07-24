package com.cleanroommc.relauncher.gui;

import com.cleanroommc.javautils.JavaUtils;
import com.cleanroommc.javautils.api.JavaDistro;
import com.cleanroommc.javautils.api.JavaInstall;
import com.cleanroommc.javautils.api.JavaVersion;
import com.cleanroommc.javautils.spi.JavaLocator;
import com.cleanroommc.relauncher.CleanroomRelauncher;
import com.cleanroommc.relauncher.config.RelauncherConfiguration;
import com.cleanroommc.relauncher.download.CleanroomRelease;
import com.cleanroommc.relauncher.util.enums.ArgsEnum;
import net.minecraftforge.fml.cleanroomrelauncher.ExitVMBypass;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class RelauncherGUI extends JDialog {

    static {
        RelauncherUI.install();
    }

    public static RelauncherGUI show(List<CleanroomRelease> eligibleReleases, Consumer<RelauncherGUI> consumer) {
        ImageIcon imageIcon = new ImageIcon(Toolkit.getDefaultToolkit().getImage(RelauncherGUI.class.getResource("/cleanroom-relauncher.png")));
        return new RelauncherGUI(new SupportingFrame("Cleanroom Relauncher", imageIcon), eligibleReleases, consumer);
    }

    private final HashSet<ArgsEnum> args = new HashSet<>();

    public CleanroomRelease selected;
    public JavaVersion targetSelected = JavaVersion.parseOrThrow(25);
    public JavaDistro vendorSelected = JavaDistro.ZULU;
    public String javaPath, javaArgs;
    public Boolean updateNotification;
    public boolean autoSetup;
    public boolean shouldScale;

    public void updateJavaArgs() {
        StringBuilder argBuilder = new StringBuilder();
        if (targetSelected.major() < 25) {
            argBuilder.append(ArgsEnum.UnlockExperimentalOptions.getArg()).append(" ");
        }
        for (ArgsEnum arg : args) {
            if (arg == ArgsEnum.CompactObjectHeaders && targetSelected.major() >= 24) {
                argBuilder.append(arg.getArg()).append(" ");
            } else if (arg == ArgsEnum.ZGC) {
                argBuilder.append(arg.getArg()).append(" ");
            }
        }
        javaArgs = argBuilder.toString();
    }

    public void updateJavaArgsPath() {
        Integer majorVersion = null;
        if (javaPath != null && !javaPath.trim().isEmpty()) {
            try {
                JavaInstall javaInstall = JavaUtils.parseInstall(javaPath);
                majorVersion = javaInstall.version().major();
            } catch (IOException | RuntimeException e) {
                CleanroomRelauncher.LOGGER.warn("Could not inspect Java path {} while updating arguments", javaPath, e);
            }
        }
        StringBuilder argBuilder = new StringBuilder();
        if (majorVersion != null && majorVersion < 25) {
            argBuilder.append(ArgsEnum.UnlockExperimentalOptions.getArg()).append(" ");
        }
        for (ArgsEnum arg : args) {
            if (arg == ArgsEnum.CompactObjectHeaders && (majorVersion == null || majorVersion >= 24)) {
                argBuilder.append(arg.getArg()).append(" ");
            } else if (arg == ArgsEnum.ZGC) {
                argBuilder.append(arg.getArg()).append(" ");
            }
        }
        javaArgs = argBuilder.toString();
    }

    private final JFrame frame;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private JComboBox<CleanroomRelease> cleanroomReleaseBox;
    private JButton startDefaultButton;
    private JButton advancedDefaultButton;
    private JScrollPane advancedScrollPane;
    private RelauncherUI.SegmentedControl javaModeControl;
    private boolean showingAdvanced;

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

        JLabel logo = new JLabel(new ImageIcon(frame.getIconImage().getScaledInstance(112, 112, Image.SCALE_SMOOTH)));
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
        int javaMajor = targetSelected != null ? targetSelected.major() : 25;
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
        fastRelaunchBtn.setToolTipText("Install the latest Cleanroom with automatic Java setup (Enter)");
        fastRelaunchBtn.addActionListener(e -> {
            autoSetup = true;
            // Match the card: latest release + automatic Java, not leftover Advanced selections.
            selected = releases.isEmpty() ? null : releases.get(0);
            frame.dispose();
        });
        JButton advancedBtn = new JButton("Advanced Settings");
        RelauncherUI.ghost(advancedBtn);
        advancedBtn.setToolTipText("Choose Cleanroom version, Java runtime, and JVM flags");
        fastRelaunchBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        advancedBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        RelauncherUI.sizeActionButton(fastRelaunchBtn, 260, 42);
        RelauncherUI.sizeActionButton(advancedBtn, 260, 40);
        startDefaultButton = fastRelaunchBtn;
        this.getRootPane().setDefaultButton(fastRelaunchBtn);

        advancedBtn.addActionListener(e -> showAdvancedScreen());

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

        JLabel logo = new JLabel(new ImageIcon(frame.getIconImage().getScaledInstance(96, 96, Image.SCALE_SMOOTH)));
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
        fastUpdateBtn.setToolTipText("Install " + latestName + " and relaunch");
        fastUpdateBtn.addActionListener(e -> {
            autoSetup = true;
            selected = null;
            frame.dispose();
        });

        JButton skipBtn = new JButton("Keep Current");
        RelauncherUI.sizeActionButton(skipBtn, 150, 40);
        skipBtn.setToolTipText("Stay on " + currentName + " for now");
        skipBtn.addActionListener(e -> {
            autoSetup = true;
            frame.dispose();
        });

        JButton advancedBtn = new JButton("Advanced Settings");
        RelauncherUI.ghost(advancedBtn);
        RelauncherUI.sizeActionButton(advancedBtn, 200, 38);
        advancedBtn.setToolTipText("Review version, Java, and JVM flags before updating");
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

        JPanel mainContent = RelauncherUI.scrollableColumn();
        mainContent.setBorder(new EmptyBorder(18, 24, 24, 24));

        mainContent.add(RelauncherUI.centeredHeader(frame.getIconImage(), "Advanced Settings",
                "Choose version, Java runtime, and optional JVM flags. Esc to return to the main menu."));
        mainContent.add(RelauncherUI.card("Cleanroom Version", "Select the release to use.",
                this.initializeCleanroomPicker(releases)));
        mainContent.add(Box.createRigidArea(new Dimension(0, 12)));
        mainContent.add(RelauncherUI.card("Java Runtime", "Automatic setup is recommended for most players.",
                this.initializeJavaPicker()));
        mainContent.add(Box.createRigidArea(new Dimension(0, 12)));
        mainContent.add(RelauncherUI.card("Java Arguments", "Optional performance and compatibility flags.",
                this.initializeArgsPanel()));
        JPanel relaunchPanel = this.initializeRelaunchPanel();

        advancedScrollPane = RelauncherUI.scrollPane(mainContent);
        container.add(RelauncherUI.themeToolbar(), BorderLayout.NORTH);
        container.add(advancedScrollPane, BorderLayout.CENTER);
        container.add(relaunchPanel, BorderLayout.SOUTH);

        return container;
    }
    private RelauncherGUI(SupportingFrame frame, List<CleanroomRelease> eligibleReleases, Consumer<RelauncherGUI> consumer) {
        super(frame, frame.getTitle(), true);
        this.frame = frame;

        consumer.accept(this);

        this.setIconImage(frame.getIconImage());

        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                RelauncherGUI.this.requestFocusInWindow();
            }
        });

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dismissWithoutRelaunch();
            }
        });
        this.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice screen = env.getDefaultScreenDevice();
        Rectangle rect = screen.getDefaultConfiguration().getBounds();
        Dimension dialogSize = RelauncherUI.dialogSize(rect);

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

        float scale = Math.max(0.9f, Math.min(1.25f, rect.width / 1920f));
        if (shouldScale) {
            scale = Math.max(0.9f, scale / 1.15f);
        }
        RelauncherUI.scaleComponent(this, scale);
        RelauncherUI.styleTree(this);

        this.pack();
        // Floor against the non-scrolling start/update card so buttons/hints stay reachable
        RelauncherUI.sizeAndGuard(this, dialogSize, startCard.getPreferredSize());
        this.setLocationRelativeTo(null);
        RelauncherUI.showInitiallyInForeground(this);
    }

    private void dismissWithoutRelaunch() {
        selected = null;
        frame.dispose();
        CleanroomRelauncher.LOGGER.info("No Cleanroom releases were selected, instance is dismissed.");
        ExitVMBypass.exit(0);
    }

    private JPanel initializeCleanroomPicker(List<CleanroomRelease> eligibleReleases) {
        // Main Panel
        JPanel cleanroomPicker = new JPanel(new BorderLayout(5, 0));
        cleanroomPicker.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        JPanel select = new JPanel();
        select.setLayout(new BoxLayout(select, BoxLayout.Y_AXIS));
        cleanroomPicker.add(select);

        JLabel title = RelauncherUI.fieldLabelAbove("Version");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        select.add(title);

        // Create dropdown panel
        JPanel dropdown = new JPanel(new BorderLayout(5, 5));
        dropdown.setAlignmentX(Component.LEFT_ALIGNMENT);
        select.add(dropdown);

        // Create the dropdown with release versions
        cleanroomReleaseBox = new JComboBox<>();
        DefaultComboBoxModel<CleanroomRelease> releaseModel = new DefaultComboBoxModel<>();
        for (CleanroomRelease release : eligibleReleases) {
            releaseModel.addElement(release);
        }
        cleanroomReleaseBox.setModel(releaseModel);
        cleanroomReleaseBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof CleanroomRelease) {
                    setText(((CleanroomRelease) value).name);
                }
                return this;
            }
        });
        cleanroomReleaseBox.setSelectedItem(selected);
        cleanroomReleaseBox.setMaximumRowCount(5);
        cleanroomReleaseBox.addActionListener(e -> selected = (CleanroomRelease) cleanroomReleaseBox.getSelectedItem());
        dropdown.add(cleanroomReleaseBox, BorderLayout.CENTER);

        return cleanroomPicker;
    }
    private <T extends Comparable<T>>JPanel initializeJavaTargetsPicker(
            String titleLabel,
            List<T> values,
            T selected,
            Consumer<T> onSelectionChange
    ) {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.add(RelauncherUI.fieldLabelAbove(titleLabel), BorderLayout.NORTH);

        JComboBox<T> targetBox = new JComboBox<>();
        DefaultComboBoxModel<T> targetModel = new DefaultComboBoxModel<>();
        for (T target : values) {
            targetModel.addElement(target);
        }
        targetBox.setModel(targetModel);
        targetBox.setSelectedItem(selected);
        targetBox.setMaximumRowCount(5);
        targetBox.addActionListener(e -> onSelectionChange.accept((T) targetBox.getSelectedItem()));
        panel.add(targetBox, BorderLayout.CENTER);
        return panel;
    }
    private JPanel initializeJavaPicker() {
        JPanel javaPicker = new JPanel();
        javaPicker.setLayout(new BoxLayout(javaPicker, BoxLayout.Y_AXIS));
        javaPicker.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        JPanel modeHeader = new JPanel(new BorderLayout(8, 0));
        modeHeader.setOpaque(false);
        modeHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        modeHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        modeHeader.add(RelauncherUI.fieldLabel("Setup mode"), BorderLayout.WEST);
        // Badge stays in the layout always (paint only toggles) so this row never jumps or clips
        JLabel recommended = RelauncherUI.badge("Recommended", true);
        JPanel badgeWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        badgeWrap.setOpaque(false);
        badgeWrap.add(recommended);
        modeHeader.add(badgeWrap, BorderLayout.EAST);
        javaPicker.add(modeHeader);
        javaPicker.add(Box.createRigidArea(new Dimension(0, 6)));

        final JLabel modeHelp = RelauncherUI.subtitle(autoSetup
                ? "Downloads and configures a matching Java runtime for you."
                : "Point Cleanroom at a Java 21+ executable you already have.");
        modeHelp.setFont(modeHelp.getFont().deriveFont(12f));
        modeHelp.setAlignmentX(Component.LEFT_ALIGNMENT);
        modeHelp.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        modeHelp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        // CardLayout keeps a stable outer height (max of both cards)
        // Each card pins content to the top so shorter Automatic mode does not stretch the dropdowns
        final CardLayout modeCards = new CardLayout();
        JPanel switchableContainer = new JPanel(modeCards);
        switchableContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        switchableContainer.setOpaque(false);

        JPanel targetPanels = new JPanel(new GridLayout(1, 2, 12, 0));
        targetPanels.setOpaque(false);
        JPanel versionPanel = this.initializeJavaTargetsPicker(
                "Target Java Version",
                IntStream.rangeClosed(21, 26)
                        .mapToObj(JavaVersion::parseOrThrow)
                        .collect(Collectors.toList()),
                targetSelected,
                (JavaVersion val) -> targetSelected = val
        );

        JPanel vendorPanel = this.initializeJavaTargetsPicker(
                "Preferred Vendor",
                JavaDistro.all(),
                vendorSelected,
                (JavaDistro val) -> vendorSelected = val
        );
        targetPanels.add(versionPanel);
        targetPanels.add(vendorPanel);

        JPanel selectPanel = new JPanel(new BorderLayout());
        selectPanel.setOpaque(false);
        JPanel subSelectPanel = new JPanel(new BorderLayout(8, 0));
        subSelectPanel.setOpaque(false);
        JLabel title = RelauncherUI.fieldLabelAbove("Java executable");
        JTextField text = new JTextField(40);
        text.setText(javaPath);
        JPanel northPanel = new JPanel(new BorderLayout(5, 0));
        northPanel.setOpaque(false);
        northPanel.add(title, BorderLayout.NORTH);
        subSelectPanel.add(northPanel, BorderLayout.NORTH);
        subSelectPanel.add(text, BorderLayout.CENTER);
        JButton browse = new JButton("Browse…");
        RelauncherUI.compact(browse);
        subSelectPanel.add(browse, BorderLayout.EAST);
        JLabel javaStatus = RelauncherUI.statusLabel("Choose a Java executable, or find an installed runtime.");
        subSelectPanel.add(javaStatus, BorderLayout.SOUTH);
        selectPanel.add(subSelectPanel, BorderLayout.NORTH);

        JPanel versionDropdown = new JPanel(new BorderLayout(5, 0));
        versionDropdown.setAlignmentX(Component.LEFT_ALIGNMENT);
        JComboBox<JavaInstall> versionBox = new JComboBox<>();
        DefaultComboBoxModel<JavaInstall> versionModel = new DefaultComboBoxModel<>();
        versionBox.setModel(versionModel);
        versionBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof JavaInstall) {
                    JavaInstall javaInstall = (JavaInstall) value;
                    setText(javaInstall.distro() + " " + javaInstall.version());
                }
                return this;
            }
        });
        versionBox.setSelectedItem(null);
        versionBox.setMaximumRowCount(10);
        versionBox.addActionListener(e -> {
            if (versionBox.getSelectedItem() != null) {
                JavaInstall javaInstall = (JavaInstall) versionBox.getSelectedItem();
                javaPath = javaInstall.executable(true).toAbsolutePath().toString();
                text.setText(javaPath);
                RelauncherUI.status(javaStatus, "Selected " + javaInstall.distro() + " " + javaInstall.version() + ".", RelauncherUI.SUCCESS);
            }
        });
        versionDropdown.add(versionBox, BorderLayout.CENTER);
        versionDropdown.setVisible(false);
        northPanel.add(versionDropdown, BorderLayout.CENTER);

        JPanel options = new JPanel(new GridLayout(1, 2, 8, 0));
        options.setOpaque(false);
        options.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        selectPanel.add(options, BorderLayout.SOUTH);

        // Top-align each mode so the taller Manual card does not inflate Automatic dropdowns
        JPanel autoCard = new JPanel(new BorderLayout());
        autoCard.setOpaque(false);
        autoCard.add(targetPanels, BorderLayout.NORTH);
        JPanel manualCard = new JPanel(new BorderLayout());
        manualCard.setOpaque(false);
        manualCard.add(selectPanel, BorderLayout.NORTH);

        switchableContainer.add(autoCard, "auto");
        switchableContainer.add(manualCard, "manual");

        Consumer<Boolean> applyMode = automatic -> {
            if (automatic) {
                if (vendorSelected == null) {
                    vendorSelected = JavaDistro.ZULU;
                }
                if (targetSelected == null) {
                    targetSelected = JavaVersion.parseOrThrow(25);
                }
                modeCards.show(switchableContainer, "auto");
                autoSetup = true;
                modeHelp.setText("Downloads and configures a matching Java runtime for you.");
                RelauncherUI.setBadgeShown(recommended, true);
            } else {
                modeCards.show(switchableContainer, "manual");
                autoSetup = false;
                modeHelp.setText("Point Cleanroom at a Java 21+ executable you already have.");
                RelauncherUI.setBadgeShown(recommended, false);
            }
            RelauncherUI.styleTree(switchableContainer);
            switchableContainer.revalidate();
            switchableContainer.repaint();
        };

        javaModeControl = RelauncherUI.segmentedControl("Automatic", "Manual", true, applyMode);
        javaModeControl.panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        javaPicker.add(javaModeControl.panel);
        javaPicker.add(modeHelp);
        javaPicker.add(switchableContainer);

        applyMode.accept(Boolean.TRUE);

        JButton autoDetect = new JButton("Find Installed Java");
        JButton test = new JButton("Test Java");
        autoDetect.setToolTipText("Scan common install locations for Java 21+");
        test.setToolTipText("Verify the selected executable is Cleanroom-compatible");
        options.add(autoDetect);
        options.add(test);

        listenToTextFieldUpdate(text, t -> javaPath = t.getText());
        addTextBoxEffect(text);

        browse.addActionListener(e -> {
            File selectedFile = RelauncherUI.chooseJavaExecutable(this, text.getText());
            if (selectedFile != null) {
                text.setText(selectedFile.getAbsolutePath());
                RelauncherUI.status(javaStatus, "Executable selected. Test it before relaunching.", RelauncherUI.MUTED_TEXT);
            }
        });

        test.addActionListener(e -> {
            String path = text.getText();
            if (path.isEmpty()) {
                RelauncherUI.status(javaStatus, "Select a Java executable before testing.", RelauncherUI.ERROR);
                return;
            }
            File javaFile = new File(path);
            if (!javaFile.exists()) {
                RelauncherUI.status(javaStatus, "The selected executable does not exist.", RelauncherUI.ERROR);
                return;
            }
            Runnable failure = this.testJavaAndReturn();
            if (failure != null) {
                failure.run();
                RelauncherUI.status(javaStatus, "Java validation failed. Review the error and choose another runtime.", RelauncherUI.ERROR);
            } else {
                RelauncherUI.status(javaStatus, "Java executable is compatible and working.", RelauncherUI.SUCCESS);
            }
        });

        autoDetect.addActionListener(e -> {
            String original = autoDetect.getText();
            autoDetect.setText("Detecting");
            autoDetect.setEnabled(false);
            RelauncherUI.status(javaStatus, "Scanning for compatible Java installations…", RelauncherUI.MUTED_TEXT);

            AtomicInteger dotI = new AtomicInteger(0);
            String[] dots = { ".", "..", "..." };
            Timer timer = new Timer(400, te -> {
                autoDetect.setText("Detecting" + dots[dotI.get()]);
                dotI.set((dotI.get() + 1) % dots.length);
            });
            timer.start();

            new SwingWorker<Void, Void>() {

                List<JavaInstall> javaInstalls = Collections.emptyList();

                @Override
                protected Void doInBackground() {
                    this.javaInstalls = JavaLocator.locators().parallelStream()
                            .map(JavaLocator::all)
                            .flatMap(Collection::stream)
                            .filter(javaInstall -> javaInstall.version().major() >= 21)
                            .distinct()
                            .sorted()
                            .collect(Collectors.toList());
                    return null;
                }

                @Override
                protected void done() {
                    timer.stop();
                    autoDetect.setText(original);
                    autoDetect.setEnabled(true);

                    try {
                        get();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        RelauncherUI.status(javaStatus, "Java detection was interrupted.", RelauncherUI.ERROR);
                        return;
                    } catch (ExecutionException failure) {
                        CleanroomRelauncher.LOGGER.error("Failed to detect installed Java runtimes", failure.getCause());
                        RelauncherUI.status(javaStatus, "Detection failed. You can still browse for Java manually.", RelauncherUI.ERROR);
                        return;
                    }

                    if (!javaInstalls.isEmpty()) {
                        versionModel.removeAllElements();
                        for (JavaInstall install : javaInstalls) {
                            versionModel.addElement(install);
                        }
                        versionDropdown.setVisible(true);
                        versionBox.setSelectedIndex(0);
                        RelauncherUI.status(javaStatus, javaInstalls.size() + " compatible Java install" + (javaInstalls.size() == 1 ? " was" : "s were") + " found.", RelauncherUI.SUCCESS);
                    } else {
                        RelauncherUI.status(javaStatus, "No Java 21+ installations found. Browse for one manually.", RelauncherUI.ERROR);
                    }
                }

            }.execute();
        });

        return javaPicker;
    }

    private JPanel initializeArgsPanel() {
        // Main Panel
        JPanel argsPanel = new JPanel(new BorderLayout(0, 10));
        argsPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        JLabel title = RelauncherUI.fieldLabelAbove("Arguments preview");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTextField text = new JTextField(40);
        // Same UI face as the rest of the app
        text.setText(javaArgs);
        listenToTextFieldUpdate(text, t -> javaArgs = t.getText());

        addTextBoxEffect(text);

        JPanel previewPanel = new JPanel();
        previewPanel.setOpaque(false);
        previewPanel.setLayout(new BoxLayout(previewPanel, BoxLayout.Y_AXIS));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel previewHelp = RelauncherUI.subtitle("Edit directly, or use the managed options below.");
        previewHelp.setFont(previewHelp.getFont().deriveFont(12f));
        previewHelp.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewPanel.add(title);
        previewPanel.add(text);
        previewPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        previewPanel.add(previewHelp);
        argsPanel.add(previewPanel, BorderLayout.NORTH);

        JPanel argsPickerPanel = new JPanel();
        argsPickerPanel.setLayout(new BoxLayout(argsPickerPanel, BoxLayout.Y_AXIS));
        argsPickerPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        // When arg checkbox is active, run SyncTextField
        Runnable syncTextField = () -> {
            boolean hasSelectedOptions = !args.isEmpty();
            text.setEditable(!hasSelectedOptions);
            text.setEnabled(!hasSelectedOptions);
            text.setText(javaArgs);
            previewHelp.setText(hasSelectedOptions
                    ? "Generated from managed options. Clear them to edit directly."
                    : "Edit directly, or use the managed options below.");
        };

        boolean javaArgsSupplied = javaArgs != null && !javaArgs.isEmpty();
        for (ArgsEnum arg : ArgsEnum.values()) {
            if (arg != ArgsEnum.UnlockExperimentalOptions) {
                JCheckBox checkBox = new JCheckBox(RelauncherUI.argumentLabel(arg));
                checkBox.setToolTipText(RelauncherUI.argumentTooltip(arg));

                boolean isPresentInArgs = RelauncherConfiguration.read().argsContain(arg);
                checkBox.setSelected(isPresentInArgs || arg.isSelectedByDefault());
                if (javaArgsSupplied) {
                    checkBox.setSelected(isPresentInArgs);
                    if (isPresentInArgs) {
                        args.add(arg);
                    }
                    syncTextField.run();
                } else if (arg.isSelectedByDefault()) {
                    args.add(arg);
                    updateJavaArgs();
                    syncTextField.run();
                }
                checkBox.addItemListener(e -> {
                    if (e.getStateChange() == ItemEvent.SELECTED) {
                        args.add(arg);
                    } else {
                        args.remove(arg);
                    }
                    if (autoSetup) {
                        updateJavaArgs();
                    } else {
                        updateJavaArgsPath();
                    }
                    syncTextField.run();
                });

                JPanel optionsPanel = RelauncherUI.optionRow(checkBox, RelauncherUI.argumentDescription(arg));
                argsPickerPanel.add(optionsPanel);
            }
        }
        argsPanel.add(argsPickerPanel, BorderLayout.CENTER);

        return argsPanel;
    }
    private JPanel initializeRelaunchPanel() {
        JPanel relaunchButtonPanel = RelauncherUI.footer();

        JButton backButton = new JButton("Back");
        backButton.setToolTipText("Return to the previous screen (Esc)");
        backButton.addActionListener(e -> showStartScreen());

        JButton relaunchButton = new JButton("Relaunch with Cleanroom");
        RelauncherUI.primary(relaunchButton);
        relaunchButton.setToolTipText("Validate settings and relaunch (Enter)");
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
                    targetSelected = JavaVersion.parseOrThrow(25);
                }
            }
            if (!autoSetup) {
                if (javaPath == null || javaPath.trim().isEmpty()) {
                    RelauncherUI.showError(this, "Java Executable Not Selected",
                            "Please provide a valid Java executable in order to relaunch.");
                    return;
                }
                Runnable test = this.testJavaAndReturn();
                if (test != null) {
                    test.run();
                    return;
                }
                vendorSelected = null;
                targetSelected = null;
            }
            if (autoSetup) {
                updateJavaArgs();
            } else {
                updateJavaArgsPath();
            }
            frame.dispose();
        });
        relaunchButtonPanel.add(backButton);
        relaunchButtonPanel.add(relaunchButton);

        return relaunchButtonPanel;
    }

    private void listenToTextFieldUpdate(JTextField text, Consumer<JTextField> textConsumer) {
        text.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                textConsumer.accept(text);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                textConsumer.accept(text);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                textConsumer.accept(text);
            }
        });
    }

    private void addTextBoxEffect(JTextField text) {
        RelauncherUI.installTextFieldFocus(text);
    }

    private Runnable testJavaAndReturn() {
        try {
            JavaInstall javaInstall = JavaUtils.parseInstall(javaPath);
            if (javaInstall.version().major() < 21) {
                CleanroomRelauncher.LOGGER.fatal("Java 21+ needed, user specified Java {} instead", javaInstall.version());
                return () -> RelauncherUI.showError(this, "Old Java Version",
                        "Java 21 is the minimum version for Cleanroom. Currently, Java "
                                + javaInstall.version().major() + " is selected.");
            }
            CleanroomRelauncher.LOGGER.info("Java {} specified from {}", javaInstall.version().major(), javaPath);
        } catch (IOException | RuntimeException e) {
            CleanroomRelauncher.LOGGER.fatal("Failed to execute Java for testing", e);
            return () -> RelauncherUI.showError(this, "Java Test Failed",
                    "Failed to test Java (more information in console): " + e.getMessage());
        }
        return null;
    }

}
