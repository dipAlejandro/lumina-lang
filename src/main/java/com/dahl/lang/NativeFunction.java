package com.dahl.lang;

import java.awt.geom.Area;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.java.accessibility.util.TopLevelWindowListener;

/**
 * NativeFunction
 */
public class NativeFunction {

  @FunctionalInterface
  public interface NativeImpl {
    Object call(List<Object> args);
  }

  record Native(String name, int arity, NativeImpl impl) {
  }

  private static final Map<String, Native> natives = new HashMap<>();

  static {
    // Basic maths
    reg("sqrt", 1, args -> Math.sqrt(toDouble(args, 0)));
    reg("cbrt", 1, args -> Math.cbrt(toDouble(args, 0)));
    reg("floor", 1, args -> Math.floor(toDouble(args, 0)));
    reg("ceil", 1, args -> Math.ceil(toDouble(args, 0)));
    reg("round", 1, args -> (double) Math.round(toDouble(args, 0)));
    reg("pow", 2, args -> Math.pow(toDouble(args, 0), toDouble(args, 1)));
    reg("log", 1, args -> {
      double x = toDouble(args, 0);
      if (x <= 0)
        throw new RuntimeException("log(): argumento debe ser mayor que 0");
      return Math.log(x);
    });
    reg("log10", 1, args -> {
      double x = toDouble(args, 0);
      if (x <= 0)
        throw new RuntimeException("log10(): argumento debe ser mayor que 0");
      return Math.log10(x);
    });

    // Trigonometric
    reg("sin", 1, args -> Math.sin(toDouble(args, 0)));
    reg("cos", 1, args -> Math.cos(toDouble(args, 0)));
    reg("tan", 1, args -> Math.tan(toDouble(args, 0)));
    reg("asin", 1, args -> Math.asin(toDouble(args, 0)));
    reg("acos", 1, args -> Math.acos(toDouble(args, 0)));
    reg("atan", 1, args -> Math.atan(toDouble(args, 0)));
    reg("atan2", 2, args -> Math.atan2(toDouble(args, 0), toDouble(args, 1)));
    // Utilidades
    reg("max", 2, args -> Math.max(toDouble(args, 0), toDouble(args, 1)));
    reg("min", 2, args -> Math.min(toDouble(args, 0), toDouble(args, 1)));
    reg("sign", 1, args -> Math.signum(toDouble(args, 0)));
    reg("trunc", 1, args -> args.get(0) instanceof Double d
        ? (d >= 0 ? Math.floor(d) : Math.ceil(d))
        : toDouble(args, 0));

  }

  private static void reg(String name, int arity, NativeImpl impl) {
    natives.put(name, new Native(name, arity, impl));
  }

  public static double toDouble(List<Object> args, int idx) {
    Object v = args.get(idx);
    if (v instanceof Double d)
      return d;
    throw new RuntimeException("Argumento: " + idx + " debe ser un numero");
  }

  public static boolean isNative(String name) {
    return natives.containsKey(name);
  }

  public static Object call(String name, List<Object> evaluatedArgs) {
    Native fn = natives.get(name);

    if (fn == null)
      throw new RuntimeException("Función nativa '" + name + "' no encontrada");

    if (fn.arity() != evaluatedArgs.size())
      throw new RuntimeException("'" + name + "' espera " + fn.arity() +
          " argumento(s), recibió " + evaluatedArgs.size());
    return fn.impl().call(evaluatedArgs);
  }

}
