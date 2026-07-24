package com.cleanroommc.relauncher.gui;

import com.cleanroommc.relauncher.CleanroomRelauncher;
import com.cleanroommc.relauncher.util.enums.ArgsEnum;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.io.File;

/** Shared visual language for the standalone relauncher windows. */
final class RelauncherUI {

    private static boolean darkTheme;

    static Color BACKGROUND;
    static Color SURFACE;
    static Color CONTROL;
    static Color CONTROL_HOVER;
    static Color TEXT;
    static Color MUTED_TEXT;
    static final Color PRIMARY = new Color(32, 184, 176);
    static Color PRIMARY_HOVER;
    static Color BORDER;
    static Color FOCUS;
    static Color SUCCESS;
    static Color ERROR;
    static Color DISABLED_TEXT;

    static {
        setPalette(loadConfiguredDarkMode());
    }

    private static final String PRIMARY_BUTTON = "relauncher.primaryButton";
    private static final String COMPACT_BUTTON = "relauncher.compactButton";
    private static final String COMBO_ARROW = "relauncher.comboArrow";
    private static final String DARK_RENDERER = "relauncher.darkRenderer";
    private static final String KEEP_OPAQUE = "relauncher.keepOpaque";
    private static final String THEME_ROLE = "relauncher.themeRole";
    private static final String THEME_VALUE = "relauncher.themeValue";
    private static final String THEME_SWITCH = "relauncher.themeSwitch";
    private static final String BACKGROUND_ROLE = "background";
    private static final String SURFACE_ROLE = "surface";
    private static final Font BASE_FONT = new Font("SansSerif", Font.PLAIN, 13);

    private RelauncherUI() { }

    private static boolean loadConfiguredDarkMode() {
        try {
            return CleanroomRelauncher.CONFIG.getDarkMode();
        } catch (LinkageError | RuntimeException ignored) { }
        return true;
    }

    private static void setPalette(boolean dark) {
        darkTheme = dark;
        BACKGROUND = dark ? new Color(11, 17, 24) : new Color(243, 247, 250);
        SURFACE = dark ? new Color(18, 28, 38) : Color.WHITE;
        CONTROL = dark ? new Color(24, 37, 49) : new Color(248, 250, 252);
        CONTROL_HOVER = dark ? new Color(31, 48, 62) : new Color(229, 242, 243);
        TEXT = dark ? new Color(238, 245, 247) : new Color(24, 35, 46);
        MUTED_TEXT = dark ? new Color(145, 164, 178) : new Color(92, 111, 126);
        PRIMARY_HOVER = dark ? new Color(44, 203, 194) : new Color(22, 154, 148);
        BORDER = dark ? new Color(41, 58, 72) : new Color(204, 217, 227);
        FOCUS = dark ? new Color(71, 220, 211) : new Color(22, 143, 138);
        SUCCESS = dark ? new Color(85, 201, 144) : new Color(33, 132, 91);
        ERROR = dark ? new Color(240, 106, 114) : new Color(194, 65, 76);
        DISABLED_TEXT = dark ? new Color(91, 108, 120) : new Color(145, 160, 172);
    }

    static void setDarkTheme(boolean useDarkTheme) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setDarkTheme(useDarkTheme));
            return;
        }
        if (useDarkTheme == darkTheme) {
            return;
        }

        Color[] previousPalette = paletteSnapshot();
        setPalette(useDarkTheme);
        Color[] currentPalette = paletteSnapshot();
        installThemeDefaults();
        CleanroomRelauncher.CONFIG.setDarkMode(useDarkTheme);
        CleanroomRelauncher.CONFIG.save();

        for (Window window : Window.getWindows()) {
            if (!window.isDisplayable()) {
                continue;
            }
            remapComponentColors(window, previousPalette, currentPalette);
            styleTree(window);
            window.invalidate();
            window.validate();
            window.repaint();
        }
    }

    private static Color[] paletteSnapshot() {
        return new Color[]{
                BACKGROUND, SURFACE, CONTROL, CONTROL_HOVER, TEXT, MUTED_TEXT,
                PRIMARY_HOVER, BORDER, FOCUS, SUCCESS, ERROR, DISABLED_TEXT
        };
    }

    private static void remapComponentColors(Component component, Color[] previous, Color[] current) {
        component.setBackground(remapColor(component.getBackground(), previous, current));
        component.setForeground(remapColor(component.getForeground(), previous, current));

        if (component instanceof JComponent) {
            JComponent swingComponent = (JComponent) component;
            Object role = swingComponent.getClientProperty(THEME_ROLE);
            if (BACKGROUND_ROLE.equals(role)) {
                swingComponent.setBackground(BACKGROUND);
                swingComponent.setOpaque(true);
            } else if (SURFACE_ROLE.equals(role)) {
                swingComponent.setBackground(SURFACE);
                swingComponent.setOpaque(true);
                swingComponent.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));
            }
            Object themeValue = swingComponent.getClientProperty(THEME_VALUE);
            if (themeValue instanceof Boolean && swingComponent instanceof AbstractButton) {
                ((AbstractButton) swingComponent).setSelected(themeValue.equals(darkTheme));
            }
        }

        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                remapComponentColors(child, previous, current);
            }
        }
    }

    private static Color remapColor(Color color, Color[] previous, Color[] current) {
        if (color == null) {
            return null;
        }
        for (int i = 0; i < previous.length; i++) {
            if (color.equals(previous[i])) {
                return current[i];
            }
        }
        return color;
    }

    static void showInitiallyInForeground(Window window) {
        window.setAutoRequestFocus(true);
        WindowAdapter foregroundOnce = new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent event) {
                window.toFront();
                window.requestFocus();
                EventQueue.invokeLater(() -> {
                    if (window.isAlwaysOnTop()) {
                        window.setAlwaysOnTop(false);
                    }
                    window.removeWindowListener(this);
                });
            }
        };
        window.addWindowListener(foregroundOnce);
        if (window.isAlwaysOnTopSupported()) {
            window.setAlwaysOnTop(true);
        }
        window.setVisible(true);
    }

    static File chooseJavaExecutable(Dialog owner, String currentPath) {
        FileDialog chooser = new FileDialog(owner, "Find Java Executable", FileDialog.LOAD);
        chooser.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
        chooser.setAlwaysOnTop(false);
        chooser.setMultipleMode(false);
        chooser.setFilenameFilter((directory, name) ->
                !System.getProperty("os.name", "").toLowerCase().contains("win")
                        || name.toLowerCase().endsWith(".exe"));

        if (currentPath != null && !currentPath.trim().isEmpty()) {
            File currentFile = new File(currentPath.trim());
            File parent = currentFile.getParentFile();
            if (parent != null && parent.isDirectory()) {
                chooser.setDirectory(parent.getAbsolutePath());
                chooser.setFile(currentFile.getName());
            }
        } else if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            // Windows' native FileDialog ignores FilenameFilter, but understands wildcards.
            chooser.setFile("*.exe");
        }

        chooser.setLocationRelativeTo(owner);
        chooser.setVisible(true);
        String selectedName = chooser.getFile();
        String selectedDirectory = chooser.getDirectory();
        if (selectedName == null || selectedDirectory == null) {
            return null;
        }
        return new File(selectedDirectory, selectedName);
    }

    static void install() {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { }

        String[] fontKeys = {
                "Button.font", "Label.font", "ComboBox.font", "TextField.font",
                "CheckBox.font", "ToggleButton.font", "Panel.font", "OptionPane.font", "List.font"
        };
        for (String key : fontKeys) {
            UIManager.put(key, BASE_FONT);
        }
        installThemeDefaults();
    }

    private static void installThemeDefaults() {
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Button.background", CONTROL);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("ToggleButton.background", CONTROL);
        UIManager.put("ToggleButton.foreground", TEXT);
        UIManager.put("TextField.background", CONTROL);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", PRIMARY);
        UIManager.put("TextField.selectionBackground", PRIMARY);
        UIManager.put("TextField.selectionForeground", Color.WHITE);
        UIManager.put("TextField.inactiveForeground", DISABLED_TEXT);
        UIManager.put("TextField.inactiveBackground", CONTROL);
        UIManager.put("ComboBox.background", CONTROL);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", PRIMARY);
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);
        UIManager.put("ComboBox.disabledBackground", SURFACE);
        UIManager.put("ComboBox.disabledForeground", DISABLED_TEXT);
        UIManager.put("List.background", CONTROL);
        UIManager.put("List.foreground", TEXT);
        UIManager.put("List.selectionBackground", PRIMARY);
        UIManager.put("List.selectionForeground", Color.WHITE);
        UIManager.put("CheckBox.background", SURFACE);
        UIManager.put("CheckBox.foreground", TEXT);
        UIManager.put("ScrollPane.background", BACKGROUND);
        UIManager.put("Viewport.background", BACKGROUND);
        UIManager.put("ProgressBar.background", CONTROL);
        UIManager.put("ProgressBar.foreground", PRIMARY);
        UIManager.put("ProgressBar.selectionBackground", TEXT);
        UIManager.put("ProgressBar.selectionForeground", TEXT);
        UIManager.put("ToolTip.background", CONTROL_HOVER);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("ToolTip.font", BASE_FONT.deriveFont(12f));
    }

    static void primary(AbstractButton button) {
        button.putClientProperty(PRIMARY_BUTTON, Boolean.TRUE);
        button.setFont(button.getFont().deriveFont(Font.BOLD));
    }

    static void compact(AbstractButton button) {
        button.putClientProperty(COMPACT_BUTTON, Boolean.TRUE);
    }

    static void backgroundPanel(JPanel panel) {
        panel.setBackground(BACKGROUND);
        panel.setOpaque(true);
        panel.putClientProperty(KEEP_OPAQUE, Boolean.TRUE);
        panel.putClientProperty(THEME_ROLE, BACKGROUND_ROLE);
    }

    static JPanel themeToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 8));
        backgroundPanel(toolbar);
        toolbar.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

        ThemeToggle themeToggle = new ThemeToggle();
        themeToggle.addActionListener(event -> setDarkTheme(themeToggle.isSelected()));

        toolbar.add(themeToggle);
        return toolbar;
    }

    static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(BASE_FONT.deriveFont(Font.BOLD, 24f));
        label.setForeground(TEXT);
        return label;
    }

    static JLabel subtitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(BASE_FONT.deriveFont(13f));
        label.setForeground(MUTED_TEXT);
        return label;
    }

    static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(BASE_FONT.deriveFont(Font.BOLD, 12f));
        label.setForeground(MUTED_TEXT);
        return label;
    }

    static JLabel statusLabel(String text) {
        JLabel label = subtitle(text);
        label.setFont(BASE_FONT.deriveFont(12f));
        label.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        return label;
    }

    static void status(JLabel label, String text, Color color) {
        label.setText(text);
        label.setForeground(color);
    }

    static String argumentLabel(ArgsEnum argument) {
        if (argument == ArgsEnum.CompactObjectHeaders) {
            return "Compact object headers";
        }
        if (argument == ArgsEnum.ZGC) {
            return "Z Garbage Collector";
        }
        return argument.name();
    }

    static String argumentDescription(ArgsEnum argument) {
        if (argument == ArgsEnum.CompactObjectHeaders) {
            return "Recommended on Java 24+ to reduce object memory overhead.";
        }
        if (argument == ArgsEnum.ZGC) {
            return "Experimental low-latency collector intended for stronger CPUs.";
        }
        return "";
    }

    static JPanel optionRow(JCheckBox checkBox, String description) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        checkBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(checkBox);
        if (description != null && !description.isEmpty()) {
            JLabel detail = subtitle(description);
            detail.setFont(BASE_FONT.deriveFont(12f));
            detail.setAlignmentX(Component.LEFT_ALIGNMENT);
            detail.setBorder(BorderFactory.createEmptyBorder(2, 24, 0, 0));
            row.add(detail);
        }
        return row;
    }

    static JPanel header(Image image, String title, String subtitle) {
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(4, 4, 18, 4));

        JLabel logo = new JLabel(new ImageIcon(image.getScaledInstance(64, 64, Image.SCALE_SMOOTH)));
        header.add(logo, BorderLayout.WEST);

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel heading = title(title);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel detail = subtitle(subtitle);
        detail.setAlignmentX(Component.LEFT_ALIGNMENT);
        copy.add(Box.createVerticalGlue());
        copy.add(heading);
        copy.add(Box.createRigidArea(new Dimension(0, 4)));
        copy.add(detail);
        copy.add(Box.createVerticalGlue());
        header.add(copy, BorderLayout.CENTER);
        return header;
    }

    static JPanel centeredHeader(Image image, String title, String subtitle) {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(0, 4, 18, 4));
        header.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel logo = new JLabel(new ImageIcon(image.getScaledInstance(56, 56, Image.SCALE_SMOOTH)));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel heading = title(title);
        heading.setAlignmentX(Component.CENTER_ALIGNMENT);
        heading.setHorizontalAlignment(SwingConstants.CENTER);
        heading.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        JLabel detail = subtitle(subtitle);
        detail.setAlignmentX(Component.CENTER_ALIGNMENT);
        detail.setHorizontalAlignment(SwingConstants.CENTER);
        detail.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        header.add(logo);
        header.add(Box.createRigidArea(new Dimension(0, 8)));
        header.add(heading);
        header.add(Box.createRigidArea(new Dimension(0, 4)));
        header.add(detail);
        return header;
    }

    static JPanel card(String title, String description, JComponent content) {
        JPanel card = new SurfacePanel(new BorderLayout(0, 12));
        card.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        card.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel(title);
        heading.setForeground(TEXT);
        heading.setFont(BASE_FONT.deriveFont(Font.BOLD, 15f));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        copy.add(heading);
        if (description != null && !description.isEmpty()) {
            JLabel detail = subtitle(description);
            detail.setAlignmentX(Component.LEFT_ALIGNMENT);
            detail.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
            copy.add(detail);
        }
        card.add(copy, BorderLayout.NORTH);
        content.setOpaque(false);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    static JScrollPane scrollPane(Component content) {
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.getViewport().setBackground(BACKGROUND);
        scrollPane.setBackground(BACKGROUND);
        return scrollPane;
    }

    static JPanel scrollableColumn() {
        return new ScrollableColumn();
    }

    static JPanel footer() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 14));
        footer.setBackground(SURFACE);
        footer.putClientProperty(KEEP_OPAQUE, Boolean.TRUE);
        footer.putClientProperty(THEME_ROLE, SURFACE_ROLE);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));
        return footer;
    }

    static void styleTree(Component component) {
        component.setFont(component.getFont() == null ? BASE_FONT : component.getFont());

        if (component instanceof JCheckBox) {
            JCheckBox checkBox = (JCheckBox) component;
            checkBox.setOpaque(false);
            checkBox.setForeground(TEXT);
            checkBox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            checkBox.setFocusPainted(false);
            checkBox.setIcon(new ModernCheckIcon());
            checkBox.setSelectedIcon(new ModernCheckIcon());
            checkBox.setDisabledIcon(new ModernCheckIcon());
            checkBox.setDisabledSelectedIcon(new ModernCheckIcon());
            checkBox.setMargin(new Insets(3, 0, 3, 0));
        } else if (component instanceof AbstractButton) {
            AbstractButton button = (AbstractButton) component;
            if (Boolean.TRUE.equals(button.getClientProperty(THEME_SWITCH))) {
                if (button instanceof ThemeToggle) {
                    ((ThemeToggle) button).applySize();
                }
                button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                button.setFocusPainted(false);
                button.setRolloverEnabled(true);
                return;
            }
            if (Boolean.TRUE.equals(button.getClientProperty(COMBO_ARROW))) {
                button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                return;
            }
            button.setUI(new ModernButtonUI());
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.setFocusPainted(false);
            button.setRolloverEnabled(true);
            boolean compact = Boolean.TRUE.equals(button.getClientProperty(COMPACT_BUTTON));
            button.setMargin(compact ? new Insets(5, 12, 5, 12) : new Insets(8, 16, 8, 16));
            if (compact) {
                Dimension preferred = button.getPreferredSize();
                button.setPreferredSize(new Dimension(preferred.width, 34));
            }
        } else if (component instanceof JTextField) {
            JTextField text = (JTextField) component;
            text.setForeground(TEXT);
            text.setBackground(CONTROL);
            text.setDisabledTextColor(DISABLED_TEXT);
            text.setCaretColor(PRIMARY);
            text.setMargin(new Insets(7, 10, 7, 10));
            text.setBorder(textFieldBorder(text.hasFocus() ? FOCUS : BORDER));
            text.setPreferredSize(new Dimension(text.getPreferredSize().width, 34));
            text.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        } else if (component instanceof JComboBox) {
            JComboBox<?> comboBox = (JComboBox<?>) component;
            comboBox.setUI(new DarkComboBoxUI());
            comboBox.setOpaque(false);
            comboBox.setBackground(CONTROL);
            comboBox.setForeground(TEXT);
            comboBox.setBorder(new RoundedBorder(BORDER, 8));
            comboBox.setPreferredSize(new Dimension(comboBox.getPreferredSize().width, 36));
            comboBox.setMinimumSize(new Dimension(80, 36));
            comboBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            installDarkRenderer(comboBox);
        } else if (component instanceof JScrollBar) {
            JScrollBar scrollBar = (JScrollBar) component;
            scrollBar.setUI(new DarkScrollBarUI());
            scrollBar.setBackground(BACKGROUND);
            if (scrollBar.getOrientation() == Adjustable.VERTICAL) {
                scrollBar.setPreferredSize(new Dimension(10, scrollBar.getPreferredSize().height));
            }
        } else if (component instanceof JLabel) {
            if (((JLabel) component).getForeground() == null || Color.BLACK.equals(((JLabel) component).getForeground())) {
                component.setForeground(TEXT);
            }
        } else if (component instanceof JPanel && !(component instanceof SurfacePanel)) {
            JPanel panel = (JPanel) component;
            if (!Boolean.TRUE.equals(panel.getClientProperty(KEEP_OPAQUE))) {
                panel.setOpaque(false);
            }
        }

        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                styleTree(child);
            }
        }
    }

    static void installTextFieldFocus(JTextField text) {
        text.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                text.setBorder(textFieldBorder(FOCUS));
            }

            @Override
            public void focusLost(FocusEvent e) {
                text.setBorder(textFieldBorder(BORDER));
            }
        });
    }

    static Dimension dialogSize(Rectangle screenBounds) {
        int width = Math.min(720, Math.max(520, screenBounds.width - 80));
        int height = Math.min(800, Math.max(560, screenBounds.height - 100));
        return new Dimension(width, height);
    }

    private static Border textFieldBorder(Color color) {
        return BorderFactory.createCompoundBorder(
                new RoundedBorder(color, 8),
                BorderFactory.createEmptyBorder(0, 4, 0, 4)
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void installDarkRenderer(JComboBox comboBox) {
        if (Boolean.TRUE.equals(comboBox.getClientProperty(DARK_RENDERER))) {
            return;
        }
        comboBox.setRenderer(new DarkListCellRenderer(comboBox.getRenderer()));
        comboBox.putClientProperty(DARK_RENDERER, Boolean.TRUE);
    }

    private static final class SurfacePanel extends JPanel {
        private SurfacePanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(SURFACE);
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 12, 12));
            g.setColor(BORDER);
            g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 2, getHeight() - 2, 12, 12));
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class ScrollableColumn extends JPanel implements Scrollable {
        private ScrollableColumn() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(680, 720);
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(18, visibleRect.height - 36);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static final class RoundedBorder implements Border {
        private final Color color;
        private final int radius;

        private RoundedBorder(Color color, int radius) {
            this.color = color;
            this.radius = radius;
        }

        @Override
        public Insets getBorderInsets(Component component) {
            return new Insets(1, 1, 1, 1);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g.dispose();
        }
    }

    private static final class ModernCheckIcon implements Icon {
        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            AbstractButton button = (AbstractButton) component;
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(button.isSelected() ? PRIMARY : CONTROL);
            g.fillRoundRect(x, y, 15, 15, 5, 5);
            g.setColor(button.isEnabled() ? (button.isSelected() ? PRIMARY_HOVER : BORDER) : DISABLED_TEXT);
            g.drawRoundRect(x, y, 15, 15, 5, 5);
            if (button.isSelected()) {
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(x + 4, y + 8, x + 7, y + 11);
                g.drawLine(x + 7, y + 11, x + 12, y + 5);
            }
            g.dispose();
        }
    }

    private static final class ThemeToggle extends JToggleButton {
        private static final int SWITCH_WIDTH = 58;
        private static final int SWITCH_HEIGHT = 23;

        private ThemeToggle() {
            putClientProperty(THEME_VALUE, Boolean.TRUE);
            putClientProperty(THEME_SWITCH, Boolean.TRUE);
            setSelected(darkTheme);
            applySize();
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            getAccessibleContext().setAccessibleName("Theme");
            updateDescription();
            addItemListener(event -> {
                updateDescription();
                repaint();
            });
        }

        private void applySize() {
            Dimension size = new Dimension(SWITCH_WIDTH, SWITCH_HEIGHT);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
        }

        private void updateDescription() {
            String description = isSelected() ? "Dark mode. Switch to light mode." : "Light mode. Switch to dark mode.";
            setToolTipText(description);
            getAccessibleContext().setAccessibleDescription(description);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            int padding = 2;
            int thumbHeight = height - padding * 2;
            int thumbWidth = width / 2 - padding;
            boolean dark = isSelected();
            int thumbX = dark ? padding : width - padding - thumbWidth;

            Color track = dark ? new Color(133, 137, 140) : new Color(103, 105, 107);
            if (getModel().isRollover()) {
                track = dark ? new Color(148, 152, 155) : new Color(116, 119, 121);
            }
            g.setColor(track);
            g.fillRoundRect(0, 0, width - 1, height - 1, height, height);

            g.setComposite(AlphaComposite.SrcOver.derive(0.22f));
            g.setColor(Color.BLACK);
            g.fillRoundRect(thumbX + 1, padding + 1, thumbWidth, thumbHeight, thumbHeight, thumbHeight);
            g.setComposite(AlphaComposite.SrcOver);

            g.setColor(dark ? new Color(29, 29, 36) : new Color(250, 250, 250));
            g.fillRoundRect(thumbX, padding, thumbWidth, thumbHeight, thumbHeight, thumbHeight);

            int iconY = height / 2;
            int leftIconX = padding + thumbWidth / 2;
            int rightIconX = width - padding - thumbWidth / 2;
            paintMoon(g, leftIconX, iconY, dark);
            paintSun(g, rightIconX, iconY, !dark);

            Color outline = hasFocus() || getModel().isRollover()
                    ? FOCUS
                    : dark ? new Color(151, 155, 158) : new Color(83, 87, 90);
            g.setColor(outline);
            g.setStroke(new BasicStroke(hasFocus() ? 1.5f : 1f));
            g.drawRoundRect(1, 1, width - 3, height - 3, height - 2, height - 2);
            g.dispose();
        }

        private void paintMoon(Graphics2D g, int centerX, int centerY, boolean active) {
            double radius = 5.5;
            Area moon = new Area(new Ellipse2D.Double(
                    centerX - radius, centerY - radius, radius * 2, radius * 2));
            moon.subtract(new Area(new Ellipse2D.Double(
                    centerX - radius * 0.42, centerY - radius * 1.08, radius * 1.72, radius * 1.72)));
            g.setColor(active ? new Color(153, 156, 159) : Color.WHITE);
            g.fill(moon);
        }

        private void paintSun(Graphics2D g, int centerX, int centerY, boolean active) {
            Color sun = active ? new Color(102, 104, 106) : new Color(27, 28, 33);
            g.setColor(sun);
            if (active) {
                g.fillOval(centerX - 4, centerY - 4, 8, 8);
            } else {
                g.setStroke(new BasicStroke(1f));
                g.drawOval(centerX - 4, centerY - 4, 8, 8);
            }

            int rayDistance = 7;
            int raySize = 2;
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI * i / 4;
                int x = (int) Math.round(centerX + Math.cos(angle) * rayDistance) - raySize / 2;
                int y = (int) Math.round(centerY + Math.sin(angle) * rayDistance) - raySize / 2;
                g.fillOval(x, y, raySize, raySize);
            }
        }
    }

    private static final class ChevronIcon implements Icon {
        @Override
        public int getIconWidth() {
            return 12;
        }

        @Override
        public int getIconHeight() {
            return 8;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(MUTED_TEXT);
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(x + 2, y + 2, x + 6, y + 6);
            g.drawLine(x + 6, y + 6, x + 10, y + 2);
            g.dispose();
        }
    }

    private static final class DarkComboBoxUI extends BasicComboBoxUI {
        @Override
        protected JButton createArrowButton() {
            JButton button = new JButton(new ChevronIcon());
            button.putClientProperty(COMBO_ARROW, Boolean.TRUE);
            button.setOpaque(false);
            button.setContentAreaFilled(false);
            button.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 10));
            button.setFocusPainted(false);
            return button;
        }

        @Override
        public void paintCurrentValueBackground(Graphics graphics, Rectangle bounds, boolean hasFocus) {
            graphics.setColor(CONTROL);
            graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        @Override
        public void paint(Graphics graphics, JComponent component) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(CONTROL);
            g.fillRoundRect(0, 0, component.getWidth(), component.getHeight(), 8, 8);
            g.dispose();
            paintCurrentValue(graphics, rectangleForCurrentValue(), comboBox.hasFocus());
        }

        @Override
        public void paintCurrentValue(Graphics graphics, Rectangle bounds, boolean hasFocus) {
            ListCellRenderer<Object> renderer = comboBox.getRenderer();
            Component rendered = renderer.getListCellRendererComponent(listBox, comboBox.getSelectedItem(),
                    -1, false, false);
            rendered.setFont(comboBox.getFont());
            rendered.setBackground(CONTROL);
            rendered.setForeground(comboBox.isEnabled() ? TEXT : DISABLED_TEXT);
            if (rendered instanceof JComponent) {
                ((JComponent) rendered).setOpaque(false);
                ((JComponent) rendered).setBorder(BorderFactory.createEmptyBorder(0, 9, 0, 4));
            }
            currentValuePane.paintComponent(graphics, rendered, comboBox,
                    bounds.x, bounds.y, bounds.width, bounds.height, true);
        }

        @Override
        protected ComboPopup createPopup() {
            return new BasicComboPopup(comboBox) {
                @Override
                protected JScrollPane createScroller() {
                    JScrollPane scroller = super.createScroller();
                    scroller.setBorder(new RoundedBorder(BORDER, 8));
                    scroller.getViewport().setBackground(CONTROL);
                    JScrollBar bar = scroller.getVerticalScrollBar();
                    bar.setUI(new DarkScrollBarUI());
                    bar.setPreferredSize(new Dimension(10, bar.getPreferredSize().height));
                    return scroller;
                }
            };
        }
    }

    private static final class DarkListCellRenderer implements ListCellRenderer<Object> {
        private final ListCellRenderer<Object> delegate;

        @SuppressWarnings("unchecked")
        private DarkListCellRenderer(ListCellRenderer<?> delegate) {
            this.delegate = (ListCellRenderer<Object>) delegate;
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                       boolean isSelected, boolean cellHasFocus) {
            Component rendered = delegate.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            boolean popupSelection = index >= 0 && isSelected;
            rendered.setBackground(popupSelection ? PRIMARY : CONTROL);
            rendered.setForeground(popupSelection ? Color.WHITE : TEXT);
            if (rendered instanceof JComponent) {
                ((JComponent) rendered).setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
                ((JComponent) rendered).setOpaque(true);
            }
            return rendered;
        }
    }

    private static final class DarkScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            trackColor = BACKGROUND;
            thumbColor = BORDER;
            thumbHighlightColor = CONTROL_HOVER;
            thumbDarkShadowColor = BORDER;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
            if (!component.isEnabled() || bounds.width <= 0 || bounds.height <= 0) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(isDragging ? PRIMARY : BORDER);
            g.fillRoundRect(bounds.x + 2, bounds.y + 2, Math.max(4, bounds.width - 4),
                    Math.max(4, bounds.height - 4), 8, 8);
            g.dispose();
        }

        private JButton zeroButton() {
            JButton button = new JButton();
            button.putClientProperty(COMBO_ARROW, Boolean.TRUE);
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            return button;
        }
    }

    private static final class ModernButtonUI extends BasicButtonUI {
        @Override
        public void installUI(JComponent component) {
            super.installUI(component);
            AbstractButton button = (AbstractButton) component;
            button.setOpaque(false);
            button.setContentAreaFilled(false);
            button.setBorderPainted(false);
        }

        @Override
        public void paint(Graphics graphics, JComponent component) {
            AbstractButton button = (AbstractButton) component;
            ButtonModel model = button.getModel();
            boolean primary = Boolean.TRUE.equals(button.getClientProperty(PRIMARY_BUTTON)) ||
                    (button instanceof JToggleButton && model.isSelected());

            Color fill;
            Color outline;
            Color foreground;
            if (!button.isEnabled()) {
                fill = SURFACE;
                outline = BORDER;
                foreground = DISABLED_TEXT;
            } else if (primary) {
                fill = model.isPressed() || model.isRollover() ? PRIMARY_HOVER : PRIMARY;
                outline = fill;
                foreground = Color.WHITE;
            } else {
                fill = model.isPressed() || model.isRollover() ? CONTROL_HOVER : CONTROL;
                outline = model.isRollover() ? FOCUS : BORDER;
                foreground = TEXT;
            }

            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);
            g.fillRoundRect(0, 0, component.getWidth() - 1, component.getHeight() - 1, 10, 10);
            g.setColor(outline);
            g.drawRoundRect(0, 0, component.getWidth() - 1, component.getHeight() - 1, 10, 10);
            if (button.hasFocus()) {
                g.setColor(FOCUS);
                g.drawRoundRect(2, 2, component.getWidth() - 5, component.getHeight() - 5, 8, 8);
            }
            g.dispose();

            button.setForeground(foreground);
            super.paint(graphics, component);
        }
    }
}
