package com.cleanroommc.relauncher.gui;

import com.cleanroommc.javautils.JavaUtils;
import com.cleanroommc.javautils.api.JavaDistro;
import com.cleanroommc.javautils.api.JavaInstall;
import com.cleanroommc.javautils.api.JavaVersion;
import com.cleanroommc.javautils.spi.JavaLocator;
import com.cleanroommc.platformutils.Platform;
import com.cleanroommc.relauncher.CleanroomRelauncher;
import com.cleanroommc.relauncher.config.RelauncherConfiguration;
import com.cleanroommc.relauncher.download.CleanroomRelease;
import com.cleanroommc.relauncher.util.enums.ArgsEnum;
import net.minecraftforge.fml.cleanroomrelauncher.ExitVMBypass;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
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

import static com.cleanroommc.relauncher.CleanroomRelauncher.*;

public class RelauncherGUI extends JDialog {

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

    public static RelauncherGUI show(List<CleanroomRelease> eligibleReleases, Consumer<RelauncherGUI> consumer) {
        ImageIcon imageIcon = new ImageIcon(Toolkit.getDefaultToolkit().getImage(RelauncherGUI.class.getResource("/cleanroom-relauncher.png")));
        return new RelauncherGUI(new SupportingFrame("Cleanroom Relaunch Configuration", imageIcon), eligibleReleases, consumer);
    }

    public CleanroomRelease selected;
    public JavaVersion targetSelected = JavaVersion.parseOrThrow(25);
    public JavaDistro vendorSelected = JavaDistro.ZULU;
    public String javaPath, javaArgs;
    public Boolean updateNotification;
    public boolean autoSetup;
    public boolean shouldScale;
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
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private JButton startDefaultButton;
    private JButton advancedDefaultButton;

    private void showAdvancedScreen() {
        cardLayout.show(cards, "ADVANCED");
        this.getRootPane().setDefaultButton(advancedDefaultButton);
    }

    private void showStartScreen() {
        cardLayout.show(cards, "START");
        this.getRootPane().setDefaultButton(startDefaultButton);
    }

    private JPanel createStartScreen() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(RelauncherUI.BACKGROUND);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(32, 48, 32, 48));

        JLabel logo = new JLabel(new ImageIcon(frame.getIconImage().getScaledInstance(128, 128, Image.SCALE_SMOOTH)));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        logo.setBorder(new EmptyBorder(0, 0, 24, 0));

        JLabel title = RelauncherUI.title("Ready to launch Cleanroom?");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel subtitle = RelauncherUI.subtitle("We'll choose a compatible Java version and handle the setup for you.");
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(6, 0, 28, 0));

        JButton fastRelaunchBtn = new JButton("Relaunch Now");
        RelauncherUI.primary(fastRelaunchBtn);
        fastRelaunchBtn.addActionListener(e -> {
            autoSetup = true;
            frame.dispose();
        });
        JButton advancedBtn = new JButton("Advanced Settings");
        fastRelaunchBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        advancedBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        Dimension actionSize = new Dimension(240, 40);
        fastRelaunchBtn.setMaximumSize(actionSize);
        advancedBtn.setMaximumSize(actionSize);
        startDefaultButton = fastRelaunchBtn;
        this.getRootPane().setDefaultButton(fastRelaunchBtn);

        advancedBtn.addActionListener(e -> showAdvancedScreen());

        content.add(logo);
        content.add(title);
        content.add(subtitle);
        content.add(fastRelaunchBtn);
        content.add(Box.createRigidArea(new Dimension(0, 10)));
        content.add(advancedBtn);
        panel.add(content);

        return panel;
    }
    private JPanel createUpdateScreen(List<CleanroomRelease> releases) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(RelauncherUI.BACKGROUND);
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(32, 44, 32, 44));

        JLabel logo = new JLabel(new ImageIcon(frame.getIconImage().getScaledInstance(112, 112, Image.SCALE_SMOOTH)));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        logo.setBorder(new EmptyBorder(0, 0, 22, 0));

        JLabel title = RelauncherUI.title("Cleanroom " + releases.get(0).name + " is available");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = RelauncherUI.subtitle("Update now, keep your current version, or review the setup first.");
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(6, 0, 28, 0));

        JButton fastUpdateBtn = new JButton("Update Now");
        RelauncherUI.primary(fastUpdateBtn);
        fastUpdateBtn.addActionListener(e -> {
            autoSetup = true;
            selected = null;
            frame.dispose();
        });

        JButton skipBtn = new JButton("Keep Current Version");
        skipBtn.addActionListener(e -> {
            autoSetup = true;
            frame.dispose();
        });

        JButton advancedBtn = new JButton("Advanced Settings");
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
        lowerBtnBox.setBorder(new EmptyBorder(10, 0, 0, 0));
        lowerBtnBox.add(advancedBtn);

        content.add(logo);
        content.add(title);
        content.add(subtitle);
        content.add(upperBtnBox);
        content.add(lowerBtnBox);
        panel.add(content);
        return panel;
    }

    private JPanel createAdvancedScreen(List<CleanroomRelease> releases) {
        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(RelauncherUI.BACKGROUND);

        JPanel mainContent = RelauncherUI.scrollableColumn();
        mainContent.setBorder(new EmptyBorder(24, 28, 28, 28));

        mainContent.add(RelauncherUI.centeredHeader(frame.getIconImage(), "Advanced Settings",
                "Choose exactly how Cleanroom should relaunch."));
        mainContent.add(RelauncherUI.card("Cleanroom version", "Select the release to use.",
                this.initializeCleanroomPicker(releases)));
        mainContent.add(Box.createRigidArea(new Dimension(0, 14)));
        mainContent.add(RelauncherUI.card("Java runtime", "Automatic setup is recommended for most players.",
                this.initializeJavaPicker()));
        mainContent.add(Box.createRigidArea(new Dimension(0, 14)));
        mainContent.add(RelauncherUI.card("Java arguments", "Optional performance and compatibility flags.",
                this.initializeArgsPanel()));
        JPanel relaunchPanel = this.initializeRelaunchPanel();

        container.add(RelauncherUI.scrollPane(mainContent), BorderLayout.CENTER);
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
                selected = null;
                frame.dispose();

                CleanroomRelauncher.LOGGER.info("No Cleanroom releases were selected, instance is dismissed.");
                ExitVMBypass.exit(0);
            }
        });
        this.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        this.setAlwaysOnTop(true);
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice screen = env.getDefaultScreenDevice();
        Rectangle rect = screen.getDefaultConfiguration().getBounds();
        Dimension dialogSize = RelauncherUI.dialogSize(rect);


        JPanel startCard = Boolean.TRUE.equals(updateNotification) ? createUpdateScreen(eligibleReleases) : createStartScreen();
        JPanel advancedCard = createAdvancedScreen(eligibleReleases);

        cards.add(startCard, "START");
        cards.add(advancedCard, "ADVANCED");


        this.add(cards);
        float scale = Math.max(0.9f, Math.min(1.25f, rect.width / 1920f));
        if (shouldScale) {
            scale = Math.max(0.9f, scale / 1.15f);
        }
        scaleComponent(this, scale);
        RelauncherUI.styleTree(this);

        this.pack();
        this.setSize(dialogSize);
        this.setMinimumSize(new Dimension(520, 560));
        this.setLocationRelativeTo(null);
        this.setVisible(true);
        this.setAutoRequestFocus(true);
    }

    private JPanel initializeCleanroomPicker(List<CleanroomRelease> eligibleReleases) {
        // Main Panel
        JPanel cleanroomPicker = new JPanel(new BorderLayout(5, 0));
        cleanroomPicker.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel select = new JPanel();
        select.setLayout(new BoxLayout(select, BoxLayout.Y_AXIS));
        cleanroomPicker.add(select);

        // Title label
        JLabel title = new JLabel("Version");
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
        javaPicker.setBorder(BorderFactory.createEmptyBorder(20, 10, 0, 10));

        JToggleButton simplifiedBtn = new JToggleButton("Automatic Setup", true);
        JToggleButton manualBtn = new JToggleButton("Choose Java Manually");

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
        JLabel title = new JLabel("Java executable");
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
                    targetSelected= JavaVersion.parseOrThrow(25);
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
        // JButton download = new JButton("Download");
        JButton autoDetect = new JButton("Find Installed Java");
        JButton test = new JButton("Test Java");
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
                    autoDetect.setEnabled(true);

                    try {
                        get();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        JOptionPane.showMessageDialog(RelauncherGUI.this, "Java detection was interrupted.", "Detection Interrupted", JOptionPane.WARNING_MESSAGE);
                        return;
                    } catch (ExecutionException failure) {
                        CleanroomRelauncher.LOGGER.error("Failed to detect installed Java runtimes", failure.getCause());
                        JOptionPane.showMessageDialog(RelauncherGUI.this, "Installed Java runtimes could not be detected. You can still browse for one manually.", "Detection Failed", JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    if (!javaInstalls.isEmpty()) {
                        versionModel.removeAllElements();
                        for (JavaInstall install : javaInstalls) {
                            versionModel.addElement(install);
                        }
                        versionDropdown.setVisible(true);
                        versionBox.setSelectedIndex(0);
                        JOptionPane.showMessageDialog(RelauncherGUI.this, javaInstalls.size() + " compatible Java install" + (javaInstalls.size() == 1 ? " was" : "s were") + " found.", "Java Detection Complete", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(RelauncherGUI.this, "No Java 21+ installations were found. You can browse for one manually.", "No Compatible Java Found", JOptionPane.WARNING_MESSAGE);
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

        JLabel title = new JLabel("Custom arguments");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTextField text = new JTextField(40);
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
            boolean hasSelectedOptions = !args.isEmpty();
            text.setEditable(!hasSelectedOptions);
            text.setEnabled(!hasSelectedOptions);
            text.setText(javaArgs);
        };

        boolean javaArgsSupplied = javaArgs != null && !javaArgs.isEmpty();
        for(ArgsEnum arg : ArgsEnum.values()) {
            if (arg != ArgsEnum.UnlockExperimentalOptions) {
                JPanel optionsPanel = new JPanel(new BorderLayout());
                optionsPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
                JCheckBox checkBox = new JCheckBox(RelauncherUI.argumentLabel(arg));

                boolean isPresentInArgs = RelauncherConfiguration.read().argsContain(arg);
                checkBox.setSelected(isPresentInArgs || arg.isSelectedByDefault());
                if (javaArgsSupplied){
                    checkBox.setSelected(isPresentInArgs);
                    if (isPresentInArgs){
                        args.add(arg);
                    }
                    syncTextField.run();
                } else {
                    if(arg.isSelectedByDefault()) {
                        args.add(arg);
                        updateJavaArgs();
                        syncTextField.run();
                    }
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

                optionsPanel.add(checkBox, BorderLayout.WEST);
                argsPickerPanel.add(optionsPanel, BorderLayout.CENTER);
            }
        }
        argsPanel.add(argsPickerPanel, BorderLayout.SOUTH);

        return argsPanel;
    }
    private JPanel initializeRelaunchPanel() {
        JPanel relaunchButtonPanel = RelauncherUI.footer();

        JButton backButton = new JButton("Back");
        backButton.addActionListener(e -> showStartScreen());

        JButton relaunchButton = new JButton("Relaunch with Cleanroom");
        RelauncherUI.primary(relaunchButton);
        advancedDefaultButton = relaunchButton;
        relaunchButton.addActionListener(e -> {
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Please select a Cleanroom version in order to relaunch.", "Cleanroom Release Not Selected", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (autoSetup) {
                if (vendorSelected==null) {
                    vendorSelected = JavaDistro.ZULU;
                }
                if (targetSelected==null) {
                    targetSelected = JavaVersion.parseOrThrow(25);
                }
            }
            if (!autoSetup) {
                if (javaPath == null || javaPath.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Please provide a valid Java Executable in order to relaunch.", "Java Executable Not Selected", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                Runnable test = this.testJavaAndReturn();
                if (test != null) {
                    test.run();
                    return;
                }
                // Force verifying path against no target
                vendorSelected = null;
                targetSelected = null;
            }
            if (autoSetup){
                updateJavaArgs();
            }else{
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
                return () -> JOptionPane.showMessageDialog(this, "Java 21 is the minimum version for Cleanroom. Currently, Java " + javaInstall.version().major() + " is selected.", "Old Java Version", JOptionPane.ERROR_MESSAGE);
            }
            CleanroomRelauncher.LOGGER.info("Java {} specified from {}", javaInstall.version().major(), javaPath);
        } catch (IOException | RuntimeException e) {
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
        } catch (IOException | RuntimeException e) {
            CleanroomRelauncher.LOGGER.fatal("Failed to execute Java for testing", e);
            JOptionPane.showMessageDialog(this, "Failed to test Java (more information in console): " + e.getMessage(), "Java Test Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

}
