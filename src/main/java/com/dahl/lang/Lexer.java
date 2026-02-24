package com.dahl.lang;

import java.util.ArrayList;
import java.util.List;

/**
 * Lexer
 */
public class Lexer {

  public enum TokenType {

    // Literals
    NUMBER, STRING, BOOLEAN, NULL,
    // Identifiers & keywords
    IDENTIFIER,
    VAR, CONST, FUN, PROC, RETURN, IF, ELSE, WHILE, FOR, PRINT, TRUE, FALSE, IMPORT, EXPORT, FROM,
    // Arithmetic operators
    PLUS, MINUS, STAR, SLASH, PERCENT,
    PLUS_ASSIGN, MINUS_ASSIGN, STAR_ASSIGN, SLASH_ASSIGN,

    // Logic operators
    EQ, NEQ, LT, LTE, GT, GTE,
    AND, OR, NOT,
    ASSIGN,
    QUESTION,

    // Binary operators
    PLUS_PLUS, MINUS_MINUS,
    // Special
    ARROW,
    // Delimiters
    LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET, COMMA, DOT, COLON, SEMICOLON,
    // Control
    EOF
  }

  public static class Token {
    public final TokenType type;
    public final String value;
    public final int line;

    public Token(TokenType type, String value, int line) {
      this.type = type;
      this.value = value;
      this.line = line;
    }

    @Override
    public String toString() {
      return String.format("Token(%s, '%s', line=%d)", type, value, line);
    }
  }

  private final String source;
  private int pos = 0;
  private int line = 1;
  private final List<Token> tokens = new ArrayList<>();

  public Lexer(String s) {
    this.source = s;
  }

  public List<Token> tokenize() {

    while (pos < source.length()) {
      skipWhiteSpaceAndComments();

      if (pos >= source.length())
        break;

      char c = source.charAt(pos);

      if (Character.isDigit(c))
        readNumber();
      else if (c == '"')
        readString();
      else if (Character.isLetter(c) || c == '_')
        readIdentifierOrKeyword();
      else
        readSymbol();
    }
    tokens.add(new Token(TokenType.EOF, "", line));

    return tokens;
  }

  private void skipWhiteSpaceAndComments() {
    while (pos < source.length()) {
      char c = source.charAt(pos);
      if (c == '\n') {
        line++;
        pos++;
      } else if (Character.isWhitespace(c))
        pos++;
      else if (c == '/' && peek(1) == '/') {
        while (pos < source.length() && source.charAt(pos) != '\n')
          pos++;
      } else
        break;
    }
  }

  private void readIdentifierOrKeyword() {
    int start = pos;
    while (pos < source.length() && (Character.isLetterOrDigit(source.charAt(pos)) || source.charAt(pos) == '_'))
      pos++;
    String word = source.substring(start, pos);
    TokenType type = switch (word) {
      case "var" -> TokenType.VAR;
      case "const" -> TokenType.CONST;
      case "fun" -> TokenType.FUN;
      case "proc" -> TokenType.PROC;
      case "return" -> TokenType.RETURN;
      case "if" -> TokenType.IF;
      case "else" -> TokenType.ELSE;
      case "while" -> TokenType.WHILE;
      case "for" -> TokenType.FOR;
      case "print" -> TokenType.PRINT;
      case "true" -> TokenType.TRUE;
      case "false" -> TokenType.FALSE;
      case "null" -> TokenType.NULL;
      case "import" -> TokenType.IMPORT;
      case "export" -> TokenType.EXPORT;
      case "from" -> TokenType.FROM;
      default -> TokenType.IDENTIFIER;
    };
    tokens.add(new Token(type, word, line));
  }

  private void readSymbol() {
    char c = source.charAt(pos++);
    switch (c) {
      case '+' -> {
        if (peek(0) == '+') {
          pos++;
          tokens.add(new Token(TokenType.PLUS_PLUS, "++", line));
        } else if (peek(0) == '=') {
          pos++;
          tokens.add(new Token(TokenType.PLUS_ASSIGN, "+=", line));
        } else
          tokens.add(new Token(TokenType.PLUS, "+", line));
      }

      case '-' -> {
        if (peek(0) == '>') {
          pos++;
          tokens.add(new Token(TokenType.ARROW, "->", line));
        } else if (peek(0) == '-') {
          pos++;
          tokens.add(new Token(TokenType.MINUS_MINUS, "--", line));
        } else if (peek(0) == '=') {
          pos++;
          tokens.add(new Token(TokenType.MINUS_ASSIGN, "-=", line));
        } else
          tokens.add(new Token(TokenType.MINUS, "-", line));
      }

      case '*' -> {
        if (peek(0) == '=') {
          pos++;
          tokens.add(new Token(TokenType.STAR_ASSIGN, "*=", line));
        } else {
          tokens.add(new Token(TokenType.STAR, "*", line));
        }
      }

      case '/' -> {
        if (peek(0) == '=') {
          pos++;
          tokens.add(new Token(TokenType.SLASH_ASSIGN, "/=", line));
        } else {
          tokens.add(new Token(TokenType.SLASH, "/", line));
        }
      }

      case '%' -> tokens.add(new Token(TokenType.PERCENT, "%", line));
      case '(' -> tokens.add(new Token(TokenType.LPAREN, "(", line));
      case ')' -> tokens.add(new Token(TokenType.RPAREN, ")", line));
      case '{' -> tokens.add(new Token(TokenType.LBRACE, "{", line));
      case '}' -> tokens.add(new Token(TokenType.RBRACE, "}", line));
      case '[' -> tokens.add(new Token(TokenType.LBRACKET, "[", line));
      case ']' -> tokens.add(new Token(TokenType.RBRACKET, "]", line));
      case ',' -> tokens.add(new Token(TokenType.COMMA, ",", line));
      case ';' -> tokens.add(new Token(TokenType.SEMICOLON, ";", line));
      case '?' -> tokens.add(new Token(TokenType.QUESTION, "?", line));
      case ':' -> tokens.add(new Token(TokenType.COLON, ":", line));
      case '.' -> tokens.add(new Token(TokenType.DOT, ".", line));
      case '=' -> {
        if (peek(0) == '=') {
          pos++;
          tokens.add(new Token(TokenType.EQ, "==", line));
        } else
          tokens.add(new Token(TokenType.ASSIGN, "=", line));
      }
      case '!' -> {
        if (peek(0) == '=') {
          pos++;
          tokens.add(new Token(TokenType.NEQ, "!=", line));
        } else
          tokens.add(new Token(TokenType.NOT, "!", line));
      }
      case '<' -> {
        if (peek(0) == '=') {
          pos++;
          tokens.add(new Token(TokenType.LTE, "<=", line));
        } else
          tokens.add(new Token(TokenType.LT, "<", line));
      }
      case '>' -> {
        if (peek(0) == '=') {
          pos++;
          tokens.add(new Token(TokenType.GTE, ">=", line));
        } else
          tokens.add(new Token(TokenType.GT, ">", line));
      }
      case '&' -> {
        if (peek(0) == '&') {
          pos++;
          tokens.add(new Token(TokenType.AND, "&&", line));
        }
      }
      case '|' -> {
        if (peek(0) == '|') {
          pos++;
          tokens.add(new Token(TokenType.OR, "||", line));
        }
      }
      default -> throw new RuntimeException("Carácter inesperado: '" + c + "' en línea " + line);
    }
  }

  private char peek(int offset) {
    int idx = pos + offset;
    return idx < source.length() ? source.charAt(idx) : '\0';
  }

  private void readString() {
    pos++; // skip opening "
    StringBuilder sb = new StringBuilder();
    while (pos < source.length() && source.charAt(pos) != '"') {
      if (source.charAt(pos) == '\\' && pos + 1 < source.length()) {
        char esc = source.charAt(++pos);
        switch (esc) {
          case 'n':
            sb.append('\n');
            break;
          case 't':
            sb.append('\t');
            break;
          default:
            sb.append(esc);
        }
      } else {
        sb.append(source.charAt(pos));
      }
      pos++;
    }
    pos++; // skip closing "
    tokens.add(new Token(TokenType.STRING, sb.toString(), line));
  }

  private void readNumber() {
    int start = pos;
    while (pos < source.length() && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '.'))
      pos++;
    tokens.add(new Token(TokenType.NUMBER, source.substring(start, pos), line));
  }

}
