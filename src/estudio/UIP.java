package estudio;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Piezas de interfaz de las pantallas de problemas. Todas las medidas son unidades de diseño. */
final class UIP {
    private UIP() {}

    static final int PAD = 24;

    // ------------------------------------------------------------------ medidas de texto

    /** Contexto de medida con las mismas opciones que al pintar (métricas fraccionarias), para no sobrestimar líneas. */
    private static Graphics2D medidor;

    static FontMetrics fm(Component c, int peso, float tam) {
        if (medidor == null) {
            medidor = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB).createGraphics();
            Estilo.suavizar(medidor);
        }
        return medidor.getFontMetrics(Estilo.fuente(peso, tam));
    }

    /** Alto (diseño) de un párrafo dibujado con {@link Estilo#parrafo}. */
    static int altoTexto(Component c, String texto, int peso, float tam, double ancho, double inter, int maxLineas) {
        if (texto == null || texto.isEmpty()) return 0;
        FontMetrics f = fm(c, peso, tam);
        int n = Estilo.lineasDe(texto, f, Math.max(10, ancho), maxLineas);
        return (int) Math.ceil(n * f.getHeight() * inter);
    }

    static int ancho(Component c, String texto, int peso, float tam) {
        return fm(c, peso, tam).stringWidth(texto);
    }

    static double parrafo(Graphics2D g, String texto, int peso, float tam, Color color, double x, double y, double ancho,
                          double inter, int maxLineas) {
        g.setFont(Estilo.fuente(peso, tam));
        g.setColor(color);
        return Estilo.parrafo(g, texto, x, y, Math.max(10, ancho), maxLineas, inter);
    }

    /** Etiqueta pequeña en mayúsculas con línea hasta {@code fin}. */
    static void seccion(Graphics2D g, String texto, double x, double y, double fin, Color color) {
        g.setFont(Estilo.etiqueta(11.5f));
        g.setColor(color);
        String t = texto.toUpperCase();
        g.drawString(t, (float) x, (float) y + 12);
        double lx = x + g.getFontMetrics().stringWidth(t) + 14;
        if (fin > lx) {
            g.setColor(Estilo.BORDE);
            g.fill(new Rectangle2D.Double(lx, y + 7, fin - lx, 1));
        }
    }

    static void tarjeta(Graphics2D g, double w, double h) {
        RoundRectangle2D forma = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 20, 20);
        g.setPaint(new GradientPaint(0, 0, new Color(0x171D33), 0, (float) h, new Color(0x12172B)));
        g.fill(forma);
        g.setStroke(new BasicStroke(1f));
        g.setColor(Estilo.BORDE);
        g.draw(forma);
    }

    /** Círculo con palomita (ok) o cruz (mal). */
    static void icono(Graphics2D g, double cx, double cy, boolean ok) {
        Color c = ok ? Estilo.EXITO : Estilo.ERROR;
        g.setColor(Estilo.alfa(c, 40));
        g.fill(new Ellipse2D.Double(cx - 12, cy - 12, 24, 24));
        g.setColor(c);
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (ok) Estilo.palomita(g, cx, cy + 0.5, 22, 1);
        else Estilo.cruz(g, cx, cy, 22, 1);
    }

    static void chapa(Graphics2D g, String texto, double x, double y, Color c) {
        g.setFont(Estilo.fuente(Estilo.SEMI, 12.5f));
        FontMetrics f = g.getFontMetrics();
        double w = f.stringWidth(texto) + 16;
        RoundRectangle2D r = new RoundRectangle2D.Double(x, y, w, 22, 8, 8);
        g.setColor(Estilo.alfa(c, 34));
        g.fill(r);
        g.setColor(Estilo.mezclar(c, Color.WHITE, 0.3));
        g.drawString(texto, (float) (x + 8), (float) (y + 15.5));
    }

    static double anchoChapa(Component c, String texto) {
        return ancho(c, texto, Estilo.SEMI, 12.5f) + 16;
    }

    /** Puntos de dificultad (1 a 3). */
    static void dificultad(Graphics2D g, int nivel, double x, double cy, Color c) {
        for (int i = 0; i < 3; i++) {
            Ellipse2D e = new Ellipse2D.Double(x + i * 12, cy - 4, 8, 8);
            g.setColor(i < nivel ? c : new Color(255, 255, 255, 30));
            g.fill(e);
        }
    }

    // ------------------------------------------------------------------ bloques apilables

    /** Bloque de contenido con alto variable; dispone a sus hijos en unidades de diseño. */
    abstract static class Bloque extends JPanel {
        boolean conTarjeta = true;

        Bloque() {
            super(null);
            setOpaque(false);
        }

        /** Alto para un ancho dado (diseño). Debe coincidir con lo que ocupa {@link #disponer}. */
        abstract int alto(int w);

        void disponer(int w) {}

        abstract void pintar(Graphics2D g, int w, int h);

        @Override
        public void doLayout() {
            disponer(UI.diseno(this).width);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Estilo.suavizar(g2);
            Dimension d = Estilo.unidadesDeDiseno(g2, getWidth(), getHeight());
            if (conTarjeta) tarjeta(g2, d.width, d.height);
            pintar(g2, d.width, d.height);
            g2.dispose();
        }
    }

    /** Columna desplazable de bloques. */
    static final class Pila extends JPanel implements Scrollable {
        private final List<Bloque> bloques = new ArrayList<>();
        int gap = 14;

        Pila() {
            super(null);
            setOpaque(false);
        }

        void poner(List<? extends Bloque> nuevos) {
            removeAll();
            bloques.clear();
            for (Bloque b : nuevos) {
                bloques.add(b);
                add(b);
            }
            revalidate();
            repaint();
        }

        private int disponer(int w, boolean aplicar) {
            int y = 4;
            for (Bloque b : bloques) {
                if (!b.isVisible()) continue;
                int h = b.alto(w - 8);
                if (aplicar) UI.poner(b, 4, y, w - 8, h);
                y += h + gap;
            }
            return y + 20;
        }

        @Override
        public void doLayout() {
            disponer(UI.diseno(this).width, true);
            for (Bloque b : bloques) b.doLayout();
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

    /** Bloque de texto con título de sección. */
    static final class BloqueTexto extends Bloque {
        private final String titulo, texto;
        private final Color color;

        BloqueTexto(String titulo, String texto, Color color) {
            this.titulo = titulo;
            this.texto = texto;
            this.color = color;
        }

        @Override
        int alto(int w) {
            return PAD + 26 + altoTexto(this, texto, Estilo.NORMAL, 16.5f, w - 2 * PAD, 1.42, 0) + PAD - 4;
        }

        @Override
        void pintar(Graphics2D g, int w, int h) {
            seccion(g, titulo, PAD, PAD - 4, w - PAD, color);
            parrafo(g, texto, Estilo.NORMAL, 16.5f, Estilo.TEXTO, PAD, PAD + 24, w - 2 * PAD, 1.42, 0);
        }
    }

    /** Dibujo de tablas como las de las guías: encabezado de color, columnas numéricas centradas y renglones de total resaltados. */
    static final class Tablas {
        private Tablas() {}

        static final float TAM = 14.5f, TAM_CAB = 12f;
        private static final int PADC = 12;

        /** Renglones que se resaltan: totales y resultados de la tabla. */
        static boolean esTotal(List<String> f) {
            String a = f.get(0).trim().toUpperCase();
            return a.equals("TOTAL") || a.equals("ETI") || a.equals("ETE") || a.equals("SF");
        }

        /** Columna de valores cortos (números, porcentajes): se centra y no se parte. */
        static boolean corta(Generadores.Tabla t, int k) {
            if (k == 0) return false;
            for (List<String> f : t.filas()) if (f.get(k).length() > 24) return false;
            return true;
        }

        private static double[] anchos(Component c, Generadores.Tabla t, double disponible) {
            int n = t.columnas().size();
            double[] nat = new double[n], min = new double[n];
            for (int k = 0; k < n; k++) {
                // El encabezado puede partirse en dos líneas: cuenta su palabra más larga y algo más de la mitad del total.
                String cab = t.columnas().get(k).toUpperCase();
                double largo = 0;
                for (String p : cab.split(" ")) largo = Math.max(largo, ancho(c, p, Estilo.SEMI, TAM_CAB));
                double cabeza = Math.max(largo, ancho(c, cab, Estilo.SEMI, TAM_CAB) * 0.62) + 2 * PADC + 4;
                nat[k] = cabeza;
                double palabra = 0;
                for (List<String> f : t.filas()) {
                    nat[k] = Math.max(nat[k], ancho(c, f.get(k), Estilo.SEMI, TAM) + 2 * PADC + 2);
                    for (String p : f.get(k).split(" ")) palabra = Math.max(palabra, ancho(c, p, Estilo.SEMI, TAM) + 2 * PADC + 2);
                }
                // Las columnas cortas conservan su ancho; las de texto pueden partirse, sin bajar de 170.
                min[k] = corta(t, k) ? nat[k] : Math.min(nat[k], Math.max(Math.max(palabra, cabeza), 170));
            }
            double suma = 0, sumaMin = 0;
            for (int k = 0; k < n; k++) {
                suma += nat[k];
                sumaMin += min[k];
            }
            if (suma <= disponible) {
                for (int k = 0; k < n; k++) nat[k] += (disponible - suma) * nat[k] / suma;
                return nat;
            }
            double[] r = min.clone();
            if (sumaMin <= disponible) {
                // Lo que sobra se reparte entre las columnas de texto, según lo que les falta para caber en una línea.
                double falta = suma - sumaMin, extra = disponible - sumaMin;
                for (int k = 0; k < n; k++) r[k] += falta > 0 ? extra * (nat[k] - min[k]) / falta : 0;
            } else {
                for (int k = 0; k < n; k++) r[k] *= disponible / sumaMin;
            }
            return r;
        }

        private static int altoCab(Component c, Generadores.Tabla t, double[] a) {
            int h = 0;
            for (int k = 0; k < a.length; k++) h = Math.max(h, altoTexto(c, t.columnas().get(k).toUpperCase(), Estilo.SEMI, TAM_CAB, a[k] - 2 * PADC, 1.25, 0));
            return Math.max(h, 16) + 20;
        }

        private static int altoFila(Component c, List<String> f, double[] a) {
            int h = 0;
            for (int k = 0; k < f.size(); k++) h = Math.max(h, altoTexto(c, f.get(k), Estilo.SEMI, TAM, a[k] - 2 * PADC, 1.3, 0));
            return Math.max(h, 18) + 16;
        }

        static int alto(Component c, Generadores.Tabla t, double ancho) {
            double[] a = anchos(c, t, ancho);
            int h = altoCab(c, t, a);
            for (List<String> f : t.filas()) h += altoFila(c, f, a);
            return h;
        }

        /** Dibuja la tabla y devuelve su alto. */
        static int pintar(Graphics2D g, Component c, Generadores.Tabla t, double x0, double y0, double total, Color color) {
            double[] a = anchos(c, t, total);
            int hc = altoCab(c, t, a);
            int alto = alto(c, t, total);
            RoundRectangle2D marco = new RoundRectangle2D.Double(x0, y0, total, alto, 12, 12);
            Shape antes = g.getClip();
            g.clip(marco);
            g.setColor(Estilo.alfa(color, 46));
            g.fill(new Rectangle2D.Double(x0, y0, total, hc));
            double x = x0;
            for (int k = 0; k < a.length; k++) {
                String cab = t.columnas().get(k).toUpperCase();
                double hTxt = altoTexto(c, cab, Estilo.SEMI, TAM_CAB, a[k] - 2 * PADC, 1.25, 0);
                parrafoAlineado(g, cab, Estilo.SEMI, TAM_CAB, Estilo.mezclar(color, Color.WHITE, 0.55), x + PADC, y0 + (hc - hTxt) / 2.0,
                        a[k] - 2 * PADC, 1.25, corta(t, k));
                x += a[k];
            }
            double y = y0 + hc;
            for (int r = 0; r < t.filas().size(); r++) {
                List<String> f = t.filas().get(r);
                int hf = altoFila(c, f, a);
                boolean tot = esTotal(f);
                if (tot) {
                    g.setColor(Estilo.alfa(color, 26));
                    g.fill(new Rectangle2D.Double(x0, y, total, hf));
                } else if (r % 2 == 1) {
                    g.setColor(new Color(255, 255, 255, 8));
                    g.fill(new Rectangle2D.Double(x0, y, total, hf));
                }
                g.setColor(Estilo.BORDE);
                g.fill(new Rectangle2D.Double(x0, y - 0.5, total, 1));
                x = x0;
                for (int k = 0; k < f.size(); k++) {
                    int peso = tot || k == 0 ? Estilo.SEMI : Estilo.NORMAL;
                    Color col = tot ? Estilo.mezclar(color, Color.WHITE, 0.6) : k == 0 ? Estilo.TEXTO : Estilo.TEXTO_SUAVE;
                    parrafoAlineado(g, f.get(k), peso, TAM, col, x + PADC, y + 8, a[k] - 2 * PADC, 1.3, corta(t, k));
                    x += a[k];
                }
                y += hf;
            }
            // Separadores verticales entre columnas.
            g.setColor(Estilo.BORDE);
            x = x0;
            for (int k = 0; k < a.length - 1; k++) {
                x += a[k];
                g.fill(new Rectangle2D.Double(x - 0.5, y0, 1, alto));
            }
            g.setClip(antes);
            g.setColor(Estilo.alfa(color, 70));
            g.setStroke(new BasicStroke(1f));
            g.draw(marco);
            return alto;
        }

        /** Párrafo a la izquierda o centrado línea por línea. */
        private static void parrafoAlineado(Graphics2D g, String texto, int peso, float tam, Color color, double x, double y, double ancho,
                                            double inter, boolean centrado) {
            if (!centrado) {
                parrafo(g, texto, peso, tam, color, x, y, ancho, inter, 0);
                return;
            }
            g.setFont(Estilo.fuente(peso, tam));
            FontMetrics f = g.getFontMetrics();
            // Se parte por palabras y se centra cada línea.
            List<String> lineas = new ArrayList<>();
            StringBuilder act = new StringBuilder();
            for (String p : texto.split(" ")) {
                String prueba = act.length() == 0 ? p : act + " " + p;
                if (act.length() > 0 && f.stringWidth(prueba) > ancho) {
                    lineas.add(act.toString());
                    act = new StringBuilder(p);
                } else {
                    act = new StringBuilder(prueba);
                }
            }
            if (act.length() > 0) lineas.add(act.toString());
            g.setColor(color);
            double alto = f.getHeight() * inter;
            for (int i = 0; i < lineas.size(); i++) {
                String l = lineas.get(i);
                g.drawString(l, (float) (x + (ancho - f.stringWidth(l)) / 2), (float) (y + f.getAscent() + i * alto));
            }
        }
    }

    /** Tabla de datos del problema. */
    static final class BloqueTabla extends Bloque {
        private final String titulo;
        private final Generadores.Tabla tabla;
        private final Color color;

        BloqueTabla(Generadores.Tabla tabla, Color color) {
            this("Datos", tabla, color);
        }

        BloqueTabla(String titulo, Generadores.Tabla tabla, Color color) {
            this.titulo = titulo;
            this.tabla = tabla;
            this.color = color;
        }

        @Override
        int alto(int w) {
            return PAD + 26 + Tablas.alto(this, tabla, w - 2 * PAD) + PAD - 6;
        }

        @Override
        void pintar(Graphics2D g, int w, int h) {
            seccion(g, titulo, PAD, PAD - 4, w - PAD, color);
            Tablas.pintar(g, this, tabla, PAD, PAD + 26, w - 2 * PAD, color);
        }
    }

    /**
     * Enunciado por secciones, en una sola tarjeta: párrafos (cada línea que empieza con "• " es una viñeta) y tablas,
     * cada una con su subtítulo, como en las diapositivas de la clase.
     */
    static final class BloqueSecciones extends Bloque {
        private static final float TAM = 16.5f;
        private final String titulo;
        private final List<Generadores.Seccion> secciones;
        private final Color color;

        BloqueSecciones(String titulo, List<Generadores.Seccion> secciones, Color color) {
            this.titulo = titulo;
            this.secciones = secciones;
            this.color = color;
        }

        private int disponer(int w, Graphics2D g) {
            double ancho = w - 2 * PAD;
            int y = PAD + 26;
            for (int i = 0; i < secciones.size(); i++) {
                Generadores.Seccion s = secciones.get(i);
                if (i > 0) y += 16;
                if (s.titulo() != null) {
                    if (g != null) {
                        g.setFont(Estilo.etiqueta(11.5f));
                        g.setColor(Estilo.mezclar(color, Color.WHITE, 0.45));
                        g.drawString(s.titulo().toUpperCase(), PAD, y + 13);
                    }
                    y += 26;
                }
                if (s.texto() != null) {
                    for (String linea : s.texto().split("\n")) {
                        boolean vineta = linea.startsWith("• ");
                        String txt = vineta ? linea.substring(2) : linea;
                        double sangria = vineta ? 22 : 0;
                        if (g != null) {
                            if (vineta) {
                                g.setColor(Estilo.mezclar(color, Color.WHITE, 0.3));
                                g.fill(new java.awt.geom.Ellipse2D.Double(PAD + 5, y + 10, 6, 6));
                            }
                            parrafo(g, txt, Estilo.NORMAL, TAM, Estilo.TEXTO, PAD + sangria, y, ancho - sangria, 1.42, 0);
                        }
                        y += altoTexto(this, txt, Estilo.NORMAL, TAM, ancho - sangria, 1.42, 0) + (vineta ? 4 : 0);
                    }
                }
                if (s.tabla() != null) {
                    if (s.texto() != null) y += 10;
                    y += g != null ? Tablas.pintar(g, this, s.tabla(), PAD, y, ancho, color) : Tablas.alto(this, s.tabla(), ancho);
                }
            }
            return y + PAD - 6;
        }

        @Override
        int alto(int w) {
            return disponer(w, null);
        }

        @Override
        void pintar(Graphics2D g, int w, int h) {
            seccion(g, titulo, PAD, PAD - 4, w - PAD, color);
            disponer(w, g);
        }
    }

    // ------------------------------------------------------------------ campo numérico

    enum Estado { NEUTRO, OK, MAL }

    /** Campo de una línea para escribir un resultado. */
    static final class CampoNumero extends JPanel {
        final JTextField campo = new JTextField();
        private final String placeholder;
        private final ValorAnimado foco = new ValorAnimado(0, this::repaint);
        private Estado estado = Estado.NEUTRO;
        private Color acento = ACENTO1();
        private Runnable alEditar = () -> {};
        private boolean silencio;

        CampoNumero(String placeholder) {
            super(null);
            this.placeholder = placeholder;
            setOpaque(false);
            campo.setOpaque(false);
            campo.setBorder(BorderFactory.createEmptyBorder());
            campo.setForeground(Estilo.TEXTO);
            campo.setCaretColor(Color.WHITE);
            campo.setSelectionColor(Estilo.alfa(acento, 120));
            campo.setSelectedTextColor(Color.WHITE);
            campo.setDisabledTextColor(Estilo.TEXTO);
            campo.setFont(Estilo.fuenteUI(Estilo.SEMI, 16f));
            campo.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { cambio(); }
                @Override public void removeUpdate(DocumentEvent e) { cambio(); }
                @Override public void changedUpdate(DocumentEvent e) { cambio(); }
            });
            campo.addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { foco.ir(1, 200); campo.selectAll(); }
                @Override public void focusLost(FocusEvent e) { foco.ir(0, 260); }
            });
            add(campo);
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { campo.requestFocusInWindow(); }
            });
        }

        private static Color ACENTO1() {
            return EstadisticasProblemas.ACENTO1;
        }

        private void cambio() {
            if (silencio) return;
            if (estado != Estado.NEUTRO) {
                estado = Estado.NEUTRO;
                repaint();
            }
            alEditar.run();
            repaint();
        }

        void setAlEditar(Runnable r) { alEditar = r; }

        void setTexto(String t) {
            silencio = true;
            campo.setText(t);
            silencio = false;
            repaint();
        }

        String texto() { return campo.getText(); }

        void setEstado(Estado e) {
            estado = e;
            repaint();
        }

        void setEditable(boolean b) {
            campo.setEditable(b);
            campo.setFocusable(b);
        }

        @Override
        public void doLayout() {
            Font f = Estilo.fuenteUI(Estilo.SEMI, 16f);
            if (!f.equals(campo.getFont())) campo.setFont(f);
            campo.setBounds(Estilo.px(14), Estilo.px(5), getWidth() - Estilo.px(28), getHeight() - Estilo.px(10));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Estilo.suavizar(g2);
            Dimension d = Estilo.unidadesDeDiseno(g2, getWidth(), getHeight());
            double f = foco.get();
            RoundRectangle2D r = new RoundRectangle2D.Double(2.5, 2.5, d.width - 5, d.height - 5, 14, 14);
            Color borde = switch (estado) {
                case OK -> Estilo.EXITO;
                case MAL -> Estilo.ERROR;
                default -> Estilo.mezclar(Estilo.BORDE_FUERTE, Estilo.alfa(acento, 230), f);
            };
            if (f > 0.01 || estado != Estado.NEUTRO) {
                g2.setColor(Estilo.alfa(borde, (int) (estado != Estado.NEUTRO ? 45 : 55 * f)));
                g2.setStroke(new BasicStroke(5f));
                g2.draw(r);
            }
            Color fondo = switch (estado) {
                case OK -> Estilo.mezclar(Estilo.CAMPO, Estilo.EXITO, 0.08);
                case MAL -> Estilo.mezclar(Estilo.CAMPO, Estilo.ERROR, 0.08);
                default -> Estilo.mezclar(Estilo.CAMPO, new Color(0x121832), f);
            };
            g2.setColor(fondo);
            g2.fill(r);
            g2.setStroke(new BasicStroke(1.3f));
            g2.setColor(borde);
            g2.draw(r);
            if (campo.getText().isEmpty() && placeholder != null && f < 0.5) {
                g2.setFont(Estilo.fuente(Estilo.NORMAL, 15f));
                g2.setColor(Estilo.TEXTO_TENUE);
                FontMetrics fmt = g2.getFontMetrics();
                g2.drawString(Estilo.recortar(fmt, placeholder, d.width - 30), 15, (float) (d.height - fmt.getHeight()) / 2f + fmt.getAscent());
            }
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ botón de opción

    /** Botón de una opción, con texto que se ajusta en varias líneas. */
    static final class OpcionBoton extends JComponent {
        private static final float TAM = 14.5f;
        final String valor, texto;
        private boolean seleccionada, habilitada = true;
        private Estado estado = Estado.NEUTRO;
        private boolean correcta; // se marca al mostrar la solución
        private final ValorAnimado hover = new ValorAnimado(0, this::repaint);
        private final ValorAnimado sel = new ValorAnimado(0, this::repaint);

        OpcionBoton(String valor, String texto, Runnable alElegir) {
            this.valor = valor;
            this.texto = texto;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { if (habilitada) hover.ir(1, 150); }
                @Override public void mouseExited(MouseEvent e) { hover.ir(0, 220); }
                @Override public void mouseClicked(MouseEvent e) {
                    if (habilitada && SwingUtilities.isLeftMouseButton(e)) alElegir.run();
                }
            });
        }

        int anchoNatural() {
            return ancho(this, texto, Estilo.SEMI, TAM) + 50;
        }

        int alto(int w) {
            return altoTexto(this, texto, Estilo.SEMI, TAM, w - 42, 1.25, 0) + 20;
        }

        void setSeleccionada(boolean s) {
            seleccionada = s;
            sel.ir(s ? 1 : 0, 180);
        }

        void setEstado(Estado e) { estado = e; repaint(); }
        void setCorrecta(boolean c) { correcta = c; repaint(); }

        void setHabilitada(boolean h) {
            habilitada = h;
            setCursor(Cursor.getPredefinedCursor(h ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            if (!h) hover.fijar(0);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Estilo.suavizar(g2);
            Dimension d = Estilo.unidadesDeDiseno(g2, getWidth(), getHeight());
            double hv = hover.get(), s = sel.get();
            RoundRectangle2D r = new RoundRectangle2D.Double(1, 1, d.width - 2, d.height - 2, 14, 14);
            Color ac = EstadisticasProblemas.ACENTO1;
            Color c = estado == Estado.OK ? Estilo.EXITO : estado == Estado.MAL ? Estilo.ERROR : ac;
            g2.setColor(Estilo.mezclar(new Color(255, 255, 255, (int) (10 + 12 * hv)), Estilo.alfa(c, 52), s));
            g2.fill(r);
            g2.setStroke(new BasicStroke(1.3f));
            Color borde = Estilo.mezclar(Estilo.BORDE_FUERTE, Estilo.alfa(c, 230), Math.max(s, hv * 0.4));
            if (correcta && !seleccionada) {
                borde = Estilo.alfa(Estilo.EXITO, 200);
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{5f, 4f}, 0f));
            }
            g2.setColor(borde);
            g2.draw(r);
            // Indicador circular
            double cy = 10 + Estilo.fuente(Estilo.SEMI, TAM).getSize2D() * 0.62;
            Ellipse2D punto = new Ellipse2D.Double(12, cy - 6, 12, 12);
            g2.setColor(Estilo.mezclar(Estilo.BORDE_FUERTE, c, s));
            g2.setStroke(new BasicStroke(1.4f));
            g2.draw(punto);
            if (s > 0.02) {
                double rr = 3.2 * s;
                g2.setColor(c);
                g2.fill(new Ellipse2D.Double(18 - rr, cy - rr, 2 * rr, 2 * rr));
            }
            parrafo(g2, texto, Estilo.SEMI, TAM, Estilo.mezclar(Estilo.TEXTO_SUAVE, Estilo.TEXTO, Math.max(s, hv)), 32, 9,
                    d.width - 42, 1.25, 0);
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ barra inferior de botones

    /** Coloca dos grupos de botones (izquierda y derecha); si no caben en una fila, usa dos. Devuelve el alto usado. */
    static int filaBotones(List<UI.Boton> izquierda, List<UI.Boton> derecha, int x0, int ancho, int y, boolean aplicar) {
        int gap = 10, alto = 44;
        int wi = 0, wd = 0;
        for (UI.Boton b : izquierda) if (b.isVisible()) wi += UI.prefDiseno(b).width + gap;
        for (UI.Boton b : derecha) if (b.isVisible()) wd += UI.prefDiseno(b).width + gap;
        boolean dosFilas = wi + wd + 20 > ancho;
        int yi = y, yd = dosFilas && wi > 0 ? y + alto + 10 : y;
        if (aplicar) {
            int x = x0;
            for (UI.Boton b : izquierda) {
                if (!b.isVisible()) continue;
                int bw = UI.prefDiseno(b).width;
                UI.poner(b, x, yi, bw, alto);
                x += bw + gap;
            }
            x = x0 + ancho;
            for (int k = derecha.size() - 1; k >= 0; k--) {
                UI.Boton b = derecha.get(k);
                if (!b.isVisible()) continue;
                int bw = UI.prefDiseno(b).width;
                x -= bw;
                // Si ni así cabe, el botón encoge.
                if (x < x0) {
                    bw = Math.max(60, bw - (x0 - x));
                    x = x0;
                }
                UI.poner(b, x, yd, bw, alto);
                x -= gap;
            }
        }
        return (yd - y) + alto;
    }

    /** Raya de color del encabezado (como en las demás pantallas). */
    static void rayaTitulo(Graphics2D g, double x0, double y, double e) {
        g.setPaint(new GradientPaint((float) x0, 0, EstadisticasProblemas.ACENTO1, (float) x0 + 60, 0, EstadisticasProblemas.ACENTO2));
        g.fill(new RoundRectangle2D.Double(x0, y, 44 * e, 4, 4, 4));
    }

    // ------------------------------------------------------------------ panel plegable

    /**
     * Panel con encabezado que se abre y se cierra al hacer clic. Su contenido son bloques apilados que se
     * desplazan dentro del propio panel; el título del primer bloque se recorta porque ya está en el encabezado.
     */
    static final class Plegable extends JPanel {
        static final int CAB = 46, RECORTE = 36;
        private String titulo;
        boolean abierto;
        private final Color acento;
        private Supplier<String> extra = () -> null;
        private Supplier<Color> colorExtra = () -> Estilo.TEXTO_TENUE;
        private Runnable alCambiar = () -> {};
        private final Interior interior = new Interior();
        final JScrollPane scroll;
        private final ValorAnimado hover = new ValorAnimado(0, this::repaint);
        private final ValorAnimado giro;

        Plegable(String titulo, Color acento, boolean abierto) {
            super(null);
            this.titulo = titulo;
            this.acento = acento;
            this.abierto = abierto;
            this.giro = new ValorAnimado(abierto ? 1 : 0, this::repaint);
            setOpaque(false);
            scroll = new JScrollPane(interior);
            estilizar(scroll);
            scroll.setVisible(abierto);
            add(scroll);
            MouseAdapter m = new MouseAdapter() {
                private boolean enCabecera(MouseEvent e) {
                    return e.getY() < Estilo.px(CAB);
                }

                @Override public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e) && enCabecera(e)) setAbierto(!Plegable.this.abierto);
                }

                @Override public void mouseMoved(MouseEvent e) {
                    boolean c = enCabecera(e);
                    setCursor(Cursor.getPredefinedCursor(c ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    hover.ir(c ? 1 : 0, c ? 140 : 220);
                }

                @Override public void mouseExited(MouseEvent e) {
                    hover.ir(0, 220);
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private static void estilizar(JScrollPane sp) {
            UI.estilizarScroll(sp);
            sp.getVerticalScrollBar().setUnitIncrement(Estilo.px(26));
        }

        void setTitulo(String t) {
            titulo = t;
            repaint();
        }

        void setExtra(Supplier<String> texto, Supplier<Color> color) {
            extra = texto;
            colorExtra = color;
        }

        void setAlCambiar(Runnable r) {
            alCambiar = r;
        }

        void setAbierto(boolean a) {
            if (a == abierto) return;
            abierto = a;
            giro.ir(a ? 1 : 0, 200);
            scroll.setVisible(a);
            alCambiar.run();
        }

        void poner(List<? extends Bloque> bloques) {
            interior.poner(bloques);
            arriba();
        }

        void arriba() {
            SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
        }

        /** Tras un cambio de contenido (pistas, calificación…). */
        void refrescar() {
            interior.revalidate();
            interior.repaint();
            repaint();
        }

        /** Alto que necesitaría el panel para mostrar todo sin desplazarse (diseño). */
        int altoNecesario(int w) {
            return abierto ? CAB + interior.disponer(w - 22, false) + 8 : CAB;
        }

        @Override
        public void doLayout() {
            Dimension d = UI.diseno(this);
            UI.poner(scroll, 4, CAB, d.width - 8, Math.max(0, d.height - CAB - 6));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Estilo.suavizar(g2);
            Dimension d = Estilo.unidadesDeDiseno(g2, getWidth(), getHeight());
            tarjeta(g2, d.width, d.height);
            double hv = hover.get();
            if (hv > 0.01) {
                g2.setColor(new Color(255, 255, 255, (int) (9 * hv)));
                g2.fill(new RoundRectangle2D.Double(1, 1, d.width - 2, CAB - 1, 20, 20));
            }
            // Flecha que gira al abrir
            double cx = PAD - 2, cy = CAB / 2.0;
            Graphics2D gf = (Graphics2D) g2.create();
            gf.translate(cx, cy);
            gf.rotate(Math.PI / 2 * giro.get());
            gf.setColor(Estilo.mezclar(acento, Color.WHITE, 0.2 + 0.3 * hv));
            gf.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D p = new Path2D.Double();
            p.moveTo(-2.5, -5);
            p.lineTo(2.5, 0);
            p.lineTo(-2.5, 5);
            gf.draw(p);
            gf.dispose();

            g2.setFont(Estilo.etiqueta(12.5f));
            g2.setColor(Estilo.mezclar(acento, Color.WHITE, 0.15 + 0.25 * hv));
            String t = titulo.toUpperCase();
            FontMetrics fm = g2.getFontMetrics();
            double tx = PAD + 14;
            g2.drawString(t, (float) tx, (float) (cy + fm.getAscent() / 2.0 - 2));
            String e = extra.get();
            if (e != null && !e.isEmpty()) {
                g2.setFont(Estilo.fuente(Estilo.SEMI, 13f));
                FontMetrics fe = g2.getFontMetrics();
                double libre = d.width - PAD - (tx + fm.stringWidth(t) + 20);
                if (libre > 40) {
                    String s = Estilo.recortar(fe, e, libre);
                    g2.setColor(colorExtra.get());
                    g2.drawString(s, (float) (d.width - PAD - fe.stringWidth(s)), (float) (cy + fe.getAscent() / 2.0 - 2));
                }
            }
            if (abierto) {
                g2.setColor(Estilo.BORDE);
                g2.fill(new Rectangle2D.Double(PAD - 8, CAB - 1, d.width - 2 * PAD + 16, 1));
            }
            g2.dispose();
        }

        /** Contenido desplazable del panel. */
        private static final class Interior extends JPanel implements Scrollable {
            private final List<Bloque> bloques = new ArrayList<>();

            Interior() {
                super(null);
                setOpaque(false);
            }

            void poner(List<? extends Bloque> nuevos) {
                removeAll();
                bloques.clear();
                for (Bloque b : nuevos) {
                    b.conTarjeta = false;
                    bloques.add(b);
                    add(b);
                }
                revalidate();
                repaint();
            }

            int disponer(int w, boolean aplicar) {
                int y = 0;
                for (int i = 0; i < bloques.size(); i++) {
                    Bloque b = bloques.get(i);
                    if (!b.isVisible()) continue;
                    int rec = i == 0 ? RECORTE : 8;
                    int h = b.alto(w);
                    if (aplicar) UI.poner(b, 0, y - rec, w, h);
                    y += h - rec;
                }
                return y + 4;
            }

            @Override
            public void doLayout() {
                disponer(UI.diseno(this).width, true);
                for (Bloque b : bloques) b.doLayout();
            }

            @Override
            public Dimension getPreferredSize() {
                int real = getParent() != null && getParent().getWidth() > 0 ? getParent().getWidth() : 600;
                int w = (int) Math.round(real / Estilo.escala());
                return new Dimension(real, Estilo.px(disponer(w, false)));
            }

            @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
            @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return Estilo.px(26); }
            @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(26, r.height - 40); }
            @Override public boolean getScrollableTracksViewportWidth() { return true; }
            @Override public boolean getScrollableTracksViewportHeight() { return false; }
        }
    }

    /**
     * Reparte el alto {@code alto} entre los paneles visibles de una columna: los cerrados ocupan su
     * encabezado; los abiertos reciben lo que necesitan y, si no cabe todo, el espacio se reparte por igual
     * (cada uno desplaza su contenido por dentro).
     */
    static void columna(List<Plegable> paneles, int x, int y, int ancho, int alto) {
        List<Plegable> vis = new ArrayList<>();
        for (Plegable p : paneles) if (p.isVisible()) vis.add(p);
        int n = vis.size();
        if (n == 0) return;
        int gap = 10, disp = alto - gap * (n - 1);
        int[] h = new int[n], necesita = new int[n];
        List<Integer> abiertos = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            necesita[i] = vis.get(i).altoNecesario(ancho);
            if (vis.get(i).abierto) abiertos.add(i);
            else {
                h[i] = Plegable.CAB;
                disp -= Plegable.CAB;
            }
        }
        abiertos.sort(java.util.Comparator.comparingInt(i -> necesita[i]));
        int quedan = abiertos.size();
        for (int i : abiertos) {
            int parte = Math.max(Plegable.CAB + 60, disp / quedan);
            h[i] = Math.min(necesita[i], parte);
            disp -= h[i];
            quedan--;
        }
        int yy = y;
        for (int i = 0; i < n; i++) {
            UI.poner(vis.get(i), x, yy, ancho, h[i]);
            yy += h[i] + gap;
        }
    }

    // ------------------------------------------------------------------ fórmulas en notación matemática

    /**
     * Dibuja una fórmula con fracciones apiladas. Sintaxis: texto normal y {@code [numerador | denominador]}.
     * Usa Cambria (la fuente de las ecuaciones de Word) si está instalada. Medidas en unidades de diseño.
     */
    static final class Formula {
        private Formula() {}

        private record Tok(String txt, String num, String den) {
            boolean frac() { return num != null; }
        }

        private record Medida(double w, double asc, double desc) {}

        private record Linea(List<Tok> toks, List<Medida> med, double asc, double desc) {}

        private static String familia;

        static Font fuente(float tam) {
            if (familia == null) {
                java.util.Set<String> f = new java.util.HashSet<>(java.util.Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
                familia = f.contains("Cambria") ? "Cambria" : Font.SERIF;
            }
            return new Font(familia, Font.PLAIN, 12).deriveFont(tam);
        }

        private static FontMetrics m(float tam) {
            fm(null, Estilo.NORMAL, 12f); // asegura el medidor
            return medidor.getFontMetrics(fuente(tam));
        }

        private static List<Tok> tokens(String s) {
            List<Tok> r = new ArrayList<>();
            int i = 0;
            while (i < s.length()) {
                int a = s.indexOf('[', i), b = a < 0 ? -1 : s.indexOf(']', a);
                String texto = a < 0 || b < 0 ? s.substring(i) : s.substring(i, a);
                // Palabras con su espacio final, para poder partir la línea en cualquier espacio.
                int k = 0;
                while (k < texto.length()) {
                    int e = texto.indexOf(' ', k);
                    int fin = e < 0 ? texto.length() : e + 1;
                    while (fin < texto.length() && texto.charAt(fin) == ' ') fin++;
                    r.add(new Tok(texto.substring(k, fin), null, null));
                    k = fin;
                }
                if (a < 0 || b < 0) break;
                String[] p = s.substring(a + 1, b).split("\\|", 2);
                r.add(new Tok(null, p[0].trim(), p.length > 1 ? p[1].trim() : ""));
                i = b + 1;
            }
            return r;
        }

        private static Medida medir(Tok t, float tam) {
            FontMetrics f = m(tam);
            if (!t.frac()) return new Medida(f.stringWidth(t.txt()), f.getAscent(), f.getDescent());
            FontMetrics p = m(tam * 0.86f);
            double eje = f.getAscent() * 0.3;
            double w = Math.max(p.stringWidth(t.num()), p.stringWidth(t.den())) + 12;
            return new Medida(w, eje + 4 + p.getDescent() + p.getAscent(), -eje + 4 + p.getAscent() + p.getDescent());
        }

        private static List<Linea> lineas(String s, float tam, double ancho) {
            List<Linea> r = new ArrayList<>();
            List<Tok> act = new ArrayList<>();
            List<Medida> med = new ArrayList<>();
            double x = 0;
            for (Tok t : tokens(s)) {
                Medida md = medir(t, tam);
                double sinEspacio = t.frac() ? md.w() : m(tam).stringWidth(t.txt().stripTrailing());
                if (!act.isEmpty() && x + sinEspacio > ancho) {
                    r.add(cerrar(act, med));
                    act = new ArrayList<>();
                    med = new ArrayList<>();
                    x = 0;
                    if (!t.frac() && t.txt().isBlank()) continue;
                }
                act.add(t);
                med.add(md);
                x += md.w();
            }
            if (!act.isEmpty()) r.add(cerrar(act, med));
            return r;
        }

        private static Linea cerrar(List<Tok> t, List<Medida> m) {
            double a = 0, d = 0;
            for (Medida x : m) {
                a = Math.max(a, x.asc());
                d = Math.max(d, x.desc());
            }
            return new Linea(t, m, a, d);
        }

        static double alto(String s, float tam, double ancho) {
            double h = 0;
            List<Linea> ls = lineas(s, tam, ancho);
            for (Linea l : ls) h += l.asc() + l.desc();
            return h + Math.max(0, ls.size() - 1) * tam * 0.35;
        }

        static double ancho(String s, float tam) {
            return lineas(s, tam, Double.MAX_VALUE).get(0).med().stream().mapToDouble(Medida::w).sum();
        }

        /** Dibuja la fórmula y devuelve la y final. */
        static double pintar(Graphics2D g, String s, float tam, Color c, double x0, double y, double ancho) {
            Font f = fuente(tam), fp = fuente(tam * 0.86f);
            FontMetrics fm = m(tam), fmp = m(tam * 0.86f);
            g.setColor(c);
            for (Linea l : lineas(s, tam, ancho)) {
                double base = y + l.asc(), x = x0;
                for (int k = 0; k < l.toks().size(); k++) {
                    Tok t = l.toks().get(k);
                    Medida md = l.med().get(k);
                    if (!t.frac()) {
                        g.setFont(f);
                        g.drawString(t.txt(), (float) x, (float) base);
                    } else {
                        double barra = base - fm.getAscent() * 0.3;
                        g.setFont(fp);
                        g.drawString(t.num(), (float) (x + (md.w() - fmp.stringWidth(t.num())) / 2), (float) (barra - 4 - fmp.getDescent()));
                        g.drawString(t.den(), (float) (x + (md.w() - fmp.stringWidth(t.den())) / 2), (float) (barra + 4 + fmp.getAscent()));
                        g.setStroke(new BasicStroke(Math.max(1.2f, tam / 16f)));
                        g.draw(new Line2D.Double(x + 3, barra, x + md.w() - 3, barra));
                    }
                    x += md.w();
                }
                y = base + l.desc() + tam * 0.35;
            }
            return y - tam * 0.35;
        }
    }

    static <T> T ultimo(List<T> l) {
        return l.get(l.size() - 1);
    }

    /** Pantalla a la que se vuelve. */
    interface Destino extends Supplier<JComponent> {}
}
