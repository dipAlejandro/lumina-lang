package com.dahl.lang.natives.func;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NativeFunction
 */
public class NativeFunction {

  @FunctionalInterface
  public interface NativeImpl {
    Object call(List<Object> args, int line, String file);
  }

  private record Native(String name, int arity, NativeImpl impl) {
  }

  protected static final Map<String, Native> natives = new HashMap<>();

  protected static void reg(String name, int arity, NativeImpl impl) {
    natives.put(name, new Native(name, arity, impl));
  }

  public static boolean isNative(String name) {
    return natives.containsKey(name);
  }

  public static Object call(String name, List<Object> evaluatedArgs, int line, String file) {
    Native fn = natives.get(name);

    if (fn == null)
      throw new RuntimeException(String.format("[%s:%d] Error: Función nativa '%s' no encontrada", file, line, name));

    if (fn.arity() != evaluatedArgs.size())
      throw new RuntimeException(String.format("[%s:%d] Error: '%s()' espera %d argumento(s), recibió %d", file, line,
          name, fn.arity(), evaluatedArgs.size()));
    return fn.impl().call(evaluatedArgs, line, file);
  }

}
