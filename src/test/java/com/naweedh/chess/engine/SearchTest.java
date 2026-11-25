package com.naweedh.chess.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchTest {

    private static Move best(String fen, int depth) {
        return new Search(depth, 10_000).findBestMove(Position.fromFen(fen)).bestMove();
    }

    @Test
    void findsMateInOne() {
        // Back-rank mate: Ra8#.
        assertEquals("a1a8", best("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1", 3).uci());
    }

    @Test
    void findsMateInTwo() {
        // 1. Nf6+ gxf6 2. Bxf7# (a classic two-move combination).
        Search.Result result = new Search(4, 10_000).findBestMove(
                Position.fromFen("r2qkb1r/pp2nppp/3p4/2pNN1B1/2BnP3/3P4/PPP2PPP/R2bK2R w KQkq - 1 1"));
        assertEquals("d5f6", result.bestMove().uci());
        assertTrue(result.score() >= Search.MATE - 4, "should see the forced mate, got " + result.score());
    }

    @Test
    void winsAHangingQueen() {
        assertEquals("d1d8", best("3q2k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1", 2).uci());
    }

    @Test
    void doesNotGiveAwayItsQueen() {
        // Qxf7+?? wins a pawn but loses the queen to Kxf7.
        Move move = best("rnbqkbnr/pppp1ppp/8/4p3/4P3/5Q2/PPPP1PPP/RNB1KBNR w KQkq - 0 2", 3);
        assertTrue(!move.uci().equals("f3f7"), "engine played the losing Qxf7+");
    }

    @Test
    @Timeout(10)
    void respectsTheTimeLimitAndReturnsALegalMove() {
        Position position = Position.start();
        Search.Result result = new Search(99, 500).findBestMove(position);

        assertTrue(MoveGenerator.legalMoves(position).contains(result.bestMove()));
        assertTrue(result.depthReached() >= 1);
        assertEquals(Position.START_FEN, position.toFen(), "search must not modify the caller's position");
    }
}
