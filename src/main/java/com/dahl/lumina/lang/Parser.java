package com.dahl.lumina.lang;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.dahl.lumina.lang.AST;
import com.dahl.lumina.lang.Lexer.Token;

import com.dahl.lumina.lang.Lexer.TokenType;
import static com.dahl.lumina.lang.Lexer.TokenType.*;

/**
 * Parser
 */
public class Parser {

  private final List<Token> tokens;
  private final Set<String> procNames = new HashSet<>();
  private final Set<String> constNames = new HashSet<>();
  private final Set<String> structNames = new HashSet<>();
  private final Set<String> moduleNames = new HashSet<>();
  private int pos = 0;

  public Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  // Entry point
  public AST.Program parse() {

    // Primera pasada: Registrar que nombres son procs, constn modulos y structs
    for (int i = 0; i < tokens.size(); i++) {
      Token t = tokens.get(i);
      int nextIdx = i + 1;

      if (nextIdx >= tokens.size())
        continue;

      if (t.type == IMPORT)
        moduleNames.add(tokens.get(nextIdx).value);

      if (t.type == CONST)
        constNames.add(tokens.get(nextIdx).value);

      if (t.type == PROC)
        procNames.add(tokens.get(nextIdx).value);

      if (t.type == STRUCT)
        structNames.add(tokens.get(nextIdx).value);
    }

    // Segunda pasada: parsear normal
    List<AST.Node> stmts = new ArrayList<>();

    while (!check(EOF))
      stmts.add(parseStatement());

    return new AST.Program(stmts);
  }
  // Statements

  private AST.Node parseStatement() {

    if (check(VAR))
      return parseVarDecl(false);
    if (check(FUN))
      return parseFunDecl();
    if (check(CONST))
      return parseConstDecl();
    if (check(PROC))
      return parseProcDecl();
    if (check(IF))
      return parseIf();
    if (check(WHILE))
      return parseWhile();
    if (check(FOR))
      return parseFor();
    if (check(RETURN))
      return parseReturn();
    if (check(PRINT))
      return parsePrint();
    if (check(LBRACE))
      return parseBlock();
    if (check(IMPORT))
      return parseImport();
    if (check(EXPORT))
      return parseExport();
    if (check(BREAK))
      return parseBreak();
    if (check(CONTINUE))
      return parseContinue();
    if (check(STRUCT))
      return parseStructDecl();
    if (check(IMPL))
      return parseImplDecl();
    return parseExprStatement();
  }

  private AST.VarDecl parseVarDecl(boolean requiredSemicolon) {
    int line = current().line;
    consume(VAR);
    // Tipado opcional
    String type = checkType() ? consumeType() : "any";
    String name = consume(IDENTIFIER).value;

    AST.Node init = null;
    if (match(ASSIGN))
      init = parseExpression();

    if (requiredSemicolon)
      consume(SEMICOLON);
    else
      matchOptional(SEMICOLON);

    return new AST.VarDecl(type, name, init, line);
  }

  private AST.ConstDecl parseConstDecl() {
    int line = current().line;
    consume(CONST);
    // Tipado opcional
    String type = checkType() ? consumeType() : "any";
    String name = consume(IDENTIFIER).value;
    consume(ASSIGN);
    AST.Node init = parseExpression();
    matchOptional(SEMICOLON);
    return new AST.ConstDecl(type, name, init, line);
  }

  private AST.FunDecl parseFunDecl() {
    consume(FUN);
    // Tipo de retorno obligatorio
    String returnType = consumeType();
    String name = consume(IDENTIFIER).value;
    consume(LPAREN);
    List<String> paramTypes = new ArrayList<>();
    List<String> params = new ArrayList<>();
    if (!check(RPAREN)) {
      String pType = checkType() ? consumeType() : "any";
      paramTypes.add(pType);
      params.add(consume(IDENTIFIER).value);
      while (match(COMMA)) {
        pType = checkType() ? consumeType() : "any";
        paramTypes.add(pType);
        params.add(consume(IDENTIFIER).value);
      }
    }
    consume(RPAREN);
    AST.Block body = parseBlock();
    return new AST.FunDecl(returnType, name, paramTypes, params, body);
  }

  private AST.ProcDecl parseProcDecl() {
    consume(PROC);
    String name = consume(IDENTIFIER).value;
    consume(LPAREN);

    List<String> paramTypes = new ArrayList<>();
    List<String> params = new ArrayList<>();

    if (!check(RPAREN)) {
      String pType = checkType() ? consumeType() : "any";
      paramTypes.add(pType);
      params.add(consume(IDENTIFIER).value);
      while (match(COMMA)) {
        pType = checkType() ? consumeType() : "any";
        paramTypes.add(pType);
        params.add(consume(IDENTIFIER).value);
      }
    }
    consume(RPAREN);
    AST.Block body = parseBlock();
    return new AST.ProcDecl(name, paramTypes, params, body);
  }

  private AST.Node parseIf() {
    consume(IF);
    consume(LPAREN);
    AST.Node condition = parseExpression();
    consume(RPAREN);

    // If arrow
    if (match(ARROW)) {
      AST.Node body = parseExpression();
      matchOptional(SEMICOLON);
      return new AST.IfArrow(condition, body);
    }

    // If con bloque
    AST.Block thenBranch = parseBlock();
    AST.Node elseBranch = null;
    if (match(ELSE)) {
      if (check(IF)) {
        elseBranch = parseIf();
      } else {
        elseBranch = parseBlock();
      }
    }
    return new AST.If(condition, thenBranch, elseBranch);
  }

  private AST.Node parseWhile() {
    consume(WHILE);
    consume(LPAREN);
    AST.Node condition = parseExpression();
    consume(RPAREN);

    // While arrow
    if (match(ARROW)) {
      AST.Node body = parseExpression();
      matchOptional(SEMICOLON);
      return new AST.WhileArrow(condition, body);
    }

    // While con bloque
    AST.Block body = parseBlock();
    return new AST.While(condition, body);
  }

  private AST.Node parseFor() {
    consume(FOR);
    consume(LPAREN);
    AST.VarDecl init = parseVarDecl(true);
    AST.Node condition = parseExpression();
    consume(SEMICOLON);

    AST.Node step = parseExpression();
    consume(RPAREN);

    // For arrow
    if (match(ARROW)) {
      AST.Node body = parseExpression();
      matchOptional(SEMICOLON);
      return new AST.ForArrow(init, condition, step, body);
    }

    // For con bloque
    AST.Block body = parseBlock();
    return new AST.For(init, condition, step, body);
  }

  private AST.Return parseReturn() {
    consume(RETURN);
    AST.Node value = check(SEMICOLON) || check(RBRACE) ? null : parseExpression();
    matchOptional(SEMICOLON);
    return new AST.Return(value);
  }

  private AST.Break parseBreak() {
    consume(BREAK);
    matchOptional(SEMICOLON);
    return new AST.Break();
  }

  private AST.Continue parseContinue() {
    consume(CONTINUE);
    matchOptional(SEMICOLON);
    return new AST.Continue();
  }

  private AST.Print parsePrint() {
    consume(PRINT);
    consume(LPAREN);
    AST.Node value = parseExpression();
    consume(RPAREN);
    matchOptional(SEMICOLON);
    return new AST.Print(value);
  }

  private AST.Block parseBlock() {
    consume(LBRACE);
    List<AST.Node> stmts = new ArrayList<>();
    while (!check(RBRACE) && !check(EOF)) {
      stmts.add(parseStatement());
    }
    consume(RBRACE);
    return new AST.Block(stmts);
  }

  private AST.Node parseExprStatement() {
    AST.Node expr = parseExpression();
    matchOptional(SEMICOLON);
    return new AST.ExprStmt(expr);
  }

  // Sintaxis import math from "std/math";
  private AST.Import parseImport() {
    consume(IMPORT);
    String namespace = consume(IDENTIFIER).value;
    consume(FROM);
    String path = consume(STRING).value;
    consume(SEMICOLON);

    return new AST.Import(namespace, path);

  }

  // export fun/proc/var
  private AST.Node parseExport() {
    consume(EXPORT);
    if (check(FUN))
      return new AST.ExportFun((AST.FunDecl) parseFunDecl());
    if (check(PROC))
      return new AST.ExportProc((AST.ProcDecl) parseProcDecl());
    if (check(VAR))
      return new AST.ExportVar(parseVarDecl(true));
    if (check(CONST))
      return new AST.ExportConst(parseConstDecl());
    throw new RuntimeException("Se esperaba fun, proc o var después de export en línea " + current().line);
  }

  private AST.StructDecl parseStructDecl() {
    consume(STRUCT);
    String name = consume(IDENTIFIER).value;
    consume(LBRACE);

    List<AST.FieldDecl> fields = new ArrayList<>();

    while (!check(RBRACE)) {
      String type = checkType() ? consumeType() : "any";
      String fieldName = consume(IDENTIFIER).value;
      AST.Node defaultValue = null;

      if (match(ASSIGN))
        defaultValue = parseExpression();

      consume(SEMICOLON);
      fields.add(new AST.FieldDecl(type, fieldName, defaultValue));
    }
    consume(RBRACE);
    return new AST.StructDecl(name, fields);
  }

  private AST.ImplDecl parseImplDecl() {

    /*
     * impl structName {
     * fun type name() { }
     * ...
     * }
     */

    consume(IMPL);
    String structName = consume(IDENTIFIER).value;
    consume(LBRACE);

    List<AST.MethodDecl> methods = new ArrayList<>();

    while (!check(RBRACE)) {
      consume(FUN);

      String returnType = checkType() ? consumeType() : "any";
      String methodName = consume(IDENTIFIER).value;

      consume(LPAREN);

      List<String> params = new ArrayList<>();
      List<String> paramTypes = new ArrayList<>();

      if (!check(RPAREN)) {
        do {
          String ptype = checkType() ? consumeType() : "any";
          String pname = consume(IDENTIFIER).value;

          paramTypes.add(ptype);
          params.add(pname);

        } while (match(COMMA));
      }

      consume(RPAREN);
      AST.Block body = parseBlock();
      methods.add(new AST.MethodDecl(methodName, params, paramTypes, returnType, body));
    }
    consume(RBRACE);
    return new AST.ImplDecl(structName, methods);
  }

  // ── Expressions (precedence climbing) ────────────────────────────────────

  private AST.Node parseExpression() {
    return parseAssignment();
  }

  private AST.Node parseAssignment() {

    // Asignación compuesta: i += 1
    if (check(IDENTIFIER) && isCompoundAssign(peek(1).type)) {
      String name = consume(IDENTIFIER).value;
      if (constNames.contains(name))
        throw new RuntimeException("No se puede reasignar la constante '" + name + "' en línea " + current().line);
      String op = advance().value;
      return new AST.CompoundAssign(name, op, parseExpression());
    }

    // Sufijo: i++ / i--
    if (check(IDENTIFIER) && isSuffixIncrement(peek(1).type)) {
      String name = consume(IDENTIFIER).value;
      if (constNames.contains(name))
        throw new RuntimeException("No se puede reasignar la constante '" + name + "' en línea " + current().line);
      String op = advance().value;
      return new AST.Increment(name, op, false);
    }

    AST.Node target = parseTernary();

    // Asignación simple: i = expr o expr.campo = expr
    if (match(ASSIGN)) {
      if (target instanceof AST.Var v) {
        if (constNames.contains(v.name()))
          throw new RuntimeException(
              "No se puede reasignar la constante '" + v.name() + "' en línea " + current().line);
        return new AST.Assign(v.name(), parseExpression());
      }

      if (target instanceof AST.PropertyAccess pa)
        return new AST.PropertyAssign(pa.object(), pa.property(), parseExpression());

      throw new RuntimeException("Objetivo de asignación inválido en línea " + current().line);
    }

    return target;
  }

  private AST.Node parseTernary() {
    AST.Node condition = parseOr();

    if (!match(QUESTION))
      return condition; // No es ternario, devolver normal

    AST.Node consequence = parseExpression(); // Rama verdadera
    consume(COLON); // el : es obligatorio
    AST.Node alternative = parseExpression(); // Rama falsa

    return new AST.Ternary(condition, consequence, alternative);
  }

  private AST.Node parseOr() {
    AST.Node left = parseAnd();
    while (check(OR)) {
      String op = consume(OR).value;
      left = new AST.Binary(left, op, parseAnd());
    }
    return left;
  }

  private AST.Node parseAnd() {
    AST.Node left = parseEquality();
    while (check(AND)) {
      String op = consume(AND).value;
      left = new AST.Binary(left, op, parseEquality());
    }
    return left;
  }

  private AST.Node parseEquality() {
    AST.Node left = parseComparison();
    while (check(EQ) || check(NEQ)) {
      String op = advance().value;
      left = new AST.Binary(left, op, parseComparison());
    }
    return left;
  }

  private AST.Node parseComparison() {
    AST.Node left = parseAddSub();
    while (check(LT) || check(LTE) || check(GT) || check(GTE)) {
      String op = advance().value;
      left = new AST.Binary(left, op, parseAddSub());
    }
    return left;
  }

  private AST.Node parseAddSub() {
    AST.Node left = parseMulDiv();
    while (check(PLUS) || check(MINUS)) {
      String op = advance().value;
      left = new AST.Binary(left, op, parseMulDiv());
    }
    return left;
  }

  private AST.Node parseMulDiv() {
    AST.Node left = parseUnary();
    while (check(STAR) || check(SLASH) || check(PERCENT)) {
      String op = advance().value;
      left = new AST.Binary(left, op, parseUnary());
    }
    return left;
  }

  private AST.Node parseUnary() {
    if (check(NOT) || check(MINUS)) {
      String op = advance().value;
      return new AST.Unary(op, parseUnary());
    }

    if (check(PLUS_PLUS) || check(MINUS_MINUS)) {
      String op = advance().value;
      String name = consume(IDENTIFIER).value;
      return new AST.Increment(name, op, true);
    }

    return parsePrimary();
  }

  private AST.Node parsePrimary() {

    // Number
    if (check(NUMBER)) {
      String val = advance().value;
      return new AST.Literal(Double.parseDouble(val));
    }
    // String
    if (check(STRING))
      return new AST.Literal(advance().value);
    // Boolean
    if (check(TRUE)) {
      advance();
      return new AST.Literal(true);
    }
    if (check(FALSE)) {
      advance();
      return new AST.Literal(false);
    }
    // Null
    if (check(NULL)) {
      advance();
      return new AST.Literal(null);
    }

    // Constructores de tipos
    if (check(IDENTIFIER) && current().value.equals("array_of") && peek(1).type == LPAREN) {
      advance();
      consume(LPAREN);
      List<AST.Node> elements = new ArrayList<>();
      if (!check(RPAREN)) {
        elements.add(parseExpression());
        while (match(COMMA))
          elements.add(parseExpression());
      }
      consume(RPAREN);
      return new AST.ArrayLiteral(elements);
    }

    if (check(IDENTIFIER) && current().value.equals("map_of") && peek(1).type == LPAREN) {
      advance();
      consume(LPAREN);
      List<String> keys = new ArrayList<>();
      List<AST.Node> values = new ArrayList<>();

      if (!check(RPAREN)) {
        String key = consume(STRING).value;
        consume(COLON);
        AST.Node value = parseExpression();
        keys.add(key);
        values.add(value);
        while (match(COMMA)) {
          key = consume(STRING).value;
          consume(COLON);
          value = parseExpression();
          keys.add(key);
          values.add(value);
        }
      }
      consume(RPAREN);
      return new AST.MapLiteral(keys, values);
    }

    if (check(IDENTIFIER) && current().value.equals("set_of") && peek(1).type == LPAREN) {
      advance();
      consume(LPAREN);
      List<AST.Node> elements = new ArrayList<>();
      if (!check(RPAREN)) {
        elements.add(parseExpression());
        while (match(COMMA))
          elements.add(parseExpression());
      }
      consume(RPAREN);
      return new AST.SetLiteral(elements);
    }

    // LAMBDAS
    if (check(LPAREN) && isLambda()) {
      return parseLambda();
    }

    // Function call, procedure call or variable
    if (check(IDENTIFIER)) {
      String name = advance().value;
      // Detectar constructor de structs
      if (structNames.contains(name) && match(LPAREN)) {
        List<String> argNames = new ArrayList<>();
        List<AST.Node> argValues = new ArrayList<>();

        if (!check(RPAREN)) {
          String argName = consume(IDENTIFIER).value;
          consume(COLON);
          AST.Node argValue = parseExpression();
          argNames.add(argName);
          argValues.add(argValue);

          while (match(COMMA)) {
            argName = consume(IDENTIFIER).value;
            consume(COLON);
            argValue = parseExpression();
            argNames.add(argName);
            argValues.add(argValue);
          }
        }
        consume(RPAREN);
        return new AST.StructCreate(name, argNames, argValues);
      }

      // Sufijo: i++ / i--
      if (check(PLUS_PLUS) || check(MINUS_MINUS)) {
        String op = advance().value;
        return new AST.Increment(name, op, false);
      }

      // Acceso a propiedad o método: arr.length, arr.push(x), namespace.fun()
      if (match(DOT)) {
        AST.Node target = new AST.Var(name);

        do {
          String member = advance().value;
          if (match(LPAREN)) {
            List<AST.Node> args = new ArrayList<>();
            if (!check(RPAREN)) {
              args.add(parseExpression());
              while (match(COMMA))
                args.add(parseExpression());
            }
            consume(RPAREN);
            if (target instanceof AST.Var v && moduleNames.contains(v.name())) {
              target = new AST.NamespaceCall(v.name(), member, args);
            } else {
              target = new AST.MethodCall(target, member, args);
            }
          } else {
            if (target instanceof AST.Var v && moduleNames.contains(v.name())) {
              target = new AST.NamespaceVar(v.name(), member);
            } else {
              target = new AST.PropertyAccess(target, member);
            }
          }
        } while (match(DOT)); // ← seguir mientras haya más '.'

        return target;
      }

      // Llamada a función o proc
      if (match(LPAREN)) {
        int line = tokens.get(pos - 1).line;
        List<AST.Node> args = new ArrayList<>();
        if (!check(RPAREN)) {
          args.add(parseExpression());
          while (match(COMMA))
            args.add(parseExpression());
        }
        consume(RPAREN);
        if (procNames.contains(name))
          return new AST.ProcCall(name, args, line);
        return new AST.FunCall(name, args, line);
      }

      return new AST.Var(name);
    }

    // Grouped expression
    if (match(LPAREN)) {
      AST.Node expr = parseExpression();
      consume(RPAREN);
      return expr;
    }

    throw new RuntimeException(
        "Se esperaba una expresión en línea " + current().line + " pero se encontró: '" + current().value + "'");
  }

  private AST.Lambda parseLambda() {
    consume(LPAREN);

    List<String> params = new ArrayList<>();

    if (!check(RPAREN)) {
      params.add(consume(IDENTIFIER).value);

      while (match(COMMA))
        params.add(consume(IDENTIFIER).value);
    }

    consume(RPAREN);
    consume(ARROW);

    AST.Node body;
    if (check(LBRACE)) {
      body = parseBlock(); // Bloque: (a, b) { return a + b; }
    } else {
      body = parseExpression(); // Expresion simple: (a, b) -> a + b
    }

    return new AST.Lambda(params, body);
  }

  // ── Token utilities ───────────────────────────────────────────────────────

  private Token consume(TokenType type) {
    if (!check(type)) {
      throw new RuntimeException(
          "Se esperaba " + type + " en línea " + current().line +
              " pero se encontró '" + current().value + "'");
    }
    return advance();
  }

  private boolean match(TokenType type) {
    if (check(type)) {
      advance();
      return true;
    }
    return false;
  }

  private void matchOptional(TokenType type) {
    match(type);
  }

  private boolean check(TokenType type) {
    return current().type == type;
  }

  private Token advance() {
    Token t = tokens.get(pos);
    if (t.type != EOF)
      pos++;
    return t;
  }

  private Token current() {
    return tokens.get(pos);
  }

  private Token peek(int offset) {
    int idx = pos + offset;
    return idx < tokens.size() ? tokens.get(idx) : tokens.get(tokens.size() - 1);
  }

  private boolean isCompoundAssign(TokenType type) {
    return type == PLUS_ASSIGN || type == MINUS_ASSIGN || type == STAR_ASSIGN || type == SLASH_ASSIGN;
  }

  private boolean isSuffixIncrement(TokenType type) {
    return type == PLUS_PLUS || type == MINUS_MINUS;
  }

  private boolean checkType() {
    return check(INT) || check(FLOAT) || check(STR) ||
        check(BOOL) || check(ARRAY) || check(MAP) || check(SET) || check(ANY);
  }

  private String consumeType() {
    if (!checkType())
      throw new RuntimeException(
          "Se esperaba un tipo en línea " + current().line +
              " pero se encontró '" + current().value + "'");
    return advance().value;
  }

  private boolean isLambda() {
    // Sintaxis:
    // (a, b) -> o (a, b) { ... }

    // Buscar RPAREN seguido de ARROW o BRACE
    int i = pos + 1; // Saltar LPAREN
    while (i < tokens.size()) {
      TokenType t = tokens.get(i).type;
      if (t == IDENTIFIER || t == COMMA) {
        i++;
      } else if (t == RPAREN) {
        // despues de RPAREN debe venir ARROW
        return i + 1 < tokens.size() && tokens.get(i + 1).type == ARROW;
      } else {
        return false;
      }
    }
    return false;
  }

  private Token consumeMemberName() {
    // Acepta IDENTIFIER o cualquier keyword como nombre de método/propiedad
    if (check(IDENTIFIER) || checkType() || check(MAP) || check(SET) || check(ARRAY))
      return advance();
    throw new RuntimeException(
        "Se esperaba un nombre de método en línea " + current().line +
            " pero se encontró '" + current().value + "'");
  }
}
