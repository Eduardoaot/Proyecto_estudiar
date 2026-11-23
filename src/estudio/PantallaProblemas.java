package estudio;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/** Catálogo de problemas de cálculo: modos de práctica, examen de problemas y los 20 tipos agrupados por paso. */
final class PantallaProblemas extends JPanel implements Pantalla {
    private static final Color A1 = EstadisticasProblemas.ACENTO1, A2 = EstadisticasProblemas.ACENTO2;
    private static final int[] PROBLEMAS = {3, 4, 5, 6};
    private static final int[] MINUTOS = {0, 30, 60, 90};
    private static int selProblemas = 1, selMinutos = 3;

    private final Ventana app;
    private final UI.Boton volver;
    private final UI.Texto titulo, subtitulo;
    private final UIP.Pila pila = new UIP.Pila();
    private final JScrollPane scroll;
    private final ValorAnimado entrada = new ValorAnimado(0, this::repaint);
    private final List<TarjetaTipo> tarjetas = new ArrayList<>();

    PantallaProblemas(Ventana app) {
        super(null);
        this.app = app;
        setOpaque(false);
        volver = new UI.Boton("←   Materias", UI.TipoBoton.FANTASMA);
        volver.addActionListener(e -> app.mostrar(new PantallaInicio(app), -1));
        titulo = new UI.Texto("Problemas de cálculo", Estilo.NEGRITA, 32f, Estilo.TEXTO);
        subtitulo = new UI.Texto("Estudio de Tiempos, pasos 8 a 12  ·  20 tipos  ·  cada problema se genera nuevo y se "
                + "califica respuesta por respuesta", Estilo.NORMAL, 15f, Estilo.TEXTO_SUAVE);

        List<UIP.Bloque> bloques = new ArrayList<>();
        bloques.add(new BloqueModos());
        bloques.add(new BloqueExamen());
        // Tipos agrupados por paso, en el orden del temario.
        Map<Integer, List<Generadores.Tipo>> grupos = new TreeMap<>();
        for (Generadores.Tipo t : Generadores.TIPOS) grupos.computeIfAbsent(t.numeroPaso(), k -> new ArrayList<>()).add(t);
        for (List<Generadores.Tipo> g : grupos.values()) bloques.add(new Grupo(g));
        pila.poner(bloques);
        scroll = new JScrollPane(pila);
        UI.estilizarScroll(scroll);
        add(volver);
        add(titulo);
        add(subtitulo);
        add(scroll);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "volver");
        getActionMap().put("volver", UI.accion(() -> true, volver::doClick));
    }

    private UIP.Destino aqui() {
        return () -> new PantallaProblemas(app);
    }

    @Override
    public void alMostrar() {
        app.setAcento(A1, A2);
        entrada.fijar(0);
        entrada.ir(1, 500);
        for (int i = 0; i < tarjetas.size(); i++) tarjetas.get(i).aparecer(120 + Math.min(i, 20) * 24);
    }

    private int anchoContenido(int w) {
        return Math.max(260, Math.min(1180, w - 2 * (w < 760 ? 22 : 32)));
    }

    @Override
    public void doLayout() {
        Dimension d = UI.diseno(this);
        int ancho = anchoContenido(d.width), x0 = (d.width - ancho) / 2;
        Dimension dv = UI.prefDiseno(volver);
        UI.poner(volver, x0 - 10, 22, dv.width, 40);
        float tam = d.width < 620 ? 22f : (d.width < 900 ? 26f : 32f);
        if (titulo.getTam() != tam) titulo.setFuente(Estilo.NEGRITA, tam);
        int ht = (int) Math.round(titulo.alturaPara(Estilo.px(ancho)) / Estilo.escala());
        int hs = (int) Math.round(subtitulo.alturaPara(Estilo.px(ancho)) / Estilo.escala());
        UI.poner(titulo, x0, 76, ancho, ht);
        UI.poner(subtitulo, x0, 76 + ht + 4, ancho, hs);
        int top = 76 + ht + 4 + hs + 18;
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

    // ================================================================== modos de práctica

    private final class BloqueModos extends UIP.Bloque {
        private final UI.Boton adaptativa = new UI.Boton("Práctica adaptativa", UI.TipoBoton.PRIMARIO);
        private final UI.Boton azar = new UI.Boton("Tipo al azar", UI.TipoBoton.SECUNDARIO);
        private final UI.Boton clase = new UI.Boton("Ejemplo de clase", UI.TipoBoton.SECUNDARIO);
        private final UI.Boton resueltos = new UI.Boton("Problemas resueltos", UI.TipoBoton.SECUNDARIO);
        private final List<UI.Boton> botones = List.of(adaptativa, azar, clase, resueltos);

        BloqueModos() {
            adaptativa.setColores(A1, A2);
            adaptativa.addActionListener(e -> app.mostrar(PantallaProblema.adaptativa(app, aqui()), 1));
            azar.addActionListener(e -> app.mostrar(PantallaProblema.azar(app, aqui()), 1));
            clase.addActionListener(e -> app.mostrar(PantallaProblema.practica(app, "T20", 0, aqui()), 1));
            resueltos.addActionListener(e -> app.mostrar(new PantallaResueltos(app), 1));
            for (UI.Boton b : botones) add(b);
        }

        private String resumen() {
            int i = EstadisticasProblemas.totalIntentos(), a = EstadisticasProblemas.totalAciertos();
            if (i == 0) return "Aún no resuelves problemas. La práctica adaptativa elige los tipos que menos has practicado y "
                    + "los que más fallas. El ejemplo de clase es el caso del laboratorio farmacéutico (T20, semilla 0).";
            int racha = EstadisticasProblemas.racha();
            return i + (i == 1 ? " problema calificado" : " problemas calificados") + "  ·  " + a + " completos sin errores ("
                    + Math.round(100.0 * a / i) + " %)  ·  " + EstadisticasProblemas.tiposDominados() + " de 20 tipos dominados"
                    + (racha > 1 ? "  ·  racha de " + racha + " días" : "");
        }

        private int filas(int w, boolean aplicar, int y) {
            int x = UIP.PAD, fila = 0, interior = w - 2 * UIP.PAD;
            for (UI.Boton b : botones) {
                int bw = UI.prefDiseno(b).width;
                if (x > UIP.PAD && x + bw > UIP.PAD + interior) {
                    x = UIP.PAD;
                    fila++;
                }
                if (aplicar) UI.poner(b, x, y + fila * 54, Math.min(bw, interior), 44);
                x += bw + 10;
            }
            return (fila + 1) * 54 - 10;
        }

        @Override
        int alto(int w) {
            return UIP.PAD + 26 + UIP.altoTexto(this, resumen(), Estilo.NORMAL, 14.5f, w - 2 * UIP.PAD, 1.4, 0) + 16
                    + filas(w, false, 0) + UIP.PAD;
        }

        @Override
        void disponer(int w) {
            int y = UIP.PAD + 26 + UIP.altoTexto(this, resumen(), Estilo.NORMAL, 14.5f, w - 2 * UIP.PAD, 1.4, 0) + 16;
            filas(w, true, y);
        }

        @Override
        void pintar(Graphics2D g, int w, int h) {
            UIP.seccion(g, "Practicar", UIP.PAD, UIP.PAD - 4, w - UIP.PAD, A1);
            UIP.parrafo(g, resumen(), Estilo.NORMAL, 14.5f, Estilo.TEXTO_SUAVE, UIP.PAD, UIP.PAD + 24, w - 2 * UIP.PAD, 1.4, 0);
        }
    }

    // ================================================================== examen

    private final class BloqueExamen extends UIP.Bloque {
        private final UI.Segmentado problemas, minutos;
        private final UI.Boton empezar = new UI.Boton("Empezar examen", UI.TipoBoton.PRIMARIO);
        private static final String TEXTO = "Problemas seguidos, sin pistas ni solución hasta terminar. Siempre incluye un "
                + "caso integral (T20) o un tiempo tipo con máquina (T14). Al final ves la calificación sobre 100 y puedes "
                + "revisar cada problema paso a paso.";

        BloqueExamen() {
            String[] op = new String[PROBLEMAS.length];
            for (int i = 0; i < op.length; i++) op[i] = PROBLEMAS[i] + " problemas";
            problemas = new UI.Segmentado(op, selProblemas);
            minutos = new UI.Segmentado(new String[]{"Sin límite", "30 min", "60 min", "90 min"}, selMinutos);
            for (UI.Segmentado s : List.of(problemas, minutos)) s.setColores(A1, A2);
            problemas.setAlCambiar(i -> selProblemas = i);
            minutos.setAlCambiar(i -> selMinutos = i);
            empezar.setColores(A1, A2);
            empezar.addActionListener(e -> app.mostrar(PantallaProblema.examen(app,
                    ExamenProblemas.nuevo(PROBLEMAS[selProblemas], MINUTOS[selMinutos]), aqui()), 1));
            add(problemas);
            add(minutos);
            add(empezar);
        }

        private String historial() {
            List<EstadisticasProblemas.Examen> h = EstadisticasProblemas.historial();
            if (h.isEmpty()) return null;
            StringBuilder sb = new StringBuilder("Últimos exámenes:  ");
            int desde = Math.max(0, h.size() - 5);
            for (int i = h.size() - 1; i >= desde; i--) {
                EstadisticasProblemas.Examen e = h.get(i);
                if (i < h.size() - 1) sb.append("   ·   ");
                sb.append(e.puntos()).append(" pts (").append(e.fecha()).append(')');
            }
            return sb.toString();
        }

        private int controles(int w, boolean aplicar, int y) {
            Dimension dp = UI.prefDiseno(problemas), dm = UI.prefDiseno(minutos), de = UI.prefDiseno(empezar);
            int interior = w - 2 * UIP.PAD;
            if (dp.width + dm.width + de.width + 40 <= interior) {
                if (aplicar) {
                    UI.poner(problemas, UIP.PAD, y + 3, dp.width, dp.height);
                    UI.poner(minutos, UIP.PAD + dp.width + 20, y + 3, dm.width, dm.height);
                    UI.poner(empezar, UIP.PAD + interior - de.width, y, de.width, 44);
                }
                return 44;
            }
            if (aplicar) {
                UI.poner(problemas, UIP.PAD, y, Math.min(dp.width, interior), dp.height);
                UI.poner(minutos, UIP.PAD, y + 48, Math.min(dm.width, interior), dm.height);
                UI.poner(empezar, UIP.PAD, y + 96, Math.min(de.width, interior), 44);
            }
            return 140;
        }

        private int altoTexto(int w) {
            int h = UIP.altoTexto(this, TEXTO, Estilo.NORMAL, 14.5f, w - 2 * UIP.PAD, 1.4, 0);
            String hi = historial();
            if (hi != null) h += 6 + UIP.altoTexto(this, hi, Estilo.SEMI, 13f, w - 2 * UIP.PAD, 1.35, 0);
            return h;
        }

        @Override
        int alto(int w) {
            return UIP.PAD + 26 + altoTexto(w) + 16 + controles(w, false, 0) + UIP.PAD;
        }

        @Override
        void disponer(int w) {
            controles(w, true, UIP.PAD + 26 + altoTexto(w) + 16);
        }

        @Override
        void pintar(Graphics2D g, int w, int h) {
            UIP.seccion(g, "Examen de problemas", UIP.PAD, UIP.PAD - 4, w - UIP.PAD, A2);
            double y = UIP.parrafo(g, TEXTO, Estilo.NORMAL, 14.5f, Estilo.TEXTO_SUAVE, UIP.PAD, UIP.PAD + 24, w - 2 * UIP.PAD, 1.4, 0);
            String hi = historial();
            if (hi != null) UIP.parrafo(g, hi, Estilo.SEMI, 13f, Estilo.mezclar(A1, Color.WHITE, 0.3), UIP.PAD, y + 6, w - 2 * UIP.PAD, 1.35, 0);
        }
    }

    // ================================================================== grupo de tipos de un paso

    private final class Grupo extends UIP.Bloque {
        private static final int ALTO = 236, GAP = 10;
        private final List<TarjetaTipo> propias = new ArrayList<>();
        private final String nombre;

        Grupo(List<Generadores.Tipo> tipos) {
            conTarjeta = false;
            Generadores.Tipo t0 = tipos.get(0);
            nombre = t0.numeroPaso() == 99 ? "Caso integral · pasos 8 a 12" : t0.paso() + nombreDelPaso(t0.numeroPaso());
            for (Generadores.Tipo t : tipos) {
                TarjetaTipo tt = new TarjetaTipo(t);
                propias.add(tt);
                tarjetas.add(tt);
                add(tt);
            }
        }

        private String nombreDelPaso(int n) {
            return switch (n) {
                case 8 -> " · minutos normales por vez y por unidad";
                case 9 -> " · misceláneos y tiempo tipo manual";
                case 10 -> " · trabajo con máquinas";
                case 11 -> " · suplementos por descanso";
                case 12 -> " · tiempo tipo, producción y carga";
                default -> "";
            };
        }

        private int columnas(int w) {
            return Math.max(1, Math.min(3, (w + GAP) / (300 + GAP)));
        }

        @Override
        int alto(int w) {
            int c = columnas(w), f = (propias.size() + c - 1) / c;
            return 30 + f * (ALTO + GAP) + 6;
        }

        @Override
        void disponer(int w) {
            int c = columnas(w), cw = (w - GAP * (c - 1)) / c;
            for (int i = 0; i < propias.size(); i++) {
                UI.poner(propias.get(i), (i % c) * (cw + GAP), 30 + (i / c) * (ALTO + GAP), cw, ALTO);
            }
        }

        @Override
        void pintar(Graphics2D g, int w, int h) {
            UIP.seccion(g, nombre, 4, 4, w - 4, Estilo.TEXTO_TENUE);
        }
    }

    /** Tarjeta de un tipo de problema. */
    private final class TarjetaTipo extends JComponent {
        private final Generadores.Tipo tipo;
        private final ValorAnimado hover = new ValorAnimado(0, this::repaint);
        private final ValorAnimado aparicion = new ValorAnimado(1, this::repaint);

        TarjetaTipo(Generadores.Tipo tipo) {
            this.tipo = tipo;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Fórmulas: " + String.join("   ·   ", Generadores.formulario(tipo.id()).stream().map(Generadores::formulaPlana).toList()));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover.ir(1, 180); }
                @Override public void mouseExited(MouseEvent e) { hover.ir(0, 260); }
                @Override public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) app.mostrar(PantallaProblema.practica(app, tipo.id(), -1, aqui()), 1);
                }
            });
        }

        void aparecer(int retraso) {
            aparicion.fijar(0);
            Anim.despues(Math.max(1, retraso), () -> aparicion.ir(1, 420));
        }

        @Override
        protected void paintComponent(Graphics g) {
            double ap = aparicion.get();
            if (ap <= 0.001) return;
            Graphics2D g2 = (Graphics2D) g.create();
            Estilo.suavizar(g2);
            Dimension d = Estilo.unidadesDeDiseno(g2, getWidth(), getHeight());
            Estilo.alfa(g2, (float) Estilo.limitar(ap * 1.4));
            double hv = hover.get();
            g2.translate(0, (1 - ap) * 14 - hv * 3);
            int w = d.width, h = d.height - 4;
            RoundRectangle2D forma = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 20, 20);
            g2.setPaint(new GradientPaint(0, 0, Estilo.mezclar(new Color(0x19203A), new Color(0x202848), hv), 0, h, new Color(0x131830)));
            g2.fill(forma);
            g2.setColor(Estilo.mezclar(Estilo.BORDE, Estilo.alfa(A1, 160), hv));
            g2.draw(forma);

            double pad = 18, x = pad, ancho = w - 2 * pad;
            UIP.chapa(g2, tipo.id(), x, pad - 2, A1);
            UIP.dificultad(g2, tipo.dificultad(), x + UIP.anchoChapa(this, tipo.id()) + 10, pad + 9, A2);
            String sub = tipo.subtema();
            g2.setFont(Estilo.fuente(Estilo.NORMAL, 12f));
            g2.setColor(Estilo.TEXTO_TENUE);
            double sx = x + UIP.anchoChapa(this, tipo.id()) + 50;
            g2.drawString(Estilo.recortar(g2.getFontMetrics(), sub, w - pad - sx), (float) sx, (float) pad + 13.5f);

            double y = UIP.parrafo(g2, tipo.titulo(), Estilo.SEMI, 16.5f, Estilo.TEXTO, x, pad + 30, ancho, 1.2, 2);
            y = UIP.parrafo(g2, "Datos: " + tipo.datos(), Estilo.NORMAL, 13f, Estilo.TEXTO_SUAVE, x, y + 4, ancho, 1.3, 2);
            UIP.parrafo(g2, "Cambia: " + tipo.queCambia(), Estilo.NORMAL, 12.5f, Estilo.TEXTO_TENUE, x, y + 3, ancho, 1.3, 2);

            // Pie con el avance
            int i = EstadisticasProblemas.intentos(tipo.id()), a = EstadisticasProblemas.aciertos(tipo.id());
            int c = EstadisticasProblemas.campos(tipo.id()), ok = EstadisticasProblemas.camposOk(tipo.id());
            double py = h - pad - 6;
            String estado = i == 0 ? "Sin intentar" : a + " de " + i + " completos  ·  " + Math.round(100.0 * ok / Math.max(1, c)) + " % de respuestas";
            boolean dom = EstadisticasProblemas.dominado(tipo.id());
            g2.setFont(Estilo.fuente(Estilo.SEMI, 12.5f));
            g2.setColor(dom ? Estilo.EXITO : i == 0 ? Estilo.TEXTO_TENUE : Estilo.TEXTO_SUAVE);
            g2.drawString(Estilo.recortar(g2.getFontMetrics(), (dom ? "Dominado  ·  " : "") + estado, ancho), (float) x, (float) (py - 10));
            UI.Barra.pintar(g2, x, py, ancho, 5, c == 0 ? 0 : ok / (double) c, dom ? Estilo.EXITO : A1, dom ? Estilo.EXITO : A2);
            g2.dispose();
        }
    }
}
