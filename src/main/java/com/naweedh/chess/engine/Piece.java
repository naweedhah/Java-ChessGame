package com.naweedh.chess.engine;

/**
 * An immutable piece. There are only twelve, so they are cached and compared by identity.
 */
public final class Piece {

    private static final Piece[][] CACHE = new Piece[2][PieceType.values().length];

    static {
        for (Side side : Side.values()) {
            for (PieceType type : PieceType.values()) {
                CACHE[side.ordinal()][type.ordinal()] = new Piece(side, type);
            }
        }
    }

    public final Side side;
    public final PieceType type;

    private Piece(Side side, PieceType type) {
        this.side = side;
        this.type = type;
    }

    public static Piece of(Side side, PieceType type) {
        return CACHE[side.ordinal()][type.ordinal()];
    }

    /** FEN letter: upper case for White, lower case for Black. */
    public char fenChar() {
        return side == Side.WHITE ? type.letter : Character.toLowerCase(type.letter);
    }

    public static Piece fromFenChar(char c) {
        return of(Character.isUpperCase(c) ? Side.WHITE : Side.BLACK, PieceType.fromLetter(c));
    }

    /** Index 0..11 used for Zobrist hashing. */
    int index() {
        return side.ordinal() * 6 + type.ordinal();
    }

    @Override
    public String toString() {
        return String.valueOf(fenChar());
    }
}
