package estudio;

import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Planificador del método de estudio.
 *
 * <ul>
 *   <li>Las preguntas se desbloquean en orden. La más reciente es el "foco".</li>
 *   <li>Antes de cada pregunta nueva se repasan las que aún no están dominadas (repaso acumulativo).</li>
 *   <li>Si el foco se falla, se repasan las demás y vuelve a salir al final de la ronda.</li>
 *   <li>Cualquier otra pregunta fallada vuelve a salir después de {@value #SEPARACION} preguntas.</li>
 *   <li>Con {@value #MAX_FALLADAS} falladas activas no se desbloquean nuevas; al acertar una se libera espacio.</li>
 *   <li>Con {@value #RETIRO} aciertos seguidos la pregunta queda dominada y sale del repaso. Un fallo
 *       pone su contador a cero.</li>
 *   <li>Cuando todas están dominadas llega el examen final: las {@code total} preguntas seguidas y
 *       correctas, en orden aleatorio. Un fallo reinicia el examen (con otro orden) y devuelve esa
 *       pregunta al repaso.</li>
 * </ul>
 */
final class Motor {
    static final int MAX_FALLADAS = 5;
    static final int SEPARACION = 2;
    static final int RETIRO = 2;

    enum Tipo { NUEVA, REPASO, REINTENTO, FINAL }

    /** {@code orden} es el instante previsto: la cola se mantiene ordenada por él, lo que evita que una pregunta se aplace sin fin. */
    record Turno(int idx, Tipo tipo, double orden) {}

    final int total;
    private int desbloqueadas;
    private int foco = -1;
    private boolean focoResuelto;
    private int ultimo = -1;
    private boolean terminado;
    private int limiteRepaso;
    private long reloj;
    private boolean examen;
    private int examenIndice;
    private int[] ordenExamen;
    private final int[] seguidas;
    private final LinkedHashSet<Integer> falladas = new LinkedHashSet<>();
    private final LinkedList<Turno> cola = new LinkedList<>();
    private final LinkedHashMap<Integer, Integer> pendientes = new LinkedHashMap<>();
    private final Random azar = new Random();

    int aciertos, errores, racha, mejorRacha;

    Motor(int total) {
        this.total = total;
        this.seguidas = new int[Math.max(0, total)];
    }

    // ---------------------------------------------------------------- consultas

    int desbloqueadas() { return desbloqueadas; }
    int falladas() { return falladas.size(); }
    boolean bloqueado() { return falladas.size() >= MAX_FALLADAS && desbloqueadas < total; }
    boolean terminado() { return terminado; }
    boolean esFallada(int idx) { return falladas.contains(idx); }

    /** Aciertos seguidos de una pregunta (0..{@value #RETIRO}). */
    int seguidas(int idx) { return idx >= 0 && idx < total ? seguidas[idx] : 0; }

    /** Una pregunta dominada ya no aparece en los repasos. */
    boolean dominada(int idx) { return seguidas(idx) >= RETIRO; }

    int dominadas() {
        int n = 0;
        for (int i = 0; i < total; i++) if (dominada(i)) n++;
        return n;
    }

    boolean enExamen() { return examen; }

    /**
     * Empieza el examen final ahora, sin haber practicado: desbloquea todas las preguntas del
     * módulo y encadena las {@code total} desde la primera.
     */
    void iniciarExamen() {
        if (total == 0) return;
        terminado = false;
        desbloqueadas = total;
        foco = total - 1;
        focoResuelto = true;
        pendientes.clear();
        cola.clear();
        empezarExamen();
        planificar();
    }

    /** Abandona el examen y vuelve a la práctica (solo si queda algo por dominar). */
    void salirExamen() {
        if (!examen || dominadas() >= total) return;
        examen = false;
        examenIndice = 0;
        ordenExamen = null;
        cola.clear();
        planificar();
    }

    /** Preguntas ya encadenadas en el examen final. */
    int examenIndice() { return examenIndice; }

    /** Número de preguntas del repaso: 0 = todas las anteriores. */
    void setLimiteRepaso(int n) { limiteRepaso = Math.max(0, n); }

    Turno actual() {
        if (!terminado && cola.isEmpty()) planificar();
        return cola.peekFirst();
    }

    /** Turnos ya planificados después del actual. */
    List<Turno> proximas(int n) {
        return cola.stream().skip(1).limit(n).toList();
    }

    /** Explica qué ocurrirá si la pregunta actual se marca como correcta o incorrecta. */
    String consecuencia(boolean ok) {
        Turno t = cola.peekFirst();
        if (t == null) return "";
        int idx = t.idx();
        if (t.tipo() == Tipo.FINAL) {
            if (!ok) return "Se reinicia el examen final y esta pregunta vuelve al repaso.";
            return examenIndice + 1 >= total
                    ? "¡Es la última! Con esto completas la sección."
                    : "Examen final: " + (examenIndice + 1) + " de " + total + " seguidas.";
        }
        boolean quedan = desbloqueadas < total;
        boolean eraFallada = falladas.contains(idx);
        int fallasDespues = falladas.size() + (ok ? (eraFallada ? -1 : 0) : (eraFallada ? 0 : 1));
        if (ok) {
            int nuevas = seguidas(idx) + 1;
            if (nuevas >= RETIRO) {
                String base = "¡Dominada! " + RETIRO + " aciertos seguidos: ya no volverá a aparecer en el repaso.";
                if (!quedan && fallasDespues == 0 && dominadas() + 1 >= total) base += " Empieza el examen final.";
                return base;
            }
            String base = "Llevas " + nuevas + " de " + RETIRO + " aciertos seguidos en esta pregunta.";
            if (idx == foco && quedan) {
                base += fallasDespues >= MAX_FALLADAS
                        ? " Tienes " + MAX_FALLADAS + " falladas activas: acierta una para desbloquear la siguiente."
                        : " Se desbloquea la siguiente pregunta.";
            } else if (eraFallada && falladas.size() >= MAX_FALLADAS && quedan) {
                base += " ¡Liberaste un espacio para una pregunta nueva!";
            }
            return base;
        }
        String base;
        if (idx == foco && !focoResuelto) {
            base = repaso(foco, -1).size() >= SEPARACION
                    ? "Repasarás las anteriores y luego volverá a salir."
                    : "Volverá a salir después de " + SEPARACION + " preguntas.";
        } else {
            base = "Volverá a salir después de " + SEPARACION + " preguntas.";
        }
        if (seguidas(idx) > 0) base = "Pierdes los " + seguidas(idx) + " aciertos seguidos de esta pregunta. " + base;
        if (!eraFallada && fallasDespues >= MAX_FALLADAS && quedan)
            base += " Llegas a " + MAX_FALLADAS + " falladas: no habrá nuevas hasta que aciertes una.";
        return base;
    }

    // ---------------------------------------------------------------- acciones

    void responder(boolean ok) {
        Turno t = cola.pollFirst();
        if (t == null) return;
        int idx = t.idx();
        ultimo = idx;
        reloj++;
        avanzarPendientes();
        if (ok) {
            aciertos++;
            racha++;
            mejorRacha = Math.max(mejorRacha, racha);
            seguidas[idx]++;
            falladas.remove(idx);
            if (idx == foco) focoResuelto = true;
        } else {
            errores++;
            racha = 0;
            seguidas[idx] = 0;
            falladas.add(idx);
        }

        if (t.tipo() == Tipo.FINAL) {
            cola.clear();
            if (ok) {
                examenIndice++;
                if (examenIndice >= total) {
                    terminado = true;
                    return;
                }
            } else {
                examen = false;
                examenIndice = 0;
                ordenExamen = null;
            }
            planificar();
            return;
        }

        if (!ok) {
            cola.removeIf(x -> x.idx() == idx);
            if (idx == foco) {
                focoResuelto = false;
                // Si la ronda sigue, el foco vuelve al final de lo que queda; si no, planificar() lo reprograma.
                if (!cola.isEmpty()) insertar(new Turno(idx, Tipo.REINTENTO, cola.getLast().orden() + 1));
            } else {
                double orden = reloj + SEPARACION + 0.5;
                long antes = cola.stream().filter(x -> x.orden() < orden).count();
                if (antes >= SEPARACION) insertar(new Turno(idx, Tipo.REINTENTO, orden));
                else pendientes.put(idx, SEPARACION);
            }
        } else if (dominada(idx)) {
            // Al quedar dominada desaparece de la ronda en curso.
            cola.removeIf(x -> x.idx() == idx);
            pendientes.remove(idx);
        }
        if (cola.isEmpty()) planificar();
    }

    private void avanzarPendientes() {
        List<Integer> listos = new ArrayList<>();
        Iterator<Map.Entry<Integer, Integer>> it = pendientes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> e = it.next();
            int restante = e.getValue() - 1;
            if (restante <= 0) {
                listos.add(e.getKey());
                it.remove();
            } else {
                e.setValue(restante);
            }
        }
        for (int k = 0; k < listos.size(); k++) {
            int i = listos.get(k);
            cola.removeIf(x -> x.idx() == i);
            insertar(new Turno(i, Tipo.REINTENTO, reloj + 0.5 + k * 0.01));
        }
    }

    private void planificar() {
        if (terminado) return;
        if (examen) {
            programarExamen();
            return;
        }
        if (foco < 0) {
            if (total == 0) {
                terminado = true;
                return;
            }
            desbloquear();
            agregar(foco, Tipo.NUEVA);
            return;
        }
        boolean quedan = desbloqueadas < total;
        boolean lleno = falladas.size() >= MAX_FALLADAS;

        if (!focoResuelto) {
            List<Integer> otros = repaso(foco, -1);
            if (otros.size() < SEPARACION && quedan && !lleno) {
                // No hay con qué intercalar: el foco fallado espera y se desbloquea la siguiente.
                pendientes.put(foco, SEPARACION);
                desbloquear();
                for (int i : repaso(foco, -1)) agregarRepaso(i);
                agregar(foco, Tipo.NUEVA);
            } else {
                for (int i : otros) agregarRepaso(i);
                agregar(foco, Tipo.REINTENTO);
            }
            return;
        }

        if (quedan && !lleno) {
            desbloquear();
            for (int i : repaso(foco, ultimo)) agregarRepaso(i);
            agregar(foco, Tipo.NUEVA);
            return;
        }

        if (lleno && quedan) {
            // De una en una: al acertar una fallada se libera espacio y entra una pregunta nueva.
            for (int i : falladas) {
                if (!pendientes.containsKey(i)) {
                    agregar(i, Tipo.REINTENTO);
                    break;
                }
            }
        } else {
            for (int i : repaso(-1, ultimo)) agregarRepaso(i);
            // Si lo único pendiente era la pregunta recién respondida, se repite.
            if (cola.isEmpty()) for (int i : repaso(-1, -1)) agregarRepaso(i);
        }

        if (cola.isEmpty() && !pendientes.isEmpty()) {
            int relleno = elegirRelleno();
            if (relleno >= 0) agregar(relleno, Tipo.REPASO);
            else vaciarPendientes();
        }
        if (cola.isEmpty()) {
            if (!quedan && falladas.isEmpty() && pendientes.isEmpty() && dominadas() >= total) {
                empezarExamen();
                programarExamen();
            } else if (!pendientes.isEmpty()) {
                vaciarPendientes();
            } else {
                terminado = true;
            }
        }
    }

    private void programarExamen() {
        if (examenIndice >= total) {
            terminado = true;
            return;
        }
        if (ordenExamen == null || ordenExamen.length != total) barajarExamen();
        agregar(ordenExamen[examenIndice], Tipo.FINAL);
    }

    private void empezarExamen() {
        examen = true;
        examenIndice = 0;
        barajarExamen();
    }

    /** El examen final pregunta en orden aleatorio, distinto en cada intento. */
    private void barajarExamen() {
        ordenExamen = new int[total];
        for (int i = 0; i < total; i++) ordenExamen[i] = i;
        for (int i = total - 1; i > 0; i--) {
            int j = azar.nextInt(i + 1);
            int t = ordenExamen[i];
            ordenExamen[i] = ordenExamen[j];
            ordenExamen[j] = t;
        }
    }

    private void desbloquear() {
        foco = desbloqueadas++;
        focoResuelto = false;
    }

    /** Añade al final de la cola. */
    private void agregar(int idx, Tipo tipo) {
        double orden = cola.isEmpty() ? reloj + 1 : Math.max(reloj + 1, Math.floor(cola.getLast().orden()) + 1);
        cola.add(new Turno(idx, tipo, orden));
    }

    private void agregarRepaso(int i) {
        agregar(i, falladas.contains(i) ? Tipo.REINTENTO : Tipo.REPASO);
    }

    /** Inserta manteniendo la cola ordenada (estable ante empates). */
    private void insertar(Turno t) {
        ListIterator<Turno> it = cola.listIterator();
        while (it.hasNext()) {
            if (it.next().orden() > t.orden()) {
                it.previous();
                break;
            }
        }
        it.add(t);
    }

    /** Preguntas desbloqueadas que siguen entrando al repaso (no dominadas ni en espera). */
    private List<Integer> repaso(int excluirA, int excluirB) {
        List<Integer> r = new ArrayList<>();
        // La ventana de repaso solo limita mientras quedan preguntas nuevas: al final entran todas.
        int desde = limiteRepaso > 0 && desbloqueadas < total ? Math.max(0, foco - limiteRepaso) : 0;
        for (int i = 0; i < desbloqueadas; i++) {
            if (i == excluirA || i == excluirB || pendientes.containsKey(i) || dominada(i)) continue;
            if (i >= desde || falladas.contains(i)) r.add(i);
        }
        return r;
    }

    /**
     * Pregunta con la que separar dos falladas. Nunca se usa una dominada: si no hay ninguna
     * disponible, se adelanta la fallada que esperaba.
     */
    private int elegirRelleno() {
        List<Integer> candidatas = new ArrayList<>();
        for (int i = 0; i < desbloqueadas; i++) {
            if (pendientes.containsKey(i) || falladas.contains(i) || i == ultimo || dominada(i)) continue;
            candidatas.add(i);
        }
        return candidatas.isEmpty() ? -1 : candidatas.get(azar.nextInt(candidatas.size()));
    }

    private void vaciarPendientes() {
        for (int i : pendientes.keySet()) agregar(i, Tipo.REINTENTO);
        pendientes.clear();
    }

    // ---------------------------------------------------------------- persistencia

    Properties exportar() {
        Properties p = new Properties();
        p.setProperty("version", "2");
        p.setProperty("total", String.valueOf(total));
        p.setProperty("desbloqueadas", String.valueOf(desbloqueadas));
        p.setProperty("foco", String.valueOf(foco));
        p.setProperty("focoResuelto", String.valueOf(focoResuelto));
        p.setProperty("ultimo", String.valueOf(ultimo));
        p.setProperty("reloj", String.valueOf(reloj));
        p.setProperty("terminado", String.valueOf(terminado));
        p.setProperty("examen", String.valueOf(examen));
        p.setProperty("examenIndice", String.valueOf(examenIndice));
        p.setProperty("ordenExamen", ordenExamen == null ? ""
                : Arrays.stream(ordenExamen).mapToObj(String::valueOf).collect(Collectors.joining(",")));
        p.setProperty("aciertos", String.valueOf(aciertos));
        p.setProperty("errores", String.valueOf(errores));
        p.setProperty("racha", String.valueOf(racha));
        p.setProperty("mejorRacha", String.valueOf(mejorRacha));
        p.setProperty("falladas", unir(falladas));
        p.setProperty("seguidas", Arrays.stream(seguidas).mapToObj(String::valueOf).collect(Collectors.joining(",")));
        p.setProperty("cola", cola.stream().map(t -> t.idx() + ":" + t.tipo().name() + ":" + t.orden()).collect(Collectors.joining(",")));
        p.setProperty("pendientes", pendientes.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(",")));
        return p;
    }

    static Motor importar(Properties p, int total) {
        Motor m = new Motor(total);
        try {
            if (Integer.parseInt(p.getProperty("total", "-1")) != total) return new Motor(total);
            m.desbloqueadas = entero(p, "desbloqueadas", 0, total);
            m.foco = Integer.parseInt(p.getProperty("foco", "-1"));
            if (m.foco >= m.desbloqueadas) throw new IllegalStateException("foco inválido");
            m.focoResuelto = Boolean.parseBoolean(p.getProperty("focoResuelto"));
            m.ultimo = Integer.parseInt(p.getProperty("ultimo", "-1"));
            m.reloj = Long.parseLong(p.getProperty("reloj", "0"));
            m.terminado = Boolean.parseBoolean(p.getProperty("terminado"));
            m.examen = Boolean.parseBoolean(p.getProperty("examen"));
            m.examenIndice = entero(p, "examenIndice", 0, total);
            m.aciertos = entero(p, "aciertos", 0, Integer.MAX_VALUE);
            m.errores = entero(p, "errores", 0, Integer.MAX_VALUE);
            m.racha = entero(p, "racha", 0, Integer.MAX_VALUE);
            m.mejorRacha = entero(p, "mejorRacha", 0, Integer.MAX_VALUE);
            for (String s : partes(p, "falladas")) m.falladas.add(indice(s, m.desbloqueadas));
            List<String> seguidas = partes(p, "seguidas");
            if (!seguidas.isEmpty()) {
                if (seguidas.size() != total) throw new IllegalStateException("tamaño inválido");
                for (int i = 0; i < total; i++) m.seguidas[i] = Math.max(0, Math.min(RETIRO, Integer.parseInt(seguidas.get(i).trim())));
            } else {
                // Progreso guardado con la versión anterior: lo acertado cuenta como un acierto seguido.
                for (String s : partes(p, "dominadas")) m.seguidas[indice(s, m.desbloqueadas)] = 1;
                m.terminado = false;
            }
            for (String s : partes(p, "cola")) {
                String[] kv = s.split(":");
                m.insertar(new Turno(indice(kv[0], m.desbloqueadas), Tipo.valueOf(kv[1]), Double.parseDouble(kv[2])));
            }
            for (String s : partes(p, "pendientes")) {
                String[] kv = s.split(":");
                m.pendientes.put(indice(kv[0], m.desbloqueadas), Integer.parseInt(kv[1]));
            }
            List<String> orden = partes(p, "ordenExamen");
            if (orden.size() == total) {
                int[] valores = new int[total];
                boolean[] vistos = new boolean[total];
                for (int i = 0; i < total; i++) {
                    int v = indice(orden.get(i), total);
                    if (vistos[v]) throw new IllegalStateException("orden repetido");
                    vistos[v] = true;
                    valores[i] = v;
                }
                m.ordenExamen = valores;
            }
            if (m.examen) m.cola.removeIf(t -> t.tipo() != Tipo.FINAL);
            return m;
        } catch (RuntimeException e) {
            return new Motor(total);
        }
    }

    private static String unir(Collection<Integer> c) {
        return c.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static List<String> partes(Properties p, String clave) {
        String v = p.getProperty(clave, "").trim();
        return v.isEmpty() ? List.of() : Arrays.asList(v.split(","));
    }

    private static int entero(Properties p, String clave, int min, int max) {
        int v = Integer.parseInt(p.getProperty(clave, "0").trim());
        if (v < min || v > max) throw new IllegalStateException(clave + " fuera de rango");
        return v;
    }

    private static int indice(String s, int limite) {
        int v = Integer.parseInt(s.trim());
        if (v < 0 || v >= limite) throw new IllegalStateException("índice fuera de rango");
        return v;
    }
}
