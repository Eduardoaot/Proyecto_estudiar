// Generadores de problemas. Estudio del Trabajo, Estudio de Tiempos pasos 8 a 12.
// Modulo ES sin dependencias. Cada generador recibe una semilla y regresa un problema
// con enunciado, datos, pasos resueltos y respuestas calificables.
// Uso:  import { generar, TIPOS, calificar } from "./generadores.js";
//       const p = generar("T20", 12345);

// ---------- utilidades ----------
export function prng(seed) {
  let a = seed >>> 0;
  return function () {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rd = (x, d = 3) => Math.round((x + Number.EPSILON) * 10 ** d) / 10 ** d;
const r3 = (x) => rd(x, 3);
export const fmt = (x, d = 3) =>
  Number(x).toLocaleString("en-US", { minimumFractionDigits: d, maximumFractionDigits: d });
const fi = (x) => Number(x).toLocaleString("en-US");
const lista = (a) => (a.length < 2 ? a.join("") : `${a.slice(0, -1).join(", ")} y ${a[a.length - 1]}`);

function herramientas(rand) {
  const R = {
    u: (a, b, d = 3) => rd(a + (b - a) * rand(), d),
    i: (a, b) => a + Math.floor(rand() * (b - a + 1)),
    pick: (arr) => arr[Math.floor(rand() * arr.length)],
    bool: (p = 0.5) => rand() < p,
    shuffle: (arr) => {
      const c = arr.slice();
      for (let k = c.length - 1; k > 0; k--) {
        const j = Math.floor(rand() * (k + 1));
        [c[k], c[j]] = [c[j], c[k]];
      }
      return c;
    },
  };
  R.sample = (arr, n) => R.shuffle(arr).slice(0, n);
  return R;
}

const num = (clave, etiqueta, valor, unidad = "", decimales = 3) => ({
  clave, etiqueta, tipo: "numero", valor, unidad, decimales,
});
const opc = (clave, etiqueta, valor, opciones) => ({ clave, etiqueta, tipo: "opcion", valor, opciones });
const paso = (titulo, formula, sustitucion, resultado) => ({ titulo, formula, sustitucion, resultado });

// Tolerancia: 1.5 % relativo o 0.006 absoluto, lo que sea mayor. Cubre el redondeo de clase.
export function calificar(respuesta, entrada) {
  if (respuesta.tipo === "opcion") return String(entrada).trim() === String(respuesta.valor);
  const x = Number(String(entrada).replace(/,/g, "").replace("%", "").trim());
  if (!Number.isFinite(x)) return false;
  const tol = Math.max(Math.abs(respuesta.valor) * 0.015, 0.006);
  return Math.abs(x - respuesta.valor) <= tol;
}

// ---------- contextos ----------
const CONTEXTOS = [
  { lugar: "un laboratorio farmacéutico de Monterrey", producto: "tabletas", pieza: "tableta", herr: "punzones", herrS: "punzón", revisar: "Revisar consistencia", masa: [0.2, 0.6], vel: [1500, 2000, 2500, 3000], lote: [60000, 80000, 100000, 120000, 150000] },
  { lugar: "una planta de galletas en Santa Catarina", producto: "galletas", pieza: "galleta", herr: "moldes", herrS: "molde", revisar: "Revisar el horneado", masa: [8, 15], vel: [300, 400, 500, 600], lote: [12000, 15000, 18000, 20000, 24000] },
  { lugar: "una planta de inyección de plástico en Apodaca", producto: "tapas", pieza: "tapa", herr: "boquillas", herrS: "boquilla", art: "las", un: "una", revisar: "Revisar rebabas", masa: [2, 5], vel: [400, 500, 600, 800], lote: [20000, 25000, 30000, 40000] },
  { lugar: "una fábrica de tornillos en Escobedo", producto: "tornillos", pieza: "tornillo", herr: "dados", herrS: "dado", revisar: "Revisar la rosca", masa: [3, 10], vel: [250, 300, 400, 500], lote: [10000, 12000, 15000, 20000] },
  { lugar: "una fábrica de dulces en Guadalupe", producto: "caramelos", pieza: "caramelo", herr: "troqueles", herrS: "troquel", revisar: "Revisar el empaque", masa: [4, 8], vel: [600, 800, 1000, 1200], lote: [30000, 40000, 50000, 60000] },
  { lugar: "una embotelladora en San Nicolás de los Garza", producto: "botellas", pieza: "botella", herr: "válvulas de llenado", herrS: "válvula", art: "las", un: "una", revisar: "Revisar el nivel de llenado", masa: [500, 600], vel: [100, 120, 150, 200], lote: [4000, 5000, 6000, 8000] },
];
const NOMBRES_ELEM = ["Tomar pieza", "Colocar en el dispositivo", "Ajustar sujetador", "Retirar pieza", "Inspeccionar medida", "Llevar a la banda", "Llenar estantería", "Llevar carro al puesto", "Etiquetar caja", "Cerrar caja", "Limpiar área", "Acomodar en tarima"];
const CALIFS = [80, 85, 90, 95, 100, 105, 110, 115];

// Genera observaciones en centiminutos por calificación para un elemento.
function observaciones(R, base, nCal) {
  const cals = R.sample(CALIFS, nCal).sort((a, b) => a - b);
  return cals.map((C) => {
    const F = R.i(1, 6);
    const obs = Array.from({ length: F }, () => Math.max(2, Math.round(((base * 10000) / C) * (0.94 + 0.12 * R.u(0, 1, 4)))));
    return { C, F, obs };
  });
}
function resolverMNvez(filas) {
  const det = filas.map((f) => {
    const suma = f.obs.reduce((a, b) => a + b, 0);
    const TO = rd(suma / 100, 2);
    const MN = r3((TO * f.C) / 100);
    return { ...f, suma, TO, MN };
  });
  const sMN = r3(det.reduce((a, b) => a + b.MN, 0));
  const sF = det.reduce((a, b) => a + b.F, 0);
  return { det, sMN, sF, mnvez: r3(sMN / sF) };
}

// ---------- tipos ----------
export const TIPOS = {
  T01: { tema: "Paso 8 - Minutos normales por vez", titulo: "MN por vez a partir de calificaciones", dificultad: 1 },
  T02: { tema: "Paso 8 - Minutos normales por unidad", titulo: "MN por unidad, varias veces por ciclo", dificultad: 1 },
  T03: { tema: "Paso 8 - Minutos normales por unidad", titulo: "MN por unidad, una vez cada n ciclos", dificultad: 1 },
  T04: { tema: "Paso 8 - Minutos normales por unidad", titulo: "Hoja completa de MN por vez y por unidad", dificultad: 2 },
  T05: { tema: "Paso 10 - Veces por unidad", titulo: "Veces por unidad de una tolva", dificultad: 2 },
  T06: { tema: "Paso 10 - Veces por unidad", titulo: "Veces por unidad de un reemplazo de herramienta", dificultad: 1 },
  T07: { tema: "Paso 9 - Misceláneos", titulo: "Asignación de misceláneos", dificultad: 1 },
  T08: { tema: "Paso 10 - Tiempo de máquina", titulo: "Tiempo condicionado por la máquina", dificultad: 1 },
  T09: { tema: "Paso 10 - ETE, ETI y TNO", titulo: "Trabajo exterior, interior y tiempo no ocupado", dificultad: 2 },
  T10: { tema: "Paso 11 - Suplementos por necesidades personales", titulo: "Tiempo ciclo y SNP", dificultad: 1 },
  T11: { tema: "Paso 11 - Suplementos por fatiga", titulo: "Tabla de suplementos por fatiga", dificultad: 1 },
  T12: { tema: "Paso 11 - Asignación en ciclos breves", titulo: "Tiempo efectivamente utilizable para descanso", dificultad: 2 },
  T13: { tema: "Paso 11 - Asignación en ciclos largos", titulo: "¿Los suplementos van dentro o fuera del TNO?", dificultad: 2 },
  T14: { tema: "Paso 12 - Tiempo tipo", titulo: "Tiempo tipo con máquina", dificultad: 2 },
  T15: { tema: "Paso 9 - Tiempo tipo", titulo: "Tiempo tipo de un trabajo manual", dificultad: 1 },
  T16: { tema: "Paso 12 - Producción esperada", titulo: "Producción esperada por turno", dificultad: 2 },
  T17: { tema: "Paso 12 - Carga de trabajo", titulo: "Carga de trabajo y número de máquinas", dificultad: 1 },
  T18: { tema: "Paso 8 - Minutos normales por unidad", titulo: "Problema inverso de veces por unidad", dificultad: 1 },
  T19: { tema: "Paso 12 - Producción esperada", titulo: "Tiempo tipo máximo para cumplir una meta", dificultad: 2 },
  T20: { tema: "Caso integral pasos 8 a 12", titulo: "Caso integral de trabajo con máquina", dificultad: 3 },
};

const G = {};

G.T01 = (R) => {
  const nombre = R.pick(NOMBRES_ELEM);
  const filas = observaciones(R, R.u(0.08, 0.6, 2), R.i(2, 4));
  const s = resolverMNvez(filas);
  const lista = filas.map((f) => `C = ${f.C} con lecturas de ${f.obs.join(", ")} cmin`).join(". ");
  return {
    enunciado: `En el estudio de tiempos del elemento "${nombre}" se registraron las siguientes lecturas en centiminutos agrupadas por calificación. ${lista}. Calcula el tiempo observado en minutos y los minutos normales de cada calificación, la sumatoria de minutos normales, la frecuencia total y los minutos normales por vez.`,
    tabla: { columnas: ["C", "Lecturas (cmin)", "F"], filas: filas.map((f) => [f.C, f.obs.join(", "), f.F]) },
    pasos: [
      ...s.det.map((d) => paso(`Calificación ${d.C}`, "TO(min) = Σ lecturas / 100, MN = TO(min) × C / 100", `TO = ${d.suma} / 100 = ${fmt(d.TO, 2)}, MN = ${fmt(d.TO, 2)} × ${d.C} / 100`, `MN = ${fmt(d.MN)} min`)),
      paso("Sumatorias", "ΣMN y ΣF", `ΣMN = ${s.det.map((d) => fmt(d.MN)).join(" + ")}, ΣF = ${s.det.map((d) => d.F).join(" + ")}`, `ΣMN = ${fmt(s.sMN)}, ΣF = ${s.sF}`),
      paso("MN por vez", "MN/vez = ΣMN / ΣF", `${fmt(s.sMN)} / ${s.sF}`, `${fmt(s.mnvez)} min`),
    ],
    respuestas: [num("sMN", "Sumatoria de MN", s.sMN, "min"), num("sF", "Frecuencia total", s.sF, "", 0), num("mnvez", "MN por vez", s.mnvez, "min/vez")],
  };
};

G.T02 = (R) => {
  const mn = R.u(0.05, 2.5, 2), k = R.i(2, 12), nombre = R.pick(NOMBRES_ELEM);
  const v = r3(mn * k);
  return {
    enunciado: `El elemento "${nombre}" tiene MN/vez = ${fmt(mn, 2)} y aparece ${k} veces en cada ciclo de trabajo. ¿Cuál es su tiempo en MN por unidad?`,
    pasos: [paso("Veces por unidad", "VEZ/UNIDAD = veces que aparece / unidades", `${k} / 1`, `${k} vez/unidad`), paso("MN por unidad", "MN/unidad = MN/vez × VEZ/unidad", `${fmt(mn, 2)} × ${k}`, `${fmt(v)} min/unidad`)],
    respuestas: [num("vu", "Veces por unidad", k, "vez/unidad", 3), num("mnu", "MN por unidad", v, "min/unidad")],
  };
};

G.T03 = (R) => {
  const mn = R.u(0.05, 3, 2), m = R.pick([1, 1, 1, 2, 3]);
  let n = R.i(2, 12);
  if (n <= m) n = m + R.i(1, 6);
  const vu = r3(m / n), v = r3((mn * m) / n), nombre = R.pick(NOMBRES_ELEM);
  return {
    enunciado: `El elemento "${nombre}" tiene MN/vez = ${fmt(mn, 2)} y aparece ${m} ${m === 1 ? "vez" : "veces"} cada ${n} ciclos de trabajo. ¿Cuál es su tiempo en MN por unidad?`,
    pasos: [paso("Veces por unidad", "VEZ/UNIDAD = veces / unidades requeridas", `${m} / ${n}`, `${fmt(vu)} vez/unidad`), paso("MN por unidad", "MN/unidad = MN/vez × VEZ/unidad", `${fmt(mn, 2)} × ${m} / ${n}`, `${fmt(v)} min/unidad`)],
    respuestas: [num("vu", "Veces por unidad", vu, "vez/unidad"), num("mnu", "MN por unidad", v, "min/unidad")],
  };
};

G.T04 = (R) => {
  const nE = R.i(2, 3), nombres = R.sample(NOMBRES_ELEM, nE);
  const elems = nombres.map((nombre) => {
    const filas = observaciones(R, R.u(0.05, 0.5, 2), R.i(2, 4));
    const s = resolverMNvez(filas);
    const porCiclo = R.bool(0.5);
    const k = porCiclo ? R.i(1, 10) : 1, n = porCiclo ? 1 : R.i(2, 8);
    const vu = r3(k / n), mnu = r3((s.mnvez * k) / n);
    return { nombre, filas, s, k, n, vu, mnu, frase: porCiclo ? `${k} ${k === 1 ? "vez" : "veces"} por ciclo` : `1 vez cada ${n} ciclos` };
  });
  const total = r3(elems.reduce((a, e) => a + e.mnu, 0));
  const pasos = [], resp = [];
  elems.forEach((e, idx) => {
    pasos.push(paso(`${e.nombre}, MN por calificación`, "MN = TO(min) × C / 100", e.s.det.map((d) => `${fmt(d.TO, 2)} × ${d.C}/100 = ${fmt(d.MN)}`).join(", "), `ΣMN = ${fmt(e.s.sMN)}, ΣF = ${e.s.sF}`));
    pasos.push(paso(`${e.nombre}, MN/vez y MN/unidad`, "MN/vez = ΣMN/ΣF, MN/unidad = MN/vez × VEZ/unidad", `${fmt(e.s.sMN)} / ${e.s.sF} = ${fmt(e.s.mnvez)}, ${fmt(e.s.mnvez)} × ${e.k}/${e.n}`, `${fmt(e.mnu)} min/unidad`));
    resp.push(num(`mnvez${idx + 1}`, `MN/vez de ${e.nombre}`, e.s.mnvez, "min/vez"));
    resp.push(num(`mnu${idx + 1}`, `MN/unidad de ${e.nombre}`, e.mnu, "min/unidad"));
  });
  pasos.push(paso("Total", "Σ MN/unidad", elems.map((e) => fmt(e.mnu)).join(" + "), `${fmt(total)} min/unidad`));
  resp.push(num("total", "Total MN/unidad", total, "min/unidad"));
  return {
    enunciado: `Hoja de minutos normales. Para cada elemento se dan las lecturas en centiminutos por calificación y su frecuencia en el ciclo. ${elems.map((e) => `${e.nombre} (${e.frase}), ${e.filas.map((f) => `C ${f.C} = ${f.obs.join(", ")}`).join(". ")}`).join(". ")}. Calcula MN/vez y MN/unidad de cada elemento y el total por unidad.`,
    tabla: { columnas: ["Elemento", "Frecuencia", "C", "Lecturas (cmin)"], filas: elems.flatMap((e) => e.filas.map((f, j) => [j === 0 ? e.nombre : "", j === 0 ? e.frase : "", f.C, f.obs.join(", ")])) },
    pasos, respuestas: resp,
  };
};

G.T05 = (R) => {
  for (;;) {
    const c = R.pick(CONTEXTOS), kg = R.pick([10, 15, 20, 25, 30, 40, 50]);
    const g = R.u(c.masa[0], c.masa[1], c.masa[1] < 1 ? 3 : 1), lote = R.pick(c.lote);
    const totalKg = r3((lote * g) / 1000), vu = r3(totalKg / kg);
    if (vu < 0.3 || vu > 8) continue;
    return {
      enunciado: `En ${c.lugar} la tolva de la máquina se alimenta con ${kg} kg cada vez que se consume la materia prima. Cada ${c.pieza} pesa aproximadamente ${g} g y cada unidad de producción (lote) es de ${fi(lote)} ${c.producto}. ¿Cuántas veces por unidad se alimenta la tolva?`,
      pasos: [
        paso("Materia prima por lote", "kg/unidad = piezas × g/pieza × (1 kg / 1000 g)", `${fi(lote)} × ${g} / 1000`, `${fmt(totalKg)} kg/unidad`),
        paso("Veces por unidad", "VEZ/unidad = (kg/unidad) / (kg/vez)", `${fmt(totalKg)} / ${kg}`, `${fmt(vu)} vez/unidad`),
      ],
      respuestas: [num("kg", "Materia prima por lote", totalKg, "kg"), num("vu", "Veces por unidad", vu, "vez/unidad")],
    };
  }
};

G.T06 = (R) => {
  const c = R.pick(CONTEXTOS), n = R.i(4, 40), k = R.i(2, 12);
  const mR = R.u(2, 12, 3), mL = R.u(1, 6, 3), vu = r3(n / k);
  const a = r3(mR * vu), b = r3(mL * vu);
  return {
    enunciado: `En ${c.lugar} cada ${k} unidades productivas se reemplazan y limpian ${c.art || "los"} ${n} ${c.herr} de la máquina. Reemplazar ${c.un || "un"} ${c.herrS} toma ${fmt(mR)} MN/vez y limpiarlo ${fmt(mL)} MN/vez. Calcula las veces por unidad y los MN por unidad de ambos elementos.`,
    pasos: [
      paso("Veces por unidad", "VEZ/unidad = veces / unidades", `${n} / ${k}`, `${fmt(vu)} vez/unidad`),
      paso("Reemplazar", "MN/unidad = MN/vez × VEZ/unidad", `${fmt(mR)} × ${fmt(vu)}`, `${fmt(a)} min/unidad`),
      paso("Limpiar", "MN/unidad = MN/vez × VEZ/unidad", `${fmt(mL)} × ${fmt(vu)}`, `${fmt(b)} min/unidad`),
    ],
    respuestas: [num("vu", "Veces por unidad", vu, "vez/unidad"), num("rep", "MN/unidad de reemplazar", a, "min/unidad"), num("lim", "MN/unidad de limpiar", b, "min/unidad")],
  };
};

G.T07 = (R) => {
  const n = R.i(3, 6), nombres = R.sample(NOMBRES_ELEM, n), p = R.i(1, 5);
  const vals = nombres.map(() => R.u(0.3, 25, 3));
  const tot = r3(vals.reduce((a, b) => a + b, 0)), misc = r3((tot * p) / 100), con = r3(tot + misc);
  return {
    enunciado: `Los elementos normalizados de una operación son ${nombres.map((x, i) => `${x} ${fmt(vals[i])} MN/unidad`).join(", ")}. La empresa asigna ${p} % por misceláneos. Calcula el total de elementos, el tiempo de misceláneos y el total con misceláneos.`,
    tabla: { columnas: ["Elemento", "MN/unidad"], filas: nombres.map((x, i) => [x, fmt(vals[i])]) },
    pasos: [paso("Total de elementos", "Σ MN/unidad", vals.map((v) => fmt(v)).join(" + "), `${fmt(tot)} min/unidad`), paso("Misceláneos", "Misc = % × Σ elementos", `${p}% × ${fmt(tot)}`, `${fmt(misc)} min/unidad`), paso("Total con misceláneos", "Σ + Misc", `${fmt(tot)} + ${fmt(misc)}`, `${fmt(con)} min/unidad`)],
    respuestas: [num("tot", "Total de elementos", tot, "min/unidad"), num("misc", "Misceláneos", misc, "min/unidad"), num("con", "Total con misceláneos", con, "min/unidad")],
  };
};

G.T08 = (R) => {
  const c = R.pick(CONTEXTOS), lote = R.pick(c.lote), porHora = R.bool(0.3);
  const velMin = R.pick(c.vel), vel = porHora ? velMin * 60 : velMin;
  const frac = rd(velMin / lote, 5), TM = r3(lote / velMin);
  const pasos = [];
  if (porHora) pasos.push(paso("Velocidad por minuto", "piezas/min = piezas/h / 60", `${fi(vel)} / 60`, `${fi(velMin)} ${c.producto}/min`));
  pasos.push(paso("Fracción del lote por minuto", "unidades/min = piezas/min × (1 unidad / piezas por lote)", `${fi(velMin)} / ${fi(lote)}`, `${frac} unidades/min (${rd(frac * 100, 3)} % del lote)`));
  pasos.push(paso("Tiempo de máquina", "TM = 1 / (unidades/min)", `1 / ${frac}`, `${fmt(TM)} min/unidad`));
  return {
    enunciado: `En ${c.lugar} la velocidad de entrega de la máquina es de ${fi(vel)} ${c.producto} por ${porHora ? "hora" : "minuto"} y cada ciclo (unidad) produce ${fi(lote)} ${c.producto}. Calcula la fracción del lote que se produce en un minuto y el tiempo condicionado por la máquina (TM).`,
    pasos,
    respuestas: [num("frac", "Unidades por minuto", frac, "unidad/min", 5), num("TM", "Tiempo de máquina", TM, "min/unidad")],
  };
};

function elementosMaquina(R, c, nInt) {
  const base = [`Alimentar tolva`, c.revisar, `Llevar ${c.producto} al siguiente proceso`, `Limpiar ${c.herr}`, `Etiquetar contenedor`, `Registrar producción`];
  const ints = R.sample(base, nInt).map((nombre) => ({ nombre, tipo: "interior", mnu: R.u(0.4, 18, 3) }));
  const exts = [{ nombre: `Reemplazar ${c.herr}`, tipo: "exterior", mnu: R.u(8, 40, 3) }];
  if (R.bool(0.35)) exts.push({ nombre: "Ajustar la máquina", tipo: "exterior", mnu: R.u(1, 6, 3) });
  return R.shuffle([...ints, ...exts]);
}

G.T09 = (R) => {
  for (;;) {
    const c = R.pick(CONTEXTOS), els = elementosMaquina(R, c, R.i(2, 4)), p = R.i(1, 5);
    const sE = r3(els.filter((e) => e.tipo === "exterior").reduce((a, e) => a + e.mnu, 0));
    const sI = r3(els.filter((e) => e.tipo === "interior").reduce((a, e) => a + e.mnu, 0));
    const ETE = r3(sE + (sE * p) / 100), ETI = r3(sI + (sI * p) / 100);
    const TM = R.pick([30, 40, 45, 50, 60, 75, 80, 90, 100, 120]);
    if (ETI >= TM - 1) continue;
    const TNO = r3(TM - ETI), TC = r3(ETE + TM);
    return {
      enunciado: `En ${c.lugar} se tienen los siguientes elementos en MN/unidad, ${els.map((e) => `${e.nombre} ${fmt(e.mnu)} (${e.tipo === "exterior" ? "con la máquina detenida" : "con la máquina en marcha"})`).join(", ")}. Se asigna ${p} % de misceláneos a cada grupo y el tiempo de máquina es TM = ${TM} min/unidad. Calcula ETE, ETI, TNO y el tiempo ciclo.`,
      tabla: { columnas: ["Elemento", "MN/unidad", "Condición de la máquina"], filas: els.map((e) => [e.nombre, fmt(e.mnu), e.tipo === "exterior" ? "Detenida" : "En marcha"]) },
      pasos: [
        paso("Trabajo exterior", "ETE = Σ elementos con máquina detenida × (1 + %misc)", `${fmt(sE)} + ${p}%(${fmt(sE)})`, `${fmt(ETE)} min/unidad`),
        paso("Trabajo interior", "ETI = Σ elementos con máquina en marcha × (1 + %misc)", `${fmt(sI)} + ${p}%(${fmt(sI)})`, `${fmt(ETI)} min/unidad`),
        paso("Tiempo no ocupado", "TNO = TM − ETI", `${TM} − ${fmt(ETI)}`, `${fmt(TNO)} min/unidad`),
        paso("Tiempo ciclo", "TC = ETE + TM", `${fmt(ETE)} + ${TM}`, `${fmt(TC)} min/unidad`),
      ],
      respuestas: [num("ETE", "ETE", ETE, "min/unidad"), num("ETI", "ETI", ETI, "min/unidad"), num("TNO", "TNO", TNO, "min/unidad"), num("TC", "Tiempo ciclo", TC, "min/unidad")],
    };
  }
};

G.T10 = (R) => {
  const ETE = R.u(5, 45, 3), TM = R.pick([30, 40, 50, 60, 75, 90]), p = R.i(5, 7);
  const TC = r3(ETE + TM), SNP = r3((TC * p) / 100);
  return {
    enunciado: `Una operación con máquina tiene ETE = ${fmt(ETE)} min/unidad y TM = ${TM} min/unidad. La empresa da ${p} % del tiempo del ciclo para necesidades personales. Calcula el tiempo ciclo y el SNP.`,
    pasos: [paso("Tiempo ciclo", "TC = ETE + TM = ETE + ETI + TNO", `${fmt(ETE)} + ${TM}`, `${fmt(TC)} min/unidad`), paso("SNP", "SNP = % × TC", `${p}% × ${fmt(TC)}`, `${fmt(SNP)} min/unidad`)],
    respuestas: [num("TC", "Tiempo ciclo", TC, "min/unidad"), num("SNP", "SNP", SNP, "min/unidad")],
  };
};

G.T11 = (R) => {
  const n = R.i(3, 5), nombres = [...R.sample(NOMBRES_ELEM, n), "Misceláneos"];
  const filas = nombres.map((x, i) => {
    const mnu = i === n ? R.u(0.2, 2, 3) : R.u(0.5, 35, 3), pct = R.i(4, 35);
    return { x, mnu, pct, sf: r3((mnu * pct) / 100) };
  });
  const SF = r3(filas.reduce((a, f) => a + f.sf, 0));
  return {
    enunciado: `De acuerdo con las tablas de la empresa, los porcentajes de suplemento por fatiga son ${filas.map((f) => `${f.x} ${fmt(f.mnu)} MN/unidad con ${f.pct} %`).join(", ")}. Calcula el suplemento de cada elemento y el SF total.`,
    tabla: { columnas: ["Elemento", "MN/unidad", "% de suplemento"], filas: filas.map((f) => [f.x, fmt(f.mnu), f.pct]) },
    pasos: [...filas.map((f) => paso(f.x, "MN/unidad × %", `${fmt(f.mnu)} × ${f.pct}%`, `${fmt(f.sf)} min/unidad`)), paso("SF total", "SF = Σ suplementos", filas.map((f) => fmt(f.sf)).join(" + "), `${fmt(SF)} min/unidad`)],
    respuestas: [...filas.map((f, i) => num(`sf${i + 1}`, `Suplemento de ${f.x}`, f.sf, "min/unidad")), num("SF", "SF total", SF, "min/unidad")],
  };
};

export function utilizable(TNO) {
  if (TNO < 0.5) return 0;
  if (TNO < 1.5) return r3((TNO - 0.5) * 1.5);
  return TNO;
}

G.T12 = (R) => {
  const TNO = R.pick([R.u(0.1, 0.49, 2), R.u(0.5, 1.49, 2), R.u(0.5, 1.49, 2), R.u(1.5, 3, 2)]);
  const SF = R.u(0.05, 1.6, 3), u = utilizable(TNO), dentro = r3(Math.min(SF, u)), fuera = r3(SF - dentro);
  const caso = TNO < 0.5 ? "A" : TNO < 1.5 ? "B" : "C";
  const desc = { A: "TNO menor a 0.5 min, no cuenta como respiro", B: "TNO entre 0.5 y 1.5 min, se usa (TNO − 0.5) × 1.5", C: "TNO de 1.5 min o más, cuenta completo para reponerse" };
  return {
    enunciado: `En un ciclo breve el operario tiene un tiempo no ocupado ininterrumpido de ${fmt(TNO, 2)} min por ciclo, puede sentarse y no necesita vigilar la máquina. El suplemento por fatiga es SF = ${fmt(SF)} min por ciclo. ¿Qué caso aplica, cuánto tiempo es utilizable para reponerse y cuánto SF debe darse fuera del ciclo?`,
    pasos: [paso("Caso", "TNO < 0.5, 0.5 ≤ TNO < 1.5, TNO ≥ 1.5", `TNO = ${fmt(TNO, 2)}`, desc[caso]), paso("Tiempo utilizable", caso === "B" ? "TED = (TNO − 0.5) × 1.5" : caso === "A" ? "TED = 0" : "TED = TNO", caso === "B" ? `(${fmt(TNO, 2)} − 0.5) × 1.5` : `${fmt(TNO, 2)}`, `${fmt(u)} min`), paso("SF dentro y fuera", "Dentro = mínimo(SF, TED), Fuera = SF − Dentro", `mín(${fmt(SF)}, ${fmt(u)})`, `Dentro ${fmt(dentro)}, fuera ${fmt(fuera)} min`)],
    respuestas: [opc("caso", "Caso que aplica", caso, [{ valor: "A", texto: desc.A }, { valor: "B", texto: desc.B }, { valor: "C", texto: desc.C }]), num("TED", "Tiempo utilizable para reponerse", u, "min"), num("fuera", "SF que se da fuera", fuera, "min")],
  };
};

function asignarLargo(TNO, SNP, SF, inint, puede) {
  const snpDentro = TNO > 10 && inint && puede;
  const cap = snpDentro ? TNO - SNP : TNO;
  const sfDentro = r3(Math.max(0, Math.min(SF, cap)));
  const sfFuera = r3(SF - sfDentro);
  const sfCaso = sfFuera === 0 ? "dentro" : sfDentro === 0 ? "fuera" : "parte";
  return { snpDentro, sfDentro, sfFuera, sfCaso, fuera: r3((snpDentro ? 0 : SNP) + sfFuera) };
}
const OPC_SNP = [{ valor: "dentro", texto: "Dentro del TNO" }, { valor: "fuera", texto: "Fuera del TNO" }];
const OPC_SF = [{ valor: "dentro", texto: "Todo dentro del TNO" }, { valor: "parte", texto: "Parte dentro y parte fuera" }, { valor: "fuera", texto: "Todo fuera" }];
function razonesSNP(TNO, inint, puede) {
  return `Condición 1 TNO > 10 min ${TNO > 10 ? "sí" : "no"} se cumple, condición 2 TNO ininterrumpido ${inint ? "sí" : "no"} se cumple, condición 3 dejar la máquina sin atender ${puede ? "sí" : "no"} se cumple`;
}

G.T13 = (R) => {
  for (;;) {
    const TNO = R.u(4, 40, 3), SNP = R.u(2, 8, 3), SF = R.u(2, 16, 3);
    const inint = R.bool(0.5), puede = R.bool(0.5);
    const a = asignarLargo(TNO, SNP, SF, inint, puede);
    if (a.snpDentro && TNO < SNP + SF) continue;
    const motivoPuede = puede ? R.pick(["la máquina es automática", "otros operarios vecinos pueden vigilar la máquina"]) : "nadie más puede vigilar la máquina";
    return {
      enunciado: `En un ciclo largo el TNO es de ${fmt(TNO)} min/unidad ${inint ? "y es ininterrumpido" : "y está repartido en varios periodos"}. Además ${motivoPuede}. Se calcularon SNP = ${fmt(SNP)} y SF = ${fmt(SF)} min/unidad. Decide dónde se asigna cada suplemento y calcula cuántos minutos de suplemento quedan fuera del TNO.`,
      pasos: [
        paso("SNP", "Dentro solo si TNO > 10, TNO ininterrumpido y se puede dejar la máquina", razonesSNP(TNO, inint, puede), a.snpDentro ? "El SNP va dentro del TNO" : "El SNP va fuera del TNO"),
        paso("SF", "Dentro si TNO > SF, si TNO < SF una parte dentro y el resto fuera", `TNO disponible ${fmt(a.snpDentro ? TNO - SNP : TNO)} contra SF ${fmt(SF)}`, `Dentro ${fmt(a.sfDentro)}, fuera ${fmt(a.sfFuera)}`),
        paso("Suplementos fuera del TNO", "SNP fuera + SF fuera", `${fmt(a.snpDentro ? 0 : SNP)} + ${fmt(a.sfFuera)}`, `${fmt(a.fuera)} min/unidad`),
      ],
      respuestas: [opc("snp", "Asignación del SNP", a.snpDentro ? "dentro" : "fuera", OPC_SNP), opc("sf", "Asignación del SF", a.sfCaso, OPC_SF), num("fuera", "Suplementos fuera del TNO", a.fuera, "min/unidad")],
    };
  }
};

G.T14 = (R) => {
  for (;;) {
    const ETE = R.u(5, 40, 3), TM = R.pick([30, 40, 45, 50, 60, 75, 90]), ETI = R.u(2, TM - 3, 3), p = R.i(5, 7);
    const SF = R.u(2, 14, 3), inint = R.bool(0.4), puede = R.bool(0.4);
    const TNO = r3(TM - ETI), TC = r3(ETE + TM), SNP = r3((TC * p) / 100);
    const a = asignarLargo(TNO, SNP, SF, inint, puede);
    if (a.snpDentro && TNO < SNP + SF) continue;
    const TT = r3(ETE + TM + a.fuera);
    return {
      enunciado: `Una máquina tiene ETE = ${fmt(ETE)}, ETI = ${fmt(ETI)} y TM = ${TM} min/unidad. El SNP es ${p} % del tiempo ciclo y el SF calculado es ${fmt(SF)} min/unidad. El TNO ${inint ? "es ininterrumpido" : "no es ininterrumpido"} y el operario ${puede ? "puede" : "no puede"} dejar la máquina sin atender. Calcula TNO, SNP y el tiempo tipo.`,
      pasos: [
        paso("TNO", "TNO = TM − ETI", `${TM} − ${fmt(ETI)}`, `${fmt(TNO)} min/unidad`),
        paso("SNP", "SNP = % × (ETE + TM)", `${p}% × (${fmt(ETE)} + ${TM})`, `${fmt(SNP)} min/unidad`),
        paso("Asignación del SNP", "Tres condiciones", razonesSNP(TNO, inint, puede), a.snpDentro ? "Dentro" : "Fuera"),
        paso("Asignación del SF", "TNO disponible contra SF", `${fmt(a.snpDentro ? TNO - SNP : TNO)} contra ${fmt(SF)}`, `Dentro ${fmt(a.sfDentro)}, fuera ${fmt(a.sfFuera)}`),
        paso("Tiempo tipo", "TT = ETE + TM + suplementos que quedan fuera del TNO", `${fmt(ETE)} + ${TM} + ${fmt(a.fuera)}`, `${fmt(TT)} min/unidad`),
      ],
      respuestas: [num("TNO", "TNO", TNO, "min/unidad"), num("SNP", "SNP", SNP, "min/unidad"), opc("snp", "Asignación del SNP", a.snpDentro ? "dentro" : "fuera", OPC_SNP), num("TT", "Tiempo tipo", TT, "min/unidad")],
    };
  }
};

G.T15 = (R) => {
  const n = R.i(3, 5), nombres = R.sample(NOMBRES_ELEM, n), vals = nombres.map(() => R.u(0.1, 3, 3));
  const p = R.i(1, 5), snp = R.i(5, 7), sf = R.pick([4, 5, 6, 8, 10, 12]);
  const tot = r3(vals.reduce((a, b) => a + b, 0)), misc = r3((tot * p) / 100), base = r3(tot + misc);
  const SNP = r3((base * snp) / 100), SF = r3((base * sf) / 100), TT = r3(base + SNP + SF);
  return {
    enunciado: `Un trabajo manual sin máquina tiene los elementos normalizados ${nombres.map((x, i) => `${x} ${fmt(vals[i])}`).join(", ")} en MN/unidad. Se asigna ${p} % de misceláneos, ${snp} % de suplemento por necesidades personales y ${sf} % de suplemento por fatiga, ambos sobre el tiempo con misceláneos. Calcula el tiempo tipo.`,
    pasos: [paso("Elementos normalizados", "Σ MN/unidad", vals.map((v) => fmt(v)).join(" + "), `${fmt(tot)}`), paso("Misceláneos", "% × Σ", `${p}% × ${fmt(tot)}`, `${fmt(misc)}`), paso("Suplementos", "SNP = % × (Σ + misc), SF = % × (Σ + misc)", `${snp}% × ${fmt(base)}, ${sf}% × ${fmt(base)}`, `SNP ${fmt(SNP)}, SF ${fmt(SF)}`), paso("Tiempo tipo", "TT = elementos normalizados + misceláneos + suplementos", `${fmt(tot)} + ${fmt(misc)} + ${fmt(SNP)} + ${fmt(SF)}`, `${fmt(TT)} min/unidad`)],
    respuestas: [num("misc", "Misceláneos", misc, "min/unidad"), num("sup", "Suplementos totales", r3(SNP + SF), "min/unidad"), num("TT", "Tiempo tipo", TT, "min/unidad")],
  };
};

function jornada(R) {
  const h = R.pick([8, 8, 8, 7, 9]), turnos = R.pick([1, 2, 3, 3]), dias = R.pick([5, 6, 6]);
  const limpH = R.pick([0.5, 1, 1, 1.5, 2]), limpV = R.i(1, 4);
  const ropa = R.pick([10, 15, 20, 30]), comida = R.pick([20, 30, 30, 45]);
  const limpMin = r3((limpH * limpV * 60) / (dias * turnos));
  const real = r3(h * 60 - (limpMin + ropa + comida));
  return { h, turnos, dias, limpH, limpV, ropa, comida, limpMin, real };
}
function textoJornada(j) {
  return `La limpieza de la máquina demora ${j.limpH} h y se hace ${j.limpV} ${j.limpV === 1 ? "vez" : "veces"} por semana. Para cambio de ropa se dan ${j.ropa} min por turno y para comida ${j.comida} min por turno. La empresa trabaja ${j.dias} días a la semana, ${j.turnos} ${j.turnos === 1 ? "turno" : "turnos"} al día de ${j.h} h cada uno.`;
}
function pasosJornada(j) {
  return [
    paso("Minutos del turno", "h/turno × 60", `${j.h} × 60`, `${j.h * 60} min/turno`),
    paso("Limpieza por turno", "h/vez × veces/semana × (1 semana / días) × (1 día / turnos) × 60", `${j.limpH} × ${j.limpV} / ${j.dias} / ${j.turnos} × 60`, `${fmt(j.limpMin)} min/turno`),
    paso("Tiempo real del turno", "min/turno − suplementos de jornada", `${j.h * 60} − (${fmt(j.limpMin)} + ${j.ropa} + ${j.comida})`, `${fmt(j.real)} min/turno`),
  ];
}

G.T16 = (R) => {
  const j = jornada(R), TT = R.u(15, 120, 3), prod = r3(j.real / TT);
  return {
    enunciado: `El tiempo tipo de una unidad es TT = ${fmt(TT)} min/unidad. ${textoJornada(j)} Calcula el tiempo de limpieza por turno, el tiempo real del turno y la producción esperada por turno.`,
    pasos: [...pasosJornada(j), paso("Producción esperada", "unidades/turno = tiempo real / TT", `${fmt(j.real)} / ${fmt(TT)}`, `${fmt(prod)} unidades/turno`)],
    respuestas: [num("limp", "Limpieza por turno", j.limpMin, "min/turno"), num("real", "Tiempo real del turno", j.real, "min/turno"), num("prod", "Producción esperada", prod, "unidades/turno")],
  };
};

G.T17 = (R) => {
  for (;;) {
    const ETE = R.u(0.5, 30, 3), TM = R.pick([5, 8, 10, 15, 20, 30, 40, 50, 60, 90]), ETI = R.u(0.2, TM * 0.8, 3);
    const carga = rd((ETE + ETI) / (ETE + TM), 4);
    if (carga > 0.98) continue;
    const nMaq = rd(1 / carga, 2), ent = Math.floor(1 / carga);
    return {
      enunciado: `Una operación con máquina tiene ETE = ${fmt(ETE)}, ETI = ${fmt(ETI)} y TM = ${TM} min/unidad. Calcula la carga de trabajo del operario y el número teórico de máquinas que puede atender.`,
      pasos: [paso("Carga de trabajo", "Carga = (ETE + ETI) / (ETE + TM)", `(${fmt(ETE)} + ${fmt(ETI)}) / (${fmt(ETE)} + ${TM})`, `${carga} = ${rd(carga * 100, 2)} %`), paso("Número de máquinas", "N = 1 / carga, se toma la parte entera", `1 / ${carga}`, `${nMaq}, el operario puede atender ${ent} ${ent === 1 ? "máquina" : "máquinas"}`)],
      respuestas: [num("carga", "Carga de trabajo (fracción)", carga, "", 4), num("n", "Número teórico de máquinas", nMaq, "máquinas", 2), num("ent", "Máquinas que puede atender", ent, "máquinas", 0)],
    };
  }
};

G.T18 = (R) => {
  const mn = R.u(0.05, 2, 2), porCiclo = R.bool(0.5);
  const k = porCiclo ? R.i(2, 10) : 1, n = porCiclo ? 1 : R.i(2, 10);
  const mnu = r3((mn * k) / n), vu = r3(mnu / mn);
  return {
    enunciado: `Un elemento tiene MN/vez = ${fmt(mn, 2)} y MN/unidad = ${fmt(mnu)}. Calcula las veces por unidad e indica con qué frecuencia aparece el elemento.`,
    pasos: [paso("Veces por unidad", "VEZ/unidad = (MN/unidad) / (MN/vez)", `${fmt(mnu)} / ${fmt(mn, 2)}`, `${fmt(vu)} vez/unidad`), paso("Interpretación", porCiclo ? "Si VEZ/unidad ≥ 1, aparece varias veces por ciclo" : "Si VEZ/unidad < 1, aparece una vez cada 1/(VEZ/unidad) ciclos", porCiclo ? `${fmt(vu)}` : `1 / ${fmt(vu)}`, porCiclo ? `${k} veces por ciclo` : `1 vez cada ${n} ciclos`)],
    respuestas: [num("vu", "Veces por unidad", vu, "vez/unidad"), num("n", porCiclo ? "Veces por ciclo" : "Cada cuántos ciclos aparece", porCiclo ? k : n, "", 0)],
  };
};

G.T19 = (R) => {
  const j = jornada(R), meta = R.i(3, 25), TTact = R.u(10, 140, 3);
  const TTmax = r3(j.real / meta), cumple = TTact <= TTmax ? "si" : "no";
  return {
    enunciado: `La empresa quiere producir al menos ${meta} unidades por turno. ${textoJornada(j)} El tiempo tipo actual es de ${fmt(TTact)} min/unidad. ¿Cuál es el tiempo tipo máximo que permite cumplir la meta y se cumple con el método actual?`,
    pasos: [...pasosJornada(j), paso("Tiempo tipo máximo", "TT máx = tiempo real / meta", `${fmt(j.real)} / ${meta}`, `${fmt(TTmax)} min/unidad`), paso("Comparación", "TT actual ≤ TT máx", `${fmt(TTact)} contra ${fmt(TTmax)}`, cumple === "si" ? "Sí se cumple la meta" : "No se cumple la meta")],
    respuestas: [num("real", "Tiempo real del turno", j.real, "min/turno"), num("TTmax", "Tiempo tipo máximo", TTmax, "min/unidad"), opc("cumple", "¿Se cumple la meta?", cumple, [{ valor: "si", texto: "Sí" }, { valor: "no", texto: "No" }])],
  };
};

// Caso integral. Con semilla 0 reproduce el ejemplo de clase del laboratorio farmacéutico.
export function casoIntegral(d) {
  const totalKg = r3((d.lote * d.g) / 1000), vuA = r3(totalKg / d.kg), vuH = d.vuH ?? r3(d.nHerr / d.cadaN);
  const els = d.elems.map((e) => {
    const vu = e.clave === "A" ? vuA : e.clave === "B" || e.clave === "C" ? vuH : 1;
    return { ...e, vu, mnu: r3(e.mnvez * vu) };
  });
  const total = r3(els.reduce((a, e) => a + e.mnu, 0)), misc = r3((total * d.pm) / 100);
  const sE = r3(els.filter((e) => e.tipo === "exterior").reduce((a, e) => a + e.mnu, 0));
  const sI = r3(els.filter((e) => e.tipo === "interior").reduce((a, e) => a + e.mnu, 0));
  const ETE = r3(sE + (sE * d.pm) / 100), ETI = r3(sI + (sI * d.pm) / 100);
  const frac = rd(d.vel / d.lote, 5), TM = r3(d.lote / d.vel), TNO = r3(TM - ETI);
  const TC = r3(ETE + TM), SNP = r3((TC * d.psnp) / 100);
  const sfF = [...els.map((e) => ({ x: e.nombre, mnu: e.mnu, pct: e.pf })), { x: "Misceláneos", mnu: misc, pct: d.pfm }].map((f) => ({ ...f, sf: r3((f.mnu * f.pct) / 100) }));
  const SF = r3(sfF.reduce((a, f) => a + f.sf, 0));
  const a = asignarLargo(TNO, SNP, SF, d.inint, d.puede);
  const TT = r3(ETE + TM + a.fuera);
  const j = d.j, prod = r3(j.real / TT);
  const carga = rd((ETE + ETI) / (ETE + TM), 4), nMaq = rd(1 / carga, 2), ent = Math.floor(1 / carga);
  return { totalKg, vuA, vuH, els, total, misc, sE, sI, ETE, ETI, frac, TM, TNO, TC, SNP, sfF, SF, a, TT, prod, carga, nMaq, ent };
}

function armarIntegral(d) {
  const s = casoIntegral(d), c = d.c, j = d.j;
  const enunciado = `En ${c.lugar} se tienen los siguientes datos para la fabricación de ${c.producto}. ${d.elems.map((e) => `${e.clave}. ${e.nombre} ${fmt(e.mnvez)} MN/vez`).join(", ")}, y ${d.pm} % de misceláneos. La tolva se alimenta con ${d.kg} kg cada vez que se consume la materia prima. Cada ${d.cadaN} unidades productivas se reemplazan y limpian ${c.art || "los"} ${d.nHerr} ${c.herr} de la máquina, y el elemento B se hace con la máquina inactiva. Los elementos ${d.elems.filter((e) => e.clave >= "D").map((e) => e.clave).join(" y ")} se hacen una vez en cada ciclo. Los elementos ${lista(d.elems.filter((e) => e.tipo === "interior").map((e) => e.clave))} se hacen con la máquina en marcha. La velocidad de entrega es de ${fi(d.vel)} ${c.producto}/min, cada ciclo produce ${fi(d.lote)} ${c.producto} de aproximadamente ${d.g} g. La empresa da ${d.psnp} % del tiempo ciclo para necesidades personales y los porcentajes de fatiga son ${d.elems.map((e) => `${e.clave} ${e.pf} %`).join(", ")} y misceláneos ${d.pfm} %. El TNO ${d.inint ? "es ininterrumpido" : "no es ininterrumpido"} y el operario ${d.puede ? "puede" : "no puede"} dejar la máquina sola. ${textoJornada(j)} Calcula los tiempos, los suplementos, el tiempo tipo, la producción esperada, la carga de trabajo y verifica si se le puede asignar más de una máquina.`;
  const pasos = [
    paso("Veces por unidad de A", "VEZ/u = (piezas × g / 1000) / kg por vez", `(${fi(d.lote)} × ${d.g} / 1000) / ${d.kg}`, `${fmt(s.vuA)} vez/unidad`),
    paso("Veces por unidad de B y C", "VEZ/u = piezas / unidades", `${d.nHerr} / ${d.cadaN}`, `${fmt(s.vuH)} vez/unidad${d.vuH ? " (la diapositiva de clase usa 3.830, el cociente exacto es 3.833)" : ""}`),
    ...s.els.map((e) => paso(`MN/unidad de ${e.clave}`, "MN/vez × VEZ/unidad", `${fmt(e.mnvez)} × ${fmt(e.vu)}`, `${fmt(e.mnu)}`)),
    paso("Total y misceláneos", "Σ MN/unidad, misc = % × Σ", `${fmt(s.total)} × ${d.pm}%`, `Total ${fmt(s.total)}, misc ${fmt(s.misc)}`),
    paso("Tiempo de máquina", "TM = piezas por lote / velocidad", `${fi(d.lote)} / ${fi(d.vel)}`, `${fmt(s.TM)} min/unidad`),
    paso("ETE", "Σ exteriores × (1 + %misc)", `${fmt(s.sE)} + ${d.pm}%(${fmt(s.sE)})`, `${fmt(s.ETE)}`),
    paso("ETI", "Σ interiores × (1 + %misc)", `${fmt(s.sI)} + ${d.pm}%(${fmt(s.sI)})`, `${fmt(s.ETI)}`),
    paso("TNO", "TM − ETI", `${fmt(s.TM)} − ${fmt(s.ETI)}`, `${fmt(s.TNO)}`),
    paso("SNP", "% × (ETE + TM)", `${d.psnp}% × (${fmt(s.ETE)} + ${fmt(s.TM)})`, `${fmt(s.SNP)}`),
    paso("SF", "Σ MN/unidad × % de fatiga", s.sfF.map((f) => fmt(f.sf)).join(" + "), `${fmt(s.SF)}`),
    paso("Asignación", "SNP con tres condiciones, SF contra TNO", razonesSNP(s.TNO, d.inint, d.puede), `SNP ${s.a.snpDentro ? "dentro" : "fuera"}, SF dentro ${fmt(s.a.sfDentro)} y fuera ${fmt(s.a.sfFuera)}`),
    paso("Tiempo tipo", "TT = ETE + TM + suplementos fuera del TNO", `${fmt(s.ETE)} + ${fmt(s.TM)} + ${fmt(s.a.fuera)}`, `${fmt(s.TT)} min/unidad`),
    ...pasosJornada(j),
    paso("Producción esperada", "tiempo real / TT", `${fmt(j.real)} / ${fmt(s.TT)}`, `${fmt(s.prod)} unidades/turno`),
    paso("Carga de trabajo", "(ETE + ETI) / (ETE + TM)", `(${fmt(s.ETE)} + ${fmt(s.ETI)}) / (${fmt(s.ETE)} + ${fmt(s.TM)})`, `${s.carga} = ${rd(s.carga * 100, 2)} %`),
    paso("Número de máquinas", "1 / carga", `1 / ${s.carga}`, `${s.nMaq}, puede atender ${s.ent} ${s.ent === 1 ? "máquina" : "máquinas"}`),
  ];
  const respuestas = [
    num("vuA", "Veces por unidad de A", s.vuA, "vez/unidad"), num("vuH", "Veces por unidad de B y C", s.vuH, "vez/unidad"),
    num("total", "Total MN/unidad sin misceláneos", s.total, "min/unidad"), num("misc", "Misceláneos", s.misc, "min/unidad"),
    num("TM", "TM", s.TM, "min/unidad"), num("ETE", "ETE", s.ETE, "min/unidad"), num("ETI", "ETI", s.ETI, "min/unidad"),
    num("TNO", "TNO", s.TNO, "min/unidad"), num("SNP", "SNP", s.SNP, "min/unidad"), num("SF", "SF", s.SF, "min/unidad"),
    opc("snp", "Asignación del SNP", s.a.snpDentro ? "dentro" : "fuera", OPC_SNP), opc("sf", "Asignación del SF", s.a.sfCaso, OPC_SF),
    num("TT", "Tiempo tipo", s.TT, "min/unidad"), num("real", "Tiempo real del turno", j.real, "min/turno"),
    num("prod", "Producción esperada", s.prod, "unidades/turno"), num("carga", "Carga de trabajo (fracción)", s.carga, "", 4),
    num("ent", "Máquinas que puede atender", s.ent, "máquinas", 0),
  ];
  return { enunciado, tabla: { columnas: ["Elemento", "MN/vez", "Máquina", "% fatiga"], filas: d.elems.map((e) => [`${e.clave}. ${e.nombre}`, fmt(e.mnvez), e.tipo === "exterior" ? "Detenida" : "En marcha", e.pf]) }, pasos, respuestas, solucion: s };
}

export const EJEMPLO_CLASE = {
  c: CONTEXTOS[0], kg: 20, g: 0.283, lote: 100000, vel: 2000, nHerr: 23, cadaN: 6, pm: 2, psnp: 7, pfm: 9,
  inint: false, puede: false, vuH: 3.83,
  elems: [
    { clave: "A", nombre: "Alimentar tolva", mnvez: 0.904, tipo: "interior", pf: 32 },
    { clave: "B", nombre: "Reemplazar punzón", mnvez: 9.122, tipo: "exterior", pf: 12 },
    { clave: "C", nombre: "Limpiar punzón", mnvez: 4.4, tipo: "interior", pf: 12 },
    { clave: "D", nombre: "Revisar consistencia", mnvez: 0.845, tipo: "interior", pf: 14 },
    { clave: "E", nombre: "Llevar tabletas al próximo proceso", mnvez: 0.993, tipo: "interior", pf: 13 },
  ],
  j: { h: 8, turnos: 3, dias: 6, limpH: 1, limpV: 2, ropa: 30, comida: 20, limpMin: 6.667, real: 423.333 },
};

G.T20 = (R, seed) => {
  if (seed === 0) return armarIntegral(EJEMPLO_CLASE);
  for (;;) {
    const c = R.pick(CONTEXTOS), lote = R.pick(c.lote), vel = R.pick(c.vel);
    const TM = lote / vel;
    if (TM < 20 || TM > 150) continue;
    const g = R.u(c.masa[0], c.masa[1], c.masa[1] < 1 ? 3 : 1), kg = R.pick([10, 15, 20, 25, 30, 40, 50]);
    const vuA = (lote * g) / 1000 / kg;
    if (vuA < 0.4 || vuA > 5) continue;
    const intD = R.bool(0.85);
    const elems = [
      { clave: "A", nombre: "Alimentar tolva", mnvez: R.u(0.3, 2, 3), tipo: "interior", pf: R.i(10, 35) },
      { clave: "B", nombre: `Reemplazar ${c.herrS}`, mnvez: R.u(0.3, 2.5, 3), tipo: "exterior", pf: R.i(8, 18) },
      { clave: "C", nombre: `Limpiar ${c.herrS}`, mnvez: R.u(0.1, 1.2, 3), tipo: "interior", pf: R.i(8, 18) },
      { clave: "D", nombre: c.revisar, mnvez: R.u(0.3, 2, 3), tipo: intD ? "interior" : "exterior", pf: R.i(8, 20) },
      { clave: "E", nombre: `Llevar ${c.producto} al próximo proceso`, mnvez: R.u(0.4, 2.5, 3), tipo: "interior", pf: R.i(8, 20) },
    ];
    const d = { c, kg, g, lote, vel, nHerr: R.i(6, 30), cadaN: R.i(3, 10), pm: R.i(1, 5), psnp: R.i(5, 7), pfm: R.i(5, 12), inint: R.bool(0.4), puede: R.bool(0.4), elems, j: jornada(R) };
    const s = casoIntegral(d);
    if (s.TNO <= 0.5 || s.ETE < 1) continue;
    if (s.a.snpDentro && s.TNO < s.SNP + s.SF) continue;
    if (s.prod < 1) continue;
    return armarIntegral(d);
  }
};

// ---------- API ----------
export function generar(tipo, seed = Math.floor(Math.random() * 1e9)) {
  if (!G[tipo]) throw new Error(`Tipo desconocido ${tipo}`);
  const R = herramientas(prng(seed + 7919 * Number(tipo.slice(1))));
  const p = G[tipo](R, seed);
  return { tipo, ...TIPOS[tipo], semilla: seed, ...p };
}
export function generarExamen(n = 10, seed = Math.floor(Math.random() * 1e9)) {
  const R = herramientas(prng(seed));
  const ids = Object.keys(TIPOS);
  return Array.from({ length: n }, (_, k) => generar(R.pick(ids), seed * 31 + k));
}
