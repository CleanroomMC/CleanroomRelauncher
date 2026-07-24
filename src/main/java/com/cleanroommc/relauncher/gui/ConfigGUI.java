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

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.cleanroommc.relauncher.CleanroomRelauncher.isJvm8;

public class ConfigGUI extends JDialog {

    static {
        RelauncherUI.install();
    }
    private static void scaleComponent(Component component, float scale) {
        // scaling rect
        if (component instanceof JTextField ||
                component instanceof AbstractButton ||
                component instanceof JComboBox) {
            Dimension size = component.getPreferredSize();
            component.setPreferredSize(new Dimension((int) (size.width * scale) + 10, (int) (size.height * scale)));
            component.setMaximumSize(new Dimension((int) (size.width * scale) + 10, (int) (size.height * scale)));
        } else if (component instanceof JLabel) {
            JLabel label = (JLabel) component;
            Icon icon = label.getIcon();
            if (icon instanceof ImageIcon) {
                ImageIcon imageIcon = (ImageIcon) icon;
                Image image = imageIcon.getImage();
                if (image != null) {
                    Image scaledImage = image.getScaledInstance(
                            (int) (imageIcon.getIconWidth() * scale),
                            (int) (imageIcon.getIconHeight() * scale),
                            Image.SCALE_SMOOTH);
                    label.setIcon(new ImageIcon(scaledImage));
                }
            }
        }

        // scaling font
        if (component instanceof JLabel ||
                component instanceof AbstractButton ||
                component instanceof JTextField ||
                component instanceof JComboBox) {
            Font font = component.getFont();
            if (font != null) {
                component.setFont(font.deriveFont(font.getSize() * scale));
            }
        }

        // scaling padding
        if (component instanceof AbstractButton) {
            AbstractButton button = (AbstractButton) component;
            Insets margin = button.getMargin();
            if (margin != null) {
                button.setMargin(new Insets(
                        (int) (margin.top * scale),
                        (int) (margin.left * scale),
                        (int) (margin.bottom * scale),
                        (int) (margin.right * scale)
                ));
            }
        } else if (component instanceof JTextField) {
            JTextField textField = (JTextField) component;
            Insets margin = textField.getMargin();
            if (margin != null) {
                textField.setMargin(new Insets(
                        (int) (margin.top * scale),
                        (int) (margin.left * scale),
                        (int) (margin.bottom * scale),
                        (int) (margin.right * scale)
                ));
            }
        } else if (component instanceof JComboBox) {
            JComboBox<?> comboBox = (JComboBox<?>) component;
            Insets margin = comboBox.getInsets();
            if (margin != null) {
                comboBox.setBorder(BorderFactory.createEmptyBorder(
                        (int) (margin.top * scale),
                        (int) (margin.left * scale),
                        (int) (margin.bottom * scale),
                        (int) (margin.right * scale)
                ));
            }
        } else if (component instanceof JLabel) {
            JLabel label = (JLabel) component;
            Insets margin = label.getInsets();
            if (margin != null) {
                label.setBorder(BorderFactory.createEmptyBorder(
                        (int) (margin.top * scale),
                        (int) (margin.left * scale),
                        (int) (margin.bottom * scale),
                        (int) (margin.right * scale)
                ));
            }
        } else if (component instanceof JPanel) {
            JPanel panel = (JPanel) component;
            Border existingBorder = panel.getBorder();

            Insets margin = existingBorder instanceof EmptyBorder ?
                    ((EmptyBorder) existingBorder).getBorderInsets()
                    : new Insets(0, 0, 0, 0);

            panel.setBorder(BorderFactory.createEmptyBorder(
                    (int) (margin.top * scale),
                    (int) (margin.left * scale),
                    (int) (margin.bottom * scale),
                    (int) (margin.right * scale)
            ));
        }

        component.revalidate();
        component.repaint();

        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                scaleComponent(child, scale);
            }
        }
    }
    public static ConfigGUI show(List<CleanroomRelease> eligibleReleases, Consumer<ConfigGUI> consumer) {
        ImageIcon imageIcon = new ImageIcon(Toolkit.getDefaultToolkit().getImage(ConfigGUI.class.getResource("/cleanroom-relauncher.png")));
        return new ConfigGUI(new SupportingFrame("Cleanroom Configuration", imageIcon), eligibleReleases, consumer);
    }

    public CleanroomRelease selected;
    public boolean autoSetup;
    public JavaVersion targetSelected;
    public JavaDistro vendorSelected;
    public String javaPath, javaArgs;
    private final HashSet<ArgsEnum> args = new HashSet<>();
    public void updateJavaArgs() {
        StringBuilder argBuilder = new StringBuilder();
        if (targetSelected.major()< 25) {
            argBuilder.append(ArgsEnum.UnlockExperimentalOptions.getArg()).append(" ");
        }
        for(ArgsEnum arg : args) {
            if (arg == ArgsEnum.CompactObjectHeaders && targetSelected.major() >= 24) {
                argBuilder.append(arg.getArg()).append(" ");
            }else if(arg == ArgsEnum.ZGC){
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
        for(ArgsEnum arg : args) {
            if (arg == ArgsEnum.CompactObjectHeaders && (majorVersion == null || majorVersion >= 24)) {
                argBuilder.append(arg.getArg()).append(" ");
            }else if(arg == ArgsEnum.ZGC){
                argBuilder.append(arg.getArg()).append(" ");
            }
        }
        javaArgs = argBuilder.toString();
    }

    private final JFrame frame;
    private ConfigGUI(SupportingFrame frame, List<CleanroomRelease> eligibleReleases, Consumer<ConfigGUI> consumer) {
        super(frame, frame.getTitle(), true);
        this.frame = frame;

        consumer.accept(this);
        if (selected == null && !eligibleReleases.isEmpty()) {
            selected = eligibleReleases.get(0);
        }
        if (autoSetup) {
            if (targetSelected == null) {
                targetSelected = JavaVersion.parseOrThrow(25);
            }
            if (vendorSelected == null) {
                vendorSelected = JavaDistro.ZULU;
            }
        }

        this.setIconImage(frame.getIconImage());

        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                ConfigGUI.this.requestFocusInWindow();
            }
        });

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                selected = null;
                frame.dispose();

                CleanroomRelauncher.LOGGER.info("ConfigurationChange button was cancelled.");
            }
        });
        this.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice screen = env.getDefaultScreenDevice();
        Rectangle rect = screen.getDefaultConfiguration().getBounds();
        Dimension dialogSize = RelauncherUI.dialogSize(rect);

        JPanel configScreen = ConfigScreen(eligibleReleases);


        this.add(configScreen);
        this.revalidate();
        float scale = Math.max(0.9f, Math.min(1.25f, rect.width / 1920f));
        if (isJvm8()){
            scale = Math.max(0.9f, scale / 1.15f);
        }
        scaleComponent(this, scale);
        RelauncherUI.styleTree(this);

        this.pack();
        this.setSize(dialogSize);
        this.setMinimumSize(new Dimension(520, 560));
        this.setLocationRelativeTo(null);
        RelauncherUI.showInitiallyInForeground(this);
    }
    private JPanel ConfigScreen(List<CleanroomRelease> releases) {
        JPanel container = new JPanel(new BorderLayout());
        RelauncherUI.backgroundPanel(container);

        JPanel mainContent = RelauncherUI.scrollableColumn();
        mainContent.setBorder(new EmptyBorder(18, 24, 24, 24));

        mainContent.add(RelauncherUI.centeredHeader(frame.getIconImage(), "Cleanroom Settings",
                "Changes are saved when you confirm below."));
        mainContent.add(RelauncherUI.card("Cleanroom version", "Select the release to use on the next launch.",
                this.initializeCleanroomPicker(releases)));
        mainContent.add(Box.createRigidArea(new Dimension(0, 12)));
        mainContent.add(RelauncherUI.card("Java runtime", "Automatic setup is recommended for most players.",
                this.initializeJavaPicker()));
        mainContent.add(Box.createRigidArea(new Dimension(0, 12)));
        mainContent.add(RelauncherUI.card("Java arguments", "Optional performance and compatibility flags.",
                this.initializeArgsPanel()));
        JPanel relaunchPanel = this.initializeRelaunchPanel();

        container.add(RelauncherUI.themeToolbar(), BorderLayout.NORTH);
        container.add(RelauncherUI.scrollPane(mainContent), BorderLayout.CENTER);
        container.add(relaunchPanel, BorderLayout.SOUTH);

        return container;
    }

    private JPanel initializeCleanroomPicker(List<CleanroomRelease> eligibleReleases) {
        // Main Panel
        JPanel cleanroomPicker = new JPanel(new BorderLayout(5, 0));
        cleanroomPicker.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        JPanel select = new JPanel();
        select.setLayout(new BoxLayout(select, BoxLayout.Y_AXIS));
        cleanroomPicker.add(select);

        // Title label
        JLabel title = RelauncherUI.fieldLabel("Version");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        select.add(title);
        select.add(Box.createRigidArea(new Dimension(0, 5)));

        // Create dropdown panel
        JPanel dropdown = new JPanel(new BorderLayout(5, 5));
        dropdown.setAlignmentX(Component.LEFT_ALIGNMENT);
        select.add(dropdown);

        // Create the dropdown with release versions
        JComboBox<CleanroomRelease> releaseBox = new JComboBox<>();
        DefaultComboBoxModel<CleanroomRelease> releaseModel = new DefaultComboBoxModel<>();
        for (CleanroomRelease release : eligibleReleases) {
            releaseModel.addElement(release);
        }
        releaseBox.setModel(releaseModel);
        releaseBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof CleanroomRelease) {
                    setText(((CleanroomRelease) value).name);
                }
                return this;
            }
        });
        releaseBox.setSelectedItem(selected);
        releaseBox.setMaximumRowCount(5);
        releaseBox.addActionListener(e -> selected = (CleanroomRelease) releaseBox.getSelectedItem());
        dropdown.add(releaseBox, BorderLayout.CENTER);

        return cleanroomPicker;
    }
    private <T extends Comparable<T>>JPanel initializeJavaTargetsPicker(
            String titleLabel,
            List<T> values,
            T selected,
            Consumer<T> onSelectionChange
    ){
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(RelauncherUI.fieldLabel(titleLabel), BorderLayout.NORTH);

        JComboBox<T> targetBox = new JComboBox<>();
        DefaultComboBoxModel<T> targetModel = new DefaultComboBoxModel<>();
        for (T target : values) {
            targetModel.addElement(target);
        }
        targetBox.setModel(targetModel);
        targetBox.setSelectedItem(selected);
        targetBox.setMaximumRowCount(5);
        targetBox.addActionListener(e -> {
            T newItem = (T) targetBox.getSelectedItem();
            onSelectionChange.accept(newItem);
        });
        panel.add(targetBox, BorderLayout.CENTER);
        return panel;
    }
    private JPanel initializeJavaPicker() {
        // Main Panel
        JPanel javaPicker = new JPanel(new BorderLayout(5, 0));
        javaPicker.setLayout(new BoxLayout(javaPicker, BoxLayout.Y_AXIS));
        javaPicker.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        // Toggle buttons
        JToggleButton simplifiedBtn = new JToggleButton("Automatic Setup", autoSetup);
        JToggleButton manualBtn = new JToggleButton("Choose Java Manually", !autoSetup);

        ButtonGroup group = new ButtonGroup();
        group.add(simplifiedBtn);
        group.add(manualBtn);


        JPanel radioPanel = new JPanel(new GridLayout(1, 2, 8, 0));
        radioPanel.add(simplifiedBtn);
        radioPanel.add(manualBtn);
        radioPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        javaPicker.add(radioPanel);

        JPanel switchableContainer = new JPanel(new BorderLayout());
        javaPicker.add(switchableContainer);


        JPanel targetPanels = new JPanel(new GridLayout(1, 2, 12, 0));
        JPanel versionPanel = this.initializeJavaTargetsPicker(
                "Target Java version",
                IntStream.rangeClosed(21, 26)
                        .mapToObj(JavaVersion::parseOrThrow)
                        .collect(Collectors.toList()),
                targetSelected,
                (JavaVersion val) -> targetSelected = val
        );

        JPanel vendorPanel = this.initializeJavaTargetsPicker(
                "Preferred vendor",
                JavaDistro.all(),
                vendorSelected,
                (JavaDistro val) -> vendorSelected = val
        );
        targetPanels.add(versionPanel);
        targetPanels.add(vendorPanel);

        // Select Panel
        JPanel selectPanel = new JPanel(new BorderLayout());
        JPanel subSelectPanel = new JPanel(new BorderLayout());
        JLabel title = RelauncherUI.fieldLabel("Java executable");
        JTextField text = new JTextField(40);
        text.setText(javaPath);
        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BorderLayout(5, 0));
        northPanel.add(title, BorderLayout.NORTH);
        subSelectPanel.add(northPanel, BorderLayout.NORTH);
        subSelectPanel.add(text, BorderLayout.CENTER);
        // JButton browse = new JButton(UIManager.getIcon("FileView.directoryIcon"));
        JButton browse = new JButton("Browse…");
        RelauncherUI.compact(browse);
        subSelectPanel.add(browse, BorderLayout.EAST);
        JLabel javaStatus = RelauncherUI.statusLabel("Choose a Java executable, or find an installed runtime.");
        subSelectPanel.add(javaStatus, BorderLayout.SOUTH);
        selectPanel.add(subSelectPanel);

        // Java Version Dropdown
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

        // Options Panel
        JPanel options = new JPanel(new GridLayout(1, 2, 8, 0));
        options.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        selectPanel.add(options, BorderLayout.SOUTH);

        // Switch
        ActionListener switchAction = e -> {
            switchableContainer.removeAll();
            if (simplifiedBtn.isSelected()) {
                if (vendorSelected == null){
                    vendorSelected = JavaDistro.ZULU;
                }
                if (targetSelected == null){
                    targetSelected=JavaVersion.parseOrThrow(25);
                }
                switchableContainer.add(targetPanels);
                autoSetup = true;
            } else {
                switchableContainer.add(selectPanel);
                autoSetup = false;
            }
            RelauncherUI.styleTree(switchableContainer);
            switchableContainer.revalidate();
            switchableContainer.repaint();
        };

        simplifiedBtn.addActionListener(switchAction);
        manualBtn.addActionListener(switchAction);
        // Initialize the switchableContainer
        switchableContainer.removeAll();
        if (autoSetup) {
            switchableContainer.add(targetPanels);
            simplifiedBtn.setSelected(true);
        }else{
            switchableContainer.add(selectPanel);
            manualBtn.setSelected(true);
        }
        switchableContainer.revalidate();
        switchableContainer.repaint();

        // JButton download = new JButton("Download");
        JButton autoDetect = new JButton("Find Installed Java");
        JButton test = new JButton("Test Java");
        options.add(autoDetect);
        options.add(test);

        listenToTextFieldUpdate(text, t -> javaPath = t.getText());
        addTextBoxEffect(text);

        browse.addActionListener(e -> {
            File selectedFile = RelauncherUI.chooseJavaExecutable(this, text.getText());
            if (selectedFile != null) {
                text.setText(selectedFile.getAbsolutePath());
                RelauncherUI.status(javaStatus, "Executable selected. Test it before saving.", RelauncherUI.MUTED_TEXT);
            }
        });

        test.addActionListener(e -> {
            String javaPath = text.getText();
            if (javaPath.isEmpty()) {
                RelauncherUI.status(javaStatus, "Select a Java executable before testing.", RelauncherUI.ERROR);
                return;
            }
            File javaFile = new File(javaPath);
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

        JLabel title = RelauncherUI.fieldLabel("Arguments preview");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTextField text = new JTextField(40);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
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
        previewPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        previewPanel.add(text);
        previewPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        previewPanel.add(previewHelp);
        argsPanel.add(previewPanel, BorderLayout.NORTH);

        JPanel argsPickerPanel = new JPanel();
        argsPickerPanel.setLayout(new BoxLayout(argsPickerPanel, BoxLayout.Y_AXIS));
        argsPickerPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        // When arg checkbox is active, run SyncTextField
        Runnable syncTextField = () -> {
            boolean hasSelectedOptions = args!=null && !args.isEmpty();
            text.setEditable(!hasSelectedOptions);
            text.setEnabled(!hasSelectedOptions);
            text.setText(javaArgs);
            previewHelp.setText(hasSelectedOptions
                    ? "Generated from managed options. Clear them to edit directly."
                    : "Edit directly, or use the managed options below.");
        };

        boolean javaArgsSupplied = javaArgs != null && !javaArgs.isEmpty();
        for(ArgsEnum arg : ArgsEnum.values()) {
            if (arg != ArgsEnum.UnlockExperimentalOptions) {
                JCheckBox checkBox = new JCheckBox(RelauncherUI.argumentLabel(arg));

                boolean isPresentInArgs = RelauncherConfiguration.read().argsContain(arg);
                checkBox.setSelected(isPresentInArgs);
                if (javaArgsSupplied){
                    checkBox.setSelected(isPresentInArgs);
                    if (isPresentInArgs){
                        args.add(arg);
                    }
                    syncTextField.run();
                }
                checkBox.addItemListener(e -> {
                    if (e.getStateChange() == ItemEvent.SELECTED) {
                        CleanroomRelauncher.LOGGER.info("Adding {} Argument {}", arg.name(), arg.getStatus());
                        args.add(arg);
                        if (autoSetup){
                            updateJavaArgs();
                        }else{
                            updateJavaArgsPath();
                        }
                    } else {
                        CleanroomRelauncher.LOGGER.info("Removing {} Argument {}", arg.name(), arg.getStatus());
                        args.remove(arg);
                        if (autoSetup){
                            updateJavaArgs();
                        }else{
                            updateJavaArgsPath();
                        }
                    }
                    syncTextField.run();
                    CleanroomRelauncher.LOGGER.warn("args are now {}", javaArgs);
                });

                JPanel optionsPanel = RelauncherUI.optionRow(checkBox, RelauncherUI.argumentDescription(arg));
                argsPickerPanel.add(optionsPanel);
            }
        }
        argsPanel.add(argsPickerPanel, BorderLayout.CENTER);

        return argsPanel;
    }

    private JPanel initializeRelaunchPanel() {
        JPanel configButtonPanel = RelauncherUI.footer();

        JButton configSaveButton = new JButton("Save Settings");
        RelauncherUI.primary(configSaveButton);
        this.getRootPane().setDefaultButton(configSaveButton);
        configSaveButton.addActionListener(e -> {
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Please select a Cleanroom version before saving.", "Cleanroom Release Not Selected", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!autoSetup && (javaPath == null || javaPath.trim().isEmpty())) {
                JOptionPane.showMessageDialog(this, "Please provide a valid Java Executable in order to relaunch.", "Java Executable Not Selected", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (autoSetup && (targetSelected == null || vendorSelected == null)) {
                JOptionPane.showMessageDialog(this, "Please select a valid Java target/vendor in order to relaunch.", "Java Target/Vendor Not Selected", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!autoSetup){
                vendorSelected = null;
                targetSelected = null;
            }
            if (autoSetup){
                updateJavaArgs();
            }else{
                updateJavaArgsPath();
            }
            if (!autoSetup) {
                Runnable test = this.testJavaAndReturn();
                if (test != null) {
                    test.run();
                    return;
                }
            }

            frame.dispose();
        });
        JButton configCancelButton = new JButton("Discard Changes");
        configCancelButton.addActionListener(e -> {
            selected = null;
            frame.dispose();
        });
        configButtonPanel.add(configCancelButton);
        configButtonPanel.add(configSaveButton);

        return configButtonPanel;
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
                return () -> JOptionPane.showMessageDialog(this, "Java 21 is the minimum version for Cleanroom. Currently, Java " + javaInstall.version().major() + " is selected.", "Old Java Version", JOptionPane.ERROR_MESSAGE);
            }
            CleanroomRelauncher.LOGGER.info("Java {} specified from {}", javaInstall.version().major(), javaPath);
        } catch (IOException | RuntimeException e) {
            CleanroomRelauncher.LOGGER.fatal("Failed to execute Java for testing", e);
            return () -> JOptionPane.showMessageDialog(this, "Failed to test Java (more information in console): " + e.getMessage(), "Java Test Failed", JOptionPane.ERROR_MESSAGE);
        }
        return null;
    }

}
