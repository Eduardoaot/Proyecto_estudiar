# Estudio Activo

App de escritorio en Java (Swing) para estudiar. Requiere Java 17 o superior.

## Cómo correrla

- `ejecutar.bat` abre la app (compila la primera vez).
- `compilar.bat` recompila todo y regenera `EstudioActivo.jar`. Úsalo después de cambiar código.
- `java -jar EstudioActivo.jar` también la abre.

Tu avance se guarda en la carpeta `progreso/`.

## Problemas de cálculo (Estudio de Tiempos, pasos 8 a 12)

En la pantalla de inicio, la tarjeta **Problemas de cálculo** abre:

- **20 tipos de problema** agrupados por paso. Cada vez que entras se genera un problema nuevo.
- **Revisar**: califica cada respuesta por separado. Se acepta coma decimal (`0,25`), `%` y una tolerancia de ±1.5 % (o 0.006).
- **Pista**: primero muestra la fórmula del siguiente paso, luego la sustitución y al final el resultado.
- **Ver solución completa**, **Otro de este tipo** y **Tipo al azar**.
- **Semilla**: escribe una semilla y pulsa Enter para repetir un problema exacto.
- **Práctica adaptativa**: elige más seguido los tipos que menos has practicado o en los que más fallas.
- **Ejemplo de clase**: el caso del laboratorio farmacéutico (T20, semilla 0).
- **Problemas resueltos**: los 100 ejemplos de `problemas_ordinario.json` con su solución.
- **Examen de problemas**: de 3 a 6 problemas, con cronómetro opcional y calificación sobre 100.

Las matemáticas están en `src/estudio/Generadores.java`, un port exacto de `referencia/generadores.js`.
`probar.bat` comprueba que Java y JS dan el mismo problema para 5,200 combinaciones de tipo y semilla, y corre las demás pruebas.

## Agregar preguntas de teoría

Cada archivo `.json` en `datos/` es una materia. El formato es:

```json
{
  "titulo": "Guía de Estudio - Nombre de la materia",
  "preguntas": [
    { "id": 1, "tema": "Tema 1", "pregunta": "¿...?", "respuesta": "..." }
  ]
}
```

Para agregar preguntas, añade objetos a `preguntas` con un `id` nuevo. Si usas un `tema` nuevo, aparece como un módulo aparte.
Para agregar una materia, crea otro archivo `.json` con este formato en `datos/`.
