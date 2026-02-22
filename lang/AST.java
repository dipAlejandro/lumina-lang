package lang;

import java.util.List;

/**
 * Nodos del arbol de sintaxis abstracta
 */
public class AST {

public interface Node {}
public interface Incrementable extends Node {}
// Statements

public record Program(List<Node> statements) implements Node{}
public record VarDecl(String name, Node initializer) implements Node {}
public record Assign(String name, Node value) implements Node{}
public record CompoundAssign(String name, String op, Node value) implements Node {}
public record If(Node condition, Block thenBranch, Block elseBranch) implements Node {}
public record IfArrow(Node condition, Node body) implements Node {}
public record While(Node condition, Block body) implements Node {}
public record WhileArrow(Node condition, Node body) implements Node {}
public record For(VarDecl init, Node condition, Assign step, Block body) implements Node {}
public record ForArrow(VarDecl init, Node condition, Assign step, Node body) implements Node {}
public record ProcDecl(String name, List<String> params, Block body) implements Node {}
public record FunDecl(String name, List<String> params, Block body) implements Node {}
public record Return(Node value) implements Node {}
public record Print(Node value) implements Node {}
public record Block(List<Node> statements) implements Node {}
public record ExprStmt(Node expr) implements Node {}
public record ArrowExpr(Node condExpr, ProcCall procCall) implements Node {};

// Expressions

public record Binary(Node left, String op, Node right) implements Node {}
public record Unary(String op, Node operand) implements Node {}
public record Ternary(Node condition, Node consequence, Node alternative) implements Node {}
public record FunCall(String callee, List<Node> args) implements Node {}
public record ProcCall(String callee, List<Node> args) implements Node {}
public record Var(String name) implements Node {}
public record Literal(Object value) implements Node {}

}
