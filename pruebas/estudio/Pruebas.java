package estudio;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Pruebas del módulo de problemas. Se ejecutan con probar.bat.
 *
 * <pre>
 *   java -cp out;out-pruebas estudio.Pruebas            pruebas propias
 *   java -cp out;out-pruebas estudio.Pruebas volcar f   además escribe f (JSONL) para comparar con el JS
 * </pre>
 */
public final class Pruebas {
    private static int fallas, total;

    public static void main(String[] args) throws IOException {
        ejemploDeClase();
        todosLosTipos();
        normalizacion();
        resueltosDelJson();
        if (args.length >= 2 && args[0].equals("volcar")) volcar(Paths.get(args[1]));
        System.out.println();
        System.out.println(fallas == 0 ? "OK: " + total + " comprobaciones" : "FALLAS: " + fallas + " de " + total);
        System.exit(fallas == 0 ? 0 : 1);
    }

    private static void check(boolean ok, String que) {
        total++;
        if (!ok) {
            fallas++;
            if (fallas <= 40) System.out.println("  FALLA: " + que);
        }
    }

    private static double valor(Generadores.Problema p, String clave) {
        for (Generadores.Respuesta r : p.respuestas()) if (r.clave().equals(clave)) return r.valor();
        throw new IllegalArgumentException(clave);
    }

    private static void ejemploDeClase() {
        System.out.println("T20 semilla 0 = ejemplo de clase");
        Generadores.Problema p = Generadores.generar("T20", 0);
        Object[][] esperado = {{"TT", 91.631}, {"ETE", 35.636}, {"ETI", 20.368}, {"TNO", 29.632}, {"SNP", 5.995},
                {"SF", 6.969}, {"real", 423.333}, {"prod", 4.62}};
        for (Object[] e : esperado) {
            double v = valor(p, (String) e[0]);
            check(v == (Double) e[1], e[0] + " = " + v + ", se esperaba " + e[1]);
        }
    }

    private static void todosLosTipos() {
        System.out.println("20 tipos × 500 semillas: sin NaN/undefined/Infinity y la respuesta correcta se califica bien");
        for (Generadores.Tipo t : Generadores.TIPOS) {
            for (long s = 0; s < 500; s++) {
                Generadores.Problema p = Generadores.generar(t.id(), s);
                StringBuilder texto = new StringBuilder(p.enunciado());
                for (Generadores.Paso x : p.pasos()) texto.append(x.titulo()).append(x.formula()).append(x.sustitucion()).append(x.resultado());
                if (p.tabla() != null) p.tabla().filas().forEach(f -> f.forEach(texto::append));
                String tx = texto.toString();
                check(!tx.contains("NaN") && !tx.contains("undefined") && !tx.contains("Infinity") && !tx.contains("null"),
                        t.id() + "/" + s + " texto con valor inválido");
                check(!p.respuestas().isEmpty() && !p.pasos().isEmpty(), t.id() + "/" + s + " sin respuestas o pasos");
                for (Generadores.Respuesta r : p.respuestas()) {
                    if (r.esOpcion()) {
                        check(Generadores.calificar(r, r.valorOpcion()), t.id() + "/" + s + " opción " + r.clave());
                        check(r.opciones().stream().anyMatch(o -> o.valor().equals(r.valorOpcion())), t.id() + "/" + s + " opción fuera de lista");
                    } else {
                        check(Double.isFinite(r.valor()), t.id() + "/" + s + " " + r.clave() + " no finito");
                        check(Generadores.calificar(r, Generadores.js(r.valor())), t.id() + "/" + s + " " + r.clave() + " = " + r.valor());
                        check(Generadores.calificarEntrada(r, Generadores.fmt(r.valor(), r.decimales())),
                                t.id() + "/" + s + " " + r.clave() + " formateado " + Generadores.fmt(r.valor(), r.decimales()));
                    }
                }
            }
        }
    }

    private static void normalizacion() {
        System.out.println("Normalización de la entrada");
        check(Objects.equals(Generadores.aNumero("0,25"), 0.25), "\"0,25\" → 0.25");
        check(Objects.equals(Generadores.aNumero("65.4%"), 65.4), "\"65.4%\" → 65.4");
        check(Objects.equals(Generadores.aNumero(" 1 234.5 "), 1234.5), "\" 1 234.5 \" → 1234.5");
        check(Objects.equals(Generadores.aNumero("1,234.5"), 1234.5), "\"1,234.5\" → 1234.5");
        check(Generadores.aNumero("abc") == null, "\"abc\" → no es número");
        Generadores.Respuesta r = Generadores.generar("T02", 11).respuestas().get(1); // 0.45
        check(Generadores.calificarEntrada(r, "0,45"), "0,45 se acepta como 0.45");
        check(!Generadores.calificarEntrada(r, ""), "vacío no se acepta");
        check(!Generadores.calificarEntrada(r, "0.5"), "0.5 no se acepta para 0.45");
    }

    /** Algunos valores de problemas_ordinario.json (problemas_fijos). */
    private static void resueltosDelJson() {
        System.out.println("Coincidencia con problemas_ordinario.json");
        check(valor(Generadores.generar("T01", 11), "mnvez") == 0.28, "T01-11 mnvez");
        check(valor(Generadores.generar("T04", 22), "total") == 3.517, "T04-22 total");
        check(valor(Generadores.generar("T06", 44), "rep") == 66.509, "T06-44 rep");
        check(valor(Generadores.generar("T08", 33), "frac") == 0.02083, "T08-33 frac");
        check(valor(Generadores.generar("T13", 22), "fuera") == 4.754, "T13-22 fuera");
        check(valor(Generadores.generar("T17", 33), "carga") == 0.1441, "T17-33 carga");
        check(valor(Generadores.generar("T19", 55), "TTmax") == 46.852, "T19-55 TTmax");
        check(valor(Generadores.generar("T20", 44), "TT") == 38.956, "T20-44 TT");
        check(valor(Generadores.generar("T20", 33), "ent") == 4, "T20-33 ent");
        check(Generadores.generar("T06", 44).enunciado().startsWith("En una planta de inyección de plástico en Apodaca cada 3 unidades"),
                "T06-44 enunciado");
    }

    // ------------------------------------------------------------------ volcado para comparar con el JS

    private static void volcar(Path destino) throws IOException {
        long[] semillas = new long[260];
        for (int i = 0; i < 250; i++) semillas[i] = i;
        long[] grandes = {999_999_999L, 123_456_789L, 4_000_000_000L, 77_777L, 31_415_926L, 271_828_182L, 1L << 33, 5_000_000_001L, 42L, 987_654_321L};
        System.arraycopy(grandes, 0, semillas, 250, grandes.length);
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(destino, StandardCharsets.UTF_8))) {
            for (Generadores.Tipo t : Generadores.TIPOS) for (long s : semillas) w.println(json(Generadores.generar(t.id(), s)));
        }
        System.out.println("Volcado en " + destino);
    }

    private static String json(Generadores.Problema p) {
        StringBuilder sb = new StringBuilder("{");
        campo(sb, "tipo", p.tipo()).append(',');
        sb.append("\"semilla\":").append(p.semilla()).append(',');
        campo(sb, "enunciado", p.enunciado()).append(',');
        sb.append("\"tabla\":");
        if (p.tabla() == null) sb.append("null");
        else {
            sb.append("{\"columnas\":");
            arr(sb, p.tabla().columnas());
            sb.append(",\"filas\":[");
            for (int i = 0; i < p.tabla().filas().size(); i++) {
                if (i > 0) sb.append(',');
                arr(sb, p.tabla().filas().get(i));
            }
            sb.append("]}");
        }
        sb.append(",\"pasos\":[");
        for (int i = 0; i < p.pasos().size(); i++) {
            Generadores.Paso x = p.pasos().get(i);
            if (i > 0) sb.append(',');
            arr(sb, List.of(x.titulo(), x.formula(), x.sustitucion(), x.resultado()));
        }
        sb.append("],\"respuestas\":[");
        for (int i = 0; i < p.respuestas().size(); i++) {
            Generadores.Respuesta r = p.respuestas().get(i);
            if (i > 0) sb.append(',');
            sb.append('{');
            campo(sb, "clave", r.clave()).append(',');
            campo(sb, "etiqueta", r.etiqueta()).append(',');
            campo(sb, "tipo", r.esOpcion() ? "opcion" : "numero").append(',');
            if (r.esOpcion()) {
                campo(sb, "valor", r.valorOpcion()).append(",\"opciones\":[");
                for (int k = 0; k < r.opciones().size(); k++) {
                    if (k > 0) sb.append(',');
                    arr(sb, List.of(r.opciones().get(k).valor(), r.opciones().get(k).texto()));
                }
                sb.append(']');
            } else {
                sb.append("\"valor\":").append(Double.toString(r.valor())).append(',');
                campo(sb, "unidad", r.unidad()).append(',');
                sb.append("\"decimales\":").append(r.decimales());
            }
            sb.append('}');
        }
        return sb.append("]}").toString();
    }

    private static StringBuilder campo(StringBuilder sb, String k, String v) {
        return cadena(sb.append('"').append(k).append("\":"), v);
    }

    private static void arr(StringBuilder sb, List<String> l) {
        sb.append('[');
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append(',');
            cadena(sb, l.get(i));
        }
        sb.append(']');
    }

    private static StringBuilder cadena(StringBuilder sb, String s) {
        sb.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"');
    }
}
