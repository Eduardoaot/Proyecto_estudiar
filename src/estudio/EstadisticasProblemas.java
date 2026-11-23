package estudio;

import java.awt.Color;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Avance en los problemas de cálculo: intentos y aciertos por tipo, última semilla e historial de exámenes. */
final class EstadisticasProblemas {
    private EstadisticasProblemas() {}

    static final Color ACENTO1 = Estilo.PALETAS[3][0], ACENTO2 = Estilo.PALETAS[3][1];

    private static Properties datos;

    private static Path archivo() {
        return Progreso.carpeta().resolve("problemas.properties");
    }

    private static Properties p() {
        if (datos == null) datos = Progreso.leer(archivo());
        return datos;
    }

    private static void guardar() {
        Progreso.escribir(archivo(), p());
    }

    private static int entero(String k) {
        try {
            return Integer.parseInt(p().getProperty(k, "0").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void sumar(String k, int v) {
        p().setProperty(k, String.valueOf(entero(k) + v));
    }

    static int intentos(String tipo) { return entero(tipo + ".intentos"); }
    static int aciertos(String tipo) { return entero(tipo + ".aciertos"); }
    static int campos(String tipo) { return entero(tipo + ".campos"); }
    static int camposOk(String tipo) { return entero(tipo + ".camposOk"); }
    static int ayudas(String tipo) { return entero(tipo + ".ayudas"); }

    static long ultimaSemilla(String tipo) {
        try {
            return Long.parseLong(p().getProperty(tipo + ".semilla", "-1").trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static void guardarSemilla(String tipo, long semilla) {
        p().setProperty(tipo + ".semilla", String.valueOf(semilla));
        guardar();
    }

    /** Un problema calificado. {@code todoBien}: todas sus respuestas correctas. */
    static void registrar(String tipo, boolean todoBien, int ok, int total, int pistas) {
        sumar(tipo + ".intentos", 1);
        if (todoBien) sumar(tipo + ".aciertos", 1);
        sumar(tipo + ".campos", total);
        sumar(tipo + ".camposOk", ok);
        sumar(tipo + ".ayudas", pistas);
        marcarDia();
        guardar();
    }

    /** Tipo dominado: al menos 2 problemas completos correctos y 60 % o más de aciertos. */
    static boolean dominado(String tipo) {
        int i = intentos(tipo), a = aciertos(tipo);
        return a >= 2 && a >= 0.6 * i;
    }

    static int tiposDominados() {
        int n = 0;
        for (Generadores.Tipo t : Generadores.TIPOS) if (dominado(t.id())) n++;
        return n;
    }

    static int totalIntentos() {
        int n = 0;
        for (Generadores.Tipo t : Generadores.TIPOS) n += intentos(t.id());
        return n;
    }

    static int totalAciertos() {
        int n = 0;
        for (Generadores.Tipo t : Generadores.TIPOS) n += aciertos(t.id());
        return n;
    }

    /**
     * Peso para la práctica adaptativa: más alto con menos aciertos y con menos intentos.
     * Un tipo nunca intentado pesa 1.55; uno con todas las respuestas bien en 10 intentos, ≈ 0.15.
     */
    static double peso(String tipo) {
        // Se usa el porcentaje de respuestas correctas: un caso integral casi nunca sale perfecto completo.
        int i = intentos(tipo);
        double tasa = (camposOk(tipo) + 1.0) / (campos(tipo) + 2.0);
        return (1 - tasa) + 1.0 / (i + 1) + 0.05;
    }

    static String elegirAdaptativo(String evitar) {
        List<Generadores.Tipo> tipos = Generadores.TIPOS;
        double total = 0;
        double[] w = new double[tipos.size()];
        for (int k = 0; k < tipos.size(); k++) {
            w[k] = tipos.get(k).id().equals(evitar) ? 0 : peso(tipos.get(k).id());
            total += w[k];
        }
        double x = ThreadLocalRandom.current().nextDouble() * total;
        for (int k = 0; k < tipos.size(); k++) {
            x -= w[k];
            if (x < 0) return tipos.get(k).id();
        }
        return tipos.get(tipos.size() - 1).id();
    }

    static String tipoAlAzar(String evitar) {
        List<Generadores.Tipo> t = Generadores.TIPOS;
        String id;
        do {
            id = t.get(ThreadLocalRandom.current().nextInt(t.size())).id();
        } while (id.equals(evitar));
        return id;
    }

    /** Semilla nueva para un tipo, distinta de la última usada en ese tipo; queda guardada. */
    static long nuevaSemilla(String tipo) {
        long s = Generadores.semillaNueva(ultimaSemilla(tipo));
        guardarSemilla(tipo, s);
        return s;
    }

    // ------------------------------------------------------------------ días de estudio

    private static void marcarDia() {
        String hoy = java.time.LocalDate.now().toString();
        if (hoy.equals(p().getProperty("ultimoDia"))) return;
        String ayer = java.time.LocalDate.now().minusDays(1).toString();
        int racha = ayer.equals(p().getProperty("ultimoDia")) ? entero("racha") + 1 : 1;
        p().setProperty("racha", String.valueOf(racha));
        p().setProperty("ultimoDia", hoy);
    }

    /** Días seguidos (hasta hoy o ayer) resolviendo problemas. */
    static int racha() {
        String u = p().getProperty("ultimoDia", "");
        String hoy = java.time.LocalDate.now().toString(), ayer = java.time.LocalDate.now().minusDays(1).toString();
        return u.equals(hoy) || u.equals(ayer) ? entero("racha") : 0;
    }

    // ------------------------------------------------------------------ exámenes

    record Examen(String fecha, int puntos, int problemas, int minutos) {}

    static void guardarExamen(int puntos, int problemas, int minutos) {
        String f = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"));
        List<String> l = new ArrayList<>(List.of(p().getProperty("examenes", "").split(";")));
        l.removeIf(String::isBlank);
        l.add(f + "|" + puntos + "|" + problemas + "|" + minutos);
        while (l.size() > 30) l.remove(0);
        p().setProperty("examenes", String.join(";", l));
        marcarDia();
        guardar();
    }

    static List<Examen> historial() {
        List<Examen> r = new ArrayList<>();
        for (String s : p().getProperty("examenes", "").split(";")) {
            String[] x = s.split("\\|");
            if (x.length < 4) continue;
            try {
                r.add(new Examen(x[0], Integer.parseInt(x[1]), Integer.parseInt(x[2]), Integer.parseInt(x[3])));
            } catch (NumberFormatException ignorada) {
                // Entrada dañada: se omite.
            }
        }
        return r;
    }
}
