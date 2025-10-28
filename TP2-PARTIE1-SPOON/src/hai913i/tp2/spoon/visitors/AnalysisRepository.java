package hai913i.tp2.spoon.visitors;

import hai913i.tp2.spoon.model.ClassInfo;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

    public final class AnalysisRepository {
    private static final CopyOnWriteArrayList<ClassInfo> CLASSES = new CopyOnWriteArrayList<>();

    private AnalysisRepository() {}

    public static void clear() { CLASSES.clear(); }

    public static void add(ClassInfo ci) { if (ci != null) CLASSES.add(ci); }

    public static List<ClassInfo> getAll() { return Collections.unmodifiableList(CLASSES); }
}
