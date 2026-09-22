package estudio;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

/** Contenedor que cambia de pantalla con un fundido y desplazamiento suave. */
final class Transicion extends JPanel {
    private JComponent actual;
    private BufferedImage imagenVieja;
    private double progreso = 1;
    private int direccion;
    private boolean animando;
    private Anim.Tarea timer;

    Transicion() {
        super(null);
        setOpaque(false);
    }

    @Override
    public void doLayout() {
        for (Component c : getComponents()) c.setBounds(0, 0, getWidth(), getHeight());
    }

    void mostrar(JComponent nueva, int dir) {
        if (timer != null && timer.isRunning()) {
            timer.stop();
            terminar();
        }
        JComponent vieja = actual;
        if (vieja instanceof Pantalla p) p.alOcultar();
        actual = nueva;
        if (vieja == null || getWidth() <= 0 || !isShowing()) {
            removeAll();
            add(nueva);
            revalidate();
            repaint();
            if (nueva instanceof Pantalla p) SwingUtilities.invokeLater(p::alMostrar);
            return;
        }
        imagenVieja = captura(vieja);
        remove(vieja);
        add(nueva);
        nueva.setBounds(0, 0, getWidth(), getHeight());
        nueva.validate();
        direccion = dir;
        progreso = 0;
        animando = true;
        if (nueva instanceof Pantalla p) p.alMostrar();
        timer = Anim.ejecutar(480, 0, t -> {
            progreso = t;
            repaint();
        }, this::terminar);
    }

    private void terminar() {
        animando = false;
        progreso = 1;
        imagenVieja = null;
        repaint();
    }

    /** Captura la pantalla saliente junto con el fondo, en una imagen opaca (copia rápida). */
    private BufferedImage captura(JComponent c) {
        GraphicsConfiguration gc = getGraphicsConfiguration();
        double escala = gc != null ? gc.getDefaultTransform().getScaleX() : 1;
        int w = Math.max(1, (int) Math.ceil(c.getWidth() * escala));
        int h = Math.max(1, (int) Math.ceil(c.getHeight() * escala));
        BufferedImage img = gc != null ? gc.createCompatibleImage(w, h, Transparency.OPAQUE)
                : new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.scale(escala, escala);
        if (getParent() instanceof Fondo f) f.pintarFondo(g, c.getWidth(), c.getHeight());
        c.paint(g);
        g.dispose();
        return img;
    }

    @Override
    protected void paintChildren(Graphics g) {
        if (!animando || imagenVieja == null) {
            super.paintChildren(g);
            return;
        }
        int w = getWidth(), h = getHeight();
        double e = Estilo.easeOut(progreso);
        // 1) Pantalla nueva en vivo (solo se desplaza: sin composición translúcida, que es cara).
        Graphics2D gn = (Graphics2D) g.create();
        gn.translate(Math.round(direccion * 80 * (1 - e)), 0);
        super.paintChildren(gn);
        gn.dispose();
        // 2) Pantalla vieja encima, desvaneciéndose: una sola copia de imagen acelerada.
        float alfa = (float) (1 - Estilo.limitar(progreso * 1.6));
        if (alfa > 0.005f) {
            Graphics2D gv = (Graphics2D) g.create();
            gv.setComposite(AlphaComposite.SrcOver.derive(alfa));
            double dx = -direccion * 50 * e;
            double esc = gv.getTransform().getScaleX();
            gv.translate(Math.round(dx * esc) / esc, 0);
            gv.scale(w / (double) imagenVieja.getWidth(), h / (double) imagenVieja.getHeight());
            gv.drawImage(imagenVieja, 0, 0, null);
            gv.dispose();
        }
    }
}
