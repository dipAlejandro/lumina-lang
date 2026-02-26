package com.dahl.lang.natives.func;

import com.dahl.lang.natives.func.NativeFunction;

/**
 * NativeUtilFunction
 */
public class NativeExceptionFunction extends NativeFunction {

  static {
    // Errors
    reg("throw_error", 1, (args, line, file) -> {
      String msg = (String) args.get(0);

      throw new RuntimeException(
          String.format("[%s:%d] -> %s", file, line, msg));
    });
  }

}
