package com.dahl.lang;

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
  interface Callable{}
  record Function(AST.FunDecl decl, Environment closure) implements Callable {}
  record Procedure(AST.ProcDecl decl, Environment closure) implements Callable {}

  private final Environment global = new Environment();
  private final Map<String, Function> functions = new HashMap<>();
  private final Map<String, Procedure> procedures = new HashMap<>();

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

      case AST.IfArrow ia -> {
        if(isTruthy(evaluate(ia.condition(), env))) evaluate(ia.body(), env);

        yield null;
      }

      case AST.While w -> {
        while (isTruthy(evaluate(w.condition(), env))) {
          executeBlock(w.body(), new Environment(env));
        }
        yield null;
      }

      case AST.WhileArrow wa -> {
        while (isTruthy(evaluate(wa.condition(), env))) evaluate(wa.body(), env);

        yield null;
      }

      case AST.For f -> {
        Environment forEnv = new Environment(env);
        execute(f.init(), forEnv);
        while (isTruthy(evaluate(f.condition(), forEnv))) {
          executeBlock(f.body(), new Environment(forEnv));
          evaluate(f.step(), forEnv);
        }
        yield null;
      }

      case AST.ForArrow fa -> {
        Environment forEnv = new Environment(env);
        execute(fa.init(), forEnv);
        while (isTruthy(evaluate(fa.condition(), forEnv))) {
          evaluate(fa.body(), forEnv);
          evaluate(fa.step(), forEnv);
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

      case AST.ProcDecl pd -> {
       procedures.put(pd.name(),new Procedure(pd, env));
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

      case AST.CompoundAssign ca -> {
        double current = toNumber(env.get(ca.name()));
        double operand = toNumber(evaluate(ca.value(), env));
        double result = switch (ca.op()) {
          case "+=" -> current + operand;
          case "-=" -> current - operand;
          case "*=" -> current * operand;
          case "/=" -> {
            if (operand == 0) throw new ArithmeticException("División por cero");
            yield current / operand;
          }
          default -> throw new RuntimeException("Operador compuesto desconocido: " + ca.op());
        };

        env.set(ca.name(), result);

        yield result;
      }

      case AST.Increment inc -> {
        double current  = toNumber(env.get(inc.name()));
        double next = inc.op().equals("++") ? current + 1 : current - 1;
        env.set(inc.name(), next);

        yield inc.prefix() ? next : current;
      }

      case AST.Ternary t -> {
        Object condition = evaluate(t.condition(), env);
        if(isTruthy(condition)) {
          yield evaluate(t.consequence(), env);
        } else {
          yield evaluate(t.alternative(), env);
        }
      }

      case AST.FunCall fc -> {
        
        Function fn = functions.get(fc.callee());
        if (fn == null)
          throw new RuntimeException("Función no definida: '" + fc.callee() + "'");

        List<String> params = fn.decl().params();
        List<AST.Node> args = fc.args();
        if (params.size() != args.size())
          throw new RuntimeException("'" + fc.callee() + "' espera " + params.size() +
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

      case AST.ProcCall pc -> {
        Procedure pr = procedures.get(pc.callee());
        if(pr == null) 
          throw new RuntimeException("Procedimiento no definido: '"+ pc.callee() + "'");

        List<String> params = pr.decl().params();
        List<AST.Node> args = pc.args();

        if (params.size() != args.size()) 
          throw new RuntimeException("'" + pc.callee() + "' espera " + params.size() + " argumentos, pero recibió " + args.size());

        Environment callEnv = new Environment(pr.closure());
        for(int i = 0; i < params.size(); i++){
          callEnv.define(params.get(i), evaluate(args.get(i), env));
        }
        
        try{
          executeBlock(pr.decl().body(), callEnv);
      } catch (ReturnSignal rs) {
        if (rs.value != null) {
          throw new RuntimeException("Un procedimiento no puede retornar un valor");
          // Nota: return; es valido, sirve para salir anticipadamente
        }
      }
      yield null;
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
