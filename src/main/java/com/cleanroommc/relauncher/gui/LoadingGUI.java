package com.cleanroommc.relauncher.gui;

import javax.swing.*;
import java.awt.*;

public class LoadingGUI {

    private final JFrame frame;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;

    public LoadingGUI() {
        RelauncherUI.install();
        frame = new JFrame("Cleanroom Relauncher");
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setSize(560, 300);
        frame.setLayout(new BorderLayout());

        JPanel panel = new JPanel(new BorderLayout(0, 22));
        panel.setBackground(RelauncherUI.BACKGROUND);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(RelauncherUI.BORDER),
                BorderFactory.createEmptyBorder(30, 36, 32, 36)
        ));

        ImageIcon rawIcon = new ImageIcon(
                Toolkit.getDefaultToolkit().getImage(LoadingGUI.class.getResource("/cleanroom-relauncher.png")));
        frame.setIconImage(rawIcon.getImage());

        JPanel header = RelauncherUI.header(rawIcon.getImage(), "Preparing Cleanroom",
                "This may take a moment. You can return to the game when it finishes.");
        panel.add(header, BorderLayout.NORTH);

        JPanel progressContent = new JPanel();
        progressContent.setOpaque(false);
        progressContent.setLayout(new BoxLayout(progressContent, BoxLayout.Y_AXIS));
        statusLabel = new JLabel("Initializing…");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 14f));
        statusLabel.setForeground(RelauncherUI.TEXT);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressContent.add(statusLabel);
        progressContent.add(Box.createRigidArea(new Dimension(0, 10)));

        progressBar = new JProgressBar();
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressBar.setPreferredSize(new Dimension(480, 14));
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
        progressBar.setIndeterminate(true);
        progressBar.setForeground(RelauncherUI.PRIMARY);
        progressBar.setBackground(new Color(225, 232, 238));
        progressBar.setBorderPainted(false);
        progressContent.add(progressBar);
        panel.add(progressContent, BorderLayout.CENTER);

        frame.add(panel, BorderLayout.CENTER);
        RelauncherUI.styleTree(panel);
        frame.setLocationRelativeTo(null);
    }

    public void show() {
        SwingUtilities.invokeLater(() -> frame.setVisible(true));
    }
    public void enableProgress() {
        if (progressBar.isIndeterminate()) {
            progressBar.setIndeterminate(false);
            progressBar.setStringPainted(true);
        }
    }
    public void disableProgress() {
        if (!progressBar.isIndeterminate()) {
            progressBar.setIndeterminate(true);
            progressBar.setStringPainted(false);
        }
    }

    public void setProgress(int percent) {
        SwingUtilities.invokeLater(() -> {
            int safePercent = Math.max(0, Math.min(100, percent));
            progressBar.setValue(safePercent);
            progressBar.setString(safePercent + "%");
        });
    }

    public void updateStatus(String status) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(status);
            statusLabel.setToolTipText(status);
        });
    }

    public void close() {
        SwingUtilities.invokeLater(() -> {
            frame.setVisible(false);
            frame.dispose();
        });
    }
}
