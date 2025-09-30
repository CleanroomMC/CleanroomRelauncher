package com.cleanroommc.relauncher.gui;

import com.cleanroommc.javautils.JavaUtils;
import com.cleanroommc.javautils.api.JavaInstall;
import com.cleanroommc.javautils.spi.JavaLocator;
import com.cleanroommc.platformutils.Platform;
import com.cleanroommc.relauncher.CleanroomRelauncher;
import com.cleanroommc.relauncher.download.CleanroomRelease;
import net.minecraftforge.fml.cleanroomrelauncher.ExitVMBypass;

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

public class RelauncherGUI extends JDialog {

    static {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignore) { }
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

    public static RelauncherGUI show(List<CleanroomRelease> eligibleReleases, Consumer<RelauncherGUI> consumer) {
        ImageIcon imageIcon = new ImageIcon(Toolkit.getDefaultToolkit().getImage(RelauncherGUI.class.getResource("/cleanroom-relauncher.png")));
        return new RelauncherGUI(new SupportingFrame(I18n.format("gui.config.supportingFrame.title"), imageIcon), eligibleReleases, consumer);
    }

    public CleanroomRelease selected;
    public String javaPath, javaArgs;

    private JFrame frame;

    private final List<Runnable> updateElements = new ArrayList<>();

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
        int width = rect.width / 3;
        int height = (int) (width);
        int x = (rect.width - width) / 2;
        int y = (rect.height - height) / 2;
        this.setLocation(x, y);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        JLabel cleanroomLogo = new JLabel(new ImageIcon(frame.getIconImage().getScaledInstance(80, 80, Image.SCALE_SMOOTH)));

        JPanel cleanroomPickerPanel = this.initializeCleanroomPicker(eligibleReleases);
        mainPanel.add(cleanroomPickerPanel);

        JPanel javaPickerPanel = this.initializeJavaPicker();
        mainPanel.add(javaPickerPanel);

        JPanel argsPanel = this.initializeArgsPanel();
        mainPanel.add(argsPanel);

        JPanel langPanel = this.initializeLangPicker();
        mainPanel.add(langPanel);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BorderLayout());
        contentPanel.add(cleanroomLogo, BorderLayout.NORTH);
        contentPanel.add(mainPanel, BorderLayout.SOUTH);

        JPanel wrapper = new JPanel();
        wrapper.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        wrapper.add(contentPanel, gbc);

        JPanel relaunchButtonPanel = this.initializeRelaunchPanel();

        this.add(wrapper, BorderLayout.NORTH);
        this.add(relaunchButtonPanel, BorderLayout.SOUTH);
        float scale = rect.width / 1463f;
        scaleComponent(this, scale);

        this.pack();
        this.setSize(width, height);
        this.setVisible(true);
        this.setAutoRequestFocus(true);
    }

    private void updateUI(){
        Iterator<Runnable> iterator = updateElements.iterator();
        while (iterator.hasNext()) {
            try {
                iterator.next().run();
            } catch (Exception e) {
                e.printStackTrace();
                
            }
        }
    }

    private JPanel initializeCleanroomPicker(List<CleanroomRelease> eligibleReleases) {
        // Main Panel
        JPanel cleanroomPicker = new JPanel(new BorderLayout(5, 0));
        cleanroomPicker.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel select = new JPanel();
        select.setLayout(new BoxLayout(select, BoxLayout.Y_AXIS));
        cleanroomPicker.add(select);

        // Title label
        JLabel title = newJLabel("gui.cleanroomPicker.selectedVersion");
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

    private JPanel initializeJavaPicker() {
        // Main Panel
        JPanel javaPicker = new JPanel(new BorderLayout(5, 0));
        javaPicker.setBorder(BorderFactory.createEmptyBorder(20, 10, 0, 10));

        // Select Panel
        JPanel selectPanel = new JPanel(new BorderLayout(5, 5));
        selectPanel.setLayout(new BoxLayout(selectPanel, BoxLayout.Y_AXIS));
        JPanel subSelectPanel = new JPanel(new BorderLayout(5, 5));
        JLabel title = newJLabel("gui.javaPicker.selectJavaExecutable");
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
        javaPicker.add(selectPanel);

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
        // JButton download = new JButton("Download");
        JButton autoDetect = newJButton("gui.javaPicker.autoDetect");
        JButton test = newJButton("gui.javaPicker.testJava");
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
                    return Platform.current().isWindows() ? I18n.format("gui.javaPicker.fileFilter.windows") : I18n.format("gui.javaPicker.fileFilter");
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
                JOptionPane.showMessageDialog(this, I18n.format("gui.notice.javaNotSelected.dialog"), I18n.format("gui.notice.javaNotSelected.title"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            File javaFile = new File(javaPath);
            if (!javaFile.exists()) {
                JOptionPane.showMessageDialog(this, I18n.format("gui.notice.javaInvalid.dialog"), I18n.format("gui.notice.javaInvalid.title"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            JDialog testing = new JDialog(this, I18n.format("gui.javaPicker.testingJava"), true);
            testing.setLocationRelativeTo(this);

            this.testJava();
        });

        autoDetect.addActionListener(e -> {
            String original = autoDetect.getText();
            autoDetect.setText(I18n.format("gui.javaPicker.detectingJava"));
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
                    JOptionPane.showMessageDialog(RelauncherGUI.this, I18n.format("gui.javaPicker.autoDetect.done.dialog", javaInstalls.size()), I18n.format("gui.javaPicker.autoDetect.done.title"), JOptionPane.INFORMATION_MESSAGE);
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

    private JPanel initializeLangPicker() {
        // Main Panel
        JPanel langPicker = new JPanel(new BorderLayout(5, 0));
        langPicker.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel select = new JPanel();
        select.setLayout(new BoxLayout(select, BoxLayout.Y_AXIS));
        langPicker.add(select);

        // Title label
        JLabel title = newJLabel("gui.langPicker.selectedLanguage");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        select.add(title);
        select.add(Box.createRigidArea(new Dimension(0, 5)));

        // Create dropdown panel
        JPanel dropdown = new JPanel(new BorderLayout(5, 5));
        dropdown.setAlignmentX(Component.LEFT_ALIGNMENT);
        select.add(dropdown);

        // Create the dropdown with languages
        JComboBox<String> langBox = new JComboBox<>();
        DefaultComboBoxModel<String> langModel = new DefaultComboBoxModel<>();
        for (String lang : I18n.getLocales()) {
            langModel.addElement(lang);
        }
        langBox.setModel(langModel);
        langBox.setSelectedItem("en_us");
        langBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof String){
                    setText(I18n.format((String)value));
                }
                return this;
            }
        });
        
        langBox.addActionListener(e -> {
            String selectedLanguage = (String) langBox.getSelectedItem();
            if (selectedLanguage != null) {
                I18n.load(selectedLanguage);
                RelauncherGUI.this.updateUI();
            }
        });
        dropdown.add(langBox, BorderLayout.CENTER);

        return langPicker;
    }

    private JPanel initializeArgsPanel() {
        // Main Panel
        JPanel argsPanel = new JPanel(new BorderLayout(0, 0));
        argsPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JLabel title = newJLabel("gui.javaarg.add");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTextField text = new JTextField(100);
        text.setText(javaArgs);
        listenToTextFieldUpdate(text, t -> javaArgs = t.getText());
        addTextBoxEffect(text);

        argsPanel.add(title, BorderLayout.NORTH);
        argsPanel.add(text, BorderLayout.CENTER);

        return argsPanel;
    }

    private JPanel initializeRelaunchPanel() {
        JPanel relaunchButtonPanel = new JPanel();

        JButton relaunchButton = newJButton("gui.relauncher.relaunch");
        relaunchButtonPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        relaunchButton.addActionListener(e -> {
            if (selected == null) {
                JOptionPane.showMessageDialog(this, I18n.format("gui.notice.cleanroomNotSelected.dialog"), I18n.format("gui.notice.cleanroomNotSelected.title"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (javaPath == null) {
                JOptionPane.showMessageDialog(this, I18n.format("gui.notice.javaNotSelected.dialog"), I18n.format("gui.notice.javaNotSelected.title"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            Runnable test = this.testJavaAndReturn();
            if (test != null) {
                test.run();
                return;
            }
            frame.dispose();
        });
        relaunchButtonPanel.add(relaunchButton);

        return relaunchButtonPanel;
    }

    public void updateElement(Runnable runnable) {
        if (runnable != null) {
            updateElements.add(runnable);
        }
    }

    public JButton newJButton(String key) {
        JButton jButton = new JButton(I18n.format(key));
        updateElement(()->jButton.setText(I18n.format(key)));
        return jButton;
    }

    public JLabel newJLabel(String key) {
        JLabel jButton = new JLabel(I18n.format(key));
        updateElement(()->jButton.setText(I18n.format(key)));
        return jButton;
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
                return () -> JOptionPane.showMessageDialog(this, I18n.format("gui.notice.javaTooOld.dialog", javaInstall.version().major()), I18n.format("gui.notice.javaTooOld.title"), JOptionPane.ERROR_MESSAGE);
            }
            CleanroomRelauncher.LOGGER.info("Java {} specified from {}", javaInstall.version().major(), javaPath);
        } catch (IOException e) {
            CleanroomRelauncher.LOGGER.fatal("Failed to execute Java for testing", e);
            return () -> JOptionPane.showMessageDialog(this, I18n.format("gui.notice.javaBad.dialog", e.getMessage()), I18n.format("gui.notice.javaBad.dialog"), JOptionPane.ERROR_MESSAGE);
        }
        return null;
    }

    private void testJava() {
        try {
            JavaInstall javaInstall = JavaUtils.parseInstall(javaPath);
            if (javaInstall.version().major() < 21) {
                CleanroomRelauncher.LOGGER.fatal("Java 21+ needed, user specified Java {} instead", javaInstall.version());
                JOptionPane.showMessageDialog(this, I18n.format("gui.notice.javaTooOld.dialog", javaInstall.version().major()), I18n.format("gui.notice.javaTooOld.title"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            CleanroomRelauncher.LOGGER.info("Java {} specified from {}", javaInstall.version().major(), javaPath);
            JOptionPane.showMessageDialog(this, I18n.format("gui.notice.javaWorks.dialog"), I18n.format("gui.notice.javaWorks.title"), JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            CleanroomRelauncher.LOGGER.fatal("Failed to execute Java for testing", e);
            JOptionPane.showMessageDialog(this, I18n.format("gui.notice.javaBad.dialog", e.getMessage()), I18n.format("gui.notice.javaBad.dialog"), JOptionPane.ERROR_MESSAGE);
        }
    }

}
