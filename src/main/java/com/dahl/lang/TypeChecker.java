package com.dahl.lang;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class TypeChecker {

    // Resuelve el tipo de un valor en tiempo de ejecución
    public static String typeOf(Object value) {
        if (value == null)       return "any";
        if (value instanceof Double d) {
            // Distinguir int de float
            if (d == Math.floor(d) && !Double.isInfinite(d)) return "int";
            return "float";
        }
        if (value instanceof String)  return "str";
        if (value instanceof Boolean) return "bool";
        if (value instanceof List)    return "array";
        if (value instanceof Map)     return "map";
        if (value instanceof Set)     return "set";
        return "any";
    }

    // Verifica si un valor es compatible con un tipo declarado
    public static boolean isCompatible(String declaredType, Object value) {
        if (declaredType == null || declaredType.equals("any")) return true;
        return typeOf(value).equals(declaredType);
    }

    // Valida y lanza error con mensaje claro si hay incompatibilidad
    public static void check(String declaredType, Object value, String context, String file, int line) {
        if (!isCompatible(declaredType, value)) {
            String actual = typeOf(value);
            throw new RuntimeException(
                String.format("[%s:%d] Error de tipo en '%s': se esperaba '%s' pero se encontró '%s'",
                    file, line, context, declaredType, actual));
        }
    }
}
