package com.dahl.lang;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.dahl.lang.AST;
import com.dahl.lang.Environment;
import com.dahl.lang.NativeFunction;

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
  interface Callable {
  }

  record Function(AST.FunDecl decl, Environment closure) implements Callable {
  }

  record Procedure(AST.ProcDecl decl, Environment closure) implements Callable {
  }

  record Module(Environment env, Map<String, Callable> callables) {
  }

  private final Environment global = new Environment();
  private final Map<String, Function> functions = new HashMap<>();
  private final Map<String, Procedure> procedures = new HashMap<>();
  private final Map<String, Module> modules = new HashMap<>();
  private Module currentModule = null; // null = scope global

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

      case AST.Import i -> {
        String src = "";
        AST.Program program;
        try {
          src = Files.readString(Path.of(i.path() + ".ptl"));
          program = new Parser(new Lexer(src).tokenize()).parse();
        } catch (IOException e) {
          throw new RuntimeException("Modulo '" + src + "' no encontrado.");
        }

        // Ejecutar en modulo en su propio entorno aislado
        Module mod = loadModule(program);
        modules.put(i.namespace(), mod);
        yield null;
      }

      case AST.ExportFun ef -> {
        functions.put(ef.decl().name(), new Function(ef.decl(), env));
        yield null;
      }

      case AST.ExportProc ep -> {
        procedures.put(ep.decl().name(), new Procedure(ep.decl(), env));
        yield null;
      }

      case AST.ExportVar ev -> {
        execute(ev.decl(), env);
        yield null;
      }

      case AST.ConstDecl c -> {
        Object val = evaluate(c.initializer(), env);
        env.defineConst(c.name(), val);
        yield null;
      }

      case AST.ExportConst ec -> {
        execute(ec.decl(), env);
        yield null;
      }
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
        if (isTruthy(evaluate(ia.condition(), env)))
          evaluate(ia.body(), env);

        yield null;
      }

      case AST.While w -> {
        while (isTruthy(evaluate(w.condition(), env))) {
          executeBlock(w.body(), new Environment(env));
        }
        yield null;
      }

      case AST.WhileArrow wa -> {
        while (isTruthy(evaluate(wa.condition(), env)))
          evaluate(wa.body(), env);

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
        procedures.put(pd.name(), new Procedure(pd, env));
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
          case "-" ->

            toNumber(left) - toNumber(right);
          case "*" -> toNumber(left) * toNumber(right);
          case "/" -> {
            double r = toNumber(right);
            if (r == 0)
              throw new ArithmeticException("División por cero");

            yield toNumber(left) / r;
          }
          case "%" ->

            toNumber(left) % toNumber(right);
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

      case

          AST.CompoundAssign ca -> {
        double current = toNumber(env.get(ca.name()));
        double operand = toNumber(evaluate(ca.value(), env));
        double result = switch (ca.op()) {
          case "+=" -> current + operand;
          case "-=" -> current - operand;
          case "*=" -> current * operand;
          case "/=" -> {
            if (operand == 0)
              throw new ArithmeticException("División por cero");
            yield current / operand;
          }
          default -> throw new RuntimeException("Operador compuesto desconocido: " + ca.op());
        };

        env.set(ca.name(), result);

        yield result;
      }

      case
          AST.Increment inc -> {
        double current = toNumber(env.get(inc.name()));
        double next = inc.op().equals("++") ? current + 1 : current - 1;
        env.set(inc.name(), next);

        yield inc.prefix() ? next : current;
      }

      case
          AST.Ternary t -> {
        Object condition = evaluate(t.condition(), env);
        if (isTruthy(condition)) {

          yield evaluate(t.consequence(), env);
        } else

        {

          yield evaluate(t.alternative(), env);
        }
      }

      case

          AST.FunCall fc -> {

        if (NativeFunction.isNative(fc.callee())) {
          List<Object> evaluatedArgs = new ArrayList<>();
          for (AST.Node arg : fc.args())
            evaluatedArgs.add(evaluate(arg, env));

          yield NativeFunction.call(fc.callee(), evaluatedArgs);
        }

        Function fn = resolveFunction(fc.callee());
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

      case
          AST.ProcCall pc -> {
        Procedure pr = resolveProcedure(pc.callee());
        if (pr == null)
          throw new RuntimeException("Procedimiento no definido: '" + pc.callee() + "'");

        List<String> params = pr.decl().params();
        List<AST.Node> args = pc.args();

        if (params.size() != args.size())
          throw new RuntimeException(
              "'" + pc.callee() + "' espera " + params.size() + " argumentos, pero recibió " + args.size());

        Environment callEnv = new Environment(pr.closure());
        for (int i = 0; i < params.size(); i++) {
          callEnv.define(params.get(i), evaluate(args.get(i), env));
        }

        try {
          executeBlock(pr.decl().body(), callEnv);
        } catch (ReturnSignal rs) {
          if (rs.value != null) {
            throw new RuntimeException("Un procedimiento no puede retornar un valor");
            // Nota: return; es valido, sirve para salir anticipadamente
          }
        }
        yield null;
      }

      case AST.NamespaceCall nc -> {
        Module mod = modules.get(nc.namespace());
        if (mod == null)
          throw new RuntimeException("Módulo no encontrado: '" + nc.namespace() + "'");

        Callable callable = mod.callables().get(nc.member());
        if (callable == null)
          throw new RuntimeException("'" + nc.member() + "' no existe en módulo '" + nc.namespace() + "'");

        List<Object> evaluatedArgs = new ArrayList<>();
        for (AST.Node arg : nc.args())
          evaluatedArgs.add(evaluate(arg, env));

        Module previous = currentModule;
        currentModule = mod; // ← setear contexto del módulo

        try {
          if (callable instanceof Function fn) {
            List<String> params = fn.decl().params();
            if (params.size() != evaluatedArgs.size())
              throw new RuntimeException("'" + nc.member() + "' espera " + params.size() + " argumento(s)");

            Environment callEnv = new Environment(fn.closure());
            for (int i = 0; i < params.size(); i++)
              callEnv.define(params.get(i), evaluatedArgs.get(i));

            try {
              executeBlock(fn.decl().body(), callEnv);
              yield null;
            } catch (ReturnSignal rs) {
              yield rs.value;
            }

          } else if (callable instanceof Procedure pr) {
            List<String> params = pr.decl().params();
            if (params.size() != evaluatedArgs.size())
              throw new RuntimeException("'" + nc.member() + "' espera " + params.size() + " argumento(s)");

            Environment callEnv = new Environment(pr.closure());
            for (int i = 0; i < params.size(); i++)
              callEnv.define(params.get(i), evaluatedArgs.get(i));

            try {
              executeBlock(pr.decl().body(), callEnv);
            } catch (ReturnSignal rs) {
              if (rs.value != null)
                throw new RuntimeException("Un procedimiento no puede retornar un valor");
            }
            yield null;
          }
          throw new RuntimeException("Callable desconocido en módulo '" + nc.namespace() + "'");
        } finally {
          currentModule = previous; // ← siempre restaurar, incluso si hay excepción
        }
      }
      case AST.NamespaceVar nv -> {
        Module mod = modules.get(nv.namespace());
        if (mod == null)
          throw new RuntimeException("Módulo no encontrado: '" + nv.namespace() + "'");
        yield mod.env().get(nv.member());
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

  private Module loadModule(AST.Program program) {
    Environment modEnv = new Environment();
    Map<String, Callable> modCallables = new HashMap<>();

    for (AST.Node stmt : program.statements()) {
      if (stmt instanceof AST.ExportFun ef) {
        modCallables.put(ef.decl().name(), new Function(ef.decl(), modEnv));
      } else if (stmt instanceof AST.FunDecl fd) {
        modCallables.put(fd.name(), new Function(fd, modEnv));
      } else if (stmt instanceof AST.ExportProc ep) {
        modCallables.put(ep.decl().name(), new Procedure(ep.decl(), modEnv));
      } else if (stmt instanceof AST.ProcDecl pc) {
        modCallables.put(pc.name(), new Procedure(pc, modEnv));
      } else if (stmt instanceof AST.ExportVar ev) {
        execute(ev.decl(), modEnv);
      } else if (stmt instanceof AST.ExportConst ec)
        execute(ec.decl(), modEnv);
      // Lo que no tiene 'export' se ejecuta pero no se expone
      else {
        execute(stmt, modEnv);
      }
    }

    Module mod = new Module(modEnv, modCallables);

    // Segunda pasada: ejecutar el resto con el módulo como contexto
    Module previous = currentModule;
    currentModule = mod;
    for (AST.Node stmt : program.statements()) {
      if (stmt instanceof AST.ExportFun || stmt instanceof AST.FunDecl ||
          stmt instanceof AST.ExportProc || stmt instanceof AST.ProcDecl)
        continue;
      execute(stmt, modEnv);
    }
    currentModule = previous; // restaurar contexto anterior

    return mod;
  }

  private Function resolveFunction(String name) {
    // Buscar primero en el módulo activo
    if (currentModule != null) {
      Callable c = currentModule.callables().get(name);
      if (c instanceof Function f)
        return f;
    }
    // Luego en el scope global
    Function fn = functions.get(name);
    if (fn == null)
      throw new RuntimeException("Función no definida: '" + name + "'");
    return fn;
  }

  private Procedure resolveProcedure(String name) {
    if (currentModule != null) {
      Callable c = currentModule.callables().get(name);
      if (c instanceof Procedure p)
        return p;
    }
    Procedure pr = procedures.get(name);
    if (pr == null)
      throw new RuntimeException("Procedimiento no definido: '" + name + "'");
    return pr;
  }
}
