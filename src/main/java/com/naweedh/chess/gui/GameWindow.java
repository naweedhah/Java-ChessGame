package com.naweedh.chess.gui;

import com.naweedh.chess.engine.Game;
import com.naweedh.chess.engine.GameStatus;
import com.naweedh.chess.engine.Move;
import com.naweedh.chess.engine.PieceType;
import com.naweedh.chess.engine.Search;
import com.naweedh.chess.engine.Side;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Main window: board, move list and controls. It owns the game flow (whose turn, when the
 * computer plays, game over) while all chess rules live in the engine package.
 */
final class GameWindow extends JFrame {

    private enum Mode {
        TWO_PLAYERS("Two players (same computer)"),
        WHITE_VS_COMPUTER("You play White vs computer"),
        BLACK_VS_COMPUTER("You play Black vs computer");

        final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private Game game = new Game();
    private Mode mode = Mode.WHITE_VS_COMPUTER;
    private Search.Level level = Search.Level.MEDIUM;
    private SwingWorker<Search.Result, Void> thinking;

    private final BoardPanel board = new BoardPanel(game, this::onHumanMove);
    private final JLabel status = new JLabel();
    private final JTextArea moveList = new JTextArea(18, 16);
    private final JButton undoButton = new JButton("Undo");

    GameWindow() {
        super("Java Chess");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        Optional.ofNullable(GameWindow.class.getResource("/chess.png"))
                .ifPresent(url -> setIconImage(new ImageIcon(url).getImage()));

        moveList.setEditable(false);
        moveList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        status.setFont(status.getFont().deriveFont(Font.BOLD, 14f));

        JButton newGame = new JButton("New game…");
        JButton flip = new JButton("Flip board");
        JButton copyPgn = new JButton("Copy PGN");
        newGame.addActionListener(e -> askForNewGame());
        undoButton.addActionListener(e -> undo());
        flip.addActionListener(e -> board.setFlipped(!board.isFlipped()));
        copyPgn.addActionListener(e -> copyPgn());

        JPanel buttons = new JPanel(new GridLayout(2, 2, 6, 6));
        buttons.add(newGame);
        buttons.add(undoButton);
        buttons.add(flip);
        buttons.add(copyPgn);

        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        status.setAlignmentX(LEFT_ALIGNMENT);
        JScrollPane scroll = new JScrollPane(moveList);
        scroll.setAlignmentX(LEFT_ALIGNMENT);
        scroll.setPreferredSize(new Dimension(220, 480));
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        side.add(status);
        side.add(Box.createVerticalStrut(10));
        side.add(scroll);
        side.add(Box.createVerticalStrut(10));
        side.add(buttons);

        add(board, BorderLayout.CENTER);
        add(side, BorderLayout.EAST);
        pack();
        setLocationRelativeTo(null);

        startGame(mode, level);
    }

    /* ------------------------------------------------------------ Game flow */

    private void startGame(Mode newMode, Search.Level newLevel) {
        cancelThinking();
        mode = newMode;
        level = newLevel;
        game = new Game();
        board.setGame(game);
        board.setFlipped(mode == Mode.BLACK_VS_COMPUTER);
        afterMove();
    }

    private Set<Side> humanSides() {
        return switch (mode) {
            case TWO_PLAYERS -> EnumSet.allOf(Side.class);
            case WHITE_VS_COMPUTER -> EnumSet.of(Side.WHITE);
            case BLACK_VS_COMPUTER -> EnumSet.of(Side.BLACK);
        };
    }

    private void onHumanMove(int from, int to) {
        boolean promotes = game.legalMovesFrom(from).stream().anyMatch(m -> m.to() == to && m.promotion() != null);
        PieceType promotion = promotes ? askPromotion() : null;
        game.findMove(from, to, promotion).ifPresent(move -> {
            game.play(move);
            afterMove();
        });
    }

    /** Refreshes the UI and, if it is the computer's turn, starts it thinking. */
    private void afterMove() {
        refresh();
        GameStatus s = game.status();
        if (s.gameOver) {
            board.setInteractive(false);
            JOptionPane.showMessageDialog(this, describe(s), "Game over", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        boolean humanTurn = humanSides().contains(game.sideToMove());
        board.setInteractive(humanTurn);
        if (!humanTurn) {
            computerMove();
        }
    }

    /** The search runs on a background thread so the window stays responsive. */
    private void computerMove() {
        status.setText("Computer is thinking…");
        undoButton.setEnabled(false);
        Search search = Search.forLevel(level);
        var snapshot = game.position().copy();
        thinking = new SwingWorker<>() {
            @Override
            protected Search.Result doInBackground() {
                return search.findBestMove(snapshot);
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    Move move = get().bestMove();
                    if (move != null) {
                        game.play(move);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    JOptionPane.showMessageDialog(GameWindow.this, "The engine failed: " + e.getCause(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
                undoButton.setEnabled(true);
                afterMove();
            }
        };
        thinking.execute();
    }

    private void cancelThinking() {
        if (thinking != null && !thinking.isDone()) {
            thinking.cancel(true);
        }
        undoButton.setEnabled(true);
    }

    private void undo() {
        cancelThinking();
        // Against the computer, take back the computer's reply too so it is the human's turn again.
        do {
            game.undo();
        } while (game.canUndo() && !humanSides().contains(game.sideToMove()));
        board.clearSelection();
        refresh();
        board.setInteractive(humanSides().contains(game.sideToMove()));
    }

    /* ---------------------------------------------------------------- UI */

    private void refresh() {
        List<String> sans = game.sanMoves();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < sans.size(); i += 2) {
            text.append(String.format("%3d. %-8s %s%n", i / 2 + 1, sans.get(i), i + 1 < sans.size() ? sans.get(i + 1) : ""));
        }
        moveList.setText(text.toString());
        moveList.setCaretPosition(moveList.getDocument().getLength());

        GameStatus s = game.status();
        String turn = game.sideToMove() == Side.WHITE ? "White" : "Black";
        status.setText(s.gameOver ? describe(s) : turn + " to move" + (game.position().inCheck() ? " (check)" : ""));
        undoButton.setEnabled(game.canUndo());
        board.repaint();
    }

    private String describe(GameStatus s) {
        if (s == GameStatus.CHECKMATE) {
            return "Checkmate: " + (game.sideToMove() == Side.WHITE ? "Black" : "White") + " wins";
        }
        return s.description;
    }

    private void askForNewGame() {
        JComboBox<Mode> modes = new JComboBox<>(Mode.values());
        modes.setSelectedItem(mode);
        JComboBox<Search.Level> levels = new JComboBox<>(Search.Level.values());
        levels.setSelectedItem(level);

        JPanel form = new JPanel(new GridLayout(4, 1, 4, 4));
        form.add(new JLabel("Mode"));
        form.add(modes);
        form.add(new JLabel("Computer strength"));
        form.add(levels);

        int choice = JOptionPane.showConfirmDialog(this, form, "New game", JOptionPane.OK_CANCEL_OPTION);
        if (choice == JOptionPane.OK_OPTION) {
            startGame((Mode) modes.getSelectedItem(), (Search.Level) levels.getSelectedItem());
        }
    }

    private PieceType askPromotion() {
        PieceType[] options = {PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT};
        String[] labels = {"Queen", "Rook", "Bishop", "Knight"};
        int choice = JOptionPane.showOptionDialog(this, "Promote to:", "Promotion",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, labels, labels[0]);
        return choice < 0 ? PieceType.QUEEN : options[choice];
    }

    private void copyPgn() {
        String white = mode == Mode.BLACK_VS_COMPUTER ? "Computer (" + level + ")" : "Player";
        String black = mode == Mode.WHITE_VS_COMPUTER ? "Computer (" + level + ")" : "Player";
        String pgn = game.toPgn(white, black);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(pgn), null);
        JOptionPane.showMessageDialog(this, "PGN copied to the clipboard.\nPaste it into lichess.org/paste to analyse the game.");
    }
}
