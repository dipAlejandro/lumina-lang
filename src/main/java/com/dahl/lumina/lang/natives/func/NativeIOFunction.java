package com.dahl.lumina.lang.natives.func;

import java.util.Scanner;

import com.dahl.lumina.lang.natives.func.NativeFunction;

/**
 * NativeIOFunction
 */
public final class NativeIOFunction extends NativeFunction {

  private static final Scanner sc = new Scanner(System.in);
  static {
    reg("input", 1, (args, line, file) -> {
      if (args.size() != 1)
        throw new RuntimeException(
            String.format("[%s:%d] Error: ", file, line));

      System.out.print(args.get(0));
      String input = sc.nextLine();
      

      return input;
    });
  }
}
