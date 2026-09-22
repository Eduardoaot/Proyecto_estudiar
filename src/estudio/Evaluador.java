package estudio;

import java.text.Normalizer;
import java.util.*;
import java.util.List;

/**
 * Compara la respuesta escrita con la oficial por palabras clave (sin acentos, sin palabras vacías,
 * tolerando plurales y pequeñas faltas de ortografía). Es una sugerencia: el usuario puede corregirla.
 */
final class Evaluador {
    private Evaluador() {}

    record Resultado(double puntaje, boolean correcta, Set<String> acertadas, boolean sinRespuesta) {
        static final Resultado NO_SE = new Resultado(0, false, Set.of(), true);
    }

    private static final Set<String> VACIAS = Set.of(
            "el", "la", "los", "las", "un", "una", "unos", "unas", "lo", "le", "les",
            "de", "del", "al", "a", "en", "y", "e", "o", "u", "que", "se", "su", "sus",
            "es", "son", "era", "ser", "esta", "estan", "este", "estos", "estas", "ese", "esa",
            "por", "para", "con", "como", "mas", "muy", "sin", "sobre", "entre", "hacia", "desde",
            "cual", "cuales", "ya", "sea", "hay", "etc", "decir", "tambien", "cada", "donde",
            "cuando", "tan", "solo", "mismo", "misma", "asi", "pero", "si", "ni", "tal",
            "otro", "otra", "otros", "otras", "todo", "toda", "todos", "todas", "aquella", "aquel",
            "cuyo", "cuyos", "quien", "quienes", "puede", "pueden", "embargo", "mediante");

    static String normalizar(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.replaceAll("[^a-z0-9]+", " ").trim();
    }

    static List<String> palabras(String s) {
        String n = normalizar(s);
        return n.isEmpty() ? List.of() : Arrays.asList(n.split(" "));
    }

    static LinkedHashSet<String> claves(String respuesta) {
        LinkedHashSet<String> r = new LinkedHashSet<>();
        for (String w : palabras(respuesta)) if (!VACIAS.contains(w)) r.add(w);
        if (r.isEmpty()) r.addAll(palabras(respuesta));
        return r;
    }

    /**
     * Las palabras que solo repiten la pregunta (p. ej. "letra" en "¿Con qué letra…?") no cuentan,
     * salvo que la respuesta no tenga otras.
     */
    static Set<String> claves(String respuesta, String pregunta) {
        LinkedHashSet<String> todas = claves(respuesta);
        LinkedHashSet<String> propias = new LinkedHashSet<>(todas);
        propias.removeAll(new HashSet<>(palabras(pregunta)));
        return propias.isEmpty() ? todas : propias;
    }

    static Resultado evaluar(String dada, String correcta, String pregunta) {
        Set<String> claves = claves(correcta, pregunta);
        List<String> usuario = palabras(dada);
        if (usuario.isEmpty()) return Resultado.NO_SE;
        if (claves.isEmpty()) return new Resultado(0, false, Set.of(), false);

        Set<String> acertadas = new LinkedHashSet<>();
        double pesoTotal = 0, pesoAcertado = 0;
        for (String c : claves) {
            double peso = Math.max(3, c.length());
            pesoTotal += peso;
            for (String u : usuario) {
                if (parecidas(c, u)) {
                    acertadas.add(c);
                    pesoAcertado += peso;
                    break;
                }
            }
        }
        double puntaje = pesoAcertado / pesoTotal;
        // Las definiciones largas rara vez se reproducen completas: se exige menos cobertura.
        double umbral = claves.size() <= 4 ? 0.5 : (claves.size() <= 8 ? 0.45 : 0.35);
        return new Resultado(puntaje, puntaje >= umbral - 1e-9, acertadas, false);
    }

    static boolean parecidas(String a, String b) {
        if (a.equals(b)) return true;
        if (Character.isDigit(a.charAt(0)) || Character.isDigit(b.charAt(0))) return false;
        int min = Math.min(a.length(), b.length());
        if (min < 4) return false;
        int comun = 0;
        while (comun < min && a.charAt(comun) == b.charAt(comun)) comun++;
        if (comun >= Math.max(4, (int) Math.ceil(min * 0.75))) return true;
        int max = Math.max(a.length(), b.length());
        int tolerancia = max >= 8 ? 2 : (max >= 5 ? 1 : 0);
        return tolerancia > 0 && max - min <= tolerancia && levenshtein(a, b) <= tolerancia;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + costo);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }
}
