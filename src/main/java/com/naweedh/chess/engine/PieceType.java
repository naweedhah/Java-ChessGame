package com.naweedh.chess.engine;

public enum PieceType {
    PAWN('P', 100),
    KNIGHT('N', 320),
    BISHOP('B', 330),
    ROOK('R', 500),
    QUEEN('Q', 900),
    KING('K', 0);

    /** Upper-case letter used in FEN and SAN. */
    public final char letter;
    /** Material value in centipawns, used by the evaluator and move ordering. */
    public final int value;

    PieceType(char letter, int value) {
        this.letter = letter;
        this.value = value;
    }

    public static PieceType fromLetter(char c) {
        for (PieceType type : values()) {
            if (type.letter == Character.toUpperCase(c)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown piece letter: " + c);
    }
}
