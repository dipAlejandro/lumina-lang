package com.dahl.lumina.lang;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

public class PropertyAssignmentTest {

  public static void main(String[] args) {
    testParserPropertyAssignment();
    testValidPropertyAssignment();
    testUnknownFieldError();
    testInvalidTypeError();
    testConstMutationError();
    testImplMutationPersists();
    System.out.println("OK");
  }

  private static void testParserPropertyAssignment() {
    AST.Program program = parse("struct Point { int x; } var p = Point(x: 1); p.x = 3;");
    AST.Node stmt = program.statements().get(2);
    if (!(stmt instanceof AST.ExprStmt expr) || !(expr.expr() instanceof AST.PropertyAssign pa))
      throw new AssertionError("El parser no construyó PropertyAssign");

    if (!(pa.object() instanceof AST.Var v) || !v.name().equals("p") || !pa.property().equals("x"))
      throw new AssertionError("PropertyAssign con objetivo incorrecto");
  }

  private static void testValidPropertyAssignment() {
    String src = """
        struct Point { int x; }
        var p = Point(x: 1);
        p.x = 3;
        print(p.x);
        """;
    String out = runAndCaptureStdout(src);
    if (!out.trim().endsWith("3"))
      throw new AssertionError("Asignación válida de campo no aplicada: " + out);
  }

  private static void testUnknownFieldError() {
    String src = """
        struct Point { int x; }
        var p = Point(x: 1);
        p.y = 3;
        """;
    String error = runAndCaptureError(src);
    if (error == null || !error.contains("no tiene campo 'y'"))
      throw new AssertionError("Se esperaba error por campo inexistente, se obtuvo: " + error);
  }

  private static void testInvalidTypeError() {
    String src = """
        struct Point { int x; }
        var p = Point(x: 1);
        p.x = \"texto\";
        """;
    String error = runAndCaptureError(src);
    if (error == null || !error.contains("Error de tipo"))
      throw new AssertionError("Se esperaba error de tipo, se obtuvo: " + error);
  }

  private static void testConstMutationError() {
    String src = """
        struct Point { int x; }
        const p = Point(x: 1);
        p.x = 3;
        """;
    String error = runAndCaptureError(src);
    if (error == null || !error.contains("No se puede mutar struct almacenado en una constante"))
      throw new AssertionError("Se esperaba error por mutación de constante, se obtuvo: " + error);
  }

  private static void testImplMutationPersists() {
    String src = """
        struct Counter { int value; }

        impl Counter {
          fun any inc() {
            self.value = self.value + 1;
          }
        }

        var c = Counter(value: 1);
        c.inc();
        print(c.value);
        """;
    String out = runAndCaptureStdout(src);
    if (!out.trim().endsWith("2"))
      throw new AssertionError("La mutación desde impl no persistió: " + out);
  }

  private static AST.Program parse(String src) {
    return new Parser(new Lexer(src).tokenize()).parse();
  }

  private static String runAndCaptureStdout(String src) {
    PrintStream prevOut = System.out;
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    System.setOut(new PrintStream(buffer));
    try {
      new Interpreter().run(parse(src));
      return buffer.toString();
    } finally {
      System.setOut(prevOut);
    }
  }

  private static String runAndCaptureError(String src) {
    try {
      new Interpreter().run(parse(src));
      return null;
    } catch (RuntimeException ex) {
      return ex.getMessage();
    }
  }
}
