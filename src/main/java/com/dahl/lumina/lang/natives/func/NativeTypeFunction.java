package com.dahl.lumina.lang.natives.func;

import com.dahl.lumina.lang.natives.func.NativeFunction;

/**
 * NativeTypeFunction
 */
public final class NativeTypeFunction extends NativeFunction {

  static {
    reg("to_int", 1, (args, line, file) -> {
      Object val = args.get(0);

      if (val instanceof Double d)
        return (double) d.intValue();
      if (val instanceof Boolean b)
        return b ? 1.0 : 0.0;
      if (val instanceof String s) {
        try {
          return (double) Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
          throw new RuntimeException(
              String.format("[%s:%d] Error: to_int() no puede convertir '%s' a int", file, line, s));
        }
      }
      throw new RuntimeException(
          String.format("[%s.%d] Error: to_int() no puede convertir el valor dado", file, line));
    });

    reg("to_float", 1, (args, line, file) -> {
      Object val = args.get(0);

      if (val instanceof Double d)
        return d;
      if (val instanceof Boolean b)
        return b ? 1.0 : 0.0;
      if (val instanceof String s) {
        try {
          return (double) Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
          throw new RuntimeException(
              String.format("[%s:%d] Error: to_float() no puede convertir '%s' a float", file, line, s));
        }
      }
      throw new RuntimeException(
          String.format("[%s.%d] Error: to_float() no puede convertir el valor dado", file, line));
    });

    reg("to_str", 1, (args, line, file) -> {
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

    reg("to_bool", 1, (args, line, file) -> {
      Object val = args.get(0);

      if (val instanceof Double d)
        return d != 0;
      if (val instanceof Boolean b)
        return b;
      if (val instanceof String s) {

      if(s.equals("true")) return true;
      if(s.equals("false")) return false;
      
        throw new RuntimeException(
            String.format("[%s:%d] Error: to_bool() no puede convertir '%s' a int", file, line, s));

      }
      throw new RuntimeException(
          String.format("[%s.%d] Error: to_bool() no puede convertir el valor dado", file, line));
    });
  }
}
