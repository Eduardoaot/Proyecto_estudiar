package estudio;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;

/** Tarjeta de la pantalla de inicio que abre los problemas de cálculo (mismo estilo que {@link TarjetaMateria}). */
final class TarjetaProblemas extends JComponent implements TarjetaInicio {
    private static final int M = 14;
    private static final Color A1 = EstadisticasProblemas.ACENTO1, A2 = EstadisticasProblemas.ACENTO2;
    private final ValorAnimado hover = new ValorAnimado(0, this::repaint);
    private final ValorAnimado aparicion = new ValorAnimado(0, this::repaint);
    private final ValorAnimado barra = new ValorAnimado(0, this::repaint);
    private int intentos, aciertos, dominados;

    TarjetaProblemas(Runnable accion) {
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

    @Override
    public void refrescar() {
        intentos = EstadisticasProblemas.totalIntentos();
        aciertos = EstadisticasProblemas.totalAciertos();
        dominados = EstadisticasProblemas.tiposDominados();
    }

    @Override
    public void aparecer(int retraso) {
        aparicion.fijar(0);
        barra.fijar(0);
        Anim.despues(Math.max(1, retraso), () -> {
            aparicion.ir(1, 560);
            barra.ir(dominados / 20.0, 1100);
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
        Estilo.halo(g2, forma, Estilo.alfa(A2, 110), 12, hv);
        Cache.dibujar(g2, "problemas", x, y, w, h, gc -> pintarBase(gc, w, h));
        if (hv > 0.01) {
            g2.setColor(new Color(255, 255, 255, (int) (10 * hv)));
            g2.fill(forma);
        }
        g2.setStroke(new BasicStroke(1.2f));
        g2.setPaint(new GradientPaint((float) x, (float) y, Estilo.alfa(A1, (int) (60 + 150 * hv)),
                (float) (x + w), (float) (y + h), Estilo.alfa(A2, (int) (40 + 150 * hv))));
        g2.draw(forma);

        boolean compacta = h < 250;
        double pad = compacta ? 20 : 30, tx = x + pad, ancho = w - 2 * pad;
        double py = y + h - (compacta ? 52 : 76);
        float tamPie = compacta ? 12f : 13.5f;
        g2.setFont(Estilo.fuente(Estilo.SEMI, tamPie));
        FontMetrics fm = g2.getFontMetrics();
        String datos = "20 tipos · problemas ilimitados";
        g2.setColor(Estilo.TEXTO_SUAVE);
        g2.drawString(datos, (float) tx, (float) py + 14);
        String estado = intentos == 0 ? "Sin empezar"
                : dominados + "/20 tipos · " + Math.round(100.0 * aciertos / intentos) + "% completos";
        int libre = (int) ancho - fm.stringWidth(datos) - 14;
        if (fm.stringWidth(estado) > libre) estado = dominados + "/20 tipos";
        g2.setColor(dominados >= 20 ? Estilo.EXITO : Estilo.TEXTO);
        g2.drawString(estado, (float) (tx + ancho - fm.stringWidth(estado)), (float) py + 14);
        UI.Barra.pintar(g2, tx, py + 26, ancho, compacta ? 6 : 7, barra.get(), A1, A2);

        if (!compacta) {
            g2.setFont(Estilo.fuente(Estilo.SEMI, 14f));
            String entrar = intentos > 0 ? "Continuar" : "Practicar";
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

    private void pintarBase(Graphics2D g2, int w, int h) {
        RoundRectangle2D forma = new RoundRectangle2D.Double(0, 0, w, h, 28, 28);
        g2.setPaint(new GradientPaint(0, 0, new Color(0x19203A), 0, h, new Color(0x131830)));
        g2.fill(forma);
        g2.clip(forma);
        g2.setPaint(new RadialGradientPaint(new Point2D.Double(w, 0), (float) (w * 0.8),
                new float[]{0f, 1f}, new Color[]{Estilo.alfa(A1, 56), Estilo.alfa(A1, 0)}));
        g2.fill(forma);
        // Cuadrícula tenue de "hoja de cálculo"
        g2.setStroke(new BasicStroke(1f));
        for (int k = 0; k < 6; k++) {
            g2.setColor(Estilo.alfa(k % 2 == 0 ? A1 : A2, 22));
            g2.draw(new Line2D.Double(w * 0.55, h * 0.16 + k * 18, w * 0.95, h * 0.16 + k * 18));
            g2.draw(new Line2D.Double(w * 0.55 + k * 30, h * 0.16, w * 0.55 + k * 30, h * 0.16 + 90));
        }
        g2.setClip(null);

        boolean compacta = h < 250;
        double pad = compacta ? 20 : 30, lado = compacta ? 46 : 64;
        RoundRectangle2D caja = new RoundRectangle2D.Double(pad, pad, lado, lado, lado * 0.31, lado * 0.31);
        g2.setPaint(new GradientPaint((float) pad, (float) pad, A1, (float) (pad + lado), (float) (pad + lado), A2));
        g2.fill(caja);
        g2.setPaint(new GradientPaint(0, (float) pad, new Color(255, 255, 255, 60), 0, (float) (pad + lado * 0.62), new Color(255, 255, 255, 0)));
        g2.fill(caja);
        Estilo.icono(g2, 1, pad + lado / 2, pad + lado / 2, lado * 0.69, Color.WHITE);

        double tx = pad, ancho = w - 2 * pad;
        g2.setFont(Estilo.fuente(Estilo.NEGRITA, compacta ? 19f : 25f));
        g2.setColor(Estilo.TEXTO);
        double inicio = pad + lado + (compacta ? 14 : 20);
        double pie = h - (compacta ? 52 : 76);
        double altoLinea = g2.getFontMetrics().getHeight() * 1.15;
        int maxLineas = (int) Math.max(1, Math.min(2, (pie - 8 - inicio) / altoLinea));
        double ty = Estilo.parrafo(g2, "Problemas de cálculo", tx, inicio, ancho, maxLineas, 1.15);
        if (!compacta && ty + 22 < pie) {
            g2.setFont(Estilo.fuente(Estilo.NORMAL, 14f));
            g2.setColor(Estilo.TEXTO_SUAVE);
            Estilo.parrafo(g2, "Estudio de Tiempos · pasos 8 a 12 · Ordinario", tx, ty + 2, ancho, 1, 1.2);
        }
    }
}
