package com.cleanroommc.relauncher.gui;

import com.cleanroommc.javautils.JavaUtils;
import com.cleanroommc.javautils.api.JavaDistro;
import com.cleanroommc.javautils.api.JavaInstall;
import com.cleanroommc.javautils.api.JavaVersion;
import com.cleanroommc.javautils.spi.JavaLocator;
import com.cleanroommc.relauncher.CleanroomRelauncher;
import com.cleanroommc.relauncher.download.CleanroomRelease;
import com.cleanroommc.relauncher.util.enums.ArgsEnum;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Everything the relaunch prompt and the in-game settings window have in common:
 * the selection state, the argument string, and the settings cards.
 * <p>
 * Subclasses own their own framing:
 * <li>Which screens exist</li>
 * <li>Behaviour of Footer Buttons</li>
 * <li>Semantics when closing the frame</li>
 */
public abstract class RelauncherFrame extends JFrame {

    protected static final String ARGS_HELP = "Options below are managed for you. Feel free to enter other options.";

    /** Lowest Java that Cleanroom will run on. */
    protected static final int MINIMUM_JAVA = 21;
    protected static final int DEFAULT_JAVA_TARGET = 25;

    // EnumSet so the generated argument string keeps a stable, declaration-order layout
    protected final EnumSet<ArgsEnum> args = EnumSet.noneOf(ArgsEnum.class);
    protected final Image windowIcon;

    public CleanroomRelease selected;
    public boolean autoSetup;
    public JavaVersion targetSelected;
    public JavaDistro vendorSelected;
    public String javaPath, javaArgs;

    JComboBox<CleanroomRelease> cleanroomReleaseBox;
    RelauncherUI.SegmentedControl javaModeControl;

    protected RelauncherFrame(String title, Image icon) {
        super(title);
        this.windowIcon = icon;
        this.setIconImage(icon);
        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        this.setResizable(false);
    }

    /** Hint shown after the user picks an executable, e.g. "Test it before saving." */
    protected abstract String executableSelectedHint();

    public void updateJavaArgs() {
        javaArgs = buildJavaArgs(targetSelected != null ? targetSelected.major() : null);
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
        javaArgs = buildJavaArgs(majorVersion);
    }

    /**
     * Rebuilds the argument string: implicit flags, then the checked managed flags
     * then whatever the user typed by hand. {@code args} is an EnumSet, so flag order stays stable between runs.
     */
    private String buildJavaArgs(Integer majorVersion) {
        String custom = RelauncherUI.unmanagedArguments(javaArgs);
        StringBuilder argBuilder = new StringBuilder(ArgsEnum.render(args, majorVersion));
        if (!custom.isEmpty()) {
            argBuilder.append(custom).append(" ");
        }
        return argBuilder.toString();
    }

    /** Rebuilds through whichever path matches the current setup mode. */
    protected void refreshJavaArgs() {
        if (autoSetup) {
            updateJavaArgs();
        } else {
            updateJavaArgsPath();
        }
    }

    /**
     * The scrolling column of settings cards shared by both windows.
     *
     * @param initialAutomatic which side of the setup-mode control starts selected
     */
    protected JPanel settingsColumn(List<CleanroomRelease> releases, String headerTitle, String headerSubtitle,
                                    String versionDescription, boolean initialAutomatic) {
        JPanel column = RelauncherUI.scrollableColumn();
        column.setBorder(new EmptyBorder(18, 24, 24, 24));

        column.add(RelauncherUI.centeredHeader(windowIcon, headerTitle, headerSubtitle));
        column.add(RelauncherUI.card("Cleanroom Version", versionDescription,
                initializeCleanroomPicker(releases)));
        column.add(Box.createRigidArea(new Dimension(0, 12)));
        column.add(RelauncherUI.card("Java Runtime", "Automatic setup is recommended for most players.",
                initializeJavaPicker(initialAutomatic)));
        column.add(Box.createRigidArea(new Dimension(0, 12)));
        column.add(RelauncherUI.card("Java Arguments", "Optional performance and compatibility flags.",
                initializeArgsPanel()));
        return column;
    }

    protected JPanel initializeCleanroomPicker(List<CleanroomRelease> eligibleReleases) {
        JPanel cleanroomPicker = new JPanel(new BorderLayout(5, 0));
        cleanroomPicker.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        JPanel select = new JPanel();
        select.setLayout(new BoxLayout(select, BoxLayout.Y_AXIS));
        cleanroomPicker.add(select);

        JLabel title = RelauncherUI.fieldLabelAbove("Version");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        select.add(title);

        JPanel dropdown = new JPanel(new BorderLayout(5, 5));
        dropdown.setAlignmentX(Component.LEFT_ALIGNMENT);
        select.add(dropdown);

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

    @SuppressWarnings("unchecked")
    private <T extends Comparable<T>> JPanel initializeJavaTargetsPicker(String titleLabel, List<T> values,
                                                                        T current, Consumer<T> onSelectionChange) {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.add(RelauncherUI.fieldLabelAbove(titleLabel), BorderLayout.NORTH);

        JComboBox<T> targetBox = new JComboBox<>();
        DefaultComboBoxModel<T> targetModel = new DefaultComboBoxModel<>();
        for (T target : values) {
            targetModel.addElement(target);
        }
        targetBox.setModel(targetModel);
        selectByValue(targetBox, targetModel, current);
        targetBox.setMaximumRowCount(5);
        targetBox.addActionListener(e -> onSelectionChange.accept((T) targetBox.getSelectedItem()));
        panel.add(targetBox, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Selects the model entry equal in value to {@code wanted}.
     * {@link JComboBox#setSelectedItem} matches with {@code equals} and silently drops anything it
     * cannot find, and {@link JavaVersion} has no value equality. Passing the saved target straight
     * in leaves the dropdown showing the first entry while the stored value says something else.
     */
    private static <T extends Comparable<T>> void selectByValue(JComboBox<T> box, DefaultComboBoxModel<T> model, T wanted) {
        if (wanted == null) {
            return;
        }
        for (int i = 0; i < model.getSize(); i++) {
            T candidate = model.getElementAt(i);
            if (candidate == wanted || candidate.compareTo(wanted) == 0) {
                box.setSelectedIndex(i);
                return;
            }
        }
    }

    protected JPanel initializeJavaPicker(boolean initialAutomatic) {
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

        final JLabel modeHelp = RelauncherUI.subtitle(modeHelpText(initialAutomatic));
        modeHelp.setFont(modeHelp.getFont().deriveFont(12f));
        modeHelp.setAlignmentX(Component.LEFT_ALIGNMENT);
        modeHelp.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        modeHelp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        // Sizes to whichever mode is showing, so Automatic does not reserve Manual's extra height
        // Each card pins content to the top so shorter Automatic mode does not stretch the dropdowns
        final CardLayout modeCards = new CardLayout();
        JPanel switchableContainer = RelauncherUI.cardStack(modeCards);
        switchableContainer.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel targetPanels = new JPanel(new GridLayout(1, 2, 12, 0));
        targetPanels.setOpaque(false);
        targetPanels.add(initializeJavaTargetsPicker(
                "Target Java version",
                IntStream.rangeClosed(MINIMUM_JAVA, 26)
                        .mapToObj(JavaVersion::parseOrThrow)
                        .collect(Collectors.toList()),
                targetSelected,
                (JavaVersion val) -> targetSelected = val));
        targetPanels.add(initializeJavaTargetsPicker(
                "Preferred Vendor",
                JavaDistro.all(),
                vendorSelected,
                (JavaDistro val) -> vendorSelected = val));

        JPanel selectPanel = new JPanel(new BorderLayout());
        selectPanel.setOpaque(false);
        JPanel subSelectPanel = new JPanel(new BorderLayout(8, 0));
        subSelectPanel.setOpaque(false);
        JLabel title = RelauncherUI.fieldLabelAbove("Java Executable");
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
                RelauncherUI.status(javaStatus, "Selected " + javaInstall.distro() + " " + javaInstall.version() + ".",
                        RelauncherUI.SUCCESS);
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
            autoSetup = automatic;
            if (automatic) {
                if (vendorSelected == null) {
                    vendorSelected = JavaDistro.ZULU;
                }
                if (targetSelected == null) {
                    targetSelected = JavaVersion.parseOrThrow(DEFAULT_JAVA_TARGET);
                }
            }
            modeCards.show(switchableContainer, automatic ? "auto" : "manual");
            modeHelp.setText(modeHelpText(automatic));
            RelauncherUI.setBadgeShown(recommended, automatic);
            RelauncherUI.styleTree(switchableContainer);
            switchableContainer.revalidate();
            switchableContainer.repaint();
        };

        javaModeControl = RelauncherUI.segmentedControl("Automatic", "Manual", initialAutomatic, applyMode);
        javaModeControl.panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        javaPicker.add(javaModeControl.panel);
        javaPicker.add(modeHelp);
        javaPicker.add(switchableContainer);

        applyMode.accept(initialAutomatic);

        JButton autoDetect = new JButton("Find Installed Java");
        JButton test = new JButton("Test Java");
        autoDetect.setToolTipText("Scan common install locations for Java " + MINIMUM_JAVA + "+");
        test.setToolTipText("Verify the selected executable to be compatible with Cleanroom");
        options.add(autoDetect);
        options.add(test);

        listenToTextFieldUpdate(text, t -> javaPath = t.getText());
        RelauncherUI.installTextFieldFocus(text);

        browse.addActionListener(e -> {
            File selectedFile = RelauncherUI.chooseJavaExecutable(this, text.getText());
            if (selectedFile != null) {
                text.setText(selectedFile.getAbsolutePath());
                RelauncherUI.status(javaStatus, executableSelectedHint(), RelauncherUI.MUTED_TEXT);
            }
        });

        test.addActionListener(e -> {
            String path = text.getText();
            if (path.isEmpty()) {
                RelauncherUI.status(javaStatus, "Select a Java executable before testing.", RelauncherUI.ERROR);
                return;
            }
            if (!new File(path).exists()) {
                RelauncherUI.status(javaStatus, "The selected executable does not exist.", RelauncherUI.ERROR);
                return;
            }
            Runnable failure = testJavaAndReturn();
            if (failure != null) {
                failure.run();
                RelauncherUI.status(javaStatus, "Java validation failed. Review the error and choose another runtime.",
                        RelauncherUI.ERROR);
            } else {
                RelauncherUI.status(javaStatus, "Java executable is compatible and working.", RelauncherUI.SUCCESS);
            }
        });

        autoDetect.addActionListener(e -> detectInstalledJava(autoDetect, javaStatus, versionModel, versionBox, versionDropdown));

        return javaPicker;
    }

    private static String modeHelpText(boolean automatic) {
        return automatic
                ? "Downloads and configures a matching Java runtime for you."
                : "Point Cleanroom at a Java " + MINIMUM_JAVA + "+ executable you already have.";
    }

    private void detectInstalledJava(JButton autoDetect, JLabel javaStatus,
                                     DefaultComboBoxModel<JavaInstall> versionModel,
                                     JComboBox<JavaInstall> versionBox, JPanel versionDropdown) {
        String original = autoDetect.getText();
        autoDetect.setText("Detecting");
        autoDetect.setEnabled(false);
        RelauncherUI.status(javaStatus, "Scanning for compatible Java installations…", RelauncherUI.MUTED_TEXT);

        AtomicInteger dotIndex = new AtomicInteger(0);
        String[] dots = { ".", "..", "..." };
        Timer timer = new Timer(400, te -> {
            autoDetect.setText("Detecting" + dots[dotIndex.get()]);
            dotIndex.set((dotIndex.get() + 1) % dots.length);
        });
        timer.start();

        new SwingWorker<Void, Void>() {

            List<JavaInstall> javaInstalls = Collections.emptyList();

            @Override
            protected Void doInBackground() {
                this.javaInstalls = JavaLocator.locators().parallelStream()
                        .map(JavaLocator::all)
                        .flatMap(Collection::stream)
                        .filter(javaInstall -> javaInstall.version().major() >= MINIMUM_JAVA)
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
                    RelauncherUI.status(javaStatus, "Detection failed. You can still browse for Java manually.",
                            RelauncherUI.ERROR);
                    return;
                }

                if (javaInstalls.isEmpty()) {
                    RelauncherUI.status(javaStatus, "No Java " + MINIMUM_JAVA + "+ installations found. Browse for one manually.",
                            RelauncherUI.ERROR);
                    return;
                }
                versionModel.removeAllElements();
                for (JavaInstall install : javaInstalls) {
                    versionModel.addElement(install);
                }
                versionDropdown.setVisible(true);
                // Newly shown, so it needs styling and a layout pass to claim its space
                RelauncherUI.styleTree(versionDropdown);
                versionDropdown.revalidate();
                versionBox.setSelectedIndex(0);
                RelauncherUI.status(javaStatus, javaInstalls.size() + " compatible Java install"
                        + (javaInstalls.size() == 1 ? " was" : "s were") + " found.", RelauncherUI.SUCCESS);
            }

        }.execute();
    }

    protected JPanel initializeArgsPanel() {
        JPanel argsPanel = new JPanel(new BorderLayout(0, 10));
        argsPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        JLabel title = RelauncherUI.fieldLabelAbove("Java arguments");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTextField text = new JTextField(40);
        // Same UI face as the rest of the app
        text.setText(javaArgs);
        text.setToolTipText("JVM flags passed to Cleanroom, e.g. -Xmx4G");
        text.setAlignmentX(Component.LEFT_ALIGNMENT);
        listenToTextFieldUpdate(text, t -> javaArgs = t.getText());
        RelauncherUI.installTextFieldFocus(text);

        JLabel previewHelp = RelauncherUI.subtitle(ARGS_HELP);
        previewHelp.setFont(previewHelp.getFont().deriveFont(12f));
        previewHelp.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel previewPanel = new JPanel();
        previewPanel.setOpaque(false);
        previewPanel.setLayout(new BoxLayout(previewPanel, BoxLayout.Y_AXIS));
        previewPanel.add(title);
        previewPanel.add(text);
        previewPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        previewPanel.add(previewHelp);
        argsPanel.add(previewPanel, BorderLayout.NORTH);

        JPanel argsPickerPanel = new JPanel();
        argsPickerPanel.setLayout(new BoxLayout(argsPickerPanel, BoxLayout.Y_AXIS));
        argsPickerPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        // Mirrors the regenerated string back into the field. The field stays editable.
        // The checkboxes own their own flags, everything else typed here is preserved verbatim
        Runnable syncTextField = () -> text.setText(javaArgs);

        // Derived from javaArgs, not from a fresh config read: javaArgs is what this panel shows
        // and what gets saved, and the two disagree whenever the in-memory config has unsaved edits
        boolean javaArgsSupplied = javaArgs != null && !javaArgs.trim().isEmpty();
        for (ArgsEnum arg : ArgsEnum.values()) {
            if (!arg.isUserSelectable()) {
                continue; // Implicit, driven by the target Java version rather than by the user
            }
            JCheckBox checkBox = new JCheckBox(RelauncherUI.argumentLabel(arg));
            checkBox.setToolTipText(RelauncherUI.argumentTooltip(arg));

            boolean preselected = javaArgsSupplied ? argumentPresent(javaArgs, arg) : arg.isSelectedByDefault();
            checkBox.setSelected(preselected);
            if (preselected) {
                args.add(arg);
            }
            checkBox.addItemListener(e -> {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    args.add(arg);
                } else {
                    args.remove(arg);
                }
                refreshJavaArgs();
                syncTextField.run();
            });

            argsPickerPanel.add(RelauncherUI.optionRow(checkBox, RelauncherUI.argumentDescription(arg)));
        }
        if (!javaArgsSupplied) {
            updateJavaArgs();
        }
        syncTextField.run();
        argsPanel.add(argsPickerPanel, BorderLayout.CENTER);

        return argsPanel;
    }

    /** Whole-token match, so one flag never counts as present because another contains it. */
    private static boolean argumentPresent(String arguments, ArgsEnum argument) {
        for (String token : arguments.trim().split("\\s+")) {
            if (token.equals(argument.getArg())) {
                return true;
            }
        }
        return false;
    }

    /** @return an action that reports the problem, or null when the selected Java is usable. */
    protected Runnable testJavaAndReturn() {
        try {
            JavaInstall javaInstall = JavaUtils.parseInstall(javaPath);
            if (javaInstall.version().major() < MINIMUM_JAVA) {
                CleanroomRelauncher.LOGGER.fatal("Java {}+ needed, user specified Java {} instead",
                        MINIMUM_JAVA, javaInstall.version());
                return () -> RelauncherUI.showError(this, "Old Java Version",
                        "Java " + MINIMUM_JAVA + " is the minimum version for Cleanroom. Currently, Java "
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

    protected void listenToTextFieldUpdate(JTextField text, Consumer<JTextField> textConsumer) {
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

    /**
     * Records the display scale before any screen is built,
     * so control heights are laid out at the same scale as the fonts.
     */
    protected float prepareScale(Rectangle screenBounds) {
        float scale = RelauncherUI.uiScaleFor(screenBounds);
        RelauncherUI.setUiScale(scale);
        return scale;
    }

    /**
     * Applies the scale to the built tree, then sizes and positions the window.
     * {@code contentFloor} is a supplier because the floor has to be measured after styling and packing.
     * Measured before, and it reports the unstyled tree and comes out too small.
     */
    protected void finishWindow(float scale, Dimension targetSize, Supplier<Dimension> contentFloor) {
        RelauncherUI.scaleComponent(this, scale);
        RelauncherUI.styleTree(this);
        this.pack();
        RelauncherUI.sizeAndGuard(this, targetSize, contentFloor.get());
        this.setLocationRelativeTo(null);
    }

}
