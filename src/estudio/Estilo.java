package estudio;

import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/** Paleta, tipografías y utilidades de dibujo compartidas por toda la interfaz. */
final class Estilo {
    private Estilo() {}

    static final Color FONDO_ARRIBA = new Color(0x0D1122);
    static final Color FONDO_ABAJO = new Color(0x080A14);
    static final Color SUPERFICIE = new Color(0x141A2E);
    static final Color SUPERFICIE_ALTA = new Color(0x1C2342);
    static final Color CAMPO = new Color(0x0E1326);
    static final Color BORDE = new Color(255, 255, 255, 22);
    static final Color BORDE_FUERTE = new Color(255, 255, 255, 42);
    static final Color TEXTO = new Color(0xECEEF8);
    static final Color TEXTO_SUAVE = new Color(0xA3A9C6);
    static final Color TEXTO_TENUE = new Color(0x6A7194);
    static final Color EXITO = new Color(0x2DD4A0);
    static final Color ERROR = new Color(0xFB5B7A);
    static final Color AVISO = new Color(0xFBBF24);
    static final Color INFO = new Color(0x60A5FA);

    static final Color[][] PALETAS = {
            {new Color(0x22D3EE), new Color(0x6366F1)},
            {new Color(0xF59E0B), new Color(0xEC4899)},
            {new Color(0x34D399), new Color(0x3B82F6)},
            {new Color(0xA78BFA), new Color(0xF472B6)},
    };

    static final int NORMAL = 0, SEMI = 1, NEGRITA = 2;

    /**
     * Zoom de toda la interfaz. 1 = tamaño de diseño (ventana de 1280×820). Crece con la ventana,
     * para que al agrandarla se agrande el contenido y no solo los márgenes.
     */
    private static double escala = 1;
    private static double zoomManual = 1;

    static double escala() {
        return escala;
    }

    static double zoomManual() {
        return zoomManual;
    }

    /** Ajuste del usuario (Ctrl + rueda o Ctrl + / Ctrl −). Devuelve true si cambió. */
    static boolean setZoomManual(double z) {
        z = Math.max(0.7, Math.min(1.8, Math.round(z * 20) / 20.0));
        if (Math.abs(z - zoomManual) < 0.001) return false;
        zoomManual = z;
        return true;
    }

    /** Devuelve true si el factor cambió y hay que rehacer la distribución. */
    static boolean setEscala(double nueva) {
        nueva = Math.max(0.85, Math.min(2.0, Math.round(nueva * 20) / 20.0));
        if (Math.abs(nueva - escala) < 0.001) return false;
        escala = nueva;
        FUENTES.clear();
        ETIQUETAS.clear();
        return true;
    }

    /** Fuente ya multiplicada por el zoom, para componentes de texto que Swing dibuja sin escalar. */
    static Font fuenteUI(int peso, float tam) {
        return fuente(peso, (float) (tam * escala));
    }

    /**
     * Prepara {@code g} para dibujar en unidades de diseño y devuelve el tamaño del componente
     * en esas unidades. Así un componente se dibuja igual y el zoom lo agranda todo por igual.
     */
    static Dimension unidadesDeDiseno(Graphics2D g, int w, int h) {
        g.scale(escala, escala);
        return new Dimension((int) Math.round(w / escala), (int) Math.round(h / escala));
    }

    /** Pasa un punto del ratón (píxeles reales) a unidades de diseño. */
    static Point aDiseno(Point p) {
        return new Point((int) Math.round(p.x / escala), (int) Math.round(p.y / escala));
    }

    /** Convierte una medida de diseño (píxeles a escala 1) al tamaño actual. */
    static int px(double v) {
        return (int) Math.round(v * escala);
    }

    static double pxd(double v) {
        return v * escala;
    }

    private static String familia;
    private static String familiaSemi;
    private static final Map<Long, Font> FUENTES = new HashMap<>();
    private static final Map<Long, Font> ETIQUETAS = new HashMap<>();

    private static long claveFuente(int peso, float tam) {
        return ((long) peso << 32) | Float.floatToIntBits(tam);
    }

    static Font fuente(int peso, float tam) {
        Font f = FUENTES.get(claveFuente(peso, tam));
        if (f != null) return f;
        if (familia == null) {
            Set<String> disponibles = new HashSet<>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            familia = disponibles.contains("Segoe UI") ? "Segoe UI" : Font.SANS_SERIF;
            familiaSemi = disponibles.contains("Segoe UI Semibold") ? "Segoe UI Semibold" : null;
        }
        Font base;
        if (peso == SEMI && familiaSemi != null) base = new Font(familiaSemi, Font.PLAIN, 12);
        else base = new Font(familia, peso >= SEMI ? Font.BOLD : Font.PLAIN, 12);
        f = base.deriveFont(tam);
        FUENTES.put(claveFuente(peso, tam), f);
        return f;
    }

    /** Fuente pequeña en mayúsculas con espaciado entre letras, para etiquetas. */
    static Font etiqueta(float tam) {
        return ETIQUETAS.computeIfAbsent(claveFuente(SEMI, tam),
                k -> fuente(SEMI, tam).deriveFont(Map.of(TextAttribute.TRACKING, 0.09f)));
    }

    static void suavizar(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    static Color alfa(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    /** Multiplica la opacidad actual del contexto gráfico. */
    static void alfa(Graphics2D g, float a) {
        Composite c = g.getComposite();
        float base = (c instanceof AlphaComposite ac && ac.getRule() == AlphaComposite.SRC_OVER) ? ac.getAlpha() : 1f;
        g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, base * a))));
    }

    static Color mezclar(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                (int) Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    static double limitar(double v) {
        return Math.max(0, Math.min(1, v));
    }

    static double easeOut(double t) {
        t = limitar(t);
        return 1 - Math.pow(1 - t, 3);
    }

    static double easeIn(double t) {
        t = limitar(t);
        return t * t * t;
    }

    static double easeInOut(double t) {
        t = limitar(t);
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    static double easeOutBack(double t) {
        t = limitar(t);
        double c1 = 1.70158, c3 = c1 + 1;
        return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
    }

    /**
     * Resplandor suave alrededor de una forma (se dibuja por fuera, reservar margen).
     * Se pre-renderiza con el color base y se copia con la opacidad {@code alfa}.
     */
    static void halo(Graphics2D g, Shape forma, Color c, int tam, double alfa) {
        if (alfa <= 0.01) return;
        Rectangle2D b = forma.getBounds2D();
        String tipo = forma instanceof RoundRectangle2D rr ? "rr" + rr.getArcWidth() : forma.getClass().getSimpleName();
        int w = (int) Math.ceil(b.getWidth()) + 2 * tam + 2, h = (int) Math.ceil(b.getHeight()) + 2 * tam + 2;
        double ox = b.getX() - tam - 1, oy = b.getY() - tam - 1;
        Shape local = AffineTransform.getTranslateInstance(-ox, -oy).createTransformedShape(forma);
        Graphics2D g2 = (Graphics2D) g.create();
        alfa(g2, (float) limitar(alfa));
        Cache.dibujar(g2, "halo:" + tipo + ':' + c.getRGB() + ':' + tam, ox, oy, w, h, gc -> {
            int pasos = Math.max(1, tam / 2);
            for (int i = pasos; i >= 1; i--) {
                double f = 1 - (i - 1) / (double) pasos;
                gc.setColor(alfa(c, (int) (c.getAlpha() * f * f / pasos * 1.6)));
                gc.setStroke(new BasicStroke(i * 2f * tam / pasos, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                gc.draw(local);
            }
        });
        g2.dispose();
    }

    /** Sombra difusa bajo un rectángulo redondeado (pre-renderizada). */
    static void sombra(Graphics2D g, double x, double y, double w, double h, double radio, int tam, int opacidad) {
        int iw = (int) Math.ceil(w), ih = (int) Math.ceil(h);
        int m = tam + 1;
        Cache.dibujar(g, "sombra:" + radio + ':' + tam + ':' + opacidad, x - m, y - m, iw + 2 * m, ih + 2 * m + tam, gc -> {
            for (int i = tam; i >= 1; i -= 2) {
                double f = 1 - i / (double) (tam + 1);
                gc.setColor(new Color(0, 0, 0, (int) Math.max(0, Math.min(255, opacidad * f * f / (tam / 2.0) * 1.8))));
                gc.fill(new RoundRectangle2D.Double(m - i, m - i + tam * 0.45, iw + 2 * i, ih + 2 * i, radio + 2 * i, radio + 2 * i));
            }
        });
    }

    static List<String> partir(String texto, FontMetrics fm, double ancho) {
        List<String> lineas = new ArrayList<>();
        for (String parrafo : texto.split("\n", -1)) {
            StringBuilder actual = new StringBuilder();
            for (String palabra : parrafo.split(" +")) {
                if (palabra.isEmpty()) continue;
                String prueba = actual.length() == 0 ? palabra : actual + " " + palabra;
                // Margen de 2 px: con métricas fraccionarias una línea "justa" se sale al dibujarla.
                if (actual.length() > 0 && fm.stringWidth(prueba) > ancho - 2) {
                    lineas.add(actual.toString());
                    actual = new StringBuilder(palabra);
                } else {
                    actual = new StringBuilder(prueba);
                }
            }
            lineas.add(actual.toString());
        }
        return lineas;
    }

    /** Dibuja un párrafo con ajuste de línea y devuelve la coordenada y final. */
    static double parrafo(Graphics2D g, String texto, double x, double y, double ancho, int maxLineas, double interlineado) {
        FontMetrics fm = g.getFontMetrics();
        List<String> lineas = partir(texto, fm, ancho);
        if (maxLineas > 0 && lineas.size() > maxLineas) {
            List<String> cortadas = new ArrayList<>(lineas.subList(0, maxLineas));
            cortadas.set(maxLineas - 1, recortar(fm, lineas.get(maxLineas - 1) + " " + lineas.get(maxLineas), ancho));
            lineas = cortadas;
        }
        double alto = fm.getHeight() * interlineado;
        for (String l : lineas) {
            g.drawString(l, (float) x, (float) (y + fm.getAscent()));
            y += alto;
        }
        return y;
    }

    static int lineasDe(String texto, FontMetrics fm, double ancho, int maxLineas) {
        int n = partir(texto, fm, ancho).size();
        return maxLineas > 0 ? Math.min(n, maxLineas) : n;
    }

    static String recortar(FontMetrics fm, String s, double ancho) {
        if (fm.stringWidth(s) <= ancho) return s;
        String e = "…";
        int n = s.length();
        while (n > 0 && fm.stringWidth(s.substring(0, n) + e) > ancho) n--;
        return s.substring(0, n).stripTrailing() + e;
    }

    static void textoCentrado(Graphics2D g, String s, double cx, double cy) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, (float) (cx - fm.stringWidth(s) / 2.0), (float) (cy - fm.getHeight() / 2.0 + fm.getAscent()));
    }

    /** Iconos vectoriales: 0 onda, 1 cronómetro, 2 libro, 3 capas. */
    static void icono(Graphics2D g, int tipo, double cx, double cy, double s, Color c) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(c);
        BasicStroke grueso = new BasicStroke((float) (s * 0.075), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        BasicStroke fino = new BasicStroke((float) (s * 0.055), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        g2.setStroke(grueso);
        switch (tipo) {
            case 0 -> {
                Path2D onda = new Path2D.Double();
                double x0 = cx - s * 0.4, w = s * 0.8;
                for (int i = 0; i <= 48; i++) {
                    double t = i / 48.0;
                    double x = x0 + w * t, y = cy - s * 0.13 - Math.sin(t * Math.PI * 2) * s * 0.15;
                    if (i == 0) onda.moveTo(x, y); else onda.lineTo(x, y);
                }
                g2.draw(onda);
                Path2D cuadrada = new Path2D.Double();
                double yb = cy + s * 0.3, ya = cy + s * 0.12;
                cuadrada.moveTo(x0, yb);
                cuadrada.lineTo(x0 + w * 0.2, yb);
                cuadrada.lineTo(x0 + w * 0.2, ya);
                cuadrada.lineTo(x0 + w * 0.45, ya);
                cuadrada.lineTo(x0 + w * 0.45, yb);
                cuadrada.lineTo(x0 + w * 0.7, yb);
                cuadrada.lineTo(x0 + w * 0.7, ya);
                cuadrada.lineTo(x0 + w, ya);
                g2.setStroke(fino);
                g2.draw(cuadrada);
            }
            case 1 -> {
                double r = s * 0.31, ccy = cy + s * 0.07;
                g2.draw(new Ellipse2D.Double(cx - r, ccy - r, 2 * r, 2 * r));
                g2.setStroke(fino);
                g2.draw(new Line2D.Double(cx, ccy, cx, ccy - r * 0.62));
                g2.draw(new Line2D.Double(cx, ccy, cx + r * 0.42, ccy + r * 0.22));
                g2.draw(new Line2D.Double(cx - s * 0.09, ccy - r - s * 0.11, cx + s * 0.09, ccy - r - s * 0.11));
                g2.draw(new Line2D.Double(cx, ccy - r - s * 0.11, cx, ccy - r));
                g2.draw(new Line2D.Double(cx + r * 0.8, ccy - r * 0.8, cx + r * 1.0, ccy - r * 1.0));
            }
            case 2 -> {
                double w = s * 0.34, h = s * 0.52, top = cy - h / 2;
                Path2D izq = new Path2D.Double();
                izq.moveTo(cx, top + s * 0.05);
                izq.quadTo(cx - w * 0.5, top - s * 0.03, cx - w, top + s * 0.02);
                izq.lineTo(cx - w, top + h);
                izq.quadTo(cx - w * 0.5, top + h - s * 0.05, cx, top + h + s * 0.03);
                izq.closePath();
                Path2D der = new Path2D.Double();
                der.moveTo(cx, top + s * 0.05);
                der.quadTo(cx + w * 0.5, top - s * 0.03, cx + w, top + s * 0.02);
                der.lineTo(cx + w, top + h);
                der.quadTo(cx + w * 0.5, top + h - s * 0.05, cx, top + h + s * 0.03);
                der.closePath();
                g2.draw(izq);
                g2.draw(der);
            }
            default -> {
                g2.setStroke(fino);
                for (int i = 0; i < 3; i++) {
                    double y = cy - s * 0.16 + i * s * 0.16;
                    Path2D rombo = new Path2D.Double();
                    rombo.moveTo(cx, y - s * 0.14);
                    rombo.lineTo(cx + s * 0.34, y);
                    rombo.lineTo(cx, y + s * 0.14);
                    rombo.lineTo(cx - s * 0.34, y);
                    rombo.closePath();
                    if (i == 0) g2.draw(rombo);
                    else {
                        Path2D v = new Path2D.Double();
                        v.moveTo(cx - s * 0.34, y);
                        v.lineTo(cx, y + s * 0.14);
                        v.lineTo(cx + s * 0.34, y);
                        g2.draw(v);
                    }
                }
            }
        }
        g2.dispose();
    }

    /** Palomita dibujada progresivamente (progreso 0..1). */
    static void palomita(Graphics2D g, double cx, double cy, double s, double progreso) {
        polilinea(g, new double[]{cx - s * 0.30, cx - s * 0.08, cx + s * 0.32},
                new double[]{cy + s * 0.01, cy + s * 0.22, cy - s * 0.22}, progreso);
    }

    /** Cruz dibujada progresivamente (progreso 0..1). */
    static void cruz(Graphics2D g, double cx, double cy, double s, double progreso) {
        double d = s * 0.22;
        polilinea(g, new double[]{cx - d, cx + d}, new double[]{cy - d, cy + d}, Math.min(1, progreso * 2));
        if (progreso > 0.5) {
            polilinea(g, new double[]{cx + d, cx - d}, new double[]{cy - d, cy + d}, (progreso - 0.5) * 2);
        }
    }

    static void polilinea(Graphics2D g, double[] xs, double[] ys, double progreso) {
        double total = 0;
        for (int i = 1; i < xs.length; i++) total += Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
        double restante = total * limitar(progreso);
        Path2D p = new Path2D.Double();
        p.moveTo(xs[0], ys[0]);
        for (int i = 1; i < xs.length && restante > 0; i++) {
            double seg = Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
            double f = Math.min(1, restante / seg);
            p.lineTo(xs[i - 1] + (xs[i] - xs[i - 1]) * f, ys[i - 1] + (ys[i] - ys[i - 1]) * f);
            restante -= seg;
        }
        g.draw(p);
    }
}
