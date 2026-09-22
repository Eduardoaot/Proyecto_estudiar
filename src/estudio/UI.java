package estudio;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** Componentes Swing dibujados a mano. */
final class UI {
    private UI() {}

    /** Coloca un componente dando coordenadas de diseño; aplica el zoom actual. */
    static void poner(Component c, double x, double y, double w, double h) {
        c.setBounds(Estilo.px(x), Estilo.px(y), Estilo.px(w), Estilo.px(h));
    }

    /** Tamaño del componente en unidades de diseño. */
    static Dimension diseno(Component c) {
        return new Dimension((int) Math.round(c.getWidth() / Estilo.escala()),
                (int) Math.round(c.getHeight() / Estilo.escala()));
    }

    /** Tamaño preferido de un componente (que ya viene en píxeles reales) en unidades de diseño. */
    static Dimension prefDiseno(Component c) {
        Dimension d = c.getPreferredSize();
        return new Dimension((int) Math.round(d.width / Estilo.escala()),
                (int) Math.round(d.height / Estilo.escala()));
    }

    static Action accion(BooleanSupplier habilitada, Runnable r) {
        return new AbstractAction() {
            @Override
            public boolean isEnabled() {
                return habilitada.getAsBoolean();
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        };
    }

    // ------------------------------------------------------------------ Capa

    /** Panel transparente con opacidad y desplazamiento vertical animables. */
    static class Capa extends JPanel {
        float alfa = 1f;
        double dy;

        Capa() {
            super(null);
            setOpaque(false);
        }

        @Override
        public void paint(Graphics g) {
            if (alfa <= 0.004f) return;
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                if (alfa < 1f) Estilo.alfa(g2, alfa);
                if (dy != 0) g2.translate(0, dy);
                super.paint(g2);
            } finally {
                g2.dispose();
            }
        }
    }

    // ------------------------------------------------------------------ Texto

    /** Texto con ajuste de línea, alineación y resaltado de palabras clave. */
    static class Texto extends JComponent {
        static final int IZQUIERDA = 0, CENTRO = 1, DERECHA = 2;

        private String texto;
        private int peso;
        private float tam;
        private Color color;
        private int alineacion = IZQUIERDA;
        private float interlineado = 1.32f;
        private int maxLineas;
        private Set<String> resaltar = Set.of();
        private Color colorResaltado = Estilo.EXITO;
        private List<String> lineasCache;
        private int anchoCache = -1;
        private double escalaCache = -1;
        private final Map<String, Boolean> coincidencias = new HashMap<>();

        /** {@code tam} es el tamaño de diseño: el zoom se aplica al pedir la fuente. */
        Texto(String t, int peso, float tam, Color c) {
            this.texto = t == null ? "" : t;
            this.peso = peso;
            this.tam = tam;
            this.color = c;
            setOpaque(false);
        }

        private void invalidar() {
            lineasCache = null;
            anchoCache = -1;
        }

        Font fuente() {
            return Estilo.fuenteUI(peso, tam);
        }

        float getTam() {
            return tam;
        }

        String getTexto() { return texto; }

        void setTexto(String t) {
            t = t == null ? "" : t;
            if (!t.equals(texto)) {
                texto = t;
                invalidar();
                revalidate();
                repaint();
            }
        }

        void setColor(Color c) {
            color = c;
            repaint();
        }

        /** Cambia el tamaño de diseño (por ejemplo, títulos más pequeños en ventanas estrechas). */
        void setFuente(int peso, float tam) {
            if (this.peso == peso && this.tam == tam) return;
            this.peso = peso;
            this.tam = tam;
            invalidar();
            revalidate();
            repaint();
        }

        void setAlineacion(int a) { alineacion = a; repaint(); }
        void setInterlineado(float f) { interlineado = f; revalidate(); }
        void setMaxLineas(int n) { maxLineas = n; invalidar(); revalidate(); }

        void setResaltado(Set<String> claves, Color c) {
            resaltar = claves == null ? Set.of() : claves;
            colorResaltado = c;
            coincidencias.clear();
            repaint();
        }

        private List<String> lineas(int ancho) {
            if (lineasCache != null && ancho == anchoCache && escalaCache == Estilo.escala()) return lineasCache;
            lineasCache = calcularLineas(ancho);
            anchoCache = ancho;
            escalaCache = Estilo.escala();
            return lineasCache;
        }

        private List<String> calcularLineas(int ancho) {
            FontMetrics fm = getFontMetrics(fuente());
            List<String> l = Estilo.partir(texto, fm, Math.max(1, ancho));
            if (maxLineas > 0 && l.size() > maxLineas) {
                List<String> c = new ArrayList<>(l.subList(0, maxLineas));
                c.set(maxLineas - 1, Estilo.recortar(fm, l.get(maxLineas - 1) + " " + l.get(maxLineas), ancho));
                return c;
            }
            return l;
        }

        int altoLinea() {
            return Math.round(getFontMetrics(fuente()).getHeight() * interlineado);
        }

        int alturaPara(int ancho) {
            FontMetrics fm = getFontMetrics(fuente());
            int n = lineas(ancho).size();
            return (n - 1) * altoLinea() + fm.getHeight();
        }

        int anchoNatural() {
            FontMetrics fm = getFontMetrics(fuente());
            int max = 0;
            for (String p : texto.split("\n")) max = Math.max(max, fm.stringWidth(p));
            return max + 2;
        }

        @Override
        public Dimension getPreferredSize() {
            int w = getWidth() > 0 ? getWidth() : anchoNatural();
            return new Dimension(anchoNatural(), alturaPara(w));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Estilo.suavizar(g2);
            g2.setFont(fuente());
            FontMetrics fm = g2.getFontMetrics();
            int esp = fm.charWidth(' ');
            int y = fm.getAscent();
            for (String linea : lineas(getWidth())) {
                int ancho = fm.stringWidth(linea);
                int x = switch (alineacion) {
                    case CENTRO -> (getWidth() - ancho) / 2;
                    case DERECHA -> getWidth() - ancho;
                    default -> 0;
                };
                if (resaltar.isEmpty()) {
                    g2.setColor(color);
                    g2.drawString(linea, x, y);
                } else {
                    for (String palabra : linea.split(" ")) {
                        int pw = fm.stringWidth(palabra);
                        if (coincide(palabra)) {
                            g2.setColor(Estilo.alfa(colorResaltado, 40));
                            g2.fill(new RoundRectangle2D.Double(x - 3, y - fm.getAscent() + 1, pw + 6, fm.getHeight() - 1, 9, 9));
                            g2.setColor(Estilo.mezclar(colorResaltado, Color.WHITE, 0.2));
                        } else {
                            g2.setColor(color);
                        }
                        g2.drawString(palabra, x, y);
                        x += pw + esp;
                    }
                }
                y += altoLinea();
            }
            g2.dispose();
        }

        private boolean coincide(String palabra) {
            return coincidencias.computeIfAbsent(palabra, p -> {
                for (String parte : Evaluador.palabras(p)) if (resaltar.contains(parte)) return true;
                return false;
            });
        }
    }

    // ------------------------------------------------------------------ Botón

    enum TipoBoton { PRIMARIO, SECUNDARIO, EXITO, PELIGRO, FANTASMA }

    static class Boton extends JButton {
        private static final int RADIO = 14;
        private TipoBoton tipo;
        private Color c1 = Estilo.PALETAS[0][0], c2 = Estilo.PALETAS[0][1];
        private String atajo;
        private final ValorAnimado hover, presion;

        Boton(String texto, TipoBoton tipo) {
            super(texto);
            this.tipo = tipo;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFocusable(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            hover = new ValorAnimado(0, this::repaint);
            presion = new ValorAnimado(0, this::repaint);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover.ir(1, 160); }
                @Override public void mouseExited(MouseEvent e) { hover.ir(0, 240); presion.ir(0, 120); }
                @Override public void mousePressed(MouseEvent e) { if (isEnabled()) presion.ir(1, 70); }
                @Override public void mouseReleased(MouseEvent e) { presion.ir(0, 200); }
            });
        }

        void setTipo(TipoBoton t) {
            tipo = t;
            repaint();
        }

        void setColores(Color a, Color b) {
            c1 = a;
            c2 = b;
            repaint();
        }

        void setAtajo(String a) {
            atajo = a;
            revalidate();
            repaint();
        }

        @Override
        public void setText(String text) {
            super.setText(text);
            revalidate();
        }

        private Font fuenteBoton() {
            return Estilo.fuente(Estilo.SEMI, 14.5f);
        }

        /** Ancho de la tecla de atajo, en unidades de diseño. */
        private int anchoAtajo() {
            return getFontMetrics(Estilo.fuente(Estilo.SEMI, 11f)).stringWidth(atajo) + 14;
        }

        @Override
        public Dimension getPreferredSize() {
            int w = getFontMetrics(fuenteBoton()).stringWidth(getText()) + 44;
            if (atajo != null) w += anchoAtajo() + 10;
            return new Dimension(Estilo.px(w), Estilo.px(44));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Estilo.suavizar(g2);
            Dimension d = Estilo.unidadesDeDiseno(g2, getWidth(), getHeight());
            int w = d.width, h = d.height;
            double hv = hover.get(), pr = presion.get();
            if (!isEnabled()) {
                Estilo.alfa(g2, 0.4f);
                hv = 0;
            }
            double s = 1 - 0.04 * pr;
            g2.translate(w / 2.0, h / 2.0);
            g2.scale(s, s);
            g2.translate(-w / 2.0, -h / 2.0);
            RoundRectangle2D forma = new RoundRectangle2D.Double(1, 1, w - 2, h - 2, RADIO, RADIO);
            Color colorTexto;
            switch (tipo) {
                case PRIMARIO -> {
                    Color a = Estilo.mezclar(c1, Color.WHITE, 0.14 * hv);
                    Color b = Estilo.mezclar(c2, Color.WHITE, 0.14 * hv);
                    g2.setPaint(new GradientPaint(0, 0, a, w, h, b));
                    g2.fill(forma);
                    g2.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 46), 0, h * 0.6f, new Color(255, 255, 255, 0)));
                    g2.fill(forma);
                    g2.setColor(new Color(255, 255, 255, (int) (30 + 40 * hv)));
                    g2.draw(forma);
                    colorTexto = Color.WHITE;
                }
                case SECUNDARIO -> {
                    g2.setColor(Estilo.mezclar(Estilo.SUPERFICIE_ALTA, Color.WHITE, 0.07 * hv));
                    g2.fill(forma);
                    g2.setColor(Estilo.mezclar(Estilo.BORDE_FUERTE, new Color(255, 255, 255, 100), hv));
                    g2.draw(forma);
                    colorTexto = Estilo.TEXTO;
                }
                case EXITO, PELIGRO -> {
                    Color c = tipo == TipoBoton.EXITO ? Estilo.EXITO : Estilo.ERROR;
                    g2.setColor(Estilo.alfa(c, (int) (30 + 36 * hv)));
                    g2.fill(forma);
                    g2.setColor(Estilo.alfa(c, (int) (110 + 90 * hv)));
                    g2.draw(forma);
                    colorTexto = Estilo.mezclar(c, Color.WHITE, 0.3);
                }
                default -> {
                    g2.setColor(new Color(255, 255, 255, (int) (18 * hv)));
                    g2.fill(forma);
                    colorTexto = Estilo.mezclar(Estilo.TEXTO_SUAVE, Estilo.TEXTO, hv);
                }
            }
            g2.setFont(fuenteBoton());
            FontMetrics fm = g2.getFontMetrics();
            int anchoTexto = fm.stringWidth(getText());
            int total = anchoTexto + (atajo != null ? anchoAtajo() + 10 : 0);
            int x = (w - total) / 2;
            int y = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.setColor(colorTexto);
            g2.drawString(getText(), x, y);
            if (atajo != null) {
                Font fa = Estilo.fuente(Estilo.SEMI, 11f);
                FontMetrics fma = g2.getFontMetrics(fa);
                int aw = anchoAtajo(), ah = 20;
                int ax = x + anchoTexto + 10, ay = (h - ah) / 2;
                RoundRectangle2D tecla = new RoundRectangle2D.Double(ax, ay, aw, ah, 8, 8);
                g2.setColor(Estilo.alfa(colorTexto, 28));
                g2.fill(tecla);
                g2.setColor(Estilo.alfa(colorTexto, 70));
                g2.draw(tecla);
                g2.setFont(fa);
                g2.setColor(Estilo.alfa(colorTexto, 210));
                g2.drawString(atajo, ax + (aw - fma.stringWidth(atajo)) / 2f, ay + (ah - fma.getHeight()) / 2f + fma.getAscent());
            }
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ Campo de texto

    static class Campo extends JPanel {
        final JTextArea area = new JTextArea();
        private final JScrollPane scroll;
        private final String placeholder;
        private final ValorAnimado foco;
        private Color acento = Estilo.PALETAS[0][0];

        Campo(String placeholder) {
            super(null);
            this.placeholder = placeholder;
            setOpaque(false);
            foco = new ValorAnimado(0, this::repaint);
            area.setOpaque(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setFont(Estilo.fuenteUI(Estilo.NORMAL, 16.5f));
            area.setForeground(Estilo.TEXTO);
            area.setCaretColor(Color.WHITE);
            area.setSelectionColor(new Color(99, 102, 241, 130));
            area.setSelectedTextColor(Color.WHITE);
            area.setDisabledTextColor(Estilo.TEXTO_SUAVE);
            area.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
            area.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { repaint(); }
                @Override public void removeUpdate(DocumentEvent e) { repaint(); }
                @Override public void changedUpdate(DocumentEvent e) { repaint(); }
            });
            area.addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { foco.ir(1, 220); }
                @Override public void focusLost(FocusEvent e) { foco.ir(0, 300); }
            });
            scroll = new JScrollPane(area);
            estilizarScroll(scroll);
            add(scroll);
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { area.requestFocusInWindow(); }
            });
        }

        void setAcento(Color c) {
            acento = c;
            area.setSelectionColor(Estilo.alfa(c, 110));
            repaint();
        }

        @Override
        public void doLayout() {
            scroll.setBounds(Estilo.px(18), Estilo.px(16), getWidth() - Estilo.px(28), getHeight() - Estilo.px(30));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Estilo.suavizar(g2);
            Dimension d = Estilo.unidadesDeDiseno(g2, getWidth(), getHeight());
            int w = d.width, h = d.height;
            double f = foco.get();
            RoundRectangle2D r = new RoundRectangle2D.Double(3.5, 3.5, w - 7, h - 7, 18, 18);
            if (f > 0.01) {
                g2.setColor(Estilo.alfa(acento, (int) (55 * f)));
                g2.setStroke(new BasicStroke(6f));
                g2.draw(r);
            }
            g2.setColor(Estilo.mezclar(Estilo.CAMPO, new Color(0x121832), f));
            g2.fill(r);
            g2.setStroke(new BasicStroke(1.2f));
            g2.setColor(Estilo.mezclar(Estilo.BORDE_FUERTE, Estilo.alfa(acento, 220), f));
            g2.draw(r);
            if (area.getDocument().getLength() == 0 && placeholder != null) {
                g2.setFont(Estilo.fuente(Estilo.NORMAL, 16.5f));
                g2.setColor(Estilo.TEXTO_TENUE);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(Estilo.recortar(fm, placeholder, w - 44), 21, 17 + fm.getAscent());
            }
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ Barra de progreso

    static class Barra extends JComponent {
        private final ValorAnimado valor = new ValorAnimado(0, this::repaint);
        private Color c1 = Estilo.PALETAS[0][0], c2 = Estilo.PALETAS[0][1];

        Barra() {
            setOpaque(false);
        }

        void setColores(Color a, Color b) {
            c1 = a;
            c2 = b;
            repaint();
        }

        void setValor(double v, boolean animar) {
            if (animar) valor.ir(Estilo.limitar(v), 800);
            else valor.fijar(Estilo.limitar(v));
        }

        @Override
        protected void paintComponent(Graphics g) {
            pintar((Graphics2D) g, 0, 0, getWidth(), getHeight(), valor.get(), c1, c2);
        }

        static void pintar(Graphics2D g0, double x, double y, double w, double h, double v, Color c1, Color c2) {
            Graphics2D g = (Graphics2D) g0.create();
            Estilo.suavizar(g);
            g.setColor(new Color(255, 255, 255, 16));
            g.fill(new RoundRectangle2D.Double(x, y, w, h, h, h));
            if (v > 0.0005) {
                double fw = Math.max(h, w * v);
                RoundRectangle2D relleno = new RoundRectangle2D.Double(x, y, fw, h, h, h);
                g.setPaint(new GradientPaint((float) x, 0, c1, (float) (x + Math.max(fw, 1)), 0, c2));
                g.fill(relleno);
                g.setPaint(new GradientPaint(0, (float) y, new Color(255, 255, 255, 70), 0, (float) (y + h), new Color(255, 255, 255, 0)));
                g.fill(relleno);
            }
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ Control segmentado

    static class Segmentado extends JComponent {
        private final String[] opciones;
        private int seleccion;
        private int encima = -1;
        private final ValorAnimado posicion;
        private IntConsumer alCambiar = i -> {};
        private Color c1 = Estilo.PALETAS[0][0], c2 = Estilo.PALETAS[0][1];

        Segmentado(String[] opciones, int seleccion) {
            this.opciones = opciones;
            this.seleccion = seleccion;
            this.posicion = new ValorAnimado(seleccion, this::repaint);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int i = indice(e.getX());
                    if (i != Segmentado.this.seleccion) {
                        Segmentado.this.seleccion = i;
                        posicion.ir(i, 320);
                        alCambiar.accept(i);
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    int i = indice(e.getX());
                    if (i != encima) {
                        encima = i;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    encima = -1;
                    repaint();
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        void setAlCambiar(IntConsumer c) { alCambiar = c; }

        void setColores(Color a, Color b) {
            c1 = a;
            c2 = b;
            repaint();
        }

        /** En unidades de diseño. */
        private int anchoSegmento() {
            FontMetrics fm = getFontMetrics(Estilo.fuente(Estilo.SEMI, 13f));
            int max = 0;
            for (String o : opciones) max = Math.max(max, fm.stringWidth(o));
            return max + 30;
        }

        private int indice(int x) {
            int seg = Math.max(1, (getWidth() - Estilo.px(8)) / opciones.length);
            return Math.max(0, Math.min(opciones.length - 1, (x - Estilo.px(4)) / seg));
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Estilo.px(anchoSegmento() * opciones.length + 8), Estilo.px(38));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Estilo.suavizar(g2);
            Dimension d = Estilo.unidadesDeDiseno(g2, getWidth(), getHeight());
            int w = d.width, h = d.height;
            RoundRectangle2D fondo = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 14, 14);
            g2.setColor(new Color(255, 255, 255, 12));
            g2.fill(fondo);
            g2.setColor(Estilo.BORDE);
            g2.draw(fondo);
            double seg = (w - 8) / (double) opciones.length;
            double px = 4 + posicion.get() * seg;
            RoundRectangle2D pastilla = new RoundRectangle2D.Double(px, 4, seg, h - 8, 10, 10);
            g2.setPaint(new GradientPaint((float) px, 0, Estilo.alfa(c1, 210), (float) (px + seg), 0, Estilo.alfa(c2, 210)));
            g2.fill(pastilla);
            g2.setFont(Estilo.fuente(Estilo.SEMI, 13f));
            for (int i = 0; i < opciones.length; i++) {
                double cercania = 1 - Math.min(1, Math.abs(posicion.get() - i));
                Color base = i == encima ? Estilo.TEXTO : Estilo.TEXTO_SUAVE;
                g2.setColor(Estilo.mezclar(base, Color.WHITE, cercania));
                Estilo.textoCentrado(g2, opciones[i], 4 + seg * i + seg / 2, h / 2.0);
            }
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ Scroll

    static void estilizarScroll(JScrollPane sp) {
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setViewportBorder(null);
        sp.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        JScrollBar b = sp.getVerticalScrollBar();
        b.setUI(new BarraScroll());
        b.setOpaque(false);
        b.setPreferredSize(new Dimension(10, 0));
        b.setUnitIncrement(24);
    }

    static class BarraScroll extends BasicScrollBarUI {
        @Override protected JButton createDecreaseButton(int o) { return vacio(); }
        @Override protected JButton createIncreaseButton(int o) { return vacio(); }

        private static JButton vacio() {
            JButton b = new JButton();
            Dimension d = new Dimension(0, 0);
            b.setPreferredSize(d);
            b.setMinimumSize(d);
            b.setMaximumSize(d);
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {}

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            Estilo.suavizar(g2);
            g2.setColor(new Color(255, 255, 255, isDragging || isThumbRollover() ? 95 : 45));
            double a = r.width - 4;
            g2.fill(new RoundRectangle2D.Double(r.x + 2, r.y + 2, a, r.height - 4, a, a));
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ Diálogo de confirmación

    static boolean confirmar(Component padre, String titulo, String mensaje, String textoSi) {
        Window ventana = SwingUtilities.getWindowAncestor(padre);
        JDialog d = new JDialog(ventana, Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        boolean translucido = false;
        try {
            d.setBackground(new Color(0, 0, 0, 0));
            translucido = true;
        } catch (UnsupportedOperationException ignorada) {
            d.setBackground(Estilo.SUPERFICIE_ALTA);
        }
        boolean[] resultado = {false};
        final int ancho = Estilo.px(440), pad = Estilo.px(28);
        final int altoBoton = Estilo.px(44);

        Texto t1 = new Texto(titulo, Estilo.NEGRITA, 19f, Estilo.TEXTO);
        Texto t2 = new Texto(mensaje, Estilo.NORMAL, 14.5f, Estilo.TEXTO_SUAVE);
        Boton no = new Boton("Cancelar", TipoBoton.SECUNDARIO);
        Boton si = new Boton(textoSi, TipoBoton.PELIGRO);
        no.addActionListener(e -> d.dispose());
        si.addActionListener(e -> {
            resultado[0] = true;
            d.dispose();
        });
        int h1 = t1.alturaPara(ancho - 2 * pad), h2 = t2.alturaPara(ancho - 2 * pad);
        int alto = pad + h1 + Estilo.px(10) + h2 + Estilo.px(26) + altoBoton + pad;

        JPanel panel = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Estilo.suavizar(g2);
                RoundRectangle2D r = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 22, 22);
                g2.setPaint(new GradientPaint(0, 0, new Color(0x1F2747), 0, getHeight(), new Color(0x171D36)));
                g2.fill(r);
                g2.setColor(Estilo.BORDE_FUERTE);
                g2.draw(r);
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        t1.setBounds(pad, pad, ancho - 2 * pad, h1);
        t2.setBounds(pad, pad + h1 + Estilo.px(10), ancho - 2 * pad, h2);
        Dimension ds = si.getPreferredSize(), dn = no.getPreferredSize();
        int yb = alto - pad - altoBoton;
        si.setBounds(ancho - pad - ds.width, yb, ds.width, altoBoton);
        no.setBounds(ancho - pad - ds.width - Estilo.px(10) - dn.width, yb, dn.width, altoBoton);
        panel.add(t1);
        panel.add(t2);
        panel.add(si);
        panel.add(no);
        panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "cancelar");
        panel.getActionMap().put("cancelar", accion(() -> true, d::dispose));
        panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ENTER"), "aceptar");
        panel.getActionMap().put("aceptar", accion(() -> true, si::doClick));
        d.setContentPane(panel);
        d.setSize(ancho, alto);
        d.setLocationRelativeTo(ventana);

        Velo velo = ventana instanceof JFrame f ? Velo.instalar(f) : null;
        if (velo != null) velo.mostrar(true);
        if (translucido) {
            try {
                d.setOpacity(0f);
                Anim.ejecutar(180, 0, t -> d.setOpacity((float) Estilo.easeOut(t)), null);
            } catch (UnsupportedOperationException | IllegalComponentStateException ignorada) {
                // Sin soporte de opacidad: se muestra directamente.
            }
        }
        d.setVisible(true);
        if (velo != null) velo.mostrar(false);
        return resultado[0];
    }

    /** Oscurece la ventana mientras hay un diálogo abierto. */
    static class Velo extends JComponent {
        private final ValorAnimado nivel = new ValorAnimado(0, this::alCambiar);

        static Velo instalar(JFrame f) {
            if (f.getGlassPane() instanceof Velo v) return v;
            Velo v = new Velo();
            f.setGlassPane(v);
            return v;
        }

        Velo() {
            setOpaque(false);
            addMouseListener(new MouseAdapter() {});
        }

        void mostrar(boolean si) {
            if (si) setVisible(true);
            nivel.ir(si ? 1 : 0, si ? 180 : 220);
        }

        private void alCambiar() {
            if (nivel.get() <= 0.001 && nivel.destino() == 0) setVisible(false);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(new Color(4, 6, 14, (int) (150 * nivel.get())));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }
}
