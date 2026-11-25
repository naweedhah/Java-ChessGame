package com.naweedh.chess.engine;

import java.util.List;

/** Standard Algebraic Notation (the "e4", "Nbd7", "exd6", "O-O", "e8=Q+" format used in PGN). */
public final class San {

    private San() {
    }

    /** SAN for a legal move in the given position. The position is left unchanged. */
    public static String of(Position position, Move move) {
        Piece piece = position.pieceAt(move.from());
        StringBuilder san = new StringBuilder();

        if (move.isCastle()) {
            san.append(Square.file(move.to()) == 6 ? "O-O" : "O-O-O");
        } else if (piece.type == PieceType.PAWN) {
            if (move.isCapture()) {
                san.append((char) ('a' + Square.file(move.from()))).append('x');
            }
            san.append(Square.name(move.to()));
            if (move.promotion() != null) {
                san.append('=').append(move.promotion().letter);
            }
        } else {
            san.append(piece.type.letter).append(disambiguation(position, move, piece));
            if (move.isCapture()) {
                san.append('x');
            }
            san.append(Square.name(move.to()));
        }

        position.makeMove(move);
        if (position.inCheck()) {
            san.append(MoveGenerator.legalMoves(position).isEmpty() ? '#' : '+');
        }
        position.unmakeMove();
        return san.toString();
    }

    /** Adds the file, rank or both when another piece of the same type can reach the same square. */
    private static String disambiguation(Position position, Move move, Piece piece) {
        List<Move> rivals = MoveGenerator.legalMoves(position).stream()
                .filter(m -> m.to() == move.to() && m.from() != move.from() && position.pieceAt(m.from()) == piece)
                .toList();
        if (rivals.isEmpty()) {
            return "";
        }
        boolean fileUnique = rivals.stream().noneMatch(m -> Square.file(m.from()) == Square.file(move.from()));
        if (fileUnique) {
            return String.valueOf((char) ('a' + Square.file(move.from())));
        }
        boolean rankUnique = rivals.stream().noneMatch(m -> Square.rank(m.from()) == Square.rank(move.from()));
        if (rankUnique) {
            return String.valueOf((char) ('1' + Square.rank(move.from())));
        }
        return Square.name(move.from());
    }
}
