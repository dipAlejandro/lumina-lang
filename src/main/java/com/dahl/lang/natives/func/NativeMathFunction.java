package com.dahl.lang.natives.func;

import java.util.List;
import com.dahl.lang.natives.func.NativeFunction;

class NativeMathFunction extends NativeFunction {

  static {
    // Basic maths
    reg("sqrt", 1, (args, line, file) -> Math.sqrt(toDouble(args, 0)));
    reg("cbrt", 1, (args, line, file) -> Math.cbrt(toDouble(args, 0)));
    reg("floor", 1, (args, line, file) -> Math.floor(toDouble(args, 0)));
    reg("ceil", 1, (args, line, file) -> Math.ceil(toDouble(args, 0)));
    reg("round", 1, (args, line, file) -> (double) Math.round(toDouble(args, 0)));
    reg("pow", 2, (args, line, file) -> Math.pow(toDouble(args, 0), toDouble(args, 1)));
    reg("log", 1, (args, line, file) -> {
      double x = toDouble(args, 0);
      if (x <= 0)
        throw new RuntimeException(String.format("[%s:%d] -> log(): argumento debe ser mayor que 0", file, line));
      return Math.log(x);
    });
    reg("log10", 1, (args, line, file) -> {
      double x = toDouble(args, 0);
      if (x <= 0)
        throw new RuntimeException(String.format("[%s:%d] -> log10(): argumento debe ser mayor que 0", file, line));
      return Math.log10(x);
    });

    // Trigonometric
    reg("sin", 1, (args, line, file) -> Math.sin(toDouble(args, 0)));
    reg("cos", 1, (args, line, file) -> Math.cos(toDouble(args, 0)));
    reg("tan", 1, (args, line, file) -> Math.tan(toDouble(args, 0)));
    reg("asin", 1, (args, line, file) -> Math.asin(toDouble(args, 0)));
    reg("acos", 1, (args, line, file) -> Math.acos(toDouble(args, 0)));
    reg("atan", 1, (args, line, file) -> Math.atan(toDouble(args, 0)));
    reg("atan2", 2, (args, line, file) -> Math.atan2(toDouble(args, 0), toDouble(args, 1)));
    // Utilidades
    reg("max", 2, (args, line, file) -> Math.max(toDouble(args, 0), toDouble(args, 1)));
    reg("min", 2, (args, line, file) -> Math.min(toDouble(args, 0), toDouble(args, 1)));
    reg("sign", 1, (args, line, file) -> Math.signum(toDouble(args, 0)));
    reg("trunc", 1, (args, line, file) -> args.get(0) instanceof Double d
        ? (d >= 0 ? Math.floor(d) : Math.ceil(d))
        : toDouble(args, 0));
  }

  public static double toDouble(List<Object> args, int idx) {
    Object v = args.get(idx);
    if (v instanceof Double d)
      return d;
    throw new RuntimeException("Argumento: " + idx + " debe ser un numero");
  }
}
