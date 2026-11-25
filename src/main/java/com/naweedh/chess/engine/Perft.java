package com.naweedh.chess.engine;

/**
 * Performance test ("perft"): counts every leaf node of the legal move tree to a fixed depth.
 * Comparing the counts with published values is the standard way to prove a move generator
 * handles castling, en passant, promotions, pins and checks correctly.
 */
public final class Perft {

    private Perft() {
    }

    public static long perft(Position position, int depth) {
        if (depth == 0) {
            return 1;
        }
        var moves = MoveGenerator.legalMoves(position);
        if (depth == 1) {
            return moves.size(); // bulk counting: no need to play the last ply
        }
        long nodes = 0;
        for (Move move : moves) {
            position.makeMove(move);
            nodes += perft(position, depth - 1);
            position.unmakeMove();
        }
        return nodes;
    }
}
