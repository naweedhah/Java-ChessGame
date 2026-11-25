package com.naweedh.chess.gui;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Entry point: {@code java -jar java-chess.jar}. */
public final class ChessApp {

    private ChessApp() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ignored) {
                // The default look and feel is fine.
            }
            new GameWindow().setVisible(true);
        });
    }
}
