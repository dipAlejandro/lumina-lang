package com.dahl.lumina.lang;

import java.util.List;

/**
 * Nodos del arbol de sintaxis abstracta
 */
public class AST {

public interface Node {}

// Statements
public record Program(List<Node> statements) implements Node{}
public record VarDecl(String type, String name, Node initializer, int line) implements Node {}
public record ConstDecl(String type, String name, Node initializer, int line) implements Node {}
public record Assign(String name, Node value) implements Node{}
public record CompoundAssign(String name, String op, Node value) implements Node {}
public record If(Node condition, Block thenBranch, Node elseBranch) implements Node {}
public record IfArrow(Node condition, Node body) implements Node {}
public record While(Node condition, Block body) implements Node {}
public record WhileArrow(Node condition, Node body) implements Node {}
public record For(VarDecl init, Node condition, Node step, Block body) implements Node {}
public record ForArrow(VarDecl init, Node condition, Node step, Node body) implements Node {}
public record Break() implements Node {}
public record Continue() implements Node {}
public record ProcDecl(String name, List<String> paramTypes, List<String> params, Block body) implements Node {}
public record FunDecl(String returnType, String name, List<String> paramTypes, List<String> params, Block body) implements Node {}
public record Return(Node value) implements Node {}
public record Print(Node value) implements Node {}
public record Block(List<Node> statements) implements Node {}
public record Import(String namespace,String path) implements Node {}
public record ExportFun(FunDecl decl) implements Node {}
public record ExportProc(ProcDecl decl) implements Node {}
public record ExportVar(VarDecl decl) implements Node {}
public record ExportConst(ConstDecl decl) implements Node {}
public record ExprStmt(Node expr) implements Node {}
public record ArrowExpr(Node condExpr, ProcCall procCall) implements Node {};

// Data structs
public record ArrayLiteral(List<Node> elements) implements Node {}
public record MapLiteral(List<String> keys, List<Node> values) implements Node {}
//public record MapAssign(Node object, Node key, Node value) implements Node {}
public record SetLiteral(List<Node> elements) implements Node {}

// Expressions
public record Binary(Node left, String op, Node right) implements Node {}
public record Unary(String op, Node operand) implements Node {}
public record Ternary(Node condition, Node consequence, Node alternative) implements Node {}
public record FunCall(String callee, List<Node> args, int line) implements Node {}
public record ProcCall(String callee, List<Node> args, int line) implements Node {}
public record NamespaceCall(String namespace, String member, List<Node> args) implements Node {}
public record NamespaceVar(String namespace, String member) implements Node {}
public record MethodCall(Node object, String method, List<Node> args) implements Node {}
public record PropertyAccess(Node object, String property) implements Node {}
public record Var(String name) implements Node {}
public record Literal(Object value) implements Node {}
public record Increment (String name, String op, boolean prefix) implements Node {}
}
