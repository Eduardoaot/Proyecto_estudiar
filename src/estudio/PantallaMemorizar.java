package estudio;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Modo memorizar: escribir la respuesta de una pregunta y compararla palabra por palabra todas las veces
 * que se quiera. No cuenta como respuesta ni cambia el avance; al salir se vuelve a la misma pantalla de
 * estudio, exactamente donde estaba.
 */
final class PantallaMemorizar extends JPanel implements Pantalla {
    private static final int PAD = 26, ANCHO_MAX = 900;

    private final Ventana app;
    private final Materia materia;
    private final Pregunta pregunta;
    private final JComponent volverA;
    private final UI.Boton volver, comparar, ver, limpiar;
    private final UI.Texto titulo, subtitulo, textoPregunta;
    private final UI.Campo campo = new UI.Campo("Escribe la respuesta de memoria…");
    private final Comparacion comparacion;
    private final Contenido contenido = new Contenido();
    private final JScrollPane scroll;
    private final ValorAnimado entrada = new ValorAnimado(0, this::repaint);

    private int intentos, exactas, mejor = -1;
    /** Evita que borrar el campo tras comparar cuente como empezar a escribir. */
    private boolean silencio;

    PantallaMemorizar(Ventana app, Materia materia, Pregunta pregunta, JComponent volverA) {
        super(null);
        this.app = app;
        this.materia = materia;
        this.pregunta = pregunta;
        this.volverA = volverA;
        this.comparacion = new Comparacion();
        setOpaque(false);

        volver = new UI.Boton("←   Volver al estudio", UI.TipoBoton.FANTASMA);
        volver.addActionListener(e -> salir());
        titulo = new UI.Texto("Memorizar respuesta", Estilo.NEGRITA, 28f, Estilo.TEXTO);
        subtitulo = new UI.Texto("", Estilo.NORMAL, 14.5f, Estilo.TEXTO_SUAVE);
        textoPregunta = new UI.Texto(pregunta.pregunta(), Estilo.SEMI, 20f, Estilo.TEXTO);

        comparar = new UI.Boton("Comparar", UI.TipoBoton.PRIMARIO);
        comparar.setColores(materia.acento1, materia.acento2);
        comparar.setAtajo("Enter");
        comparar.addActionListener(e -> comparar());
        ver = new UI.Boton("Ver respuesta", UI.TipoBoton.SECUNDARIO);
        ver.addActionListener(e -> {
            comparacion.alternarVer();
            contenido.revalidate();
            contenido.repaint();
        });
        limpiar = new UI.Boton("Borrar y volver a intentar", UI.TipoBoton.FANTASMA);
        limpiar.addActionListener(e -> {
            campo.area.setText("");
            comparacion.ocultar();
            campo.area.requestFocusInWindow();
        });
        campo.setAcento(materia.acento1);
        campo.area.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { editado(); }
            @Override public void removeUpdate(DocumentEvent e) { editado(); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });
        campo.area.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "comparar");
        campo.area.getActionMap().put("comparar", UI.accion(() -> true, this::comparar));
        campo.area.getInputMap().put(KeyStroke.getKeyStroke("shift ENTER"), "insert-break");

        contenido.add(textoPregunta);
        contenido.add(campo);
        contenido.add(comparar);
        contenido.add(ver);
        contenido.add(limpiar);
        contenido.add(comparacion);
        scroll = new JScrollPane(contenido);
        UI.estilizarScroll(scroll);
        add(volver);
        add(titulo);
        add(subtitulo);
        add(scroll);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "salir");
        getActionMap().put("salir", UI.accion(() -> true, this::salir));
        actualizarSubtitulo();
    }

    private void salir() {
        app.mostrar(volverA, -1);
    }

    private void actualizarSubtitulo() {
        String s = "Sin penalización: tu avance queda exactamente igual  ·  intentos: " + intentos;
        if (mejor >= 0) s += "  ·  mejor: " + mejor + " %";
        if (exactas > 0) s += "  ·  idénticas: " + exactas;
        subtitulo.setTexto(s);
    }

    /** Al empezar a escribir de nuevo desaparece todo lo de abajo, para no copiar la respuesta. */
    private void editado() {
        if (silencio) return;
        if (comparacion.estado != Comparacion.OCULTA || comparacion.verRespuesta) comparacion.ocultar();
    }

    private void comparar() {
        String t = campo.area.getText().trim();
        if (t.isEmpty()) {
            campo.area.requestFocusInWindow();
            return;
        }
        intentos++;
        comparacion.comparar(t);
        // El campo queda vacío para volver a escribirla desde cero.
        silencio = true;
        campo.area.setText("");
        silencio = false;
        campo.area.requestFocusInWindow();
        mejor = Math.max(mejor, comparacion.porcentaje);
        if (comparacion.identica) exactas++;
        actualizarSubtitulo();
        contenido.revalidate();
        contenido.repaint();
        SwingUtilities.invokeLater(() -> {
            contenido.validate();
            contenido.scrollRectToVisible(comparacion.getBounds());
        });
    }

    @Override
    public void alMostrar() {
        app.setAcento(materia.acento1, materia.acento2);
        entrada.fijar(0);
        entrada.ir(1, 500);
        SwingUtilities.invokeLater(() -> campo.area.requestFocusInWindow());
    }

    private int anchoContenido(int w) {
        return Math.max(260, Math.min(ANCHO_MAX, w - 2 * (w < 760 ? 20 : 32)));
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
        UI.poner(scroll, x0 - 6, top, ancho + 12, Math.max(80, d.height - top));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Estilo.suavizar(g2);
        Dimension d = Estilo.unidadesDeDiseno(g2, getWidth(), getHeight());
        int x0 = (d.width - anchoContenido(d.width)) / 2;
        double e = Estilo.easeOut(entrada.get());
        g2.setPaint(new GradientPaint(x0, 0, materia.acento1, x0 + 60, 0, materia.acento2));
        g2.fill(new RoundRectangle2D.Double(x0, 66, 44 * e, 4, 4, 4));
        g2.dispose();
    }

    // ================================================================== contenido

    private final class Contenido extends JPanel implements Scrollable {
        private int yRespuesta;

        Contenido() {
            super(null);
            setOpaque(false);
        }

        private int disponer(int w, boolean aplicar) {
            int interior = w - 2 * PAD - 12;
            int x = PAD + 6;
            int y = 6 + PAD + 22;
            int hp = (int) Math.round(textoPregunta.alturaPara(Estilo.px(interior)) / Estilo.escala());
            if (aplicar) UI.poner(textoPregunta, x, y, interior, hp);
            y += hp + 18;
            if (aplicar) yRespuesta = y;
            y += 22;
            if (aplicar) UI.poner(campo, x - 4, y, interior + 8, 140);
            y += 140 + 14;
            Dimension dc = UI.prefDiseno(comparar), dv = UI.prefDiseno(ver), dl = UI.prefDiseno(limpiar);
            if (aplicar) {
                UI.poner(comparar, x + interior - dc.width, y, dc.width, 44);
                UI.poner(ver, x + interior - dc.width - 10 - dv.width, y, dv.width, 44);
                UI.poner(limpiar, x - 8, y, Math.min(dl.width, Math.max(60, interior - dc.width - dv.width - 30)), 44);
            }
            y += 44 + 18;
            int hc = comparacion.alto(interior);
            comparacion.setVisible(hc > 0);
            if (aplicar && hc > 0) UI.poner(comparacion, x, y, interior, hc);
            if (hc > 0) y += hc + 10;
            return y + PAD;
        }

        @Override
        public void doLayout() {
            disponer(UI.diseno(this).width, true);
        }

        @Override
        public Dimension getPreferredSize() {
            int real = getParent() != null && getParent().getWidth() > 0 ? getParent().getWidth() : 800;
            int w = (int) Math.round(real / Estilo.escala());
            return new Dimension(real, Estilo.px(disponer(w, false)));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Estilo.suavizar(g2);
            Dimension d = Estilo.unidadesDeDiseno(g2, getWidth(), getHeight());
            RoundRectangle2D forma = new RoundRectangle2D.Double(6.5, 6.5, d.width - 13, d.height - 13, 22, 22);
            g2.setPaint(new GradientPaint(0, 0, new Color(0x171D33), 0, d.height, new Color(0x12172B)));
            g2.fill(forma);
            g2.setColor(Estilo.BORDE);
            g2.draw(forma);
            g2.setFont(Estilo.etiqueta(11.5f));
            g2.setColor(Estilo.mezclar(materia.acento1, Color.WHITE, 0.2));
            g2.drawString("PREGUNTA " + pregunta.id() + "  ·  " + pregunta.tema().toUpperCase(), PAD + 6, 6 + PAD + 8);
            g2.drawString("TU RESPUESTA", PAD + 6, yRespuesta + 12);
            g2.dispose();
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return Estilo.px(26); }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(26, r.height - 60); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    // ================================================================== comparación palabra por palabra

    /**
     * Compara en orden (subsecuencia común más larga) las palabras escritas con las de la respuesta, sin
     * tomar en cuenta mayúsculas, acentos ni signos. Pinta la respuesta oficial palabra por palabra:
     * verde si la escribiste igual, ámbar si casi (falta de ortografía), rojo si falta.
     */
    private final class Comparacion extends JComponent {
        static final int OCULTA = 0, COMPARADA = 1, EDITANDO = 2;
        private static final int MAL = 0, CASI = 1, BIEN = 2, SIGNO = 3;
        private static final float TAM = 17f;

        int estado = OCULTA;
        boolean verRespuesta, identica;
        int porcentaje;
        private final List<String> tokens = new ArrayList<>();
        private final List<Integer> colores = new ArrayList<>();
        private List<String> sobran = List.of();

        Comparacion() {
            setOpaque(false);
            for (String t : pregunta.respuesta().split("\\s+")) if (!t.isEmpty()) tokens.add(t);
        }

        void ocultar() {
            estado = OCULTA;
            verRespuesta = false;
            ver.setText("Ver respuesta");
            contenido.revalidate();
            contenido.repaint();
        }

        void alternarVer() {
            verRespuesta = !verRespuesta;
            ver.setText(verRespuesta ? "Ocultar respuesta" : "Ver respuesta");
        }

        private String escrita = "";

        void comparar(String escritaUsuario) {
            String escrita = escritaUsuario;
            // Palabras de la respuesta con el índice del token que las contiene.
            List<String> resp = new ArrayList<>();
            List<Integer> dueno = new ArrayList<>();
            for (int i = 0; i < tokens.size(); i++) {
                for (String p : Evaluador.palabras(tokens.get(i))) {
                    resp.add(p);
                    dueno.add(i);
                }
            }
            List<String> usr = Evaluador.palabras(escrita);
            int n = resp.size(), m = usr.size();
            int[][] lcs = new int[n + 1][m + 1];
            for (int i = n - 1; i >= 0; i--) {
                for (int j = m - 1; j >= 0; j--) {
                    lcs[i][j] = Evaluador.parecidas(resp.get(i), usr.get(j)) ? lcs[i + 1][j + 1] + 1
                            : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
            int[] marca = new int[n]; // MAL por omisión
            boolean[] usada = new boolean[m];
            int i = 0, j = 0;
            while (i < n && j < m) {
                if (Evaluador.parecidas(resp.get(i), usr.get(j)) && lcs[i][j] == lcs[i + 1][j + 1] + 1) {
                    marca[i] = resp.get(i).equals(usr.get(j)) ? BIEN : CASI;
                    usada[j] = true;
                    i++;
                    j++;
                } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                    i++;
                } else {
                    j++;
                }
            }
            colores.clear();
            for (int t = 0; t < tokens.size(); t++) colores.add(SIGNO);
            int bien = 0;
            for (int k = 0; k < n; k++) {
                int t = dueno.get(k), c = marca[k];
                int previo = colores.get(t);
                colores.set(t, previo == SIGNO ? c : Math.min(previo, c));
                if (c == BIEN) bien++;
            }
            List<String> extra = new ArrayList<>();
            for (int k = 0; k < m; k++) if (!usada[k]) extra.add(usr.get(k));
            sobran = extra;
            this.escrita = escritaUsuario;
            // Las palabras de más también restan: la meta es escribirla exactamente igual.
            porcentaje = n == 0 ? 0 : (int) Math.round(100.0 * bien / (n + extra.size()));
            identica = n > 0 && bien == n && extra.isEmpty();
            estado = COMPARADA;
        }

        private String encabezado() {
            if (identica) return "¡Idéntica a la respuesta!";
            return porcentaje + " % exacta  ·  " + (porcentaje >= 90 ? "casi la tienes" : porcentaje >= 60 ? "vas bien" : "sigue intentando");
        }

        private boolean muestraRespuesta() {
            return estado == COMPARADA || verRespuesta;
        }

        int alto(int w) {
            if (estado == OCULTA && !verRespuesta) return 0;
            int h = 0;
            if (estado != OCULTA) h += 34;
            if (estado == EDITANDO && !verRespuesta) {
                h += UIP.altoTexto(this, "La respuesta se ocultó mientras escribes. Pulsa Comparar para revisar otra vez.",
                        Estilo.NORMAL, 14f, w, 1.35, 0);
                return h + 6;
            }
            if (muestraRespuesta()) {
                h += 22 + palabras(w, null);
                if (estado == COMPARADA) h += 10 + UIP.altoTexto(this, textoEscrita(), Estilo.NORMAL, 14.5f, w, 1.35, 0);
                if (estado == COMPARADA && !sobran.isEmpty()) {
                    h += 10 + UIP.altoTexto(this, textoSobran(), Estilo.NORMAL, 14f, w, 1.35, 0);
                }
                if (estado == COMPARADA && !identica) h += 8 + UIP.altoTexto(this, LEYENDA, Estilo.NORMAL, 12.5f, w, 1.3, 0);
            }
            return h + 6;
        }

        private static final String LEYENDA = "Verde: igual  ·  ámbar: casi (revisa la ortografía)  ·  rojo: te faltó o está fuera de orden";

        private String textoEscrita() {
            return "Lo que escribiste: " + escrita;
        }

        private String textoSobran() {
            return "Escribiste de más: " + String.join(" ", sobran);
        }

        /** Distribuye y (si g no es null) dibuja la respuesta palabra por palabra. Devuelve el alto usado. */
        private int palabras(int w, Graphics2D g) {
            FontMetrics fm = UIP.fm(this, Estilo.SEMI, TAM);
            double alto = fm.getHeight() * 1.45, x = 0, y = 0, esp = fm.stringWidth(" ");
            boolean colorear = estado == COMPARADA && colores.size() == tokens.size();
            for (int t = 0; t < tokens.size(); t++) {
                String s = tokens.get(t);
                double tw = fm.stringWidth(s);
                if (x > 0 && x + tw > w) {
                    x = 0;
                    y += alto;
                }
                if (g != null) {
                    Color c = Estilo.TEXTO;
                    if (colorear) {
                        int k = colores.get(t);
                        c = k == BIEN ? Estilo.EXITO : k == CASI ? Estilo.AVISO : k == MAL ? Estilo.ERROR : Estilo.TEXTO_SUAVE;
                        if (k != SIGNO) {
                            g.setColor(Estilo.alfa(c, 34));
                            g.fill(new RoundRectangle2D.Double(x - 3, y + 2, tw + 6, fm.getHeight() + 2, 8, 8));
                        }
                        c = Estilo.mezclar(c, Color.WHITE, 0.25);
                    }
                    g.setColor(c);
                    g.drawString(s, (float) x, (float) (y + 3 + fm.getAscent()));
                }
                x += tw + esp + 4;
            }
            return (int) Math.ceil(y + alto);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Estilo.suavizar(g2);
            Dimension d = Estilo.unidadesDeDiseno(g2, getWidth(), getHeight());
            int w = d.width;
            double y = 0;
            if (estado != OCULTA) {
                g2.setFont(Estilo.fuente(Estilo.NEGRITA, 18f));
                Color c = identica ? Estilo.EXITO : porcentaje >= 60 ? Estilo.AVISO : Estilo.ERROR;
                g2.setColor(c);
                g2.drawString(encabezado(), 0, (float) (y + 22));
                y += 34;
            }
            if (estado == EDITANDO && !verRespuesta) {
                UIP.parrafo(g2, "La respuesta se ocultó mientras escribes. Pulsa Comparar para revisar otra vez.", Estilo.NORMAL, 14f,
                        Estilo.TEXTO_TENUE, 0, y, w, 1.35, 0);
                g2.dispose();
                return;
            }
            if (muestraRespuesta()) {
                g2.setFont(Estilo.etiqueta(11.5f));
                g2.setColor(Estilo.mezclar(materia.acento1, Color.WHITE, 0.2));
                g2.drawString("RESPUESTA CORRECTA", 0, (float) (y + 12));
                y += 22;
                Graphics2D gp = (Graphics2D) g2.create();
                gp.translate(0, y);
                gp.setFont(Estilo.fuente(Estilo.SEMI, TAM));
                y += palabras(w, gp);
                gp.dispose();
                if (estado == COMPARADA) {
                    y = UIP.parrafo(g2, textoEscrita(), Estilo.NORMAL, 14.5f, Estilo.TEXTO_SUAVE, 0, y + 10, w, 1.35, 0);
                }
                if (estado == COMPARADA && !sobran.isEmpty()) {
                    y = UIP.parrafo(g2, textoSobran(), Estilo.NORMAL, 14f, Estilo.mezclar(Estilo.AVISO, Color.WHITE, 0.2), 0, y + 10, w, 1.35, 0);
                }
                if (estado == COMPARADA && !identica) {
                    UIP.parrafo(g2, LEYENDA, Estilo.NORMAL, 12.5f, Estilo.TEXTO_TENUE, 0, y + 8, w, 1.3, 0);
                }
            }
            g2.dispose();
        }
    }
}
