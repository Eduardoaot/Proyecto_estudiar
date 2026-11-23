package estudio;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Los 100 problemas resueltos de problemas_ordinario.json (cinco semillas fijas por tipo), con su solución. */
final class PantallaResueltos extends JPanel implements Pantalla {
    private static final Color A1 = EstadisticasProblemas.ACENTO1, A2 = EstadisticasProblemas.ACENTO2;

    private final Ventana app;
    private final UI.Boton volver;
    private final UI.Texto titulo, subtitulo;
    private final UIP.Pila pila = new UIP.Pila();
    private final JScrollPane scroll;
    private final ValorAnimado entrada = new ValorAnimado(0, this::repaint);

    PantallaResueltos(Ventana app) {
        super(null);
        this.app = app;
        setOpaque(false);
        volver = new UI.Boton("←   Problemas", UI.TipoBoton.FANTASMA);
        volver.addActionListener(e -> app.mostrar(new PantallaProblemas(app), -1));
        titulo = new UI.Texto("Problemas resueltos", Estilo.NEGRITA, 30f, Estilo.TEXTO);
        subtitulo = new UI.Texto("Cinco ejemplos de cada tipo con su solución paso a paso. En T20, el ejemplo 1 es el caso "
                + "del laboratorio farmacéutico visto en clase.", Estilo.NORMAL, 14.5f, Estilo.TEXTO_SUAVE);
        List<UIP.Bloque> bloques = new ArrayList<>();
        for (Generadores.Tipo t : Generadores.TIPOS.stream()
                .sorted((a, b) -> a.numeroPaso() != b.numeroPaso() ? Integer.compare(a.numeroPaso(), b.numeroPaso()) : a.id().compareTo(b.id()))
                .toList()) {
            bloques.add(new Fila(t));
        }
        pila.poner(bloques);
        pila.gap = 10;
        scroll = new JScrollPane(pila);
        UI.estilizarScroll(scroll);
        add(volver);
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
        entrada.ir(1, 500);
    }

    private int anchoContenido(int w) {
        return Math.max(260, Math.min(980, w - 2 * (w < 760 ? 20 : 32)));
    }

    @Override
    public void doLayout() {
        Dimension d = UI.diseno(this);
        int ancho = anchoContenido(d.width), x0 = (d.width - ancho) / 2;
        Dimension dv = UI.prefDiseno(volver);
        UI.poner(volver, x0 - 10, 22, dv.width, 40);
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
        UIP.rayaTitulo(g2, (d.width - anchoContenido(d.width)) / 2.0, 66, Estilo.easeOut(entrada.get()));
        g2.dispose();
    }

    /** Un tipo con sus cinco ejemplos. */
    private final class Fila extends UIP.Bloque {
        private final Generadores.Tipo tipo;
        private final List<UI.Boton> botones = new ArrayList<>();

        Fila(Generadores.Tipo tipo) {
            this.tipo = tipo;
            List<Long> semillas = Generadores.semillasResueltas(tipo.id());
            for (int i = 0; i < semillas.size(); i++) {
                long s = semillas.get(i);
                UI.Boton b = new UI.Boton(tipo.id().equals("T20") && s == 0 ? "Ejemplo de clase" : "Ejemplo " + (i + 1),
                        UI.TipoBoton.SECUNDARIO);
                b.addActionListener(e -> app.mostrar(PantallaProblema.resuelto(app, tipo.id(), s, () -> new PantallaResueltos(app)), 1));
                botones.add(b);
                add(b);
            }
        }

        private String nombre() {
            return tipo.id() + "  ·  " + tipo.titulo();
        }

        private int botonesAlto(int w, boolean aplicar, int y) {
            int x = UIP.PAD, fila = 0;
            for (UI.Boton b : botones) {
                int bw = UI.prefDiseno(b).width;
                if (x > UIP.PAD && x + bw > w - UIP.PAD) {
                    x = UIP.PAD;
                    fila++;
                }
                if (aplicar) UI.poner(b, x, y + fila * 52, bw, 44);
                x += bw + 8;
            }
            return (fila + 1) * 52 - 8;
        }

        private int cabecera(int w) {
            return UIP.altoTexto(this, nombre(), Estilo.SEMI, 16.5f, w - 2 * UIP.PAD, 1.25, 0)
                    + 4 + UIP.altoTexto(this, tipo.tema(), Estilo.NORMAL, 13f, w - 2 * UIP.PAD, 1.3, 0);
        }

        @Override
        int alto(int w) {
            return UIP.PAD - 4 + cabecera(w) + 12 + botonesAlto(w, false, 0) + UIP.PAD - 4;
        }

        @Override
        void disponer(int w) {
            botonesAlto(w, true, UIP.PAD - 4 + cabecera(w) + 12);
        }

        @Override
        void pintar(Graphics2D g, int w, int h) {
            double y = UIP.parrafo(g, nombre(), Estilo.SEMI, 16.5f, Estilo.TEXTO, UIP.PAD, UIP.PAD - 4, w - 2 * UIP.PAD, 1.25, 0);
            UIP.parrafo(g, tipo.tema(), Estilo.NORMAL, 13f, Estilo.TEXTO_TENUE, UIP.PAD, y + 4, w - 2 * UIP.PAD, 1.3, 0);
        }
    }
}
