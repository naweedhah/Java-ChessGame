package com.naweedh.chess.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates legal moves.
 *
 * First every pseudo-legal move is produced (moves that follow each piece's movement rules), then
 * each one is played on the board and kept only if it does not leave the mover's own king in
 * check. That one rule covers pins, moving into check, and answering check, without special
 * cases. Correctness is verified by perft tests against published node counts.
 */
public final class MoveGenerator {

    private static final PieceType[] PROMOTIONS = {PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT};

    private MoveGenerator() {
    }

    public static List<Move> legalMoves(Position position) {
        Side us = position.sideToMove();
        List<Move> pseudo = pseudoLegalMoves(position);
        List<Move> legal = new ArrayList<>(pseudo.size());
        for (Move move : pseudo) {
            position.makeMove(move);
            if (!position.inCheck(us)) {
                legal.add(move);
            }
            position.unmakeMove();
        }
        return legal;
    }

    /** Legal captures and promotions only, for the quiescence search. */
    public static List<Move> legalNoisyMoves(Position position) {
        List<Move> noisy = new ArrayList<>();
        for (Move move : legalMoves(position)) {
            if (move.isCapture() || move.promotion() != null) {
                noisy.add(move);
            }
        }
        return noisy;
    }

    static List<Move> pseudoLegalMoves(Position position) {
        List<Move> moves = new ArrayList<>(48);
        Side us = position.sideToMove();
        for (int sq = 0; sq < 64; sq++) {
            Piece piece = position.pieceAt(sq);
            if (piece == null || piece.side != us) {
                continue;
            }
            switch (piece.type) {
                case PAWN -> pawnMoves(position, sq, us, moves);
                case KNIGHT -> stepMoves(position, sq, us, Position.knightSteps(), moves);
                case BISHOP -> slideMoves(position, sq, us, Position.DIAGONAL, moves);
                case ROOK -> slideMoves(position, sq, us, Position.ORTHOGONAL, moves);
                case QUEEN -> {
                    slideMoves(position, sq, us, Position.ORTHOGONAL, moves);
                    slideMoves(position, sq, us, Position.DIAGONAL, moves);
                }
                case KING -> {
                    stepMoves(position, sq, us, Position.kingSteps(), moves);
                    castlingMoves(position, sq, us, moves);
                }
            }
        }
        return moves;
    }

    private static void pawnMoves(Position position, int from, Side us, List<Move> moves) {
        int file = Square.file(from);
        int rank = Square.rank(from);
        int dir = us.forward();
        int startRank = us == Side.WHITE ? 1 : 6;
        int lastRank = us == Side.WHITE ? 7 : 0;

        // Pushes
        int oneAhead = Square.of(file, rank + dir);
        if (position.pieceAt(oneAhead) == null) {
            addPawnMove(from, oneAhead, Move.QUIET, rank + dir == lastRank, moves);
            int twoAhead = Square.of(file, rank + 2 * dir);
            if (rank == startRank && position.pieceAt(twoAhead) == null) {
                moves.add(new Move(from, twoAhead, null, Move.DOUBLE_PAWN_PUSH));
            }
        }

        // Captures, including en passant
        for (int df : new int[]{-1, 1}) {
            if (!Square.onBoard(file + df, rank + dir)) {
                continue;
            }
            int to = Square.of(file + df, rank + dir);
            Piece target = position.pieceAt(to);
            if (target != null && target.side != us) {
                addPawnMove(from, to, Move.CAPTURE, rank + dir == lastRank, moves);
            } else if (to == position.enPassantSquare()) {
                moves.add(new Move(from, to, null, Move.CAPTURE | Move.EN_PASSANT));
            }
        }
    }

    private static void addPawnMove(int from, int to, int flags, boolean promotes, List<Move> moves) {
        if (promotes) {
            for (PieceType type : PROMOTIONS) {
                moves.add(new Move(from, to, type, flags));
            }
        } else {
            moves.add(new Move(from, to, null, flags));
        }
    }

    private static void stepMoves(Position position, int from, Side us, int[][] steps, List<Move> moves) {
        int file = Square.file(from);
        int rank = Square.rank(from);
        for (int[] s : steps) {
            if (!Square.onBoard(file + s[0], rank + s[1])) {
                continue;
            }
            int to = Square.of(file + s[0], rank + s[1]);
            Piece target = position.pieceAt(to);
            if (target == null) {
                moves.add(new Move(from, to, null, Move.QUIET));
            } else if (target.side != us) {
                moves.add(new Move(from, to, null, Move.CAPTURE));
            }
        }
    }

    private static void slideMoves(Position position, int from, Side us, int[][] directions, List<Move> moves) {
        for (int[] d : directions) {
            int f = Square.file(from) + d[0];
            int r = Square.rank(from) + d[1];
            while (Square.onBoard(f, r)) {
                int to = Square.of(f, r);
                Piece target = position.pieceAt(to);
                if (target == null) {
                    moves.add(new Move(from, to, null, Move.QUIET));
                } else {
                    if (target.side != us) {
                        moves.add(new Move(from, to, null, Move.CAPTURE));
                    }
                    break;
                }
                f += d[0];
                r += d[1];
            }
        }
    }

    /**
     * Castling needs: the right still available, empty squares between king and rook, and a king
     * that is not in check and does not pass through or land on an attacked square.
     */
    private static void castlingMoves(Position position, int from, Side us, List<Move> moves) {
        int rank = us == Side.WHITE ? 0 : 7;
        if (from != Square.of(4, rank)) {
            return;
        }
        Side them = us.opposite();
        int kingside = us == Side.WHITE ? Position.WHITE_KINGSIDE : Position.BLACK_KINGSIDE;
        int queenside = us == Side.WHITE ? Position.WHITE_QUEENSIDE : Position.BLACK_QUEENSIDE;

        if (position.hasRight(kingside)
                && empty(position, rank, 5, 6)
                && !position.isAttacked(from, them)
                && !position.isAttacked(Square.of(5, rank), them)
                && !position.isAttacked(Square.of(6, rank), them)) {
            moves.add(new Move(from, Square.of(6, rank), null, Move.CASTLE));
        }
        if (position.hasRight(queenside)
                && empty(position, rank, 1, 2, 3)
                && !position.isAttacked(from, them)
                && !position.isAttacked(Square.of(3, rank), them)
                && !position.isAttacked(Square.of(2, rank), them)) {
            moves.add(new Move(from, Square.of(2, rank), null, Move.CASTLE));
        }
    }

    private static boolean empty(Position position, int rank, int... files) {
        for (int file : files) {
            if (position.pieceAt(Square.of(file, rank)) != null) {
                return false;
            }
        }
        return true;
    }
}
