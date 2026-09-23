package estudio;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

/** Guarda el avance de cada sesión (materia + tema) en la carpeta "progreso". */
final class Progreso {
    private Progreso() {}

    record Resumen(int dominadas, int total, boolean terminado, boolean existe, boolean completado,
                   boolean enExamen, int examenIndice) {
        double fraccion() {
            if (terminado) return 1;
            return total == 0 ? 0 : dominadas / (double) total;
        }

        /** Hay un examen final a medias que se puede retomar donde se dejó. */
        boolean examenEnCurso() {
            return enExamen && !terminado;
        }
    }

    /** Avance de una materia entera, sumando el temario completo y cada módulo por separado. */
    record ResumenMateria(int preguntas, int dominadas, int modulos, int modulosCompletados, boolean existe) {
        double fraccionModulos() {
            return modulos == 0 ? 0 : modulosCompletados / (double) modulos;
        }

        double fraccionPreguntas() {
            return preguntas == 0 ? 0 : dominadas / (double) preguntas;
        }

        boolean completa() {
            return modulos > 0 && modulosCompletados >= modulos;
        }
    }

    private static Path carpeta = Paths.get(System.getProperty("user.dir"), "progreso");

    static void iniciar(Path carpetaDatos) {
        Path padre = carpetaDatos.toAbsolutePath().getParent();
        if (padre != null) carpeta = padre.resolve("progreso");
    }

    static String clave(Materia m, String tema) {
        return m.clave + "__" + (tema == null ? "todo" : slug(tema));
    }

    private static String slug(String s) {
        String n = Evaluador.normalizar(s).replace(' ', '_');
        return n.isEmpty() ? "tema" : n;
    }

    private static Path archivo(String clave) {
        return carpeta.resolve(clave + ".properties");
    }

    private static Properties leer(Path ruta) {
        Properties p = new Properties();
        if (Files.isRegularFile(ruta)) {
            try (Reader r = Files.newBufferedReader(ruta, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException | IllegalArgumentException e) {
                p.clear();
            }
        }
        return p;
    }

    private static void escribir(Path ruta, Properties p) {
        try {
            Files.createDirectories(ruta.getParent());
            Path tmp = ruta.resolveSibling(ruta.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                p.store(w, "Estudio Activo");
            }
            Files.move(tmp, ruta, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("No se pudo guardar " + ruta + ": " + e.getMessage());
        }
    }

    /** {@code temarioCompleto} distingue el módulo "Todo el temario" (tema nulo) de un tema suelto. */
    static Motor cargar(String clave, int total, boolean temarioCompleto) {
        Properties p = leer(archivo(clave));
        return p.isEmpty() ? new Motor(total, temarioCompleto) : Motor.importar(p, total, temarioCompleto);
    }

    static void guardar(String clave, Motor m) {
        escribir(archivo(clave), m.exportar());
    }

    static void borrar(String clave) {
        try {
            Files.deleteIfExists(archivo(clave));
        } catch (IOException e) {
            System.err.println("No se pudo borrar el progreso: " + e.getMessage());
        }
    }

    static Resumen resumen(String clave, int total, boolean temarioCompleto) {
        boolean completado = completado(clave);
        Path ruta = archivo(clave);
        if (!Files.isRegularFile(ruta)) return new Resumen(0, total, false, false, completado, false, 0);
        Motor m = Motor.importar(leer(ruta), total, temarioCompleto);
        return new Resumen(m.dominadas(), total, m.terminado(), m.desbloqueadas() > 0, completado,
                m.enExamen(), m.examenIndice());
    }

    private static Path archivoCompletados() {
        return carpeta.resolve("completados.properties");
    }

    /**
     * Marca una sección como completada. El sello es permanente: reiniciar el progreso o volver a
     * estudiar no quita la etiqueta.
     */
    static void marcarCompletado(String clave) {
        Path ruta = archivoCompletados();
        Properties p = leer(ruta);
        if (p.getProperty(clave) != null) return;
        p.setProperty(clave, java.time.LocalDate.now().toString());
        escribir(ruta, p);
    }

    static boolean completado(String clave) {
        return leer(archivoCompletados()).getProperty(clave) != null;
    }

    /** Fecha en que se completó por primera vez (o null). */
    static String fechaCompletado(String clave) {
        return leer(archivoCompletados()).getProperty(clave);
    }

    /**
     * Recorre las sesiones de la materia (el temario completo y cada tema) y cuenta los módulos
     * completados y las preguntas dominadas en cualquiera de ellas.
     */
    static ResumenMateria resumenMateria(Materia m) {
        java.util.List<Pregunta> todas = m.preguntas;
        Properties sellos = leer(archivoCompletados());
        boolean[] dominadas = new boolean[todas.size()];
        boolean existe = false;

        String claveTodo = clave(m, null);
        boolean todoCompletado = sellos.getProperty(claveTodo) != null;
        Path rutaTodo = archivo(claveTodo);
        if (Files.isRegularFile(rutaTodo)) {
            Motor motor = Motor.importar(leer(rutaTodo), todas.size(), true);
            if (motor.desbloqueadas() > 0) existe = true;
            if (motor.terminado()) todoCompletado = true;
            for (int i = 0; i < todas.size(); i++) if (motor.dominada(i)) dominadas[i] = true;
        }

        java.util.List<String> temas = m.temas();
        int completados = 0;
        for (String tema : temas) {
            java.util.List<Integer> globales = new java.util.ArrayList<>();
            for (int i = 0; i < todas.size(); i++) if (todas.get(i).tema().equals(tema)) globales.add(i);
            String c = clave(m, tema);
            boolean completado = sellos.getProperty(c) != null;
            Path ruta = archivo(c);
            if (Files.isRegularFile(ruta)) {
                Motor motor = Motor.importar(leer(ruta), globales.size(), false);
                if (motor.desbloqueadas() > 0) existe = true;
                if (motor.terminado()) completado = true;
                for (int k = 0; k < globales.size(); k++) if (motor.dominada(k)) dominadas[globales.get(k)] = true;
            }
            if (completado || todoCompletado) {
                completados++;
                // Un modulo completado cuenta como sabido entero, aunque se aprobara sin practicar.
                for (int g : globales) dominadas[g] = true;
            }
        }
        if (todoCompletado) {
            existe = true;
            java.util.Arrays.fill(dominadas, true);
        }
        int n = 0;
        for (boolean b : dominadas) if (b) n++;
        return new ResumenMateria(todas.size(), n, temas.size(), completados, existe);
    }

    /** Última posición y tamaño de la ventana (null si no hay nada guardado o no cabe). */
    static java.awt.Rectangle ventanaGuardada() {
        Properties p = leer(carpeta.resolve("config.properties"));
        try {
            int x = Integer.parseInt(p.getProperty("ventana.x", ""));
            int y = Integer.parseInt(p.getProperty("ventana.y", ""));
            int w = Integer.parseInt(p.getProperty("ventana.ancho", ""));
            int h = Integer.parseInt(p.getProperty("ventana.alto", ""));
            if (w < 300 || h < 240) return null;
            return new java.awt.Rectangle(x, y, w, h);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean ventanaMaximizada() {
        return Boolean.parseBoolean(leer(carpeta.resolve("config.properties")).getProperty("ventana.maximizada", "false"));
    }

    static void guardarVentana(java.awt.Rectangle r, boolean maximizada) {
        Path ruta = carpeta.resolve("config.properties");
        Properties p = leer(ruta);
        if (r != null) {
            p.setProperty("ventana.x", String.valueOf(r.x));
            p.setProperty("ventana.y", String.valueOf(r.y));
            p.setProperty("ventana.ancho", String.valueOf(r.width));
            p.setProperty("ventana.alto", String.valueOf(r.height));
        }
        p.setProperty("ventana.maximizada", String.valueOf(maximizada));
        escribir(ruta, p);
    }

    static double zoomManual() {
        try {
            return Double.parseDouble(leer(carpeta.resolve("config.properties")).getProperty("zoom", "1"));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    static void guardarZoomManual(double z) {
        Path ruta = carpeta.resolve("config.properties");
        Properties p = leer(ruta);
        p.setProperty("zoom", String.valueOf(z));
        escribir(ruta, p);
    }

    static int limiteRepaso() {
        try {
            return Integer.parseInt(leer(carpeta.resolve("config.properties")).getProperty("limiteRepaso", "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static void guardarLimiteRepaso(int n) {
        Path ruta = carpeta.resolve("config.properties");
        Properties p = leer(ruta);
        p.setProperty("limiteRepaso", String.valueOf(n));
        escribir(ruta, p);
    }
}
