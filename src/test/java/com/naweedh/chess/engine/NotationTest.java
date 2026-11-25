package com.naweedh.chess.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotationTest {

    private static String san(String fen, String from, String to) {
        Game game = new Game(fen);
        Move move = game.findMove(Square.parse(from), Square.parse(to), null).orElseThrow();
        return San.of(game.position(), move);
    }

    @Test
    void basicMovesCapturesAndCastling() {
        assertEquals("e4", san(Position.START_FEN, "e2", "e4"));
        assertEquals("Nf3", san(Position.START_FEN, "g1", "f3"));
        assertEquals("exd5", san("rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2", "e4", "d5"));
        assertEquals("O-O", san("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "e1", "g1"));
        assertEquals("O-O-O", san("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "e1", "c1"));
    }

    @Test
    void disambiguatesByFileThenRankThenSquare() {
        // Knights on b1 and f1 can both reach d2.
        assertEquals("Nbd2", san("4k3/8/8/8/8/8/8/1N2KN2 w - - 0 1", "b1", "d2"));
        // Rooks on a1 and a5 can both reach a3.
        assertEquals("R1a3", san("4k3/8/8/R7/8/8/8/R3K3 w - - 0 1", "a1", "a3"));
        // Queens on h4, h1 and e4 can all reach e1; h4 shares a file with h1 and a rank with e4.
        assertEquals("Qh4e1", san("8/8/1k6/8/4Q2Q/8/8/K6Q w - - 0 1", "h4", "e1"));
    }

    @Test
    void checkMateAndPromotionSuffixes() {
        assertEquals("Qh4#", san("rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq g3 0 2", "d8", "h4"));
        assertEquals("a8=Q+", san("7k/P7/8/8/8/8/8/K7 w - - 0 1", "a7", "a8"));
    }

    @Test
    void exportsPgn() {
        Game game = new Game();
        for (String move : new String[]{"e4", "e5", "Qh5", "Nc6", "Bc4", "Nf6", "Qxf7#"}) {
            game.playSan(move);
        }
        String pgn = game.toPgn("Alice", "Bob");

        assertTrue(pgn.contains("[Result \"1-0\"]"));
        assertTrue(pgn.contains("1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7# 1-0"), pgn);
    }
}
