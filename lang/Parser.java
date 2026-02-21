package lang;

import java.util.ArrayList;
import java.util.List;

import lang.Lexer.Token;
import lang.Lexer.TokenType;

import static lang.Lexer.TokenType.*;

/**
 * Parser
 */
public class Parser {

  private final List<Token> tokens;
  private int pos = 0;

  public Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  // Entry point
  public AST.Program parse() {
    List<AST.Node> stmts = new ArrayList<>();

    while (!check(EOF)) {
      stmts.add(parseStatement());
    }
    return new AST.Program(stmts);
  }
  // Statements

  private AST.Node parseStatement() {
    if (check(VAR))
      return parseVarDecl();
    if (check(FUN))
      return parseFunDecl();
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
    return parseExprStatement();
  }

  private AST.VarDecl parseVarDecl() {
    consume(VAR);
    String name = consume(IDENTIFIER).value;

    AST.Node init = null;
    if (match(ASSIGN))
      init = parseExpression();

    matchOptional(SEMICOLON);
    return new AST.VarDecl(name, init);
  }

  private AST.FunDecl parseFunDecl() {
    consume(FUN);
    String name = consume(IDENTIFIER).value;
    consume(LPAREN);
    List<String> params = new ArrayList<>();
    if (!check(RPAREN)) {
      params.add(consume(IDENTIFIER).value);
      while (match(COMMA)) {
        params.add(consume(IDENTIFIER).value);
      }
    }
    consume(RPAREN);
    AST.Block body = parseBlock();
    return new AST.FunDecl(name, params, body);
  }

  private AST.ProcDecl parseProcDecl() {
    consume(PROC);
    String name = consume(IDENTIFIER).value;
    consume(LPAREN);
    List<String> params = new ArrayList<>();

    if (!check(RPAREN)) {
      params.add(consume(IDENTIFIER).value);
      while (match(COMMA)) {
        params.add(consume(IDENTIFIER).value);
      }
    }

    consume(RPAREN);
    AST.Block body = parseBlock();
    return new AST.ProcDecl(name, params, body);
  }

  private AST.If parseIf() {
    consume(IF);
    consume(LPAREN);
    AST.Node condition = parseExpression();
    consume(RPAREN);
    AST.Block thenBranch = parseBlock();
    AST.Block elseBranch = null;
    if (match(ELSE)) {
      elseBranch = parseBlock();
    }
    return new AST.If(condition, thenBranch, elseBranch);
  }

  private AST.While parseWhile() {
    consume(WHILE);
    consume(LPAREN);
    AST.Node condition = parseExpression();
    consume(RPAREN);
    AST.Block body = parseBlock();
    return new AST.While(condition, body);
  }

  private AST.For parseFor() {
    consume(FOR);
    consume(LPAREN);
    AST.VarDecl init = parseVarDecl();
    AST.Node condition = parseExpression();
    matchOptional(SEMICOLON);
    // step: identifier = expr
    String stepName = consume(IDENTIFIER).value;
    consume(ASSIGN);
    AST.Node stepVal = parseExpression();
    AST.Assign step = new AST.Assign(stepName, stepVal);
    consume(RPAREN);
    AST.Block body = parseBlock();
    return new AST.For(init, condition, step, body);
  }

  private AST.Return parseReturn() {
    consume(RETURN);
    AST.Node value = check(SEMICOLON) || check(RBRACE) ? null : parseExpression();
    matchOptional(SEMICOLON);
    return new AST.Return(value);
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

  // ── Expressions (precedence climbing) ────────────────────────────────────

  private AST.Node parseExpression() {
    return parseAssignment();
  }

  private AST.Node parseAssignment() {
    // Check if it's an assignment: IDENTIFIER = expr
    if (check(IDENTIFIER) && peek(1).type == ASSIGN) {
      String name = consume(IDENTIFIER).value;
      consume(ASSIGN);
      AST.Node value = parseExpression();
      return new AST.Assign(name, value);
    }
    return parseOr();
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
    // Function call or variable
    if (check(IDENTIFIER)) {
      String name = advance().value;
      if (match(LPAREN)) {
        List<AST.Node> args = new ArrayList<>();
        if (!check(RPAREN)) {
          args.add(parseExpression());
          while (match(COMMA))
            args.add(parseExpression());
        }
        consume(RPAREN);
        return new AST.Call(name, args);
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
}
