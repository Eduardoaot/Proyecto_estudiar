package estudio;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

/** Tarjeta de un tema con su progreso guardado. */
final class TarjetaTema extends JComponent {
    private static final int M = 10;
    final int total;
    private final String titulo, detalle;
    private final boolean grande;
    private final Materia materia;
    private final ValorAnimado hover = new ValorAnimado(0, this::repaint);
    private final ValorAnimado aparicion = new ValorAnimado(0, this::repaint);
    private final ValorAnimado barra = new ValorAnimado(0, this::repaint);
    private final ValorAnimado hoverReinicio = new ValorAnimado(0, this::repaint);
    private Progreso.Resumen resumen;
    private Runnable alAbrir = () -> {}, alReiniciar = () -> {}, alExaminar = () -> {}, alVerRespuestas = () -> {};
    private final Rectangle zonaReinicio = new Rectangle();
    private final Rectangle zonaExamen = new Rectangle();
    private final Rectangle zonaRespuestas = new Rectangle();
    private final ValorAnimado hoverExamen = new ValorAnimado(0, this::repaint);
    private final ValorAnimado hoverRespuestas = new ValorAnimado(0, this::repaint);

    TarjetaTema(String titulo, String detalle, int total, boolean grande, Materia materia) {
        this.titulo = titulo;
        this.detalle = detalle;
        this.total = total;
        this.grande = grande;
        this.materia = materia;
        this.resumen = new Progreso.Resumen(0, total, false, false, false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover.ir(1, 200); }

            @Override public void mouseExited(MouseEvent e) {
                hover.ir(0, 300);
                hoverReinicio.ir(0, 200);
                hoverExamen.ir(0, 200);
                hoverRespuestas.ir(0, 200);
            }

            @Override public void mouseMoved(MouseEvent e) {
                hoverReinicio.ir(sobreReinicio(e) ? 1 : 0, 160);
                Point p = Estilo.aDiseno(e.getPoint());
                hoverExamen.ir(zonaExamen.contains(p) ? 1 : 0, 160);
                hoverRespuestas.ir(zonaRespuestas.contains(p) ? 1 : 0, 160);
            }

            @Override public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                Point p = Estilo.aDiseno(e.getPoint());
                if (sobreReinicio(e)) alReiniciar.run();
                else if (zonaExamen.contains(p)) alExaminar.run();
                else if (zonaRespuestas.contains(p)) alVerRespuestas.run();
                else alAbrir.run();
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    private boolean sobreReinicio(MouseEvent e) {
        return resumen.existe() && zonaReinicio.contains(Estilo.aDiseno(e.getPoint()));
    }

    void setAcciones(Runnable abrir, Runnable reiniciar, Runnable examinar, Runnable verRespuestas) {
        alAbrir = abrir;
        alReiniciar = reiniciar;
        alExaminar = examinar;
        alVerRespuestas = verRespuestas;
    }

    void setResumen(Progreso.Resumen r, boolean animar) {
        resumen = r;
        if (animar) barra.ir(r.fraccion(), 700);
        repaint();
    }

    void aparecer(int retraso) {
        aparicion.fijar(0);
        barra.fijar(0);
        Anim.despues(Math.max(1, retraso), () -> {
            aparicion.ir(1, 480);
            barra.ir(resumen.fraccion(), 1000);
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        double ap = aparicion.get();
        if (ap <= 0.001) return;
        Graphics2D g2 = (Graphics2D) g.create();
        Estilo.suavizar(g2);
        Dimension dim = Estilo.unidadesDeDiseno(g2, getWidth(), getHeight());
        Estilo.alfa(g2, (float) Estilo.limitar(ap * 1.4));
        double hv = hover.get();
        g2.translate(0, Math.round(((1 - ap) * 22 - hv * 3) * 2) / 2.0);

        int w = dim.width - 2 * M, h = dim.height - 2 * M - 3;
        double x = M, y = M;
        RoundRectangle2D forma = new RoundRectangle2D.Double(x, y, w, h, 22, 22);
        Estilo.halo(g2, forma, Estilo.alfa(materia.acento1, 80), 10, hv);
        String firma = "tema:" + materia.clave + ':' + titulo + ':' + grande + ':'
                + resumen.dominadas() + ':' + resumen.existe() + ':' + resumen.terminado() + ':' + resumen.completado();
        Cache.dibujar(g2, firma, x, y, w, h, gc -> pintarBase(gc, w, h));
        if (hv > 0.01) {
            g2.setColor(new Color(255, 255, 255, (int) (12 * hv)));
            g2.fill(forma);
        }
        g2.setStroke(new BasicStroke(1.1f));
        g2.setColor(Estilo.mezclar(Estilo.BORDE, Estilo.alfa(materia.acento1, 170), hv));
        g2.draw(forma);

        double pad = grande ? 26 : 18;
        // Botón reiniciar (solo visible al pasar el ratón)
        if (resumen.existe()) {
            g2.setFont(Estilo.etiqueta(10.5f));
            double bw = g2.getFontMetrics().stringWidth(estado()) + 18;
            double bx = x + w - pad - bw, by = y + pad - 2;
            double rx = bx - 30;
            zonaReinicio.setBounds((int) rx - 4, (int) by - 4, 30, 30);
            if (hv > 0.01) {
                double hr = hoverReinicio.get();
                Graphics2D gr = (Graphics2D) g2.create();
                Estilo.alfa(gr, (float) Estilo.limitar(hv));
                gr.setColor(new Color(255, 255, 255, (int) (10 + 26 * hr)));
                gr.fill(new Ellipse2D.Double(rx - 1, by - 1, 24, 24));
                gr.setColor(Estilo.mezclar(Estilo.TEXTO_SUAVE, Estilo.ERROR, hr));
                gr.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                double ccx = rx + 11, ccy = by + 11, rr = 6;
                gr.draw(new Arc2D.Double(ccx - rr, ccy - rr, rr * 2, rr * 2, 110, 280, Arc2D.OPEN));
                double ax = ccx + rr * Math.cos(Math.toRadians(110)), ay = ccy - rr * Math.sin(Math.toRadians(110));
                gr.draw(new Line2D.Double(ax, ay, ax + 4.2, ay - 1.8));
                gr.draw(new Line2D.Double(ax, ay, ax + 1.6, ay + 3.8));
                gr.dispose();
            }
        } else {
            zonaReinicio.setBounds(0, 0, 0, 0);
        }

        double pieY = y + h - pad - 8;
        // Datos del pie: se desvanecen para dejar sitio a las píldoras
        double hv2 = hover.get();
        if (hv2 < 0.99) {
            Graphics2D gp = (Graphics2D) g2.create();
            Estilo.alfa(gp, (float) (1 - hv2));
            gp.setFont(Estilo.fuente(Estilo.NORMAL, 13f));
            gp.setColor(Estilo.TEXTO_SUAVE);
            String cantidad = total + (total == 1 ? " pregunta" : " preguntas");
            if (resumen.existe()) cantidad = resumen.dominadas() + " de " + total + " dominadas";
            gp.drawString(cantidad, (float) (x + pad), (float) (pieY - 12));
            gp.setFont(Estilo.fuente(Estilo.SEMI, 13f));
            FontMetrics fmp = gp.getFontMetrics();
            String pct = Math.round(resumen.fraccion() * 100) + "%";
            gp.setColor(resumen.terminado() || resumen.completado() ? Estilo.EXITO : Estilo.TEXTO);
            gp.drawString(pct, (float) (x + w - pad - fmp.stringWidth(pct)), (float) (pieY - 12));
            gp.dispose();
        }
        // Píldoras: examen final y, si ya se completó, las respuestas
        if (hv2 > 0.01) {
            double derecha = pildora(g2, "Examen", x + w - pad, pieY, hv2, hoverExamen.get(), Estilo.EXITO, zonaExamen);
            if (resumen.completado()) {
                pildora(g2, "Respuestas", derecha - 8, pieY, hv2, hoverRespuestas.get(), materia.acento1, zonaRespuestas);
            } else {
                zonaRespuestas.setBounds(0, 0, 0, 0);
            }
        } else {
            zonaExamen.setBounds(0, 0, 0, 0);
            zonaRespuestas.setBounds(0, 0, 0, 0);
        }
        UI.Barra.pintar(g2, x + pad, pieY, w - 2 * pad, grande ? 8 : 6, barra.get(),
                resumen.terminado() ? Estilo.EXITO : materia.acento1,
                resumen.terminado() ? Estilo.mezclar(Estilo.EXITO, materia.acento2, 0.3) : materia.acento2);
        g2.dispose();
    }

    /** Dibuja una píldora alineada a la derecha y devuelve su borde izquierdo. */
    private double pildora(Graphics2D g2, String texto, double derecha, double pieY, double visible, double hoverP,
                           Color color, Rectangle zona) {
        g2.setFont(Estilo.fuente(Estilo.SEMI, 12f));
        FontMetrics fm = g2.getFontMetrics();
        double pw = fm.stringWidth(texto) + 26, ph = 26;
        double px = derecha - pw, py = pieY - 12 - ph + 4;
        zona.setBounds((int) px - 2, (int) py - 2, (int) pw + 4, (int) ph + 4);
        Graphics2D ge = (Graphics2D) g2.create();
        Estilo.alfa(ge, (float) Estilo.limitar(visible));
        RoundRectangle2D pastilla = new RoundRectangle2D.Double(px, py, pw, ph, ph, ph);
        ge.setColor(Estilo.alfa(color, (int) (24 + 34 * hoverP)));
        ge.fill(pastilla);
        ge.setStroke(new BasicStroke(1.1f));
        ge.setColor(Estilo.alfa(color, (int) (90 + 90 * hoverP)));
        ge.draw(pastilla);
        ge.setColor(Estilo.mezclar(color, Color.WHITE, 0.25 + 0.3 * hoverP));
        ge.drawString(texto, (float) (px + 13), (float) (py + (ph - fm.getHeight()) / 2 + fm.getAscent()));
        ge.dispose();
        return px;
    }

    private String estado() {
        if (resumen.terminado() || resumen.completado()) return "COMPLETADO";
        return resumen.existe() ? "EN CURSO" : "NUEVO";
    }

    /** Todo lo que no depende del ratón ni de la animación. */
    private void pintarBase(Graphics2D g2, int w, int h) {
        RoundRectangle2D forma = new RoundRectangle2D.Double(0, 0, w, h, 22, 22);
        Color fondo = Estilo.mezclar(Estilo.SUPERFICIE, Estilo.SUPERFICIE_ALTA, 0.35);
        g2.setPaint(new GradientPaint(0, 0, fondo, 0, h, Estilo.mezclar(fondo, Estilo.FONDO_ABAJO, 0.35)));
        g2.fill(forma);
        if (grande) {
            g2.clip(forma);
            g2.setPaint(new RadialGradientPaint(new Point2D.Double(w * 0.85, 0), (float) (w * 0.5),
                    new float[]{0f, 1f}, new Color[]{Estilo.alfa(materia.acento2, 50), Estilo.alfa(materia.acento2, 0)}));
            g2.fill(forma);
            g2.setClip(null);
        }

        double pad = grande ? 26 : 18;
        double cx = pad, ancho = w - 2 * pad;

        String estado = estado();
        Color colorEstado = resumen.terminado() || resumen.completado()
                ? Estilo.EXITO : (resumen.existe() ? materia.acento1 : Estilo.TEXTO_TENUE);
        g2.setFont(Estilo.etiqueta(10.5f));
        FontMetrics fe = g2.getFontMetrics();
        double bw = fe.stringWidth(estado) + 18, bh = 22;
        double bx = w - pad - bw, by = pad - 2;
        RoundRectangle2D pastilla = new RoundRectangle2D.Double(bx, by, bw, bh, bh, bh);
        g2.setColor(Estilo.alfa(colorEstado, 30));
        g2.fill(pastilla);
        g2.setColor(Estilo.alfa(colorEstado, 90));
        g2.draw(pastilla);
        g2.setColor(colorEstado);
        g2.drawString(estado, (float) (bx + 9), (float) (by + (bh - fe.getHeight()) / 2 + fe.getAscent()));

        double anchoTitulo = bx - cx - (resumen.existe() ? 40 : 12);
        if (grande) {
            RoundRectangle2D caja = new RoundRectangle2D.Double(cx, pad, 52, 52, 16, 16);
            g2.setPaint(new GradientPaint((float) cx, (float) pad, materia.acento1, (float) cx + 52, (float) (pad + 52), materia.acento2));
            g2.fill(caja);
            Estilo.icono(g2, 3, cx + 26, pad + 26, 38, Color.WHITE);
            double tx = cx + 70;
            g2.setFont(Estilo.fuente(Estilo.NEGRITA, 22f));
            g2.setColor(Estilo.TEXTO);
            double ty = Estilo.parrafo(g2, titulo, tx, pad + 1, anchoTitulo - 70, 1, 1.1);
            g2.setFont(Estilo.fuente(Estilo.NORMAL, 14f));
            g2.setColor(Estilo.TEXTO_SUAVE);
            Estilo.parrafo(g2, detalle, tx, ty + 2, anchoTitulo - 70, 1, 1.2);
        } else {
            g2.setFont(Estilo.fuente(Estilo.SEMI, 15.5f));
            g2.setColor(Estilo.TEXTO);
            Estilo.parrafo(g2, titulo, cx, pad - 2, anchoTitulo, 2, 1.18);
        }

    }
}
