package com.naweedh.chess.engine;

/** The two players. */
public enum Side {
    WHITE, BLACK;

    public Side opposite() {
        return this == WHITE ? BLACK : WHITE;
    }

    /** Direction pawns of this side move along ranks: +1 for White, -1 for Black. */
    public int forward() {
        return this == WHITE ? 1 : -1;
    }
}
