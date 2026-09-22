package estudio;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** Una materia = un archivo JSON = una sesión de estudio independiente. */
final class Materia {
    final String clave;
    final String nombre;
    final String descripcion;
    final List<Pregunta> preguntas;
    final Color acento1, acento2;
    final int icono;

    Materia(String clave, String nombre, String descripcion, List<Pregunta> preguntas, int indice) {
        this.clave = clave;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.preguntas = List.copyOf(preguntas);
        Color[] paleta = Estilo.PALETAS[indice % Estilo.PALETAS.length];
        this.acento1 = paleta[0];
        this.acento2 = paleta[1];
        this.icono = indice % 3;
    }

    List<String> temas() {
        LinkedHashSet<String> temas = new LinkedHashSet<>();
        for (Pregunta p : preguntas) temas.add(p.tema());
        return new ArrayList<>(temas);
    }

    /** Preguntas de un tema, o todas si {@code tema} es null. */
    List<Pregunta> preguntasDe(String tema) {
        if (tema == null) return preguntas;
        return preguntas.stream().filter(p -> p.tema().equals(tema)).toList();
    }

    static Path carpetaDatos() throws IOException {
        List<Path> candidatos = new ArrayList<>();
        candidatos.add(Paths.get(System.getProperty("user.dir"), "datos"));
        try {
            Path codigo = Paths.get(Materia.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path base = Files.isDirectory(codigo) ? codigo : codigo.getParent();
            int n = 0;
            for (Path p = base; p != null && n < 4; p = p.getParent(), n++) candidatos.add(p.resolve("datos"));
        } catch (Exception ignorada) {
            // Solo se usa la carpeta de trabajo.
        }
        for (Path c : candidatos) if (Files.isDirectory(c)) return c.toAbsolutePath();
        throw new IOException("No se encontró la carpeta 'datos' con los archivos JSON de preguntas.");
    }

    static List<Materia> cargarTodas(Path carpeta) throws IOException {
        List<Path> archivos;
        try (Stream<Path> st = Files.list(carpeta)) {
            archivos = st.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted().toList();
        }
        List<Materia> materias = new ArrayList<>();
        for (Path archivo : archivos) {
            String nombreArchivo = archivo.getFileName().toString();
            String clave = nombreArchivo.substring(0, nombreArchivo.length() - 5);
            Object raiz;
            try {
                raiz = Json.parsear(Files.readString(archivo, StandardCharsets.UTF_8));
            } catch (RuntimeException e) {
                throw new IOException("Error leyendo " + nombreArchivo + ": " + e.getMessage(), e);
            }
            if (!(raiz instanceof Map<?, ?> mapa)) continue;

            List<Pregunta> preguntas = new ArrayList<>();
            if (mapa.get("preguntas") instanceof List<?> lista) {
                for (Object o : lista) {
                    if (!(o instanceof Map<?, ?> q)) continue;
                    int id = q.get("id") instanceof Number num ? num.intValue() : preguntas.size() + 1;
                    String pregunta = texto(q.get("pregunta"));
                    String respuesta = texto(q.get("respuesta"));
                    if (pregunta.isBlank() || respuesta.isBlank()) continue;
                    String tema = texto(q.get("tema"));
                    preguntas.add(new Pregunta(id, tema.isBlank() ? "General" : tema, pregunta, respuesta));
                }
            }
            if (preguntas.isEmpty()) continue;

            String titulo = mapa.get("titulo") != null ? texto(mapa.get("titulo")) : clave;
            String nombre = titulo, descripcion = "";
            int guion = titulo.lastIndexOf(" - ");
            if (guion > 0) {
                nombre = titulo.substring(guion + 3);
                descripcion = titulo.substring(0, guion);
            }
            int parentesis = nombre.indexOf(" (");
            if (parentesis > 0 && nombre.endsWith(")")) {
                String extra = nombre.substring(parentesis + 2, nombre.length() - 1);
                nombre = nombre.substring(0, parentesis);
                descripcion = descripcion.isEmpty() ? extra : descripcion + " · " + extra;
            }
            materias.add(new Materia(clave, nombre.trim(), descripcion.trim(), preguntas, materias.size()));
        }
        return materias;
    }

    private static String texto(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
