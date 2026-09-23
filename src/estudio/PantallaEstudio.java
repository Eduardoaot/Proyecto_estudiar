package estudio;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Sesión de estudio: una pregunta cada vez, con corrección, repaso y bloqueo por falladas. */
final class PantallaEstudio extends JPanel implements Pantalla {
    private enum Modo { PREGUNTA, RESPUESTA }

    private static final int ANCHO_MAX_TARJETA = 860;
    private static final int ANCHO_LATERAL = 300;

    private final Ventana app;
    private final Materia materia;
    private final String tema;
    private final List<Pregunta> preguntas;
    private final String clave;
    private final Motor motor;

    private final UI.Boton volver, examenBoton;
    private final UI.Texto titulo, subtitulo, textoProgreso;
    private final UI.Barra barra = new UI.Barra();
    private final TarjetaPregunta tarjeta;
    private final Centro centro = new Centro();
    private final JScrollPane scroll;
    private final PanelLateral lateral;

    private Motor.Turno turno;
    private Pregunta pregunta;
    private Modo modo = Modo.PREGUNTA;
    private Evaluador.Resultado resultado;
    private boolean veredicto;
    private boolean ocupado;
    private String consecuenciaOk = "", consecuenciaMal = "";

    PantallaEstudio(Ventana app, Materia materia, String tema) {
        this(app, materia, tema, false);
    }

    PantallaEstudio(Ventana app, Materia materia, String tema, boolean examenDirecto) {
        super(null);
        this.app = app;
        this.materia = materia;
        this.tema = tema;
        this.preguntas = materia.preguntasDe(tema);
        this.clave = Progreso.clave(materia, tema);
        this.motor = Progreso.cargar(clave, preguntas.size(), tema == null);
        this.motor.setLimiteRepaso(Progreso.limiteRepaso());
        // Un examen guardado a medias se retoma tal cual: iniciarExamen() lo pondría a cero.
        if (examenDirecto && !this.motor.enExamen()) this.motor.iniciarExamen();
        this.tarjeta = new TarjetaPregunta();
        this.lateral = new PanelLateral();
        setOpaque(false);

        volver = new UI.Boton("←   Temas", UI.TipoBoton.FANTASMA);
        volver.addActionListener(e -> salir());
        examenBoton = new UI.Boton("Examen final", UI.TipoBoton.SECUNDARIO);
        examenBoton.addActionListener(e -> alternarExamen());
        titulo = new UI.Texto(tema == null ? "Todo el temario" : tema, Estilo.NEGRITA, 19f, Estilo.TEXTO);
        titulo.setMaxLineas(1);
        subtitulo = new UI.Texto(materia.nombre, Estilo.NORMAL, 13f, Estilo.TEXTO_SUAVE);
        subtitulo.setMaxLineas(1);
        textoProgreso = new UI.Texto("", Estilo.SEMI, 13f, Estilo.TEXTO_SUAVE);
        textoProgreso.setAlineacion(UI.Texto.DERECHA);
        barra.setColores(materia.acento1, materia.acento2);

        centro.add(tarjeta);
        scroll = new JScrollPane(centro);
        UI.estilizarScroll(scroll);

        add(volver);
        add(examenBoton);
        add(titulo);
        add(subtitulo);
        add(textoProgreso);
        add(barra);
        add(scroll);
        add(lateral);

        configurarTeclado();
        mostrarTurno(false);
    }

    private void configurarTeclado() {
        InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke("ENTER"), "enter");
        am.put("enter", UI.accion(() -> !ocupado, this::accionEnter));
        im.put(KeyStroke.getKeyStroke("M"), "marcar");
        am.put("marcar", UI.accion(() -> modo == Modo.RESPUESTA && !ocupado, this::cambiarVeredicto));
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "salir");
        am.put("salir", UI.accion(() -> !ocupado, this::salir));

        JTextArea area = tarjeta.campo.area;
        area.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "comprobar");
        area.getActionMap().put("comprobar", UI.accion(() -> true, this::accionEnter));
        area.getInputMap().put(KeyStroke.getKeyStroke("shift ENTER"), "insert-break");
    }

    private void accionEnter() {
        if (ocupado) return;
        if (modo == Modo.RESPUESTA) continuar();
        else comprobar(false);
    }

    @Override
    public void alMostrar() {
        app.setAcento(materia.acento1, materia.acento2);
        if (turno == null) {
            SwingUtilities.invokeLater(this::finalizar);
            return;
        }
        tarjeta.alfa = 0;
        Anim.ejecutar(520, 140, t -> {
            double e = Estilo.easeOut(t);
            tarjeta.alfa = (float) e;
            tarjeta.dy = 26 * (1 - e);
            centro.repaint();
        }, () -> {
            tarjeta.dy = 0;
            centro.repaint();
        });
        lateral.aparecer();
        SwingUtilities.invokeLater(() -> tarjeta.campo.area.requestFocusInWindow());
    }

    @Override
    public void alOcultar() {
        Progreso.guardar(clave, motor);
    }

    /** Salta al examen final del módulo (o vuelve a la práctica si ya está en él). */
    private void alternarExamen() {
        if (ocupado) return;
        if (motor.enExamen()) {
            motor.salirExamen();
        } else {
            boolean listo = motor.dominadas() >= motor.total;
            String alFallar = tema == null
                    ? "si fallas una, la repites hasta acertarla " + motor.retiro
                            + " veces seguidas y el examen continúa donde estaba."
                    : "si fallas una, vuelves a la práctica.";
            if (!listo && !UI.confirmar(this, "¿Empezar el examen final?",
                    "Tendrás que responder las " + preguntas.size() + " preguntas seguidas. "
                            + "Se desbloquean todas y, " + alFallar, "Empezar examen")) {
                return;
            }
            motor.iniciarExamen();
        }
        Progreso.guardar(clave, motor);
        mostrarTurno(true);
        tarjeta.alfa = 0;
        Anim.ejecutar(420, 0, t -> {
            double e = Estilo.easeOut(t);
            tarjeta.alfa = (float) e;
            tarjeta.dy = 20 * (1 - e);
            centro.repaint();
        }, () -> {
            tarjeta.dy = 0;
            centro.repaint();
        });
    }

    private void salir() {
        app.mostrar(new PantallaTemas(app, materia), -1);
    }

    // ------------------------------------------------------------------ flujo

    private void mostrarTurno(boolean animar) {
        turno = motor.actual();
        actualizarProgreso(animar);
        if (turno == null) return;
        pregunta = preguntas.get(turno.idx());
        modo = Modo.PREGUNTA;
        resultado = null;
        tarjeta.prepararPregunta();
        centro.revalidate();
        scroll.getVerticalScrollBar().setValue(0);
        SwingUtilities.invokeLater(() -> tarjeta.campo.area.requestFocusInWindow());
    }

    private void comprobar(boolean noSe) {
        if (modo != Modo.PREGUNTA || ocupado || turno == null) return;
        String texto = tarjeta.campo.area.getText().trim();
        if (!noSe && texto.isEmpty()) {
            tarjeta.sacudirCampo();
            return;
        }
        resultado = noSe ? Evaluador.Resultado.NO_SE : Evaluador.evaluar(texto, pregunta.respuesta(), pregunta.pregunta());
        veredicto = resultado.correcta();
        consecuenciaOk = motor.consecuencia(true);
        consecuenciaMal = motor.consecuencia(false);
        modo = Modo.RESPUESTA;
        tarjeta.prepararRespuesta(noSe ? "" : texto);
        centro.revalidate();
        tarjeta.requestFocusInWindow();
        tarjeta.mostrarVeredicto(true);
        SwingUtilities.invokeLater(() -> {
            centro.validate();
            Rectangle r = SwingUtilities.convertRectangle(tarjeta, tarjeta.feedback.getBounds(), centro);
            r.height += 20;
            centro.scrollRectToVisible(r);
        });
    }

    private void cambiarVeredicto() {
        if (modo != Modo.RESPUESTA || ocupado) return;
        veredicto = !veredicto;
        tarjeta.mostrarVeredicto(false);
    }

    private void continuar() {
        if (modo != Modo.RESPUESTA || ocupado) return;
        ocupado = true;
        motor.responder(veredicto);
        Progreso.guardar(clave, motor);
        int base = centro.xBase();
        Anim.ejecutar(190, 0, t -> {
            double e = Estilo.easeIn(t);
            tarjeta.setLocation(base - (int) Math.round(70 * e), tarjeta.getY());
            tarjeta.alfa = (float) (1 - t);
            centro.repaint();
        }, () -> {
            mostrarTurno(true);
            if (turno == null) {
                ocupado = false;
                finalizar();
                return;
            }
            centro.validate();
            Anim.ejecutar(420, 0, t -> {
                double e = Estilo.easeOut(t);
                tarjeta.setLocation(centro.xBase() + (int) Math.round(80 * (1 - e)), tarjeta.getY());
                tarjeta.alfa = (float) Estilo.limitar(t * 1.5);
                centro.repaint();
            }, () -> {
                tarjeta.setLocation(centro.xBase(), tarjeta.getY());
                tarjeta.alfa = 1;
                ocupado = false;
                centro.repaint();
            });
        });
    }

    private void finalizar() {
        Progreso.guardar(clave, motor);
        Progreso.marcarCompletado(clave);
        app.mostrar(new PantallaFin(app, materia, tema, motor), 1);
    }

    private void actualizarProgreso(boolean animar) {
        int total = motor.total;
        if (motor.enExamen()) {
            barra.setColores(Estilo.AVISO, Estilo.EXITO);
            barra.setValor(total == 0 ? 0 : motor.examenIndice() / (double) total, animar);
            textoProgreso.setTexto("Examen final  ·  " + motor.examenIndice() + " de " + total + " seguidas");
        } else {
            int dom = motor.dominadas();
            barra.setColores(materia.acento1, materia.acento2);
            barra.setValor(total == 0 ? 0 : dom / (double) total, animar);
            int pct = total == 0 ? 0 : (int) Math.round(dom * 100.0 / total);
            textoProgreso.setTexto(dom + " de " + total + " dominadas  ·  " + pct + "%");
        }
        lateral.actualizar(animar);
        boolean todoDominado = motor.dominadas() >= motor.total;
        examenBoton.setText(motor.enExamen() ? "Volver a practicar" : "Examen final");
        examenBoton.setVisible(!motor.terminado() && !(motor.enExamen() && todoDominado));
        revalidate();
    }

    // ------------------------------------------------------------------ distribución

    @Override
    public void doLayout() {
        Dimension dim = UI.diseno(this);
        int w = dim.width, h = dim.height;
        int margen = w >= 1200 ? 40 : (w < 700 ? 16 : 28);
        boolean conLateral = w >= 1060;
        Dimension dv = UI.prefDiseno(volver);
        UI.poner(volver, margen - 10, 20, dv.width, 40);
        int tx = margen - 10 + dv.width + 14;
        int derecha = w - margen;
        if (examenBoton.isVisible()) {
            examenBoton.setText(examenBoton.getText().startsWith("Volver")
                    ? (w < 820 ? "Practicar" : "Volver a practicar")
                    : (w < 820 ? "Examen" : "Examen final"));
            Dimension de = UI.prefDiseno(examenBoton);
            UI.poner(examenBoton, derecha - de.width, 20, de.width, 40);
            derecha -= de.width + 16;
        }
        // En ventanas estrechas el texto de progreso estorba: lo dice la barra.
        boolean conProgreso = derecha - tx > 420;
        textoProgreso.setVisible(conProgreso);
        int anchoProgreso = 280;
        if (conProgreso) UI.poner(textoProgreso, derecha - anchoProgreso, 32, anchoProgreso, 20);
        int anchoTitulo = Math.max(60, derecha - (conProgreso ? anchoProgreso + 20 : 8) - tx);
        UI.poner(titulo, tx, 16, anchoTitulo, titulo.alturaPara(Estilo.px(anchoTitulo)) / Estilo.escala());
        UI.poner(subtitulo, tx, 42, anchoTitulo, subtitulo.alturaPara(Estilo.px(anchoTitulo)) / Estilo.escala());
        UI.poner(barra, margen, 76, w - 2 * margen, 8);

        int top = 100;
        int anchoIzq = w - 2 * margen;
        if (conLateral) {
            anchoIzq -= ANCHO_LATERAL + 24;
            UI.poner(lateral, w - margen - ANCHO_LATERAL, top + 12, ANCHO_LATERAL, h - top - 36);
        }
        lateral.setVisible(conLateral);
        // El área izquierda se extiende 16px por lado para dejar espacio a la sombra y la sacudida.
        UI.poner(scroll, margen - 16, top, anchoIzq + 32, Math.max(80, h - top));
    }

    /** Vista desplazable que centra la tarjeta. */
    private final class Centro extends JPanel implements Scrollable {
        Centro() {
            super(null);
            setOpaque(false);
        }

        /** Unidades de diseño. */
        private int anchoTarjeta(int w) {
            return Math.max(300, Math.min(ANCHO_MAX_TARJETA, w - 32));
        }

        /** Posición izquierda de la tarjeta, en píxeles reales (la usan las animaciones). */
        int xBase() {
            int w = UI.diseno(this).width;
            return Estilo.px((w - anchoTarjeta(w)) / 2.0);
        }

        @Override
        public void doLayout() {
            int w = UI.diseno(this).width;
            int cw = anchoTarjeta(w);
            UI.poner(tarjeta, (w - cw) / 2.0, 12, cw, tarjeta.alturaPara(cw));
        }

        @Override
        public Dimension getPreferredSize() {
            int real = getParent() != null && getParent().getWidth() > 0 ? getParent().getWidth() : 800;
            int w = (int) Math.round(real / Estilo.escala());
            return new Dimension(real, Estilo.px(tarjeta.alturaPara(anchoTarjeta(w)) + 48));
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (tarjeta.alfa <= 0.01) return;
            Graphics2D g2 = (Graphics2D) g.create();
            Estilo.suavizar(g2);
            Estilo.alfa(g2, tarjeta.alfa);
            Estilo.unidadesDeDiseno(g2, getWidth(), getHeight());
            Rectangle b = tarjeta.getBounds();
            double e = Estilo.escala();
            Estilo.sombra(g2, b.x / e, (b.y + tarjeta.dy) / e, b.width / e, b.height / e, 28, 14, 150);
            g2.dispose();
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(24, r.height - 60); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    // ------------------------------------------------------------------ tarjeta de pregunta

    private final class TarjetaPregunta extends UI.Capa {
        static final int PAD = 36;

        /** Margen interior de la tarjeta: se estrecha en ventanas pequeñas. */
        private int pad(int w) {
            return w < 520 ? 20 : (w < 680 ? 28 : PAD);
        }

        /** Alto del cuadro de respuesta según el alto disponible (unidades de diseño). */
        private int altoCampo() {
            int disponible = (int) Math.round(scroll.getHeight() / Estilo.escala());
            if (disponible <= 0) return 156;
            return Math.max(84, Math.min(156, disponible - 260));
        }
        final UI.Texto textoPregunta = new UI.Texto("", Estilo.SEMI, 23f, Estilo.TEXTO);
        final UI.Campo campo = new UI.Campo("Escribe tu respuesta…");
        final UI.Boton botonNoSe = new UI.Boton("No lo sé", UI.TipoBoton.SECUNDARIO);
        final UI.Boton botonComprobar = new UI.Boton("Comprobar", UI.TipoBoton.PRIMARIO);
        final UI.Texto ayuda = new UI.Texto("Shift + Enter para salto de línea  ·  Esc para salir",
                Estilo.NORMAL, 12.5f, Estilo.TEXTO_TENUE);
        final PanelRespuesta feedback = new PanelRespuesta();
        final ValorAnimado destello = new ValorAnimado(0, this::repaint);
        final ValorAnimado insignia = new ValorAnimado(1, this::repaint);
        Color colorDestello = Estilo.EXITO;
        private final List<double[]> particulas = new ArrayList<>();
        private Anim.Tarea timerParticulas;
        private int xCampo;

        TarjetaPregunta() {
            textoPregunta.setInterlineado(1.28f);
            campo.setAcento(materia.acento1);
            botonComprobar.setColores(materia.acento1, materia.acento2);
            botonComprobar.setAtajo("Enter");
            botonComprobar.addActionListener(e -> comprobar(false));
            botonNoSe.addActionListener(e -> comprobar(true));
            add(textoPregunta);
            add(campo);
            add(botonNoSe);
            add(botonComprobar);
            add(ayuda);
            add(feedback);
            feedback.setVisible(false);
            setFocusable(true);
        }

        void prepararPregunta() {
            textoPregunta.setTexto(pregunta.pregunta());
            campo.area.setText("");
            campo.area.setEditable(true);
            campo.setVisible(true);
            botonNoSe.setVisible(true);
            botonComprobar.setVisible(true);
            ayuda.setVisible(true);
            feedback.setVisible(false);
            destello.fijar(0);
            insignia.fijar(0);
            insignia.ir(1, 500);
            revalidate();
            repaint();
        }

        void prepararRespuesta(String textoUsuario) {
            campo.area.setEditable(false);
            campo.setVisible(false);
            botonNoSe.setVisible(false);
            botonComprobar.setVisible(false);
            ayuda.setVisible(false);
            feedback.cargar(textoUsuario);
            feedback.setVisible(true);
            feedback.alfa = 0;
            feedback.dy = 16;
            Anim.ejecutar(380, 0, t -> {
                double e = Estilo.easeOut(t);
                feedback.alfa = (float) e;
                feedback.dy = 16 * (1 - e);
                repaint();
            }, null);
            revalidate();
        }

        void mostrarVeredicto(boolean inicial) {
            colorDestello = veredicto ? Estilo.EXITO : Estilo.ERROR;
            destello.fijar(1);
            destello.ir(0.22, 900);
            feedback.actualizarVeredicto();
            if (veredicto) estallido();
            else if (inicial) sacudir(this, centro.xBase(), 12);
            revalidate();
            repaint();
        }

        void sacudirCampo() {
            sacudir(campo, xCampo, 8);
            campo.area.requestFocusInWindow();
        }

        private void sacudir(Component c, int base, int amplitud) {
            Anim.ejecutar(430, 0, t -> {
                double a = amplitud * (1 - t);
                c.setLocation(base + (int) Math.round(Math.sin(t * Math.PI * 6) * a), c.getY());
                centro.repaint();
            }, () -> {
                c.setLocation(base, c.getY());
                centro.repaint();
            });
        }

        private void estallido() {
            particulas.clear();
            Point o = SwingUtilities.convertPoint(feedback, Estilo.px(26), Estilo.px(26), this);
            o = Estilo.aDiseno(o);
            Color[] colores = {Estilo.EXITO, materia.acento1, materia.acento2, Color.WHITE};
            for (int i = 0; i < 26; i++) {
                double ang = Math.random() * Math.PI * 2;
                double vel = 2.2 + Math.random() * 4.2;
                particulas.add(new double[]{o.x, o.y, Math.cos(ang) * vel, Math.sin(ang) * vel,
                        2 + Math.random() * 3, i % colores.length, 1});
            }
            if (timerParticulas != null) timerParticulas.stop();
            double[] anterior = {0};
            timerParticulas = Anim.ejecutar(800, 0, t -> {
                double k = (t - anterior[0]) * 800 / (1000 / 60.0);
                anterior[0] = t;
                double friccion = Math.pow(0.93, k);
                for (double[] p : particulas) {
                    p[0] += p[2] * k;
                    p[1] += p[3] * k;
                    p[2] *= friccion;
                    p[3] = p[3] * friccion + 0.12 * k;
                    p[6] = 1 - t;
                }
                repaint();
            }, () -> {
                particulas.clear();
                repaint();
            });
        }

        /** Recibe y devuelve unidades de diseño. */
        int alturaPara(int w) {
            return disponer(w, false);
        }

        @Override
        public void doLayout() {
            disponer(UI.diseno(this).width, true);
        }

        private int disponer(int w, boolean aplicar) {
            int pad = pad(w);
            int iw = w - 2 * pad;
            float tamPregunta = w < 560 ? 19f : (w < 700 ? 21f : 23f);
            if (textoPregunta.getTam() != tamPregunta) {
                textoPregunta.setFuente(Estilo.SEMI, tamPregunta);
            }
            int y = pad + 28 + 20;
            int hp = (int) Math.round(textoPregunta.alturaPara(Estilo.px(iw)) / Estilo.escala());
            if (aplicar) UI.poner(textoPregunta, pad, y, iw, hp);
            y += hp + 26;
            if (modo == Modo.PREGUNTA) {
                int hc = altoCampo();
                if (aplicar) {
                    xCampo = Estilo.px(pad - 4);
                    UI.poner(campo, pad - 4, y, iw + 8, hc);
                }
                y += hc + 18;
                if (aplicar) {
                    Dimension dc = UI.prefDiseno(botonComprobar), dn = UI.prefDiseno(botonNoSe);
                    UI.poner(botonComprobar, pad + iw - dc.width, y, dc.width, 46);
                    UI.poner(botonNoSe, pad + iw - dc.width - 12 - dn.width, y, dn.width, 46);
                    int anchoAyuda = Math.max(0, iw - dc.width - dn.width - 30);
                    UI.poner(ayuda, pad, y + 14, anchoAyuda, 18);
                    ayuda.setVisible(anchoAyuda > 200);
                }
                y += 46;
            } else {
                int hf = feedback.alturaPara(iw);
                if (aplicar) UI.poner(feedback, pad, y, iw, hf);
                y += hf;
            }
            return y + pad;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Estilo.suavizar(g2);
            Dimension dim = Estilo.unidadesDeDiseno(g2, getWidth(), getHeight());
            int w = dim.width, h = dim.height;
            RoundRectangle2D forma = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 28, 28);
            Cache.dibujar(g2, "pregunta:" + materia.clave, 0, 0, w, h, gc -> {
                RoundRectangle2D f = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 28, 28);
                gc.setPaint(new GradientPaint(0, 0, new Color(0x19203A), 0, h, new Color(0x121729)));
                gc.fill(f);
                gc.clip(f);
                gc.setPaint(new RadialGradientPaint(new Point2D.Double(w, 0), (float) Math.max(200, w * 0.6),
                        new float[]{0f, 1f}, new Color[]{Estilo.alfa(materia.acento2, 26), Estilo.alfa(materia.acento2, 0)}));
                gc.fill(f);
                gc.setPaint(new GradientPaint(0, 0, materia.acento1, w, 0, materia.acento2));
                gc.fillRect(0, 0, w, 3);
            });
            double d = destello.get();
            if (d > 0.001) {
                g2.setPaint(new GradientPaint(0, 0, Estilo.alfa(colorDestello, (int) (70 * d)), 0, h, Estilo.alfa(colorDestello, (int) (12 * d))));
                g2.fill(forma);
            }
            g2.setStroke(new BasicStroke(1.2f));
            g2.setColor(d > 0.001 ? Estilo.mezclar(Estilo.BORDE, Estilo.alfa(colorDestello, 200), Math.min(1, d * 1.6)) : Estilo.BORDE);
            g2.draw(forma);

            if (turno != null) pintarCabecera(g2, w);
            g2.dispose();
        }

        private void pintarCabecera(Graphics2D g2, int w) {
            String etiqueta;
            Color color;
            switch (turno.tipo()) {
                case NUEVA -> {
                    etiqueta = "NUEVA";
                    color = materia.acento1;
                }
                case REPASO -> {
                    etiqueta = "REPASO";
                    color = Estilo.INFO;
                }
                case FINAL -> {
                    etiqueta = "EXAMEN FINAL";
                    color = Estilo.EXITO;
                }
                case REPARACION -> {
                    etiqueta = "REPETIR " + motor.reparacionRestante() + "×";
                    color = Estilo.AVISO;
                }
                default -> {
                    etiqueta = "REINTENTO";
                    color = Estilo.AVISO;
                }
            }
            double ap = Estilo.easeOutBack(insignia.get());
            g2.setFont(Estilo.etiqueta(11f));
            FontMetrics fm = g2.getFontMetrics();
            double bw = fm.stringWidth(etiqueta) + 36, bh = 28;
            double bx = pad(w), by = pad(w);
            Graphics2D gb = (Graphics2D) g2.create();
            gb.translate(bx + bw / 2, by + bh / 2);
            gb.scale(Math.max(0.01, ap), Math.max(0.01, ap));
            gb.translate(-bw / 2, -bh / 2);
            RoundRectangle2D pastilla = new RoundRectangle2D.Double(0, 0, bw, bh, bh, bh);
            gb.setColor(Estilo.alfa(color, 32));
            gb.fill(pastilla);
            gb.setColor(Estilo.alfa(color, 110));
            gb.draw(pastilla);
            gb.fill(new Ellipse2D.Double(12, bh / 2 - 3.5, 7, 7));
            gb.setColor(Estilo.mezclar(color, Color.WHITE, 0.25));
            gb.drawString(etiqueta, 25f, (float) ((bh - fm.getHeight()) / 2 + fm.getAscent()));
            gb.dispose();

            g2.setFont(Estilo.fuente(Estilo.SEMI, 13.5f));
            fm = g2.getFontMetrics();
            String numero = "Pregunta " + pregunta.id();
            g2.setColor(Estilo.TEXTO_SUAVE);
            float yTexto = (float) (by + (bh - fm.getHeight()) / 2 + fm.getAscent());
            g2.drawString(numero, (float) (bx + bw + 14), yTexto);

            double izquierda = bx + bw + 14 + fm.stringWidth(numero) + 18;
            // Los puntos de racha solo caben en tarjetas anchas.
            if (turno.tipo() != Motor.Tipo.FINAL && w > 620) izquierda = pintarSeguidas(g2, izquierda, by + bh / 2);

            String derecha = switch (turno.tipo()) {
                case FINAL -> (motor.examenIndice() + 1) + " de " + preguntas.size() + " del examen";
                case REPARACION -> "examen en pausa  ·  " + motor.examenIndice() + " de " + preguntas.size();
                default -> tema == null ? pregunta.tema() : (turno.idx() + 1) + " de " + preguntas.size();
            };
            g2.setFont(Estilo.fuente(Estilo.NORMAL, 13f));
            fm = g2.getFontMetrics();
            double libre = w - pad(w) - izquierda;
            if (libre > 60) {
                derecha = Estilo.recortar(fm, derecha, libre);
                g2.setColor(turno.tipo() == Motor.Tipo.FINAL ? Estilo.mezclar(Estilo.EXITO, Estilo.TEXTO, 0.4) : Estilo.TEXTO_TENUE);
                g2.drawString(derecha, (float) (w - pad(w) - fm.stringWidth(derecha)), yTexto);
            }
        }

        /** Aciertos seguidos de esta pregunta: al completarlos sale del repaso. */
        private double pintarSeguidas(Graphics2D g2, double x, double cy) {
            int n = motor.seguidas(turno.idx());
            double d = 9, sep = 6;
            for (int i = 0; i < motor.retiro; i++) {
                Ellipse2D punto = new Ellipse2D.Double(x + i * (d + sep), cy - d / 2, d, d);
                g2.setColor(i < n ? Estilo.EXITO : new Color(255, 255, 255, 30));
                g2.fill(punto);
            }
            double fin = x + motor.retiro * (d + sep) - sep + 8;
            g2.setFont(Estilo.fuente(Estilo.NORMAL, 12.5f));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Estilo.TEXTO_TENUE);
            String txt = n + "/" + motor.retiro + " seguidas";
            g2.drawString(txt, (float) fin, (float) (cy - fm.getHeight() / 2f + fm.getAscent()));
            return fin + fm.stringWidth(txt) + 20;
        }

        @Override
        protected void paintChildren(Graphics g) {
            super.paintChildren(g);
            if (particulas.isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            Estilo.suavizar(g2);
            Estilo.unidadesDeDiseno(g2, getWidth(), getHeight());
            Color[] colores = {Estilo.EXITO, materia.acento1, materia.acento2, Color.WHITE};
            for (double[] p : particulas) {
                g2.setColor(Estilo.alfa(colores[(int) p[5]], (int) (230 * p[6])));
                g2.fill(new Ellipse2D.Double(p[0] - p[4] / 2, p[1] + feedback.dy - p[4] / 2, p[4], p[4]));
            }
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ panel de respuesta

    private final class PanelRespuesta extends UI.Capa {
        final UI.Texto textoUsuario = new UI.Texto("", Estilo.NORMAL, 15.5f, Estilo.TEXTO_SUAVE);
        final UI.Texto textoCorrecto = new UI.Texto("", Estilo.NORMAL, 18f, Estilo.TEXTO);
        final UI.Texto textoInfo = new UI.Texto("", Estilo.NORMAL, 13.5f, Estilo.TEXTO_SUAVE);
        final UI.Boton botonCambiar = new UI.Boton("", UI.TipoBoton.SECUNDARIO);
        final UI.Boton botonContinuar = new UI.Boton("Continuar", UI.TipoBoton.PRIMARIO);
        final ValorAnimado trazo = new ValorAnimado(0, this::repaint);
        final ValorAnimado color = new ValorAnimado(0, this::repaint);
        private boolean sinRespuesta;
        private int yEtiquetaUsuario, yCaja, altoCaja, yInfo;

        PanelRespuesta() {
            textoCorrecto.setInterlineado(1.38f);
            botonCambiar.setAtajo("M");
            botonCambiar.addActionListener(e -> cambiarVeredicto());
            botonContinuar.setAtajo("Enter");
            botonContinuar.setColores(materia.acento1, materia.acento2);
            botonContinuar.addActionListener(e -> continuar());
            add(textoUsuario);
            add(textoCorrecto);
            add(textoInfo);
            add(botonCambiar);
            add(botonContinuar);
        }

        void cargar(String respuestaUsuario) {
            sinRespuesta = respuestaUsuario.isEmpty();
            textoUsuario.setTexto(sinRespuesta ? "No respondiste esta pregunta." : respuestaUsuario);
            textoCorrecto.setTexto(pregunta.respuesta());
            Set<String> acertadas = resultado.acertadas();
            textoCorrecto.setResaltado(acertadas, Estilo.EXITO);
            color.fijar(veredicto ? 1 : 0);
        }

        void actualizarVeredicto() {
            trazo.fijar(0);
            trazo.ir(1, 520);
            color.ir(veredicto ? 1 : 0, 300);
            botonCambiar.setText(veredicto ? "Marcar como incorrecta" : "La sabía: marcar correcta");
            botonCambiar.setTipo(veredicto ? UI.TipoBoton.SECUNDARIO : UI.TipoBoton.EXITO);
            textoInfo.setTexto(veredicto ? consecuenciaOk : consecuenciaMal);
            revalidate();
            repaint();
        }

        /** Unidades de diseño. */
        int alturaPara(int w) {
            return disponer(w, false);
        }

        @Override
        public void doLayout() {
            disponer(UI.diseno(this).width, true);
        }

        private int disponer(int w, boolean aplicar) {
            int y = 56 + 24;
            if (aplicar) yEtiquetaUsuario = y;
            y += 24;
            int hu = (int) Math.round(textoUsuario.alturaPara(Estilo.px(w)) / Estilo.escala());
            if (aplicar) UI.poner(textoUsuario, 0, y, w, hu);
            y += hu + 24;
            int yc = y;
            int interior = w - 50;
            int y2 = yc + 20 + 26;
            int hc = (int) Math.round(textoCorrecto.alturaPara(Estilo.px(interior)) / Estilo.escala());
            if (aplicar) UI.poner(textoCorrecto, 28, y2, interior, hc);
            y2 += hc + 22;
            if (aplicar) {
                yCaja = yc;
                altoCaja = y2 - yc;
            }
            y = y2 + 20;
            int hi = (int) Math.round(textoInfo.alturaPara(Estilo.px(w - 30)) / Estilo.escala());
            if (aplicar) {
                yInfo = y;
                UI.poner(textoInfo, 30, y, w - 30, hi);
            }
            y += hi + 26;
            if (aplicar) {
                Dimension dc = UI.prefDiseno(botonContinuar), db = UI.prefDiseno(botonCambiar);
                UI.poner(botonContinuar, w - dc.width, y, dc.width, 46);
                UI.poner(botonCambiar, w - dc.width - 12 - db.width, y, db.width, 46);
            }
            return y + 46;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Estilo.suavizar(g2);
            int w = Estilo.unidadesDeDiseno(g2, getWidth(), getHeight()).width;
            Color c = Estilo.mezclar(Estilo.ERROR, Estilo.EXITO, color.get());

            // Veredicto
            Ellipse2D circulo = new Ellipse2D.Double(1, 1, 50, 50);
            g2.setColor(Estilo.alfa(c, 38));
            g2.fill(circulo);
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(Estilo.alfa(c, 170));
            g2.draw(circulo);
            g2.setStroke(new BasicStroke(3.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(Estilo.mezclar(c, Color.WHITE, 0.2));
            if (veredicto) Estilo.palomita(g2, 26, 27, 34, trazo.get());
            else Estilo.cruz(g2, 26, 26, 34, trazo.get());

            String tituloVeredicto = veredicto ? "¡Correcto!" : (sinRespuesta ? "Aquí está la respuesta" : "Incorrecto");
            g2.setFont(Estilo.fuente(Estilo.NEGRITA, 23f));
            g2.setColor(Estilo.mezclar(c, Color.WHITE, 0.35));
            g2.drawString(tituloVeredicto, 68, 24);
            g2.setFont(Estilo.fuente(Estilo.NORMAL, 13.5f));
            g2.setColor(Estilo.TEXTO_SUAVE);
            String detalle;
            if (resultado.sinRespuesta()) detalle = "Léela con calma: volverá a salir pronto.";
            else detalle = "Coincidencia con la respuesta oficial: " + Math.round(resultado.puntaje() * 100) + "%"
                    + (veredicto != resultado.correcta() ? "   ·   marcada manualmente" : "");
            g2.drawString(detalle, 68, 46);

            // Tu respuesta
            g2.setFont(Estilo.etiqueta(11f));
            g2.setColor(Estilo.TEXTO_TENUE);
            g2.drawString("TU RESPUESTA", 0, yEtiquetaUsuario + 12);

            // Respuesta correcta
            RoundRectangle2D caja = new RoundRectangle2D.Double(0.5, yCaja, w - 1, altoCaja, 18, 18);
            g2.setPaint(new GradientPaint(0, yCaja, Estilo.alfa(materia.acento1, 26), w, yCaja + altoCaja, Estilo.alfa(materia.acento2, 18)));
            g2.fill(caja);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(Estilo.alfa(materia.acento1, 70));
            g2.draw(caja);
            g2.setPaint(new GradientPaint(0, yCaja, materia.acento1, 0, yCaja + altoCaja, materia.acento2));
            g2.fill(new RoundRectangle2D.Double(12, yCaja + 16, 4, altoCaja - 32, 4, 4));
            g2.setFont(Estilo.etiqueta(11f));
            g2.setColor(Estilo.mezclar(materia.acento1, Color.WHITE, 0.3));
            g2.drawString("RESPUESTA CORRECTA", 28, yCaja + 32);

            // Información de lo que sigue
            Color ci = textoInfo.getTexto().contains("preguntas activas") ? Estilo.AVISO : Estilo.TEXTO_SUAVE;
            Ellipse2D icono = new Ellipse2D.Double(0, yInfo + 1, 19, 19);
            g2.setColor(Estilo.alfa(ci, 40));
            g2.fill(icono);
            g2.setColor(ci);
            g2.setFont(Estilo.fuente(Estilo.NEGRITA, 12f));
            Estilo.textoCentrado(g2, "i", 9.5, yInfo + 10.5);
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ panel lateral

    private final class PanelLateral extends JComponent {
        private final ValorAnimado anillo = new ValorAnimado(0, this::repaint);
        private final ValorAnimado fallos = new ValorAnimado(0, this::repaint);
        private final ValorAnimado entrada = new ValorAnimado(0, this::repaint);

        PanelLateral() {
            setOpaque(false);
        }

        void aparecer() {
            entrada.fijar(0);
            Anim.despues(260, () -> entrada.ir(1, 600));
        }

        void actualizar(boolean animar) {
            double f = motor.total == 0 ? 0 : motor.dominadas() / (double) motor.total;
            if (animar) {
                anillo.ir(f, 900);
                fallos.ir(motor.activas(), 450);
            } else {
                anillo.fijar(f);
                fallos.fijar(motor.activas());
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            double ap = entrada.get();
            if (ap <= 0.001) return;
            Graphics2D g2 = (Graphics2D) g.create();
            Estilo.suavizar(g2);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Dimension dim = Estilo.unidadesDeDiseno(g2, getWidth(), getHeight());
            int w = dim.width, h = dim.height;
            if (ap >= 1 && !anillo.animando() && !fallos.animando()) {
                Cache.dibujar(g2, firma(), 0, 0, w, h, gc -> pintarContenido(gc, w, h));
            } else {
                Estilo.alfa(g2, (float) ap);
                g2.translate((1 - Estilo.easeOut(ap)) * 30, 0);
                pintarContenido(g2, w, h);
            }
            g2.dispose();
        }

        private String firma() {
            StringBuilder sb = new StringBuilder("lateral:").append(materia.clave).append(':').append(tema)
                    .append(':').append(motor.dominadas()).append(':').append(motor.desbloqueadas())
                    .append(':').append(motor.aciertos).append(':').append(motor.errores)
                    .append(':').append(motor.racha).append(':').append(motor.mejorRacha)
                    .append(':').append(motor.activas()).append(':').append(motor.bloqueado())
                    .append(':').append(motor.enExamen()).append(':').append(motor.examenIndice())
                    .append(':').append(motor.reparacionRestante())
                    .append(':').append(anillo.get()).append(':').append(fallos.get());
            for (Motor.Turno t : motor.proximas(8)) sb.append(':').append(t.idx()).append(t.tipo().ordinal());
            return sb.toString();
        }

        private void pintarContenido(Graphics2D g2, int w, int h) {
            RoundRectangle2D forma = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 24, 24);
            g2.setPaint(new GradientPaint(0, 0, new Color(0x161C33), 0, h, new Color(0x10152A)));
            g2.fill(forma);
            g2.setColor(Estilo.BORDE);
            g2.draw(forma);

            int pad = 24;
            int ancho = w - 2 * pad;
            int y = pad;
            etiqueta(g2, "TU PROGRESO", pad, y);
            y += 22;

            // Anillo
            double r = 44, cx = pad + r, cy = y + r + 4;
            g2.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(255, 255, 255, 18));
            g2.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
            double v = anillo.get();
            if (v > 0.001) {
                g2.setPaint(new GradientPaint((float) (cx - r), (float) (cy - r), materia.acento1, (float) (cx + r), (float) (cy + r), materia.acento2));
                g2.draw(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 90, -360 * v, Arc2D.OPEN));
            }
            g2.setFont(Estilo.fuente(Estilo.NEGRITA, 21f));
            g2.setColor(Estilo.TEXTO);
            Estilo.textoCentrado(g2, Math.round(v * 100) + "%", cx, cy);

            double tx = cx + r + 22;
            g2.setFont(Estilo.fuente(Estilo.NEGRITA, 22f));
            g2.drawString(motor.dominadas() + " / " + motor.total, (float) tx, (float) (cy - 8));
            g2.setFont(Estilo.fuente(Estilo.NORMAL, 13f));
            g2.setColor(Estilo.TEXTO_SUAVE);
            g2.drawString("dominadas", (float) tx, (float) (cy + 12));
            g2.setColor(Estilo.TEXTO_TENUE);
            g2.drawString(motor.desbloqueadas() + " desbloqueadas", (float) tx, (float) (cy + 32));
            g2.drawString(motor.retiro + " aciertos seguidos", (float) tx, (float) (cy + 50));
            y += (int) (2 * r) + 30;

            // Mosaicos de estadísticas
            int tw = (ancho - 10) / 2, th = 60;
            mosaico(g2, pad, y, tw, th, String.valueOf(motor.aciertos), "Aciertos", Estilo.EXITO);
            mosaico(g2, pad + tw + 10, y, tw, th, String.valueOf(motor.errores), "Errores", Estilo.ERROR);
            y += th + 10;
            mosaico(g2, pad, y, tw, th, String.valueOf(motor.racha), "Racha actual", materia.acento1);
            mosaico(g2, pad + tw + 10, y, tw, th, String.valueOf(motor.mejorRacha), "Mejor racha", materia.acento2);
            y += th + 28;

            // Falladas activas
            etiqueta(g2, "PREGUNTAS ACTIVAS", pad, y);
            g2.setFont(Estilo.fuente(Estilo.SEMI, 13f));
            String cuenta = motor.activas() + " / " + Motor.MAX_ACTIVAS;
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(motor.activas() >= Motor.MAX_ACTIVAS ? Estilo.ERROR : Estilo.TEXTO_SUAVE);
            g2.drawString(cuenta, pad + ancho - fm.stringWidth(cuenta), y + 11);
            y += 24;
            double f = fallos.get();
            double paso = ancho / (double) Motor.MAX_ACTIVAS;
            double d = Math.min(30, paso - 12);
            for (int i = 0; i < Motor.MAX_ACTIVAS; i++) {
                double px = pad + i * paso + (paso - d) / 2;
                Ellipse2D base = new Ellipse2D.Double(px, y, d, d);
                g2.setColor(new Color(255, 255, 255, 12));
                g2.fill(base);
                g2.setStroke(new BasicStroke(1.2f));
                g2.setColor(new Color(255, 255, 255, 34));
                g2.draw(base);
                double lleno = Estilo.limitar(f - i);
                if (lleno > 0.001) {
                    double s = d * Estilo.easeOutBack(lleno);
                    Ellipse2D punto = new Ellipse2D.Double(px + (d - s) / 2, y + (d - s) / 2, s, s);
                    g2.setPaint(new GradientPaint((float) px, (float) y, Estilo.ERROR, (float) (px + d), (float) (y + d), Estilo.mezclar(Estilo.ERROR, Estilo.AVISO, 0.35)));
                    g2.fill(punto);
                }
            }
            y += (int) d + 12;
            g2.setFont(Estilo.fuente(Estilo.NORMAL, 12.5f));
            if (motor.bloqueado()) {
                g2.setColor(Estilo.AVISO);
                y = (int) Estilo.parrafo(g2, "Nuevas bloqueadas: domina una activa para hacer sitio.", pad, y, ancho, 2, 1.25);
            } else {
                g2.setColor(Estilo.TEXTO_TENUE);
                y = (int) Estilo.parrafo(g2, "Con " + Motor.MAX_ACTIVAS + " activas (repaso incluido) se bloquean las nuevas.", pad, y, ancho, 2, 1.25);
            }
            y += 22;

            // Examen final
            if (motor.enExamen()) {
                etiqueta(g2, "EXAMEN FINAL", pad, y);
                y += 24;
                RoundRectangle2D caja = new RoundRectangle2D.Double(pad, y, ancho, 66, 16, 16);
                g2.setColor(Estilo.alfa(Estilo.EXITO, 26));
                g2.fill(caja);
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(Estilo.alfa(Estilo.EXITO, 90));
                g2.draw(caja);
                g2.setFont(Estilo.fuente(Estilo.NEGRITA, 19f));
                g2.setColor(Estilo.TEXTO);
                g2.drawString(motor.examenIndice() + " / " + motor.total, pad + 14, y + 26);
                g2.setFont(Estilo.fuente(Estilo.NORMAL, 12.5f));
                g2.setColor(Estilo.TEXTO_SUAVE);
                g2.drawString("seguidas sin fallar", pad + 14, y + 44);
                UI.Barra.pintar(g2, pad + 14, y + 52, ancho - 28, 6,
                        motor.total == 0 ? 0 : motor.examenIndice() / (double) motor.total, Estilo.AVISO, Estilo.EXITO);
                y += 76;
                g2.setFont(Estilo.fuente(Estilo.NORMAL, 12.5f));
                g2.setColor(Estilo.AVISO);
                String aviso = tema == null
                        ? "Un fallo no reinicia el examen: repites esa pregunta " + motor.retiro
                                + " veces seguidas y continúas aquí."
                        : "Un fallo reinicia el examen y devuelve esa pregunta al repaso.";
                y = (int) Estilo.parrafo(g2, aviso, pad, y, ancho, 3, 1.25);
                y += 18;
            }

            // Próximas
            if (y + 60 < h && !motor.enExamen()) {
                etiqueta(g2, "A CONTINUACIÓN", pad, y);
                y += 24;
                List<Motor.Turno> proximas = motor.proximas(8);
                if (proximas.isEmpty()) {
                    g2.setFont(Estilo.fuente(Estilo.NORMAL, 12.5f));
                    g2.setColor(Estilo.TEXTO_TENUE);
                    Estilo.parrafo(g2, "Se decide según tu respuesta a esta pregunta.", pad, y, ancho, 2, 1.25);
                } else {
                    for (Motor.Turno t : proximas) {
                        if (y + 40 > h - pad + 6) break;
                        fila(g2, pad, y, ancho, t);
                        y += 44;
                    }
                }
            }
        }

        private void etiqueta(Graphics2D g2, String s, int x, int y) {
            g2.setFont(Estilo.etiqueta(11f));
            g2.setColor(Estilo.TEXTO_TENUE);
            g2.drawString(s, x, y + 11);
        }

        private void mosaico(Graphics2D g2, int x, int y, int w, int h, String valor, String texto, Color c) {
            RoundRectangle2D r = new RoundRectangle2D.Double(x, y, w, h, 16, 16);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(new Color(255, 255, 255, 9));
            g2.fill(r);
            g2.setColor(Estilo.BORDE);
            g2.draw(r);
            g2.setColor(c);
            g2.fill(new RoundRectangle2D.Double(x + 12, y + 14, 3, h - 28, 3, 3));
            g2.setFont(Estilo.fuente(Estilo.NEGRITA, 20f));
            g2.setColor(Estilo.TEXTO);
            g2.drawString(valor, x + 24, y + 29);
            g2.setFont(Estilo.fuente(Estilo.NORMAL, 12f));
            g2.setColor(Estilo.TEXTO_SUAVE);
            g2.drawString(texto, x + 24, y + 46);
        }

        private void fila(Graphics2D g2, int x, int y, int w, Motor.Turno t) {
            Pregunta p = preguntas.get(t.idx());
            Color c = switch (t.tipo()) {
                case NUEVA -> materia.acento1;
                case REPASO -> Estilo.INFO;
                default -> Estilo.AVISO;
            };

            RoundRectangle2D r = new RoundRectangle2D.Double(x, y, w, 38, 12, 12);
            g2.setColor(new Color(255, 255, 255, 7));
            g2.fill(r);
            g2.setColor(c);
            g2.fill(new Ellipse2D.Double(x + 12, y + 15, 8, 8));
            g2.setFont(Estilo.fuente(Estilo.SEMI, 12.5f));
            FontMetrics fm = g2.getFontMetrics();
            String num = "#" + p.id();
            g2.setColor(Estilo.TEXTO);
            g2.drawString(num, x + 28, y + 24);
            String tipo = switch (t.tipo()) {
                case NUEVA -> "Nueva";
                case REPASO -> "Repaso";
                case REPARACION -> "Repetir";
                default -> "Reintento";
            };
            g2.setFont(Estilo.fuente(Estilo.NORMAL, 11.5f));
            FontMetrics ft = g2.getFontMetrics();
            g2.setColor(c);
            g2.drawString(tipo, x + w - 12 - ft.stringWidth(tipo), y + 24);
            int inicio = x + 28 + fm.stringWidth(num) + 10;
            int disponible = x + w - 12 - ft.stringWidth(tipo) - 10 - inicio;
            g2.setColor(Estilo.TEXTO_TENUE);
            g2.drawString(Estilo.recortar(ft, p.pregunta(), disponible), inicio, y + 24);
        }
    }
}
