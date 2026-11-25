package com.naweedh.chess.engine;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A game in progress: the current position plus the move list in SAN, with undo and PGN export.
 * This is the only class the GUI talks to, which keeps all chess rules out of the Swing code.
 */
public final class Game {

    private final Position position;
    private final String startFen;
    private final List<Move> moves = new ArrayList<>();
    private final List<String> sanMoves = new ArrayList<>();

    public Game() {
        this(Position.START_FEN);
    }

    public Game(String fen) {
        this.startFen = fen;
        this.position = Position.fromFen(fen);
    }

    public Position position() {
        return position;
    }

    public Side sideToMove() {
        return position.sideToMove();
    }

    public List<Move> legalMoves() {
        return MoveGenerator.legalMoves(position);
    }

    /** Legal moves of the piece on {@code from}. */
    public List<Move> legalMovesFrom(int from) {
        return legalMoves().stream().filter(m -> m.from() == from).toList();
    }

    /** Finds the legal move between two squares (with the chosen promotion piece, if any). */
    public Optional<Move> findMove(int from, int to, PieceType promotion) {
        return legalMoves().stream()
                .filter(m -> m.from() == from && m.to() == to)
                .filter(m -> m.promotion() == null || m.promotion() == (promotion == null ? PieceType.QUEEN : promotion))
                .findFirst();
    }

    /** Plays a legal move and records it. Rejects anything illegal. */
    public void play(Move move) {
        if (status().gameOver) {
            throw new IllegalStateException("The game is over");
        }
        if (!legalMoves().contains(move)) {
            throw new IllegalArgumentException("Illegal move: " + move.uci());
        }
        sanMoves.add(San.of(position, move));
        position.makeMove(move);
        moves.add(move);
    }

    /** Plays a move written in SAN, e.g. "Nf3" or "exd5". Handy for tests and PGN import. */
    public Move playSan(String san) {
        String wanted = san.replace("+", "").replace("#", "");
        for (Move move : legalMoves()) {
            if (San.of(position, move).replace("+", "").replace("#", "").equals(wanted)) {
                play(move);
                return move;
            }
        }
        throw new IllegalArgumentException("Illegal or ambiguous move: " + san);
    }

    public boolean canUndo() {
        return !moves.isEmpty();
    }

    public void undo() {
        if (!canUndo()) {
            return;
        }
        position.unmakeMove();
        moves.remove(moves.size() - 1);
        sanMoves.remove(sanMoves.size() - 1);
    }

    public Optional<Move> lastMove() {
        return moves.isEmpty() ? Optional.empty() : Optional.of(moves.get(moves.size() - 1));
    }

    public List<String> sanMoves() {
        return Collections.unmodifiableList(sanMoves);
    }

    public GameStatus status() {
        return GameStatus.of(position);
    }

    /** "1-0", "0-1", "1/2-1/2" or "*" for a game still in progress. */
    public String result() {
        GameStatus status = status();
        if (status == GameStatus.CHECKMATE) {
            return position.sideToMove() == Side.WHITE ? "0-1" : "1-0";
        }
        return status.isDraw() ? "1/2-1/2" : "*";
    }

    /** The game in Portable Game Notation, ready to paste into any chess site. */
    public String toPgn(String white, String black) {
        StringBuilder pgn = new StringBuilder();
        pgn.append("[Event \"Casual game\"]\n");
        pgn.append("[Site \"Java Chess\"]\n");
        pgn.append("[Date \"").append(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))).append("\"]\n");
        pgn.append("[White \"").append(white).append("\"]\n");
        pgn.append("[Black \"").append(black).append("\"]\n");
        pgn.append("[Result \"").append(result()).append("\"]\n");
        if (!startFen.equals(Position.START_FEN)) {
            pgn.append("[SetUp \"1\"]\n[FEN \"").append(startFen).append("\"]\n");
        }
        pgn.append('\n');

        Position start = Position.fromFen(startFen);
        int moveNumber = start.fullmoveNumber();
        boolean whiteToMove = start.sideToMove() == Side.WHITE;
        for (int i = 0; i < sanMoves.size(); i++) {
            if (whiteToMove) {
                pgn.append(moveNumber).append(". ");
            } else if (i == 0) {
                pgn.append(moveNumber).append("... ");
            }
            pgn.append(sanMoves.get(i)).append(' ');
            if (!whiteToMove) {
                moveNumber++;
            }
            whiteToMove = !whiteToMove;
        }
        return pgn.append(result()).append('\n').toString();
    }
}
