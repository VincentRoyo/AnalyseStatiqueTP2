package org.example.visitor;

import org.eclipse.jdt.core.dom.*;
import org.example.visitor.model.MethodCallInfo;
import org.example.visitor.model.MethodInfo;

public class MethodVisitor extends ASTVisitor {

    private final MethodInfo methodInfo;
    private final CompilationUnit cu;
    private final StaticImportIndex staticIndex;

    public MethodVisitor(MethodInfo methodInfo) {
        this(methodInfo, null, null);
    }

    public MethodVisitor(MethodInfo methodInfo, CompilationUnit cu, StaticImportIndex index) {
        this.methodInfo = methodInfo;
        this.cu = cu;
        this.staticIndex = index;
    }


    @Override
    public boolean visit(MethodInvocation node) {
        MethodCallInfo call = new MethodCallInfo();
        call.methodName = node.getName().toString();

        IMethodBinding mb = node.resolveMethodBinding();
        Expression expr = node.getExpression();

        // Classe englobante (pour comparer interne/externe)
        ITypeBinding enclosing = getEnclosingTypeBinding(node);
        String enclosingSimple = simpleTypeName(enclosing);

        String receiver;

        if (expr != null) {
            // --- cas qualifié: obj.m()
            ITypeBinding tb = expr.resolveTypeBinding();
            if (tb != null) {
                receiver = simpleTypeName(tb);
            } else if (mb != null && mb.getDeclaringClass() != null) {
                receiver = simpleTypeName(mb.getDeclaringClass());
            } else {
                receiver = expr.toString();
            }
        } else {
            // --- cas non qualifié: m()
            // 1) binding dispo ? (résout aussi super.m(), ou m() dans autre type si classpath OK)
            if (mb != null && mb.getDeclaringClass() != null) {
                receiver = simpleTypeName(mb.getDeclaringClass());
            } else {
                // 2) import static explicite: import static a.b.C.m;
                String ownerFqn = (staticIndex != null) ? staticIndex.ownerFqnForMethod(call.methodName) : null;
                if (ownerFqn != null) {
                    receiver = StaticImportIndex.simpleNameFromFqn(ownerFqn);
                } else {
                    // 3) import static a.b.C.* (unique) → on attribue à C
                    String starFqn = (staticIndex != null) ? staticIndex.uniqueStarOwnerFqnOrNull() : null;
                    if (starFqn != null) {
                        receiver = StaticImportIndex.simpleNameFromFqn(starFqn);
                    } else {
                        // 4) fallback: classe englobante (comportement historique)
                        receiver = (enclosingSimple != null) ? enclosingSimple : "<?>";
                    }
                }
            }
        }

        call.receiverType = receiver;

        // externalType = vrai si la classe destinataire != classe englobante
        call.externalType = receiver != null && enclosingSimple != null && !receiver.equals(enclosingSimple);

        methodInfo.methodCalls.add(call);
        return super.visit(node);
    }

    // --- (reprise de tes utilitaires, inchangés) ---


    private static ITypeBinding getEnclosingTypeBinding(ASTNode node) {
        for (ASTNode n = node; n != null; n = n.getParent()) {
            if (n instanceof TypeDeclaration td) {
                ITypeBinding b = td.resolveBinding();
                if (b != null) return b;
            } else if (n instanceof AnonymousClassDeclaration acd) {
                ITypeBinding b = acd.resolveBinding();
                if (b != null) return b;
            } else if (n instanceof EnumDeclaration ed) {
                ITypeBinding b = ed.resolveBinding();
                if (b != null) return b;
            }
        }
        return null;
    }

    private static String simpleTypeName(ITypeBinding tb) {
        if (tb == null) return "<?>";

        if (tb.isArray()) {
            return simpleTypeName(tb.getElementType()) + "[]";
        }
        if (tb.isPrimitive() || tb.isNullType()) {
            return tb.getName();
        }
        if (tb.isAnonymous()) {
            ITypeBinding sup = tb.getSuperclass();
            if (sup != null && !"java.lang.Object".equals(sup.getQualifiedName())) {
                return simpleTypeName(sup);
            }
            ITypeBinding[] ifaces = tb.getInterfaces();
            if (ifaces != null && ifaces.length > 0) {
                return simpleTypeName(ifaces[0]);
            }
            return "Anonymous";
        }
        if (tb.isParameterizedType() || tb.isCapture() || tb.isWildcardType()) {
            ITypeBinding erasure = tb.getErasure();
            return erasure != null ? erasure.getName() : tb.getName();
        }
        String name = tb.getName();
        return (name == null || name.isEmpty()) ? "<?>": name;
    }
}
