package org.example.visitor;

import org.eclipse.jdt.core.dom.*;
import org.example.visitor.model.AttributeInfo;
import org.example.visitor.model.ClassInfo;
import org.example.visitor.model.MethodInfo;

import java.util.ArrayList;
import java.util.List;

public class ClassVisitor extends ASTVisitor {

    private static final List<ClassInfo> classes = new ArrayList<>();
    private static ClassInfo currentClass;

    private final CompilationUnit cu;

    public ClassVisitor(CompilationUnit cu) { this.cu = cu; }

    @Override
    public boolean visit(TypeDeclaration node) {
        currentClass = new ClassInfo();
        currentClass.className = node.getName().toString();
        if (node.getSuperclassType() != null) {
            currentClass.superClassNames.add(node.getSuperclassType().toString());
        }
        if (node.superInterfaceTypes() != null) {
            for (Object o : node.superInterfaceTypes()) {
                currentClass.superClassNames.add(o.toString());
            }
        }

        PackageDeclaration pkg = cu.getPackage();
        if (pkg != null) {
            currentClass.packageName = pkg.getName().getFullyQualifiedName();
        } else {
            currentClass.packageName = "(default package)";
        }

        classes.add(currentClass);
        return true;
    }

    @Override
    public boolean visit(FieldDeclaration node) {
        for (Object frag : node.fragments()) {
            VariableDeclarationFragment var = (VariableDeclarationFragment) frag;
            AttributeInfo attr = new AttributeInfo();
            attr.name = var.getName().toString();

            int mods = node.getModifiers();
            if (Modifier.isPublic(mods)) attr.visibility = "public";
            else if (Modifier.isProtected(mods)) attr.visibility = "protected";
            else if (Modifier.isPrivate(mods)) attr.visibility = "private";
            else attr.visibility = "package-private";

            currentClass.attributes.add(attr);
        }

        return false;
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        MethodInfo method = new MethodInfo();
        method.name = node.getName().toString();

        method.paramsCount = node.parameters().size();
        Block body = node.getBody();
        if (body != null) {
            StaticImportIndex staticImports = new StaticImportIndex(cu);

            body.accept(new MethodVisitor(method, cu, staticImports));

            int start = body.getStartPosition();
            int endExclusive = start + body.getLength();
            int startLine = cu.getLineNumber(start);
            int endLine   = cu.getLineNumber(endExclusive - 1);
            method.lineCount = (startLine > 0 && endLine > 0) ? (endLine - startLine + 1) : 0;
        } else {
            method.lineCount = 0;
        }
        currentClass.methods.add(method);
        return false;
    }

    public static List<ClassInfo> getClasses() {
        return classes;
    }
}
