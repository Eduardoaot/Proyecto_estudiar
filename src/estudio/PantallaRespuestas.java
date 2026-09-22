package estudio;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.List;

/** Lista completa de preguntas y respuestas de un módulo completado, para repasar leyendo. */
final class PantallaRespuestas extends JPanel implements Pantalla {
    private final Ventana app;
    private final Materia materia;
    private final String tema;
    private final List<Pregunta> preguntas;
    private final UI.Boton volver;
    private final UI.Texto titulo, subtitulo;
    private final Lista lista = new Lista();
    private final JScrollPane scroll;
    private final ValorAnimado entrada = new ValorAnimado(0, this::repaint);

    PantallaRespuestas(Ventana app, Materia materia, String tema) {
        super(null);
        this.app = app;
        this.materia = materia;
        this.tema = tema;
        this.preguntas = materia.preguntasDe(tema);
        setOpaque(false);

        volver = new UI.Boton("←   Temas", UI.TipoBoton.FANTASMA);
        volver.addActionListener(e -> app.mostrar(new PantallaTemas(app, materia), -1));
        titulo = new UI.Texto(tema == null ? "Todo el temario" : tema, Estilo.NEGRITA, 30f, Estilo.TEXTO);
        String fecha = Progreso.fechaCompletado(Progreso.clave(materia, tema));
        String sub = materia.nombre + "  ·  " + preguntas.size() + (preguntas.size() == 1 ? " pregunta" : " preguntas");
        if (fecha != null) sub += "  ·  completado el " + fecha;
        subtitulo = new UI.Texto(sub, Estilo.NORMAL, 14.5f, Estilo.TEXTO_SUAVE);

        String seccion = null;
        for (int i = 0; i < preguntas.size(); i++) {
            Pregunta p = preguntas.get(i);
            String cabecera = null;
            if (tema == null && !p.tema().equals(seccion)) {
                cabecera = p.tema();
                seccion = p.tema();
            }
            lista.add(new Ficha(p, cabecera, materia));
        }
        scroll = new JScrollPane(lista);
        UI.estilizarScroll(scroll);

        add(volver);
        add(titulo);
        add(subtitulo);
        add(scroll);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "volver");
        getActionMap().put("volver", UI.accion(() -> true, volver::doClick));
    }

    @Override
    public void alMostrar() {
        app.setAcento(materia.acento1, materia.acento2);
        entrada.fijar(0);
        entrada.ir(1, 500);
        lista.aparecer();
    }

    @Override
    public void doLayout() {
        Dimension d = UI.diseno(this);
        int w = d.width, h = d.height;
        int margen = w < 760 ? 22 : 32;
        int ancho = Math.max(240, Math.min(980, w - 2 * margen));
        int x0 = (w - ancho) / 2;
        Dimension dv = UI.prefDiseno(volver);
        UI.poner(volver, x0 - 10, 22, dv.width, 40);
        float tamTitulo = w < 620 ? 22f : (w < 900 ? 26f : 30f);
        if (titulo.getTam() != tamTitulo) titulo.setFuente(Estilo.NEGRITA, tamTitulo);
        int ht = (int) Math.round(titulo.alturaPara(Estilo.px(ancho)) / Estilo.escala());
        int hsub = (int) Math.round(subtitulo.alturaPara(Estilo.px(ancho)) / Estilo.escala());
        UI.poner(titulo, x0, 74, ancho, ht);
        UI.poner(subtitulo, x0, 74 + ht + 4, ancho, hsub);
        int top = 74 + ht + 4 + hsub + 20;
        UI.poner(scroll, x0 - 6, top, ancho + 12, Math.max(60, h - top));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Estilo.suavizar(g2);
        int total = Estilo.unidadesDeDiseno(g2, getWidth(), getHeight()).width;
        int margen = total < 760 ? 22 : 32;
        int ancho = Math.max(240, Math.min(980, total - 2 * margen));
        int x0 = (total - ancho) / 2;
        double e = Estilo.easeOut(entrada.get());
        g2.setPaint(new GradientPaint(x0, 0, materia.acento1, x0 + 60, 0, materia.acento2));
        g2.fill(new RoundRectangle2D.Double(x0, 64, 44 * e, 4, 4, 4));
        g2.dispose();
    }

    /** Contenedor desplazable con las fichas. */
    private final class Lista extends JPanel implements Scrollable {
        Lista() {
            super(null);
            setOpaque(false);
        }

        void aparecer() {
            Component[] cs = getComponents();
            for (int i = 0; i < cs.length && i < 12; i++) ((Ficha) cs[i]).aparecer(60 + i * 45);
            for (int i = 12; i < cs.length; i++) ((Ficha) cs[i]).mostrarYa();
        }

        /** Unidades de diseño. */
        private int disponer(int w, boolean aplicar) {
            int y = 4;
            for (Component c : getComponents()) {
                Ficha f = (Ficha) c;
                int alto = f.alturaPara(w - 8);
                if (aplicar) UI.poner(f, 4, y, w - 8, alto);
                y += alto + 10;
            }
            return y + 24;
        }

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

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return Estilo.px(28); }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(28, r.height - 60); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** Una pregunta con su respuesta. */
    private static final class Ficha extends JComponent {
        private static final int PAD = 22, ALTO_CABECERA = 44;
        private final Pregunta pregunta;
        private final String cabecera;
        private final Materia materia;
        private final ValorAnimado aparicion = new ValorAnimado(1, this::repaint);

        Ficha(Pregunta pregunta, String cabecera, Materia materia) {
            this.pregunta = pregunta;
            this.cabecera = cabecera;
            this.materia = materia;
            setOpaque(false);
        }

        void aparecer(int retraso) {
            aparicion.fijar(0);
            Anim.despues(Math.max(1, retraso), () -> aparicion.ir(1, 420));
        }

        void mostrarYa() {
            aparicion.fijar(1);
        }

        int alturaPara(int w) {
            int interior = w - 2 * PAD - 46;
            FontMetrics fp = getFontMetrics(Estilo.fuente(Estilo.SEMI, 16.5f));
            FontMetrics fr = getFontMetrics(Estilo.fuente(Estilo.NORMAL, 15f));
            int alto = PAD
                    + Estilo.lineasDe(pregunta.pregunta(), fp, interior, 0) * Math.round(fp.getHeight() * 1.25f)
                    + 8
                    + Estilo.lineasDe(pregunta.respuesta(), fr, interior, 0) * Math.round(fr.getHeight() * 1.35f)
                    + PAD;
            return alto + (cabecera != null ? ALTO_CABECERA : 0);
        }

        @Override
        protected void paintComponent(Graphics g) {
            double ap = aparicion.get();
            if (ap <= 0.001) return;
            Graphics2D g2 = (Graphics2D) g.create();
            Estilo.suavizar(g2);
            Dimension dim = Estilo.unidadesDeDiseno(g2, getWidth(), getHeight());
            Estilo.alfa(g2, (float) Estilo.limitar(ap * 1.4));
            g2.translate(0, (1 - ap) * 14);
            int w = dim.width;
            int y0 = 0;
            if (cabecera != null) {
                g2.setFont(Estilo.etiqueta(11.5f));
                g2.setColor(Estilo.mezclar(materia.acento1, Color.WHITE, 0.2));
                FontMetrics fe = g2.getFontMetrics();
                g2.drawString(cabecera.toUpperCase(), PAD, 26);
                int x = PAD + fe.stringWidth(cabecera.toUpperCase()) + 14;
                g2.setColor(Estilo.BORDE);
                g2.fillRect(x, 21, Math.max(0, w - x - PAD), 1);
                y0 = ALTO_CABECERA;
            }
            int h = dim.height - y0;
            RoundRectangle2D forma = new RoundRectangle2D.Double(0.5, y0 + 0.5, w - 1, h - 1, 20, 20);
            g2.setPaint(new GradientPaint(0, y0, new Color(0x171D33), 0, y0 + h, new Color(0x12172B)));
            g2.fill(forma);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(Estilo.BORDE);
            g2.draw(forma);

            // Número de la pregunta
            g2.setFont(Estilo.fuente(Estilo.SEMI, 12.5f));
            FontMetrics fn = g2.getFontMetrics();
            String num = String.valueOf(pregunta.id());
            double nw = Math.max(28, fn.stringWidth(num) + 16);
            RoundRectangle2D chapa = new RoundRectangle2D.Double(PAD - 4, y0 + PAD - 2, nw, 22, 8, 8);
            g2.setColor(Estilo.alfa(materia.acento1, 30));
            g2.fill(chapa);
            g2.setColor(Estilo.mezclar(materia.acento1, Color.WHITE, 0.25));
            g2.drawString(num, (float) (PAD - 4 + (nw - fn.stringWidth(num)) / 2), (float) (y0 + PAD + 13));

            double x = PAD + 42, interior = w - 2 * PAD - 46;
            g2.setFont(Estilo.fuente(Estilo.SEMI, 16.5f));
            g2.setColor(Estilo.TEXTO);
            double y = Estilo.parrafo(g2, pregunta.pregunta(), x, y0 + PAD - 2, interior, 0, 1.25);
            g2.setFont(Estilo.fuente(Estilo.NORMAL, 15f));
            g2.setColor(Estilo.TEXTO_SUAVE);
            Estilo.parrafo(g2, pregunta.respuesta(), x, y + 8, interior, 0, 1.35);
            g2.dispose();
        }
    }
}
