javac -d out $(find src/main/java -name "*.java")
jar cvfm interpreter.jar MANIFEST.MF -C out .
