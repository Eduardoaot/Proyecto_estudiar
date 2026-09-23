package estudio;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

/** Apartados de una materia: todo el temario o un tema concreto. */
final class PantallaTemas extends JPanel implements Pantalla {
    private static final String[] OPCIONES_REPASO = {"Todas", "Últimas 10", "Últimas 5"};
    private static final int[] VALORES_REPASO = {0, 10, 5};

    private final Ventana app;
    private final Materia materia;
    private final UI.Boton volver;
    private final UI.Texto titulo, subtitulo, etiquetaRepaso;
    private final UI.Segmentado repaso;
    private final TarjetaTema todo;
    private final List<TarjetaTema> tarjetas = new ArrayList<>();
    private final List<String> temas;
    private final Contenido contenido = new Contenido();
    private final JScrollPane scroll;
    private final ValorAnimado entrada = new ValorAnimado(0, this::repaint);

    PantallaTemas(Ventana app, Materia materia) {
        super(null);
        this.app = app;
        this.materia = materia;
        this.temas = materia.temas();
        setOpaque(false);

        volver = new UI.Boton("←   Materias", UI.TipoBoton.FANTASMA);
        volver.addActionListener(e -> app.mostrar(new PantallaInicio(app), -1));
        titulo = new UI.Texto(materia.nombre, Estilo.NEGRITA, 32f, Estilo.TEXTO);
        String sub = materia.preguntas.size() + " preguntas  ·  " + temas.size() + " temas";
        if (!materia.descripcion.isEmpty()) sub += "  ·  " + materia.descripcion;
        subtitulo = new UI.Texto(sub, Estilo.NORMAL, 15f, Estilo.TEXTO_SUAVE);
        etiquetaRepaso = new UI.Texto("Preguntas en cada repaso", Estilo.NORMAL, 12.5f, Estilo.TEXTO_TENUE);
        etiquetaRepaso.setAlineacion(UI.Texto.DERECHA);

        int limite = Progreso.limiteRepaso();
        int sel = 0;
        for (int i = 0; i < VALORES_REPASO.length; i++) if (VALORES_REPASO[i] == limite) sel = i;
        repaso = new UI.Segmentado(OPCIONES_REPASO, sel);
        repaso.setColores(materia.acento1, materia.acento2);
        repaso.setAlCambiar(i -> Progreso.guardarLimiteRepaso(VALORES_REPASO[i]));

        todo = new TarjetaTema("Todo el temario", "Todas las preguntas de la materia, en el orden del cuestionario.",
                materia.preguntas.size(), true, materia);
        todo.setAcciones(() -> abrir(null), () -> reiniciar(null, todo), () -> examinar(null), () -> verRespuestas(null));
        contenido.add(todo);
        for (String t : temas) {
            TarjetaTema tt = new TarjetaTema(t, null, materia.preguntasDe(t).size(), false, materia);
            tt.setAcciones(() -> abrir(t), () -> reiniciar(t, tt), () -> examinar(t), () -> verRespuestas(t));
            tarjetas.add(tt);
            contenido.add(tt);
        }
        scroll = new JScrollPane(contenido);
        UI.estilizarScroll(scroll);

        add(volver);
        add(titulo);
        add(subtitulo);
        add(etiquetaRepaso);
        add(repaso);
        add(scroll);
    }

    private void abrir(String tema) {
        List<Pregunta> ps = materia.preguntasDe(tema);
        String clave = Progreso.clave(materia, tema);
        Progreso.Resumen r = Progreso.resumen(clave, ps.size(), tema == null);
        if (r.terminado()) {
            app.mostrar(new PantallaFin(app, materia, tema, Progreso.cargar(clave, ps.size(), tema == null)), 1);
        } else {
            app.mostrar(new PantallaEstudio(app, materia, tema), 1);
        }
    }

    /** Abre el módulo en su examen final, retomando el que estuviera a medias. */
    private void examinar(String tema) {
        List<Pregunta> ps = materia.preguntasDe(tema);
        String clave = Progreso.clave(materia, tema);
        // Un examen a medias se continúa donde se dejó, sin preguntar ni reiniciar la cuenta.
        if (Progreso.resumen(clave, ps.size(), tema == null).examenEnCurso()) {
            app.mostrar(new PantallaEstudio(app, materia, tema), 1);
            return;
        }
        String nombre = tema == null ? "todo el temario" : "“" + tema + "”";
        String alFallar = tema == null
                ? "si fallas una, la repites hasta acertarla " + Motor.RETIRO_TEMARIO
                        + " veces seguidas y el examen sigue donde estaba."
                : "si fallas una, vuelves a la práctica.";
        if (UI.confirmar(this, "¿Hacer el examen final?",
                "Responderás las " + ps.size() + " preguntas de " + nombre + " seguidas. "
                        + "No hace falta haber practicado; " + alFallar, "Empezar examen")) {
            app.mostrar(new PantallaEstudio(app, materia, tema, true), 1);
        }
    }

    /** Lista completa de preguntas y respuestas de un módulo ya completado. */
    private void verRespuestas(String tema) {
        app.mostrar(new PantallaRespuestas(app, materia, tema), 1);
    }

    private void reiniciar(String tema, TarjetaTema tarjeta) {
        String nombre = tema == null ? "todo el temario" : "“" + tema + "”";
        if (UI.confirmar(this, "¿Reiniciar el progreso?",
                "Se borrará tu avance en " + nombre + ". Empezarás otra vez desde la primera pregunta. "
                        + "Si ya lo habías completado, la etiqueta se conserva.", "Reiniciar")) {
            Progreso.borrar(Progreso.clave(materia, tema));
            tarjeta.setResumen(Progreso.resumen(Progreso.clave(materia, tema), tarjeta.total, tema == null), true);
        }
    }

    @Override
    public void alMostrar() {
        app.setAcento(materia.acento1, materia.acento2);
        entrada.fijar(0);
        entrada.ir(1, 500);
        todo.setResumen(Progreso.resumen(Progreso.clave(materia, null), materia.preguntas.size(), true), false);
        todo.aparecer(80);
        for (int i = 0; i < tarjetas.size(); i++) {
            String t = temas.get(i);
            TarjetaTema tt = tarjetas.get(i);
            tt.setResumen(Progreso.resumen(Progreso.clave(materia, t), tt.total, false), false);
            tt.aparecer(160 + Math.min(i, 24) * 28);
        }
    }

    @Override
    public void doLayout() {
        Dimension d = UI.diseno(this);
        int w = d.width, h = d.height;
        int margen = w < 760 ? 22 : 32;
        int ancho = Math.max(240, Math.min(1180, w - 2 * margen));
        int x0 = (w - ancho) / 2;
        Dimension dv = UI.prefDiseno(volver);
        UI.poner(volver, x0 - 10, 22, dv.width, 40);

        float tamTitulo = w < 620 ? 22f : (w < 900 ? 26f : 32f);
        if (titulo.getTam() != tamTitulo) titulo.setFuente(Estilo.NEGRITA, tamTitulo);

        Dimension ds = UI.prefDiseno(repaso);
        // Si el título no cabe entero al lado, el selector de repaso baja a su propia fila.
        int anchoTituloNatural = (int) Math.round(titulo.anchoNatural() / Estilo.escala());
        boolean apilado = ancho < 640 || ancho - ds.width - 40 < anchoTituloNatural;
        int y = 76;
        int anchoTitulo;
        if (apilado) {
            etiquetaRepaso.setVisible(false);
            anchoTitulo = ancho;
        } else {
            etiquetaRepaso.setVisible(true);
            int xs = x0 + ancho - ds.width;
            UI.poner(repaso, xs, 92, ds.width, ds.height);
            UI.poner(etiquetaRepaso, xs - 20, 72, ds.width + 20, 18);
            anchoTitulo = xs - x0 - 24;
        }
        int ht = (int) Math.round(titulo.alturaPara(Estilo.px(anchoTitulo)) / Estilo.escala());
        int hsub = (int) Math.round(subtitulo.alturaPara(Estilo.px(anchoTitulo)) / Estilo.escala());
        UI.poner(titulo, x0, y, anchoTitulo, ht);
        UI.poner(subtitulo, x0, y + ht + 4, anchoTitulo, hsub);
        int top = y + ht + 4 + hsub + 18;
        if (apilado) {
            UI.poner(repaso, x0, top, Math.min(ds.width, ancho), ds.height);
            top += ds.height + 16;
        } else {
            top = Math.max(150, top + 4);
        }
        // El contenido se extiende 10px por los lados para el resplandor de las tarjetas.
        UI.poner(scroll, x0 - 12, top, ancho + 24, Math.max(60, h - top));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Estilo.suavizar(g2);
        int w = Estilo.unidadesDeDiseno(g2, getWidth(), getHeight()).width;
        int ancho = Math.min(1180, w - 64);
        int x0 = (w - ancho) / 2;
        double e = Estilo.easeOut(entrada.get());
        g2.setPaint(new GradientPaint(x0, 0, materia.acento1, x0 + 60, 0, materia.acento2));
        g2.fill(new RoundRectangle2D.Double(x0, 66, 44 * e, 4, 4, 4));
        g2.dispose();
    }

    /** Contenido desplazable: tarjeta general + cuadrícula de temas. */
    private final class Contenido extends JPanel implements Scrollable {
        private static final int ALTO = 150, GAP = 2, M = 10;

        Contenido() {
            super(null);
            setOpaque(false);
        }

        /** Recibe y devuelve unidades de diseño. */
        private int disponer(int w, boolean aplicar) {
            int y = 4;
            if (aplicar) UI.poner(todo, 2, y, w - 4, 188);
            y += 188 + 22;
            if (aplicar) seccionY = y;
            y += 26;
            int util = w - 4;
            int anchoMin = w < 620 ? 230 : 270;
            int cols = Math.max(1, (util + GAP) / (anchoMin + GAP));
            int cw = (util - GAP * (cols - 1)) / cols;
            for (int i = 0; i < tarjetas.size(); i++) {
                int c = i % cols, f = i / cols;
                if (aplicar) UI.poner(tarjetas.get(i), 2 + c * (cw + GAP), y + f * (ALTO + GAP), cw, ALTO);
            }
            int filas = (tarjetas.size() + cols - 1) / cols;
            return y + filas * (ALTO + GAP) + 24;
        }

        private int seccionY;

        @Override
        public void doLayout() {
            disponer(UI.diseno(this).width, true);
        }

        @Override
        public Dimension getPreferredSize() {
            int real = getParent() != null && getParent().getWidth() > 0 ? getParent().getWidth() : 900;
            int w = (int) Math.round(real / Estilo.escala());
            return new Dimension(real, Estilo.px(disponer(w, false)));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Estilo.suavizar(g2);
            int ancho = Estilo.unidadesDeDiseno(g2, getWidth(), getHeight()).width;
            g2.setFont(Estilo.etiqueta(12f));
            g2.setColor(Estilo.TEXTO_TENUE);
            g2.drawString("TEMAS DE LA MATERIA", M + 2, seccionY + 14);
            FontMetrics fm = g2.getFontMetrics();
            int x = M + 2 + fm.stringWidth("TEMAS DE LA MATERIA") + 14;
            g2.setColor(Estilo.BORDE);
            g2.fillRect(x, seccionY + 9, ancho - x - M, 1);
            g2.dispose();
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return Estilo.px(24); }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(24, r.height - 60); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
