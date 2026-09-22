package estudio;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Resumen al completar una sesión, con confeti. */
final class PantallaFin extends JPanel implements Pantalla {
    private final Ventana app;
    private final Materia materia;
    private final String tema;
    private final Motor motor;
    private final UI.Boton volver, repetir, respuestas;
    private final ValorAnimado entrada = new ValorAnimado(0, this::repaint);
    private final ValorAnimado anillo = new ValorAnimado(0, this::repaint);
    private final List<double[]> confeti = new ArrayList<>();
    private Anim.Tarea timerConfeti;
    private final Rectangle tarjeta = new Rectangle();
    /** Factor de escala de la tarjeta: 1 con sitio de sobra, menos en ventanas pequeñas. */
    private double k = 1;
    private boolean botonesEnDosFilas;

    PantallaFin(Ventana app, Materia materia, String tema, Motor motor) {
        super(null);
        this.app = app;
        this.materia = materia;
        this.tema = tema;
        this.motor = motor;
        setOpaque(false);
        volver = new UI.Boton("Volver a los temas", UI.TipoBoton.SECUNDARIO);
        volver.addActionListener(e -> app.mostrar(new PantallaTemas(app, materia), -1));
        repetir = new UI.Boton("Estudiar de nuevo", UI.TipoBoton.SECUNDARIO);
        repetir.addActionListener(e -> {
            if (UI.confirmar(this, "¿Empezar de nuevo?",
                    "Se reiniciará el progreso de esta sesión y comenzarás otra vez desde la primera pregunta. "
                            + "La etiqueta de completado se conserva.", "Reiniciar")) {
                Progreso.borrar(Progreso.clave(materia, tema));
                app.mostrar(new PantallaEstudio(app, materia, tema), 1);
            }
        });
        respuestas = new UI.Boton("Ver preguntas y respuestas", UI.TipoBoton.PRIMARIO);
        respuestas.setColores(materia.acento1, materia.acento2);
        respuestas.addActionListener(e -> app.mostrar(new PantallaRespuestas(app, materia, tema), 1));
        add(volver);
        add(repetir);
        add(respuestas);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "volver");
        getActionMap().put("volver", UI.accion(() -> true, volver::doClick));
    }

    @Override
    public void alMostrar() {
        app.setAcento(materia.acento1, materia.acento2);
        entrada.fijar(0);
        entrada.ir(1, 700);
        anillo.fijar(0);
        Anim.despues(250, () -> anillo.ir(1, 1100));
        lanzarConfeti();
    }

    @Override
    public void alOcultar() {
        if (timerConfeti != null) timerConfeti.stop();
    }

    private void lanzarConfeti() {
        confeti.clear();
        Random r = new Random();
        int w = Math.max(UI.diseno(this).width, 800);
        for (int i = 0; i < 160; i++) {
            confeti.add(new double[]{
                    r.nextDouble() * w,              // x
                    -20 - r.nextDouble() * 400,      // y
                    (r.nextDouble() - 0.5) * 2.4,    // vx
                    2 + r.nextDouble() * 3.5,        // vy
                    r.nextDouble() * Math.PI * 2,    // ángulo
                    (r.nextDouble() - 0.5) * 0.3,    // giro
                    5 + r.nextDouble() * 6,          // tamaño
                    r.nextInt(5),                    // color
                    r.nextDouble() * Math.PI * 2     // fase del balanceo
            });
        }
        double[] anterior = {0};
        timerConfeti = Anim.ejecutar(4200, 150, t -> {
            // Pasos expresados en "cuadros de 60 Hz" para que la velocidad no dependa del monitor.
            double k = (t - anterior[0]) * 4200 / (1000 / 60.0);
            anterior[0] = t;
            for (double[] c : confeti) {
                c[8] += 0.08 * k;
                c[0] += (c[2] + Math.sin(c[8]) * 0.8) * k;
                c[1] += c[3] * k;
                c[4] += c[5] * k;
            }
            alfaConfeti = t < 0.75 ? 1 : 1 - (t - 0.75) / 0.25;
            repaint();
        }, () -> {
            confeti.clear();
            repaint();
        });
    }

    private double alfaConfeti = 1;

    @Override
    public void doLayout() {
        Dimension dim = UI.diseno(this);
        int w = dim.width, h = dim.height;
        int tw = Math.max(280, Math.min(700, w - 48));
        Dimension dv = UI.prefDiseno(volver), dr = UI.prefDiseno(repetir), ds = UI.prefDiseno(respuestas);
        int anchoBotones = dv.width + 10 + dr.width + 10 + ds.width;
        botonesEnDosFilas = anchoBotones > tw - 40;
        int altoBotones = botonesEnDosFilas ? 46 * 2 + 10 : 46;

        // La parte de arriba (anillo, título, estadísticas) se encoge hasta un 60%.
        k = Math.max(0.6, Math.min(1, (h - 48 - altoBotones - 40) / 430.0));
        int th = (int) Math.round(430 * k) + altoBotones + 40;
        tarjeta.setBounds((w - tw) / 2, Math.max(16, (h - th) / 2), tw, th);

        int by = tarjeta.y + th - 40 - altoBotones;
        if (botonesEnDosFilas) {
            int fila1 = dv.width + 10 + dr.width;
            int bx = tarjeta.x + (tw - fila1) / 2;
            UI.poner(volver, bx, by, dv.width, 46);
            UI.poner(repetir, bx + dv.width + 10, by, dr.width, 46);
            UI.poner(respuestas, tarjeta.x + (tw - ds.width) / 2.0, by + 56, ds.width, 46);
        } else {
            int bx = tarjeta.x + (tw - anchoBotones) / 2;
            UI.poner(volver, bx, by, dv.width, 46);
            UI.poner(repetir, bx + dv.width + 10, by, dr.width, 46);
            UI.poner(respuestas, bx + dv.width + 10 + dr.width + 10, by, ds.width, 46);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Estilo.suavizar(g2);
        Estilo.unidadesDeDiseno(g2, getWidth(), getHeight());
        double ap = Estilo.easeOut(entrada.get());
        Graphics2D gc = (Graphics2D) g2.create();
        Estilo.alfa(gc, (float) ap);
        gc.translate(0, (1 - ap) * 30);
        pintarTarjeta(gc);
        gc.dispose();

        Color[] colores = {materia.acento1, materia.acento2, Estilo.EXITO, Estilo.AVISO, Color.WHITE};
        java.awt.geom.AffineTransform base = g2.getTransform();
        Rectangle2D.Double pieza = new Rectangle2D.Double();
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
        for (double[] c : confeti) {
            if (c[1] < -20 || c[1] > UI.diseno(this).height + 20) continue;
            g2.setTransform(base);
            g2.translate(c[0], c[1]);
            g2.rotate(c[4]);
            g2.scale(1, Math.abs(Math.cos(c[8])) * 0.8 + 0.2);
            g2.setColor(Estilo.alfa(colores[(int) c[7]], (int) (230 * alfaConfeti)));
            pieza.setRect(-c[6] / 2, -c[6] / 4, c[6], c[6] / 2);
            g2.fill(pieza);
        }
        g2.setTransform(base);
        g2.dispose();
    }

    private void pintarTarjeta(Graphics2D g2) {
        double x = tarjeta.x, y = tarjeta.y;
        int w = tarjeta.width, h = tarjeta.height;
        Estilo.sombra(g2, x, y, w, h, 30, 16, 150);
        int respondidas = motor.aciertos + motor.errores;
        String firma = "fin:" + materia.clave + ':' + tema + ':' + motor.total + ':' + respondidas + ':'
                + motor.aciertos + ':' + motor.mejorRacha + ':' + Math.round(k * 100);
        Cache.dibujar(g2, firma, x, y, w, h, gc -> pintarBase(gc, w, h));

        double cx = x + w / 2.0, cy = y + 90 * k, r = 46 * k;
        double a = anillo.get();
        Ellipse2D circulo = new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
        Estilo.halo(g2, circulo, Estilo.alfa(Estilo.EXITO, 70), (int) Math.round(14 * k), a);
        g2.setColor(Estilo.alfa(Estilo.EXITO, 30));
        g2.fill(circulo);
        g2.setStroke(new BasicStroke((float) (5 * k), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(Estilo.EXITO);
        g2.draw(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 90, -360 * a, Arc2D.OPEN));
        g2.setStroke(new BasicStroke((float) (6 * k), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(Color.WHITE);
        Estilo.palomita(g2, cx, cy + 2 * k, 60 * k, Estilo.limitar((a - 0.45) / 0.55));
    }

    /** Fondo, textos y estadísticas de la tarjeta (estáticos, en caché). */
    private void pintarBase(Graphics2D g2, int w, int h) {
        RoundRectangle2D forma = new RoundRectangle2D.Double(0, 0, w, h, 30, 30);
        g2.setPaint(new GradientPaint(0, 0, new Color(0x1A2140), 0, h, new Color(0x121729)));
        g2.fill(forma);
        g2.clip(forma);
        g2.setPaint(new RadialGradientPaint(new Point2D.Double(w / 2.0, 0), w * 0.7f,
                new float[]{0f, 1f}, new Color[]{Estilo.alfa(Estilo.EXITO, 40), Estilo.alfa(Estilo.EXITO, 0)}));
        g2.fill(forma);
        g2.setClip(null);
        g2.setColor(Estilo.BORDE_FUERTE);
        g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 30, 30));

        double cx = w / 2.0;
        g2.setFont(Estilo.fuente(Estilo.NEGRITA, (float) (30 * k)));
        g2.setColor(Estilo.TEXTO);
        Estilo.textoCentrado(g2, tema == null ? "¡Temario completado!" : "¡Tema completado!", cx, 178 * k);
        g2.setFont(Estilo.fuente(Estilo.NORMAL, (float) (15 * k)));
        g2.setColor(Estilo.TEXTO_SUAVE);
        FontMetrics fm = g2.getFontMetrics();
        String sub = Estilo.recortar(fm, materia.nombre + "  ·  " + (tema == null ? "Todo el temario" : tema), w - 60);
        Estilo.textoCentrado(g2, sub, cx, 212 * k);

        int respondidas = motor.aciertos + motor.errores;
        int precision = respondidas == 0 ? 0 : (int) Math.round(motor.aciertos * 100.0 / respondidas);
        String[][] datos = {
                {String.valueOf(motor.total), "Preguntas"},
                {String.valueOf(respondidas), "Respuestas"},
                {precision + "%", "Precisión"},
                {String.valueOf(motor.mejorRacha), "Mejor racha"},
        };
        double pad = Math.max(16, 36 * k), gap = 12 * k;
        double tw = (w - 2 * pad - gap * 3) / 4, th = 88 * k;
        double ty = 252 * k;
        for (int i = 0; i < datos.length; i++) {
            double tx = pad + i * (tw + gap);
            RoundRectangle2D m = new RoundRectangle2D.Double(tx, ty, tw, th, 18, 18);
            g2.setColor(new Color(255, 255, 255, 10));
            g2.fill(m);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(Estilo.BORDE);
            g2.draw(m);
            g2.setFont(Estilo.fuente(Estilo.NEGRITA, (float) (26 * k)));
            g2.setColor(i == 2 ? Estilo.EXITO : Estilo.TEXTO);
            Estilo.textoCentrado(g2, datos[i][0], tx + tw / 2, ty + 36 * k);
            g2.setFont(Estilo.fuente(Estilo.NORMAL, (float) Math.max(10, 12.5 * k)));
            g2.setColor(Estilo.TEXTO_SUAVE);
            Estilo.textoCentrado(g2, datos[i][1], tx + tw / 2, ty + 64 * k);
        }

        g2.setFont(Estilo.fuente(Estilo.NORMAL, (float) Math.max(11, 13.5 * k)));
        g2.setColor(Estilo.TEXTO_TENUE);
        String nota = Estilo.recortar(g2.getFontMetrics(),
                "Respondiste las " + motor.total + " preguntas seguidas y sin fallar.", w - 40);
        Estilo.textoCentrado(g2, nota, cx, ty + th + 26 * k);
    }
}
