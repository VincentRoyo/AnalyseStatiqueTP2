package hai913i.tp2.spoon.parsers;

import hai913i.tp2.parsers.Parser;
import spoon.Launcher;
import spoon.processing.Processor;
import spoon.reflect.declaration.CtClass;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SpoonParser extends Parser<Launcher> {

	public SpoonParser(String projectPath) {
		super(projectPath);
	}

    public void setLauncher(String sourceOutputPath, String binaryOutputPath,
                            boolean autoImports, boolean commentsEnabled) {
        parser = new Launcher();

        // 1) Détection des racines de sources (src/, src/main/java, src/java…)
        for (String srcRoot : detectSourceRoots(getProjectPath())) {
            parser.addInputResource(srcRoot);
        }

        parser.setSourceOutputDirectory(sourceOutputPath);
        parser.setBinaryOutputDirectory(binaryOutputPath);
        parser.getEnvironment().setAutoImports(autoImports);
        parser.getEnvironment().setCommentEnabled(commentsEnabled);

        // 2) Détection du classpath (bin/, target/classes, build/classes/java/main, out/production, lib/*.jar…)
        List<String> cp = detectClassPathEntries(getProjectPath());
        if (cp.isEmpty()) {
            // Pas de classes compilées => mode tolérant
            parser.getEnvironment().setNoClasspath(true);
        } else {
            parser.getEnvironment().setNoClasspath(false);
            parser.getEnvironment().setSourceClasspath(cp.toArray(new String[0]));
        }
    }


    private static List<String> detectSourceRoots(String projectPath) {
        File root = new File(projectPath);
        List<String> roots = new ArrayList<>();
        File src = new File(root, "src");
        File srcMainJava = new File(root, "src/main/java");
        File srcJava = new File(root, "src/java");

        // Priorité: maven/gradle
        if (srcMainJava.exists()) roots.add(srcMainJava.getAbsolutePath());
        if (srcJava.exists()) roots.add(srcJava.getAbsolutePath());
        // Fallback générique: src/ (contiendra éventuellement main/java)
        if (src.exists()) roots.add(src.getAbsolutePath());

        // Si rien trouvé, on laisse vide: Spoon lèvera une erreur explicite (ou tu peux décider d’ajouter root)
        return roots;
    }

    private static List<String> detectClassPathEntries(String projectPath) {
        File root = new File(projectPath);
        List<String> cp = new ArrayList<>();

        // Patterns fréquents (multi-OS)
        String[][] candidates = new String[][]{
                {"bin"},                                // projets Eclipse/“classiques”
                {"out", "production"},                  // IntelliJ
                {"target", "classes"},                  // Maven
                {"build", "classes", "java", "main"},   // Gradle
                {"build", "classes"},                   // autre
        };

        for (String[] parts : candidates) {
            File f = path(root, parts);
            if (f.exists()) cp.add(f.getAbsolutePath());
        }

        // libs locales (lib/*.jar)
        File lib = new File(root, "lib");
        if (lib.exists() && lib.isDirectory()) {
            File[] jars = lib.listFiles(x -> x.isFile() && x.getName().endsWith(".jar"));
            if (jars != null) for (File j : jars) cp.add(j.getAbsolutePath());
        }

        return cp;
    }

    private static File path(File base, String... parts) {
        File f = base;
        for (String p : parts) f = new File(f, p);
        return f;
    }
	
	public void configure() {
		setLauncher(projectPath+"/spooned/src/", projectPath+"/spooned/bin/", true, true);
	}
	
	public void addProcessor(Processor<CtClass> processor) {
		parser.addProcessor(processor);
	}
	
	public void run() {
		parser.run();
	}
}
