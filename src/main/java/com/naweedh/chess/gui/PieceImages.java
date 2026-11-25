package com.naweedh.chess.gui;

import com.naweedh.chess.engine.Piece;
import com.naweedh.chess.engine.PieceType;
import com.naweedh.chess.engine.Side;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/** Loads the piece artwork from the classpath once ({@code /piece/w-king.png} etc.). */
final class PieceImages {

    private final Map<Side, Map<PieceType, Image>> images = new EnumMap<>(Side.class);

    PieceImages() {
        for (Side side : Side.values()) {
            Map<PieceType, Image> bySide = new EnumMap<>(PieceType.class);
            for (PieceType type : PieceType.values()) {
                String path = "/piece/" + (side == Side.WHITE ? "w-" : "b-") + type.name().toLowerCase() + ".png";
                bySide.put(type, load(path));
            }
            images.put(side, bySide);
        }
    }

    Image get(Piece piece) {
        return images.get(piece.side).get(piece.type);
    }

    private static Image load(String path) {
        try (InputStream in = PieceImages.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing image resource " + path);
            }
            return ImageIO.read(in);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }
}
