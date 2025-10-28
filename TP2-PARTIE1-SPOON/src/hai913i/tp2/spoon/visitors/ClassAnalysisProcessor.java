package hai913i.tp2.spoon.visitors;

import hai913i.tp2.spoon.model.AttributeInfo;
import hai913i.tp2.spoon.model.ClassInfo;
import hai913i.tp2.spoon.model.MethodCallInfo;
import hai913i.tp2.spoon.model.MethodInfo;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

@SuppressWarnings({"rawtypes"})
public class ClassAnalysisProcessor extends AbstractProcessor<CtClass> {

    @Override
    public boolean isToBeProcessed(CtClass candidate) {
        return candidate != null
                && !candidate.isImplicit()
                && candidate.getSimpleName() != null
                && !candidate.getSimpleName().isEmpty();
    }

    @Override
    public void process(CtClass ctClass) {
        ClassInfo ci = new ClassInfo();                 // modèle inchangé :contentReference[oaicite:0]{index=0}
        ci.className = ctClass.getSimpleName();

        CtPackage pkg = ctClass.getPackage();
        ci.packageName = (pkg != null && pkg.getQualifiedName() != null)
                ? pkg.getQualifiedName()
                : "(default package)";

        CtTypeReference sc = ctClass.getSuperclass();
        if (sc != null) {
            ci.superClassNames.add(simpleName(sc));
        }
        for (Object itfObj : ctClass.getSuperInterfaces()) {
            CtTypeReference itf = (CtTypeReference) itfObj;
            ci.superClassNames.add(simpleName(itf));
        }

        for (Object fObj : ctClass.getFields()) {
            CtField f = (CtField) fObj;
            AttributeInfo ai = new AttributeInfo();      // :contentReference[oaicite:1]{index=1}
            ai.name = f.getSimpleName();
            ai.visibility = visibilityOf(f.getVisibility());
            ci.attributes.add(ai);
        }

        for (Object mObj : ctClass.getMethods()) {
            CtMethod m = (CtMethod) mObj;

            MethodInfo mi = new MethodInfo();            // :contentReference[oaicite:2]{index=2}
            mi.name = m.getSimpleName();
            mi.paramsCount = (m.getParameters() != null) ? m.getParameters().size() : 0;
            mi.lineCount = computeLocFromBody(m);

            CtBlock body = m.getBody();
            if (body != null) {
                for (CtInvocation inv : (List<CtInvocation>) body.getElements(new TypeFilter<>(CtInvocation.class))) {
                    MethodCallInfo call = new MethodCallInfo();  // :contentReference[oaicite:3]{index=3}
                    CtExecutableReference exec = inv.getExecutable();
                    call.methodName = (exec != null && exec.getSimpleName() != null)
                            ? exec.getSimpleName()
                            : "<unknown>";

                    String receiverSimple = inferReceiverSimpleType(inv, exec, ctClass.getSimpleName());
                    call.receiverType = receiverSimple;
                    call.externalType = !receiverSimple.equals(ctClass.getSimpleName());

                    mi.methodCalls.add(call);            // *** dans MethodInfo, pas dans ClassInfo ***
                }
            }

            ci.methods.add(mi);
        }

        AnalysisRepository.add(ci); // <-- pousse la classe analysée dans le repo global
    }

    private static int computeLocFromBody(CtMethod m) {
        CtBlock body = m.getBody();
        if (body == null) return 0;
        SourcePosition p = body.getPosition();
        if (p == null || !p.isValidPosition()) return 0;
        return Math.max(0, p.getEndLine() - p.getLine() + 1);
    }

    private static String visibilityOf(ModifierKind mk) {
        if (mk == null) return "package-private";
        return switch (mk) {
            case PUBLIC -> "public";
            case PROTECTED -> "protected";
            case PRIVATE -> "private";
            default -> "package-private";
        };
    }

    private static String simpleName(CtTypeReference tr) {
        if (tr == null) return "";
        String qn = tr.getQualifiedName();
        if (qn == null || qn.isEmpty()) return tr.getSimpleName();
        int idx = qn.lastIndexOf('.');
        return (idx >= 0) ? qn.substring(idx + 1) : qn;
    }

    private static String inferReceiverSimpleType(CtInvocation inv,
                                                  CtExecutableReference exec,
                                                  String currentClassSimple) {
        CtTypeReference decl = (exec != null) ? exec.getDeclaringType() : null;
        if (decl != null && decl.getSimpleName() != null && !decl.getSimpleName().isEmpty()) {
            return decl.getSimpleName();
        }
        CtExpression target = inv.getTarget();
        if (target != null) {
            CtTypeReference tr = target.getType();
            if (tr != null && tr.getSimpleName() != null && !tr.getSimpleName().isEmpty()) {
                return tr.getSimpleName();
            }
        }
        return currentClassSimple;
    }
}

