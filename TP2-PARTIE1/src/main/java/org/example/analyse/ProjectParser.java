package org.example.analyse;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProjectParser {
    private final String sourceDir;

    public ProjectParser(String sourceDir) {
        this.sourceDir = sourceDir;
    }

    public List<CompilationUnit> parseProject() throws IOException {
        List<CompilationUnit> units = new ArrayList<>();

        try (var stream = Files.walk(Paths.get(sourceDir))) {
            stream.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    String code = Files.readString(path);

                    Map<String, String> options = JavaCore.getOptions();
                    JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);

                    ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
                    parser.setKind(ASTParser.K_COMPILATION_UNIT);
                    parser.setSource(code.toCharArray());
                    parser.setCompilerOptions(options);

                    parser.setResolveBindings(true);
                    parser.setBindingsRecovery(true);
                    parser.setEnvironment(null, null, null, true);
                    parser.setUnitName(path.toString());

                    CompilationUnit cu = (CompilationUnit) parser.createAST(null);
                    units.add(cu);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        return units;
    }
}
