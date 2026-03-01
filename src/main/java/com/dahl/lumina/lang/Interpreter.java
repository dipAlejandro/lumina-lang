package com.dahl.lumina.lang;

import java.awt.image.renderable.ParameterBlock;
import java.beans.BeanDescriptor;
import java.io.IOException;
import java.lang.annotation.Target;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.dahl.lumina.lang.AST;
import com.dahl.lumina.lang.Environment;
import com.dahl.lumina.lang.TypeChecker;
import com.dahl.lumina.lang.natives.func.NativeFunction;

public class Interpreter {
  static class ReturnSignal extends RuntimeException {
    final Object value;

    /** Señal de retorno de funcion **/
    ReturnSignal(Object value) {
      super(null, null, true, false);
      this.value = value;
    }
  }

  static class BreakSignal extends RuntimeException {
    BreakSignal() {
      super(null, null, true, false);
    }
  }

  static class ContinueSignal extends RuntimeException {
    ContinueSignal() {
      super(null, null, true, false);
    }
  }

  /** Representacion de una funcion definida por el usuario **/
  interface Callable {
  }

  record Function(AST.FunDecl decl, Environment closure) implements Callable {
  }

  record Procedure(AST.ProcDecl decl, Environment closure) implements Callable {
  }

  record LambdaValue(AST.Lambda decl, Environment closure) {
  };

  record Module(Environment env, Map<String, Callable> callables) {
  }

  private final Environment global = new Environment();
  private final Map<String, Function> functions = new HashMap<>();
  private final Map<String, Procedure> procedures = new HashMap<>();
  private final Map<String, Module> modules = new HashMap<>();
  private Module currentModule = null; // null = scope global

  private String currentFile = "<main>";

  // Entry point
  public void run(AST.Program program) {
    final String basePackage = "com.dahl.lumina.lang.natives";
    try {
      Class.forName(basePackage + ".func.NativeExceptionFunction");
      Class.forName(basePackage + ".func.NativeMathFunction");
      Class.forName(basePackage + ".func.NativeIOFunction");
      Class.forName(basePackage + ".func.NativeTypeFunction");

    } catch (ClassNotFoundException e) {
      throw new RuntimeException("No se pudo cargar función nativa", e);
    }
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
        String previousFile = currentFile;
        try {
          src = Files.readString(Path.of(i.path() + ".lum"));
          currentFile = i.path() + ".lum";
          program = new Parser(new Lexer(src).tokenize()).parse();
        } catch (IOException e) {
          throw new RuntimeException(
              String.format("[%s] Error: Módulo '%s' no encontrado", currentFile, i.path()));
        }
        Module mod = loadModule(program);
        currentFile = previousFile;
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
        if (!c.type().equals("any"))
          TypeChecker.check(c.type(), val, c.name(), currentFile, c.line());
        env.defineConst(c.name(), val);
        env.defineType(c.name(), c.type());
        yield null;
      }
      case AST.ExportConst ec -> {
        execute(ec.decl(), env);
        yield null;
      }
      case AST.VarDecl v -> {
        Object val = v.initializer() != null ? evaluate(v.initializer(), env) : null;
        // Validar tipo solo si no es any y no hay inicializador
        if (!v.type().equals("any") && val != null)
          TypeChecker.check(v.type(), val, v.name(), currentFile, v.line());
        env.define(v.name(), val);
        env.defineType(v.name(), v.type());
        yield null;
      }

      case AST.If i -> {
        if (isTruthy(evaluate(i.condition(), env))) {
          executeBlock(i.thenBranch(), new Environment(env));
        } else if (i.elseBranch() != null) {
          if (i.elseBranch() instanceof AST.Block b) {
            executeBlock(b, new Environment(env));
          } else {
            execute(i.elseBranch(), env); // ejecuta if anidado
          }
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
          try {
            executeBlock(w.body(), new Environment(env));
          } catch (BreakSignal bs) {
            break;
          } catch (ContinueSignal cs) {
            continue;
          }
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
          try {
            executeBlock(f.body(), new Environment(forEnv));
          } catch (BreakSignal bs) {
            break;
          } catch (ContinueSignal sc) {
          }

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

      case AST.Break b -> throw new BreakSignal();
      case AST.Continue c -> throw new ContinueSignal();

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

      default -> throw new RuntimeException(
          String.format("[%s] Error: Nodo desconocido: %s", currentFile, node.getClass().getSimpleName()));
    };
  }

  private void executeBlock(AST.Block block, Environment env) {
    for (AST.Node stmt : block.statements()) {
      execute(stmt, env);
    }
  }

  // Evaluation

  @SuppressWarnings("unchecked")
  private Object evaluate(AST.Node node, Environment env) {
    return switch (node) {

      case AST.Literal l -> l.value();

      case AST.MapLiteral ml -> {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < ml.keys().size(); i++)
          map.put(ml.keys().get(i), evaluate(ml.values().get(i), env));
        yield map;
      }

      case AST.SetLiteral sl -> {
        Set<Object> set = new LinkedHashSet<>();
        for (AST.Node el : sl.elements())
          set.add(evaluate(el, env));

        yield set;
      }

      case AST.ArrayLiteral al -> {
        List<Object> elements = new ArrayList<>();
        for (AST.Node el : al.elements())
          elements.add(evaluate(el, env));
        yield elements;
      }

      case AST.PropertyAccess pa -> {
        Object target = evaluate(pa.object(), env);
        yield switch (pa.property()) {
          case "len" -> {
            if (target instanceof List<?> list)
              yield (double) list.size();
            if (target instanceof String s)
              yield (double) s.length();

            throw new RuntimeException(
                String.format("[%s] Error: 'len' no aplicable a este tipo: '%s'", currentFile,
                    target.getClass().getName()));
          }
          case "size" -> {
            if (target instanceof Map<?, ?> map)
              yield (double) map.size();

            if (target instanceof Set<?> set)
              yield (double) set.size();

            throw new RuntimeException(
                String.format("[%s] Error: 'size' no aplicada a este tipo: '%s'", currentFile,
                    target.getClass().getName()));

          }
          default -> throw new RuntimeException(
              String.format("[%s] Error: Propiedad '%s' desconocida", currentFile, pa.property()));
        };
      }

      case AST.MethodCall mc -> {
        Object target = evaluate(mc.object(), env);
        String method = mc.method();
        var args = mc.args();

        // strings
        if (target instanceof String str) {
          yield switch (method) {
            case "upper" -> {
              if (!args.isEmpty())
                throw new RuntimeException(
                    String.format("[%s] Error: str.upper() no espera argumentos, recibió %d", currentFile,
                        args.size()));

              yield str.toUpperCase();
            }

            case "lower" -> {
              if (!args.isEmpty())
                throw new RuntimeException(
                    String.format("[%s] Error: str.lower() no espera argumentos, recibió %d", currentFile,
                        args.size()));

              yield str.toLowerCase();
            }

            case "contains" -> {
              if (args.size() != 1)
                throw new RuntimeException(
                    String.format("[%s] Error: str.contains(str) espera 1 argumento, recibió %d", currentFile,
                        args.size()));

              String sub = stringify(evaluate(args.get(0), env));
              yield str.contains(sub);
            }

            case "replace" -> {
              if (args.size() != 2)
                throw new RuntimeException(
                    String.format("[%s] Error: str.replace(old, new) espera 2 argumentos, recibió %d", currentFile,
                        args.size()));

              String old = stringify(evaluate(args.get(0), env));
              String neo = stringify(evaluate(args.get(1), env));
              String result = str.replace(old, neo);

              yield result;
            }

            case "split" -> {

              if (args.size() != 1)
                throw new RuntimeException(
                    String.format("[%s] Error: str.split(sep) espera 1 argumento, recibió %d", currentFile,
                        args.size()));

              String sep = stringify(evaluate(args.get(0), env));
              String[] parts = str.split(sep, -1);
              List<Object> result = new ArrayList<>();
              for (String part : parts)
                result.add(part);
              yield result;
            }

            default -> throw new RuntimeException(
                String.format("[%s] Error: Método de string desconocido: '%s'", currentFile, method));
          };
        }

        // arrays
        if (target instanceof List list) {
          yield switch (method) {

            case "get" -> {

              if (args.size() != 1)
                throw new RuntimeException(
                    String.format("[%s] Error: array.get(idx) espera 1 argumento, recibió %d", currentFile,
                        args.size()));

              int idx = (int) toNumber(evaluate(args.get(0), env));

              if (idx < 0 || idx >= list.size())
                throw new RuntimeException(
                    String.format("[%] Error: Índice fuera de rango: %d (tamaño %d)", currentFile, idx, list.size()));

              yield list.get(idx);
            }

            case "push" -> {
              if (args.size() != 1)
                throw new RuntimeException(
                    String.format("[%s] Error: array.push(val) espera 1 argumento, recibió %d", currentFile,
                        args.size()));

              list.add(evaluate(args.get(0), env));
              yield null;
            }

            case "replace" -> {
              if (args.size() != 2)
                throw new RuntimeException(
                    String.format("[%s] Error: array.replace(idx, val) espera 2 argumentos, recibió %d", currentFile,
                        args.size()));

              int idx = (int) toNumber(evaluate(args.get(0), env));
              if (idx < 0 || idx >= list.size())
                throw new RuntimeException(
                    String.format("[%] Error: Índice fuera de rango: %d (tamaño %d)", currentFile, idx, list.size()));

              Object val = evaluate(args.get(1), env);
              list.set(idx, val);
              yield val;

            }

            case "pop" -> {
              if (list.isEmpty())
                throw new RuntimeException(
                    String.format("[%s] Error: array.pop() sobre array vacío", currentFile));
              if (args.size() != 0)
                throw new RuntimeException(
                    String.format("[%s] Error: array.pop() no espera argumento, recibió %d", currentFile,
                        args.size()));

              yield list.remove(list.size() - 1);
            }
            case "contains" -> {
              if (args.size() != 1)
                throw new RuntimeException(
                    String.format("[%s] Error: array.contains(val) espera 1 argumento, recibió %d", currentFile,
                        args.size()));

              Object target2 = evaluate(args.get(0), env);
              yield list.stream().anyMatch(e -> isEqual(e, target2));
            }
            default -> throw new RuntimeException(
                String.format("[%s] Error: Método de array desconocido: '%s'", currentFile, method));
          };
        }

        if (target instanceof Set set) {
          yield switch (method) {

            case "add" -> {

              if (args.size() != 1)
                throw new RuntimeException(
                    String.format("[%s] Error: set.add(elem) espera un argumento, recibió %d", currentFile,
                        args.size()));
              set.add(evaluate(args.get(0), env));
              yield null;
            }

            case "remove" -> {

              if (args.size() != 1)
                throw new RuntimeException(
                    String.format("[%s] Error: set.remove(val) espera un argumento, recibió %d", currentFile,
                        args.size()));
              Object val = evaluate(args.get(0), env);
              yield set.remove(val);
            }

            case "contains" -> {

              if (args.size() != 1)
                throw new RuntimeException(
                    String.format("[%s] Error: set.contains(elem) espera un argumento, recibió %d", currentFile,
                        args.size()));
              Object val = evaluate(args.get(0), env);
              yield set.contains(val);
            }

            case "to_array" -> {

              if (!args.isEmpty())
                throw new RuntimeException(
                    String.format("[%s] Error: set.to_array() no espera argumentos, recibió %d", currentFile,
                        args.size()));
              yield new ArrayList<>(set);
            }
            case "union" -> {

              if (args.size() != 1)
                throw new RuntimeException(
                    String.format("[%s] Error: set.union(set) espera un argumento, recibió %d", currentFile,
                        args.size()));

              Object other = evaluate(args.get(0), env);
              if (!(other instanceof Set otherSet))
                throw new RuntimeException(
                    String.format("[%s] Error: set.union(set) espera un argumento tipo 'set', recibió %s", currentFile,
                        other.getClass().getName()));

              Set<Object> result = new LinkedHashSet<>(set);
              result.addAll(otherSet);

              yield result;
            }

            default -> throw new RuntimeException(
                String.format("[%s] Error: Método de set desconocido: '%'", currentFile, method));
          };
        }

        if (target instanceof Map map) {
          yield switch (method) {
            case "get" -> {
              if (args.size() != 1)
                throw new RuntimeException(
                    String.format("[%s] Error: map.get(key) espera 1 argumento, recibió %d", currentFile,
                        args.size()));

              String key = stringify(evaluate(args.get(0), env));
              if (!map.containsKey(key))
                throw new RuntimeException(
                    String.format("[%s] Error: Clave no encontrada: '%s'", currentFile, key));
              yield map.get(key);
            }
            case "put" -> {
              if (args.size() != 2)
                throw new RuntimeException(
                    String.format("[%s] Error: map.put(k, v) espera 2 argumentos, recibió %d", currentFile,
                        args.size()));

              String key = stringify(evaluate(args.get(0), env));
              Object value = evaluate(args.get(1), env);
              yield map.put(key, value);
            }
            case "remove" -> {
              if (mc.args().size() != 1)
                throw new RuntimeException(
                    String.format("[%s] Error: map.remove(key) espera 1 argumento, recibió %d", currentFile,
                        args.size()));

              String key = stringify(evaluate(args.get(0), env));
              yield map.remove(key);
            }
            case "contains_key" -> {
              if (args.size() != 1)
                throw new RuntimeException(
                    String.format("[%s] Error: map.contains_key(key) espera 1 argumento, recibió %d", currentFile,
                        args.size()));

              String key = stringify(evaluate(args.get(0), env));
              yield map.containsKey(key);
            }
            case "contains_val" -> {
              if (args.size() != 1)
                throw new RuntimeException(
                    String.format("[%s] Error: map.contains_val(value) espera 1 argumento, recibió %d", currentFile,
                        args.size()));

              Object val = evaluate(args.get(0), env);
              yield map.containsValue(val);
            }
            case "keys" -> {
              if (!args.isEmpty())
                throw new RuntimeException(
                    String.format("[%s] Error: map.keys() no espera argumentos, recibió %d", currentFile,
                        args.size()));

              yield new ArrayList<>(map.keySet());
            }
            case "values" -> {
              if (!args.isEmpty())
                throw new RuntimeException(
                    String.format("[%s] Error: map.values() no espera argumentos, recibió %d", currentFile,
                        args.size()));

              yield new ArrayList<>(map.values());
            }
            case "clear" -> {
              if (!args.isEmpty())
                throw new RuntimeException(
                    String.format("[%s] Error: map.clear() no espera argumentos, recibió %d", currentFile,
                        args.size()));

              map.clear();
              yield null;
            }
            default -> throw new RuntimeException(
                String.format("[%s] Error: Método de mapa desconocido: '%s'", currentFile, method));
          };
        }
        throw new RuntimeException(
            String.format("[%s] Error: Método '%s' no aplicable a este tipo", currentFile, method));
      }

      case AST.Var v -> {
        try {
          yield env.get(v.name());
        } catch (RuntimeException e) {
          throw new RuntimeException(
              String.format("[%s] Error: Variable no definida: '%s'", currentFile, v.name()));
        }
      }

      case AST.Assign a -> {
        Object val = evaluate(a.value(), env);
        // Verificar tipo declarado si existe
        String declaredType = env.getType(a.name());
        if (declaredType != null && !declaredType.equals("any"))
          TypeChecker.check(declaredType, val, a.name(), currentFile, 0);
        env.set(a.name(), val);
        yield val;
      }

      case AST.Unary u -> {
        Object operand = evaluate(u.operand(), env);
        yield switch (u.op()) {
          case "-" -> -(double) toNumber(operand);
          case "!" -> !isTruthy(operand);
          default -> throw new RuntimeException(
              String.format("[%s] Error: Operador unario desconocido: '%s'", currentFile, u.op()));
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
              throw new RuntimeException(
                  String.format("[%s] Error: División por cero", currentFile));

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
          default -> throw new RuntimeException(
              String.format("[%s] Error: Operador desconocido: '%s'", currentFile, b.op()));
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
              throw new RuntimeException(
                  String.format("[%s] Error: División por cero en '%s'", currentFile, ca.name()));
            yield current / operand;
          }
          default -> throw new RuntimeException(
              String.format("[%s] Error: Operador compuesto desconocido: '%s'", currentFile, ca.op()));
        };

        // validar que el resultado sea compatible con el tipo declarado
        String declaredType = env.getType(ca.name());
        if (declaredType != null && !declaredType.equals("any")) {
          TypeChecker.check(declaredType, result, ca.name(), currentFile, 0);
        }
        env.set(ca.name(), result);
        yield result;
      }

      case
          AST.Increment inc -> {
        double current = toNumber(env.get(inc.name()));
        double next = inc.op().equals("++") ? current + 1 : current - 1;

        // validar que el resultado sea compatible con el tipo declarado
        String declaredType = env.getType(inc.name());
        if (declaredType != null && !declaredType.equals("any")) {
          TypeChecker.check(declaredType, next, inc.name(), currentFile, 0);
        }

        env.set(inc.name(), next);
        yield inc.prefix() ? next : current;
      }

      case
          AST.Ternary t -> {
        Object condition = evaluate(t.condition(), env);

        yield isTruthy(condition)
            ? evaluate(t.consequence(), env)
            : evaluate(t.alternative(), env);
      }

      case

          AST.FunCall fc -> {
        if (NativeFunction.isNative(fc.callee())) {
          List<Object> evaluatedArgs = new ArrayList<>();
          for (AST.Node arg : fc.args())
            evaluatedArgs.add(evaluate(arg, env));
          yield NativeFunction.call(fc.callee(), evaluatedArgs, fc.line(), currentFile);
        }

        // Lambda almacenada en variables
        Object maybeCallable = null;
        try {
          maybeCallable = env.get(fc.callee());
        } catch (RuntimeException ignored) {
        }

        if (maybeCallable instanceof LambdaValue lv) {
          List<String> params = lv.decl().params();
          List<AST.Node> args = fc.args();

          if (params.size() != args.size())
            throw new RuntimeException(
                String.format("[%s:%d] Error: lambda esoera %d argumento(s), recivió %d", currentFile, fc.line(),
                    params.size(), args.size()));

          Environment callEnv = new Environment(lv.closure());
          for (int i = 0; i < params.size(); i++)
            callEnv.define(params.get(i), evaluate(args.get(0), env));

          try {
            if (lv.decl().body() instanceof AST.Block block) {
              executeBlock(block, callEnv);
              yield null;
            } else {
              yield evaluate(lv.decl().body(), callEnv);
            }

          } catch (ReturnSignal rs) {
            yield rs.value;
          }
        }

        Function fn = resolveFunction(fc.callee());
        List<String> params = fn.decl().params();
        List<String> paramTypes = fn.decl().paramTypes();
        List<AST.Node> args = fc.args();

        if (params.size() != args.size())
          throw new RuntimeException(
              String.format("[%s:%d] Error: '%s' espera %d argumento(s), recibió %d",
                  currentFile, fc.line(), fc.callee(), params.size(), args.size()));

        Environment callEnv = new Environment(fn.closure());
        for (int i = 0; i < params.size(); i++) {
          Object argVal = evaluate(args.get(i), env);
          // Validar tipo del argumento
          String pType = paramTypes.get(i);
          if (!pType.equals("any"))
            TypeChecker.check(pType, argVal,
                fc.callee() + "(" + params.get(i) + ")", currentFile, fc.line());
          callEnv.define(params.get(i), argVal);
          callEnv.defineType(params.get(i), pType);
        }

        try {
          executeBlock(fn.decl().body(), callEnv);
          // Función declaró tipo de retorno pero no retornó nada
          if (!fn.decl().returnType().equals("any"))
            throw new RuntimeException(
                String.format("[%s:%d] Error: '%s' debe retornar '%s' pero no retornó nada",
                    currentFile, fc.line(), fc.callee(), fn.decl().returnType()));
          yield null;
        } catch (ReturnSignal rs) {
          // Validar tipo de retorno
          if (!fn.decl().returnType().equals("any"))
            TypeChecker.check(fn.decl().returnType(), rs.value,
                fc.callee() + " (retorno)", currentFile, fc.line());
          yield rs.value;
        }
      }

      case
          AST.ProcCall pc -> {
        Procedure pr = resolveProcedure(pc.callee());
        List<String> paramTypes = pr.decl().paramTypes();
        List<String> params = pr.decl().params();
        List<AST.Node> args = pc.args();

        if (params.size() != args.size())
          throw new RuntimeException(
              String.format("[%s:%d] Error: '%s' espera %d argumento(s), recibió %d",
                  currentFile, pc.line(), pc.callee(), params.size(), args.size()));

        Environment callEnv = new Environment(pr.closure());
        for (int i = 0; i < params.size(); i++) {
          Object argVal = evaluate(args.get(i), env);
          String pType = paramTypes.get(i);
          if (!pType.equals("any"))
            TypeChecker.check(pType, argVal, pc.callee() + "(" + params.get(i) + ")", currentFile, pc.line());

          callEnv.define(params.get(i), argVal);
          callEnv.defineType(params.get(i), pType);
        }

        try {
          executeBlock(pr.decl().body(), callEnv);
        } catch (ReturnSignal rs) {
          if (rs.value != null)
            throw new RuntimeException(
                String.format("[%s:%d] Error: El procedimiento '%s' no puede retornar un valor",
                    currentFile, pc.line(), pc.callee()));
        }
        yield null;
      }

      case
          AST.NamespaceCall nc -> {
        Module mod = modules.get(nc.namespace());
        if (mod == null)
          throw new RuntimeException(
              String.format("[%s] Error: Módulo no encontrado: '%s'", currentFile, nc.namespace()));

        Callable callable = mod.callables().get(nc.member());
        if (callable == null)
          throw new RuntimeException(
              String.format("[%s] Error: '%s' no existe en módulo '%s'",
                  currentFile, nc.member(), nc.namespace()));

        // Evaluar y validar argumentos
        List<Object> evaluatedArgs = new ArrayList<>();
        List<String> paramTypes = callable instanceof Function fn
            ? fn.decl().paramTypes()
            : ((Procedure) callable).decl().paramTypes();
        List<String> params = callable instanceof Function fn
            ? fn.decl().params()
            : ((Procedure) callable).decl().params();

        List<AST.Node> args = nc.args();
        if (params.size() != args.size())
          throw new RuntimeException(
              String.format("[%s] Error: '%s.%s' espera %d argumento(s), recibió %d",
                  currentFile, nc.namespace(), nc.member(), params.size(), args.size()));

        for (int i = 0; i < params.size(); i++) {
          Object argVal = evaluate(args.get(i), env);
          String pType = paramTypes.get(i);
          if (!pType.equals("any"))
            TypeChecker.check(pType, argVal,
                nc.namespace() + "." + nc.member() + "(" + params.get(i) + ")",
                currentFile, 0);
          evaluatedArgs.add(argVal);
        }

        Module previous = currentModule;
        String previousFile = currentFile;
        currentModule = mod;
        currentFile = nc.namespace() + ".ptl";

        try {
          if (callable instanceof Function fn) {
            Environment callEnv = new Environment(fn.closure());
            for (int i = 0; i < params.size(); i++) {
              callEnv.define(params.get(i), evaluatedArgs.get(i));
              callEnv.defineType(params.get(i), paramTypes.get(i));
            }
            try {
              executeBlock(fn.decl().body(), callEnv);
              if (!fn.decl().returnType().equals("any"))
                throw new RuntimeException(
                    String.format("[%s] Error: '%s.%s' debe retornar '%s' pero no retornó nada",
                        currentFile, nc.namespace(), nc.member(), fn.decl().returnType()));
              yield null;
            } catch (ReturnSignal rs) {
              if (!fn.decl().returnType().equals("any"))
                TypeChecker.check(fn.decl().returnType(), rs.value,
                    nc.namespace() + "." + nc.member() + " (retorno)", currentFile, 0);
              yield rs.value;
            }

          } else if (callable instanceof Procedure pr) {
            Environment callEnv = new Environment(pr.closure());
            for (int i = 0; i < params.size(); i++) {
              callEnv.define(params.get(i), evaluatedArgs.get(i));
              callEnv.defineType(params.get(i), paramTypes.get(i));
            }
            try {
              executeBlock(pr.decl().body(), callEnv);
            } catch (ReturnSignal rs) {
              if (rs.value != null)
                throw new RuntimeException(
                    String.format("[%s] Error: El procedimiento '%s.%s' no puede retornar un valor",
                        currentFile, nc.namespace(), nc.member()));
            }
            yield null;
          }
          throw new RuntimeException(
              String.format("[%s] Error: Callable desconocido en módulo '%s'",
                  currentFile, nc.namespace()));
        } finally {
          currentModule = previous;
          currentFile = previousFile;
        }
      }

      case
          AST.NamespaceVar nv -> {
        Module mod = modules.get(nv.namespace());
        if (mod == null)
          throw new RuntimeException(
              String.format("[%s] Error: Módulo no encontrado: '%s'", currentFile, nv.namespace()));
        yield mod.env().get(nv.member());
      }

      case AST.Lambda l -> new LambdaValue(l, env);

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
