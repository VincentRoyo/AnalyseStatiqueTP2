package hai913i.tp2.spoon.model;

import java.util.ArrayList;
import java.util.List;

public class ClassInfo {
    public String className;
    public String packageName;
    public List<String> superClassNames = new ArrayList<>();
    public List<AttributeInfo> attributes = new ArrayList<>();
    public List<MethodInfo> methods = new ArrayList<>();

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("Classe : ").append(className).append("\n");

        // Supers
        if (!superClassNames.isEmpty()) {
            sb.append("  Hérite/Implémente :\n");
            for (String sup : superClassNames) {
                sb.append("    - ").append(sup).append("\n");
            }
        } else {
            sb.append("  Hérite/Implémente : (aucun)\n");
        }

        // Attributs
        if (!attributes.isEmpty()) {
            sb.append("  Attributs :\n");
            for (AttributeInfo attr : attributes) {
                sb.append("    - ")
                        .append(attr.visibility).append(" ")
                        .append(attr.name).append("\n");
            }
        } else {
            sb.append("  Attributs : (aucun)\n");
        }

        // Méthodes
        if (!methods.isEmpty()) {
            sb.append("  Méthodes :\n");
            for (MethodInfo m : methods) {
                sb.append("    - ").append(m.name).append("()\n");

                if (!m.methodCalls.isEmpty()) {
                    sb.append("      Appels :\n");
                    for (MethodCallInfo call : m.methodCalls) {
                        sb.append("        • ")
                                .append(call.methodName)
                                .append("() sur ")
                                .append(call.receiverType.substring(0, 1).toUpperCase())
                                .append(call.receiverType.substring(1))
                                .append("\n");
                    }
                } else {
                    sb.append("      Appels : (aucun)\n");
                }
            }
        } else {
            sb.append("  Méthodes : (aucune)\n");
        }

        return sb.toString();
    }


}
