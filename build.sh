echo "Compilando proyecto..."

SRC_DIR="src/main/java"
OUT_DIR="out"
JAR_NAME="interpreter.jar"

javac -d "$OUT_DIR" $(find "$SRC_DIR" -name "*.java")

# Verificar si javac falló
if [ $? -ne 0 ]; then
    echo "ERROR: La compilación falló. No se generará en JAR."
    exit 1
fi

echo "Compilación exitosa."

echo "Generando JAR..."

jar cfm "$JAR_NAME" MANIFEST.MF -C "$OUT_DIR" .

# Verificar si JAR falló
if [ $? -ne 0 ]; then
    echo "Error al generar JAR."
    exit 1
fi

echo "JAR generado correctamente: $JAR_NAME"
