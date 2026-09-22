package estudio;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** Lector JSON mínimo (objetos, arreglos, cadenas, números, booleanos y null). */
final class Json {
    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    static Object parsear(String texto) {
        Json j = new Json(texto);
        if (!texto.isEmpty() && texto.charAt(0) == '﻿') j.i = 1;
        Object v = j.valor();
        j.espacios();
        if (j.i != j.s.length()) throw j.error("contenido extra al final");
        return v;
    }

    private void espacios() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private char actual() {
        if (i >= s.length()) throw error("fin inesperado");
        return s.charAt(i);
    }

    private Object valor() {
        espacios();
        return switch (actual()) {
            case '{' -> objeto();
            case '[' -> arreglo();
            case '"' -> cadena();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> numero();
        };
    }

    private Object literal(String palabra, Object v) {
        if (!s.startsWith(palabra, i)) throw error("literal inválido");
        i += palabra.length();
        return v;
    }

    private Map<String, Object> objeto() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        espacios();
        if (actual() == '}') {
            i++;
            return m;
        }
        while (true) {
            espacios();
            String k = cadena();
            espacios();
            if (actual() != ':') throw error("se esperaba ':'");
            i++;
            m.put(k, valor());
            espacios();
            char c = actual();
            i++;
            if (c == '}') return m;
            if (c != ',') throw error("se esperaba ',' o '}'");
        }
    }

    private List<Object> arreglo() {
        List<Object> l = new ArrayList<>();
        i++;
        espacios();
        if (actual() == ']') {
            i++;
            return l;
        }
        while (true) {
            l.add(valor());
            espacios();
            char c = actual();
            i++;
            if (c == ']') return l;
            if (c != ',') throw error("se esperaba ',' o ']'");
        }
    }

    private String cadena() {
        if (actual() != '"') throw error("se esperaba una cadena");
        i++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = actual();
            i++;
            if (c == '"') return sb.toString();
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char e = actual();
            i++;
            switch (e) {
                case '"', '\\', '/' -> sb.append(e);
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (i + 4 > s.length()) throw error("escape unicode incompleto");
                    sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                }
                default -> throw error("escape inválido");
            }
        }
    }

    private Number numero() {
        int ini = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        String t = s.substring(ini, i);
        if (t.isEmpty()) throw error("valor inesperado '" + actual() + "'");
        try {
            if (t.contains(".") || t.contains("e") || t.contains("E")) return Double.parseDouble(t);
            return Long.parseLong(t);
        } catch (NumberFormatException ex) {
            throw error("número inválido");
        }
    }

    private RuntimeException error(String m) {
        return new IllegalArgumentException("JSON inválido (posición " + i + "): " + m);
    }
}
