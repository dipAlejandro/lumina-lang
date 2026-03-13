package com.dahl.lumina.lang;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ambito de variables con soporte de scopes anidados
 */
public class Environment {

  private final Map<String, Object> variables = new HashMap<>();
  private final Set<String> constants = new HashSet<>();
  private final Map<String, String> types = new HashMap<>();
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

  public void defineConst(String name, Object value) {
    variables.put(name, freezeValue(value));
    constants.add(name);
  }

  private Object freezeValue(Object value) {
    if (value instanceof List<?> list) {
      return list.stream().map(this::freezeValue).toList();
    }

    if (value instanceof Set<?> set) {
      Set<Object> frozen = new LinkedHashSet<>();
      for (Object element : set)
        frozen.add(freezeValue(element));
      return Set.copyOf(frozen);
    }

    if (value instanceof Map<?, ?> map) {
      Map<String, Object> frozen = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet())
        frozen.put(String.valueOf(entry.getKey()), freezeValue(entry.getValue()));
      return Map.copyOf(frozen);
    }

    if (value instanceof Interpreter.StructInstance si) {
      Map<String, Object> frozenFields = new LinkedHashMap<>();
      for (Map.Entry<String, Object> entry : si.fields().entrySet())
        frozenFields.put(entry.getKey(), freezeValue(entry.getValue()));

      return new Interpreter.StructInstance(si.typeName(), Map.copyOf(frozenFields), true);
    }

    return value;
  }

  public Object get(String name) {
    if (variables.containsKey(name))
      return variables.get(name);
    if (parent != null)
      return parent.get(name);
    throw new RuntimeException("Variable no definida: '" + name + "'");
  }

  public void set(String name, Object value) {
    if (constants.contains(name))
      throw new RuntimeException("No se puede reasignar la constante '" + name + "'");

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

  public void defineType(String name, String type) {
    types.put(name, type);
  }

  public String getType(String name) {
    if (types.containsKey(name))
      return types.get(name);
    if (parent != null)
      return parent.getType(name);
    return null; // any implícito
  }
}
