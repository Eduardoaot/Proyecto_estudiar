package estudio;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

public final class App {
    private App() {}

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        SwingUtilities.invokeLater(() -> {
            List<Materia> materias;
            try {
                Path datos = Materia.carpetaDatos();
                Progreso.iniciar(datos);
                materias = Materia.cargarTodas(datos);
                if (materias.isEmpty()) throw new IllegalStateException("La carpeta 'datos' no contiene preguntas.");
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, e.getMessage(), "Estudio Activo", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
                return;
            }
            new Ventana(materias).setVisible(true);
        });
    }
}
