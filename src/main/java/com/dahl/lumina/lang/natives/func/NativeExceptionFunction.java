package com.dahl.lumina.lang.natives.func;

import com.dahl.lumina.lang.natives.func.NativeFunction;

/**
 * NativeUtilFunction
 */
public final class NativeExceptionFunction extends NativeFunction {

  static {
    // Errors
    reg("throw_error", 1, (args, line, file, interpreter) -> {
      String msg = (String) args.get(0);

      throw new RuntimeException(
          String.format("[%s:%d] Error: %s", file, line, msg));
    });
  }

}
