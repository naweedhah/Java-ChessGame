package com.naweedh.chess.engine;

import java.util.Comparator;
import java.util.List;

/**
 * The computer opponent: negamax with alpha-beta pruning, iterative deepening and a
 * quiescence search.
 *
 * <ul>
 *   <li><b>Alpha-beta</b> skips branches that cannot change the result, so good move ordering
 *       (captures of valuable pieces by cheap ones first, MVV-LVA, and the previous iteration's
 *       best move first) roughly doubles the reachable depth.</li>
 *   <li><b>Iterative deepening</b> searches depth 1, 2, 3... until the time budget runs out and
 *       keeps the best move from the deepest completed iteration.</li>
 *   <li><b>Quiescence search</b> keeps following captures at the leaves, so the engine does not
 *       stop halfway through an exchange and misjudge it (the "horizon effect").</li>
 * </ul>
 */
public final class Search {

    public static final int MATE = 1_000_000;
    private static final int INFINITY = 2 * MATE;

    private final int maxDepth;
    private final long timeLimitMillis;

    private long deadline;
    private boolean outOfTime;
    private long nodes;
    private Move rootBest;

    /** Result of a search: the move, its score for the side to move, and some statistics. */
    public record Result(Move bestMove, int score, int depthReached, long nodes) {
    }

    public Search(int maxDepth, long timeLimitMillis) {
        this.maxDepth = maxDepth;
        this.timeLimitMillis = timeLimitMillis;
    }

    /** Difficulty presets used by the GUI. */
    public static Search forLevel(Level level) {
        return new Search(level.depth, level.millis);
    }

    public enum Level {
        EASY(2, 300), MEDIUM(4, 1000), HARD(6, 3000);

        final int depth;
        final long millis;

        Level(int depth, long millis) {
            this.depth = depth;
            this.millis = millis;
        }
    }

    public Result findBestMove(Position original) {
        Position position = original.copy(); // never touch the caller's (e.g. the GUI's) position
        List<Move> rootMoves = MoveGenerator.legalMoves(position);
        if (rootMoves.isEmpty()) {
            return new Result(null, position.inCheck() ? -MATE : 0, 0, 0);
        }

        deadline = System.currentTimeMillis() + timeLimitMillis;
        outOfTime = false;
        nodes = 0;
        Move best = rootMoves.get(0);
        int bestScore = -INFINITY;
        int completedDepth = 0;

        for (int depth = 1; depth <= maxDepth; depth++) {
            rootBest = null;
            int score = negamax(position, depth, 0, -INFINITY, INFINITY, best);
            if (outOfTime && depth > 1) {
                break; // the unfinished iteration is unreliable, keep the previous one
            }
            if (rootBest != null) {
                best = rootBest;
                bestScore = score;
                completedDepth = depth;
            }
            if (Math.abs(score) >= MATE - maxDepth) {
                break; // a forced mate was found; searching deeper will not improve it
            }
        }
        return new Result(best, bestScore, completedDepth, nodes);
    }

    private int negamax(Position position, int depth, int ply, int alpha, int beta, Move hashMove) {
        if ((++nodes & 1023) == 0 && System.currentTimeMillis() > deadline) {
            outOfTime = true;
        }
        if (outOfTime && ply > 0) {
            return 0;
        }
        if (ply > 0 && (position.halfmoveClock() >= 100 || position.repetitionCount() >= 2)) {
            return 0; // draw (a repetition inside the search is treated as a draw)
        }

        List<Move> moves = MoveGenerator.legalMoves(position);
        if (moves.isEmpty()) {
            // Prefer faster mates and slower losses.
            return position.inCheck() ? -MATE + ply : 0;
        }
        if (depth == 0) {
            return quiescence(position, alpha, beta, ply);
        }

        order(position, moves, ply == 0 ? hashMove : null);
        int best = -INFINITY;
        for (Move move : moves) {
            position.makeMove(move);
            int score = -negamax(position, depth - 1, ply + 1, -beta, -alpha, null);
            position.unmakeMove();
            if (outOfTime && ply > 0) {
                return 0;
            }
            if (score > best) {
                best = score;
                if (ply == 0) {
                    rootBest = move;
                }
            }
            alpha = Math.max(alpha, score);
            if (alpha >= beta) {
                break; // the opponent will never allow this line
            }
        }
        return best;
    }

    private int quiescence(Position position, int alpha, int beta, int ply) {
        nodes++;
        int standPat = Evaluator.evaluate(position);
        if (standPat >= beta) {
            return standPat;
        }
        alpha = Math.max(alpha, standPat);

        List<Move> noisy = MoveGenerator.legalNoisyMoves(position);
        order(position, noisy, null);
        for (Move move : noisy) {
            position.makeMove(move);
            int score = -quiescence(position, -beta, -alpha, ply + 1);
            position.unmakeMove();
            if (score >= beta) {
                return score;
            }
            alpha = Math.max(alpha, score);
        }
        return alpha;
    }

    /** Best-first ordering: previous best move, then MVV-LVA captures, then promotions, then quiet moves. */
    private static void order(Position position, List<Move> moves, Move first) {
        moves.sort(Comparator.comparingInt((Move m) -> -orderScore(position, m, first)));
    }

    private static int orderScore(Position position, Move move, Move first) {
        if (move.equals(first)) {
            return 1_000_000;
        }
        int score = 0;
        if (move.isCapture()) {
            Piece victim = move.isEnPassant() ? Piece.of(Side.WHITE, PieceType.PAWN) : position.pieceAt(move.to());
            Piece attacker = position.pieceAt(move.from());
            score += 10_000 + 10 * victim.type.value - attacker.type.value;
        }
        if (move.promotion() != null) {
            score += 8_000 + move.promotion().value;
        }
        return score;
    }
}
