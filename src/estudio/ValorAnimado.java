package estudio;

/** Un número que se desplaza suavemente hacia un destino (hover, barras, anillos…). */
final class ValorAnimado {
    private double valor, desde, hasta;
    private long inicio;
    private int duracion;
    private final Runnable alCambiar;
    private Anim.Tarea tarea;

    ValorAnimado(double inicial, Runnable alCambiar) {
        this.valor = this.desde = this.hasta = inicial;
        this.alCambiar = alCambiar;
    }

    double get() {
        return valor;
    }

    double destino() {
        return hasta;
    }

    boolean animando() {
        return tarea != null && tarea.isRunning();
    }

    void ir(double destino, int ms) {
        if (destino == hasta && (animando() || valor == destino)) return;
        if (ms <= 0) {
            fijar(destino);
            return;
        }
        desde = valor;
        hasta = destino;
        duracion = ms;
        inicio = System.nanoTime();
        if (!animando()) tarea = Anim.continuo(this::paso);
    }

    void fijar(double v) {
        if (tarea != null) tarea.stop();
        valor = desde = hasta = v;
        alCambiar.run();
    }

    private boolean paso(long ahora) {
        double p = Math.min(1, (ahora - inicio) / 1e6 / duracion);
        valor = desde + (hasta - desde) * Estilo.easeOut(p);
        if (p >= 1) valor = hasta;
        alCambiar.run();
        return p < 1;
    }
}
