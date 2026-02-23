package com.dahl.lang;

import java.util.HashMap;
import java.util.Map;

/**
 * Ambito de variables con soporte de scopes anidados
 */
public class Environment {

  private final Map<String, Object> variables = new HashMap<>();
  private final Environment parent;

  public Environment(Environment parent) {
    this.parent = parent;
  }

  public Environment() {
    this(null);
  }

  public void define(String name, Object value) {
    variables.put(name, value);
  }

  public Object get(String name) {
    if (variables.containsKey(name))
      return variables.get(name);
    if (parent != null)
      return parent.get(name);
    throw new RuntimeException("Variable no definida: '" + name + "'");
  }

  public void set(String name, Object value) {
    if (variables.containsKey(name)) {
      variables.put(name, value);
      return;
    }

    if (parent != null) {
      parent.set(name, value);
      return;
    }

    throw new RuntimeException("Variable no definida: '" + name + "'");
  }

  public boolean has(String name) {
    return variables.containsKey(name) || (parent != null && parent.has(name));
  }
}
