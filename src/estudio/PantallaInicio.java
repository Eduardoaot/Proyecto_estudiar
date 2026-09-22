package estudio;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

/** Pantalla principal: elegir materia. */
final class PantallaInicio extends JPanel implements Pantalla {
    private final Ventana app;
    private final UI.Texto titulo, subtitulo;
    private final List<TarjetaMateria> tarjetas = new ArrayList<>();
    private final List<Paso> pasos = new ArrayList<>();
    private final ValorAnimado entrada = new ValorAnimado(0, this::repaint);
    private final Rectangle logo = new Rectangle();

    PantallaInicio(Ventana app) {
        super(null);
        this.app = app;
        setOpaque(false);
        titulo = new UI.Texto("Estudio Activo", Estilo.NEGRITA, 40f, Estilo.TEXTO);
        subtitulo = new UI.Texto("Elige una materia para comenzar. Tu avance se guarda automáticamente en cada respuesta.",
                Estilo.NORMAL, 16f, Estilo.TEXTO_SUAVE);
        add(titulo);
        add(subtitulo);
        for (Materia m : app.materias) {
            TarjetaMateria t = new TarjetaMateria(m, () -> app.mostrar(new PantallaTemas(app, m), 1));
            tarjetas.add(t);
            add(t);
        }
        pasos.add(new Paso(1, "Aprende en orden", "Cada acierto te muestra la respuesta y desbloquea la siguiente pregunta."));
        pasos.add(new Paso(2, "Repaso acumulativo", "Antes de cada pregunta nueva repasas las que aún no dominas."));
        pasos.add(new Paso(3, Motor.RETIRO + " aciertos seguidos", "Con " + Motor.RETIRO + " aciertos seguidos la pregunta queda dominada y sale del repaso."));
        pasos.add(new Paso(4, "Examen final", "Para completar un módulo: sus preguntas seguidas y correctas. Puedes hacerlo cuando quieras."));
        pasos.forEach(this::add);
    }

    @Override
    public void alMostrar() {
        Materia primera = app.materias.get(0);
        app.setAcento(primera.acento1, primera.acento2);
        entrada.fijar(0);
        entrada.ir(1, 600);
        for (int i = 0; i < tarjetas.size(); i++) {
            tarjetas.get(i).refrescar();
            tarjetas.get(i).aparecer(120 + i * 110);
        }
        for (int i = 0; i < pasos.size(); i++) pasos.get(i).aparecer(360 + i * 90);
    }

    @Override
    public void doLayout() {
        // Todo el cálculo va en unidades de diseño; al colocar se aplica el zoom.
        Dimension d = UI.diseno(this);
        int w = d.width, h = d.height;
        int margen = w < 700 ? 24 : 48;
        int contenido = Math.max(240, Math.min(w >= 1500 ? 1240 : 1060, w - 2 * margen));
        int x0 = (w - contenido) / 2;

        // Cabecera: el logo y el título encogen en ventanas pequeñas.
        int logoTam = w < 760 ? 44 : 60;
        float tamTitulo = w < 620 ? 25f : (w < 900 ? 32f : 40f);
        if (titulo.getTam() != tamTitulo) titulo.setFuente(Estilo.NEGRITA, tamTitulo);
        int xTexto = x0 + logoTam + 24;
        int anchoTexto = contenido - logoTam - 24;
        int hs = (int) Math.round(subtitulo.alturaPara(Estilo.px(anchoTexto)) / Estilo.escala());
        int ht = (int) Math.round(titulo.alturaPara(Estilo.px(anchoTexto)) / Estilo.escala());
        int altoCabecera = Math.max(logoTam, ht + 6 + hs);

        int n = tarjetas.size();
        int columnas = contenido >= 480 ? Math.min(Math.max(n, 1), 2) : 1;
        int filas = (n + columnas - 1) / columnas;
        int gap = 8;

        int pasoColumnas = contenido >= 940 ? 4 : (contenido >= 520 ? 2 : 1);
        int pasoFilas = (pasos.size() + pasoColumnas - 1) / pasoColumnas;
        int altoPaso = pasoColumnas >= 4 ? 96 : (pasoColumnas == 2 ? 104 : 88);
        int altoPasos = pasoFilas * altoPaso;

        // Se reparte el alto disponible: primero encogen las tarjetas, después desaparecen los pasos.
        int libre = h - 2 * 28 - altoCabecera - 32 - 24;
        int altoTarjeta = Math.min(320, (libre - altoPasos) / Math.max(1, filas) - gap);
        boolean conPasos = true;
        if (altoTarjeta < 250) {
            altoTarjeta = Math.min(320, libre / Math.max(1, filas) - gap);
            conPasos = false;
        }
        altoTarjeta = Math.max(200, altoTarjeta);
        for (Paso p : pasos) p.setVisible(conPasos);

        int altoTotal = altoCabecera + 32 + filas * (altoTarjeta + gap) + (conPasos ? 24 + altoPasos : 0);
        int y = Math.max(24, (h - altoTotal) / 2);

        logo.setBounds(x0, y + (altoCabecera - logoTam) / 2, logoTam, logoTam);
        UI.poner(titulo, xTexto, y, anchoTexto, ht);
        UI.poner(subtitulo, xTexto, y + ht + 6, anchoTexto, hs);
        y += altoCabecera + 32;

        // Las tarjetas reservan 14px alrededor para su resplandor.
        int anchoCol = (contenido + 28 - gap * (columnas - 1)) / columnas;
        for (int i = 0; i < n; i++) {
            int col = i % columnas, fila = i / columnas;
            UI.poner(tarjetas.get(i), x0 - 14 + col * (anchoCol + gap), y - 14 + fila * (altoTarjeta + gap), anchoCol, altoTarjeta);
        }
        y += filas * (altoTarjeta + gap) + 24;

        if (conPasos) {
            int anchoPaso = (contenido - (pasoColumnas - 1) * 16) / pasoColumnas;
            for (int i = 0; i < pasos.size(); i++) {
                int col = i % pasoColumnas, fila = i / pasoColumnas;
                UI.poner(pasos.get(i), x0 + col * (anchoPaso + 16), y + fila * altoPaso, anchoPaso, altoPaso);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Estilo.suavizar(g2);
        Estilo.unidadesDeDiseno(g2, getWidth(), getHeight());
        double e = Estilo.easeOutBack(entrada.get());
        double cx = logo.getCenterX(), cy = logo.getCenterY();
        g2.translate(cx, cy);
        g2.scale(Math.max(0.01, e), Math.max(0.01, e));
        g2.rotate((1 - Estilo.limitar(entrada.get())) * -0.6);
        RoundRectangle2D r = new RoundRectangle2D.Double(-30, -30, 60, 60, 20, 20);
        Estilo.halo(g2, r, Estilo.alfa(Estilo.PALETAS[0][1], 90), 12, 1);
        g2.setPaint(new GradientPaint(-30, -30, Estilo.PALETAS[0][0], 30, 30, Estilo.PALETAS[0][1]));
        g2.fill(r);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Estilo.palomita(g2, 0, 1, 58, Estilo.limitar(entrada.get() * 1.4 - 0.3));
        g2.dispose();
    }

    // ------------------------------------------------------------------

    /** Tarjeta con una explicación breve del método. */
    private static final class Paso extends UI.Capa {
        private final int numero;
        private final UI.Texto titulo, texto;

        Paso(int numero, String t, String d) {
            this.numero = numero;
            titulo = new UI.Texto(t, Estilo.SEMI, 15f, Estilo.TEXTO);
            texto = new UI.Texto(d, Estilo.NORMAL, 13f, Estilo.TEXTO_SUAVE);
            texto.setMaxLineas(3);
            add(titulo);
            add(texto);
            alfa = 0;
        }

        void aparecer(int retraso) {
            alfa = 0;
            dy = 14;
            repaint();
            Anim.ejecutar(500, retraso, t -> {
                double e = Estilo.easeOut(t);
                alfa = (float) e;
                dy = 14 * (1 - e);
                repaint();
            }, null);
        }

        @Override
        public void doLayout() {
            int w = UI.diseno(this).width - 48;
            int ht = (int) Math.round(titulo.alturaPara(Estilo.px(w)) / Estilo.escala());
            int hx = (int) Math.round(texto.alturaPara(Estilo.px(w)) / Estilo.escala());
            UI.poner(titulo, 48, 2, w, ht);
            UI.poner(texto, 48, 26, w, hx);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Estilo.suavizar(g2);
            Estilo.unidadesDeDiseno(g2, getWidth(), getHeight());
            Ellipse2D c = new Ellipse2D.Double(0, 0, 34, 34);
            g2.setColor(new Color(255, 255, 255, 14));
            g2.fill(c);
            g2.setColor(Estilo.BORDE_FUERTE);
            g2.draw(c);
            g2.setFont(Estilo.fuente(Estilo.NEGRITA, 14f));
            g2.setColor(Estilo.TEXTO);
            Estilo.textoCentrado(g2, String.valueOf(numero), 17, 17);
            g2.dispose();
        }
    }
}
