// Compara el volcado de Java (JSONL) con generadores.js, problema por problema.
// Uso: node referencia/comparar.mjs volcado_java.jsonl
import { readFileSync } from "node:fs";
import { generar } from "./generadores.js";

const archivo = process.argv[2];
if (!archivo) { console.error("Falta el archivo del volcado"); process.exit(2); }

// T16, T19 y T20 muestran en Java los pasos con fracciones y tablas, como en las diapositivas de clase:
// en esos tipos se comparan enunciado, datos y respuestas, pero no el texto de los pasos.
const PASOS_PROPIOS = new Set(["T16", "T19", "T20"]);

const canon = (p) => ({
  tipo: p.tipo,
  semilla: p.semilla,
  enunciado: p.enunciado,
  tabla: p.tabla ? { columnas: p.tabla.columnas.map(String), filas: p.tabla.filas.map((f) => f.map(String)) } : null,
  pasos: PASOS_PROPIOS.has(p.tipo) ? null : p.pasos.map((x) => (Array.isArray(x) ? x : [x.titulo, x.formula, x.sustitucion, x.resultado])),
  respuestas: p.respuestas.map((r) =>
    r.tipo === "opcion"
      ? { clave: r.clave, etiqueta: r.etiqueta, tipo: r.tipo, valor: r.valor, opciones: r.opciones.map((o) => (Array.isArray(o) ? o : [o.valor, o.texto])) }
      : { clave: r.clave, etiqueta: r.etiqueta, tipo: r.tipo, valor: r.valor, unidad: r.unidad, decimales: r.decimales }),
});

let n = 0, malas = 0;
for (const linea of readFileSync(archivo, "utf8").split(/\r?\n/)) {
  if (!linea.trim()) continue;
  n++;
  const java = canon(JSON.parse(linea));
  const a = JSON.stringify(java), b = JSON.stringify(canon(generar(java.tipo, java.semilla)));
  if (a !== b) {
    malas++;
    if (malas <= 5) {
      let i = 0;
      while (i < a.length && a[i] === b[i]) i++;
      console.log(`DIFERENTE ${java.tipo} semilla ${java.semilla}\n  java: …${a.slice(Math.max(0, i - 60), i + 80)}\n  js:   …${b.slice(Math.max(0, i - 60), i + 80)}`);
    }
  }
}
console.log(malas === 0 ? `Java y JS idénticos en ${n} problemas` : `${malas} de ${n} problemas difieren`);
process.exit(malas === 0 ? 0 : 1);
