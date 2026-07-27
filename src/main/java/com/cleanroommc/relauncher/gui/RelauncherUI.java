package com.cleanroommc.relauncher.gui;

import com.cleanroommc.platformutils.Platform;
import com.cleanroommc.relauncher.CleanroomRelauncher;
import com.cleanroommc.relauncher.util.enums.ArgsEnum;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.TextAttribute;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Shared visual language for our windows. */
final class RelauncherUI {

    private static boolean darkTheme;
    private static float uiScale = 1f;

    static final Color PRIMARY = new Color(32, 184, 176);

    /**
     * Widest a settings card is allowed to get. BoxLayout centres anything narrower than the
     * column, so this keeps a maximized window from stretching cards edge to edge.
     */
    private static final int MAX_CONTENT_WIDTH = 720;

    static Color BACKGROUND, SURFACE, CONTROL, CONTROL_HOVER, TEXT, MUTED_TEXT, PRIMARY_HOVER, BORDER, FOCUS,
            SUCCESS, ERROR, DISABLED_TEXT;

    static {
        setPalette(loadConfiguredDarkMode());
    }

    private static final boolean WINDOWS = Platform.current().isWindows();
    private static final String PRIMARY_BUTTON = "relauncher.primaryButton";
    private static final String COMPACT_BUTTON = "relauncher.compactButton";
    private static final String GHOST_BUTTON = "relauncher.ghostButton";
    private static final String SEGMENTED = "relauncher.segmented";
    private static final String COMBO_ARROW = "relauncher.comboArrow";
    private static final String DARK_RENDERER = "relauncher.darkRenderer";
    private static final String KEEP_OPAQUE = "relauncher.keepOpaque";
    private static final String THEME_ROLE = "relauncher.themeRole";
    private static final String THEME_VALUE = "relauncher.themeValue";
    private static final String THEME_SWITCH = "relauncher.themeSwitch";
    private static final String BACKGROUND_ROLE = "background";
    private static final String SURFACE_ROLE = "surface";
    private static final String BADGE_ROLE = "badge";
    private static final String UI_FAMILY = resolveUiFamily();
    private static final Font BASE_FONT = uiFont(13f, TextAttribute.WEIGHT_REGULAR);

    private RelauncherUI() { }

    private static String resolveUiFamily() {
        // Preference order, not an assumption about what is installed
        String[] candidates = {
                "Segoe UI", // Windows
                "SF Pro Text", "Helvetica Neue", "Lucida Grande", // Mac
                "Inter", "Ubuntu", "Noto Sans", "DejaVu Sans", "Liberation Sans", "Cantarell", // Linux
                "Lucida Sans" // Java (Oracle)
        };
        for (String candidate : candidates) {
            Font probe = new Font(candidate, Font.PLAIN, 12);
            if (isFontFamily(probe, candidate) && paintsGlyphs(probe)) {
                return probe.getFamily();
            }
        }
        // Last ditch
        // SANS_SERIF is a logical family, every platform maps it onto something it considers its default
        return Font.SANS_SERIF;
    }

    private static boolean isFontFamily(Font font, String expected) {
        String family = font.getFamily();
        return family != null && family.equalsIgnoreCase(expected);
    }

    /**
     * A family can resolve, report metrics and claim every character while rasterizing nothing.
     * Draw with the face and look for ink.
     */
    private static boolean paintsGlyphs(Font font) {
        int size = 24;
        try {
            BufferedImage canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            try {
                g.setFont(font.deriveFont(Font.PLAIN, 16f));
                g.setColor(Color.BLACK);
                g.drawString("AB", 2, size - 6);
            } finally {
                g.dispose();
            }
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    if ((canvas.getRGB(x, y) >>> 24) != 0) {
                        return true;
                    }
                }
            }
            return false;
        } catch (LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    /**
     * UI type with explicit weight. Prefer {@link TextAttribute#WEIGHT_SEMIBOLD} over full {@link Font#BOLD}.
     * Segoe Bold paints heavy and muddy at dialog sizes.
     */
    private static Font uiFont(float size, Float weight) {
        // Whole-pixel sizes avoid soft/fractional glyph rasterization under Windows DPI
        float pixelSize = Math.max(11f, Math.round(size));
        Map<TextAttribute, Object> attributes = new HashMap<>();
        attributes.put(TextAttribute.FAMILY, UI_FAMILY);
        attributes.put(TextAttribute.SIZE, pixelSize);
        attributes.put(TextAttribute.WEIGHT, weight);
        attributes.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
        return new Font(attributes);
    }

    private static Font uiRegular(float size) {
        return uiFont(size, TextAttribute.WEIGHT_REGULAR);
    }

    private static Font uiMedium(float size) {
        // Slightly stronger than regular for hierarchy without full bold fatness
        return uiFont(size, TextAttribute.WEIGHT_MEDIUM);
    }

    private static Font uiSemibold(float size) {
        return uiFont(size, TextAttribute.WEIGHT_SEMIBOLD);
    }

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
        return new Color[] {
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

    /**
     * Shows a frame on the EDT and blocks until it is disposed.
     * Same pattern as a modal dialog, but the window is a real {@link Frame} so taskbar icons and
     * thumbnails work (unlike an empty owner frame), as it was previously done...
     */
    static void showAndWait(final Window window) {
        if (!SwingUtilities.isEventDispatchThread()) {
            try {
                SwingUtilities.invokeAndWait(() -> showAndWait(window));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                throw new RuntimeException(cause);
            }
            return;
        }

        final SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                loop.exit();
            }
        });
        showInitiallyInForeground(window);
        if (window.isDisplayable()) {
            loop.enter();
        }
    }

    static File chooseJavaExecutable(Frame owner, String currentPath) {
        FileDialog chooser = new FileDialog(owner, "Find Java Executable", FileDialog.LOAD);
        chooser.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
        chooser.setAlwaysOnTop(false);
        chooser.setMultipleMode(false);
        chooser.setFilenameFilter((directory, name) -> !WINDOWS || name.toLowerCase().endsWith(".exe"));

        if (currentPath != null && !currentPath.trim().isEmpty()) {
            File currentFile = new File(currentPath.trim());
            File parent = currentFile.getParentFile();
            if (parent != null && parent.isDirectory()) {
                chooser.setDirectory(parent.getAbsolutePath());
                chooser.setFile(currentFile.getName());
            }
        } else if (WINDOWS) {
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
        // LCD/ClearType on Windows
        System.setProperty("awt.useSystemAAFontSettings", "lcd");
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
        UIManager.put("ToolTip.font", uiRegular(12f));
    }

    static void primary(AbstractButton button) {
        button.putClientProperty(PRIMARY_BUTTON, Boolean.TRUE);
        button.putClientProperty(GHOST_BUTTON, null);
        button.setFont(uiMedium(button.getFont().getSize2D()));
    }

    static void compact(AbstractButton button) {
        button.putClientProperty(COMPACT_BUTTON, Boolean.TRUE);
    }

    /** Secondary outline-style action that stays quieter than the primary CTA. */
    static void ghost(AbstractButton button) {
        button.putClientProperty(GHOST_BUTTON, Boolean.TRUE);
        button.putClientProperty(PRIMARY_BUTTON, null);
    }

    static void sizeActionButton(AbstractButton button, int width, int height) {
        Dimension size = new Dimension(width, height);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
    }

    static void showError(Component parent, String title, String message) {
        showMessage(parent, title, message, true);
    }

    static void showInfo(Component parent, String title, String message) {
        showMessage(parent, title, message, false);
    }

    private static void showMessage(Component parent, String title, String message, boolean error) {
        // Panel.background has to be SURFACE for the duration of the dialog only
        // Leaving it set would hand the wrong background to every panel created afterwards
        Object previousPanelBackground = UIManager.get("Panel.background");
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("Panel.background", SURFACE);
        UIManager.put("OptionPane.messageForeground", TEXT);
        try {
            JOptionPane.showMessageDialog(parent, wrapMessage(message), title,
                    error ? JOptionPane.ERROR_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
        } finally {
            UIManager.put("Panel.background", previousPanelBackground);
        }
    }

    /** Long single-line messages (stack trace text, paths) otherwise stretch the dialog off-screen. */
    private static String wrapMessage(String message) {
        if (message == null || message.length() < 90) {
            return message;
        }
        return "<html><body style='width: 380px'>" + escapeHtml(message) + "</body></html>";
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Binds Escape on the root pane. Prefer this over raw KeyListeners so
     * focused text fields still pass Escape through when empty focus is fine.
     */
    static void onEscape(JRootPane rootPane, Runnable action) {
        String key = "relauncher.escape";
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), key);
        rootPane.getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    /**
     * Records the display scale so fixed control sizes grow with the fonts.
     * Call before building a window: {@link #styleTree} and the control factories read it while
     * laying out, and scaled-up fonts inside fixed-height fields clip otherwise.
     */
    static void setUiScale(float scale) {
        uiScale = scale;
    }

    /** Computes the display scale used for a window on the given screen. */
    static float uiScaleFor(Rectangle screenBounds) {
        return Math.max(0.9f, Math.min(1.25f, screenBounds.width / 1920f));
    }

    /** Scales a fixed control dimension by the active UI scale. */
    static int scaled(int base) {
        return Math.round(base * uiScale);
    }

    static void scaleComponent(Component component, float scale) {
        if (component instanceof JTextField || component instanceof AbstractButton || component instanceof JComboBox) {
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

        if (component instanceof JLabel || component instanceof AbstractButton || component instanceof JTextField ||
                component instanceof JComboBox) {
            Font font = component.getFont();
            if (font != null) {
                // Round to whole pixels so scaled text stays crisp
                float scaled = Math.max(11f, Math.round(font.getSize2D() * scale));
                component.setFont(font.deriveFont(scaled));
            }
        }

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
        // Semibold + size for hierarchy
        label.setFont(uiSemibold(22f));
        label.setForeground(TEXT);
        return label;
    }

    static JLabel subtitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(uiRegular(13f));
        label.setForeground(MUTED_TEXT);
        return label;
    }

    static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        // Regular + muted color since bold labels looked too thick
        label.setFont(uiRegular(12f));
        label.setForeground(MUTED_TEXT);
        return label;
    }

    /** Field label with spacing so it never sits flush against the control below. */
    static JLabel fieldLabelAbove(String text) {
        JLabel label = fieldLabel(text);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        return label;
    }

    static JLabel statusLabel(String text) {
        JLabel label = subtitle(text);
        label.setFont(uiRegular(12f));
        label.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        return label;
    }

    static void status(JLabel label, String text, Color color) {
        label.setText(text);
        label.setForeground(color);
    }

    static String argumentLabel(ArgsEnum argument) {
        return argument.getTitle();
    }

    static String argumentDescription(ArgsEnum argument) {
        return argument.getDescription();
    }

    /**
     * Everything in {@code arguments} that is not one of the checkbox-managed flags.
     * Allows handwritten flags to survive a checkbox toggle instead of being wiped by the regenerated argument string.
     */
    static String unmanagedArguments(String arguments) {
        if (arguments == null || arguments.trim().isEmpty()) {
            return "";
        }
        StringBuilder kept = new StringBuilder();
        for (String token : arguments.trim().split("\\s+")) {
            boolean managed = false;
            for (ArgsEnum argument : ArgsEnum.values()) {
                if (argument.getArg().equals(token)) {
                    managed = true;
                    break;
                }
            }
            if (!managed) {
                if (kept.length() > 0) {
                    kept.append(' ');
                }
                kept.append(token);
            }
        }
        return kept.toString();
    }

    static String argumentTooltip(ArgsEnum argument) {
        String description = argumentDescription(argument);
        if (description.isEmpty()) {
            return argument.getArg();
        }
        return argument.getArg() + ": " + description;
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
            detail.setFont(uiRegular(12f));
            detail.setAlignmentX(Component.LEFT_ALIGNMENT);
            detail.setBorder(BorderFactory.createEmptyBorder(2, 24, 0, 0));
            row.add(detail);
        }
        return row;
    }

    /** Soft pill badge used for recommended labels and status chips. */
    static JLabel badge(String text, boolean emphasize) {
        JLabel badge = new JLabel(text);
        badge.setOpaque(false);
        badge.setFont(uiMedium(11f));
        badge.setForeground(emphasize ? Color.WHITE : MUTED_TEXT);
        badge.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        badge.putClientProperty(BADGE_ROLE, emphasize ? "primary" : "muted");
        badge.setUI(new BadgeLabelUI());
        return badge;
    }

    /**
     * Show/hide badge paint without removing it from layout, so parent rows don't jump.
     */
    static void setBadgeShown(JLabel badge, boolean shown) {
        badge.putClientProperty(BADGE_ROLE, shown ? "primary" : "hidden");
        badge.setForeground(shown ? Color.WHITE : new Color(0, 0, 0, 0));
        badge.repaint();
    }

    static JPanel summaryCard(String... rows) {
        JPanel card = new SurfacePanel(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        card.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.setMaximumSize(new Dimension(420, Integer.MAX_VALUE));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        for (int i = 0; i < rows.length; i += 2) {
            String key = rows[i];
            String value = i + 1 < rows.length ? rows[i + 1] : "";
            content.add(summaryRow(key, value));
            if (i + 2 < rows.length) {
                content.add(Box.createRigidArea(new Dimension(0, 8)));
            }
        }
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    static JPanel summaryRow(String key, String value) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        JLabel keyLabel = fieldLabel(key);
        JLabel valueLabel = new JLabel(value);
        valueLabel.setForeground(TEXT);
        valueLabel.setFont(uiMedium(13f));
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        row.add(keyLabel, BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);
        return row;
    }

    static JPanel versionTransition(String from, String to) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel current = badge(from == null || from.isEmpty() ? "None" : from, false);
        JLabel arrow = new JLabel("→");
        arrow.setForeground(MUTED_TEXT);
        arrow.setFont(uiMedium(14f));
        JLabel next = badge(to, true);

        row.add(current);
        row.add(arrow);
        row.add(next);
        return row;
    }

    /** Handle for a two-option segmented control, {@link #panel} is the component to add. */
    static final class SegmentedControl {

        final JPanel panel;
        private final JToggleButton left;
        private final JToggleButton right;
        private final Consumer<Boolean> onSelection;

        private SegmentedControl(JPanel panel, JToggleButton left, JToggleButton right, Consumer<Boolean> onSelection) {
            this.panel = panel;
            this.left = left;
            this.right = right;
            this.onSelection = onSelection;
        }

        /** Select the left option (e.g. Automatic) and run the selection callback. */
        void selectLeft() {
            left.setSelected(true);
            onSelection.accept(Boolean.TRUE);
            panel.repaint();
        }

        /** Select the right option (e.g. Manual) and run the selection callback. */
        void selectRight() {
            right.setSelected(true);
            onSelection.accept(Boolean.FALSE);
            panel.repaint();
        }

    }

    /**
     * Two-option segmented control. {@code leftSelected} true selects the first option.
     * Callback receives true when the left option becomes selected.
     * <p>
     * The shell paints a single track + one selection thumb. Segment buttons are text-only
     * so you never get a pill drawn inside another pill.
     */
    static SegmentedControl segmentedControl(String leftLabel, String rightLabel, boolean leftSelected,
                                             Consumer<Boolean> onSelection) {
        final SegmentedShell shell = new SegmentedShell();
        shell.setLayout(new GridLayout(1, 2, 0, 0));
        shell.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        // Max == preferred: a two-option toggle reads as a control, not as a full-width banner
        int shellHeight = scaled(36);
        int shellWidth = scaled(260);
        shell.setPreferredSize(new Dimension(shellWidth, shellHeight));
        shell.setMinimumSize(new Dimension(scaled(160), shellHeight));
        shell.setMaximumSize(new Dimension(shellWidth, shellHeight));

        final JToggleButton left = new JToggleButton(leftLabel);
        final JToggleButton right = new JToggleButton(rightLabel);
        prepareSegmentButton(left, "left");
        prepareSegmentButton(right, "right");
        left.setSelected(leftSelected);
        right.setSelected(!leftSelected);
        ButtonGroup group = new ButtonGroup();
        group.add(left);
        group.add(right);

        left.addActionListener(e -> {
            if (left.isSelected()) {
                onSelection.accept(Boolean.TRUE);
            }
            shell.repaint();
        });
        right.addActionListener(e -> {
            if (right.isSelected()) {
                onSelection.accept(Boolean.FALSE);
            }
            shell.repaint();
        });

        // Keep thumb in sync with press/rollover/selection model changes
        ChangeListener repaintShell = e -> shell.repaint();
        left.addChangeListener(repaintShell);
        right.addChangeListener(repaintShell);

        // Native segmented controls move with arrow keys, Tab alone would skip past the whole group
        installArrowNavigation(left, right);
        installArrowNavigation(right, left);

        shell.bind(left, right);
        shell.add(left);
        shell.add(right);
        return new SegmentedControl(shell, left, right, onSelection);
    }

    /** Left/Right/Up/Down on {@code from} selects and focuses {@code to}. */
    private static void installArrowNavigation(final JToggleButton from, final JToggleButton to) {
        String key = "relauncher.segment.move";
        InputMap inputMap = from.getInputMap(JComponent.WHEN_FOCUSED);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), key);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), key);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), key);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), key);
        from.getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                to.requestFocusInWindow();
                to.doClick();
            }
        });
    }

    private static void prepareSegmentButton(JToggleButton button, String side) {
        button.putClientProperty(SEGMENTED, side);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setMargin(new Insets(6, 12, 6, 12));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Dimension segmentSize = new Dimension(scaled(120), scaled(30));
        button.setPreferredSize(segmentSize);
        button.setMinimumSize(segmentSize);
    }

    static JProgressBar progressBar() {
        JProgressBar bar = new JProgressBar();
        bar.setOpaque(false);
        bar.setBorderPainted(false);
        bar.setStringPainted(false);
        bar.setForeground(PRIMARY);
        bar.setBackground(CONTROL);
        bar.setPreferredSize(new Dimension(scaled(480), scaled(12)));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, scaled(12)));
        bar.setUI(new ModernProgressBarUI());
        return bar;
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
        header.setMaximumSize(new Dimension(scaled(MAX_CONTENT_WIDTH), Integer.MAX_VALUE));

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
        // Capped and centre-aligned, so widening the window adds margin rather than card width
        card.setMaximumSize(new Dimension(scaled(MAX_CONTENT_WIDTH), Integer.MAX_VALUE));

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel(title);
        heading.setForeground(TEXT);
        heading.setFont(uiSemibold(15f));
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

    /**
     * A {@link CardLayout} host that measures the card currently showing.
     * Plain CardLayout reserves the tallest card's height at all times, which leaves the shorter
     * card sitting above a block of dead space.
     */
    static JPanel cardStack(CardLayout layout) {
        return new CardStack(layout);
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
        if (component.getFont() == null) {
            component.setFont(BASE_FONT);
        }
        // Ask Swing to use LCD glyph rasterization on every component paint path
        if (component instanceof JComponent) {
            JComponent swing = (JComponent) component;
            swing.putClientProperty(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            swing.putClientProperty(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        }

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
            boolean segmented = button.getClientProperty(SEGMENTED) != null;
            button.setUI(new ModernButtonUI());
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.setFocusPainted(false);
            button.setRolloverEnabled(true);
            // Strip L&F chrome so we never get a second border/pill under
            button.setBorderPainted(false);
            button.setContentAreaFilled(false);
            button.setOpaque(false);
            // Padding has to live in the border, not the margin: an empty border contributes zero
            // insets, and BasicButtonUI sizes from the border, so margin alone paints text-tight pills.
            if (segmented) {
                setButtonPadding(button, new Insets(6, 12, 6, 12));
            } else {
                boolean compact = Boolean.TRUE.equals(button.getClientProperty(COMPACT_BUTTON));
                setButtonPadding(button, compact ? new Insets(5, 12, 5, 12) : new Insets(8, 16, 8, 16));
                if (compact) {
                    Dimension preferred = button.getPreferredSize();
                    button.setPreferredSize(new Dimension(preferred.width, scaled(34)));
                }
            }
        } else if (component instanceof JTextField) {
            JTextField text = (JTextField) component;
            text.setForeground(TEXT);
            text.setBackground(CONTROL);
            text.setDisabledTextColor(DISABLED_TEXT);
            text.setCaretColor(PRIMARY);
            text.setMargin(new Insets(7, 10, 7, 10));
            text.setBorder(textFieldBorder(text.hasFocus() ? FOCUS : BORDER));
            int fieldHeight = scaled(34);
            text.setPreferredSize(new Dimension(text.getPreferredSize().width, fieldHeight));
            text.setMaximumSize(new Dimension(Integer.MAX_VALUE, fieldHeight));
        } else if (component instanceof JComboBox) {
            JComboBox<?> comboBox = (JComboBox<?>) component;
            comboBox.setUI(new DarkComboBoxUI());
            comboBox.setOpaque(false);
            comboBox.setBackground(CONTROL);
            comboBox.setForeground(TEXT);
            comboBox.setBorder(new RoundedBorder(BORDER, 8));
            int comboHeight = scaled(36);
            comboBox.setPreferredSize(new Dimension(comboBox.getPreferredSize().width, comboHeight));
            comboBox.setMinimumSize(new Dimension(scaled(80), comboHeight));
            comboBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, comboHeight));
            installDarkRenderer(comboBox);
        } else if (component instanceof JScrollBar) {
            JScrollBar scrollBar = (JScrollBar) component;
            scrollBar.setUI(new DarkScrollBarUI());
            scrollBar.setBackground(BACKGROUND);
            if (scrollBar.getOrientation() == Adjustable.VERTICAL) {
                scrollBar.setPreferredSize(new Dimension(10, scrollBar.getPreferredSize().height));
            }
        } else if (component instanceof JProgressBar) {
            JProgressBar progressBar = (JProgressBar) component;
            progressBar.setUI(new ModernProgressBarUI());
            progressBar.setForeground(PRIMARY);
            progressBar.setBackground(CONTROL);
            progressBar.setBorderPainted(false);
            progressBar.setOpaque(false);
        } else if (component instanceof JLabel) {
            JLabel label = (JLabel) component;
            Object badgeRole = label.getClientProperty(BADGE_ROLE);
            if (badgeRole != null) {
                if ("hidden".equals(badgeRole)) {
                    label.setForeground(new Color(0, 0, 0, 0));
                } else {
                    boolean emphasize = "primary".equals(badgeRole);
                    label.setForeground(emphasize ? Color.WHITE : MUTED_TEXT);
                }
                label.setUI(new BadgeLabelUI());
            } else if (label.getForeground() == null || Color.BLACK.equals(label.getForeground())) {
                component.setForeground(TEXT);
            }
        } else if (component instanceof JPanel && !(component instanceof SurfacePanel) && !(component instanceof SegmentedShell)) {
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

    /** Scales the padding with the UI and keeps margin/border in agreement. */
    private static void setButtonPadding(AbstractButton button, Insets padding) {
        Insets scaled = new Insets(scaled(padding.top), scaled(padding.left),
                scaled(padding.bottom), scaled(padding.right));
        button.setMargin(scaled);
        button.setBorder(BorderFactory.createEmptyBorder(scaled.top, scaled.left, scaled.bottom, scaled.right));
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
        int width = Math.min(720, Math.max(560, screenBounds.width - 80));
        int height = Math.min(820, Math.max(640, screenBounds.height - 100));
        return new Dimension(width, height);
    }

    /**
     * Sizes a dialog and enforces a minimum size.
     * Windows native peers sometimes ignore {@link Window#setMinimumSize(Dimension)}.
     * The resize guard clamps after-the-fact.
     *
     * @param contentFloor preferred size of the screen that must remain usable (e.g. starting UI)
     */
    static void sizeAndGuard(Window window, Dimension targetSize, Dimension contentFloor) {
        final Dimension contentFloorCopy = new Dimension(contentFloor);
        Dimension minimum = computeMinimumSize(window, contentFloorCopy);
        window.setMinimumSize(minimum);

        GraphicsConfiguration gc = window.getGraphicsConfiguration();
        if (gc == null) {
            gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        }
        Rectangle screen = gc.getBounds();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        int maxW = Math.max(minimum.width, screen.width - screenInsets.left - screenInsets.right - 40);
        int maxH = Math.max(minimum.height, screen.height - screenInsets.top - screenInsets.bottom - 40);

        int width = Math.min(maxW, Math.max(minimum.width, targetSize.width));
        int height = Math.min(maxH, Math.max(minimum.height, targetSize.height));
        window.setSize(width, height);
        installMinimumSizeGuard(window, contentFloorCopy);
    }

    private static Dimension computeMinimumSize(Window window, Dimension contentFloor) {
        Insets insets = window.getInsets();
        // Peer insets are often 0 before first show
        int chromeW = Math.max(insets.left + insets.right, 16);
        int chromeH = Math.max(insets.top + insets.bottom, 48);

        int minW = Math.max(560, contentFloor.width + chromeW);
        int minH = Math.max(660, contentFloor.height + chromeH);

        GraphicsConfiguration gc = window.getGraphicsConfiguration();
        if (gc == null) {
            gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        }
        Rectangle screen = gc.getBounds();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        int maxW = Math.max(minW, screen.width - screenInsets.left - screenInsets.right - 40);
        int maxH = Math.max(minH, screen.height - screenInsets.top - screenInsets.bottom - 40);

        return new Dimension(Math.min(minW, maxW), Math.min(minH, maxH));
    }

    private static void installMinimumSizeGuard(final Window window, final Dimension contentFloor) {
        window.addComponentListener(new ComponentAdapter() {
            private boolean adjusting;

            @Override
            public void componentResized(ComponentEvent event) {
                if (adjusting) {
                    return;
                }
                Dimension minimum = computeMinimumSize(window, contentFloor);
                window.setMinimumSize(minimum);
                int width = window.getWidth();
                int height = window.getHeight();
                int clampedW = Math.max(width, minimum.width);
                int clampedH = Math.max(height, minimum.height);
                if (clampedW != width || clampedH != height) {
                    adjusting = true;
                    try {
                        window.setSize(clampedW, clampedH);
                    } finally {
                        adjusting = false;
                    }
                }
            }
        });
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent event) {
                Dimension minimum = computeMinimumSize(window, contentFloor);
                window.setMinimumSize(minimum);
                int width = Math.max(window.getWidth(), minimum.width);
                int height = Math.max(window.getHeight(), minimum.height);
                if (width != window.getWidth() || height != window.getHeight()) {
                    window.setSize(width, height);
                }
            }
        });
    }

    /**
     * Centers {@code content} when there is room; scrolls when the viewport is shorter/narrower
     * so actions and footer hints never vanish off-screen.
     */
    static JScrollPane centeredScroll(JComponent content) {
        CenteredHost host = new CenteredHost();
        host.add(content);
        return scrollPane(host);
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

    private static final class CardStack extends JPanel {

        private CardStack(CardLayout layout) {
            super(layout);
            setOpaque(false);
        }

        /** CardLayout hides every card but the active one, so visibility identifies it. */
        private Component visibleCard() {
            for (Component child : getComponents()) {
                if (child.isVisible()) {
                    return child;
                }
            }
            return null;
        }

        @Override
        public Dimension getPreferredSize() {
            Component card = visibleCard();
            if (card == null) {
                return super.getPreferredSize();
            }
            Dimension size = card.getPreferredSize();
            Insets insets = getInsets();
            return new Dimension(size.width + insets.left + insets.right,
                    size.height + insets.top + insets.bottom);
        }

        @Override
        public Dimension getMaximumSize() {
            // CardLayout reports an unbounded maximum, which lets BoxLayout stretch the stack
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
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

    /** Centers a single child and tracks the viewport when smaller so content stays centered. */
    private static final class CenteredHost extends JPanel implements Scrollable {

        private CenteredHost() {
            super(new GridBagLayout());
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
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
            Container parent = getParent();
            return parent == null || getPreferredSize().width <= parent.getWidth();
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            Container parent = getParent();
            return parent == null || getPreferredSize().height <= parent.getHeight();
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
                g.setStroke(new BasicStroke(1.8F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
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
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
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
            button.setBorder(BorderFactory.createEmptyBorder());
        }

        @Override
        public void update(Graphics graphics, JComponent component) {
            // Skip ComponentUI's opaque fill, we paint it ourselves
            paint(graphics, component);
        }

        @Override
        public void paint(Graphics graphics, JComponent component) {
            AbstractButton button = (AbstractButton) component;
            ButtonModel model = button.getModel();
            boolean segmented = button.getClientProperty(SEGMENTED) != null;
            boolean primary = Boolean.TRUE.equals(button.getClientProperty(PRIMARY_BUTTON)) ||
                    (!segmented && button instanceof JToggleButton && model.isSelected());
            boolean ghost = Boolean.TRUE.equals(button.getClientProperty(GHOST_BUTTON));

            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            Color foreground;
            int width = component.getWidth();
            int height = component.getHeight();

            if (segmented) {
                // Selection thumb is painted by SegmentedShell and the button is label-only.
                foreground = model.isSelected() ? Color.WHITE : (button.isEnabled() ? TEXT : DISABLED_TEXT);
            } else if (!button.isEnabled()) {
                fillButton(g, SURFACE, BORDER, width, height, true);
                foreground = DISABLED_TEXT;
            } else if (primary) {
                // One solid pill only
                Color fill = model.isPressed() || model.isRollover() ? PRIMARY_HOVER : PRIMARY;
                fillButton(g, fill, null, width, height, false);
                foreground = Color.WHITE;
            } else if (ghost) {
                Color fill = model.isPressed() || model.isRollover() ? CONTROL_HOVER : SURFACE;
                Color outline = model.isRollover() || model.isPressed() || button.hasFocus() ? FOCUS : BORDER;
                fillButton(g, fill, outline, width, height, true);
                foreground = TEXT;
            } else {
                Color fill = model.isPressed() || model.isRollover() ? CONTROL_HOVER : CONTROL;
                Color outline = model.isRollover() || button.hasFocus() ? FOCUS : BORDER;
                fillButton(g, fill, outline, width, height, true);
                foreground = TEXT;
            }
            g.dispose();

            button.setForeground(foreground);
            super.paint(graphics, component);
        }

        private static void fillButton(Graphics2D g, Color fill, Color outline, int width, int height, boolean drawOutline) {
            // Fill the full bounds so AA doesn't leave a 1px "outer ring" of the background
            int arc = Math.min(height, 12);
            g.setColor(fill);
            g.fillRoundRect(0, 0, width, height, arc, arc);
            if (drawOutline && outline != null) {
                g.setColor(outline);
                g.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
            }
        }
    }

    /**
     * Paints track + a single selection thumb. Child toggle buttons only render labels.
     */
    private static final class SegmentedShell extends JPanel {

        private JToggleButton left;
        private JToggleButton right;

        private SegmentedShell() {
            setOpaque(false);
        }

        private void bind(JToggleButton left, JToggleButton right) {
            this.left = left;
            this.right = right;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            int width = getWidth();
            int height = getHeight();
            float trackArc = height;

            g.setColor(CONTROL);
            g.fill(new RoundRectangle2D.Float(0, 0, width, height, trackArc, trackArc));
            g.setColor(BORDER);
            g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, trackArc, trackArc));

            JToggleButton selected = null;
            if (left != null && left.isSelected()) {
                selected = left;
            } else if (right != null && right.isSelected()) {
                selected = right;
            }

            if (selected != null) {
                Insets insets = getInsets();
                int innerW = Math.max(0, width - insets.left - insets.right);
                int innerH = Math.max(0, height - insets.top - insets.bottom);
                int thumbW = innerW / 2;
                int thumbX = insets.left;
                if (selected == right) {
                    thumbX = insets.left + thumbW;
                    thumbW = innerW - thumbW; // Absorb odd pixel on the right half
                }
                boolean pressed = selected.getModel().isPressed();
                g.setColor(pressed ? PRIMARY_HOVER : PRIMARY);
                float thumbArc = Math.max(8, innerH);
                g.fill(new RoundRectangle2D.Float(thumbX, insets.top, thumbW, innerH, thumbArc, thumbArc));
            }

            g.dispose();
            super.paintComponent(graphics);
        }

    }

    private static final class BadgeLabelUI extends BasicLabelUI {

        @Override
        public void paint(Graphics graphics, JComponent component) {
            JLabel label = (JLabel) component;
            Object role = label.getClientProperty(BADGE_ROLE);
            // Hidden badges keep layout size but paint nothing, prevents row height jumps
            if ("hidden".equals(role)) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean emphasize = "primary".equals(role);
            g.setColor(emphasize ? PRIMARY : CONTROL);
            g.fillRoundRect(0, 0, component.getWidth(), component.getHeight(), 12, 12);
            if (!emphasize) {
                g.setColor(BORDER);
                g.drawRoundRect(0, 0, component.getWidth() - 1, component.getHeight() - 1, 12, 12);
            }
            g.dispose();
            super.paint(graphics, component);
        }

    }

    private static final class ModernProgressBarUI extends BasicProgressBarUI {

        @Override
        protected void paintDeterminate(Graphics graphics, JComponent component) {
            paintTrackAndFill(graphics, component, true);
        }

        @Override
        protected void paintIndeterminate(Graphics graphics, JComponent component) {
            paintTrackAndFill(graphics, component, false);
        }

        private void paintTrackAndFill(Graphics graphics, JComponent component, boolean determinate) {
            Insets insets = progressBar.getInsets();
            int width = progressBar.getWidth() - insets.right - insets.left;
            int height = progressBar.getHeight() - insets.top - insets.bottom;
            if (width <= 0 || height <= 0) {
                return;
            }

            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int x = insets.left;
            int y = insets.top;
            int radius = Math.max(8, height);

            g.setColor(CONTROL);
            g.fillRoundRect(x, y, width, height, radius, radius);

            if (determinate) {
                int fill = getAmountFull(insets, width, height);
                if (fill > 0) {
                    g.setColor(PRIMARY);
                    g.fillRoundRect(x, y, Math.max(height, fill), height, radius, radius);
                }
            } else {
                boxRect = getBox(boxRect);
                if (boxRect != null) {
                    g.setColor(PRIMARY);
                    int pulseWidth = Math.max(height * 3, boxRect.width);
                    int pulseX = Math.min(x + width - pulseWidth, Math.max(x, boxRect.x));
                    g.fillRoundRect(pulseX, y, pulseWidth, height, radius, radius);
                }
            }

            if (progressBar.isStringPainted()) {
                paintString(g, x, y, width, height, getAmountFull(insets, width, height), insets);
            }
            g.dispose();
        }

        @Override
        protected Color getSelectionForeground() {
            return TEXT;
        }

        @Override
        protected Color getSelectionBackground() {
            return TEXT;
        }

    }
}
