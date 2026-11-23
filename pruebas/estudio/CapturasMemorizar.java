package estudio;

import javax.swing.*;
import java.nio.file.*;
import java.util.List;

/** Capturas del botón de respuestas y del modo memorizar (con progreso temporal). */
public final class CapturasMemorizar {
    public static void main(String[] args) throws Exception {
        Path salida = Paths.get(args[0]);
        Files.createDirectories(salida);
        Materia[] m = new Materia[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                List<Materia> ms = Materia.cargarTodas(Materia.carpetaDatos());
                Progreso.iniciar(salida.resolve("datos"));
                m[0] = ms.stream().filter(x -> x.clave.endsWith("guia_ordinario")).findFirst().orElseThrow();
                Capturas.v = new Ventana(ms);
                Capturas.v.setExtendedState(JFrame.NORMAL);
                Capturas.v.setSize(1280, 820);
                Capturas.v.setLocation(0, 0);
                Capturas.v.setVisible(true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Capturas.captura(salida, "m1_temas", a -> new PantallaTemas(a, m[0]));
        PantallaEstudio[] est = new PantallaEstudio[1];
        Capturas.captura(salida, "m2_estudio", a -> est[0] = new PantallaEstudio(a, m[0], null));
        SwingUtilities.invokeAndWait(() -> {
            Capturas.buscar(est[0], JTextArea.class).get(0).setText("borrador que debe seguir aqui");
            Capturas.boton(est[0], "Memorizar").doClick();
        });
        Thread.sleep(800);
        SwingUtilities.invokeAndWait(() -> {
            JComponent p = (JComponent) Capturas.actual();
            Capturas.buscar(p, JTextArea.class).get(0).setText("El tamano de la muestra total");
            Capturas.boton(p, "Comparar").doClick();
        });
        Capturas.captura(salida, "m3_memorizar", null);
        SwingUtilities.invokeAndWait(() -> {
            JComponent p = (JComponent) Capturas.actual();
            Capturas.buscar(p, JTextArea.class).get(0).setText("El tamaño de muestra.");
            Capturas.boton(p, "Comparar").doClick();
        });
        Capturas.captura(salida, "m4_identica", null);
        SwingUtilities.invokeAndWait(() -> Capturas.boton((JComponent) Capturas.actual(), "←").doClick());
        Capturas.captura(salida, "m5_regreso", null);
        Capturas.captura(salida, "m6_respuestas", a -> new PantallaRespuestas(a, m[0], null));
        System.exit(0);
    }
}
