package estudio;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;

/** Tarjeta grande de una materia. */
final class TarjetaMateria extends JComponent {
    private static final int M = 14;
    private final Materia materia;
    private final int numTemas;
    private final ValorAnimado hover = new ValorAnimado(0, this::repaint);
    private final ValorAnimado aparicion = new ValorAnimado(0, this::repaint);
    private final ValorAnimado barra = new ValorAnimado(0, this::repaint);
    private Progreso.ResumenMateria resumen;

    TarjetaMateria(Materia m, Runnable accion) {
        this.materia = m;
        this.numTemas = m.temas().size();
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover.ir(1, 220); }
            @Override public void mouseExited(MouseEvent e) { hover.ir(0, 320); }
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) accion.run();
            }
        });
        refrescar();
    }

    void refrescar() {
        resumen = Progreso.resumenMateria(materia);
    }

    void aparecer(int retraso) {
        aparicion.fijar(0);
        barra.fijar(0);
        Anim.despues(Math.max(1, retraso), () -> {
            aparicion.ir(1, 560);
            barra.ir(resumen.fraccionModulos(), 1100);
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        double ap = aparicion.get();
        if (ap <= 0.001) return;
        Graphics2D g2 = (Graphics2D) g.create();
        Estilo.suavizar(g2);
        Dimension dim = Estilo.unidadesDeDiseno(g2, getWidth(), getHeight());
        Estilo.alfa(g2, (float) Estilo.limitar(ap * 1.3));
        double hv = hover.get();
        g2.translate(0, (1 - ap) * 30 - hv * 5);

        int w = dim.width - 2 * M, h = dim.height - 2 * M - 6;
        double x = M, y = M;
        RoundRectangle2D forma = new RoundRectangle2D.Double(x, y, w, h, 28, 28);
        Estilo.sombra(g2, x, y, w, h, 28, 12, 120);
        Estilo.halo(g2, forma, Estilo.alfa(materia.acento2, 110), 12, hv);
        Cache.dibujar(g2, "materia:" + materia.clave, x, y, w, h, gc -> pintarBase(gc, w, h));
        boolean compacta = compacta(h);
        // Brillo extra al pasar el ratón
        if (hv > 0.01) {
            g2.setColor(new Color(255, 255, 255, (int) (10 * hv)));
            g2.fill(forma);
        }
        g2.setStroke(new BasicStroke(1.2f));
        g2.setPaint(new GradientPaint((float) x, (float) y, Estilo.alfa(materia.acento1, (int) (60 + 150 * hv)),
                (float) (x + w), (float) (y + h), Estilo.alfa(materia.acento2, (int) (40 + 150 * hv))));
        g2.draw(forma);

        // Pie: estado + progreso + acción (lo único que cambia)
        double pad = pad(h);
        double tx = x + pad, ancho = w - 2 * pad;
        double py = y + pie(h);
        float tamPie = compacta ? 12f : 13.5f;
        g2.setFont(Estilo.fuente(Estilo.SEMI, tamPie));
        FontMetrics fm = g2.getFontMetrics();
        String pct = textoEstado(fm, (int) ancho - anchoDatos(g2, tamPie) - 14);
        g2.setColor(resumen.completa() ? Estilo.EXITO : Estilo.TEXTO);
        g2.drawString(pct, (float) (tx + ancho - fm.stringWidth(pct)), (float) py + 14);
        UI.Barra.pintar(g2, tx, py + 26, ancho, compacta ? 6 : 7, barra.get(),
                resumen.completa() ? Estilo.EXITO : materia.acento1,
                resumen.completa() ? Estilo.mezclar(Estilo.EXITO, materia.acento2, 0.3) : materia.acento2);

        if (!compacta) {
            g2.setFont(Estilo.fuente(Estilo.SEMI, 14f));
            String entrar = resumen.existe() ? "Continuar" : "Comenzar";
            fm = g2.getFontMetrics();
            double ay = py + 56;
            g2.setColor(Estilo.mezclar(Estilo.TEXTO_SUAVE, Color.WHITE, hv));
            g2.drawString(entrar, (float) tx, (float) ay + 4);
            double fx = tx + fm.stringWidth(entrar) + 10 + hv * 6;
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D flecha = new Path2D.Double();
            flecha.moveTo(fx, ay);
            flecha.lineTo(fx + 14, ay);
            flecha.moveTo(fx + 9, ay - 5);
            flecha.lineTo(fx + 14, ay);
            flecha.lineTo(fx + 9, ay + 5);
            g2.draw(flecha);
        }
        g2.dispose();
    }

    /** Tarjeta baja: sin descripción ni enlace, para que quepa todo. */
    private static boolean compacta(int h) {
        return h < 250;
    }

    private static double pad(int h) {
        return compacta(h) ? 20 : 30;
    }

    /** Coordenada donde empieza el pie (datos, estado y barra). */
    private static double pie(int h) {
        return h - (compacta(h) ? 52 : 76);
    }

    /** Ancho del texto fijo del pie ("130 preguntas · 36 temas"), para saber cuánto queda. */
    private int anchoDatos(Graphics2D g2, float tam) {
        return g2.getFontMetrics(Estilo.fuente(Estilo.SEMI, tam))
                .stringWidth(datos(compacta(UI.diseno(this).height - 2 * M - 6)));
    }

    private String datos(boolean compacta) {
        return compacta ? materia.preguntas.size() + " preguntas · " + numTemas + " temas"
                : materia.preguntas.size() + " preguntas   ·   " + numTemas + " temas";
    }

    /** Elige la versión más completa del estado que quepa en el espacio libre. */
    private String textoEstado(FontMetrics fm, int disponible) {
        if (resumen.completa()) return "Materia completada";
        if (!resumen.existe()) return "Sin empezar";
        int mods = resumen.modulosCompletados(), total = resumen.modulos();
        int pct = (int) Math.round(resumen.fraccionPreguntas() * 100);
        String[] opciones = {
                mods + " de " + total + " módulos   ·   " + pct + "% dominado",
                mods + "/" + total + " módulos · " + pct + "%",
                mods + "/" + total + " · " + pct + "%",
        };
        for (String o : opciones) if (fm.stringWidth(o) <= disponible) return o;
        return opciones[opciones.length - 1];
    }

    /** Parte estática de la tarjeta (se dibuja una vez y se guarda en caché). */
    private void pintarBase(Graphics2D g2, int w, int h) {
        RoundRectangle2D forma = new RoundRectangle2D.Double(0, 0, w, h, 28, 28);
        g2.setPaint(new GradientPaint(0, 0, new Color(0x19203A), 0, h, new Color(0x131830)));
        g2.fill(forma);
        g2.clip(forma);
        g2.setPaint(new RadialGradientPaint(new Point2D.Double(w, 0), (float) (w * 0.8),
                new float[]{0f, 1f}, new Color[]{Estilo.alfa(materia.acento1, 56), Estilo.alfa(materia.acento1, 0)}));
        g2.fill(forma);
        g2.setStroke(new BasicStroke(1.3f));
        for (int k = 0; k < 3; k++) {
            Path2D onda = new Path2D.Double();
            double base = h * 0.28 + k * 16;
            for (int i = 0; i <= 60; i++) {
                double t = i / 60.0;
                double px = w * 0.52 + t * w * 0.5;
                double py = base + Math.sin(t * Math.PI * 3 + k * 0.7) * (10 - k * 2);
                if (i == 0) onda.moveTo(px, py); else onda.lineTo(px, py);
            }
            g2.setColor(Estilo.alfa(k % 2 == 0 ? materia.acento1 : materia.acento2, 30 - k * 6));
            g2.draw(onda);
        }
        g2.setClip(null);

        boolean compacta = compacta(h);
        double pad = pad(h), lado = compacta ? 46 : 64;
        RoundRectangle2D caja = new RoundRectangle2D.Double(pad, pad, lado, lado, lado * 0.31, lado * 0.31);
        g2.setPaint(new GradientPaint((float) pad, (float) pad, materia.acento1, (float) (pad + lado), (float) (pad + lado), materia.acento2));
        g2.fill(caja);
        g2.setPaint(new GradientPaint(0, (float) pad, new Color(255, 255, 255, 60), 0, (float) (pad + lado * 0.62), new Color(255, 255, 255, 0)));
        g2.fill(caja);
        Estilo.icono(g2, materia.icono, pad + lado / 2, pad + lado / 2, lado * 0.69, Color.WHITE);

        double tx = pad, ancho = w - 2 * pad;
        g2.setFont(Estilo.fuente(Estilo.NEGRITA, compacta ? 19f : 25f));
        g2.setColor(Estilo.TEXTO);
        double inicio = pad + lado + (compacta ? 14 : 20);
        // Solo se escriben las líneas de título que caben antes del pie.
        double altoLinea = g2.getFontMetrics().getHeight() * 1.15;
        int maxLineas = (int) Math.max(1, Math.min(2, (pie(h) - 8 - inicio) / altoLinea));
        double ty = Estilo.parrafo(g2, materia.nombre, tx, inicio, ancho, maxLineas, 1.15);
        if (!materia.descripcion.isEmpty() && !compacta && ty + 22 < pie(h)) {
            g2.setFont(Estilo.fuente(Estilo.NORMAL, 14f));
            g2.setColor(Estilo.TEXTO_SUAVE);
            Estilo.parrafo(g2, materia.descripcion, tx, ty + 2, ancho, 1, 1.2);
        }
        g2.setFont(Estilo.fuente(Estilo.SEMI, compacta ? 12f : 13.5f));
        g2.setColor(Estilo.TEXTO_SUAVE);
        g2.drawString(datos(compacta), (float) tx, (float) pie(h) + 14f);
    }
}
