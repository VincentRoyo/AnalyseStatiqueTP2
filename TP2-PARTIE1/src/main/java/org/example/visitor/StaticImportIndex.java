package org.example.visitor;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;

import java.util.*;


final class StaticImportIndex {
    // "m" -> "a.b.C" pour import static a.b.C.m;
    private final Map<String, String> exactMethodOwnerFqn = new HashMap<>();
    // "a.b.C" pour import static a.b.C.*;
    private final List<String> starTypeFqns = new ArrayList<>();

    StaticImportIndex(CompilationUnit cu) {
        if (cu == null) return;
        @SuppressWarnings("unchecked")
        List<ImportDeclaration> imports = cu.imports();
        if (imports == null) return;

        for (ImportDeclaration id : imports) {
            if (!id.isStatic()) continue;

            String qn = id.getName().getFullyQualifiedName(); // ex: a.b.C.m  ou  a.b.C
            if (id.isOnDemand()) {
                // import static a.b.C.*;
                starTypeFqns.add(qn);
            } else {
                // import static a.b.C.m;
                int lastDot = qn.lastIndexOf('.');
                if (lastDot > 0 && lastDot < qn.length() - 1) {
                    String owner = qn.substring(0, lastDot);   // a.b.C
                    String method = qn.substring(lastDot + 1); // m
                    exactMethodOwnerFqn.put(method, owner);
                }
            }
        }
    }

    /** Retourne le FQN du type déclarant si on a un import statique explicite. */
    String ownerFqnForMethod(String simpleMethodName) {
        return exactMethodOwnerFqn.get(simpleMethodName);
    }

    /** Retourne le FQN unique si un seul import static * est présent (sinon null). */
    String uniqueStarOwnerFqnOrNull() {
        return starTypeFqns.size() == 1 ? starTypeFqns.get(0) : null;
    }

    static String simpleNameFromFqn(String fqn) {
        if (fqn == null) return null;
        int i = fqn.lastIndexOf('.');
        return (i >= 0 && i < fqn.length() - 1) ? fqn.substring(i + 1) : fqn;
    }
}

