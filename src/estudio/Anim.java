package estudio;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleConsumer;
import java.util.function.LongPredicate;

/**
 * Animaciones sincronizadas con un reloj único.
 *
 * <p>javax.swing.Timer en Windows tiene una resolución de ~15 ms (≈ 60 fps irregulares) y cada animación
 * con su propio Timer provoca un repintado distinto. Aquí un solo hilo marca el ritmo a la frecuencia
 * del monitor (hasta 240 Hz) y todas las animaciones avanzan en el mismo evento, de modo que Swing
 * agrupa sus repintados en uno solo por cuadro.</p>
 */
final class Anim {
    private Anim() {}

    /** Animación en curso. Solo se manipula desde el hilo de eventos. */
    static final class Tarea {
        private final LongPredicate paso;
        private boolean activa = true;

        private Tarea(LongPredicate paso) {
            this.paso = paso;
        }

        void stop() {
            activa = false;
        }

        boolean isRunning() {
            return activa;
        }
    }

    /**
     * Ejecuta una animación de {@code ms} milisegundos. {@code cuadro} recibe el progreso lineal 0..1;
     * el llamador aplica la curva de suavizado que necesite.
     */
    static Tarea ejecutar(int ms, int retraso, DoubleConsumer cuadro, Runnable fin) {
        long[] inicio = {0};
        long despierta = System.nanoTime() + retraso * 1_000_000L;
        Tarea[] self = new Tarea[1];
        self[0] = new Tarea(ahora -> {
            if (ahora < despierta) return true;
            // El reloj empieza en el primer cuadro visible: si la interfaz estuvo ocupada no hay saltos.
            if (inicio[0] == 0) inicio[0] = ahora;
            double p = ms <= 0 ? 1 : Math.min(1, (ahora - inicio[0]) / 1e6 / ms);
            cuadro.accept(p);
            if (p >= 1) {
                self[0].activa = false;
                if (fin != null) fin.run();
                return false;
            }
            return true;
        });
        Reloj.agregar(self[0]);
        return self[0];
    }

    /** Llama a {@code paso} en cada cuadro hasta que devuelva false. */
    static Tarea continuo(LongPredicate paso) {
        Tarea t = new Tarea(paso);
        Reloj.agregar(t);
        return t;
    }

    static void despues(int ms, Runnable r) {
        if (ms <= 0) {
            SwingUtilities.invokeLater(r);
            return;
        }
        // El retraso no necesita precisión de cuadro: se usa el reloj para no depender del Timer de 15 ms.
        long objetivo = System.nanoTime() + ms * 1_000_000L;
        continuo(ahora -> {
            if (ahora < objetivo) return true;
            r.run();
            return false;
        });
    }

    /** Hilo que marca el ritmo de los cuadros. */
    private static final class Reloj {
        private static final List<Tarea> TAREAS = new ArrayList<>();
        private static final AtomicBoolean PENDIENTE = new AtomicBoolean();
        private static final long PERIODO_NS;
        private static final long MARGEN_NS = 2_500_000;
        private static volatile boolean hayTareas;
        private static Thread hilo;

        static {
            int hz = 60;
            try {
                if (!GraphicsEnvironment.isHeadless()) {
                    int r = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                            .getDisplayMode().getRefreshRate();
                    if (r > 0) hz = r;
                }
            } catch (RuntimeException ignorada) {
                // Se mantiene 60 Hz.
            }
            PERIODO_NS = 1_000_000_000L / Math.max(60, Math.min(240, hz));
        }

        static void agregar(Tarea t) {
            TAREAS.add(t);
            if (!hayTareas) {
                hayTareas = true;
                despertar();
            }
        }

        private static synchronized void despertar() {
            if (hilo == null) {
                hilo = new Thread(Reloj::bucle, "estudio-animador");
                hilo.setDaemon(true);
                hilo.setPriority(Thread.MAX_PRIORITY - 1);
                hilo.start();
            } else {
                java.util.concurrent.locks.LockSupport.unpark(hilo);
            }
        }

        private static void bucle() {
            long siguiente = System.nanoTime();
            while (true) {
                if (!hayTareas) {
                    java.util.concurrent.locks.LockSupport.park();
                    siguiente = System.nanoTime();
                    continue;
                }
                if (PENDIENTE.compareAndSet(false, true)) SwingUtilities.invokeLater(Reloj::cuadro);
                siguiente += PERIODO_NS;
                long ahora = System.nanoTime();
                if (siguiente < ahora - PERIODO_NS) siguiente = ahora; // solo se resincroniza si se perdió un cuadro entero
                // Thread.sleep tiene resolución de ~1-2 ms en Windows (LockSupport.parkNanos, ~15 ms):
                // se duerme hasta poco antes del cuadro y el resto se espera activamente.
                long espera = siguiente - ahora - MARGEN_NS;
                try {
                    if (espera > 0) Thread.sleep(espera / 1_000_000, (int) (espera % 1_000_000));
                } catch (InterruptedException e) {
                    return;
                }
                while (System.nanoTime() < siguiente) Thread.onSpinWait();
            }
        }

        private static void cuadro() {
            PENDIENTE.set(false);
            long ahora = System.nanoTime();
            Tarea[] copia = TAREAS.toArray(new Tarea[0]);
            for (Tarea t : copia) {
                if (!t.activa) continue;
                try {
                    if (!t.paso.test(ahora)) t.activa = false;
                } catch (RuntimeException e) {
                    t.activa = false;
                    e.printStackTrace();
                }
            }
            TAREAS.removeIf(t -> !t.activa);
            if (TAREAS.isEmpty()) hayTareas = false;
        }
    }
}
