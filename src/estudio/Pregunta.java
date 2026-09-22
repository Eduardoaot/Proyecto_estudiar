package estudio;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

record Pregunta(int id, String tema, String pregunta, String respuesta) {}
