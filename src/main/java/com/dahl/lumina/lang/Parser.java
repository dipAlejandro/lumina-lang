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
  private final Set<String> moduleNames = new HashSet<>();
  private int pos = 0;

  public Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  // Entry point
  public AST.Program parse() {

    // Primera pasada: Registrar que nombres son procs
    for (Token t : tokens) {
      if (t.type == IMPORT) {
        int idx = tokens.indexOf(t) + 1;
        moduleNames.add(tokens.get(idx).value);
      }

      if (t.type == CONST) {
        int idx = tokens.indexOf(t) + 1;
        constNames.add(tokens.get(idx).value);
      }
      if (t.type == PROC) {
        int idx = tokens.indexOf(t) + 1;
        procNames.add(tokens.get(idx).value);
      }
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

    // Asignación simple: i = expr
    if (check(IDENTIFIER) && peek(1).type == ASSIGN) {
      String name = consume(IDENTIFIER).value;
      if (constNames.contains(name))
        throw new RuntimeException("No se puede reasignar la constante '" + name + "' en línea " + current().line);
      consume(ASSIGN);
      return new AST.Assign(name, parseExpression());
    }

    // Sufijo: i++ / i--
    if (check(IDENTIFIER) && isSuffixIncrement(peek(1).type)) {
      String name = consume(IDENTIFIER).value;
      if (constNames.contains(name))
        throw new RuntimeException("No se puede reasignar la constante '" + name + "' en línea " + current().line);
      String op = advance().value;
      return new AST.Increment(name, op, false);
    }

    return parseTernary();
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
    if (check(ARRAY) && peek(1).type == LPAREN) {
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

    if (check(MAP) && peek(1).type == LPAREN) {
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

    if (check(SET) && peek(1).type == LPAREN) {
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

      // Sufijo: i++ / i--
      if (check(PLUS_PLUS) || check(MINUS_MINUS)) {
        String op = advance().value;
        return new AST.Increment(name, op, false);
      }

      // Acceso a propiedad o método: arr.length, arr.push(x), namespace.fun()
      if (match(DOT)) {
        String member = consume(IDENTIFIER).value;
        if (match(LPAREN)) {
          List<AST.Node> args = new ArrayList<>();
          if (!check(RPAREN)) {
            args.add(parseExpression());
            while (match(COMMA))
              args.add(parseExpression());
          }
          consume(RPAREN);
          // Si es namespace conocido → NamespaceCall, si no → MethodCall
          if (moduleNames.contains(name)) {
            return new AST.NamespaceCall(name, member, args);
          }
          return new AST.MethodCall(new AST.Var(name), member, args);
        }
        // Propiedad sin paréntesis: arr.length o namespace.PI
        if (moduleNames.contains(name)) {
          return new AST.NamespaceVar(name, member);
        }
        return new AST.PropertyAccess(new AST.Var(name), member);
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
}
