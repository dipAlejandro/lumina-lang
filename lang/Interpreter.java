package lang;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Interpreter {
  static class ReturnSignal extends RuntimeException {
    final Object value;

    /** Señal de retorno de funcion **/
    ReturnSignal(Object value) {
      super(null, null, true, false);
      this.value = value;
    }
  }

  /** Representacion de una funcion definida por el usuario **/
  record Function(AST.FunDecl decl, Environment closure) {
  }

  private final Environment global = new Environment();
  private final Map<String, Function> functions = new HashMap<>();

  // Entry point

  public void run(AST.Program program) {
    // Primer paso: registrar funciones
    for (AST.Node stmt : program.statements()) {
      if (stmt instanceof AST.FunDecl fd) {
        functions.put(fd.name(), new Function(fd, global));
      }
    }
    // Segundo paso: ejecutar
    for (AST.Node stmt : program.statements()) {
      if (!(stmt instanceof AST.FunDecl))
        execute(stmt, global);
    }
  }

  // Execution
  private Object execute(AST.Node node, Environment env) {
    return switch (node) {
      case AST.VarDecl v -> {
        Object val = v.initializer() != null ? evaluate(v.initializer(), env) : null;
        env.define(v.name(), val);
        yield null;
      }

      case AST.Assign a -> {
        Object val = evaluate(a.value(), env);
        yield val;
      }

      case AST.If i -> {
        if (isTruthy(evaluate(i.condition(), env))) {
          executeBlock(i.thenBranch(), new Environment(env));
        } else if (i.elseBranch() != null) {
          executeBlock(i.elseBranch(), new Environment(env));
        }
        yield null;
      }

      case AST.While w -> {
        while (isTruthy(evaluate(w.condition(), env))) {
          executeBlock(w.body(), new Environment(env));
        }
        yield null;
      }

      case AST.For f -> {
        Environment forEnv = new Environment(env);
        execute(f.init(), forEnv);
        while (isTruthy(evaluate(f.condition(), forEnv))) {
          executeBlock(f.body(), new Environment(forEnv));
          execute(f.step(), forEnv);
        }
        yield null;
      }

      case AST.Return r -> {
        Object val = r.value() != null ? evaluate(r.value(), env) : null;
        throw new ReturnSignal(val);
      }

      case AST.Print p -> {
        Object val = evaluate(p.value(), env);
        System.out.println(stringify(val));
        yield null;
      }

      case AST.Block b -> {
        executeBlock(b, new Environment(env));
        yield null;
      }

      case AST.ExprStmt e -> evaluate(e.expr(), env);

      case AST.FunDecl fd -> {
        functions.put(fd.name(), new Function(fd, env));
        yield null;
      }

      default -> throw new RuntimeException("Nodo desconocido: " + node.getClass().getSimpleName());
    };
  }

  private void executeBlock(AST.Block block, Environment env) {
    for (AST.Node stmt : block.statements()) {
      execute(stmt, env);
    }
  }

  // Evaluation

  private Object evaluate(AST.Node node, Environment env) {
    return switch (node) {

      case AST.Literal l -> l.value();

      case AST.Var v -> env.get(v.name());

      case AST.Assign a -> {
        Object val = evaluate(a.value(), env);
        env.set(a.name(), val);
        yield val;
      }

      case AST.Unary u -> {
        Object operand = evaluate(u.operand(), env);
        yield switch (u.op()) {
          case "-" -> -(double) toNumber(operand);
          case "!" -> !isTruthy(operand);
          default -> throw new RuntimeException("Operador unario desconocido: " + u.op());
        };
      }

      case AST.Binary b -> {
        Object left = evaluate(b.left(), env);
        Object right = evaluate(b.right(), env);
        yield switch (b.op()) {
          case "+" -> {
            if (left instanceof String || right instanceof String)
              yield stringify(left) + stringify(right);
            yield toNumber(left) + toNumber(right);
          }
          case "-" -> toNumber(left) - toNumber(right);
          case "*" -> toNumber(left) * toNumber(right);
          case "/" -> {
            double r = toNumber(right);
            if (r == 0)
              throw new ArithmeticException("División por cero");
            yield toNumber(left) / r;
          }
          case "%" -> toNumber(left) % toNumber(right);
          case "==" -> isEqual(left, right);
          case "!=" -> !isEqual(left, right);
          case "<" -> toNumber(left) < toNumber(right);
          case "<=" -> toNumber(left) <= toNumber(right);
          case ">" -> toNumber(left) > toNumber(right);
          case ">=" -> toNumber(left) >= toNumber(right);
          case "&&" -> isTruthy(left) && isTruthy(right);
          case "||" -> isTruthy(left) || isTruthy(right);
          default -> throw new RuntimeException("Operador desconocido: " + b.op());
        };
      }

      case AST.Call c -> {
        Function fn = functions.get(c.callee());
        if (fn == null)
          throw new RuntimeException("Función no definida: '" + c.callee() + "'");

        List<String> params = fn.decl().params();
        List<AST.Node> args = c.args();
        if (params.size() != args.size())
          throw new RuntimeException("'" + c.callee() + "' espera " + params.size() +
              " argumento(s), pero recibió " + args.size());

        Environment callEnv = new Environment(fn.closure());
        for (int i = 0; i < params.size(); i++) {
          callEnv.define(params.get(i), evaluate(args.get(i), env));
        }
        try {
          executeBlock(fn.decl().body(), callEnv);
          yield null;
        } catch (ReturnSignal rs) {
          yield rs.value;
        }
      }

      default -> execute(node, env);
    };
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private boolean isTruthy(Object value) {
    if (value == null)
      return false;
    if (value instanceof Boolean b)
      return b;
    if (value instanceof Double d)
      return d != 0;
    return true;
  }

  private boolean isEqual(Object a, Object b) {
    if (a == null && b == null)
      return true;
    if (a == null)
      return false;
    return a.equals(b);
  }

  private double toNumber(Object value) {
    if (value instanceof Double d)
      return d;
    if (value instanceof Boolean b)
      return b ? 1.0 : 0.0;
    if (value instanceof String s) {
      try {
        return Double.parseDouble(s);
      } catch (NumberFormatException e) {
        throw new RuntimeException("No se puede convertir a número: '" + s + "'");
      }
    }
    throw new RuntimeException("Valor no numérico: " + stringify(value));
  }

  private String stringify(Object value) {
    if (value == null)
      return "null";
    if (value instanceof Double d) {
      // Mostrar enteros sin decimales
      if (d == Math.floor(d) && !Double.isInfinite(d))
        return String.valueOf(d.longValue());
      return d.toString();
    }
    return value.toString();
  }
}
