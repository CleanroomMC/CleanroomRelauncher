package com.cleanroommc.relauncher.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

import static com.cleanroommc.relauncher.CleanroomRelauncher.isJvm8Oracle;

public class LoadingGUI {

    private final JFrame frame;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;

    public LoadingGUI() {
        frame = new JFrame("Cleanroom Relauncher Progress");
        frame.setUndecorated(true);
        frame.setSize(800,400);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(105, 126, 147));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        statusLabel = new JLabel("Initializing..");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));
        if (!isJvm8Oracle()) {
            statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 30));
        }
        statusLabel.setForeground(Color.white);
        gbc.gridy = 1;
        gbc.weighty = 0.45;
        gbc.weightx = 0.8;
        panel.add(statusLabel, gbc);

        progressBar = new JProgressBar();
        if (!isJvm8Oracle()) {
            progressBar.setPreferredSize(new Dimension(progressBar.getPreferredSize().width, 22));
        }
        progressBar.setIndeterminate(true);
        progressBar.setForeground(new Color(62, 164, 168));
        progressBar.setBackground(new Color(75, 90, 104));

        gbc.gridy = 2;
        gbc.weighty = 0.15;
        gbc.weightx = 1;
        panel.add(progressBar, gbc);

        ImageIcon rawIcon = new ImageIcon(
                Toolkit.getDefaultToolkit().getImage(LoadingGUI.class.getResource("/cleanroom-relauncher.png")));
        JLabel logo = new JLabel(new ImageIcon(rawIcon.getImage().getScaledInstance(160, 160, Image.SCALE_SMOOTH)));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        logo.setBorder(new EmptyBorder(50, 0, 50, 0));

        gbc.gridy = 0;
        gbc.weighty = 0.4;
        gbc.weightx = 1;
        panel.add(logo, gbc);
        frame.add(panel, BorderLayout.CENTER);
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
            progressBar.setValue(percent);
        });
    }

    public void updateStatus(String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(status));
    }

    public void close() {
        SwingUtilities.invokeLater(() -> {
            frame.setVisible(false);
            frame.dispose();
        });
    }
}
