package estudio;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * Fondo oscuro con resplandores del color de la materia actual y una retícula de puntos.
 * Cada combinación de colores se genera una sola vez; al cambiar de materia se funden dos imágenes.
 */
final class Fondo extends JPanel {
    private Color a1 = Estilo.PALETAS[0][0], a2 = Estilo.PALETAS[0][1];
    private BufferedImage actual, anterior;
    private String claveActual = "";
    private final ValorAnimado mezcla = new ValorAnimado(1, this::repaint);

    Fondo() {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(Estilo.FONDO_ABAJO);
    }

    void setAcento(Color c1, Color c2) {
        if (c1.equals(a1) && c2.equals(a2)) return;
        // Se congela lo que se ve ahora y se funde hacia el fondo nuevo.
        anterior = actual;
        a1 = c1;
        a2 = c2;
        claveActual = "";
        mezcla.fijar(anterior == null ? 1 : 0);
        mezcla.ir(1, 700);
    }

    /** Pinta el fondo completo (lo usa también la transición para capturar la pantalla saliente). */
    void pintarFondo(Graphics2D g, int w, int h) {
        if (w <= 0 || h <= 0) return;
        double escala = Cache.escala(g);
        String clave = w + "x" + h + "@" + escala + ":" + a1.getRGB() + ":" + a2.getRGB();
        if (!clave.equals(claveActual) || actual == null) {
            // Clave vacía = cambio de color (se conserva la imagen anterior para el fundido);
            // cualquier otro cambio es de tamaño y la anterior ya no sirve.
            if (!claveActual.isEmpty()) anterior = null;
            actual = generar(g.getDeviceConfiguration(), w, h, escala);
            claveActual = clave;
        }
        double t = mezcla.get();
        if (anterior != null && t < 1) {
            dibujar(g, anterior, w, h, 1f);
            dibujar(g, actual, w, h, (float) t);
        } else {
            anterior = null;
            dibujar(g, actual, w, h, 1f);
        }
    }

    private static void dibujar(Graphics2D g, BufferedImage img, int w, int h, float alfa) {
        Graphics2D g2 = (Graphics2D) g.create();
        if (alfa < 1f) g2.setComposite(AlphaComposite.SrcOver.derive(alfa));
        double sx = w / (double) img.getWidth(), sy = h / (double) img.getHeight();
        if (Math.abs(sx * Cache.escala(g) - 1) < 1e-6) {
            g2.scale(sx, sy);
            AffineTransform t = g2.getTransform();
            g2.setTransform(new AffineTransform(t.getScaleX(), 0, 0, t.getScaleY(),
                    Math.round(t.getTranslateX()), Math.round(t.getTranslateY())));
            g2.drawImage(img, 0, 0, null);
        } else {
            g2.drawImage(img, 0, 0, w, h, null);
        }
        g2.dispose();
    }

    @Override
    protected void paintComponent(Graphics g) {
        pintarFondo((Graphics2D) g, getWidth(), getHeight());
    }

    private BufferedImage generar(GraphicsConfiguration gc, int w, int h, double escala) {
        int iw = (int) Math.ceil(w * escala), ih = (int) Math.ceil(h * escala);
        BufferedImage img = gc != null ? gc.createCompatibleImage(iw, ih, Transparency.OPAQUE)
                : new BufferedImage(iw, ih, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.scale(escala, escala);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setPaint(new GradientPaint(0, 0, Estilo.FONDO_ARRIBA, 0, h, Estilo.FONDO_ABAJO));
        g.fillRect(0, 0, w, h);

        // Los resplandores son degradados muy suaves: se calculan a 1/4 de resolución y se amplían.
        int rw = Math.max(1, w / 4), rh = Math.max(1, h / 4);
        BufferedImage brillo = new BufferedImage(rw, rh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gb = brillo.createGraphics();
        float radio = Math.max(rw, rh);
        gb.setPaint(new RadialGradientPaint(new Point2D.Double(rw * 0.12, -rh * 0.08), radio * 0.75f,
                new float[]{0f, 1f}, new Color[]{Estilo.alfa(a1, 60), Estilo.alfa(a1, 0)}));
        gb.fillRect(0, 0, rw, rh);
        gb.setPaint(new RadialGradientPaint(new Point2D.Double(rw * 0.98, rh * 1.05), radio * 0.65f,
                new float[]{0f, 1f}, new Color[]{Estilo.alfa(a2, 48), Estilo.alfa(a2, 0)}));
        gb.fillRect(0, 0, rw, rh);
        gb.dispose();
        g.drawImage(brillo, 0, 0, w, h, null);

        // Retícula de puntos con una baldosa repetida (sin antialiasing por punto).
        int paso = 28;
        BufferedImage baldosa = new BufferedImage(paso * 2, paso * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gt = baldosa.createGraphics();
        Estilo.suavizar(gt);
        gt.scale(2, 2);
        gt.setColor(new Color(255, 255, 255, 9));
        gt.fill(new Ellipse2D.Double(paso / 2.0 - 0.9, paso / 2.0 - 0.9, 1.8, 1.8));
        gt.dispose();
        g.setPaint(new TexturePaint(baldosa, new Rectangle(0, 0, paso, paso)));
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }
}
