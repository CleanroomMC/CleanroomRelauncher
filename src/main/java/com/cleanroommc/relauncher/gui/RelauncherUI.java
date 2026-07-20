package com.cleanroommc.relauncher.gui;

import com.cleanroommc.relauncher.util.enums.ArgsEnum;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;

/** Shared visual language for the standalone relauncher windows. */
final class RelauncherUI {

    static final Color BACKGROUND = new Color(244, 247, 250);
    static final Color SURFACE = Color.WHITE;
    static final Color TEXT = new Color(27, 38, 49);
    static final Color MUTED_TEXT = new Color(91, 105, 120);
    static final Color PRIMARY = new Color(34, 139, 143);
    static final Color PRIMARY_HOVER = new Color(28, 119, 123);
    static final Color BORDER = new Color(216, 224, 232);
    static final Color FOCUS = new Color(82, 177, 181);

    private static final String PRIMARY_BUTTON = "relauncher.primaryButton";
    private static final String COMPACT_BUTTON = "relauncher.compactButton";
    private static final Font BASE_FONT = new Font("SansSerif", Font.PLAIN, 13);

    private RelauncherUI() { }

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
        UIManager.put("ToolTip.font", BASE_FONT.deriveFont(12f));
    }

    static void primary(AbstractButton button) {
        button.putClientProperty(PRIMARY_BUTTON, Boolean.TRUE);
        button.setFont(button.getFont().deriveFont(Font.BOLD));
    }

    static void compact(AbstractButton button) {
        button.putClientProperty(COMPACT_BUTTON, Boolean.TRUE);
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

    static String argumentLabel(ArgsEnum argument) {
        if (argument == ArgsEnum.CompactObjectHeaders) {
            return "Compact object headers (recommended for Java 24+)";
        }
        if (argument == ArgsEnum.ZGC) {
            return "ZGC (experimental; best on stronger CPUs)";
        }
        return argument.name();
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
        header.setBorder(BorderFactory.createEmptyBorder(4, 4, 22, 4));
        header.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel logo = new JLabel(new ImageIcon(image.getScaledInstance(72, 72, Image.SCALE_SMOOTH)));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel heading = title(title);
        heading.setAlignmentX(Component.CENTER_ALIGNMENT);
        heading.setHorizontalAlignment(SwingConstants.CENTER);
        heading.setMaximumSize(new Dimension(Integer.MAX_VALUE, heading.getPreferredSize().height));
        JLabel detail = subtitle(subtitle);
        detail.setAlignmentX(Component.CENTER_ALIGNMENT);
        detail.setHorizontalAlignment(SwingConstants.CENTER);
        detail.setMaximumSize(new Dimension(Integer.MAX_VALUE, detail.getPreferredSize().height));

        header.add(logo);
        header.add(Box.createRigidArea(new Dimension(0, 10)));
        header.add(heading);
        header.add(Box.createRigidArea(new Dimension(0, 4)));
        header.add(detail);
        return header;
    }

    static JPanel card(String title, String description, JComponent content) {
        JPanel card = new SurfacePanel(new BorderLayout(0, 14));
        card.setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));
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
        return scrollPane;
    }

    static JPanel scrollableColumn() {
        return new ScrollableColumn();
    }

    static JPanel footer() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        footer.setBackground(SURFACE);
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
        } else if (component instanceof AbstractButton) {
            AbstractButton button = (AbstractButton) component;
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
            text.setBackground(SURFACE);
            text.setCaretColor(PRIMARY);
            text.setMargin(new Insets(7, 10, 7, 10));
            text.setBorder(textFieldBorder(BORDER));
            text.setPreferredSize(new Dimension(text.getPreferredSize().width, 34));
        } else if (component instanceof JComboBox) {
            component.setBackground(SURFACE);
            component.setForeground(TEXT);
            component.setPreferredSize(new Dimension(component.getPreferredSize().width, 36));
        } else if (component instanceof JLabel) {
            if (((JLabel) component).getForeground() == null || Color.BLACK.equals(((JLabel) component).getForeground())) {
                component.setForeground(TEXT);
            }
        } else if (component instanceof JPanel && !(component instanceof SurfacePanel)) {
            JPanel panel = (JPanel) component;
            if (!BACKGROUND.equals(panel.getBackground()) && !SURFACE.equals(panel.getBackground())) {
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
                BorderFactory.createLineBorder(color),
                BorderFactory.createEmptyBorder(0, 4, 0, 4)
        );
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
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 14, 14));
            g.setColor(BORDER);
            g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 2, getHeight() - 2, 14, 14));
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
                fill = new Color(232, 236, 240);
                outline = BORDER;
                foreground = new Color(145, 154, 164);
            } else if (primary) {
                fill = model.isPressed() || model.isRollover() ? PRIMARY_HOVER : PRIMARY;
                outline = fill;
                foreground = Color.WHITE;
            } else {
                fill = model.isPressed() || model.isRollover() ? new Color(235, 242, 245) : SURFACE;
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
