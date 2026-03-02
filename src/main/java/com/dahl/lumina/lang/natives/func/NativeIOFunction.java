package com.dahl.lumina.lang.natives.func;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import com.dahl.lumina.lang.natives.func.NativeFunction;

/**
 * NativeIOFunction
 */
public final class NativeIOFunction extends NativeFunction {

  private static final Scanner sc = new Scanner(System.in);
  static {
    reg("input", 1, (args, line, file, interpreter) -> {
      if (args.size() != 1)
        throw new RuntimeException(
            String.format("[%s:%d] Error: input(prompt) espera 1 argumento, recibió %d", file, line, args.size()));

      System.out.print(args.get(0));
      String input = sc.nextLine();

      return input;
    });

    reg("read_file", 1, (args, line, file, interpreter) -> {

      if (args.size() != 1)
        throw new RuntimeException(
            String.format("[%s:%d] Error: read_file(path) espera 1 argumento, recibió %d", file, line, args.size()));
      String path = interpreter.stringify(args.get(0));

      try {
        return Files.readString(Path.of(path));
      } catch (IOException ioe) {
        throw new RuntimeException(
            String.format("[%s:%d] IO Error: No se pudo leer el archivo '%s'", file, line, path));
      }
    });

    reg("read_lines", 1, (args, line, file, interpreter) -> {
      if (args.size() != 1)
        throw new RuntimeException(
            String.format("[%s:%d] Error: read_lines(path) espera 1 argumento, recibió %d", file, line, args.size()));
      String path = interpreter.stringify(args.get(0));

      try {
        List<String> lines = Files.readAllLines(Path.of(path));
        return new ArrayList<>(lines);
      } catch (IOException ioe) {
        throw new RuntimeException(
            String.format("[%s:%d] IO Error: No se pudo leer el archivo '%s'", file, line, path));
      }
    });

    reg("write_file", 2, (args, line, file, interpreter) -> {
      if (args.size() != 2)
        throw new RuntimeException(
            String.format("[%s:%d] Error: write_file(path, content) espera 2 argumentos, recibió %d", file, line,
                args.size()));

      String path = interpreter.stringify(args.get(0));
      String content = interpreter.stringify(args.get(1));

      try {
        Files.writeString(Path.of(path), content);
        return null;
      } catch (IOException ioe) {
        throw new RuntimeException(
            String.format("[%s:%d] IO Error: No se pudo escribir el archivo '%s'", file, line, path));
      }
    });

    reg("append_file", 2, (args, line, file, interpreter) -> {
      if (args.size() != 2)
        throw new RuntimeException(
            String.format("[%s:%d] Error: append_line(path, content) espera 2 argumentos, recibió %d", file, line,
                args.size()));
      String path = interpreter.stringify(args.get(0));
      String content = interpreter.stringify(args.get(1));

      try {
        Files.writeString(Path.of(path), content, java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND);
        return null;
      } catch (IOException ioe) {
        throw new RuntimeException(
            String.format("[%s:%d] IO Error: No se pudo escribir el archivo '%s'", file, line, path));
      }
    });

    reg("file_exists", 1, (args, line, file, interpreter) -> {
      if (args.size() != 1)
        throw new RuntimeException(
            String.format("[%s:%d] Error: exist_file(path) espera 1 argumento, recibió %d", file, line,
                args.size()));

      String path = interpreter.stringify(args.get(0));
      return Files.exists(Path.of(path));
    });

    reg("delete_file", 1, (args, line, file, interpreter) -> {
      if (args.size() != 1)
        throw new RuntimeException(
            String.format("[%s:%d] Error: delete_file(path) espera 1 argumento, recibió %d", file, line,
                args.size()));

      String path = interpreter.stringify(args.get(0));

      try {
        return Files.deleteIfExists(Path.of(path));
      } catch (IOException ioe) {
        throw new RuntimeException(
            String.format("[%s:%d] IO Error: No se pudo eliminar el archivo '%s'", file, line, path));
      }
    });
  }
}
