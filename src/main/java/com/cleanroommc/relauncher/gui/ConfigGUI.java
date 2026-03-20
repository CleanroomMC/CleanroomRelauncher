package com.cleanroommc.relauncher.gui;

import com.cleanroommc.javautils.JavaUtils;
import com.cleanroommc.javautils.api.JavaInstall;
import com.cleanroommc.javautils.spi.JavaLocator;
import com.cleanroommc.platformutils.Platform;
import com.cleanroommc.relauncher.CleanroomRelauncher;
import com.cleanroommc.relauncher.config.RelauncherConfiguration;
import com.cleanroommc.relauncher.download.CleanroomRelease;
import com.cleanroommc.relauncher.util.enums.ArgsEnum;
import com.cleanroommc.relauncher.util.enums.IDisplayableEnum;
import com.cleanroommc.relauncher.util.enums.JavaTargetsEnum;
import com.cleanroommc.relauncher.util.enums.VendorsEnum;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.cleanroommc.relauncher.CleanroomRelauncher.isJvm8;

public class ConfigGUI extends JDialog {

    static {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignore) { }
        Font baseFont = new Font("SansSerif", Font.PLAIN, 12);
        String[] fontKeys = {
                "Button.font", "Label.font", "ComboBox.font",
                "TextField.font", "CheckBox.font", "ToggleButton.font",
                "Panel.font", "OptionPane.font", "List.font"
        };
        for (String key : fontKeys) {
            UIManager.put(key, baseFont);
        }
        System.setProperty("awt.useSystemAAFontSettings","on");
        System.setProperty("swing.aatext", "true");
    }
    private static void scaleComponent(Component component, float scale) {
        // scaling rect
        if (component instanceof JTextField ||
                component instanceof JButton ||
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
                component instanceof JButton ||
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
    public JavaTargetsEnum targetSelected;
    public VendorsEnum vendorSelected;
    public String javaPath, javaArgs;
    private static HashSet<ArgsEnum> args = new HashSet<>();
    public void updateJavaArgs() {
        StringBuilder argBuilder = new StringBuilder();
        if (targetSelected.getInternalNameInt()< 25) {
            argBuilder.append(ArgsEnum.UnlockExperimentalOptions.getArg()).append(" ");
        }
        for(ArgsEnum arg : args) {
            if (arg == ArgsEnum.CompactObjectHeaders && targetSelected.getInternalNameInt() >= 24) {
                argBuilder.append(arg.getArg()).append(" ");
            }else if(arg == ArgsEnum.ZGC){
                argBuilder.append(arg.getArg()).append(" ");
            }
        }
        javaArgs = argBuilder.toString();
    }
    public void updateJavaArgsPath() {
        int majorVersion;
        try {
            JavaInstall javaInstall = JavaUtils.parseInstall(javaPath);
            majorVersion = javaInstall.version().major();
        } catch (IOException e) {
            CleanroomRelauncher.LOGGER.warn("could not parse path {}", javaPath, e);
            javaPath = "";
            return;
        }
        StringBuilder argBuilder = new StringBuilder();
        if (majorVersion < 25) {
            argBuilder.append(ArgsEnum.UnlockExperimentalOptions.getArg()).append(" ");
        }
        for(ArgsEnum arg : args) {
            if (arg == ArgsEnum.CompactObjectHeaders && majorVersion >= 24) {
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
        this.setAlwaysOnTop(true);

        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice screen = env.getDefaultScreenDevice();
        Rectangle rect = screen.getDefaultConfiguration().getBounds();
        int width = (int) (rect.width / 3.25f);//Changed values to accommodate for new GUI
        int height = (int) (width / 0.95f);
        int x = (rect.width - width) / 2;
        int y = (rect.height - height) / 2;
        this.setLocation(x, y);

        JPanel configScreen = ConfigScreen(eligibleReleases);


        this.add(configScreen);
        this.revalidate();
        float scale = rect.width / 1463f;
        if (isJvm8()){
            scale = scale/1.5f;
        }
        scaleComponent(this, scale);

        this.pack();
        this.setSize(width, height);
        this.setVisible(true);
        this.setAutoRequestFocus(true);
    }
    private JPanel ConfigScreen(List<CleanroomRelease> releases) {
        JPanel container = new JPanel(new BorderLayout());


        JPanel mainContent = new JPanel();
        mainContent.setLayout(new BoxLayout(mainContent, BoxLayout.Y_AXIS));

        JLabel logo = new JLabel(new ImageIcon(frame.getIconImage().getScaledInstance(80, 80, Image.SCALE_SMOOTH)));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);

        mainContent.add(logo);
        mainContent.add(this.initializeCleanroomPicker(releases));
        mainContent.add(this.initializeJavaPicker());
        mainContent.add(this.initializeArgsPanel());

        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.add(mainContent);

        JPanel relaunchPanel = this.initializeRelaunchPanel();

        container.add(wrapper, BorderLayout.CENTER);
        container.add(relaunchPanel, BorderLayout.SOUTH);

        return container;
    }
    private JPanel initializeCleanroomPicker(List<CleanroomRelease> eligibleReleases) {
        // Main Panel
        JPanel cleanroomPicker = new JPanel(new BorderLayout(5, 0));
        cleanroomPicker.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel select = new JPanel();
        select.setLayout(new BoxLayout(select, BoxLayout.Y_AXIS));
        cleanroomPicker.add(select);

        // Title label
        JLabel title = new JLabel("Select Cleanroom Version:");
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
    private <T extends Enum<T> & IDisplayableEnum>JPanel initializeJavaTargetsPicker(
            String titleLabel,
            T[] values,
            T selected,
            Consumer<T> onSelectionChange
    ){
        // Target Java Version
        // Panel
        JPanel panel = new JPanel(new BorderLayout(5, 0));

        JPanel select = new JPanel();
        select.setLayout(new BoxLayout(select, BoxLayout.Y_AXIS));
        panel.add(select);

        // Title label
        JLabel title = new JLabel(titleLabel);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        select.add(title);
        select.add(Box.createRigidArea(new Dimension(0, 5)));

        // Create dropdown panel
        JPanel dropdown = new JPanel(new BorderLayout(5, 5));
        dropdown.setAlignmentX(Component.LEFT_ALIGNMENT);
        select.add(dropdown);

        JComboBox<T> targetBox = new JComboBox<>();
        DefaultComboBoxModel<T> targetModel = new DefaultComboBoxModel<>();
        for (T target : values) {
            targetModel.addElement(target);
        }
        targetBox.setModel(targetModel);
        targetBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof IDisplayableEnum) {
                    setText((((IDisplayableEnum) value).getDisplayName()));
                }
                return this;
            }
        });
        targetBox.setSelectedItem(selected);
        targetBox.setMaximumRowCount(5);
        targetBox.addActionListener(e -> {
            T newItem = (T) targetBox.getSelectedItem();
            onSelectionChange.accept(newItem);
        });
        dropdown.add(targetBox, BorderLayout.CENTER);
        return panel;
    }
    private JPanel initializeJavaPicker() {
        // Main Panel
        JPanel javaPicker = new JPanel(new BorderLayout(5, 0));
        javaPicker.setLayout(new BoxLayout(javaPicker, BoxLayout.Y_AXIS));
        javaPicker.setBorder(BorderFactory.createEmptyBorder(20, 10, 0, 10));

        // Toggle buttons
        JToggleButton simplifiedBtn = new JToggleButton("Automatic Setup", autoSetup);
        JToggleButton manualBtn = new JToggleButton("Manual Setup", !autoSetup);

        ButtonGroup group = new ButtonGroup();
        group.add(simplifiedBtn);
        group.add(manualBtn);


        JPanel radioPanel = new JPanel(new BorderLayout(5, 0));
        radioPanel.setLayout(new BoxLayout(radioPanel, BoxLayout.X_AXIS));
        radioPanel.add(simplifiedBtn);
        radioPanel.add(manualBtn);
        javaPicker.add(radioPanel);

        JPanel switchableContainer = new JPanel(new BorderLayout());
        javaPicker.add(switchableContainer);


        JPanel targetPanels = new JPanel();
        targetPanels.setLayout(new BoxLayout(targetPanels, BoxLayout.Y_AXIS));
        JPanel versionPanel = this.initializeJavaTargetsPicker(
                "Select Target Java Version:",
                JavaTargetsEnum.values(),
                targetSelected,
                (JavaTargetsEnum val) -> targetSelected = val
        );

        JPanel vendorPanel = this.initializeJavaTargetsPicker(
                "Select Preferred Vendor:",
                VendorsEnum.values(),
                vendorSelected,
                (VendorsEnum val) -> vendorSelected = val
        );
        targetPanels.add(versionPanel, BorderLayout.NORTH);
        targetPanels.add(vendorPanel, BorderLayout.SOUTH);

        // Select Panel
        JPanel selectPanel = new JPanel();
        selectPanel.setLayout(new BoxLayout(selectPanel, BoxLayout.Y_AXIS));
        JPanel subSelectPanel = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Select Java Executable:");
        JTextField text = new JTextField(100);
        text.setText(javaPath);
        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BorderLayout(5, 0));
        northPanel.add(title, BorderLayout.NORTH);
        subSelectPanel.add(northPanel, BorderLayout.NORTH);
        subSelectPanel.add(text, BorderLayout.CENTER);
        // JButton browse = new JButton(UIManager.getIcon("FileView.directoryIcon"));
        JButton browse = new JButton("Browse");
        subSelectPanel.add(browse, BorderLayout.EAST);
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
                    setText(javaInstall.vendor() + " " + javaInstall.version());
                }
                return this;
            }
        });
        versionBox.setSelectedItem(null);
        versionBox.setMaximumRowCount(10);
        versionBox.addActionListener(e -> {
            if (versionBox.getSelectedItem() != null) {
                JavaInstall javaInstall = (JavaInstall) versionBox.getSelectedItem();
                javaPath = javaInstall.executable(true).getAbsolutePath();
                text.setText(javaPath);
            }
        });
        versionDropdown.add(versionBox, BorderLayout.CENTER);
        versionDropdown.setVisible(false);
        northPanel.add(versionDropdown, BorderLayout.CENTER);

        // Options Panel
        JPanel options = new JPanel(new BorderLayout(5, 0));
        options.setLayout(new BoxLayout(options, BoxLayout.X_AXIS));
        options.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        selectPanel.add(options);

        // Switch
        ActionListener switchAction = e -> {
            switchableContainer.removeAll();
            if (simplifiedBtn.isSelected()) {
                if (vendorSelected == null){
                    vendorSelected = VendorsEnum.AZUL_ZULU;
                }
                if (targetSelected == null){
                    targetSelected=JavaTargetsEnum.J25;
                }
                switchableContainer.add(targetPanels);
                autoSetup = true;
            } else {
                switchableContainer.add(selectPanel);
                autoSetup = false;
            }
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
        JButton autoDetect = new JButton("Auto-Detect");
        JButton test = new JButton("Test");
        options.add(autoDetect);
        options.add(test);

        listenToTextFieldUpdate(text, t -> javaPath = t.getText());
        addTextBoxEffect(text);

        browse.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Find Java Executable");
            if (!text.getText().isEmpty()) {
                File currentFile = new File(text.getText());
                if (currentFile.getParentFile() != null && currentFile.getParentFile().exists()) {
                    fileChooser.setCurrentDirectory(currentFile.getParentFile());
                }
            }
            FileFilter filter = new FileFilter() {
                @Override
                public boolean accept(File file) {
                    if (file.isDirectory()) {
                        return true;
                    }
                    if (file.isFile()) {
                        return !Platform.current().isWindows() || file.getName().endsWith(".exe");
                    }
                    return false;
                }

                @Override
                public String getDescription() {
                    return Platform.current().isWindows() ? "Java Executable (*.exe)" : "Java Executable";
                }
            };
            fileChooser.setFileFilter(filter);
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                text.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });

        test.addActionListener(e -> {
            String javaPath = text.getText();
            if (javaPath.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please select a Java executable first.", "No Java Selected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            File javaFile = new File(javaPath);
            if (!javaFile.exists()) {
                JOptionPane.showMessageDialog(this, "The selected Java executable does not exist.", "Invalid Java Executable Path", JOptionPane.ERROR_MESSAGE);
                return;
            }
            JDialog testing = new JDialog(this, "Testing Java Executable", true);
            testing.setLocationRelativeTo(this);

            this.testJava();
        });

        autoDetect.addActionListener(e -> {
            String original = autoDetect.getText();
            autoDetect.setText("Detecting");
            autoDetect.setEnabled(false);

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
                    JOptionPane.showMessageDialog(ConfigGUI.this, javaInstalls.size() + " Java 21+ Installs Found!", "Auto-Detection Finished", JOptionPane.INFORMATION_MESSAGE);
                    autoDetect.setEnabled(true);

                    if (!javaInstalls.isEmpty()) {
                        versionModel.removeAllElements();
                        for (JavaInstall install : javaInstalls) {
                            versionModel.addElement(install);
                        }
                        versionDropdown.setVisible(true);
                    }
                }

            }.execute();

        });

        return javaPicker;
    }
    private JPanel initializeArgsPanel() {

        // Main Panel
        JPanel argsPanel = new JPanel(new BorderLayout(0, 0));
        argsPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JLabel title = new JLabel("Add Java Arguments:");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTextField text = new JTextField(100);
        text.setText(javaArgs);
        listenToTextFieldUpdate(text, t -> javaArgs = t.getText());

        addTextBoxEffect(text);

        argsPanel.add(title, BorderLayout.NORTH);
        argsPanel.add(text, BorderLayout.CENTER);

        JPanel argsPickerPanel = new JPanel();
        argsPickerPanel.setLayout(new BoxLayout(argsPickerPanel, BoxLayout.Y_AXIS));
        argsPickerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        // When arg checkbox is active, run SyncTextField
        Runnable syncTextField = () -> {
            boolean hasSelectedOptions = args!=null && !args.isEmpty();
            text.setEditable(!hasSelectedOptions);
            text.setEnabled(!hasSelectedOptions);
            text.setText(javaArgs);
        };

        boolean javaArgsSupplied = javaArgs != null && !javaArgs.isEmpty();
        for(ArgsEnum arg : ArgsEnum.values()) {
            if (arg != ArgsEnum.UnlockExperimentalOptions) {
                JPanel optionsPanel = new JPanel(new BorderLayout());
                optionsPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
                JLabel label = new JLabel("Use "+arg.name()+" Argument "+arg.getStatus());
                JCheckBox checkBox = new JCheckBox();

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

                optionsPanel.add(label, BorderLayout.WEST);
                optionsPanel.add(checkBox, BorderLayout.EAST);
                argsPickerPanel.add(optionsPanel, BorderLayout.CENTER);
            }
        }
        argsPanel.add(argsPickerPanel, BorderLayout.SOUTH);

        return argsPanel;
    }

    private JPanel initializeRelaunchPanel() {
        JPanel configButtonPanel = new JPanel();

        JButton configSaveButton = new JButton("Save Settings");
        configButtonPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        configSaveButton.addActionListener(e -> {
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Please select a Cleanroom version in order to relaunch.", "Cleanroom Release Not Selected", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (javaPath == null) {
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
            Runnable test = this.testJavaAndReturn();
            if (test != null) {
                test.run();
                return;
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
        text.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                text.setBorder(BorderFactory.createLineBorder(new Color(142, 177, 204)));
            }
            @Override
            public void focusLost(FocusEvent e) {
                text.setBorder(null);
            }
        });
    }

    private Runnable testJavaAndReturn() {
        try {
            JavaInstall javaInstall = JavaUtils.parseInstall(javaPath);
            if (javaInstall.version().major() < 21) {
                CleanroomRelauncher.LOGGER.fatal("Java 21+ needed, user specified Java {} instead", javaInstall.version());
                return () -> JOptionPane.showMessageDialog(this, "Java 21 is the minimum version for Cleanroom. Currently, Java " + javaInstall.version().major() + " is selected.", "Old Java Version", JOptionPane.ERROR_MESSAGE);
            }
            CleanroomRelauncher.LOGGER.info("Java {} specified from {}", javaInstall.version().major(), javaPath);
        } catch (IOException e) {
            CleanroomRelauncher.LOGGER.fatal("Failed to execute Java for testing", e);
            return () -> JOptionPane.showMessageDialog(this, "Failed to test Java (more information in console): " + e.getMessage(), "Java Test Failed", JOptionPane.ERROR_MESSAGE);
        }
        return null;
    }

    private void testJava() {
        try {
            JavaInstall javaInstall = JavaUtils.parseInstall(javaPath);
            if (javaInstall.version().major() < 21) {
                CleanroomRelauncher.LOGGER.fatal("Java 21+ needed, user specified Java {} instead", javaInstall.version());
                JOptionPane.showMessageDialog(this, "Java 21 is the minimum version for Cleanroom. Currently, Java " + javaInstall.version().major() + " is selected.", "Old Java Version", JOptionPane.ERROR_MESSAGE);
                return;
            }
            CleanroomRelauncher.LOGGER.info("Java {} specified from {}", javaInstall.version().major(), javaPath);
            JOptionPane.showMessageDialog(this, "Java executable is working correctly!", "Java Test Successful", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            CleanroomRelauncher.LOGGER.fatal("Failed to execute Java for testing", e);
            JOptionPane.showMessageDialog(this, "Failed to test Java (more information in console): " + e.getMessage(), "Java Test Failed", JOptionPane.ERROR_MESSAGE);
        }
    }
}
