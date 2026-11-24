package estudio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Generadores de problemas de Estudio de Tiempos (pasos 8 a 12).
 *
 * <p>Port exacto de {@code referencia/generadores.js}: mismo generador pseudoaleatorio, mismo orden de
 * llamadas, mismas fórmulas y mismo redondeo. Con la misma semilla produce el mismo problema, número a
 * número y texto a texto (lo comprueba {@code probar.bat}). La semilla 0 de T20 es el ejemplo de clase.</p>
 *
 * <p>Excepción a propósito: en T16, T19 y T20 los pasos se escriben como en las diapositivas (fracciones
 * apiladas y tablas) y T20 trae además el enunciado por secciones. Ahí cambia solo la presentación; los
 * números y las respuestas siguen siendo idénticos a los de JS.</p>
 */
final class Generadores {
    private Generadores() {}

    // ================================================================== modelo

    /** Un paso de la solución. {@code tabla} (opcional) se muestra con la sustitución, como en las diapositivas. */
    record Paso(String titulo, String formula, String sustitucion, String resultado, Tabla tabla) {
        Paso(String titulo, String formula, String sustitucion, String resultado) {
            this(titulo, formula, sustitucion, resultado, null);
        }
    }

    /** Parte del enunciado presentado como en las guías: un título opcional, un texto y/o una tabla. */
    record Seccion(String titulo, String texto, Tabla tabla) {}

    record Opcion(String valor, String texto) {}

    /** Respuesta calificable: numérica ({@code valor}) o de opción ({@code valorOpcion}). */
    record Respuesta(String clave, String etiqueta, boolean esOpcion, double valor, String valorOpcion,
                     String unidad, int decimales, List<Opcion> opciones) {
        /** Valor correcto listo para mostrarse. */
        String textoCorrecto() {
            if (esOpcion) {
                for (Opcion o : opciones) if (o.valor().equals(valorOpcion)) return o.texto();
                return valorOpcion;
            }
            String v = fmt(valor, decimales);
            return unidad.isEmpty() ? v : v + " " + unidad;
        }
    }

    record Tabla(List<String> columnas, List<List<String>> filas) {}

    record Problema(String tipo, String tema, String titulo, int dificultad, long semilla, String enunciado,
                    Tabla tabla, List<Paso> pasos, List<Respuesta> respuestas, List<Seccion> secciones) {}
    // secciones: enunciado como en las guías (tablas de datos y detalles); null si basta el texto y la tabla.

    /** Ficha del catálogo de tipos (campos de problemas_ordinario.json). */
    record Tipo(String id, String tema, String titulo, int dificultad, String datos, String queCambia,
                List<String> formulas) {
        /** "Paso 8", "Paso 10"… o "Caso integral". */
        String paso() {
            int g = tema.indexOf(" - ");
            return g > 0 ? tema.substring(0, g) : "Caso integral";
        }

        /** Parte del tema después del paso. */
        String subtema() {
            int g = tema.indexOf(" - ");
            return g > 0 ? tema.substring(g + 3) : tema;
        }

        int numeroPaso() {
            String p = paso();
            if (p.startsWith("Paso ")) {
                try {
                    return Integer.parseInt(p.substring(5).trim());
                } catch (NumberFormatException ignorada) {
                    return 99;
                }
            }
            return 99;
        }
    }

    private record Parcial(String enunciado, Tabla tabla, List<Paso> pasos, List<Respuesta> respuestas, List<Seccion> secciones) {
        Parcial(String enunciado, Tabla tabla, List<Paso> pasos, List<Respuesta> respuestas) {
            this(enunciado, tabla, pasos, respuestas, null);
        }
    }

    // ================================================================== utilidades numéricas (como en JS)

    private static final double EPSILON = Math.ulp(1.0); // Number.EPSILON

    static double rd(double x, int d) {
        double p = Math.pow(10, d);
        return Math.round((x + EPSILON) * p) / p;
    }

    static double r3(double x) {
        return rd(x, 3);
    }

    /** {@code Number(x).toLocaleString("en-US", {min y max fraction digits = d})}. */
    static String fmt(double x, int d) {
        BigDecimal b = new BigDecimal(x).setScale(d, RoundingMode.HALF_UP);
        String s = b.abs().toPlainString();
        String entero = s, dec = "";
        int punto = s.indexOf('.');
        if (punto >= 0) {
            entero = s.substring(0, punto);
            dec = s.substring(punto);
        }
        return (b.signum() < 0 ? "-" : "") + agrupar(entero) + dec;
    }

    static String fmt(double x) {
        return fmt(x, 3);
    }

    /** {@code Number(x).toLocaleString("en-US")} para enteros. */
    static String fi(double x) {
        long v = Math.round(x);
        return (v < 0 ? "-" : "") + agrupar(Long.toString(Math.abs(v)));
    }

    private static String agrupar(String digitos) {
        StringBuilder sb = new StringBuilder();
        int n = digitos.length();
        for (int i = 0; i < n; i++) {
            if (i > 0 && (n - i) % 3 == 0) sb.append(',');
            sb.append(digitos.charAt(i));
        }
        return sb.toString();
    }

    /** Conversión de número a texto de JavaScript ({@code `${x}`}): 3 → "3", 0.283 → "0.283". */
    static String js(double x) {
        if (x == Math.rint(x) && Math.abs(x) < 1e21) return Long.toString((long) x);
        return new BigDecimal(Double.toString(x)).stripTrailingZeros().toPlainString();
    }

    private static String lista(List<String> a) {
        if (a.size() < 2) return String.join("", a);
        return String.join(", ", a.subList(0, a.size() - 1)) + " y " + a.get(a.size() - 1);
    }

    // ================================================================== generador pseudoaleatorio

    /** mulberry32, idéntico a {@code prng(seed)} de JS. */
    private static final class Prng {
        private int a;

        Prng(long semilla) {
            a = (int) semilla; // seed >>> 0: solo cuentan los 32 bits bajos
        }

        double siguiente() {
            a += 0x6d2b79f5;
            int t = a;
            t = (t ^ (t >>> 15)) * (t | 1);
            t ^= t + (t ^ (t >>> 7)) * (t | 61);
            return ((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0;
        }
    }

    /** Las "herramientas" R del JS. */
    private static final class R {
        private final Prng rand;

        R(Prng rand) {
            this.rand = rand;
        }

        double u(double a, double b, int d) {
            return rd(a + (b - a) * rand.siguiente(), d);
        }

        double u(double a, double b) {
            return u(a, b, 3);
        }

        int i(int a, int b) {
            return a + (int) Math.floor(rand.siguiente() * (b - a + 1));
        }

        <T> T pick(List<T> arr) {
            return arr.get((int) Math.floor(rand.siguiente() * arr.size()));
        }

        int pick(int... arr) {
            return arr[(int) Math.floor(rand.siguiente() * arr.length)];
        }

        double pickD(double... arr) {
            return arr[(int) Math.floor(rand.siguiente() * arr.length)];
        }

        boolean bool(double p) {
            return rand.siguiente() < p;
        }

        <T> List<T> shuffle(List<T> arr) {
            List<T> c = new ArrayList<>(arr);
            for (int k = c.size() - 1; k > 0; k--) {
                int j = (int) Math.floor(rand.siguiente() * (k + 1));
                T tmp = c.get(k);
                c.set(k, c.get(j));
                c.set(j, tmp);
            }
            return c;
        }

        <T> List<T> sample(List<T> arr, int n) {
            return new ArrayList<>(shuffle(arr).subList(0, Math.min(n, arr.size())));
        }
    }

    // ================================================================== constructores

    private static Respuesta num(String clave, String etiqueta, double valor, String unidad, int decimales) {
        return new Respuesta(clave, etiqueta, false, valor, null, unidad, decimales, List.of());
    }

    private static Respuesta num(String clave, String etiqueta, double valor, String unidad) {
        return num(clave, etiqueta, valor, unidad, 3);
    }

    private static Respuesta opc(String clave, String etiqueta, String valor, List<Opcion> opciones) {
        return new Respuesta(clave, etiqueta, true, Double.NaN, valor, "", 0, opciones);
    }

    private static Paso paso(String titulo, String formula, String sustitucion, String resultado) {
        return new Paso(titulo, formula, sustitucion, resultado);
    }

    private static Paso paso(String titulo, String formula, String sustitucion, String resultado, Tabla tabla) {
        return new Paso(titulo, formula, sustitucion, resultado, tabla);
    }

    private static Tabla tabla(List<String> columnas, List<List<String>> filas) {
        return new Tabla(List.copyOf(columnas), filas.stream().map(List::copyOf).toList());
    }

    private static <T> String unir(List<T> xs, String sep, Function<T, String> f) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < xs.size(); k++) {
            if (k > 0) sb.append(sep);
            sb.append(f.apply(xs.get(k)));
        }
        return sb.toString();
    }

    private static String unirInts(int[] xs, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < xs.length; k++) {
            if (k > 0) sb.append(sep);
            sb.append(xs[k]);
        }
        return sb.toString();
    }

    // ================================================================== calificación

    private static final Pattern NUMERO_JS = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

    /** {@code Number(texto)} de JS para lo que puede escribir un alumno; null si no es un número finito. */
    private static Double numeroJs(String t) {
        t = t.trim();
        if (t.isEmpty()) return 0.0;
        if (!NUMERO_JS.matcher(t).matches()) return null;
        double v = Double.parseDouble(t);
        return Double.isFinite(v) ? v : null;
    }

    /**
     * Normaliza lo que escribe el alumno antes de calificar: quita espacios y, si hay una sola coma y
     * ningún punto, la toma como punto decimal ("0,25" → "0.25"). El símbolo % se conserva: lo quita
     * {@link #calificar}.
     */
    static String normalizarEntrada(String entrada) {
        if (entrada == null) return "";
        String s = entrada.replaceAll("\\s+", "");
        int comas = s.length() - s.replace(",", "").length();
        if (comas == 1 && s.indexOf('.') < 0) s = s.replace(',', '.');
        return s;
    }

    /** Valor numérico de una entrada ya normalizada, como lo interpreta {@link #calificar}. */
    static Double aNumero(String entrada) {
        String s = normalizarEntrada(entrada);
        if (s.isEmpty()) return null;
        return numeroJs(s.replace(",", "").replaceFirst("%", ""));
    }

    /** Tolerancia: 1.5 % relativo o 0.006 absoluto, lo que sea mayor. Igual que {@code calificar} de JS. */
    static boolean calificar(Respuesta r, String entrada) {
        if (r.esOpcion()) return String.valueOf(entrada).trim().equals(r.valorOpcion());
        Double x = numeroJs(String.valueOf(entrada).replace(",", "").replaceFirst("%", ""));
        if (x == null) return false;
        double tol = Math.max(Math.abs(r.valor()) * 0.015, 0.006);
        return Math.abs(x - r.valor()) <= tol;
    }

    /** Lo que usa la interfaz: normaliza y considera vacía una respuesta sin escribir. */
    static boolean calificarEntrada(Respuesta r, String entrada) {
        if (entrada == null || entrada.isBlank()) return false;
        return calificar(r, r.esOpcion() ? entrada : normalizarEntrada(entrada));
    }

    // ================================================================== contextos

    private record Ctx(String lugar, String producto, String pieza, String herr, String herrS, String art,
                       String un, String revisar, double[] masa, int[] vel, int[] lote) {
        String articulo() { return art != null ? art : "los"; }
        String uno() { return un != null ? un : "un"; }
    }

    private static final List<Ctx> CONTEXTOS = List.of(
            new Ctx("un laboratorio farmacéutico de Monterrey", "tabletas", "tableta", "punzones", "punzón", null, null,
                    "Revisar consistencia", new double[]{0.2, 0.6}, new int[]{1500, 2000, 2500, 3000},
                    new int[]{60000, 80000, 100000, 120000, 150000}),
            new Ctx("una planta de galletas en Santa Catarina", "galletas", "galleta", "moldes", "molde", null, null,
                    "Revisar el horneado", new double[]{8, 15}, new int[]{300, 400, 500, 600},
                    new int[]{12000, 15000, 18000, 20000, 24000}),
            new Ctx("una planta de inyección de plástico en Apodaca", "tapas", "tapa", "boquillas", "boquilla", "las", "una",
                    "Revisar rebabas", new double[]{2, 5}, new int[]{400, 500, 600, 800},
                    new int[]{20000, 25000, 30000, 40000}),
            new Ctx("una fábrica de tornillos en Escobedo", "tornillos", "tornillo", "dados", "dado", null, null,
                    "Revisar la rosca", new double[]{3, 10}, new int[]{250, 300, 400, 500},
                    new int[]{10000, 12000, 15000, 20000}),
            new Ctx("una fábrica de dulces en Guadalupe", "caramelos", "caramelo", "troqueles", "troquel", null, null,
                    "Revisar el empaque", new double[]{4, 8}, new int[]{600, 800, 1000, 1200},
                    new int[]{30000, 40000, 50000, 60000}),
            new Ctx("una embotelladora en San Nicolás de los Garza", "botellas", "botella", "válvulas de llenado", "válvula",
                    "las", "una", "Revisar el nivel de llenado", new double[]{500, 600}, new int[]{100, 120, 150, 200},
                    new int[]{4000, 5000, 6000, 8000}));

    private static final List<String> NOMBRES_ELEM = List.of("Tomar pieza", "Colocar en el dispositivo",
            "Ajustar sujetador", "Retirar pieza", "Inspeccionar medida", "Llevar a la banda", "Llenar estantería",
            "Llevar carro al puesto", "Etiquetar caja", "Cerrar caja", "Limpiar área", "Acomodar en tarima");
    private static final List<Integer> CALIFS = List.of(80, 85, 90, 95, 100, 105, 110, 115);

    // ================================================================== observaciones (paso 8)

    private record Fila(int C, int F, int[] obs) {}

    private record Det(int C, int F, int[] obs, int suma, double TO, double MN) {}

    private record MNvez(List<Det> det, double sMN, int sF, double mnvez) {}

    private static List<Fila> observaciones(R R, double base, int nCal) {
        List<Integer> cals = R.sample(CALIFS, nCal);
        Collections.sort(cals);
        List<Fila> filas = new ArrayList<>();
        for (int C : cals) {
            int F = R.i(1, 6);
            int[] obs = new int[F];
            for (int k = 0; k < F; k++) {
                obs[k] = (int) Math.max(2, Math.round(((base * 10000) / C) * (0.94 + 0.12 * R.u(0, 1, 4))));
            }
            filas.add(new Fila(C, F, obs));
        }
        return filas;
    }

    private static MNvez resolverMNvez(List<Fila> filas) {
        List<Det> det = new ArrayList<>();
        for (Fila f : filas) {
            int suma = 0;
            for (int o : f.obs()) suma += o;
            double TO = rd(suma / 100.0, 2);
            double MN = r3((TO * f.C()) / 100);
            det.add(new Det(f.C(), f.F(), f.obs(), suma, TO, MN));
        }
        double s = 0;
        int sF = 0;
        for (Det d : det) {
            s += d.MN();
            sF += d.F();
        }
        double sMN = r3(s);
        return new MNvez(det, sMN, sF, r3(sMN / sF));
    }

    // ================================================================== catálogo

    static final List<Tipo> TIPOS = List.of(
            new Tipo("T01", "Paso 8 - Minutos normales por vez", "MN por vez a partir de calificaciones", 1,
                    "Lecturas en cmin por calificación de un elemento",
                    "Cambian el nombre del elemento, de 2 a 4 calificaciones entre 80 y 115, de 1 a 6 lecturas por calificación y el tiempo base",
                    List.of("TO(min) = Σ lecturas / 100", "MN = TO(min) × C / 100", "MN/vez = ΣMN / ΣF")),
            new Tipo("T02", "Paso 8 - Minutos normales por unidad", "MN por unidad, varias veces por ciclo", 1,
                    "MN/vez y veces que aparece en un ciclo",
                    "Cambian MN/vez de 0.05 a 2.5 y las veces por ciclo de 2 a 12",
                    List.of("MN/unidad = MN/vez × VEZ/unidad")),
            new Tipo("T03", "Paso 8 - Minutos normales por unidad", "MN por unidad, una vez cada n ciclos", 1,
                    "MN/vez y una o varias veces cada n ciclos",
                    "Cambian MN/vez, las veces (1 a 3) y los ciclos (2 a 12)",
                    List.of("VEZ/unidad = veces / ciclos", "MN/unidad = MN/vez × VEZ/unidad")),
            new Tipo("T04", "Paso 8 - Minutos normales por unidad", "Hoja completa de MN por vez y por unidad", 2,
                    "Hoja completa con 2 o 3 elementos, lecturas por calificación y frecuencia",
                    "Cambian elementos, calificaciones, lecturas y si aparecen varias veces por ciclo o una vez cada n ciclos",
                    List.of("MN = TO × C/100", "MN/vez = ΣMN/ΣF", "MN/unidad = MN/vez × VEZ/unidad")),
            new Tipo("T05", "Paso 10 - Veces por unidad", "Veces por unidad de una tolva", 2,
                    "Tolva con kg por carga, gramos por pieza y piezas por lote",
                    "Cambian el contexto (6 industrias), kg, gramos y tamaño de lote",
                    List.of("kg/unidad = piezas × g / 1000", "VEZ/unidad = kg/unidad / kg por carga")),
            new Tipo("T06", "Paso 10 - Veces por unidad", "Veces por unidad de un reemplazo de herramienta", 1,
                    "Reemplazo y limpieza de n herramientas cada k unidades",
                    "Cambian contexto, número de herramientas (4 a 40), unidades entre reemplazos (2 a 12) y MN/vez",
                    List.of("VEZ/unidad = n / k", "MN/unidad = MN/vez × VEZ/unidad")),
            new Tipo("T07", "Paso 9 - Misceláneos", "Asignación de misceláneos", 1,
                    "Elementos normalizados y porcentaje de misceláneos",
                    "Cambian de 3 a 6 elementos, sus tiempos y el porcentaje de 1 a 5",
                    List.of("Misc = % × Σ", "Total = Σ + Misc")),
            new Tipo("T08", "Paso 10 - Tiempo de máquina", "Tiempo condicionado por la máquina", 1,
                    "Velocidad de la máquina y piezas por lote",
                    "Cambian contexto, lote y velocidad, a veces en piezas por hora",
                    List.of("unidades/min = velocidad / lote", "TM = 1 / (unidades/min)")),
            new Tipo("T09", "Paso 10 - ETE, ETI y TNO", "Trabajo exterior, interior y tiempo no ocupado", 2,
                    "Elementos con máquina detenida o en marcha, misceláneos y TM",
                    "Cambian cuáles elementos son interiores o exteriores, sus tiempos, el % de misceláneos y TM",
                    List.of("ETE = Σ ext × (1 + %)", "ETI = Σ int × (1 + %)", "TNO = TM − ETI", "TC = ETE + TM")),
            new Tipo("T10", "Paso 11 - Suplementos por necesidades personales", "Tiempo ciclo y SNP", 1,
                    "ETE, TM y porcentaje de necesidades personales",
                    "Cambian ETE, TM y el porcentaje de 5 a 7",
                    List.of("TC = ETE + TM", "SNP = % × TC")),
            new Tipo("T11", "Paso 11 - Suplementos por fatiga", "Tabla de suplementos por fatiga", 1,
                    "Tabla de fatiga por elemento",
                    "Cambian de 3 a 5 elementos más misceláneos y porcentajes de 4 a 35",
                    List.of("SF = Σ MN/unidad × %")),
            new Tipo("T12", "Paso 11 - Asignación en ciclos breves", "Tiempo efectivamente utilizable para descanso", 2,
                    "Ciclo breve con TNO y SF",
                    "Cambia el TNO para caer en los tres casos (< 0.5, entre 0.5 y 1.5, ≥ 1.5)",
                    List.of("TED = (TNO − 0.5) × 1.5")),
            new Tipo("T13", "Paso 11 - Asignación en ciclos largos", "¿Los suplementos van dentro o fuera del TNO?", 2,
                    "Ciclo largo con TNO, SNP, SF y condiciones",
                    "Cambian TNO, SNP, SF, si el TNO es ininterrumpido y si se puede dejar la máquina",
                    List.of("SNP dentro solo con las tres condiciones", "SF dentro si TNO > SF")),
            new Tipo("T14", "Paso 12 - Tiempo tipo", "Tiempo tipo con máquina", 2,
                    "ETE, ETI, TM, % de SNP, SF y condiciones",
                    "Cambian todos los datos y las condiciones de asignación",
                    List.of("TNO = TM − ETI", "SNP = % (ETE + TM)", "TT = ETE + TM + suplementos fuera")),
            new Tipo("T15", "Paso 9 - Tiempo tipo", "Tiempo tipo de un trabajo manual", 1,
                    "Trabajo manual con elementos, misceláneos y suplementos en %",
                    "Cambian elementos y porcentajes",
                    List.of("TT = elementos + misceláneos + suplementos")),
            new Tipo("T16", "Paso 12 - Producción esperada", "Producción esperada por turno", 2,
                    "TT y datos de jornada",
                    "Cambian horas por turno, turnos, días, limpieza, cambio de ropa, comida y TT",
                    List.of("Limpieza/turno = h × veces / días / turnos × 60", "Tiempo real = min turno − suplementos de jornada",
                            "Producción = tiempo real / TT")),
            new Tipo("T17", "Paso 12 - Carga de trabajo", "Carga de trabajo y número de máquinas", 1,
                    "ETE, ETI y TM", "Cambian los tres tiempos",
                    List.of("Carga = (ETE + ETI) / (ETE + TM)", "N = 1 / carga")),
            new Tipo("T18", "Paso 8 - Minutos normales por unidad", "Problema inverso de veces por unidad", 1,
                    "MN/vez y MN/unidad",
                    "Cambia si el elemento aparece varias veces por ciclo o una vez cada n ciclos",
                    List.of("VEZ/unidad = MN/unidad / MN/vez")),
            new Tipo("T19", "Paso 12 - Producción esperada", "Tiempo tipo máximo para cumplir una meta", 2,
                    "Meta de producción y datos de jornada", "Cambian meta, jornada y TT actual",
                    List.of("TT máx = tiempo real / meta")),
            new Tipo("T20", "Caso integral pasos 8 a 12", "Caso integral de trabajo con máquina", 3,
                    "Caso completo tipo laboratorio farmacéutico",
                    "Cambian industria, tiempos, tolva, herramientas, velocidad, lote, porcentajes, condiciones y jornada. Semilla 0 es el ejemplo de clase",
                    List.of("Todas las anteriores")));

    static Tipo tipo(String id) {
        for (Tipo t : TIPOS) if (t.id().equals(id)) return t;
        throw new IllegalArgumentException("Tipo desconocido " + id);
    }

    // ================================================================== tipos

    private static Parcial t01(R R) {
        String nombre = R.pick(NOMBRES_ELEM);
        double base = R.u(0.08, 0.6, 2);
        List<Fila> filas = observaciones(R, base, R.i(2, 4));
        MNvez s = resolverMNvez(filas);
        String lst = unir(filas, ". ", f -> "C = " + f.C() + " con lecturas de " + unirInts(f.obs(), ", ") + " cmin");
        List<Paso> pasos = new ArrayList<>();
        for (Det d : s.det()) {
            pasos.add(paso("Calificación " + d.C(), "TO(min) = Σ lecturas / 100, MN = TO(min) × C / 100",
                    "TO = " + d.suma() + " / 100 = " + fmt(d.TO(), 2) + ", MN = " + fmt(d.TO(), 2) + " × " + d.C() + " / 100",
                    "MN = " + fmt(d.MN()) + " min"));
        }
        pasos.add(paso("Sumatorias", "ΣMN y ΣF",
                "ΣMN = " + unir(s.det(), " + ", d -> fmt(d.MN())) + ", ΣF = " + unir(s.det(), " + ", d -> String.valueOf(d.F())),
                "ΣMN = " + fmt(s.sMN()) + ", ΣF = " + s.sF()));
        pasos.add(paso("MN por vez", "MN/vez = ΣMN / ΣF", fmt(s.sMN()) + " / " + s.sF(), fmt(s.mnvez()) + " min"));
        List<List<String>> tf = new ArrayList<>();
        for (Fila f : filas) tf.add(List.of(String.valueOf(f.C()), unirInts(f.obs(), ", "), String.valueOf(f.F())));
        return new Parcial("En el estudio de tiempos del elemento \"" + nombre + "\" se registraron las siguientes lecturas en "
                + "centiminutos agrupadas por calificación. " + lst + ". Calcula el tiempo observado en minutos y los minutos "
                + "normales de cada calificación, la sumatoria de minutos normales, la frecuencia total y los minutos normales por vez.",
                tabla(List.of("C", "Lecturas (cmin)", "F"), tf), pasos,
                List.of(num("sMN", "Sumatoria de MN", s.sMN(), "min"), num("sF", "Frecuencia total", s.sF(), "", 0),
                        num("mnvez", "MN por vez", s.mnvez(), "min/vez")));
    }

    private static Parcial t02(R R) {
        double mn = R.u(0.05, 2.5, 2);
        int k = R.i(2, 12);
        String nombre = R.pick(NOMBRES_ELEM);
        double v = r3(mn * k);
        return new Parcial("El elemento \"" + nombre + "\" tiene MN/vez = " + fmt(mn, 2) + " y aparece " + k
                + " veces en cada ciclo de trabajo. ¿Cuál es su tiempo en MN por unidad?", null,
                List.of(paso("Veces por unidad", "VEZ/UNIDAD = veces que aparece / unidades", k + " / 1", k + " vez/unidad"),
                        paso("MN por unidad", "MN/unidad = MN/vez × VEZ/unidad", fmt(mn, 2) + " × " + k, fmt(v) + " min/unidad")),
                List.of(num("vu", "Veces por unidad", k, "vez/unidad", 3), num("mnu", "MN por unidad", v, "min/unidad")));
    }

    private static Parcial t03(R R) {
        double mn = R.u(0.05, 3, 2);
        int m = R.pick(1, 1, 1, 2, 3);
        int n = R.i(2, 12);
        if (n <= m) n = m + R.i(1, 6);
        double vu = r3((double) m / n), v = r3((mn * m) / n);
        String nombre = R.pick(NOMBRES_ELEM);
        return new Parcial("El elemento \"" + nombre + "\" tiene MN/vez = " + fmt(mn, 2) + " y aparece " + m + " "
                + (m == 1 ? "vez" : "veces") + " cada " + n + " ciclos de trabajo. ¿Cuál es su tiempo en MN por unidad?", null,
                List.of(paso("Veces por unidad", "VEZ/UNIDAD = veces / unidades requeridas", m + " / " + n, fmt(vu) + " vez/unidad"),
                        paso("MN por unidad", "MN/unidad = MN/vez × VEZ/unidad", fmt(mn, 2) + " × " + m + " / " + n, fmt(v) + " min/unidad")),
                List.of(num("vu", "Veces por unidad", vu, "vez/unidad"), num("mnu", "MN por unidad", v, "min/unidad")));
    }

    private static Parcial t04(R R) {
        int nE = R.i(2, 3);
        List<String> nombres = R.sample(NOMBRES_ELEM, nE);
        record El(String nombre, List<Fila> filas, MNvez s, int k, int n, double vu, double mnu, String frase) {}
        List<El> elems = new ArrayList<>();
        for (String nombre : nombres) {
            double base = R.u(0.05, 0.5, 2);
            List<Fila> filas = observaciones(R, base, R.i(2, 4));
            MNvez s = resolverMNvez(filas);
            boolean porCiclo = R.bool(0.5);
            int k = porCiclo ? R.i(1, 10) : 1;
            int n = porCiclo ? 1 : R.i(2, 8);
            double vu = r3((double) k / n), mnu = r3((s.mnvez() * k) / n);
            elems.add(new El(nombre, filas, s, k, n, vu, mnu,
                    porCiclo ? k + " " + (k == 1 ? "vez" : "veces") + " por ciclo" : "1 vez cada " + n + " ciclos"));
        }
        double t = 0;
        for (El e : elems) t += e.mnu();
        double total = r3(t);
        List<Paso> pasos = new ArrayList<>();
        List<Respuesta> resp = new ArrayList<>();
        for (int idx = 0; idx < elems.size(); idx++) {
            El e = elems.get(idx);
            pasos.add(paso(e.nombre() + ", MN por calificación", "MN = TO(min) × C / 100",
                    unir(e.s().det(), ", ", d -> fmt(d.TO(), 2) + " × " + d.C() + "/100 = " + fmt(d.MN())),
                    "ΣMN = " + fmt(e.s().sMN()) + ", ΣF = " + e.s().sF()));
            pasos.add(paso(e.nombre() + ", MN/vez y MN/unidad", "MN/vez = ΣMN/ΣF, MN/unidad = MN/vez × VEZ/unidad",
                    fmt(e.s().sMN()) + " / " + e.s().sF() + " = " + fmt(e.s().mnvez()) + ", " + fmt(e.s().mnvez()) + " × " + e.k() + "/" + e.n(),
                    fmt(e.mnu()) + " min/unidad"));
            resp.add(num("mnvez" + (idx + 1), "MN/vez de " + e.nombre(), e.s().mnvez(), "min/vez"));
            resp.add(num("mnu" + (idx + 1), "MN/unidad de " + e.nombre(), e.mnu(), "min/unidad"));
        }
        pasos.add(paso("Total", "Σ MN/unidad", unir(elems, " + ", e -> fmt(e.mnu())), fmt(total) + " min/unidad"));
        resp.add(num("total", "Total MN/unidad", total, "min/unidad"));
        List<List<String>> tf = new ArrayList<>();
        for (El e : elems) {
            for (int j = 0; j < e.filas().size(); j++) {
                Fila f = e.filas().get(j);
                tf.add(List.of(j == 0 ? e.nombre() : "", j == 0 ? e.frase() : "", String.valueOf(f.C()), unirInts(f.obs(), ", ")));
            }
        }
        String detalle = unir(elems, ". ", e -> e.nombre() + " (" + e.frase() + "), "
                + unir(e.filas(), ". ", f -> "C " + f.C() + " = " + unirInts(f.obs(), ", ")));
        return new Parcial("Hoja de minutos normales. Para cada elemento se dan las lecturas en centiminutos por calificación "
                + "y su frecuencia en el ciclo. " + detalle + ". Calcula MN/vez y MN/unidad de cada elemento y el total por unidad.",
                tabla(List.of("Elemento", "Frecuencia", "C", "Lecturas (cmin)"), tf), pasos, resp);
    }

    private static Parcial t05(R R) {
        for (;;) {
            Ctx c = R.pick(CONTEXTOS);
            int kg = R.pick(10, 15, 20, 25, 30, 40, 50);
            double g = R.u(c.masa()[0], c.masa()[1], c.masa()[1] < 1 ? 3 : 1);
            int lote = R.pick(c.lote());
            double totalKg = r3((lote * g) / 1000), vu = r3(totalKg / kg);
            if (vu < 0.3 || vu > 8) continue;
            return new Parcial("En " + c.lugar() + " la tolva de la máquina se alimenta con " + kg + " kg cada vez que se "
                    + "consume la materia prima. Cada " + c.pieza() + " pesa aproximadamente " + js(g) + " g y cada unidad de "
                    + "producción (lote) es de " + fi(lote) + " " + c.producto() + ". ¿Cuántas veces por unidad se alimenta la tolva?", null,
                    List.of(paso("Materia prima por lote", "kg/unidad = piezas × g/pieza × (1 kg / 1000 g)", fi(lote) + " × " + js(g) + " / 1000",
                                    fmt(totalKg) + " kg/unidad"),
                            paso("Veces por unidad", "VEZ/unidad = (kg/unidad) / (kg/vez)", fmt(totalKg) + " / " + kg, fmt(vu) + " vez/unidad")),
                    List.of(num("kg", "Materia prima por lote", totalKg, "kg"), num("vu", "Veces por unidad", vu, "vez/unidad")));
        }
    }

    private static Parcial t06(R R) {
        Ctx c = R.pick(CONTEXTOS);
        int n = R.i(4, 40), k = R.i(2, 12);
        double mR = R.u(2, 12, 3), mL = R.u(1, 6, 3), vu = r3((double) n / k);
        double a = r3(mR * vu), b = r3(mL * vu);
        return new Parcial("En " + c.lugar() + " cada " + k + " unidades productivas se reemplazan y limpian " + c.articulo() + " "
                + n + " " + c.herr() + " de la máquina. Reemplazar " + c.uno() + " " + c.herrS() + " toma " + fmt(mR)
                + " MN/vez y limpiarlo " + fmt(mL) + " MN/vez. Calcula las veces por unidad y los MN por unidad de ambos elementos.", null,
                List.of(paso("Veces por unidad", "VEZ/unidad = veces / unidades", n + " / " + k, fmt(vu) + " vez/unidad"),
                        paso("Reemplazar", "MN/unidad = MN/vez × VEZ/unidad", fmt(mR) + " × " + fmt(vu), fmt(a) + " min/unidad"),
                        paso("Limpiar", "MN/unidad = MN/vez × VEZ/unidad", fmt(mL) + " × " + fmt(vu), fmt(b) + " min/unidad")),
                List.of(num("vu", "Veces por unidad", vu, "vez/unidad"), num("rep", "MN/unidad de reemplazar", a, "min/unidad"),
                        num("lim", "MN/unidad de limpiar", b, "min/unidad")));
    }

    private static Parcial t07(R R) {
        int n = R.i(3, 6);
        List<String> nombres = R.sample(NOMBRES_ELEM, n);
        int p = R.i(1, 5);
        List<Double> vals = new ArrayList<>();
        for (int k = 0; k < nombres.size(); k++) vals.add(R.u(0.3, 25, 3));
        double s = 0;
        for (double v : vals) s += v;
        double tot = r3(s), misc = r3((tot * p) / 100), con = r3(tot + misc);
        StringBuilder el = new StringBuilder();
        List<List<String>> tf = new ArrayList<>();
        for (int k = 0; k < nombres.size(); k++) {
            if (k > 0) el.append(", ");
            el.append(nombres.get(k)).append(' ').append(fmt(vals.get(k))).append(" MN/unidad");
            tf.add(List.of(nombres.get(k), fmt(vals.get(k))));
        }
        return new Parcial("Los elementos normalizados de una operación son " + el + ". La empresa asigna " + p
                + " % por misceláneos. Calcula el total de elementos, el tiempo de misceláneos y el total con misceláneos.",
                tabla(List.of("Elemento", "MN/unidad"), tf),
                List.of(paso("Total de elementos", "Σ MN/unidad", unir(vals, " + ", v -> fmt(v)), fmt(tot) + " min/unidad"),
                        paso("Misceláneos", "Misc = % × Σ elementos", p + "% × " + fmt(tot), fmt(misc) + " min/unidad"),
                        paso("Total con misceláneos", "Σ + Misc", fmt(tot) + " + " + fmt(misc), fmt(con) + " min/unidad")),
                List.of(num("tot", "Total de elementos", tot, "min/unidad"), num("misc", "Misceláneos", misc, "min/unidad"),
                        num("con", "Total con misceláneos", con, "min/unidad")));
    }

    private static Parcial t08(R R) {
        Ctx c = R.pick(CONTEXTOS);
        int lote = R.pick(c.lote());
        boolean porHora = R.bool(0.3);
        int velMin = R.pick(c.vel());
        int vel = porHora ? velMin * 60 : velMin;
        double frac = rd((double) velMin / lote, 5), TM = r3((double) lote / velMin);
        List<Paso> pasos = new ArrayList<>();
        if (porHora) pasos.add(paso("Velocidad por minuto", "piezas/min = piezas/h / 60", fi(vel) + " / 60", fi(velMin) + " " + c.producto() + "/min"));
        pasos.add(paso("Fracción del lote por minuto", "unidades/min = piezas/min × (1 unidad / piezas por lote)",
                fi(velMin) + " / " + fi(lote), js(frac) + " unidades/min (" + js(rd(frac * 100, 3)) + " % del lote)"));
        pasos.add(paso("Tiempo de máquina", "TM = 1 / (unidades/min)", "1 / " + js(frac), fmt(TM) + " min/unidad"));
        return new Parcial("En " + c.lugar() + " la velocidad de entrega de la máquina es de " + fi(vel) + " " + c.producto()
                + " por " + (porHora ? "hora" : "minuto") + " y cada ciclo (unidad) produce " + fi(lote) + " " + c.producto()
                + ". Calcula la fracción del lote que se produce en un minuto y el tiempo condicionado por la máquina (TM).", null, pasos,
                List.of(num("frac", "Unidades por minuto", frac, "unidad/min", 5), num("TM", "Tiempo de máquina", TM, "min/unidad")));
    }

    private record ElMaq(String nombre, String tipo, double mnu) {
        boolean exterior() { return tipo.equals("exterior"); }
    }

    private static List<ElMaq> elementosMaquina(R R, Ctx c, int nInt) {
        List<String> base = List.of("Alimentar tolva", c.revisar(), "Llevar " + c.producto() + " al siguiente proceso",
                "Limpiar " + c.herr(), "Etiquetar contenedor", "Registrar producción");
        List<ElMaq> todos = new ArrayList<>();
        for (String nombre : R.sample(base, nInt)) todos.add(new ElMaq(nombre, "interior", R.u(0.4, 18, 3)));
        todos.add(new ElMaq("Reemplazar " + c.herr(), "exterior", R.u(8, 40, 3)));
        if (R.bool(0.35)) todos.add(new ElMaq("Ajustar la máquina", "exterior", R.u(1, 6, 3)));
        return R.shuffle(todos);
    }

    private static Parcial t09(R R) {
        for (;;) {
            Ctx c = R.pick(CONTEXTOS);
            List<ElMaq> els = elementosMaquina(R, c, R.i(2, 4));
            int p = R.i(1, 5);
            double se = 0, si = 0;
            for (ElMaq e : els) if (e.exterior()) se += e.mnu();
            for (ElMaq e : els) if (!e.exterior()) si += e.mnu();
            double sE = r3(se), sI = r3(si);
            double ETE = r3(sE + (sE * p) / 100), ETI = r3(sI + (sI * p) / 100);
            int TM = R.pick(30, 40, 45, 50, 60, 75, 80, 90, 100, 120);
            if (ETI >= TM - 1) continue;
            double TNO = r3(TM - ETI), TC = r3(ETE + TM);
            List<List<String>> tf = new ArrayList<>();
            for (ElMaq e : els) tf.add(List.of(e.nombre(), fmt(e.mnu()), e.exterior() ? "Detenida" : "En marcha"));
            return new Parcial("En " + c.lugar() + " se tienen los siguientes elementos en MN/unidad, "
                    + unir(els, ", ", e -> e.nombre() + " " + fmt(e.mnu()) + " (" + (e.exterior() ? "con la máquina detenida" : "con la máquina en marcha") + ")")
                    + ". Se asigna " + p + " % de misceláneos a cada grupo y el tiempo de máquina es TM = " + TM
                    + " min/unidad. Calcula ETE, ETI, TNO y el tiempo ciclo.",
                    tabla(List.of("Elemento", "MN/unidad", "Condición de la máquina"), tf),
                    List.of(paso("Trabajo exterior", "ETE = Σ elementos con máquina detenida × (1 + %misc)", fmt(sE) + " + " + p + "%(" + fmt(sE) + ")", fmt(ETE) + " min/unidad"),
                            paso("Trabajo interior", "ETI = Σ elementos con máquina en marcha × (1 + %misc)", fmt(sI) + " + " + p + "%(" + fmt(sI) + ")", fmt(ETI) + " min/unidad"),
                            paso("Tiempo no ocupado", "TNO = TM − ETI", TM + " − " + fmt(ETI), fmt(TNO) + " min/unidad"),
                            paso("Tiempo ciclo", "TC = ETE + TM", fmt(ETE) + " + " + TM, fmt(TC) + " min/unidad")),
                    List.of(num("ETE", "ETE", ETE, "min/unidad"), num("ETI", "ETI", ETI, "min/unidad"),
                            num("TNO", "TNO", TNO, "min/unidad"), num("TC", "Tiempo ciclo", TC, "min/unidad")));
        }
    }

    private static Parcial t10(R R) {
        double ETE = R.u(5, 45, 3);
        int TM = R.pick(30, 40, 50, 60, 75, 90), p = R.i(5, 7);
        double TC = r3(ETE + TM), SNP = r3((TC * p) / 100);
        return new Parcial("Una operación con máquina tiene ETE = " + fmt(ETE) + " min/unidad y TM = " + TM + " min/unidad. "
                + "La empresa da " + p + " % del tiempo del ciclo para necesidades personales. Calcula el tiempo ciclo y el SNP.", null,
                List.of(paso("Tiempo ciclo", "TC = ETE + TM = ETE + ETI + TNO", fmt(ETE) + " + " + TM, fmt(TC) + " min/unidad"),
                        paso("SNP", "SNP = % × TC", p + "% × " + fmt(TC), fmt(SNP) + " min/unidad")),
                List.of(num("TC", "Tiempo ciclo", TC, "min/unidad"), num("SNP", "SNP", SNP, "min/unidad")));
    }

    private static Parcial t11(R R) {
        int n = R.i(3, 5);
        List<String> nombres = new ArrayList<>(R.sample(NOMBRES_ELEM, n));
        nombres.add("Misceláneos");
        record F(String x, double mnu, int pct, double sf) {}
        List<F> filas = new ArrayList<>();
        for (int i = 0; i < nombres.size(); i++) {
            double mnu = i == n ? R.u(0.2, 2, 3) : R.u(0.5, 35, 3);
            int pct = R.i(4, 35);
            filas.add(new F(nombres.get(i), mnu, pct, r3((mnu * pct) / 100)));
        }
        double s = 0;
        for (F f : filas) s += f.sf();
        double SF = r3(s);
        List<Paso> pasos = new ArrayList<>();
        for (F f : filas) pasos.add(paso(f.x(), "MN/unidad × %", fmt(f.mnu()) + " × " + f.pct() + "%", fmt(f.sf()) + " min/unidad"));
        pasos.add(paso("SF total", "SF = Σ suplementos", unir(filas, " + ", f -> fmt(f.sf())), fmt(SF) + " min/unidad"));
        List<Respuesta> resp = new ArrayList<>();
        for (int i = 0; i < filas.size(); i++) resp.add(num("sf" + (i + 1), "Suplemento de " + filas.get(i).x(), filas.get(i).sf(), "min/unidad"));
        resp.add(num("SF", "SF total", SF, "min/unidad"));
        List<List<String>> tf = new ArrayList<>();
        for (F f : filas) tf.add(List.of(f.x(), fmt(f.mnu()), String.valueOf(f.pct())));
        return new Parcial("De acuerdo con las tablas de la empresa, los porcentajes de suplemento por fatiga son "
                + unir(filas, ", ", f -> f.x() + " " + fmt(f.mnu()) + " MN/unidad con " + f.pct() + " %")
                + ". Calcula el suplemento de cada elemento y el SF total.",
                tabla(List.of("Elemento", "MN/unidad", "% de suplemento"), tf), pasos, resp);
    }

    static double utilizable(double TNO) {
        if (TNO < 0.5) return 0;
        if (TNO < 1.5) return r3((TNO - 0.5) * 1.5);
        return TNO;
    }

    private static Parcial t12(R R) {
        double TNO = R.pickD(R.u(0.1, 0.49, 2), R.u(0.5, 1.49, 2), R.u(0.5, 1.49, 2), R.u(1.5, 3, 2));
        double SF = R.u(0.05, 1.6, 3), u = utilizable(TNO), dentro = r3(Math.min(SF, u)), fuera = r3(SF - dentro);
        String caso = TNO < 0.5 ? "A" : TNO < 1.5 ? "B" : "C";
        Map<String, String> desc = Map.of("A", "TNO menor a 0.5 min, no cuenta como respiro",
                "B", "TNO entre 0.5 y 1.5 min, se usa (TNO − 0.5) × 1.5",
                "C", "TNO de 1.5 min o más, cuenta completo para reponerse");
        return new Parcial("En un ciclo breve el operario tiene un tiempo no ocupado ininterrumpido de " + fmt(TNO, 2)
                + " min por ciclo, puede sentarse y no necesita vigilar la máquina. El suplemento por fatiga es SF = " + fmt(SF)
                + " min por ciclo. ¿Qué caso aplica, cuánto tiempo es utilizable para reponerse y cuánto SF debe darse fuera del ciclo?", null,
                List.of(paso("Caso", "TNO < 0.5, 0.5 ≤ TNO < 1.5, TNO ≥ 1.5", "TNO = " + fmt(TNO, 2), desc.get(caso)),
                        paso("Tiempo utilizable", caso.equals("B") ? "TED = (TNO − 0.5) × 1.5" : caso.equals("A") ? "TED = 0" : "TED = TNO",
                                caso.equals("B") ? "(" + fmt(TNO, 2) + " − 0.5) × 1.5" : fmt(TNO, 2), fmt(u) + " min"),
                        paso("SF dentro y fuera", "Dentro = mínimo(SF, TED), Fuera = SF − Dentro", "mín(" + fmt(SF) + ", " + fmt(u) + ")",
                                "Dentro " + fmt(dentro) + ", fuera " + fmt(fuera) + " min")),
                List.of(opc("caso", "Caso que aplica", caso, List.of(new Opcion("A", desc.get("A")), new Opcion("B", desc.get("B")),
                                new Opcion("C", desc.get("C")))),
                        num("TED", "Tiempo utilizable para reponerse", u, "min"), num("fuera", "SF que se da fuera", fuera, "min")));
    }

    private record Asignacion(boolean snpDentro, double sfDentro, double sfFuera, String sfCaso, double fuera) {}

    private static Asignacion asignarLargo(double TNO, double SNP, double SF, boolean inint, boolean puede) {
        boolean snpDentro = TNO > 10 && inint && puede;
        double cap = snpDentro ? TNO - SNP : TNO;
        double sfDentro = r3(Math.max(0, Math.min(SF, cap)));
        double sfFuera = r3(SF - sfDentro);
        String sfCaso = sfFuera == 0 ? "dentro" : sfDentro == 0 ? "fuera" : "parte";
        return new Asignacion(snpDentro, sfDentro, sfFuera, sfCaso, r3((snpDentro ? 0 : SNP) + sfFuera));
    }

    private static final List<Opcion> OPC_SNP = List.of(new Opcion("dentro", "Dentro del TNO"), new Opcion("fuera", "Fuera del TNO"));
    private static final List<Opcion> OPC_SF = List.of(new Opcion("dentro", "Todo dentro del TNO"),
            new Opcion("parte", "Parte dentro y parte fuera"), new Opcion("fuera", "Todo fuera"));

    private static String razonesSNP(double TNO, boolean inint, boolean puede) {
        return "Condición 1 TNO > 10 min " + (TNO > 10 ? "sí" : "no") + " se cumple, condición 2 TNO ininterrumpido "
                + (inint ? "sí" : "no") + " se cumple, condición 3 dejar la máquina sin atender " + (puede ? "sí" : "no") + " se cumple";
    }

    private static Parcial t13(R R) {
        for (;;) {
            double TNO = R.u(4, 40, 3), SNP = R.u(2, 8, 3), SF = R.u(2, 16, 3);
            boolean inint = R.bool(0.5), puede = R.bool(0.5);
            Asignacion a = asignarLargo(TNO, SNP, SF, inint, puede);
            if (a.snpDentro() && TNO < SNP + SF) continue;
            String motivoPuede = puede ? R.pick(List.of("la máquina es automática", "otros operarios vecinos pueden vigilar la máquina"))
                    : "nadie más puede vigilar la máquina";
            return new Parcial("En un ciclo largo el TNO es de " + fmt(TNO) + " min/unidad "
                    + (inint ? "y es ininterrumpido" : "y está repartido en varios periodos") + ". Además " + motivoPuede
                    + ". Se calcularon SNP = " + fmt(SNP) + " y SF = " + fmt(SF) + " min/unidad. Decide dónde se asigna cada "
                    + "suplemento y calcula cuántos minutos de suplemento quedan fuera del TNO.", null,
                    List.of(paso("SNP", "Dentro solo si TNO > 10, TNO ininterrumpido y se puede dejar la máquina", razonesSNP(TNO, inint, puede),
                                    a.snpDentro() ? "El SNP va dentro del TNO" : "El SNP va fuera del TNO"),
                            paso("SF", "Dentro si TNO > SF, si TNO < SF una parte dentro y el resto fuera",
                                    "TNO disponible " + fmt(a.snpDentro() ? TNO - SNP : TNO) + " contra SF " + fmt(SF),
                                    "Dentro " + fmt(a.sfDentro()) + ", fuera " + fmt(a.sfFuera())),
                            paso("Suplementos fuera del TNO", "SNP fuera + SF fuera", fmt(a.snpDentro() ? 0 : SNP) + " + " + fmt(a.sfFuera()),
                                    fmt(a.fuera()) + " min/unidad")),
                    List.of(opc("snp", "Asignación del SNP", a.snpDentro() ? "dentro" : "fuera", OPC_SNP),
                            opc("sf", "Asignación del SF", a.sfCaso(), OPC_SF),
                            num("fuera", "Suplementos fuera del TNO", a.fuera(), "min/unidad")));
        }
    }

    private static Parcial t14(R R) {
        for (;;) {
            double ETE = R.u(5, 40, 3);
            int TM = R.pick(30, 40, 45, 50, 60, 75, 90);
            double ETI = R.u(2, TM - 3, 3);
            int p = R.i(5, 7);
            double SF = R.u(2, 14, 3);
            boolean inint = R.bool(0.4), puede = R.bool(0.4);
            double TNO = r3(TM - ETI), TC = r3(ETE + TM), SNP = r3((TC * p) / 100);
            Asignacion a = asignarLargo(TNO, SNP, SF, inint, puede);
            if (a.snpDentro() && TNO < SNP + SF) continue;
            double TT = r3(ETE + TM + a.fuera());
            return new Parcial("Una máquina tiene ETE = " + fmt(ETE) + ", ETI = " + fmt(ETI) + " y TM = " + TM + " min/unidad. "
                    + "El SNP es " + p + " % del tiempo ciclo y el SF calculado es " + fmt(SF) + " min/unidad. El TNO "
                    + (inint ? "es ininterrumpido" : "no es ininterrumpido") + " y el operario " + (puede ? "puede" : "no puede")
                    + " dejar la máquina sin atender. Calcula TNO, SNP y el tiempo tipo.", null,
                    List.of(paso("TNO", "TNO = TM − ETI", TM + " − " + fmt(ETI), fmt(TNO) + " min/unidad"),
                            paso("SNP", "SNP = % × (ETE + TM)", p + "% × (" + fmt(ETE) + " + " + TM + ")", fmt(SNP) + " min/unidad"),
                            paso("Asignación del SNP", "Tres condiciones", razonesSNP(TNO, inint, puede), a.snpDentro() ? "Dentro" : "Fuera"),
                            paso("Asignación del SF", "TNO disponible contra SF", fmt(a.snpDentro() ? TNO - SNP : TNO) + " contra " + fmt(SF),
                                    "Dentro " + fmt(a.sfDentro()) + ", fuera " + fmt(a.sfFuera())),
                            paso("Tiempo tipo", "TT = ETE + TM + suplementos que quedan fuera del TNO", fmt(ETE) + " + " + TM + " + " + fmt(a.fuera()),
                                    fmt(TT) + " min/unidad")),
                    List.of(num("TNO", "TNO", TNO, "min/unidad"), num("SNP", "SNP", SNP, "min/unidad"),
                            opc("snp", "Asignación del SNP", a.snpDentro() ? "dentro" : "fuera", OPC_SNP), num("TT", "Tiempo tipo", TT, "min/unidad")));
        }
    }

    private static Parcial t15(R R) {
        int n = R.i(3, 5);
        List<String> nombres = R.sample(NOMBRES_ELEM, n);
        List<Double> vals = new ArrayList<>();
        for (int k = 0; k < nombres.size(); k++) vals.add(R.u(0.1, 3, 3));
        int p = R.i(1, 5), snp = R.i(5, 7), sf = R.pick(4, 5, 6, 8, 10, 12);
        double s = 0;
        for (double v : vals) s += v;
        double tot = r3(s), misc = r3((tot * p) / 100), base = r3(tot + misc);
        double SNP = r3((base * snp) / 100), SF = r3((base * sf) / 100), TT = r3(base + SNP + SF);
        StringBuilder el = new StringBuilder();
        for (int k = 0; k < nombres.size(); k++) {
            if (k > 0) el.append(", ");
            el.append(nombres.get(k)).append(' ').append(fmt(vals.get(k)));
        }
        return new Parcial("Un trabajo manual sin máquina tiene los elementos normalizados " + el + " en MN/unidad. Se asigna " + p
                + " % de misceláneos, " + snp + " % de suplemento por necesidades personales y " + sf + " % de suplemento por "
                + "fatiga, ambos sobre el tiempo con misceláneos. Calcula el tiempo tipo.", null,
                List.of(paso("Elementos normalizados", "Σ MN/unidad", unir(vals, " + ", v -> fmt(v)), fmt(tot)),
                        paso("Misceláneos", "% × Σ", p + "% × " + fmt(tot), fmt(misc)),
                        paso("Suplementos", "SNP = % × (Σ + misc), SF = % × (Σ + misc)", snp + "% × " + fmt(base) + ", " + sf + "% × " + fmt(base),
                                "SNP " + fmt(SNP) + ", SF " + fmt(SF)),
                        paso("Tiempo tipo", "TT = elementos normalizados + misceláneos + suplementos",
                                fmt(tot) + " + " + fmt(misc) + " + " + fmt(SNP) + " + " + fmt(SF), fmt(TT) + " min/unidad")),
                List.of(num("misc", "Misceláneos", misc, "min/unidad"), num("sup", "Suplementos totales", r3(SNP + SF), "min/unidad"),
                        num("TT", "Tiempo tipo", TT, "min/unidad")));
    }

    private record Jornada(int h, int turnos, int dias, double limpH, int limpV, int ropa, int comida, double limpMin, double real) {}

    private static Jornada jornada(R R) {
        int h = R.pick(8, 8, 8, 7, 9), turnos = R.pick(1, 2, 3, 3), dias = R.pick(5, 6, 6);
        double limpH = R.pickD(0.5, 1, 1, 1.5, 2);
        int limpV = R.i(1, 4);
        int ropa = R.pick(10, 15, 20, 30), comida = R.pick(20, 30, 30, 45);
        double limpMin = r3((limpH * limpV * 60) / (dias * turnos));
        double real = r3(h * 60 - (limpMin + ropa + comida));
        return new Jornada(h, turnos, dias, limpH, limpV, ropa, comida, limpMin, real);
    }

    private static String textoJornada(Jornada j) {
        return "La limpieza de la máquina demora " + js(j.limpH()) + " h y se hace " + j.limpV() + " " + (j.limpV() == 1 ? "vez" : "veces")
                + " por semana. Para cambio de ropa se dan " + j.ropa() + " min por turno y para comida " + j.comida()
                + " min por turno. La empresa trabaja " + j.dias() + " días a la semana, " + j.turnos() + " "
                + (j.turnos() == 1 ? "turno" : "turnos") + " al día de " + j.h() + " h cada uno.";
    }

    private static Parcial t16(R R) {
        Jornada j = jornada(R);
        double TT = R.u(15, 120, 3), prod = r3(j.real() / TT);
        List<Paso> pasos = new ArrayList<>(pasosJornadaGuia(j));
        pasos.add(paso("Producción esperada", "unidades/turno = tiempo real / TT", fmt(j.real()) + " / " + fmt(TT), fmt(prod) + " unidades/turno"));
        return new Parcial("El tiempo tipo de una unidad es TT = " + fmt(TT) + " min/unidad. " + textoJornada(j)
                + " Calcula el tiempo de limpieza por turno, el tiempo real del turno y la producción esperada por turno.", null, pasos,
                List.of(num("limp", "Limpieza por turno", j.limpMin(), "min/turno"), num("real", "Tiempo real del turno", j.real(), "min/turno"),
                        num("prod", "Producción esperada", prod, "unidades/turno")));
    }

    private static Parcial t17(R R) {
        for (;;) {
            double ETE = R.u(0.5, 30, 3);
            int TM = R.pick(5, 8, 10, 15, 20, 30, 40, 50, 60, 90);
            double ETI = R.u(0.2, TM * 0.8, 3);
            double carga = rd((ETE + ETI) / (ETE + TM), 4);
            if (carga > 0.98) continue;
            double nMaq = rd(1 / carga, 2);
            int ent = (int) Math.floor(1 / carga);
            return new Parcial("Una operación con máquina tiene ETE = " + fmt(ETE) + ", ETI = " + fmt(ETI) + " y TM = " + TM
                    + " min/unidad. Calcula la carga de trabajo del operario y el número teórico de máquinas que puede atender.", null,
                    List.of(paso("Carga de trabajo", "Carga = (ETE + ETI) / (ETE + TM)", "(" + fmt(ETE) + " + " + fmt(ETI) + ") / (" + fmt(ETE) + " + " + TM + ")",
                                    js(carga) + " = " + js(rd(carga * 100, 2)) + " %"),
                            paso("Número de máquinas", "N = 1 / carga, se toma la parte entera", "1 / " + js(carga),
                                    js(nMaq) + ", el operario puede atender " + ent + " " + (ent == 1 ? "máquina" : "máquinas"))),
                    List.of(num("carga", "Carga de trabajo (fracción)", carga, "", 4), num("n", "Número teórico de máquinas", nMaq, "máquinas", 2),
                            num("ent", "Máquinas que puede atender", ent, "máquinas", 0)));
        }
    }

    private static Parcial t18(R R) {
        double mn = R.u(0.05, 2, 2);
        boolean porCiclo = R.bool(0.5);
        int k = porCiclo ? R.i(2, 10) : 1;
        int n = porCiclo ? 1 : R.i(2, 10);
        double mnu = r3((mn * k) / n), vu = r3(mnu / mn);
        return new Parcial("Un elemento tiene MN/vez = " + fmt(mn, 2) + " y MN/unidad = " + fmt(mnu)
                + ". Calcula las veces por unidad e indica con qué frecuencia aparece el elemento.", null,
                List.of(paso("Veces por unidad", "VEZ/unidad = (MN/unidad) / (MN/vez)", fmt(mnu) + " / " + fmt(mn, 2), fmt(vu) + " vez/unidad"),
                        paso("Interpretación", porCiclo ? "Si VEZ/unidad ≥ 1, aparece varias veces por ciclo"
                                        : "Si VEZ/unidad < 1, aparece una vez cada 1/(VEZ/unidad) ciclos",
                                porCiclo ? fmt(vu) : "1 / " + fmt(vu), porCiclo ? k + " veces por ciclo" : "1 vez cada " + n + " ciclos")),
                List.of(num("vu", "Veces por unidad", vu, "vez/unidad"),
                        num("n", porCiclo ? "Veces por ciclo" : "Cada cuántos ciclos aparece", porCiclo ? k : n, "", 0)));
    }

    private static Parcial t19(R R) {
        Jornada j = jornada(R);
        int meta = R.i(3, 25);
        double TTact = R.u(10, 140, 3);
        double TTmax = r3(j.real() / meta);
        String cumple = TTact <= TTmax ? "si" : "no";
        List<Paso> pasos = new ArrayList<>(pasosJornadaGuia(j));
        pasos.add(paso("Tiempo tipo máximo", "TT máx = tiempo real / meta", fmt(j.real()) + " / " + meta, fmt(TTmax) + " min/unidad"));
        pasos.add(paso("Comparación", "TT actual ≤ TT máx", fmt(TTact) + " contra " + fmt(TTmax),
                cumple.equals("si") ? "Sí se cumple la meta" : "No se cumple la meta"));
        return new Parcial("La empresa quiere producir al menos " + meta + " unidades por turno. " + textoJornada(j)
                + " El tiempo tipo actual es de " + fmt(TTact) + " min/unidad. ¿Cuál es el tiempo tipo máximo que permite cumplir "
                + "la meta y se cumple con el método actual?", null, pasos,
                List.of(num("real", "Tiempo real del turno", j.real(), "min/turno"), num("TTmax", "Tiempo tipo máximo", TTmax, "min/unidad"),
                        opc("cumple", "¿Se cumple la meta?", cumple, List.of(new Opcion("si", "Sí"), new Opcion("no", "No")))));
    }

    // ------------------------------------------------------------------ caso integral

    private record ElInt(String clave, String nombre, double mnvez, String tipo, int pf) {
        boolean exterior() { return tipo.equals("exterior"); }
    }

    private record Datos(Ctx c, int kg, double g, int lote, int vel, int nHerr, int cadaN, int pm, int psnp, int pfm,
                         boolean inint, boolean puede, Double vuH, List<ElInt> elems, Jornada j) {}

    private record ElCalc(ElInt e, double vu, double mnu) {}

    private record FilaSF(String x, double mnu, int pct, double sf) {}

    /** Resultados del caso integral (equivale a {@code casoIntegral(d)} de JS). */
    record Caso(double totalKg, double vuA, double vuH, double total, double misc, double sE, double sI, double ETE,
                double ETI, double frac, double TM, double TNO, double TC, double SNP, double SF, boolean snpDentro,
                double sfDentro, double sfFuera, String sfCaso, double fuera, double TT, double real, double prod,
                double carga, double nMaq, int ent) {}

    private static final class Calculo {
        double totalKg, vuA, vuH, total, misc, sE, sI, ETE, ETI, frac, TM, TNO, TC, SNP, SF, TT, prod, carga, nMaq;
        int ent;
        List<ElCalc> els;
        List<FilaSF> sfF;
        Asignacion a;
    }

    private static Calculo casoIntegral(Datos d) {
        Calculo s = new Calculo();
        s.totalKg = r3((d.lote() * d.g()) / 1000);
        s.vuA = r3(s.totalKg / d.kg());
        s.vuH = d.vuH() != null ? d.vuH() : r3((double) d.nHerr() / d.cadaN());
        s.els = new ArrayList<>();
        for (ElInt e : d.elems()) {
            double vu = e.clave().equals("A") ? s.vuA : e.clave().equals("B") || e.clave().equals("C") ? s.vuH : 1;
            s.els.add(new ElCalc(e, vu, r3(e.mnvez() * vu)));
        }
        double t = 0, se = 0, si = 0;
        for (ElCalc e : s.els) t += e.mnu();
        for (ElCalc e : s.els) if (e.e().exterior()) se += e.mnu();
        for (ElCalc e : s.els) if (!e.e().exterior()) si += e.mnu();
        s.total = r3(t);
        s.misc = r3((s.total * d.pm()) / 100);
        s.sE = r3(se);
        s.sI = r3(si);
        s.ETE = r3(s.sE + (s.sE * d.pm()) / 100);
        s.ETI = r3(s.sI + (s.sI * d.pm()) / 100);
        s.frac = rd((double) d.vel() / d.lote(), 5);
        s.TM = r3((double) d.lote() / d.vel());
        s.TNO = r3(s.TM - s.ETI);
        s.TC = r3(s.ETE + s.TM);
        s.SNP = r3((s.TC * d.psnp()) / 100);
        s.sfF = new ArrayList<>();
        for (ElCalc e : s.els) s.sfF.add(new FilaSF(e.e().nombre(), e.mnu(), e.e().pf(), r3((e.mnu() * e.e().pf()) / 100)));
        s.sfF.add(new FilaSF("Misceláneos", s.misc, d.pfm(), r3((s.misc * d.pfm()) / 100)));
        double sf = 0;
        for (FilaSF f : s.sfF) sf += f.sf();
        s.SF = r3(sf);
        s.a = asignarLargo(s.TNO, s.SNP, s.SF, d.inint(), d.puede());
        s.TT = r3(s.ETE + s.TM + s.a.fuera());
        s.prod = r3(d.j().real() / s.TT);
        s.carga = rd((s.ETE + s.ETI) / (s.ETE + s.TM), 4);
        s.nMaq = rd(1 / s.carga, 2);
        s.ent = (int) Math.floor(1 / s.carga);
        return s;
    }

    private static Parcial armarIntegral(Datos d) {
        Calculo s = casoIntegral(d);
        Ctx c = d.c();
        Jornada j = d.j();
        List<String> unaVez = new ArrayList<>(), interiores = new ArrayList<>();
        for (ElInt e : d.elems()) {
            if (e.clave().compareTo("D") >= 0) unaVez.add(e.clave());
            if (!e.exterior()) interiores.add(e.clave());
        }
        String enunciado = "En " + c.lugar() + " se tienen los siguientes datos para la fabricación de " + c.producto() + ". "
                + unir(d.elems(), ", ", e -> e.clave() + ". " + e.nombre() + " " + fmt(e.mnvez()) + " MN/vez")
                + ", y " + d.pm() + " % de misceláneos. La tolva se alimenta con " + d.kg() + " kg cada vez que se consume la "
                + "materia prima. Cada " + d.cadaN() + " unidades productivas se reemplazan y limpian " + c.articulo() + " "
                + d.nHerr() + " " + c.herr() + " de la máquina, y el elemento B se hace con la máquina inactiva. Los elementos "
                + String.join(" y ", unaVez) + " se hacen una vez en cada ciclo. Los elementos " + lista(interiores)
                + " se hacen con la máquina en marcha. La velocidad de entrega es de " + fi(d.vel()) + " " + c.producto()
                + "/min, cada ciclo produce " + fi(d.lote()) + " " + c.producto() + " de aproximadamente " + js(d.g())
                + " g. La empresa da " + d.psnp() + " % del tiempo ciclo para necesidades personales y los porcentajes de "
                + "fatiga son " + unir(d.elems(), ", ", e -> e.clave() + " " + e.pf() + " %") + " y misceláneos " + d.pfm()
                + " %. El TNO " + (d.inint() ? "es ininterrumpido" : "no es ininterrumpido") + " y el operario "
                + (d.puede() ? "puede" : "no puede") + " dejar la máquina sola. " + textoJornada(j)
                + " Calcula los tiempos, los suplementos, el tiempo tipo, la producción esperada, la carga de trabajo y "
                + "verifica si se le puede asignar más de una máquina.";
        List<Paso> pasos = pasosIntegral(d, s);
        List<Respuesta> resp = List.of(
                num("vuA", "Veces por unidad de A", s.vuA, "vez/unidad"), num("vuH", "Veces por unidad de B y C", s.vuH, "vez/unidad"),
                num("total", "Total MN/unidad sin misceláneos", s.total, "min/unidad"), num("misc", "Misceláneos", s.misc, "min/unidad"),
                num("TM", "TM", s.TM, "min/unidad"), num("ETE", "ETE", s.ETE, "min/unidad"), num("ETI", "ETI", s.ETI, "min/unidad"),
                num("TNO", "TNO", s.TNO, "min/unidad"), num("SNP", "SNP", s.SNP, "min/unidad"), num("SF", "SF", s.SF, "min/unidad"),
                opc("snp", "Asignación del SNP", s.a.snpDentro() ? "dentro" : "fuera", OPC_SNP), opc("sf", "Asignación del SF", s.a.sfCaso(), OPC_SF),
                num("TT", "Tiempo tipo", s.TT, "min/unidad"), num("real", "Tiempo real del turno", j.real(), "min/turno"),
                num("prod", "Producción esperada", s.prod, "unidades/turno"), num("carga", "Carga de trabajo (fracción)", s.carga, "", 4),
                num("ent", "Máquinas que puede atender", s.ent, "máquinas", 0));
        List<List<String>> tf = new ArrayList<>();
        for (ElInt e : d.elems()) tf.add(List.of(e.clave() + ". " + e.nombre(), fmt(e.mnvez()), e.exterior() ? "Detenida" : "En marcha", String.valueOf(e.pf())));
        return new Parcial(enunciado, tabla(List.of("Elemento", "MN/vez", "Máquina", "% fatiga"), tf), pasos, resp, seccionesIntegral(d));
    }

    /** Enunciado del caso integral con la forma de las diapositivas: tabla de datos, detalles, máquina, suplementos y jornada. */
    private static List<Seccion> seccionesIntegral(Datos d) {
        Ctx c = d.c();
        Jornada j = d.j();
        List<Seccion> r = new ArrayList<>();
        r.add(new Seccion(null, "En " + c.lugar() + " se tienen los siguientes datos para la fabricación de " + c.producto() + ":", null));
        List<List<String>> datos = new ArrayList<>();
        for (ElInt e : d.elems()) datos.add(List.of(e.clave() + ". " + e.nombre(), fmt(e.mnvez())));
        datos.add(List.of("F. Misceláneos", d.pm() + " %"));
        r.add(new Seccion("Datos", null, tabla(List.of("Elemento", "Tiempo en MN/vez"), datos)));

        List<List<String>> det = new ArrayList<>();
        ElInt a = d.elems().get(0), b = d.elems().get(1), cc = d.elems().get(2);
        det.add(List.of(a.clave() + ". " + a.nombre(), "La tolva se alimenta con " + d.kg() + " kg cada vez que la máquina consume la materia prima."));
        det.add(List.of(b.clave() + ". " + b.nombre() + " y " + cc.clave() + ". " + cc.nombre(),
                "Cada " + d.cadaN() + " unidades productivas se hace el reemplazo y limpieza de " + c.articulo() + " " + d.nHerr() + " "
                        + c.herr() + " que tiene la máquina. El elemento " + b.clave() + " se realiza con la máquina inactiva."));
        for (ElInt e : d.elems().subList(3, d.elems().size())) {
            det.add(List.of(e.clave() + ". " + e.nombre(), "Este elemento se hace una vez en cada ciclo."
                    + (e.exterior() ? " Se realiza con la máquina detenida." : "")));
        }
        r.add(new Seccion("Detalles", null, tabla(List.of("Elemento", "Detalles"), det)));
        List<String> interiores = new ArrayList<>();
        for (ElInt e : d.elems()) if (!e.exterior()) interiores.add(e.clave());
        r.add(new Seccion(null, "Los elementos " + lista(interiores) + " se hacen con la máquina en marcha. La velocidad de entrega de la máquina es de "
                + fi(d.vel()) + " " + c.producto() + "/min y en cada ciclo se producen " + fi(d.lote()) + " " + c.producto()
                + " de aproximadamente " + js(d.g()) + " g.", null));

        List<List<String>> sup = new ArrayList<>();
        for (ElInt e : d.elems()) sup.add(List.of(e.clave() + ". " + e.nombre(), e.pf() + " %"));
        sup.add(List.of("F. Misceláneos", d.pfm() + " %"));
        r.add(new Seccion("Suplementos", "La empresa da el " + d.psnp() + " % del tiempo del ciclo para suplementos por necesidades personales. El TNO "
                + (d.inint() ? "es ininterrumpido" : "no es ininterrumpido") + " y el operario " + (d.puede() ? "puede" : "no puede")
                + " dejar la máquina sola. Para los suplementos por fatiga, según las tablas de la empresa:",
                tabla(List.of("Elemento", "% de suplementos"), sup)));

        r.add(new Seccion("Producción esperada", "• La limpieza de la máquina demora " + js(j.limpH()) + " " + (j.limpH() == 1 ? "hora" : "horas")
                + " y se hace " + j.limpV() + " " + (j.limpV() == 1 ? "vez" : "veces") + " por semana.\n"
                + "• Para cambio de ropa se dan " + j.ropa() + " minutos por turno.\n"
                + "• Para almuerzo se dan " + j.comida() + " minutos por turno.\n"
                + "• La empresa tiene " + j.turnos() + " " + (j.turnos() == 1 ? "turno" : "turnos") + " al día de " + j.h()
                + " horas c/u y trabaja " + j.dias() + " días a la semana.", null));
        r.add(new Seccion(null, "Calcular los diferentes tiempos, los suplementos, el tiempo tipo, la producción esperada, la carga de trabajo "
                + "del operario y con ello verificar si se le puede asignar más de una máquina.", null));
        return r;
    }

    /** Solución del caso integral en el orden de las diapositivas (pasos 8 a 12), con sus tablas. */
    private static List<Paso> pasosIntegral(Datos d, Calculo s) {
        Ctx c = d.c();
        Jornada j = d.j();
        List<Paso> pasos = new ArrayList<>();
        pasos.add(paso("Veces por unidad del elemento A",
                "VEZ/unidad = [1 vez | kg por carga] × [1 kg | 1000 g] × [g | 1 " + c.pieza() + "] × [" + c.producto() + " | 1 unidad]",
                "[1 vez | " + d.kg() + " kg] × [1 kg | 1000 g] × [" + js(d.g()) + " g | 1 " + c.pieza() + "] × [" + fi(d.lote()) + " "
                        + c.producto() + " | 1 unidad]",
                fmt(s.vuA) + " vez/unidad"));
        pasos.add(paso("Veces por unidad de los elementos B y C", "VEZ/unidad = [" + c.herr() + " | unidades]",
                "[" + d.nHerr() + " veces | " + d.cadaN() + " unidades]",
                fmt(s.vuH) + " veces/unidad" + (d.vuH() != null && d.vuH() != 0 ? " (la diapositiva de clase usa 3.830; el cociente exacto es 3.833)" : "")));
        List<String> unaVez = new ArrayList<>();
        for (ElInt e : d.elems()) if (e.clave().compareTo("D") >= 0) unaVez.add(e.clave());
        pasos.add(paso("Veces por unidad de los elementos " + String.join(" y ", unaVez), "Se hacen una vez en cada ciclo",
                "1 vez por ciclo de 1 unidad", "1 vez/unidad"));

        List<List<String>> mn = new ArrayList<>();
        for (ElCalc e : s.els) mn.add(List.of(e.e().clave() + ". " + e.e().nombre(), fmt(e.e().mnvez()), fmt(e.vu()), fmt(e.mnu())));
        mn.add(List.of("TOTAL", "", "", fmt(s.total)));
        mn.add(List.of("F. Misceláneos", d.pm() + " %", "", d.pm() + "%(" + fmt(s.total) + ") = " + fmt(s.misc)));
        pasos.add(paso("Tiempo en MN/unidad de cada elemento", "MN/unidad = MN/vez × VEZ/unidad;  misceláneos = " + d.pm() + " % del TOTAL",
                "Se multiplica cada fila de la tabla:", "TOTAL = " + fmt(s.total) + " min/unidad,  misceláneos = " + fmt(s.misc) + " min/unidad",
                tabla(List.of("Elemento", "Tiempo en MN/vez", "Vez/unidad", "Tiempo en MN/unidad"), mn)));

        pasos.add(paso("Tiempo condicionado por la máquina (TM)",
                "[" + c.producto() + " | minuto] × [1 unidad | " + c.producto() + " por ciclo] = [unidades | minuto];   TM = [1 | unidades/minuto]",
                "[" + fi(d.vel()) + " " + c.producto() + " | minuto] × [1 unidad | " + fi(d.lote()) + " " + c.producto() + "] = " + js(s.frac)
                        + " [unidades | minuto];   TM = [1 | " + js(s.frac) + "]",
                "TM = " + fmt(s.TM) + " min/unidad"));

        pasos.add(paso("Elementos de trabajo interior (ETI)", "ETI = TOTAL de los elementos interiores + " + d.pm() + " % de misceláneos",
                "Elementos que se hacen con la máquina en marcha:", "ETI = " + fmt(s.ETI) + " min/unidad",
                tablaGrupo(s, false, d.pm())));
        pasos.add(paso("Elementos de trabajo exterior (ETE)", "ETE = TOTAL de los elementos exteriores + " + d.pm() + " % de misceláneos",
                "Elementos que se hacen con la máquina detenida:", "ETE = " + fmt(s.ETE) + " min/unidad",
                tablaGrupo(s, true, d.pm())));
        pasos.add(paso("Tiempo no ocupado (TNO)", "TNO = TM − ETI", "TNO = " + fmt(s.TM) + " − " + fmt(s.ETI), "TNO = " + fmt(s.TNO) + " min/unidad"));

        pasos.add(paso("Suplementos por necesidades personales (SNP)", "SNP = " + d.psnp() + " % (TC);   TC = ETE + TM",
                "SNP = " + d.psnp() + " % (" + fmt(s.ETE) + " + " + fmt(s.TM) + ")", "SNP = " + fmt(s.SNP) + " min/unidad"));
        List<List<String>> sf = new ArrayList<>();
        for (int k = 0; k < s.els.size(); k++) {
            ElCalc e = s.els.get(k);
            FilaSF f = s.sfF.get(k);
            sf.add(List.of(e.e().clave() + ". " + e.e().nombre(), fmt(f.mnu()), String.valueOf(f.pct()), fmt(f.sf())));
        }
        FilaSF fm = s.sfF.get(s.sfF.size() - 1);
        sf.add(List.of("F. Misceláneos", fmt(fm.mnu()), String.valueOf(fm.pct()), fmt(fm.sf())));
        sf.add(List.of("TOTAL", "", "", fmt(s.SF)));
        pasos.add(paso("Suplementos por fatiga (SF)", "MN/unidad de suplementos = Tiempo MN/unidad × % de suplementos;   SF = suma",
                "Según las tablas de la empresa:", "SF = " + fmt(s.SF) + " min/unidad",
                tabla(List.of("Elemento", "Tiempo MN/unidad", "% de suplementos", "MN/unidad de suplementos"), sf)));

        pasos.add(paso("Asignación del SNP dentro del ciclo",
                "Para dar el SNP dentro del TNO se debe cumplir: 1) TNO > 10 min, 2) el TNO es ininterrumpido y 3) el operario puede dejar la máquina sin atender",
                razonesSNP(s.TNO, d.inint(), d.puede()),
                s.a.snpDentro() ? "El SNP se da dentro del TNO" : "El SNP se da por fuera"));
        double disp = s.a.snpDentro() ? r3(s.TNO - s.SNP) : s.TNO;
        pasos.add(paso("Asignación del SF dentro del ciclo",
                "Ciclo largo: si TNO > SF, todo el SF se da dentro del TNO; si TNO < SF, parte dentro y parte afuera",
                (s.a.snpDentro() ? "TNO libre = " + fmt(s.TNO) + " − " + fmt(s.SNP) + " = " + fmt(disp) : "TNO = " + fmt(s.TNO))
                        + (disp > s.SF ? " > " : " < ") + "SF = " + fmt(s.SF),
                s.a.sfCaso().equals("dentro") ? "Todo el SF se da dentro del TNO"
                        : s.a.sfCaso().equals("fuera") ? "Todo el SF se da por fuera"
                        : "SF dentro " + fmt(s.a.sfDentro()) + " y por fuera " + fmt(s.a.sfFuera())));
        List<String> fuera = new ArrayList<>();
        if (!s.a.snpDentro()) fuera.add(fmt(s.SNP));
        if (s.a.sfFuera() > 0) fuera.add(fmt(s.a.sfFuera()));
        pasos.add(paso("Tiempo tipo (TT)", "TT = ETE + TM + (SNP + SF)*   * solo la porción de suplementos que queda fuera del TNO",
                "TT = " + fmt(s.ETE) + " + " + fmt(s.TM) + (fuera.isEmpty() ? " + 0" : fuera.size() == 1 ? " + " + fuera.get(0) : " + (" + String.join(" + ", fuera) + ")"),
                "TT = " + fmt(s.TT) + " min/unidad"));

        pasos.addAll(pasosJornadaGuia(j));
        pasos.add(paso("Producción esperada", "[tiempo real | turno] × [1 unidad | TT]",
                "[" + fmt(j.real()) + " min | turno] × [1 unidad | " + fmt(s.TT) + " min]", fmt(s.prod) + " unidades/turno"));
        pasos.add(paso("Carga de trabajo del operario", "Carga de trabajo = [ETE + ETI | ETE + TM]",
                "[" + fmt(s.ETE) + " + " + fmt(s.ETI) + " | " + fmt(s.ETE) + " + " + fmt(s.TM) + "] = [" + fmt(r3(s.ETE + s.ETI)) + " | "
                        + fmt(r3(s.ETE + s.TM)) + "]",
                js(s.carga) + " = " + js(rd(s.carga * 100, 2)) + " %"));
        pasos.add(paso("Número de máquinas", "Número de máquinas = [1 | carga de trabajo]", "[1 | " + js(s.carga) + "]",
                js(s.nMaq) + ": el operario puede atender " + s.ent + " " + (s.ent == 1 ? "máquina" : "máquinas") + " al tiempo"));
        return pasos;
    }

    /** Tabla de los elementos interiores ({@code exterior = false}) o exteriores con su renglón de misceláneos. */
    private static Tabla tablaGrupo(Calculo s, boolean exterior, int pm) {
        List<List<String>> f = new ArrayList<>();
        for (ElCalc e : s.els) if (e.e().exterior() == exterior) f.add(List.of(e.e().clave() + ". " + e.e().nombre(), fmt(e.mnu())));
        double suma = exterior ? s.sE : s.sI, total = exterior ? s.ETE : s.ETI;
        f.add(List.of("TOTAL", fmt(suma)));
        f.add(List.of("F. Misceláneos", pm + "%(" + fmt(suma) + ") = " + fmt(r3(total - suma))));
        f.add(List.of(exterior ? "ETE" : "ETI", fmt(total)));
        return tabla(List.of("Elemento", "Tiempo en MN/unidad"), f);
    }

    /** Tiempo real del turno escrito con factores de conversión, como en la diapositiva de producción esperada. */
    private static List<Paso> pasosJornadaGuia(Jornada j) {
        String veces = j.limpV() == 1 ? "vez" : "veces", turnos = j.turnos() == 1 ? "turno" : "turnos";
        return List.of(
                paso("Tiempo del turno", "[h | turno] × [60 min | h]", "[" + j.h() + " h | turno] × [60 min | h]", (j.h() * 60) + " min/turno"),
                paso("Limpieza de la máquina por turno", "[h | vez] × [veces | semana] × [1 semana | días] × [1 día | turnos] × [60 min | h]",
                        "[" + js(j.limpH()) + " h | vez] × [" + j.limpV() + " " + veces + " | semana] × [1 semana | " + j.dias() + " días] × [1 día | "
                                + j.turnos() + " " + turnos + "] × [60 min | h]",
                        fmt(j.limpMin() / 60) + " h/turno = " + fmt(j.limpMin()) + " min/turno"),
                paso("Tiempo real por turno", "min/turno − (limpieza + cambio de ropa + almuerzo)",
                        (j.h() * 60) + " − (" + fmt(j.limpMin()) + " + " + j.ropa() + " + " + j.comida() + ")", fmt(j.real()) + " min/turno"));
    }

    private static final Datos EJEMPLO_CLASE = new Datos(CONTEXTOS.get(0), 20, 0.283, 100000, 2000, 23, 6, 2, 7, 9, false, false, 3.83,
            List.of(new ElInt("A", "Alimentar tolva", 0.904, "interior", 32),
                    new ElInt("B", "Reemplazar punzón", 9.122, "exterior", 12),
                    new ElInt("C", "Limpiar punzón", 4.4, "interior", 12),
                    new ElInt("D", "Revisar consistencia", 0.845, "interior", 14),
                    new ElInt("E", "Llevar tabletas al próximo proceso", 0.993, "interior", 13)),
            new Jornada(8, 3, 6, 1, 2, 30, 20, 6.667, 423.333));

    private static Parcial t20(R R, long semilla) {
        if (semilla == 0) return armarIntegral(EJEMPLO_CLASE);
        for (;;) {
            Ctx c = R.pick(CONTEXTOS);
            int lote = R.pick(c.lote()), vel = R.pick(c.vel());
            double TM = (double) lote / vel;
            if (TM < 20 || TM > 150) continue;
            double g = R.u(c.masa()[0], c.masa()[1], c.masa()[1] < 1 ? 3 : 1);
            int kg = R.pick(10, 15, 20, 25, 30, 40, 50);
            double vuA = (lote * g) / 1000 / kg;
            if (vuA < 0.4 || vuA > 5) continue;
            boolean intD = R.bool(0.85);
            List<ElInt> elems = new ArrayList<>();
            double m;
            m = R.u(0.3, 2, 3);
            elems.add(new ElInt("A", "Alimentar tolva", m, "interior", R.i(10, 35)));
            m = R.u(0.3, 2.5, 3);
            elems.add(new ElInt("B", "Reemplazar " + c.herrS(), m, "exterior", R.i(8, 18)));
            m = R.u(0.1, 1.2, 3);
            elems.add(new ElInt("C", "Limpiar " + c.herrS(), m, "interior", R.i(8, 18)));
            m = R.u(0.3, 2, 3);
            elems.add(new ElInt("D", c.revisar(), m, intD ? "interior" : "exterior", R.i(8, 20)));
            m = R.u(0.4, 2.5, 3);
            elems.add(new ElInt("E", "Llevar " + c.producto() + " al próximo proceso", m, "interior", R.i(8, 20)));
            int nHerr = R.i(6, 30), cadaN = R.i(3, 10), pm = R.i(1, 5), psnp = R.i(5, 7), pfm = R.i(5, 12);
            boolean inint = R.bool(0.4), puede = R.bool(0.4);
            Jornada j = jornada(R);
            Datos d = new Datos(c, kg, g, lote, vel, nHerr, cadaN, pm, psnp, pfm, inint, puede, null, elems, j);
            Calculo s = casoIntegral(d);
            if (s.TNO <= 0.5 || s.ETE < 1) continue;
            if (s.a.snpDentro() && s.TNO < s.SNP + s.SF) continue;
            if (s.prod < 1) continue;
            return armarIntegral(d);
        }
    }

    // ================================================================== API

    static Problema generar(String tipo, long semilla) {
        Tipo info = tipo(tipo);
        R R = new R(new Prng(semilla + 7919L * Integer.parseInt(tipo.substring(1))));
        Parcial p = switch (tipo) {
            case "T01" -> t01(R);
            case "T02" -> t02(R);
            case "T03" -> t03(R);
            case "T04" -> t04(R);
            case "T05" -> t05(R);
            case "T06" -> t06(R);
            case "T07" -> t07(R);
            case "T08" -> t08(R);
            case "T09" -> t09(R);
            case "T10" -> t10(R);
            case "T11" -> t11(R);
            case "T12" -> t12(R);
            case "T13" -> t13(R);
            case "T14" -> t14(R);
            case "T15" -> t15(R);
            case "T16" -> t16(R);
            case "T17" -> t17(R);
            case "T18" -> t18(R);
            case "T19" -> t19(R);
            case "T20" -> t20(R, semilla);
            default -> throw new IllegalArgumentException("Tipo desconocido " + tipo);
        };
        return new Problema(tipo, info.tema(), info.titulo(), info.dificultad(), semilla, p.enunciado(), p.tabla(),
                List.copyOf(p.pasos()), List.copyOf(p.respuestas()), p.secciones() == null ? null : List.copyOf(p.secciones()));
    }

    /** Igual que {@code generarExamen} de JS. */
    static List<Problema> generarExamen(int n, long semilla) {
        R R = new R(new Prng(semilla));
        List<String> ids = TIPOS.stream().map(Tipo::id).toList();
        List<Problema> l = new ArrayList<>();
        for (int k = 0; k < n; k++) l.add(generar(R.pick(ids), semilla * 31 + k));
        return l;
    }

    /** Semilla nueva al azar (1 a 999 999 999) distinta de {@code evitar}. */
    static long semillaNueva(long evitar) {
        long s;
        do {
            s = 1 + java.util.concurrent.ThreadLocalRandom.current().nextLong(999_999_999L);
        } while (s == evitar);
        return s;
    }

    // ================================================================== formulario

    private static final String MN = "MN = TO(min) × [C | 100]", TO = "TO(min) = [Σ lecturas (cmin) | 100]",
            MNVEZ = "MN/vez = [ΣMN | ΣF]", MNU = "MN/unidad = MN/vez × VEZ/unidad",
            TOLVA = "VEZ/unidad = [piezas por lote × g por pieza | 1000 × kg por carga]",
            TM = "TM = [piezas por lote | velocidad (piezas/min)]",
            ETE = "ETE = Σ MN/unidad exteriores × (1 + %misc)", ETI = "ETI = Σ MN/unidad interiores × (1 + %misc)",
            TNO = "TNO = TM − ETI", TC = "TC = ETE + TM = ETE + ETI + TNO", SNP = "SNP = %SNP × (ETE + TM)",
            SF = "SF = Σ (MN/unidad × %fatiga)", TT = "TT = ETE + TM + (SNP + SF que quedan fuera del TNO)",
            LIMP = "Limpieza/turno = [h por vez × veces/semana × 60 | días/semana × turnos/día]",
            REAL = "Tiempo real = h/turno × 60 − (limpieza + ropa + comida)",
            CARGA = "Carga = [ETE + ETI | ETE + TM]", NMAQ = "N máquinas = [1 | Carga]   (parte entera)";

    /**
     * Fórmulas de cada tipo en notación de examen. {@code [a | b]} es una fracción con a arriba y b abajo
     * (la dibuja {@link UIP.Formula}). Solo es material de consulta: las cuentas están en los generadores.
     */
    private static final Map<String, List<String>> FORMULARIO = Map.ofEntries(
            Map.entry("T01", List.of(TO, MN, MNVEZ)),
            Map.entry("T02", List.of("VEZ/unidad = [veces que aparece | unidades (ciclos)]", MNU)),
            Map.entry("T03", List.of("VEZ/unidad = [veces que aparece | ciclos]", MNU)),
            Map.entry("T04", List.of(TO, MN, MNVEZ, "VEZ/unidad = [veces | ciclos]", MNU, "Total = Σ MN/unidad")),
            Map.entry("T05", List.of("kg/unidad = [piezas por lote × g por pieza | 1000]", "VEZ/unidad = [kg/unidad | kg por carga]")),
            Map.entry("T06", List.of("VEZ/unidad = [piezas reemplazadas | unidades entre reemplazos]", MNU)),
            Map.entry("T07", List.of("Misc = %misc × Σ MN/unidad", "Total = Σ MN/unidad + Misc")),
            Map.entry("T08", List.of("unidades/min = [velocidad (piezas/min) | piezas por lote]", "TM = [1 | unidades/min] = [piezas por lote | velocidad]",
                    "piezas/min = [piezas/h | 60]")),
            Map.entry("T09", List.of(ETE, ETI, TNO, TC)),
            Map.entry("T10", List.of(TC, "SNP = %SNP × TC")),
            Map.entry("T11", List.of("Suplemento = MN/unidad × [%fatiga | 100]", SF)),
            Map.entry("T12", List.of("TNO < 0.5 min   →   TED = 0", "0.5 ≤ TNO < 1.5 min   →   TED = (TNO − 0.5) × 1.5",
                    "TNO ≥ 1.5 min   →   TED = TNO", "SF dentro = mín(SF, TED)     SF fuera = SF − SF dentro")),
            Map.entry("T13", List.of("SNP dentro solo si:  TNO > 10 min,  TNO ininterrumpido  y  se puede dejar la máquina",
                    "TNO disponible = TNO − SNP  (si el SNP va dentro),  si no  TNO disponible = TNO",
                    "SF dentro = mín(SF, TNO disponible)     SF fuera = SF − SF dentro", "Fuera del TNO = SNP fuera + SF fuera")),
            Map.entry("T14", List.of(TNO, SNP, "SNP dentro solo si:  TNO > 10,  ininterrumpido  y  se puede dejar la máquina",
                    "SF dentro si TNO > SF", TT)),
            Map.entry("T15", List.of("Misc = %misc × Σ", "SNP = %SNP × (Σ + Misc)     SF = %SF × (Σ + Misc)",
                    "TT = Σ + Misc + SNP + SF")),
            Map.entry("T16", List.of(LIMP, REAL, "Producción = [Tiempo real | TT]")),
            Map.entry("T17", List.of(CARGA, NMAQ)),
            Map.entry("T18", List.of("VEZ/unidad = [MN/unidad | MN/vez]", "VEZ/unidad ≥ 1  →  veces por ciclo",
                    "VEZ/unidad < 1  →  1 vez cada [1 | VEZ/unidad] ciclos")),
            Map.entry("T19", List.of(LIMP, REAL, "TT máx = [Tiempo real | meta]", "Se cumple si  TT actual ≤ TT máx")),
            Map.entry("T20", List.of(TOLVA, "VEZ/unidad (B y C) = [piezas reemplazadas | unidades entre reemplazos]", MNU,
                    "Misc = %misc × Σ MN/unidad", TM, ETE, ETI, TNO, SNP, SF, TT, LIMP, REAL, "Producción = [Tiempo real | TT]",
                    CARGA, NMAQ)));

    static List<String> formulario(String tipo) {
        return FORMULARIO.getOrDefault(tipo, List.of());
    }

    /** La misma fórmula en una sola línea de texto ([a | b] → a / b). */
    static String formulaPlana(String f) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < f.length()) {
            int a = f.indexOf('[', i), b = a < 0 ? -1 : f.indexOf(']', a);
            if (a < 0 || b < 0) {
                sb.append(f.substring(i));
                break;
            }
            sb.append(f, i, a);
            String[] p = f.substring(a + 1, b).split("\\|", 2);
            String n = p[0].trim(), d = p.length > 1 ? p[1].trim() : "";
            sb.append(n.contains(" ") ? "(" + n + ")" : n).append(" / ").append(d.contains(" ") ? "(" + d + ")" : d);
            i = b + 1;
        }
        return sb.toString();
    }

    /** Semillas de los problemas resueltos del archivo problemas_ordinario.json. */
    static List<Long> semillasResueltas(String tipo) {
        return tipo.equals("T20") ? List.of(0L, 11L, 22L, 33L, 44L) : List.of(11L, 22L, 33L, 44L, 55L);
    }
}
