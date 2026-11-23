package estudio;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

/** Calificación del examen de problemas y acceso a la revisión de cada uno. */
final class PantallaResultadoExamen extends JPanel implements Pantalla {
    private static final Color A1 = EstadisticasProblemas.ACENTO1, A2 = EstadisticasProblemas.ACENTO2;

    private final Ventana app;
    private final ExamenProblemas examen;
    private final UIP.Destino volverA;
    private final UI.Boton volver, nuevo;
    private final UI.Texto titulo, subtitulo;
    private final UIP.Pila pila = new UIP.Pila();
    private final JScrollPane scroll;
    private final ValorAnimado entrada = new ValorAnimado(0, this::repaint);

    PantallaResultadoExamen(Ventana app, ExamenProblemas examen, UIP.Destino volverA) {
        super(null);
        this.app = app;
        this.examen = examen;
        this.volverA = volverA;
        setOpaque(false);
        volver = new UI.Boton("←   Problemas", UI.TipoBoton.FANTASMA);
        volver.addActionListener(e -> app.mostrar(volverA.get(), -1));
        nuevo = new UI.Boton("Nuevo examen", UI.TipoBoton.PRIMARIO);
        nuevo.setColores(A1, A2);
        nuevo.addActionListener(e -> app.mostrar(PantallaProblema.examen(app,
                ExamenProblemas.nuevo(examen.problemas.size(), examen.minutos), volverA), 1));
        titulo = new UI.Texto("Resultado del examen", Estilo.NEGRITA, 28f, Estilo.TEXTO);
        int n = examen.problemas.size();
        subtitulo = new UI.Texto(n + " problemas  ·  " + examen.minutosUsados() + " min"
                + (examen.minutos > 0 ? " de " + examen.minutos : "") + "  ·  cada problema vale "
                + Generadores.fmt(examen.valorProblema(), 1) + " puntos", Estilo.NORMAL, 14.5f, Estilo.TEXTO_SUAVE);

        List<UIP.Bloque> bloques = new ArrayList<>();
        bloques.add(new Marcador());
        for (int i = 0; i < n; i++) bloques.add(new FilaProblema(i));
        pila.poner(bloques);
        scroll = new JScrollPane(pila);
        UI.estilizarScroll(scroll);
        add(volver);
        add(nuevo);
        add(titulo);
        add(subtitulo);
        add(scroll);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "volver");
        getActionMap().put("volver", UI.accion(() -> true, volver::doClick));
    }

    @Override
    public void alMostrar() {
        app.setAcento(A1, A2);
        entrada.fijar(0);
        entrada.ir(1, 1100);
    }

    private int anchoContenido(int w) {
        return Math.max(260, Math.min(900, w - 2 * (w < 760 ? 20 : 32)));
    }

    @Override
    public void doLayout() {
        Dimension d = UI.diseno(this);
        int ancho = anchoContenido(d.width), x0 = (d.width - ancho) / 2;
        Dimension dv = UI.prefDiseno(volver), dn = UI.prefDiseno(nuevo);
        UI.poner(volver, x0 - 10, 22, dv.width, 40);
        UI.poner(nuevo, x0 + ancho - dn.width, 20, dn.width, 44);
        int ht = (int) Math.round(titulo.alturaPara(Estilo.px(ancho)) / Estilo.escala());
        int hs = (int) Math.round(subtitulo.alturaPara(Estilo.px(ancho)) / Estilo.escala());
        UI.poner(titulo, x0, 76, ancho, ht);
        UI.poner(subtitulo, x0, 76 + ht + 3, ancho, hs);
        int top = 76 + ht + 3 + hs + 16;
        UI.poner(scroll, x0 - 6, top, ancho + 12, Math.max(60, d.height - top));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Estilo.suavizar(g2);
        Dimension d = Estilo.unidadesDeDiseno(g2, getWidth(), getHeight());
        int ancho = anchoContenido(d.width);
        UIP.rayaTitulo(g2, (d.width - ancho) / 2.0, 66, Estilo.easeOut(entrada.get()));
        g2.dispose();
    }

    /** Calificación grande con un anillo. */
    private final class Marcador extends UIP.Bloque {
        @Override
        int alto(int w) {
            return 170;
        }

        @Override
        void pintar(Graphics2D g, int w, int h) {
            int cal = examen.calificacion();
            double e = Estilo.easeOut(entrada.get());
            Color c = cal >= 80 ? Estilo.EXITO : cal >= 60 ? Estilo.AVISO : Estilo.ERROR;
            double cx = UIP.PAD + 60, cy = h / 2.0, r = 54;
            g.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(255, 255, 255, 18));
            g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
            g.setColor(c);
            g.draw(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 90, -360 * cal / 100.0 * e, Arc2D.OPEN));
            g.setFont(Estilo.fuente(Estilo.NEGRITA, 30f));
            g.setColor(Estilo.TEXTO);
            Estilo.textoCentrado(g, String.valueOf((int) Math.round(cal * e)), cx, cy - 4);
            g.setFont(Estilo.fuente(Estilo.NORMAL, 12.5f));
            g.setColor(Estilo.TEXTO_SUAVE);
            Estilo.textoCentrado(g, "de 100", cx, cy + 22);

            double x = cx + r + 34, ancho = w - x - UIP.PAD;
            String msg = cal >= 90 ? "Excelente. Dominas el procedimiento." : cal >= 80 ? "Muy bien, casi todo correcto."
                    : cal >= 60 ? "Vas bien. Revisa los problemas con errores." : "Hay que practicar más. Revisa cada solución paso a paso.";
            double y = UIP.parrafo(g, msg, Estilo.SEMI, 18f, Estilo.TEXTO, x, cy - 44, ancho, 1.25, 2);
            int ok = 0, tot = 0;
            for (int i = 0; i < examen.problemas.size(); i++) {
                ok += examen.correctas(i);
                tot += examen.total(i);
            }
            UIP.parrafo(g, ok + " de " + tot + " respuestas correctas. Las estadísticas de cada tipo ya se actualizaron "
                    + "y la práctica adaptativa insistirá en los que fallaste.", Estilo.NORMAL, 14f, Estilo.TEXTO_SUAVE, x, y + 6, ancho, 1.35, 3);
        }
    }

    /** Un problema del examen con su puntuación y el botón para revisarlo. */
    private final class FilaProblema extends UIP.Bloque {
        private final int i;
        private final UI.Boton revisar = new UI.Boton("Revisar", UI.TipoBoton.SECUNDARIO);

        FilaProblema(int i) {
            this.i = i;
            revisar.addActionListener(e -> app.mostrar(PantallaProblema.revision(app, examen, i,
                    () -> new PantallaResultadoExamen(app, examen, volverA)), 1));
            add(revisar);
        }

        private String nombre() {
            Generadores.Problema p = examen.problemas.get(i);
            return (i + 1) + ".  " + p.tipo() + "  ·  " + p.titulo();
        }

        private int anchoTexto(int w) {
            return w - 2 * UIP.PAD - UI.prefDiseno(revisar).width - 20;
        }

        @Override
        int alto(int w) {
            return UIP.PAD + UIP.altoTexto(this, nombre(), Estilo.SEMI, 16f, anchoTexto(w), 1.25, 0) + 8 + 20 + 10 + UIP.PAD - 6;
        }

        @Override
        void disponer(int w) {
            Dimension d = UI.prefDiseno(revisar);
            UI.poner(revisar, w - UIP.PAD - d.width, (UI.diseno(this).height - 44) / 2.0, d.width, 44);
        }

        @Override
        void pintar(Graphics2D g, int w, int h) {
            int ok = examen.correctas(i), tot = examen.total(i);
            double y = UIP.parrafo(g, nombre(), Estilo.SEMI, 16f, Estilo.TEXTO, UIP.PAD, UIP.PAD - 4, anchoTexto(w), 1.25, 0);
            Color c = ok == tot ? Estilo.EXITO : ok == 0 ? Estilo.ERROR : Estilo.AVISO;
            String t = ok + " de " + tot + " correctas  ·  " + Generadores.fmt(examen.puntos(i), 1) + " de "
                    + Generadores.fmt(examen.valorProblema(), 1) + " pts";
            g.setFont(Estilo.fuente(Estilo.SEMI, 13.5f));
            g.setColor(c);
            g.drawString(t, (float) UIP.PAD, (float) (y + 16));
            UI.Barra.pintar(g, UIP.PAD, y + 26, Math.min(260, anchoTexto(w)), 6, tot == 0 ? 0 : ok / (double) tot, c,
                    Estilo.mezclar(c, Color.WHITE, 0.25));
        }
    }
}
