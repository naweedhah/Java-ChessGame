package com.naweedh.chess.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesTest {

    @ParameterizedTest
    @ValueSource(strings = {
            Position.START_FEN,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3",
            "8/8/8/8/8/8/8/K6k b - - 42 99",
    })
    void fenRoundTrips(String fen) {
        assertEquals(fen, Position.fromFen(fen).toFen());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1",
            "rnbqkbnr/pppppppp/9/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            "rnbq1bnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1",
    })
    void rejectsMalformedFen(String fen) {
        assertThrows(IllegalArgumentException.class, () -> Position.fromFen(fen));
    }

    @Test
    void foolsMateIsCheckmateForBlack() {
        Game game = new Game();
        for (String san : new String[]{"f3", "e5", "g4", "Qh4#"}) {
            game.playSan(san);
        }
        assertEquals(GameStatus.CHECKMATE, game.status());
        assertEquals("0-1", game.result());
        assertEquals("Qh4#", game.sanMoves().get(3));
    }

    @Test
    void stalemateIsADraw() {
        Game game = new Game("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1");
        assertEquals(GameStatus.STALEMATE, game.status());
        assertEquals("1/2-1/2", game.result());
    }

    @Test
    void threefoldRepetitionIsDetected() {
        Game game = new Game();
        String[] shuffle = {"Nf3", "Nf6", "Ng1", "Ng8"};
        for (int i = 0; i < 2; i++) {
            for (String san : shuffle) {
                assertEquals(GameStatus.ONGOING, game.status());
                game.playSan(san);
            }
        }
        // The start position has now occurred three times.
        assertEquals(GameStatus.DRAW_THREEFOLD_REPETITION, game.status());
    }

    @Test
    void fiftyMoveRule() {
        assertEquals(GameStatus.DRAW_FIFTY_MOVES, new Game("8/8/4k3/8/8/4K3/8/R7 w - - 100 80").status());
        assertEquals(GameStatus.ONGOING, new Game("8/8/4k3/8/8/4K3/8/R7 w - - 99 80").status());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "8/8/4k3/8/8/4K3/8/8 w - - 0 1",      // K v K
            "8/8/4k3/8/8/4K3/8/5N2 w - - 0 1",    // K+N v K
            "8/8/4k3/8/8/4K3/8/2B5 w - - 0 1",    // K+B v K
            "8/8/4k3/2b5/8/4K3/8/2B5 w - - 0 1",  // bishops on the same colour
    })
    void insufficientMaterialIsADraw(String fen) {
        assertEquals(GameStatus.DRAW_INSUFFICIENT_MATERIAL, new Game(fen).status());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "8/8/4k3/8/8/4K3/8/4N1N1 w - - 0 1",  // two knights can still (help)mate
            "8/8/4k3/3b4/8/4K3/8/2B5 w - - 0 1",  // opposite-coloured bishops
            "8/8/4k3/8/8/4K3/4P3/8 w - - 0 1",    // a pawn can promote
    })
    void sufficientMaterialPlaysOn(String fen) {
        assertEquals(GameStatus.ONGOING, new Game(fen).status());
    }

    @Test
    void cannotCastleThroughCheck() {
        // Black rook on f8 attacks f1, which the king must cross to castle short.
        Game game = new Game("4kr2/8/8/8/8/8/8/R3K2R w KQ - 0 1");
        assertTrue(game.findMove(Square.parse("e1"), Square.parse("g1"), null).isEmpty());
        assertTrue(game.findMove(Square.parse("e1"), Square.parse("c1"), null).isPresent());
    }

    @Test
    void enPassantIsIllegalWhenItExposesTheKing() {
        // Capturing e.p. would remove both pawns from rank 5 and open the h5 rook onto the a5 king.
        Game game = new Game("8/8/8/KPp4r/8/8/8/7k w - c6 0 2");
        assertTrue(game.findMove(Square.parse("b5"), Square.parse("c6"), null).isEmpty());
    }

    @Test
    void underPromotionIsAvailable() {
        Game game = new Game("8/P6k/8/8/8/8/8/K7 w - - 0 1");
        assertEquals(4, game.legalMovesFrom(Square.parse("a7")).size());
        game.play(game.findMove(Square.parse("a7"), Square.parse("a8"), PieceType.KNIGHT).orElseThrow());
        assertEquals(PieceType.KNIGHT, game.position().pieceAt(Square.parse("a8")).type);
    }

    @Test
    void illegalMovesAreRejected() {
        Game game = new Game();
        assertThrows(IllegalArgumentException.class, () -> game.play(new Move(Square.parse("e2"), Square.parse("e5"))));
        assertFalse(game.canUndo());
    }

    @Test
    void undoRestoresTheExactPosition() {
        Game game = new Game();
        String before = game.position().toFen();
        game.playSan("e4");
        game.playSan("d5");
        game.playSan("exd5");
        game.undo();
        game.undo();
        game.undo();
        assertEquals(before, game.position().toFen());
        assertEquals(0, game.sanMoves().size());
    }
}
