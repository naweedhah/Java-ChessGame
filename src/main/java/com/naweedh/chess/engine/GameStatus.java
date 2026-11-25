package com.naweedh.chess.engine;

public enum GameStatus {
    ONGOING(false, "Game in progress"),
    CHECKMATE(true, "Checkmate"),
    STALEMATE(true, "Draw by stalemate"),
    DRAW_FIFTY_MOVES(true, "Draw by the fifty-move rule"),
    DRAW_THREEFOLD_REPETITION(true, "Draw by threefold repetition"),
    DRAW_INSUFFICIENT_MATERIAL(true, "Draw by insufficient material");

    public final boolean gameOver;
    public final String description;

    GameStatus(boolean gameOver, String description) {
        this.gameOver = gameOver;
        this.description = description;
    }

    public boolean isDraw() {
        return gameOver && this != CHECKMATE;
    }

    /** Decides the status of a position under the standard FIDE rules. */
    public static GameStatus of(Position position) {
        if (MoveGenerator.legalMoves(position).isEmpty()) {
            return position.inCheck() ? CHECKMATE : STALEMATE;
        }
        if (position.halfmoveClock() >= 100) {
            return DRAW_FIFTY_MOVES;
        }
        if (position.repetitionCount() >= 3) {
            return DRAW_THREEFOLD_REPETITION;
        }
        if (insufficientMaterial(position)) {
            return DRAW_INSUFFICIENT_MATERIAL;
        }
        return ONGOING;
    }

    /** K vs K, K+minor vs K, and K+B vs K+B with bishops on the same colour. */
    static boolean insufficientMaterial(Position position) {
        int minors = 0;
        int lightBishops = 0;
        int darkBishops = 0;
        int knights = 0;
        for (int sq = 0; sq < 64; sq++) {
            Piece p = position.pieceAt(sq);
            if (p == null || p.type == PieceType.KING) {
                continue;
            }
            switch (p.type) {
                case BISHOP -> {
                    minors++;
                    if (Square.isLight(sq)) {
                        lightBishops++;
                    } else {
                        darkBishops++;
                    }
                }
                case KNIGHT -> {
                    minors++;
                    knights++;
                }
                default -> {
                    return false; // any pawn, rook or queen can still mate
                }
            }
        }
        if (minors <= 1) {
            return true;
        }
        // Only bishops, all on one square colour: no mate is possible.
        return knights == 0 && (lightBishops == 0 || darkBishops == 0);
    }
}
