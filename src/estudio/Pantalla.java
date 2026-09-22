package estudio;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

/** Contrato opcional de las pantallas para reaccionar a las transiciones. */
interface Pantalla {
    default void alMostrar() {}

    default void alOcultar() {}
}
