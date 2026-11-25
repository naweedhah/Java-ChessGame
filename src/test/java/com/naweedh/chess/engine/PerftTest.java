package com.naweedh.chess.engine;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Leaf-node counts for the standard perft test positions (chessprogramming.org/Perft_Results).
 * A single wrong rule anywhere (castling through check, en passant exposing the king,
 * under-promotion, pinned pieces...) changes these numbers.
 */
class PerftTest {

    private static final String START = Position.START_FEN;
    private static final String KIWIPETE = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
    private static final String POSITION_3 = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1";
    private static final String POSITION_4 = "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1";
    private static final String POSITION_4_MIRRORED = "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1";
    private static final String POSITION_5 = "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8";

    @ParameterizedTest(name = "{0} depth {1} = {2}")
    @CsvSource({
            "start, 1, 20",
            "start, 2, 400",
            "start, 3, 8902",
            "start, 4, 197281",
            "kiwipete, 1, 48",
            "kiwipete, 2, 2039",
            "kiwipete, 3, 97862",
            "position3, 1, 14",
            "position3, 2, 191",
            "position3, 3, 2812",
            "position3, 4, 43238",
            "position4, 1, 6",
            "position4, 2, 264",
            "position4, 3, 9467",
            "position4mirrored, 3, 9467",
            "position5, 1, 44",
            "position5, 2, 1486",
            "position5, 3, 62379",
    })
    void matchesPublishedNodeCounts(String name, int depth, long expected) {
        String fen = switch (name) {
            case "start" -> START;
            case "kiwipete" -> KIWIPETE;
            case "position3" -> POSITION_3;
            case "position4" -> POSITION_4;
            case "position4mirrored" -> POSITION_4_MIRRORED;
            case "position5" -> POSITION_5;
            default -> throw new IllegalArgumentException(name);
        };
        Position position = Position.fromFen(fen);

        assertEquals(expected, Perft.perft(position, depth));
        assertEquals(fen, position.toFen(), "make/unmake must restore the position exactly");
    }
}
