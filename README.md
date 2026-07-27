# Lumina Lang

Lumina es un lenguaje de programación interpretado, de sintaxis sencilla y orientado al aprendizaje, escrito en Java. El repositorio incluye el lexer, parser, árbol de sintaxis abstracta, intérprete, comprobador de tipos en tiempo de ejecución, funciones nativas y una pequeña biblioteca estándar.

## Estado del proyecto

El intérprete se identifica como **Lumina - Lang v1.0** y ejecuta archivos fuente con extensión `.lum`.

> Nota: Lumina está en desarrollo. Algunas decisiones de sintaxis y comportamiento pueden cambiar conforme evolucione el proyecto.

## Características principales

- Variables mutables con `var` y constantes con `const`.
- Tipado opcional con `int`, `float`, `str`, `bool`, `array`, `map`, `set` y `any`.
- Funciones con retorno mediante `fun` y procedimientos sin valor de retorno mediante `proc`.
- Control de flujo con `if`, `else`, `while`, `for`, `break` y `continue`.
- Expresiones ternarias, operadores aritméticos, lógicos, comparación e incrementos.
- Colecciones nativas: arreglos, mapas y conjuntos.
- Lambdas y operaciones funcionales en arreglos como `map`, `filter`, `reduce`, `find` y `sort`.
- Structs con campos, constructores con argumentos nombrados, métodos mediante `impl` y asignación de propiedades.
- Módulos con `import` y declaraciones exportables con `export`.
- Funciones nativas de matemáticas, entrada/salida, conversión de tipos y errores.

## Requisitos

- JDK compatible con las características modernas usadas por el proyecto, incluyendo pattern matching en `switch`.
- Bash para usar los scripts `build.sh` y `lumina.sh`.

## Compilación

Desde la raíz del repositorio:

```bash
./build.sh
```

El script compila las fuentes Java en `out/` y genera `interpreter.jar` usando `MANIFEST.MF`.

## Ejecución

Ejecuta un programa Lumina con:

```bash
java -jar interpreter.jar ruta/al/archivo.lum
```

También puedes usar el wrapper:

```bash
./lumina.sh ruta/al/archivo.lum
```

## Hola mundo

```lumina
print("Hola, Lumina!");
```

## Sintaxis básica

### Variables, constantes y tipos

```lumina
var nombre = "Ada";
var int edad = 36;
const float PI = 3.141592653589793;

print(nombre + " tiene " + edad + " años");
```

Si no se indica tipo, Lumina usa `any`. Cuando se declara un tipo concreto, el intérprete valida compatibilidad en asignaciones, parámetros, retornos y campos de structs.

### Funciones y procedimientos

```lumina
fun int sumar(int a, int b) {
  return a + b;
}

proc saludar(str nombre) {
  print("Hola, " + nombre);
}

print(sumar(2, 3));
saludar("Lumina");
```

- `fun` declara un tipo de retorno y puede devolver un valor con `return`.
- `proc` modela acciones y no debe retornar un valor.

### Condicionales y bucles

```lumina
var int n = 3;

if (n > 0) {
  print("positivo");
} else {
  print("cero o negativo");
}

while (n > 0) {
  print(n);
  n--;
}

for (var int i = 0; i < 3; i++) {
  print(i);
}
```

Lumina también acepta formas compactas con flecha para expresiones simples:

```lumina
if (true) -> print("ok");
```

### Operadores

Lumina soporta:

- Aritméticos: `+`, `-`, `*`, `/`, `%`.
- Asignación compuesta: `+=`, `-=`, `*=`, `/=`.
- Incremento y decremento: `++`, `--` en forma prefija o sufija.
- Comparación: `==`, `!=`, `<`, `<=`, `>`, `>=`.
- Lógicos: `&&`, `||`, `!`.
- Ternario: `condicion ? valor_si_verdadero : valor_si_falso`.

## Colecciones

### Arreglos

```lumina
var nums = array_of(1, 2, 3);
nums.push(4);

print(nums.len);       // 4
print(nums.get(0));    // 1
print(nums.contains(2));
print(nums.pop());
```

Métodos disponibles para arreglos:

- `get(idx)`
- `push(val)`
- `replace(idx, val)`
- `pop()`
- `contains(val)`
- `map(lambda)`
- `filter(lambda)`
- `reduce(lambda, init)`
- `find(lambda)`
- `sort()` o `sort(lambda)`

### Lambdas y estilo funcional

```lumina
var nums = array_of(1, 2, 3, 4);
var dobles = nums.map((x) -> x * 2);
var pares = nums.filter((x) -> x % 2 == 0);
var total = nums.reduce((acc, x) -> acc + x, 0);

print(dobles);
print(pares);
print(total);
```

### Mapas

```lumina
var persona = map_of("nombre": "Ada", "edad": 36);
print(persona.get("nombre"));

persona.put("pais", "UK");
print(persona.contains_key("pais"));
print(persona.keys());
```

Métodos disponibles para mapas:

- `get(key)`
- `put(key, value)`
- `remove(key)`
- `contains_key(key)`
- `contains_val(value)`
- `keys()`
- `values()`
- `clear()`

### Conjuntos

```lumina
var tags = set_of("lang", "java");
tags.add("interpreter");

print(tags.size);
print(tags.contains("java"));
print(tags.to_array());
```

Métodos disponibles para conjuntos:

- `add(elem)`
- `remove(elem)`
- `contains(elem)`
- `to_array()`
- `union(set)`

## Strings

Los strings exponen métodos útiles:

```lumina
var texto = "Lumina Lang";

print(texto.len);
print(texto.upper());
print(texto.lower());
print(texto.contains("Lang"));
print(texto.replace("Lang", "Script"));
print(texto.split(" "));
```

## Structs e implementación de métodos

```lumina
struct Counter {
  int value;
}

impl Counter {
  fun any inc() {
    self.value = self.value + 1;
  }
}

var c = Counter(value: 1);
c.inc();
print(c.value); // 2

c.value = 10;
print(c);
```

Los campos pueden tener valores por defecto:

```lumina
struct User {
  str name;
  bool active = true;
}

var user = User(name: "Ada");
print(user.active);
```

Las estructuras almacenadas en constantes no pueden mutarse.

## Módulos y biblioteca estándar

Lumina permite importar módulos desde archivos `.lum`:

```lumina
import math from "std/math";

print(math.PI);
print(math.clamp(15, 0, 10));
print(math.deg_to_rad(180));
```

La biblioteca estándar incluida en `std/math.lum` exporta constantes matemáticas (`PI`, `E`, `TAU`) y funciones como `clamp`, `lerp`, `hypot`, `deg_to_rad` y `rad_to_deg`.

## Funciones nativas

### Matemáticas

- `sqrt(x)`, `cbrt(x)`, `floor(x)`, `ceil(x)`, `round(x)`
- `pow(x, y)`, `log(x)`, `log10(x)`
- `sin(x)`, `cos(x)`, `tan(x)`, `asin(x)`, `acos(x)`, `atan(x)`, `atan2(y, x)`
- `max(a, b)`, `min(a, b)`, `sign(x)`, `trunc(x)`

### Tipos

- `to_int(value)`
- `to_float(value)`
- `to_str(value)`
- `to_bool(value)`

### Entrada/salida y archivos

- `input(prompt)`
- `read_file(path)`
- `read_lines(path)`
- `write_file(path, content)`
- `append_file(path, content)`
- `file_exists(path)`
- `delete_file(path)`

### Errores

- `throw_error(message)`

## Pruebas

El repositorio contiene pruebas Java ejecutables con `main`, por ejemplo `PropertyAssignmentTest`.

Una forma directa de compilar y ejecutar las pruebas es:

```bash
mkdir -p out/test
javac --enable-preview --release 24 -d out/test $(find src/main/java src/test/java -name "*.java")
java --enable-preview -cp out/test com.dahl.lumina.lang.PropertyAssignmentTest
```

Ajusta el valor de `--release` según la versión del JDK instalada si corresponde.

## Estructura del repositorio

```text
.
├── build.sh                         # Compila el intérprete y genera interpreter.jar
├── lumina.sh                        # Wrapper para ejecutar archivos .lum
├── MANIFEST.MF                      # Clase principal del JAR
├── std/
│   └── math.lum                     # Biblioteca estándar matemática
├── src/main/java/com/dahl/lumina/lang/
│   ├── Lexer.java                   # Tokenización
│   ├── Parser.java                  # Parser y reglas sintácticas
│   ├── AST.java                     # Nodos del AST
│   ├── TypeChecker.java             # Validación de tipos en runtime
│   ├── Environment.java             # Entornos y scopes
│   ├── Interpreter.java             # Ejecución del AST
│   └── natives/func/                # Funciones nativas
└── src/test/java/com/dahl/lumina/lang/
    └── PropertyAssignmentTest.java  # Pruebas de asignación de propiedades
```

## Licencia

Este repositorio no declara una licencia explícita. Añade una licencia antes de distribuir o reutilizar el proyecto públicamente.
