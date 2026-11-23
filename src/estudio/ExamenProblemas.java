package estudio;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Examen de problemas: varios problemas seguidos, sin pistas, con tiempo opcional y calificación sobre 100. */
final class ExamenProblemas {
    final List<Generadores.Problema> problemas;
    final List<Map<String, String>> entradas = new ArrayList<>();
    final int minutos; // 0 = sin límite
    final long inicio = System.currentTimeMillis();
    private long fin;

    private ExamenProblemas(List<Generadores.Problema> problemas, int minutos) {
        this.problemas = List.copyOf(problemas);
        this.minutos = minutos;
        for (int i = 0; i < problemas.size(); i++) entradas.add(new HashMap<>());
    }

    /** {@code n} problemas de tipos distintos; siempre incluye un T20 (caso integral) o un T14 (tiempo tipo), al final. */
    static ExamenProblemas nuevo(int n, int minutos) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String grande = rnd.nextInt(3) < 2 ? "T20" : "T14";
        List<String> otros = new ArrayList<>();
        for (Generadores.Tipo t : Generadores.TIPOS) if (!t.id().equals(grande)) otros.add(t.id());
        Collections.shuffle(otros, rnd);
        List<String> elegidos = new ArrayList<>(otros.subList(0, Math.max(0, n - 1)));
        // Orden del temario (paso 8 → 12) y el problema grande al final.
        elegidos.sort(Comparator.comparingInt((String id) -> Generadores.tipo(id).numeroPaso()).thenComparing(id -> id));
        elegidos.add(grande);
        List<Generadores.Problema> ps = new ArrayList<>();
        for (String id : elegidos) ps.add(Generadores.generar(id, EstadisticasProblemas.nuevaSemilla(id)));
        return new ExamenProblemas(ps, minutos);
    }

    boolean terminado() {
        return fin != 0;
    }

    /** Segundos que quedan; {@code Integer.MAX_VALUE} si no hay límite. */
    int segundosRestantes() {
        if (minutos <= 0) return Integer.MAX_VALUE;
        long fin = terminado() ? this.fin : System.currentTimeMillis();
        return (int) Math.max(0, minutos * 60L - (fin - inicio) / 1000);
    }

    int minutosUsados() {
        long f = terminado() ? fin : System.currentTimeMillis();
        return (int) Math.max(1, Math.round((f - inicio) / 60000.0));
    }

    int correctas(int i) {
        int ok = 0;
        Generadores.Problema p = problemas.get(i);
        for (Generadores.Respuesta r : p.respuestas()) if (Generadores.calificarEntrada(r, entradas.get(i).get(r.clave()))) ok++;
        return ok;
    }

    int total(int i) {
        return problemas.get(i).respuestas().size();
    }

    /** Cada problema vale lo mismo; dentro de él, cada respuesta vale igual. */
    double puntos(int i) {
        return 100.0 / problemas.size() * correctas(i) / Math.max(1, total(i));
    }

    double valorProblema() {
        return 100.0 / problemas.size();
    }

    int calificacion() {
        double s = 0;
        for (int i = 0; i < problemas.size(); i++) s += puntos(i);
        return (int) Math.round(s);
    }

    int enBlanco() {
        int n = 0;
        for (int i = 0; i < problemas.size(); i++) {
            for (Generadores.Respuesta r : problemas.get(i).respuestas()) {
                String e = entradas.get(i).get(r.clave());
                if (e == null || e.isBlank()) n++;
            }
        }
        return n;
    }

    void terminar() {
        if (terminado()) return;
        fin = System.currentTimeMillis();
        for (int i = 0; i < problemas.size(); i++) {
            EstadisticasProblemas.registrar(problemas.get(i).tipo(), correctas(i) == total(i), correctas(i), total(i), 0);
        }
        EstadisticasProblemas.guardarExamen(calificacion(), problemas.size(), minutosUsados());
    }
}
