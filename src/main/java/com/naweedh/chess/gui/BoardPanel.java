package com.naweedh.chess.gui;

import com.naweedh.chess.engine.Game;
import com.naweedh.chess.engine.Move;
import com.naweedh.chess.engine.Piece;
import com.naweedh.chess.engine.PieceType;
import com.naweedh.chess.engine.Square;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Draws the board and turns mouse input into "move from A to B" requests. It never decides
 * whether a move is legal: it asks the {@link Game} for legal targets and hands the chosen
 * squares to the window, which owns the game flow.
 */
final class BoardPanel extends JPanel {

    static final int SQUARE = 80;

    private static final Color LIGHT = new Color(240, 217, 181);
    private static final Color DARK = new Color(181, 136, 99);
    private static final Color LAST_MOVE = new Color(205, 210, 106, 170);
    private static final Color SELECTED = new Color(130, 151, 105, 200);
    private static final Color CHECK = new Color(220, 50, 50, 170);
    private static final Color HINT = new Color(20, 85, 30, 110);

    private final PieceImages images = new PieceImages();
    private final BiConsumer<Integer, Integer> onMove;

    private Game game;
    private boolean flipped;
    private boolean interactive = true;

    private int selected = Square.NONE;
    private List<Move> targets = List.of();
    private Point dragPoint; // non-null while a piece is being dragged

    BoardPanel(Game game, BiConsumer<Integer, Integer> onMove) {
        this.game = game;
        this.onMove = onMove;
        setPreferredSize(new Dimension(8 * SQUARE, 8 * SQUARE));

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                pressed(squareAt(e.getPoint()));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (selected != Square.NONE) {
                    dragPoint = e.getPoint();
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragPoint != null) {
                    dragPoint = null;
                    int target = squareAt(e.getPoint());
                    if (target != selected && isTarget(target)) {
                        moveTo(target);
                    }
                    repaint();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    void setGame(Game game) {
        this.game = game;
        clearSelection();
    }

    void setFlipped(boolean flipped) {
        this.flipped = flipped;
        repaint();
    }

    boolean isFlipped() {
        return flipped;
    }

    /** Disabled while the computer is thinking or the game is over. */
    void setInteractive(boolean interactive) {
        this.interactive = interactive;
        if (!interactive) {
            clearSelection();
        }
    }

    void clearSelection() {
        selected = Square.NONE;
        targets = List.of();
        dragPoint = null;
        repaint();
    }

    private void pressed(int square) {
        if (!interactive || square == Square.NONE) {
            return;
        }
        if (selected != Square.NONE && isTarget(square)) {
            moveTo(square); // click-click movement
            return;
        }
        Piece piece = game.position().pieceAt(square);
        if (piece != null && piece.side == game.sideToMove()) {
            selected = square;
            targets = game.legalMovesFrom(square);
        } else {
            clearSelection();
        }
        repaint();
    }

    private void moveTo(int target) {
        int from = selected;
        clearSelection();
        onMove.accept(from, target);
    }

    private boolean isTarget(int square) {
        return targets.stream().anyMatch(m -> m.to() == square);
    }

    private int squareAt(Point p) {
        int col = p.x / SQUARE;
        int row = p.y / SQUARE;
        if (col < 0 || col > 7 || row < 0 || row > 7) {
            return Square.NONE;
        }
        return flipped ? Square.of(7 - col, row) : Square.of(col, 7 - row);
    }

    private Point topLeft(int square) {
        int file = Square.file(square);
        int rank = Square.rank(square);
        return flipped
                ? new Point((7 - file) * SQUARE, rank * SQUARE)
                : new Point(file * SQUARE, (7 - rank) * SQUARE);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int checkedKing = game.position().inCheck() ? game.position().kingSquare(game.sideToMove()) : Square.NONE;
        Move last = game.lastMove().orElse(null);

        for (int sq = 0; sq < 64; sq++) {
            Point p = topLeft(sq);
            g.setColor(Square.isLight(sq) ? LIGHT : DARK);
            g.fillRect(p.x, p.y, SQUARE, SQUARE);
            if (last != null && (sq == last.from() || sq == last.to())) {
                fill(g, p, LAST_MOVE);
            }
            if (sq == selected) {
                fill(g, p, SELECTED);
            }
            if (sq == checkedKing) {
                fill(g, p, CHECK);
            }
        }
        drawCoordinates(g);

        for (int sq = 0; sq < 64; sq++) {
            Piece piece = game.position().pieceAt(sq);
            if (piece != null && !(dragPoint != null && sq == selected)) {
                Point p = topLeft(sq);
                g.drawImage(images.get(piece), p.x, p.y, SQUARE, SQUARE, null);
            }
        }

        // Legal-move hints: dots on empty squares, rings around capturable pieces.
        g.setColor(HINT);
        for (Move move : targets) {
            if (move.promotion() != null && move.promotion() != PieceType.QUEEN) {
                continue; // one hint per square is enough
            }
            Point p = topLeft(move.to());
            if (move.isCapture()) {
                g.setStroke(new BasicStroke(6));
                g.drawOval(p.x + 4, p.y + 4, SQUARE - 8, SQUARE - 8);
            } else {
                int d = SQUARE / 4;
                g.fillOval(p.x + (SQUARE - d) / 2, p.y + (SQUARE - d) / 2, d, d);
            }
        }

        if (dragPoint != null && selected != Square.NONE) {
            Piece piece = game.position().pieceAt(selected);
            g.drawImage(images.get(piece), dragPoint.x - SQUARE / 2, dragPoint.y - SQUARE / 2, SQUARE, SQUARE, null);
        }
    }

    private static void fill(Graphics2D g, Point p, Color color) {
        g.setColor(color);
        g.fillRect(p.x, p.y, SQUARE, SQUARE);
    }

    private void drawCoordinates(Graphics2D g) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        for (int i = 0; i < 8; i++) {
            int file = flipped ? 7 - i : i;
            int rank = flipped ? i : 7 - i;
            // Files along the bottom edge, ranks along the left edge, in the contrasting colour.
            g.setColor(Square.isLight(Square.of(file, flipped ? 7 : 0)) ? DARK : LIGHT);
            g.drawString(String.valueOf((char) ('a' + file)), i * SQUARE + SQUARE - 12, 8 * SQUARE - 5);
            g.setColor(Square.isLight(Square.of(flipped ? 7 : 0, rank)) ? DARK : LIGHT);
            g.drawString(String.valueOf(rank + 1), 4, i * SQUARE + 14);
        }
    }
}
