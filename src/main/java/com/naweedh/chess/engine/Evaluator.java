package com.naweedh.chess.engine;

/**
 * Static evaluation in centipawns from the side-to-move's point of view.
 *
 * Material plus piece-square tables (the "simplified evaluation function" tables): knights
 * prefer the centre, pawns are rewarded for advancing, the king prefers shelter in the
 * middlegame and the centre in the endgame. Simple, but enough for sensible, human-like play.
 */
public final class Evaluator {

    private Evaluator() {
    }

    // Tables are written from White's view with rank 8 on the first line, as on a diagram.
    private static final int[] PAWN = {
            0, 0, 0, 0, 0, 0, 0, 0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
            5, 5, 10, 25, 25, 10, 5, 5,
            0, 0, 0, 20, 20, 0, 0, 0,
            5, -5, -10, 0, 0, -10, -5, 5,
            5, 10, 10, -20, -20, 10, 10, 5,
            0, 0, 0, 0, 0, 0, 0, 0};
    private static final int[] KNIGHT = {
            -50, -40, -30, -30, -30, -30, -40, -50,
            -40, -20, 0, 0, 0, 0, -20, -40,
            -30, 0, 10, 15, 15, 10, 0, -30,
            -30, 5, 15, 20, 20, 15, 5, -30,
            -30, 0, 15, 20, 20, 15, 0, -30,
            -30, 5, 10, 15, 15, 10, 5, -30,
            -40, -20, 0, 5, 5, 0, -20, -40,
            -50, -40, -30, -30, -30, -30, -40, -50};
    private static final int[] BISHOP = {
            -20, -10, -10, -10, -10, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 10, 10, 5, 0, -10,
            -10, 5, 5, 10, 10, 5, 5, -10,
            -10, 0, 10, 10, 10, 10, 0, -10,
            -10, 10, 10, 10, 10, 10, 10, -10,
            -10, 5, 0, 0, 0, 0, 5, -10,
            -20, -10, -10, -10, -10, -10, -10, -20};
    private static final int[] ROOK = {
            0, 0, 0, 0, 0, 0, 0, 0,
            5, 10, 10, 10, 10, 10, 10, 5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            0, 0, 0, 5, 5, 0, 0, 0};
    private static final int[] QUEEN = {
            -20, -10, -10, -5, -5, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 5, 5, 5, 0, -10,
            -5, 0, 5, 5, 5, 5, 0, -5,
            0, 0, 5, 5, 5, 5, 0, -5,
            -10, 5, 5, 5, 5, 5, 0, -10,
            -10, 0, 5, 0, 0, 0, 0, -10,
            -20, -10, -10, -5, -5, -10, -10, -20};
    private static final int[] KING_MIDDLEGAME = {
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -20, -30, -30, -40, -40, -30, -30, -20,
            -10, -20, -20, -20, -20, -20, -20, -10,
            20, 20, 0, 0, 0, 0, 20, 20,
            20, 30, 10, 0, 0, 10, 30, 20};
    private static final int[] KING_ENDGAME = {
            -50, -40, -30, -20, -20, -30, -40, -50,
            -30, -20, -10, 0, 0, -10, -20, -30,
            -30, -10, 20, 30, 30, 20, -10, -30,
            -30, -10, 30, 40, 40, 30, -10, -30,
            -30, -10, 30, 40, 40, 30, -10, -30,
            -30, -10, 20, 30, 30, 20, -10, -30,
            -30, -30, 0, 0, 0, 0, -30, -30,
            -50, -30, -30, -30, -30, -30, -30, -50};

    /** Score of the position for the side to move (positive = good for them). */
    public static int evaluate(Position position) {
        boolean endgame = isEndgame(position);
        int score = 0;
        for (int sq = 0; sq < 64; sq++) {
            Piece p = position.pieceAt(sq);
            if (p == null) {
                continue;
            }
            int value = p.type.value + tableValue(p, sq, endgame);
            score += p.side == Side.WHITE ? value : -value;
        }
        return position.sideToMove() == Side.WHITE ? score : -score;
    }

    private static int tableValue(Piece p, int square, boolean endgame) {
        // Tables list rank 8 first; mirror the rank for White so a1 maps to the last row.
        int file = Square.file(square);
        int rank = Square.rank(square);
        int index = p.side == Side.WHITE ? (7 - rank) * 8 + file : rank * 8 + file;
        return switch (p.type) {
            case PAWN -> PAWN[index];
            case KNIGHT -> KNIGHT[index];
            case BISHOP -> BISHOP[index];
            case ROOK -> ROOK[index];
            case QUEEN -> QUEEN[index];
            case KING -> endgame ? KING_ENDGAME[index] : KING_MIDDLEGAME[index];
        };
    }

    /** Endgame once queens are off, or each side has at most a queen and one minor piece. */
    private static boolean isEndgame(Position position) {
        int nonPawnMaterial = 0;
        boolean queens = false;
        for (int sq = 0; sq < 64; sq++) {
            Piece p = position.pieceAt(sq);
            if (p == null || p.type == PieceType.PAWN || p.type == PieceType.KING) {
                continue;
            }
            if (p.type == PieceType.QUEEN) {
                queens = true;
            }
            nonPawnMaterial += p.type.value;
        }
        return !queens || nonPawnMaterial <= 2 * (PieceType.QUEEN.value + PieceType.BISHOP.value);
    }
}
