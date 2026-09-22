package estudio;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

final class Ventana extends JFrame {
    final List<Materia> materias;
    private final Fondo fondo = new Fondo();
    private final Transicion transicion = new Transicion();

    Ventana(List<Materia> materias) {
        super("Estudio Activo");
        this.materias = materias;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getRootPane().setBackground(Estilo.FONDO_ABAJO);
        fondo.add(transicion, BorderLayout.CENTER);
        setContentPane(fondo);
        setIconImages(List.of(icono(16), icono(32), icono(64), icono(128)));
        setMinimumSize(new Dimension(560, 430));
        Rectangle guardada = Progreso.ventanaGuardada();
        if (guardada != null && visibleEnAlgunaPantalla(guardada)) {
            setBounds(guardada);
        } else {
            setSize(1280, 820);
            setLocationRelativeTo(null);
        }
        if (Progreso.ventanaMaximizada()) setExtendedState(MAXIMIZED_BOTH);
        Estilo.setZoomManual(Progreso.zoomManual());
        ajustarEscala();
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                ajustarEscala();
            }
        });
        atajosDeZoom();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                boolean max = (getExtendedState() & MAXIMIZED_BOTH) == MAXIMIZED_BOTH;
                Progreso.guardarVentana(max ? null : getBounds(), max);
            }
        });
        mostrar(new PantallaInicio(this), 0);
    }

    /**
     * El zoom sigue al tamaño de la ventana: con la ventana del tamaño de diseño (1280×820) vale 1
     * y crece al agrandarla, de modo que textos y tarjetas se hacen más grandes, no solo los márgenes.
     */
    private void ajustarEscala() {
        int w = fondo.getWidth(), h = fondo.getHeight();
        if (w <= 0 || h <= 0) return;
        double automatica = Math.min(w / 1280.0, h / 820.0);
        if (Estilo.setEscala(automatica * Estilo.zoomManual())) rehacer();
    }

    /** Marca toda la interfaz para recalcular tamaños y volver a dibujarse. */
    private void rehacer() {
        invalidarTodo(getContentPane());
        getContentPane().revalidate();
        getContentPane().repaint();
    }

    private static void invalidarTodo(Component c) {
        if (c instanceof JComponent jc) {
            jc.invalidate();
            jc.revalidate();
        }
        if (c instanceof Container cont) {
            for (Component hijo : cont.getComponents()) invalidarTodo(hijo);
        }
    }

    /** Ctrl + / Ctrl − ajustan el zoom a mano; Ctrl 0 lo devuelve al automático. */
    private void atajosDeZoom() {
        JComponent raiz = getRootPane();
        int ctrl = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        Object[][] teclas = {
                {KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, ctrl), 0.05},
                {KeyStroke.getKeyStroke(KeyEvent.VK_ADD, ctrl), 0.05},
                {KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, ctrl), 0.05},
                {KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, ctrl), -0.05},
                {KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, ctrl), -0.05},
                {KeyStroke.getKeyStroke(KeyEvent.VK_0, ctrl), 0.0},
        };
        for (Object[] t : teclas) {
            String nombre = "zoom" + t[1] + t[0];
            raiz.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put((KeyStroke) t[0], nombre);
            double paso = (Double) t[1];
            raiz.getActionMap().put(nombre, UI.accion(() -> true, () -> cambiarZoom(paso)));
        }
        addMouseWheelListener(e -> {
            if ((e.getModifiersEx() & java.awt.event.InputEvent.CTRL_DOWN_MASK) != 0) {
                cambiarZoom(e.getWheelRotation() < 0 ? 0.05 : -0.05);
            }
        });
    }

    private void cambiarZoom(double paso) {
        boolean cambio = paso == 0 ? Estilo.setZoomManual(1) : Estilo.setZoomManual(Estilo.zoomManual() + paso);
        if (!cambio) return;
        Progreso.guardarZoomManual(Estilo.zoomManual());
        ajustarEscala();
        rehacer();
    }

    /** Evita restaurar la ventana en un monitor que ya no está conectado. */
    private static boolean visibleEnAlgunaPantalla(Rectangle r) {
        Rectangle visible = new Rectangle();
        for (GraphicsDevice d : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            visible = visible.union(d.getDefaultConfiguration().getBounds());
        }
        return visible.intersects(new Rectangle(r.x + 40, r.y + 10, Math.max(1, r.width - 80), 20));
    }

    /** Cambia de pantalla. dir: 1 avanza, -1 retrocede, 0 aparece en el sitio. */
    void mostrar(JComponent pantalla, int dir) {
        transicion.mostrar(pantalla, dir);
    }

    void setAcento(Color a, Color b) {
        fondo.setAcento(a, b);
    }

    private static Image icono(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Estilo.suavizar(g);
        RoundRectangle2D r = new RoundRectangle2D.Double(0, 0, s, s, s * 0.34, s * 0.34);
        g.setPaint(new GradientPaint(0, 0, Estilo.PALETAS[0][0], s, s, Estilo.PALETAS[0][1]));
        g.fill(r);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(s * 0.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Estilo.palomita(g, s / 2.0, s / 2.0 + s * 0.02, s * 0.95, 1);
        g.dispose();
        return img;
    }
}
