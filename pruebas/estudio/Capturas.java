package estudio;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.function.Function;

/** Abre la app con un progreso temporal y guarda capturas PNG de las pantallas de problemas. */
public final class Capturas {
    static Ventana v;

    public static void main(String[] args) throws Exception {
        Path salida = Paths.get(args[0]);
        Files.createDirectories(salida);
        int w = args.length > 1 ? Integer.parseInt(args[1]) : 1280, h = args.length > 2 ? Integer.parseInt(args[2]) : 820;
        SwingUtilities.invokeAndWait(() -> {
            try {
                List<Materia> ms = Materia.cargarTodas(Materia.carpetaDatos());
                Progreso.iniciar(salida.resolve("datos"));
                v = new Ventana(ms);
                v.setExtendedState(JFrame.NORMAL);
                v.setSize(w, h);
                v.setLocation(0, 0);
                v.setVisible(true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        captura(salida, "01_inicio", null);
        captura(salida, "02_catalogo", a -> new PantallaProblemas(a));
        captura(salida, "03_T20_clase", a -> PantallaProblema.practica(a, "T20", 0, () -> new PantallaProblemas(a)));
        PantallaProblema[] p = new PantallaProblema[1];
        captura(salida, "04_T01", a -> p[0] = PantallaProblema.practica(a, "T01", 11, () -> new PantallaProblemas(a)));
        captura(salida, "05_T12_resuelto", a -> PantallaProblema.resuelto(a, "T12", 44, () -> new PantallaResueltos(a)));
        captura(salida, "06_resueltos", a -> new PantallaResueltos(a));
        for (Generadores.Tipo t : Generadores.TIPOS) {
            captura(salida, "t_" + t.id(), a -> PantallaProblema.resuelto(a, t.id(), 22, () -> new PantallaProblemas(a)));
        }
        // Interacción: respuestas con coma decimal, una mal, revisar y dos pistas.
        captura(salida, "10_T01_practica", a -> PantallaProblema.practica(a, "T01", 11, () -> new PantallaProblemas(a)));
        SwingUtilities.invokeAndWait(() -> {
            JComponent pant = (JComponent) actual();
            List<UIP.CampoNumero> cs = buscar(pant, UIP.CampoNumero.class);
            // cs[0] es el campo de la semilla
            cs.get(1).campo.setText("2,523");
            cs.get(2).campo.setText("9");
            cs.get(3).campo.setText("0.3");
            boton(pant, "Revisar").doClick();
            boton(pant, "Pista").doClick();
            boton(pant, "Pista").doClick();
        });
        captura(salida, "11_T01_revisado", null);
        abajo();
        captura(salida, "12_T01_pistas", null);
        captura(salida, "13_T13", a -> PantallaProblema.practica(a, "T13", 22, () -> new PantallaProblemas(a)));
        SwingUtilities.invokeAndWait(() -> {
            JComponent pant = (JComponent) actual();
            buscar(pant, UIP.OpcionBoton.class).get(0).dispatchEvent(new java.awt.event.MouseEvent(buscar(pant, UIP.OpcionBoton.class).get(0),
                    java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 1, false, java.awt.event.MouseEvent.BUTTON1));
            boton(pant, "Ver solución completa").doClick();
        });
        captura(salida, "14_T13_solucion", null);
        abajo();
        captura(salida, "15_T13_solucion_abajo", null);
        SwingUtilities.invokeAndWait(() -> v.setSize(700, 800));
        captura(salida, "16_estrecho_T20", a -> PantallaProblema.practica(a, "T20", 0, () -> new PantallaProblemas(a)));
        SwingUtilities.invokeAndWait(() -> boton((JComponent) actual(), "Ver solución completa").doClick());
        Thread.sleep(400);
        SwingUtilities.invokeAndWait(() -> {
            for (JScrollPane sp : buscar((JComponent) actual(), JScrollPane.class)) sp.getVerticalScrollBar().setValue(1500);
        });
        captura(salida, "17_estrecho_T20_resp", null);
        captura(salida, "18_estrecho_catalogo", a -> new PantallaProblemas(a));
        SwingUtilities.invokeAndWait(() -> v.setSize(1280, 820));
        ExamenProblemas ex = ExamenProblemas.nuevo(3, 30);
        captura(salida, "07_examen", a -> PantallaProblema.examen(a, ex, () -> new PantallaProblemas(a)));
        SwingUtilities.invokeAndWait(ex::terminar);
        captura(salida, "08_resultado", a -> new PantallaResultadoExamen(a, ex, () -> new PantallaProblemas(a)));
        System.exit(0);
    }

    static Component actual() {
        Container t = (Container) v.getContentPane().getComponent(0);
        return t.getComponent(t.getComponentCount() - 1);
    }

    static <T> List<T> buscar(Container c, Class<T> tipo) {
        List<T> r = new java.util.ArrayList<>();
        for (Component h : c.getComponents()) {
            if (tipo.isInstance(h)) r.add(tipo.cast(h));
            if (h instanceof Container k) r.addAll(buscar(k, tipo));
        }
        return r;
    }

    static UI.Boton boton(Container c, String prefijo) {
        for (UI.Boton b : buscar(c, UI.Boton.class)) if (b.getText().startsWith(prefijo)) return b;
        throw new IllegalStateException("Sin botón " + prefijo);
    }

    static void abajo() throws Exception {
        Thread.sleep(500);
        SwingUtilities.invokeAndWait(() -> {
            for (JScrollPane sp : buscar((JComponent) actual(), JScrollPane.class)) sp.getVerticalScrollBar().setValue(sp.getVerticalScrollBar().getMaximum());
        });
    }

    static void captura(Path dir, String nombre, Function<Ventana, JComponent> f) throws Exception {
        if (f != null) SwingUtilities.invokeAndWait(() -> v.mostrar(f.apply(v), 0));
        Thread.sleep(1700);
        SwingUtilities.invokeAndWait(() -> {
            Container c = v.getContentPane();
            BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            c.paint(g);
            g.dispose();
            try {
                ImageIO.write(img, "png", dir.resolve(nombre + ".png").toFile());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
