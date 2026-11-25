package com.naweedh.chess.engine;

/**
 * A move from one square to another. {@code promotion} is null unless a pawn promotes.
 * Flags describe moves whose side effects go beyond "piece goes from A to B".
 */
public record Move(int from, int to, PieceType promotion, int flags) {

    public static final int QUIET = 0;
    public static final int CAPTURE = 1;
    public static final int DOUBLE_PAWN_PUSH = 1 << 1;
    public static final int EN_PASSANT = 1 << 2;
    public static final int CASTLE = 1 << 3;

    public Move(int from, int to) {
        this(from, to, null, QUIET);
    }

    public boolean isCapture() {
        return (flags & CAPTURE) != 0;
    }

    public boolean isEnPassant() {
        return (flags & EN_PASSANT) != 0;
    }

    public boolean isCastle() {
        return (flags & CASTLE) != 0;
    }

    public boolean isDoublePawnPush() {
        return (flags & DOUBLE_PAWN_PUSH) != 0;
    }

    /** Long algebraic / UCI form, e.g. "e2e4" or "e7e8q". */
    public String uci() {
        String s = Square.name(from) + Square.name(to);
        return promotion == null ? s : s + Character.toLowerCase(promotion.letter);
    }

    @Override
    public String toString() {
        return uci();
    }
}
