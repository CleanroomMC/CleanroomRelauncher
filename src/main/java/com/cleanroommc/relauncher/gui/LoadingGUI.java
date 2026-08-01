package com.cleanroommc.relauncher.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

public class LoadingGUI {

    private static final int WINDOW_WIDTH = 560;
    private static final int WINDOW_HEIGHT = 280;
    private static final int CORNER_RADIUS = 18;

    private final JFrame frame;
    private final JLabel statusLabel;
    private final JLabel detailLabel;
    private final JProgressBar progressBar;
    private final JLabel percentLabel;

    public LoadingGUI() {
        RelauncherUI.install();
        Rectangle screenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        float scale = RelauncherUI.uiScaleFor(screenBounds);
        RelauncherUI.setUiScale(scale);

        frame = new JFrame("Cleanroom Relauncher");
        frame.setUndecorated(true);
        frame.setResizable(false);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setSize(Math.round(WINDOW_WIDTH * scale), Math.round(WINDOW_HEIGHT * scale));
        frame.setLayout(new BorderLayout());
        frame.setBackground(new Color(0, 0, 0, 0));

        JPanel panel = new JPanel(new BorderLayout(0, 0)) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(RelauncherUI.BACKGROUND);
                g.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, CORNER_RADIUS, CORNER_RADIUS));
                g.setColor(RelauncherUI.BORDER);
                g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 2, getHeight() - 2, CORNER_RADIUS, CORNER_RADIUS));
                g.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32));

        ImageIcon rawIcon = new ImageIcon(
                Toolkit.getDefaultToolkit().getImage(LoadingGUI.class.getResource("/cleanroom-relauncher.png")));
        frame.setIconImage(rawIcon.getImage());

        JPanel header = RelauncherUI.header(rawIcon.getImage(), "Preparing Cleanroom",
                "This may take a moment. You can return to the game when it finishes.");
        panel.add(header, BorderLayout.NORTH);

        JPanel progressContent = new JPanel();
        progressContent.setOpaque(false);
        progressContent.setLayout(new BoxLayout(progressContent, BoxLayout.Y_AXIS));
        progressContent.setBorder(BorderFactory.createEmptyBorder(8, 4, 0, 4));

        // Glue above and below balances the block in whatever height is left under the header,
        // instead of stacking it at the top with all the slack below.
        progressContent.add(Box.createVerticalGlue());

        statusLabel = new JLabel("Initializing…");
        statusLabel.setFont(statusLabel.getFont().deriveFont(14f));
        statusLabel.setForeground(RelauncherUI.TEXT);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressContent.add(statusLabel);
        progressContent.add(Box.createRigidArea(new Dimension(0, 4)));

        detailLabel = RelauncherUI.subtitle("Working in the background…");
        detailLabel.setFont(detailLabel.getFont().deriveFont(12f));
        detailLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressContent.add(detailLabel);
        progressContent.add(Box.createRigidArea(new Dimension(0, 14)));

        JPanel progressRow = new JPanel(new BorderLayout(10, 0));
        progressRow.setOpaque(false);
        progressRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        progressBar = RelauncherUI.progressBar();
        progressBar.setIndeterminate(true);
        progressRow.add(progressBar, BorderLayout.CENTER);

        percentLabel = new JLabel("");
        percentLabel.setForeground(RelauncherUI.MUTED_TEXT);
        percentLabel.setFont(percentLabel.getFont().deriveFont(12f));
        percentLabel.setVisible(false);
        progressRow.add(percentLabel, BorderLayout.EAST);

        progressContent.add(progressRow);
        progressContent.add(Box.createVerticalGlue());
        panel.add(progressContent, BorderLayout.CENTER);

        frame.add(panel, BorderLayout.CENTER);
        RelauncherUI.scaleComponent(panel, scale);
        RelauncherUI.styleTree(panel);
        panel.setOpaque(false);
        installDragToMove(panel);
        frame.setLocationRelativeTo(null);
        applyRoundedShape();
    }

    /**
     * Undecorated windows have no title bar to grab. Without this the progress window cannot be
     * moved out of the way while a long download runs.
     */
    private void installDragToMove(JComponent grip) {
        MouseAdapter dragger = new MouseAdapter() {

            private Point grabOffset;

            @Override
            public void mousePressed(MouseEvent event) {
                grabOffset = event.getPoint();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                grabOffset = null;
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (grabOffset == null) {
                    return;
                }
                Point onScreen = event.getLocationOnScreen();
                frame.setLocation(onScreen.x - grabOffset.x, onScreen.y - grabOffset.y);
            }
        };
        grip.addMouseListener(dragger);
        grip.addMouseMotionListener(dragger);
        grip.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
    }

    private void applyRoundedShape() {
        frame.setShape(new RoundRectangle2D.Double(0, 0, frame.getWidth(), frame.getHeight(), CORNER_RADIUS, CORNER_RADIUS));
    }

    public void show() {
        SwingUtilities.invokeLater(() -> {
            applyRoundedShape();
            RelauncherUI.showInitiallyInForeground(frame);
        });
    }

    public void enableProgress() {
        SwingUtilities.invokeLater(() -> {
            if (progressBar.isIndeterminate()) {
                progressBar.setIndeterminate(false);
                progressBar.setStringPainted(false);
                percentLabel.setVisible(true);
                detailLabel.setText("Download progress updates as files arrive.");
            }
        });
    }

    public void disableProgress() {
        SwingUtilities.invokeLater(() -> {
            if (!progressBar.isIndeterminate()) {
                progressBar.setIndeterminate(true);
                progressBar.setStringPainted(false);
                percentLabel.setVisible(false);
                percentLabel.setText("");
                detailLabel.setText("Working in the background…");
            }
        });
    }

    public void setProgress(int percent) {
        SwingUtilities.invokeLater(() -> {
            int safePercent = Math.max(0, Math.min(100, percent));
            progressBar.setValue(safePercent);
            percentLabel.setText(safePercent + "%");
            percentLabel.setVisible(true);
            if (safePercent == 100) {
                detailLabel.setText("Finishing up…");
            }
        });
    }

    public void updateStatus(String status) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(status);
            RelauncherUI.tooltip(statusLabel, status);
        });
    }

    public void close() {
        SwingUtilities.invokeLater(() -> {
            frame.setVisible(false);
            frame.dispose();
        });
    }
}
