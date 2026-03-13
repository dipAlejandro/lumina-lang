package com.dahl.lumina.lang;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Lumina {

  public static void main(String[] args) {

    if (args.length != 1) {
      System.err.println("Uso: java -jar interpreter.jar <archivo.lum>");
      return;
    }

    System.out.println("┌─────────────────────────────────────┐");
    System.out.println("│     Intérprete Lumina - Lang v1.0   │");
    System.out.println("└─────────────────────────────────────┘");
    System.out.println();

    try {
      Path sourcePath = Path.of(args[0]);
      String code = Files.readString(sourcePath);
      // 1. Lexer
      Lexer lexer = new Lexer(code);
      List<Lexer.Token> tokens = lexer.tokenize();

      // 2. Parser
      Parser parser = new Parser(tokens);
      AST.Program program = parser.parse();

      // 3. Interpreter
      Interpreter interpreter = new Interpreter();
      interpreter.run(program);

    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
    }
  }
}
