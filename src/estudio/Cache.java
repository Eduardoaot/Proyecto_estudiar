package estudio;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Caché de imágenes pre-renderizadas. Las sombras, resplandores, degradados radiales y fondos de tarjeta
 * se dibujan una sola vez y después se copian (una operación acelerada por la GPU).
 */
final class Cache {
    private Cache() {}

    private static final long MAX_BYTES = 384L << 20;
    private static final LinkedHashMap<String, BufferedImage> IMAGENES = new LinkedHashMap<>(128, 0.75f, true);
    private static long bytes;

    /**
     * Resolución a la que conviene generar la imagen: la de la pantalla multiplicada por el zoom
     * (así el contenido se ve nítido también con la ventana grande).
     */
    static double escala(Graphics2D g) {
        GraphicsConfiguration gc = g.getDeviceConfiguration();
        double pantalla = gc != null ? gc.getDefaultTransform().getScaleX() : 1;
        double s = pantalla * Estilo.escala();
        return Math.max(1, Math.min(4, Math.round(s * 4) / 4.0));
    }

    /**
     * Dibuja en (x, y) la imagen identificada por {@code clave} y tamaño w×h, creándola con {@code pintor}
     * (que dibuja en coordenadas locales 0..w, 0..h) si no existe.
     */
    static void dibujar(Graphics2D g, String clave, double x, double y, int w, int h, Consumer<Graphics2D> pintor) {
        if (w <= 0 || h <= 0) return;
        double esc = escala(g);
        String k = clave + '|' + w + 'x' + h + '@' + esc;
        BufferedImage img = IMAGENES.get(k);
        if (img == null) {
            img = crear(g, w, h, esc, pintor);
            IMAGENES.put(k, img);
            bytes += (long) img.getWidth() * img.getHeight() * 4;
            recortar();
        }
        AffineTransform antes = g.getTransform();
        g.translate(x, y);
        g.scale(1 / esc, 1 / esc);
        AffineTransform t = g.getTransform();
        if (t.getShearX() == 0 && t.getShearY() == 0
                && Math.abs(t.getScaleX() - 1) < 1e-9 && Math.abs(t.getScaleY() - 1) < 1e-9) {
            // Traslación pura: se alinea al píxel para usar la copia directa más rápida y nítida.
            g.setTransform(AffineTransform.getTranslateInstance(Math.round(t.getTranslateX()), Math.round(t.getTranslateY())));
        }
        g.drawImage(img, 0, 0, null);
        g.setTransform(antes);
    }

    private static BufferedImage crear(Graphics2D destino, int w, int h, double esc, Consumer<Graphics2D> pintor) {
        int iw = (int) Math.ceil(w * esc), ih = (int) Math.ceil(h * esc);
        GraphicsConfiguration gc = destino.getDeviceConfiguration();
        BufferedImage img = gc != null
                ? gc.createCompatibleImage(iw, ih, Transparency.TRANSLUCENT)
                : new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = img.createGraphics();
        g.scale(esc, esc);
        Estilo.suavizar(g);
        // El texto LCD no se puede componer sobre superficies translúcidas.
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        pintor.accept(g);
        g.dispose();
        return img;
    }

    private static void recortar() {
        Iterator<Map.Entry<String, BufferedImage>> it = IMAGENES.entrySet().iterator();
        while (bytes > MAX_BYTES && it.hasNext()) {
            BufferedImage img = it.next().getValue();
            bytes -= (long) img.getWidth() * img.getHeight() * 4;
            img.flush();
            it.remove();
        }
    }
}
