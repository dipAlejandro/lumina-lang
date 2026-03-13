package com.dahl.lumina.lang.natives.func;

import com.dahl.lumina.lang.natives.func.NativeFunction;

/**
 * NativeTypeFunction
 */
public final class NativeTypeFunction extends NativeFunction {

  private static RuntimeException conversionError(String function, String file, int line, String value, String target) {
    return new RuntimeException(
        String.format("[%s:%d] Error: %s() no puede convertir '%s' a %s", file, line, function, value, target));
  }

  private static RuntimeException genericConversionError(String function, String file, int line) {
    return new RuntimeException(
        String.format("[%s:%d] Error: %s() no puede convertir el valor dado", file, line, function));
  }

  static {
    reg("to_int", 1, (args, line, file, interpreter) -> {
      Object val = args.get(0);

      if (val instanceof Double d)
        return (double) d.intValue();
      if (val instanceof Boolean b)
        return b ? 1.0 : 0.0;
      if (val instanceof String s) {
        try {
          return (double) Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
          throw conversionError("to_int", file, line, s, "int");
        }
      }
      throw genericConversionError("to_int", file, line);
    });

    reg("to_float", 1, (args, line, file, interpreter) -> {
      Object val = args.get(0);

      if (val instanceof Double d)
        return d;
      if (val instanceof Boolean b)
        return b ? 1.0 : 0.0;
      if (val instanceof String s) {
        try {
          return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
          throw conversionError("to_float", file, line, s, "float");
        }
      }
      throw genericConversionError("to_float", file, line);
    });

    reg("to_str", 1, (args, line, file, interpreter) -> {
      Object val = args.get(0);

      if (val == null)
        return "null";
      if (val instanceof Double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d))
          return String.valueOf(d.longValue());

        return d.toString();
      }
      return val.toString();
    });

    reg("to_bool", 1, (args, line, file, interpreter) -> {
      Object val = args.get(0);

      if (val instanceof Double d)
        return d != 0;
      if (val instanceof Boolean b)
        return b;
      if (val instanceof String s) {

        if (s.equals("true"))
          return true;
        if (s.equals("false"))
          return false;

        throw conversionError("to_bool", file, line, s, "bool");

      }
      throw genericConversionError("to_bool", file, line);
    });
  }
}
