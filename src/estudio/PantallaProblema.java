package estudio;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/**
 * Un problema de cálculo: enunciado, datos, campos de respuesta calificados uno por uno, pistas progresivas
 * (fórmula → sustitución → resultado de cada paso) y solución completa. Sirve para practicar, para el examen
 * de problemas, para ver ejemplos resueltos y para revisar un examen terminado.
 */
final class PantallaProblema extends JPanel implements Pantalla {
    enum Modo { PRACTICA, ADAPTATIVA, AZAR, EXAMEN, RESUELTO, REVISION }

    private static final Color A1 = EstadisticasProblemas.ACENTO1, A2 = EstadisticasProblemas.ACENTO2;

    private final Ventana app;
    private final Modo modo;
    private final UIP.Destino volverA;
    private final ExamenProblemas examen;
    private int indice;
    private String tipoActual;

    private Generadores.Problema problema;
    private Map<String, String> entradas = new HashMap<>();
    private boolean revisado, registrado, solucion;
    private int pistas;

    private final UI.Boton volver;
    private final UI.Texto titulo, subtitulo, etiquetaModo, reloj, etiquetaSemilla;
    private final UIP.CampoNumero campoSemilla = new UIP.CampoNumero("semilla");
    /** Paneles plegables: enunciado y datos, fórmulas, respuestas, pistas y solución. */
    private final UIP.Plegable pEnunciado = new UIP.Plegable("Enunciado y datos", A1, ABIERTOS[0]);
    private final UIP.Plegable pFormulas = new UIP.Plegable("Fórmulas", A1, ABIERTOS[1]);
    private final UIP.Plegable pRespuestas = new UIP.Plegable("Tus respuestas", A1, ABIERTOS[2]);
    private final UIP.Plegable pPasos = new UIP.Plegable("Pistas y solución", A2, ABIERTOS[3]);
    private final List<UIP.Plegable> paneles = List.of(pEnunciado, pFormulas, pRespuestas, pPasos);
    /** Qué paneles quedan abiertos; se recuerda al pasar de un problema a otro. */
    private static final boolean[] ABIERTOS = {true, true, true, false};
    private int yLinea;
    private final UI.Boton bPista, bSolucion, bOtro, bAzar, bRevisar, bAnterior, bSiguiente, bTerminar, bPracticar;
    private final List<UI.Boton> izquierda = new ArrayList<>(), derecha = new ArrayList<>();
    private BloqueRespuestas bloqueRespuestas;
    private BloquePasos bloquePasos;
    private javax.swing.Timer timer;
    private final ValorAnimado entrada = new ValorAnimado(0, this::repaint);

    // ------------------------------------------------------------------ fábricas

    static PantallaProblema practica(Ventana app, String tipo, long semilla, UIP.Destino volverA) {
        return new PantallaProblema(app, Modo.PRACTICA, tipo, semilla, null, 0, volverA);
    }

    static PantallaProblema adaptativa(Ventana app, UIP.Destino volverA) {
        return new PantallaProblema(app, Modo.ADAPTATIVA, EstadisticasProblemas.elegirAdaptativo(null), -1, null, 0, volverA);
    }

    static PantallaProblema azar(Ventana app, UIP.Destino volverA) {
        return new PantallaProblema(app, Modo.AZAR, EstadisticasProblemas.tipoAlAzar(null), -1, null, 0, volverA);
    }

    static PantallaProblema examen(Ventana app, ExamenProblemas ex, UIP.Destino volverA) {
        return new PantallaProblema(app, Modo.EXAMEN, null, 0, ex, 0, volverA);
    }

    static PantallaProblema resuelto(Ventana app, String tipo, long semilla, UIP.Destino volverA) {
        return new PantallaProblema(app, Modo.RESUELTO, tipo, semilla, null, 0, volverA);
    }

    static PantallaProblema revision(Ventana app, ExamenProblemas ex, int indice, UIP.Destino volverA) {
        return new PantallaProblema(app, Modo.REVISION, null, 0, ex, indice, volverA);
    }

    /** {@code semilla} negativa: se elige una nueva al azar. */
    private PantallaProblema(Ventana app, Modo modo, String tipo, long semilla, ExamenProblemas examen, int indice,
                             UIP.Destino volverA) {
        super(null);
        this.app = app;
        this.modo = modo;
        this.examen = examen;
        this.indice = indice;
        this.volverA = volverA;
        setOpaque(false);

        volver = new UI.Boton(modo == Modo.REVISION ? "←   Resultado" : "←   Problemas", UI.TipoBoton.FANTASMA);
        volver.addActionListener(e -> salir());
        titulo = new UI.Texto("", Estilo.NEGRITA, 24f, Estilo.TEXTO);
        subtitulo = new UI.Texto("", Estilo.NORMAL, 14f, Estilo.TEXTO_SUAVE);
        etiquetaModo = new UI.Texto(textoModo(), Estilo.SEMI, 12.5f, Estilo.mezclar(A1, Color.WHITE, 0.3));
        etiquetaModo.setAlineacion(UI.Texto.DERECHA);
        etiquetaModo.setMaxLineas(1);
        reloj = new UI.Texto("", Estilo.SEMI, 16f, Estilo.TEXTO);
        reloj.setAlineacion(UI.Texto.DERECHA);
        etiquetaSemilla = new UI.Texto("Semilla", Estilo.NORMAL, 12.5f, Estilo.TEXTO_TENUE);
        etiquetaSemilla.setAlineacion(UI.Texto.DERECHA);
        campoSemilla.campo.addActionListener(e -> irASemilla());
        campoSemilla.campo.setToolTipText("Escribe una semilla y pulsa Enter para repetir un problema exacto");

        bPista = boton("Pista", UI.TipoBoton.SECUNDARIO, this::pista);
        bSolucion = boton("Ver solución completa", UI.TipoBoton.FANTASMA, this::verSolucion);
        bAzar = boton("Tipo al azar", UI.TipoBoton.SECUNDARIO, () -> otro(true));
        bOtro = boton(switch (modo) {
            case ADAPTATIVA -> "Siguiente adaptativo   →";
            case AZAR -> "Otro al azar   →";
            default -> "Otro de este tipo   →";
        }, UI.TipoBoton.SECUNDARIO, () -> otro(false));
        bRevisar = boton("Revisar", UI.TipoBoton.PRIMARIO, this::revisar);
        bRevisar.setAtajo("Enter");
        bAnterior = boton("←   Anterior", UI.TipoBoton.SECUNDARIO, () -> mover(-1));
        bSiguiente = boton("Siguiente   →", UI.TipoBoton.SECUNDARIO, () -> mover(1));
        bTerminar = boton("Terminar examen", UI.TipoBoton.PRIMARIO, this::terminarExamen);
        bPracticar = boton("Practicar este tipo", UI.TipoBoton.PRIMARIO,
                () -> app.mostrar(practica(app, tipoActual, -1, volverA), 1));
        for (UI.Boton b : List.of(bRevisar, bTerminar, bPracticar)) b.setColores(A1, A2);

        switch (modo) {
            case PRACTICA -> { izquierda.addAll(List.of(bPista, bSolucion)); derecha.addAll(List.of(bAzar, bOtro, bRevisar)); }
            case ADAPTATIVA, AZAR -> { izquierda.addAll(List.of(bPista, bSolucion)); derecha.addAll(List.of(bOtro, bRevisar)); }
            case EXAMEN -> { izquierda.add(bAnterior); derecha.addAll(List.of(bSiguiente, bTerminar)); }
            case RESUELTO -> derecha.add(bPracticar);
            case REVISION -> { izquierda.add(bAnterior); derecha.add(bSiguiente); }
        }

        for (int i = 0; i < paneles.size(); i++) {
            int k = i;
            UIP.Plegable p = paneles.get(i);
            p.setAlCambiar(() -> {
                ABIERTOS[k] = p.abierto;
                revalidate();
                repaint();
            });
        }
        pRespuestas.setExtra(this::resumenRespuestas, this::colorResumen);
        pFormulas.setExtra(() -> Generadores.formulario(tipoActual).size() + " fórmulas", () -> Estilo.TEXTO_TENUE);
        pPasos.setExtra(() -> solucion ? "Solución completa" : pistas == 0 ? "Usa el botón Pista" : "Pistas usadas: " + pistas,
                () -> Estilo.TEXTO_TENUE);
        pPasos.setVisible(modo != Modo.EXAMEN);
        add(volver);
        add(titulo);
        add(subtitulo);
        add(etiquetaModo);
        add(reloj);
        add(etiquetaSemilla);
        add(campoSemilla);
        for (UIP.Plegable p : paneles) add(p);
        for (UI.Boton b : izquierda) add(b);
        for (UI.Boton b : derecha) add(b);

        boolean conSemilla = modo == Modo.PRACTICA || modo == Modo.ADAPTATIVA || modo == Modo.AZAR;
        campoSemilla.setVisible(conSemilla);
        etiquetaSemilla.setVisible(conSemilla);
        reloj.setVisible(modo == Modo.EXAMEN && examen.minutos > 0);

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "salir");
        getActionMap().put("salir", UI.accion(() -> true, this::salir));

        if (examen != null) {
            cargar(examen.problemas.get(indice));
        } else {
            tipoActual = tipo;
            long s = semilla >= 0 ? semilla : EstadisticasProblemas.nuevaSemilla(tipo);
            if (semilla >= 0 && conSemilla) EstadisticasProblemas.guardarSemilla(tipo, semilla);
            cargar(Generadores.generar(tipo, s));
        }
    }

    private UI.Boton boton(String texto, UI.TipoBoton tipo, Runnable r) {
        UI.Boton b = new UI.Boton(texto, tipo);
        b.addActionListener(e -> r.run());
        return b;
    }

    private String textoModo() {
        return switch (modo) {
            case PRACTICA -> "PRÁCTICA";
            case ADAPTATIVA -> "PRÁCTICA ADAPTATIVA";
            case AZAR -> "TIPO AL AZAR";
            case EXAMEN -> "EXAMEN DE PROBLEMAS";
            case RESUELTO -> "EJEMPLO RESUELTO";
            case REVISION -> "REVISIÓN DEL EXAMEN";
        };
    }

    private boolean esPractica() {
        return modo == Modo.PRACTICA || modo == Modo.ADAPTATIVA || modo == Modo.AZAR;
    }

    // ------------------------------------------------------------------ carga

    private void cargar(Generadores.Problema p) {
        problema = p;
        tipoActual = p.tipo();
        revisado = registrado = solucion = false;
        pistas = 0;
        if (examen != null) {
            entradas = examen.entradas.get(indice);
        } else {
            entradas = new HashMap<>();
        }
        if (modo == Modo.RESUELTO) {
            for (Generadores.Respuesta r : p.respuestas()) {
                entradas.put(r.clave(), r.esOpcion() ? r.valorOpcion() : Generadores.fmt(r.valor(), r.decimales()));
            }
        }
        if (modo == Modo.RESUELTO || modo == Modo.REVISION) {
            revisado = solucion = true;
        }

        String sub;
        Generadores.Tipo info = Generadores.tipo(p.tipo());
        if (examen != null) {
            sub = "Problema " + (indice + 1) + " de " + examen.problemas.size() + "  ·  " + info.tema();
            if (modo == Modo.REVISION) sub += "  ·  " + examen.correctas(indice) + " de " + examen.total(indice) + " correctas";
        } else {
            sub = info.tema() + "  ·  dificultad " + p.dificultad() + " de 3";
            if (modo == Modo.RESUELTO) sub += "  ·  semilla " + p.semilla() + (p.tipo().equals("T20") && p.semilla() == 0 ? " (ejemplo de clase)" : "");
        }
        titulo.setTexto(p.tipo() + "  ·  " + p.titulo());
        subtitulo.setTexto(sub);
        campoSemilla.setTexto(String.valueOf(p.semilla()));

        List<UIP.Bloque> enun = new ArrayList<>();
        enun.add(new UIP.BloqueTexto("Enunciado", p.enunciado(), A1));
        if (p.tabla() != null) enun.add(new UIP.BloqueTabla(p.tabla(), A1));
        pEnunciado.setTitulo(p.tipo().equals("T20") && p.semilla() == 0 ? "Enunciado y datos · ejemplo de clase" : "Enunciado y datos");
        pEnunciado.poner(enun);
        pFormulas.poner(List.of(new BloqueFormulas(info)));
        bloqueRespuestas = new BloqueRespuestas();
        pRespuestas.setTitulo(modo == Modo.RESUELTO ? "Respuestas" : modo == Modo.REVISION ? "Tus respuestas en el examen" : "Tus respuestas");
        pRespuestas.poner(List.of(bloqueRespuestas));
        bloquePasos = new BloquePasos();
        pPasos.poner(List.of(bloquePasos));
        if (modo == Modo.RESUELTO || modo == Modo.REVISION) pPasos.setAbierto(true);
        if (revisado) bloqueRespuestas.calificar();
        actualizar();
        SwingUtilities.invokeLater(() -> {
            if (modo != Modo.RESUELTO && modo != Modo.REVISION) bloqueRespuestas.enfocar(0);
        });
    }

    /** Refresca botones y bloques tras un cambio de estado. */
    private void actualizar() {
        int n = problema.pasos().size();
        bPista.setText(pistas == 0 ? "Pista" : "Pista  (" + pistas + ")");
        bPista.setEnabled(!solucion && pistas < 3 * n);
        bSolucion.setEnabled(!solucion);
        boolean listo = revisado && (solucion || bloqueRespuestas.correctas() == problema.respuestas().size());
        bOtro.setTipo(listo ? UI.TipoBoton.PRIMARIO : UI.TipoBoton.SECUNDARIO);
        bOtro.setColores(A1, A2);
        bRevisar.setTipo(listo ? UI.TipoBoton.SECUNDARIO : UI.TipoBoton.PRIMARIO);
        if (examen != null) {
            bAnterior.setEnabled(indice > 0);
            bSiguiente.setEnabled(indice < examen.problemas.size() - 1);
        }
        for (UIP.Plegable p : paneles) p.refrescar();
        revalidate();
        repaint();
    }

    private String resumenRespuestas() {
        if (bloqueRespuestas == null) return null;
        if (!revisado || modo == Modo.RESUELTO) return esPractica() ? "Se acepta coma decimal y ±1.5 %" : null;
        int ok = bloqueRespuestas.correctas(), n = problema.respuestas().size();
        return ok == n ? "¡Todo correcto!  " + ok + " de " + n : ok + " de " + n + " correctas";
    }

    private Color colorResumen() {
        if (!revisado || modo == Modo.RESUELTO) return Estilo.TEXTO_TENUE;
        int ok = bloqueRespuestas.correctas(), n = problema.respuestas().size();
        return ok == n ? Estilo.EXITO : ok == 0 ? Estilo.ERROR : Estilo.AVISO;
    }

    // ------------------------------------------------------------------ acciones

    private void revisar() {
        if (!esPractica()) return;
        revisado = true;
        bloqueRespuestas.calificar();
        registrar();
        actualizar();
    }

    private void registrar() {
        if (registrado || !esPractica()) return;
        registrado = true;
        int ok = bloqueRespuestas.correctas(), n = problema.respuestas().size();
        EstadisticasProblemas.registrar(problema.tipo(), ok == n && !solucion, solucion ? 0 : ok, n, pistas);
    }

    private void pista() {
        if (solucion) return;
        pistas = Math.min(pistas + 1, 3 * problema.pasos().size());
        actualizar();
        mostrarPasos();
    }

    private void verSolucion() {
        if (solucion) return;
        if (!revisado) {
            // Ver la solución sin haber revisado cuenta como intento fallido.
            solucion = true;
            registrar();
        }
        solucion = true;
        revisado = true;
        bloqueRespuestas.calificar();
        actualizar();
        mostrarPasos();
    }

    private void mostrarPasos() {
        pPasos.setAbierto(true);
        SwingUtilities.invokeLater(() -> {
            validate();
            Rectangle r = bloquePasos.rectanguloDe(bloquePasos.ultimoVisible());
            if (r != null) bloquePasos.scrollRectToVisible(r);
        });
    }

    private void otro(boolean alAzar) {
        String tipo = switch (modo) {
            case ADAPTATIVA -> EstadisticasProblemas.elegirAdaptativo(tipoActual);
            case AZAR -> EstadisticasProblemas.tipoAlAzar(tipoActual);
            default -> alAzar ? EstadisticasProblemas.tipoAlAzar(tipoActual) : tipoActual;
        };
        cargarTransicion(Generadores.generar(tipo, EstadisticasProblemas.nuevaSemilla(tipo)));
    }

    private void irASemilla() {
        String t = campoSemilla.texto().replaceAll("[^0-9]", "");
        if (t.isEmpty() || t.length() > 15) {
            campoSemilla.setTexto(String.valueOf(problema.semilla()));
            return;
        }
        long s = Long.parseLong(t);
        EstadisticasProblemas.guardarSemilla(tipoActual, s);
        cargarTransicion(Generadores.generar(tipoActual, s));
    }

    private void mover(int d) {
        int n = examen.problemas.size();
        int nuevo = Math.max(0, Math.min(n - 1, indice + d));
        if (nuevo == indice) return;
        indice = nuevo;
        cargarTransicion(examen.problemas.get(indice));
    }

    /** Cambia de problema con un fundido suave del contenido. */
    private void cargarTransicion(Generadores.Problema p) {
        cargar(p);
        Anim.ejecutar(320, 0, t -> {
            pilaAlfa = (float) Estilo.easeOut(t);
            repaint();
        }, () -> {
            pilaAlfa = 1f;
            repaint();
        });
    }

    private float pilaAlfa = 1f;

    private void terminarExamen() {
        if (examen.terminado()) return;
        int blanco = examen.enBlanco();
        String msg = blanco > 0
                ? "Te quedan " + blanco + (blanco == 1 ? " respuesta" : " respuestas") + " en blanco. Al terminar se califica todo y ya no podrás cambiar nada."
                : "Se calificarán los " + examen.problemas.size() + " problemas y ya no podrás cambiar tus respuestas.";
        if (!UI.confirmar(this, "¿Terminar el examen?", msg, "Terminar")) return;
        finalizarExamen();
    }

    private void finalizarExamen() {
        if (timer != null) timer.stop();
        examen.terminar();
        app.mostrar(new PantallaResultadoExamen(app, examen, volverA), 1);
    }

    private void salir() {
        if (modo == Modo.EXAMEN && !examen.terminado()) {
            if (!UI.confirmar(this, "¿Salir del examen?", "Se perderán las respuestas de este examen y no se guardará la calificación.",
                    "Salir")) return;
        }
        app.mostrar(volverA.get(), -1);
    }

    // ------------------------------------------------------------------ ciclo de vida

    @Override
    public void alMostrar() {
        app.setAcento(A1, A2);
        entrada.fijar(0);
        entrada.ir(1, 500);
        if (modo == Modo.EXAMEN && examen.minutos > 0) {
            actualizarReloj();
            timer = new javax.swing.Timer(500, e -> actualizarReloj());
            timer.start();
        }
    }

    @Override
    public void alOcultar() {
        if (timer != null) timer.stop();
    }

    private void actualizarReloj() {
        int s = examen.segundosRestantes();
        reloj.setTexto(String.format("%d:%02d", s / 60, s % 60));
        reloj.setColor(s < 300 ? Estilo.AVISO : Estilo.TEXTO);
        if (s <= 0 && !examen.terminado() && isShowing()) {
            timer.stop();
            finalizarExamen();
        }
    }

    // ------------------------------------------------------------------ distribución

    private int anchoContenido(int w) {
        int margen = w < 760 ? 20 : 32;
        return Math.max(260, Math.min(1560, w - 2 * margen));
    }

    @Override
    public void doLayout() {
        Dimension d = UI.diseno(this);
        int w = d.width, h = d.height;
        int ancho = anchoContenido(w);
        int x0 = (w - ancho) / 2;
        Dimension dv = UI.prefDiseno(volver);
        UI.poner(volver, x0 - 10, 22, dv.width, 40);

        int derechaX = x0 + ancho;
        if (campoSemilla.isVisible()) {
            UI.poner(campoSemilla, derechaX - 140, 22, 140, 40);
            UI.poner(etiquetaSemilla, derechaX - 140 - 74, 33, 66, 18);
            derechaX -= 140 + 84;
        }
        if (reloj.isVisible()) {
            UI.poner(reloj, derechaX - 90, 30, 90, 24);
            derechaX -= 100;
        }
        int anchoModo = Math.max(0, derechaX - (x0 + dv.width + 10));
        etiquetaModo.setVisible(anchoModo > 120);
        UI.poner(etiquetaModo, derechaX - Math.min(anchoModo, 260), 33, Math.min(anchoModo, 260), 18);

        float tam = w < 620 ? 19f : (w < 900 ? 21f : 24f);
        if (titulo.getTam() != tam) titulo.setFuente(Estilo.NEGRITA, tam);
        int ht = (int) Math.round(titulo.alturaPara(Estilo.px(ancho)) / Estilo.escala());
        int hs = (int) Math.round(subtitulo.alturaPara(Estilo.px(ancho)) / Estilo.escala());
        UI.poner(titulo, x0, 76, ancho, ht);
        UI.poner(subtitulo, x0, 76 + ht + 3, ancho, hs);
        int top = 76 + ht + 3 + hs + 14;

        int hb = UIP.filaBotones(izquierda, derecha, x0, ancho, 0, false);
        int yb = h - 18 - hb;
        UIP.filaBotones(izquierda, derecha, x0, ancho, yb, true);
        yLinea = yb - 9;
        int alto = Math.max(120, yb - 18 - top);
        if (ancho >= 900) {
            // Dos columnas: a la izquierda lo que se consulta (enunciado y fórmulas), a la derecha lo que se responde.
            int ai = (ancho - 14) / 2;
            UIP.columna(List.of(pEnunciado, pFormulas), x0, top, ai, alto);
            UIP.columna(List.of(pRespuestas, pPasos), x0 + ai + 14, top, ancho - ai - 14, alto);
        } else {
            UIP.columna(paneles, x0, top, ancho, alto);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Estilo.suavizar(g2);
        Dimension d = Estilo.unidadesDeDiseno(g2, getWidth(), getHeight());
        int ancho = anchoContenido(d.width);
        int x0 = (d.width - ancho) / 2;
        UIP.rayaTitulo(g2, x0, 66, Estilo.easeOut(entrada.get()));
        // Línea sobre la barra de botones
        g2.setColor(Estilo.BORDE);
        g2.fill(new Rectangle2D.Double(x0, yLinea, ancho, 1));
        g2.dispose();
    }

    @Override
    protected void paintChildren(Graphics g) {
        if (pilaAlfa >= 1f) {
            super.paintChildren(g);
            return;
        }
        // Fundido solo del contenido desplazable al cambiar de problema.
        Graphics2D g2 = (Graphics2D) g.create();
        for (Component c : getComponents()) {
            if (!c.isVisible()) continue;
            Graphics2D gc = (Graphics2D) g2.create(c.getX(), c.getY(), c.getWidth(), c.getHeight());
            if (c instanceof UIP.Plegable) Estilo.alfa(gc, pilaAlfa);
            c.paint(gc);
            gc.dispose();
        }
        g2.dispose();
    }

    // ================================================================== bloque de respuestas

    private final class BloqueRespuestas extends UIP.Bloque {
        private static final int CAMPO_W = 170, ICONO = 30;

        private final class Fila {
            final Generadores.Respuesta r;
            UIP.CampoNumero campo;
            final List<UIP.OpcionBoton> opciones = new ArrayList<>();
            boolean ok;
            // geometría (diseño)
            double top, etqX, etqY, etqW, uniX, uniY, icoX, icoY, corY;

            Fila(Generadores.Respuesta r) {
                this.r = r;
            }
        }

        private final List<Fila> filas = new ArrayList<>();
        private final boolean soloLectura = modo == Modo.RESUELTO || modo == Modo.REVISION;

        BloqueRespuestas() {
            for (Generadores.Respuesta r : problema.respuestas()) {
                Fila f = new Fila(r);
                filas.add(f);
                if (r.esOpcion()) {
                    for (Generadores.Opcion o : r.opciones()) {
                        UIP.OpcionBoton b = new UIP.OpcionBoton(o.valor(), o.texto(), () -> elegir(f, o.valor()));
                        b.setSeleccionada(o.valor().equals(entradas.get(r.clave())));
                        b.setHabilitada(!soloLectura);
                        f.opciones.add(b);
                        add(b);
                    }
                } else {
                    String ph = r.decimales() == 0 ? "entero" : "0." + "0".repeat(Math.max(1, Math.min(r.decimales(), 5)));
                    UIP.CampoNumero c = new UIP.CampoNumero(ph);
                    c.setTexto(entradas.getOrDefault(r.clave(), ""));
                    c.setEditable(!soloLectura);
                    c.setAlEditar(() -> {
                        entradas.put(r.clave(), c.texto());
                        f.ok = false;
                        repaint();
                    });
                    int idx = filas.size() - 1;
                    c.campo.addActionListener(e -> alEnter(idx));
                    f.campo = c;
                    add(c);
                }
            }
        }

        private void elegir(Fila f, String valor) {
            entradas.put(f.r.clave(), valor);
            for (UIP.OpcionBoton b : f.opciones) {
                b.setSeleccionada(b.valor.equals(valor));
                b.setEstado(UIP.Estado.NEUTRO);
            }
            f.ok = false;
            repaint();
        }

        /** Enter en un campo: pasa al siguiente campo numérico; en el último, revisa (o avanza en el examen). */
        private void alEnter(int idx) {
            for (int k = idx + 1; k < filas.size(); k++) {
                if (filas.get(k).campo != null) {
                    filas.get(k).campo.campo.requestFocusInWindow();
                    Rectangle r = filas.get(k).campo.getBounds();
                    scrollRectToVisible(r);
                    return;
                }
            }
            if (esPractica()) revisar();
            else if (modo == Modo.EXAMEN && indice < examen.problemas.size() - 1) mover(1);
        }

        void enfocar(int k) {
            for (int i = k; i < filas.size(); i++) {
                if (filas.get(i).campo != null) {
                    filas.get(i).campo.campo.requestFocusInWindow();
                    return;
                }
            }
        }

        void calificar() {
            for (Fila f : filas) {
                f.ok = Generadores.calificarEntrada(f.r, entradas.get(f.r.clave()));
                if (f.campo != null) {
                    f.campo.setEstado(modo == Modo.RESUELTO ? UIP.Estado.NEUTRO : f.ok ? UIP.Estado.OK : UIP.Estado.MAL);
                } else {
                    String elegido = entradas.get(f.r.clave());
                    for (UIP.OpcionBoton b : f.opciones) {
                        boolean esElegida = b.valor.equals(elegido);
                        b.setEstado(!esElegida || modo == Modo.RESUELTO ? UIP.Estado.NEUTRO : f.ok ? UIP.Estado.OK : UIP.Estado.MAL);
                        b.setCorrecta(solucion && b.valor.equals(f.r.valorOpcion()));
                    }
                }
            }
            revalidate();
            repaint();
        }

        int correctas() {
            int n = 0;
            for (Fila f : filas) if (Generadores.calificarEntrada(f.r, entradas.get(f.r.clave()))) n++;
            return n;
        }

        /** Muestra la respuesta correcta bajo una fila. */
        private boolean conCorrecta(Fila f) {
            return solucion && modo != Modo.RESUELTO && !Generadores.calificarEntrada(f.r, entradas.get(f.r.clave()));
        }

        private boolean conIcono() {
            return revisado && modo != Modo.RESUELTO;
        }

        private int disponer(int w, boolean aplicar) {
            int interior = w - 2 * UIP.PAD;
            int y = UIP.PAD + 30;
            boolean ancha = interior >= 560;
            // Columna de unidades común, para que los campos queden alineados.
            int uw = 0;
            for (Fila f : filas) if (!f.r.esOpcion()) uw = Math.max(uw, Math.min(120, UIP.ancho(this, f.r.unidad(), Estilo.NORMAL, 14f) + 4));
            for (int i = 0; i < filas.size(); i++) {
                Fila f = filas.get(i);
                if (aplicar) f.top = y;
                int espacioIcono = conIcono() ? ICONO + 8 : 0;
                if (!f.r.esOpcion()) {
                    int alto;
                    if (ancha) {
                        int etqW = interior - CAMPO_W - 12 - uw - 12 - espacioIcono;
                        int he = UIP.altoTexto(this, f.r.etiqueta(), Estilo.SEMI, 15.5f, etqW, 1.25, 0);
                        alto = Math.max(he, 44);
                        double cx = UIP.PAD + etqW + 12;
                        if (aplicar) {
                            UI.poner(f.campo, cx, y + (alto - 44) / 2.0, CAMPO_W, 44);
                            f.etqX = UIP.PAD;
                            f.etqY = y + (alto - he) / 2.0;
                            f.etqW = etqW;
                            f.uniX = cx + CAMPO_W + 10;
                            f.uniY = y + alto / 2.0;
                            f.icoX = UIP.PAD + interior - ICONO / 2.0;
                            f.icoY = y + alto / 2.0;
                        }
                    } else {
                        int etqW = interior - espacioIcono;
                        int he = UIP.altoTexto(this, f.r.etiqueta(), Estilo.SEMI, 15.5f, etqW, 1.25, 0);
                        int cw = Math.max(110, Math.min(CAMPO_W + 30, interior - uw - 12));
                        if (aplicar) {
                            UI.poner(f.campo, UIP.PAD, y + he + 6, cw, 44);
                            f.etqX = UIP.PAD;
                            f.etqY = y;
                            f.etqW = etqW;
                            f.uniX = UIP.PAD + cw + 10;
                            f.uniY = y + he + 6 + 22;
                            f.icoX = UIP.PAD + interior - ICONO / 2.0;
                            f.icoY = y + he / 2.0 + 2;
                        }
                        alto = he + 6 + 44;
                    }
                    y += alto;
                } else {
                    int etqW = interior - espacioIcono;
                    int he = UIP.altoTexto(this, f.r.etiqueta(), Estilo.SEMI, 15.5f, etqW, 1.25, 0);
                    if (aplicar) {
                        f.etqX = UIP.PAD;
                        f.etqY = y;
                        f.etqW = etqW;
                        f.icoX = UIP.PAD + interior - ICONO / 2.0;
                        f.icoY = y + he / 2.0 + 2;
                    }
                    y += he + 8;
                    int x = 0, altoLinea = 0;
                    for (UIP.OpcionBoton b : f.opciones) {
                        int bw = Math.min(interior, b.anchoNatural());
                        if (x > 0 && x + bw > interior) {
                            y += altoLinea + 8;
                            x = 0;
                            altoLinea = 0;
                        }
                        int bh = b.alto(bw);
                        if (aplicar) UI.poner(b, UIP.PAD + x, y, bw, bh);
                        x += bw + 8;
                        altoLinea = Math.max(altoLinea, bh);
                    }
                    y += altoLinea;
                }
                if (conCorrecta(f)) {
                    String t = "Correcta: " + f.r.textoCorrecto();
                    if (aplicar) f.corY = y + 6;
                    y += 6 + UIP.altoTexto(this, t, Estilo.SEMI, 14f, interior, 1.25, 0);
                }
                y += i < filas.size() - 1 ? 26 : 0;
            }
            return y + UIP.PAD - 2;
        }

        @Override
        int alto(int w) {
            return disponer(w, false);
        }

        @Override
        void disponer(int w) {
            disponer(w, true);
        }

        @Override
        void pintar(Graphics2D g, int w, int h) {
            String titulo = modo == Modo.RESUELTO ? "Respuestas" : modo == Modo.REVISION ? "Tus respuestas en el examen" : "Tus respuestas";
            UIP.seccion(g, titulo, UIP.PAD, UIP.PAD - 4, w - UIP.PAD - 180, A1);
            if (revisado && modo != Modo.RESUELTO) {
                int ok = correctas(), n = filas.size();
                String t = ok == n ? "¡Todo correcto!  " + ok + " de " + n : ok + " de " + n + " correctas";
                g.setFont(Estilo.fuente(Estilo.SEMI, 13.5f));
                g.setColor(ok == n ? Estilo.EXITO : ok == 0 ? Estilo.ERROR : Estilo.AVISO);
                g.drawString(t, (float) (w - UIP.PAD - g.getFontMetrics().stringWidth(t)), (float) UIP.PAD + 8);
            } else if (esPractica()) {
                g.setFont(Estilo.fuente(Estilo.NORMAL, 12.5f));
                g.setColor(Estilo.TEXTO_TENUE);
                String t = "Se acepta coma decimal y ±1.5 %";
                g.drawString(t, (float) (w - UIP.PAD - g.getFontMetrics().stringWidth(t)), (float) UIP.PAD + 8);
            }
            for (int i = 0; i < filas.size(); i++) {
                Fila f = filas.get(i);
                UIP.parrafo(g, f.r.etiqueta(), Estilo.SEMI, 15.5f, Estilo.TEXTO, f.etqX, f.etqY, f.etqW, 1.25, 0);
                if (!f.r.esOpcion() && !f.r.unidad().isEmpty()) {
                    g.setFont(Estilo.fuente(Estilo.NORMAL, 14f));
                    g.setColor(Estilo.TEXTO_SUAVE);
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString(Estilo.recortar(fm, f.r.unidad(), 120), (float) f.uniX, (float) (f.uniY - fm.getHeight() / 2.0 + fm.getAscent()));
                }
                if (conIcono()) UIP.icono(g, f.icoX, f.icoY, Generadores.calificarEntrada(f.r, entradas.get(f.r.clave())));
                if (conCorrecta(f)) {
                    UIP.parrafo(g, "Correcta: " + f.r.textoCorrecto(), Estilo.SEMI, 14f, Estilo.mezclar(Estilo.EXITO, Color.WHITE, 0.15),
                            UIP.PAD, f.corY, w - 2 * UIP.PAD, 1.25, 0);
                }
                if (i < filas.size() - 1) {
                    double yl = filas.get(i + 1).top - 13;
                    g.setColor(new Color(255, 255, 255, 12));
                    g.fill(new Rectangle2D.Double(UIP.PAD, yl, w - 2 * UIP.PAD, 1));
                }
            }
        }
    }

    // ================================================================== bloque de pistas / solución

    private final class BloquePasos extends UIP.Bloque {
        private static final float T_TIT = 15.5f, T_FOR = 14.5f, T_SUS = 14.5f, T_RES = 15f;
        private final List<Rectangle> zonas = new ArrayList<>();

        int nivel(int i) {
            if (solucion) return 3;
            return Math.max(0, Math.min(3, pistas - 3 * i));
        }

        int ultimoVisible() {
            int u = -1;
            for (int i = 0; i < problema.pasos().size(); i++) if (nivel(i) > 0) u = i;
            return u;
        }

        Rectangle rectanguloDe(int i) {
            if (i < 0 || i >= zonas.size()) return null;
            Rectangle z = zonas.get(i);
            return new Rectangle(Estilo.px(z.x), Estilo.px(z.y), Estilo.px(z.width), Estilo.px(z.height + 30));
        }

        private int disponer(int w, Graphics2D g) {
            int interior = w - 2 * UIP.PAD - 42;
            double x = UIP.PAD + 42;
            int y = UIP.PAD + 30;
            if (g == null) zonas.clear();
            List<Generadores.Paso> pasos = problema.pasos();
            for (int i = 0; i < pasos.size(); i++) {
                int nv = nivel(i);
                if (nv == 0) continue;
                Generadores.Paso p = pasos.get(i);
                int y0 = y;
                if (g != null) {
                    Ellipse2D c = new Ellipse2D.Double(UIP.PAD, y - 2, 28, 28);
                    g.setColor(Estilo.alfa(A1, 34));
                    g.fill(c);
                    g.setFont(Estilo.fuente(Estilo.NEGRITA, 13f));
                    g.setColor(Estilo.mezclar(A1, Color.WHITE, 0.35));
                    Estilo.textoCentrado(g, String.valueOf(i + 1), UIP.PAD + 14, y + 12);
                    UIP.parrafo(g, p.titulo(), Estilo.SEMI, T_TIT, Estilo.TEXTO, x, y, interior, 1.25, 0);
                }
                y += UIP.altoTexto(this, p.titulo(), Estilo.SEMI, T_TIT, interior, 1.25, 0) + 8;
                int hf = UIP.altoTexto(this, p.formula(), Estilo.SEMI, T_FOR, interior - 28, 1.3, 0);
                if (g != null) {
                    RoundRectangle2D caja = new RoundRectangle2D.Double(x, y, interior, hf + 18, 12, 12);
                    g.setColor(Estilo.alfa(A1, 22));
                    g.fill(caja);
                    g.setColor(Estilo.alfa(A1, 70));
                    g.setStroke(new BasicStroke(1f));
                    g.draw(caja);
                    UIP.parrafo(g, p.formula(), Estilo.SEMI, T_FOR, Estilo.mezclar(A1, Color.WHITE, 0.55), x + 14, y + 9, interior - 28, 1.3, 0);
                }
                y += hf + 18;
                if (nv >= 2) {
                    String s = "Sustitución:  " + p.sustitucion();
                    y += 8;
                    if (g != null) UIP.parrafo(g, s, Estilo.NORMAL, T_SUS, Estilo.TEXTO_SUAVE, x, y, interior, 1.32, 0);
                    y += UIP.altoTexto(this, s, Estilo.NORMAL, T_SUS, interior, 1.32, 0);
                }
                if (nv >= 3) {
                    String r = "Resultado:  " + p.resultado();
                    y += 6;
                    if (g != null) UIP.parrafo(g, r, Estilo.SEMI, T_RES, Estilo.mezclar(Estilo.EXITO, Color.WHITE, 0.15), x, y, interior, 1.3, 0);
                    y += UIP.altoTexto(this, r, Estilo.SEMI, T_RES, interior, 1.3, 0);
                }
                if (g == null) {
                    while (zonas.size() < i) zonas.add(new Rectangle(0, y0, 1, 1));
                    zonas.add(new Rectangle(0, y0, w, y - y0));
                }
                y += 20;
            }
            if (!solucion) {
                String sig = siguientePista();
                if (sig != null) {
                    if (g != null) UIP.parrafo(g, sig, Estilo.NORMAL, 13f, Estilo.TEXTO_TENUE, UIP.PAD, y, w - 2 * UIP.PAD, 1.3, 0);
                    y += UIP.altoTexto(this, sig, Estilo.NORMAL, 13f, w - 2 * UIP.PAD, 1.3, 0) + 6;
                }
            }
            return y + UIP.PAD - 18;
        }

        private String siguientePista() {
            int total = problema.pasos().size();
            if (pistas >= 3 * total) return "Ya viste todos los pasos.";
            int i = pistas / 3, n = pistas % 3;
            String que = n == 0 ? "la fórmula" : n == 1 ? "la sustitución" : "el resultado";
            return "Siguiente pista: " + que + " del paso " + (i + 1) + " de " + total + ".";
        }

        @Override
        int alto(int w) {
            return disponer(w, null);
        }

        @Override
        void pintar(Graphics2D g, int w, int h) {
            String t = solucion ? "Solución paso a paso" : "Pistas usadas: " + pistas;
            UIP.seccion(g, t, UIP.PAD, UIP.PAD - 4, w - UIP.PAD, A2);
            disponer(w, g);
        }
    }

    // ================================================================== fórmulas del tipo

    private static final class BloqueFormulas extends UIP.Bloque {
        private static final float TAM = 21f;
        private static final int GAP = 10, PADC = 18;
        private final Generadores.Tipo info;
        private final List<String> formulas;

        BloqueFormulas(Generadores.Tipo info) {
            this.info = info;
            this.formulas = Generadores.formulario(info.id());
        }

        private String cambia() {
            return "Qué cambia en cada versión: " + info.queCambia();
        }

        /** Dos columnas si todas las fórmulas caben en media fila. */
        private int columnas(int interior) {
            double media = (interior - GAP) / 2.0 - 2 * PADC;
            for (String f : formulas) if (UIP.Formula.ancho(f, TAM) > media) return 1;
            return 2;
        }

        private int altoCaja(String f, double ancho) {
            return (int) Math.ceil(UIP.Formula.alto(f, TAM, ancho - 2 * PADC)) + 24;
        }

        /** Recorre las cajas; si {@code g} no es null las dibuja. Devuelve la y final. */
        private int recorrer(int w, Graphics2D g) {
            int interior = w - 2 * UIP.PAD, cols = columnas(interior);
            double cw = cols == 1 ? interior : (interior - GAP) / 2.0;
            int y = UIP.PAD + 30;
            for (int i = 0; i < formulas.size(); i += cols) {
                int hFila = 0;
                for (int c = 0; c < cols && i + c < formulas.size(); c++) hFila = Math.max(hFila, altoCaja(formulas.get(i + c), cw));
                for (int c = 0; c < cols && i + c < formulas.size() && g != null; c++) {
                    double x = UIP.PAD + c * (cw + GAP);
                    RoundRectangle2D caja = new RoundRectangle2D.Double(x, y, cw, hFila, 14, 14);
                    g.setColor(Estilo.alfa(A1, 24));
                    g.fill(caja);
                    g.setColor(Estilo.alfa(A1, 80));
                    g.setStroke(new BasicStroke(1f));
                    g.draw(caja);
                    String f = formulas.get(i + c);
                    double hf = UIP.Formula.alto(f, TAM, cw - 2 * PADC);
                    UIP.Formula.pintar(g, f, TAM, new Color(0xF4F1FF), x + PADC, y + (hFila - hf) / 2, cw - 2 * PADC);
                }
                y += hFila + GAP;
            }
            y += 6;
            if (g != null) UIP.parrafo(g, cambia(), Estilo.NORMAL, 14f, Estilo.TEXTO_SUAVE, UIP.PAD, y, interior, 1.35, 0);
            y += UIP.altoTexto(this, cambia(), Estilo.NORMAL, 14f, interior, 1.35, 0);
            return y + UIP.PAD - 6;
        }

        @Override
        int alto(int w) {
            return recorrer(w, null);
        }

        @Override
        void pintar(Graphics2D g, int w, int h) {
            UIP.seccion(g, "Fórmulas de este tipo", UIP.PAD, UIP.PAD - 4, w - UIP.PAD, A1);
            recorrer(w, g);
        }
    }
}
