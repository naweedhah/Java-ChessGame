package com.naweedh.chess.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * The full state of a chess game at one moment: piece placement, side to move, castling rights,
 * en passant square and move counters, i.e. everything a FEN string describes.
 *
 * Moves are applied with {@link #makeMove(Move)} and reverted with {@link #unmakeMove()}. The
 * search and the legality check use make/unmake on one mutable object instead of copying the
 * board for every move. A Zobrist hash is kept up to date for repetition detection.
 */
public final class Position {

    public static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    // Castling-right bits.
    public static final int WHITE_KINGSIDE = 1;
    public static final int WHITE_QUEENSIDE = 2;
    public static final int BLACK_KINGSIDE = 4;
    public static final int BLACK_QUEENSIDE = 8;

    /**
     * Castling rights that survive a move touching each square. Moving the king or a rook, or
     * capturing a rook on its home square, clears the matching rights.
     */
    private static final int[] CASTLING_MASK = new int[64];

    static {
        java.util.Arrays.fill(CASTLING_MASK, 15);
        CASTLING_MASK[Square.parse("e1")] = 15 & ~(WHITE_KINGSIDE | WHITE_QUEENSIDE);
        CASTLING_MASK[Square.parse("h1")] = 15 & ~WHITE_KINGSIDE;
        CASTLING_MASK[Square.parse("a1")] = 15 & ~WHITE_QUEENSIDE;
        CASTLING_MASK[Square.parse("e8")] = 15 & ~(BLACK_KINGSIDE | BLACK_QUEENSIDE);
        CASTLING_MASK[Square.parse("h8")] = 15 & ~BLACK_KINGSIDE;
        CASTLING_MASK[Square.parse("a8")] = 15 & ~BLACK_QUEENSIDE;
    }

    // Zobrist keys, fixed seed so hashes are reproducible between runs.
    private static final long[][] PIECE_KEYS = new long[12][64];
    private static final long[] CASTLING_KEYS = new long[16];
    private static final long[] EN_PASSANT_FILE_KEYS = new long[8];
    private static final long BLACK_TO_MOVE_KEY;

    static {
        Random random = new Random(0x5EED_C4E55L);
        for (long[] row : PIECE_KEYS) {
            for (int i = 0; i < 64; i++) {
                row[i] = random.nextLong();
            }
        }
        for (int i = 0; i < 16; i++) {
            CASTLING_KEYS[i] = random.nextLong();
        }
        for (int i = 0; i < 8; i++) {
            EN_PASSANT_FILE_KEYS[i] = random.nextLong();
        }
        BLACK_TO_MOVE_KEY = random.nextLong();
    }

    private final Piece[] board = new Piece[64];
    private Side sideToMove = Side.WHITE;
    private int castlingRights;
    private int enPassantSquare = Square.NONE;
    private int halfmoveClock;
    private int fullmoveNumber = 1;
    private long hash;

    /** Everything needed to undo one move. */
    private record Undo(Move move, Piece moved, Piece captured, int castlingRights, int enPassantSquare,
                        int halfmoveClock, long hash) {
    }

    private final Deque<Undo> history = new ArrayDeque<>();
    /** Hash of every position reached so far in this game, for repetition detection. */
    private final List<Long> hashHistory = new ArrayList<>();

    private Position() {
    }

    public static Position start() {
        return fromFen(START_FEN);
    }

    /* ---------------------------------------------------------------- FEN */

    public static Position fromFen(String fen) {
        String[] parts = fen.trim().split("\\s+");
        if (parts.length < 4) {
            throw new IllegalArgumentException("FEN needs at least 4 fields: " + fen);
        }
        Position p = new Position();

        String[] ranks = parts[0].split("/");
        if (ranks.length != 8) {
            throw new IllegalArgumentException("FEN board needs 8 ranks: " + fen);
        }
        for (int i = 0; i < 8; i++) {
            int rank = 7 - i;
            int file = 0;
            for (char c : ranks[i].toCharArray()) {
                if (Character.isDigit(c)) {
                    file += c - '0';
                } else {
                    if (file > 7) {
                        throw new IllegalArgumentException("Too many squares on rank " + (rank + 1));
                    }
                    p.board[Square.of(file, rank)] = Piece.fromFenChar(c);
                    file++;
                }
            }
            if (file != 8) {
                throw new IllegalArgumentException("Rank " + (rank + 1) + " does not have 8 squares: " + fen);
            }
        }

        p.sideToMove = switch (parts[1]) {
            case "w" -> Side.WHITE;
            case "b" -> Side.BLACK;
            default -> throw new IllegalArgumentException("Side to move must be w or b: " + fen);
        };

        if (!parts[2].equals("-")) {
            for (char c : parts[2].toCharArray()) {
                p.castlingRights |= switch (c) {
                    case 'K' -> WHITE_KINGSIDE;
                    case 'Q' -> WHITE_QUEENSIDE;
                    case 'k' -> BLACK_KINGSIDE;
                    case 'q' -> BLACK_QUEENSIDE;
                    default -> throw new IllegalArgumentException("Bad castling field: " + parts[2]);
                };
            }
        }

        p.enPassantSquare = parts[3].equals("-") ? Square.NONE : Square.parse(parts[3]);
        p.halfmoveClock = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
        p.fullmoveNumber = parts.length > 5 ? Integer.parseInt(parts[5]) : 1;

        if (p.kingSquare(Side.WHITE) == Square.NONE || p.kingSquare(Side.BLACK) == Square.NONE) {
            throw new IllegalArgumentException("Both sides need a king: " + fen);
        }

        p.hash = p.computeHash();
        p.hashHistory.add(p.hash);
        return p;
    }

    public String toFen() {
        StringBuilder sb = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                Piece piece = board[Square.of(file, rank)];
                if (piece == null) {
                    empty++;
                } else {
                    if (empty > 0) {
                        sb.append(empty);
                        empty = 0;
                    }
                    sb.append(piece.fenChar());
                }
            }
            if (empty > 0) {
                sb.append(empty);
            }
            if (rank > 0) {
                sb.append('/');
            }
        }
        sb.append(sideToMove == Side.WHITE ? " w " : " b ");
        String castling = (hasRight(WHITE_KINGSIDE) ? "K" : "") + (hasRight(WHITE_QUEENSIDE) ? "Q" : "")
                + (hasRight(BLACK_KINGSIDE) ? "k" : "") + (hasRight(BLACK_QUEENSIDE) ? "q" : "");
        sb.append(castling.isEmpty() ? "-" : castling);
        sb.append(' ').append(enPassantSquare == Square.NONE ? "-" : Square.name(enPassantSquare));
        sb.append(' ').append(halfmoveClock).append(' ').append(fullmoveNumber);
        return sb.toString();
    }

    public Position copy() {
        Position p = fromFen(toFen());
        // Keep the repetition history so a copied position still detects threefold repetition.
        p.hashHistory.clear();
        p.hashHistory.addAll(hashHistory);
        return p;
    }

    /* ---------------------------------------------------------- Accessors */

    public Piece pieceAt(int square) {
        return board[square];
    }

    public Side sideToMove() {
        return sideToMove;
    }

    public int castlingRights() {
        return castlingRights;
    }

    public boolean hasRight(int right) {
        return (castlingRights & right) != 0;
    }

    public int enPassantSquare() {
        return enPassantSquare;
    }

    public int halfmoveClock() {
        return halfmoveClock;
    }

    public int fullmoveNumber() {
        return fullmoveNumber;
    }

    public long hash() {
        return hash;
    }

    public int kingSquare(Side side) {
        for (int sq = 0; sq < 64; sq++) {
            Piece p = board[sq];
            if (p != null && p.type == PieceType.KING && p.side == side) {
                return sq;
            }
        }
        return Square.NONE;
    }

    public boolean inCheck(Side side) {
        return isAttacked(kingSquare(side), side.opposite());
    }

    public boolean inCheck() {
        return inCheck(sideToMove);
    }

    /** How many times the current position has occurred in this game (including now). */
    public int repetitionCount() {
        int count = 0;
        for (long h : hashHistory) {
            if (h == hash) {
                count++;
            }
        }
        return count;
    }

    /* ------------------------------------------------------------ Attacks */

    private static final int[][] KNIGHT_STEPS = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
    private static final int[][] KING_STEPS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    static final int[][] ORTHOGONAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    static final int[][] DIAGONAL = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    static int[][] knightSteps() {
        return KNIGHT_STEPS;
    }

    static int[][] kingSteps() {
        return KING_STEPS;
    }

    /** Is {@code square} attacked by any piece of {@code by}? */
    public boolean isAttacked(int square, Side by) {
        int file = Square.file(square);
        int rank = Square.rank(square);

        // Pawns attack diagonally forward, so look one rank "behind" the square from their side.
        int pawnRank = rank - by.forward();
        for (int df : new int[]{-1, 1}) {
            if (Square.onBoard(file + df, pawnRank) && is(Square.of(file + df, pawnRank), by, PieceType.PAWN)) {
                return true;
            }
        }
        for (int[] s : KNIGHT_STEPS) {
            if (Square.onBoard(file + s[0], rank + s[1]) && is(Square.of(file + s[0], rank + s[1]), by, PieceType.KNIGHT)) {
                return true;
            }
        }
        for (int[] s : KING_STEPS) {
            if (Square.onBoard(file + s[0], rank + s[1]) && is(Square.of(file + s[0], rank + s[1]), by, PieceType.KING)) {
                return true;
            }
        }
        return slidingAttack(file, rank, by, ORTHOGONAL, PieceType.ROOK)
                || slidingAttack(file, rank, by, DIAGONAL, PieceType.BISHOP);
    }

    private boolean slidingAttack(int file, int rank, Side by, int[][] directions, PieceType slider) {
        for (int[] d : directions) {
            int f = file + d[0];
            int r = rank + d[1];
            while (Square.onBoard(f, r)) {
                Piece p = board[Square.of(f, r)];
                if (p != null) {
                    if (p.side == by && (p.type == slider || p.type == PieceType.QUEEN)) {
                        return true;
                    }
                    break;
                }
                f += d[0];
                r += d[1];
            }
        }
        return false;
    }

    private boolean is(int square, Side side, PieceType type) {
        Piece p = board[square];
        return p != null && p.side == side && p.type == type;
    }

    /* ------------------------------------------------------- Make / unmake */

    /**
     * Plays a move generated by {@link MoveGenerator} for this position. The move is trusted;
     * legality is the generator's job.
     */
    public void makeMove(Move move) {
        Piece moved = board[move.from()];
        int capturedSquare = move.isEnPassant() ? move.to() - 8 * sideToMove.forward() : move.to();
        Piece captured = board[capturedSquare];

        history.push(new Undo(move, moved, captured, castlingRights, enPassantSquare, halfmoveClock, hash));

        board[capturedSquare] = null;
        board[move.from()] = null;
        board[move.to()] = move.promotion() == null ? moved : Piece.of(sideToMove, move.promotion());

        if (move.isCastle()) {
            int rank = Square.rank(move.from());
            boolean kingside = Square.file(move.to()) == 6;
            int rookFrom = Square.of(kingside ? 7 : 0, rank);
            int rookTo = Square.of(kingside ? 5 : 3, rank);
            board[rookTo] = board[rookFrom];
            board[rookFrom] = null;
        }

        castlingRights &= CASTLING_MASK[move.from()] & CASTLING_MASK[move.to()];
        enPassantSquare = move.isDoublePawnPush() ? (move.from() + move.to()) / 2 : Square.NONE;

        boolean irreversible = moved.type == PieceType.PAWN || captured != null;
        halfmoveClock = irreversible ? 0 : halfmoveClock + 1;
        if (sideToMove == Side.BLACK) {
            fullmoveNumber++;
        }
        sideToMove = sideToMove.opposite();

        hash = computeHash();
        // Positions before a capture or pawn move can never recur, so they never match later hashes.
        hashHistory.add(hash);
    }

    public void unmakeMove() {
        Undo undo = history.pop();
        Move move = undo.move();

        sideToMove = sideToMove.opposite();
        if (sideToMove == Side.BLACK) {
            fullmoveNumber--;
        }

        board[move.from()] = undo.moved();
        board[move.to()] = null;
        if (move.isEnPassant()) {
            board[move.to() - 8 * sideToMove.forward()] = undo.captured();
        } else {
            board[move.to()] = undo.captured();
        }

        if (move.isCastle()) {
            int rank = Square.rank(move.from());
            boolean kingside = Square.file(move.to()) == 6;
            int rookFrom = Square.of(kingside ? 7 : 0, rank);
            int rookTo = Square.of(kingside ? 5 : 3, rank);
            board[rookFrom] = board[rookTo];
            board[rookTo] = null;
        }

        castlingRights = undo.castlingRights();
        enPassantSquare = undo.enPassantSquare();
        halfmoveClock = undo.halfmoveClock();
        hash = undo.hash();

        hashHistory.remove(hashHistory.size() - 1);
    }

    /** Number of moves that can currently be undone. */
    public int plyCount() {
        return history.size();
    }

    private long computeHash() {
        long h = 0;
        for (int sq = 0; sq < 64; sq++) {
            if (board[sq] != null) {
                h ^= PIECE_KEYS[board[sq].index()][sq];
            }
        }
        h ^= CASTLING_KEYS[castlingRights];
        if (enPassantSquare != Square.NONE) {
            h ^= EN_PASSANT_FILE_KEYS[Square.file(enPassantSquare)];
        }
        if (sideToMove == Side.BLACK) {
            h ^= BLACK_TO_MOVE_KEY;
        }
        return h;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            sb.append(rank + 1).append("  ");
            for (int file = 0; file < 8; file++) {
                Piece p = board[Square.of(file, rank)];
                sb.append(p == null ? '.' : p.fenChar()).append(' ');
            }
            sb.append('\n');
        }
        return sb.append("   a b c d e f g h\n").toString();
    }
}
