package org.example;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.example.analyse.ProjectParser;
import org.example.gui.MainWindow;
import org.example.visitor.ClassVisitor;
import org.example.visitor.model.ClassInfo;

import javax.swing.*;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {
        //String src = "/home/e20210003242/Bureau/targetAST/src/main/java";
        //String src = "/home/e20210003242/Bureau/targetASTMinify/src/main/java";
        String src = "/home/royo/Bureau/targetASTMinify/src/main/java";

        DecimalFormat df = new DecimalFormat("#0.00000");

        ProjectParser parser = new ProjectParser(src);
        List<CompilationUnit> units = parser.parseProject();

        for (CompilationUnit cu : units) {
            ClassVisitor visitor = new ClassVisitor(cu);
            cu.accept(visitor);
        }

        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow("Mon Application Swing");

            List<ClassInfo> classes = ClassVisitor.getClasses();

            window.setClassChoices(
                    classes.stream().map(ci -> ci.className).sorted().toList()
            );

            HashSet<UnorderedPair<String>> couplesAlreadyCalculated = new HashSet<>();
            long tempResult = 0;

            for (ClassInfo classInfoA : classes) {
                for (ClassInfo classInfoB : classes) {
                    if (!classInfoA.className.equals(classInfoB.className)) {
                        if (couplesAlreadyCalculated.contains(new UnorderedPair<>(classInfoA.className, classInfoB.className))) continue;
                        couplesAlreadyCalculated.add(new UnorderedPair<>(classInfoA.className, classInfoB.className));
                        long nbCallAToB =
                                classInfoA.methods.stream()
                                        .mapToLong(m -> m.methodCalls.stream()
                                                .filter(mc -> mc.receiverType.equals(classInfoB.className))
                                                .count()
                                        ).sum();

                        long nbCallBToA =  classInfoB.methods.stream()
                                .mapToLong(m -> m.methodCalls.stream()
                                        .filter(mc -> mc.receiverType.equals(classInfoA.className))
                                        .count()
                                ).sum();

                        tempResult += nbCallAToB + nbCallBToA;
                    }
                }
            }

            final long totCallAppBetweenBinaryClasses = tempResult;

            window.setOnCalculate((a, b) -> {

                Optional<ClassInfo> ca = classes.stream().filter(c -> c.className.equalsIgnoreCase(a)).findFirst();
                Optional<ClassInfo> cb = classes.stream().filter(c -> c.className.equalsIgnoreCase(b)).findFirst();

                if (ca.isPresent() && cb.isPresent()) {

                    long nbCallAToB =
                            ca.get().methods.stream()
                                    .mapToLong(m -> m.methodCalls.stream()
                                            .filter(mc -> mc.receiverType.equals(b))
                                            .count()
                                    ).sum();

                    long nbCallBToA =  cb.get().methods.stream()
                            .mapToLong(m -> m.methodCalls.stream()
                                    .filter(mc -> mc.receiverType.equals(a))
                                    .count()
                            ).sum();

                    double couplage =  (double) (nbCallAToB + nbCallBToA) / totCallAppBetweenBinaryClasses;

                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("Nombre de call classe ").append(a).append(" -> ").append(b).append(" : ").append(nbCallAToB).append("\n");
                    stringBuilder.append("Nombre de call classe ").append(b).append(" -> ").append(a).append(" : ").append(nbCallBToA).append("\n");
                    stringBuilder.append("Nombre de call tot app entre deux classes : ").append(totCallAppBetweenBinaryClasses).append("\n");
                    stringBuilder.append("Couplage entre ").append(a).append(" et ").append(b).append(" => ").append(df.format(couplage)).append("\n");

                    window.showResult(stringBuilder.toString());
                } else {
                    window.showResult("Class not found : " + (ca.isPresent() ? b : a));
                }
            });


            Map<String, Map<String, Double>> dataForGraph = new HashMap<>();
            Map<UnorderedPair<String>, Double> couplesCalculated = new HashMap<>();
            couplesAlreadyCalculated.clear();

            for (ClassInfo classInfoA : classes) {
                dataForGraph.put(classInfoA.className, new HashMap<>());
                for (ClassInfo classInfoB : classes) {
                    if (!classInfoA.className.equals(classInfoB.className)) {
                        if (couplesAlreadyCalculated.contains(new UnorderedPair<>(classInfoA.className, classInfoB.className))) continue;
                        couplesAlreadyCalculated.add(new UnorderedPair<>(classInfoA.className, classInfoB.className));


                        long nbCallAToB =
                                classInfoA.methods.stream()
                                        .mapToLong(m -> m.methodCalls.stream()
                                                .filter(mc -> mc.receiverType.equals(classInfoB.className))
                                                .count()
                                        ).sum();

                        long nbCallBToA =
                                classInfoB.methods.stream()
                                        .mapToLong(m -> m.methodCalls.stream()
                                                .filter(mc -> mc.receiverType.equals(classInfoA.className))
                                                .count()
                                        ).sum();

                        dataForGraph.get(classInfoA.className).put(classInfoB.className,(double) (nbCallAToB + nbCallBToA) / totCallAppBetweenBinaryClasses);
                        couplesCalculated.put(new UnorderedPair<>(classInfoA.className, classInfoB.className), (double) (nbCallAToB + nbCallBToA) / totCallAppBetweenBinaryClasses);
                    }
                }
            }

            ClusteringResult res = clusterWithDendrogram(couplesCalculated);

            window.setClusters(res.clusters);
            window.showClusters(true);

            List<MainWindow.MergeStep> steps = new java.util.ArrayList<>();
            for (MergeStep m : res.merges) {
                steps.add(new MainWindow.MergeStep(m.left, m.right, m.score));
            }

            window.setDendrogram(steps);
            window.setWeightedCoupling(dataForGraph, true);
            window.setVisible(true);
        });
    }

    public static ClusteringResult clusterWithDendrogram(Map<UnorderedPair<String>, Double> couples) {
        if (couples == null || couples.isEmpty()) {
            return new ClusteringResult(List.of(), List.of());
        }

        // 1) classes -> clusters initiaux (singletons)
        Set<String> all = new TreeSet<>(); // trié pour stabilité
        for (var p : couples.keySet()) { all.add(p.a); all.add(p.b); }

        List<Set<String>> clusters = new ArrayList<>();
        for (String s : all) clusters.add(new LinkedHashSet<>(List.of(s)));

        List<MergeStep> merges = new ArrayList<>();

        // 2) boucle de fusion : toujours jusqu’à 1 cluster (clustering hiérarchique complet)
        while (clusters.size() > 1) {
            int bi = -1, bj = -1;
            double best = Double.NEGATIVE_INFINITY;

            // recherche du meilleur couple (average-link)
            for (int i = 0; i < clusters.size(); i++) {
                for (int j = i + 1; j < clusters.size(); j++) {
                    double s = avgCoupling(clusters.get(i), clusters.get(j), couples);
                    if (s > best) {
                        best = s; bi = i; bj = j;
                    } else if (s == best && bi >= 0) {
                        // tie-break : préférer la paire avec (taille totale plus grande, puis ordre lexicographique)
                        Set<String> li = clusters.get(i), lj = clusters.get(j);
                        Set<String> lbi = clusters.get(bi), lbj = clusters.get(bj);
                        int sizeCurrent = li.size() + lj.size();
                        int sizeBest    = lbi.size() + lbj.size();
                        if (sizeCurrent > sizeBest) {
                            bi = i; bj = j;
                        } else if (sizeCurrent == sizeBest) {
                            String keyCurrent = String.join(",", new TreeSet<>(li)) + "|" + String.join(",", new TreeSet<>(lj));
                            String keyBest    = String.join(",", new TreeSet<>(lbi)) + "|" + String.join(",", new TreeSet<>(lbj));
                            if (keyCurrent.compareTo(keyBest) < 0) { bi = i; bj = j; }
                        }
                    }
                }
            }

            // sécurité (ne devrait pas arriver si clusters.size()>1)
            if (bi < 0) break;

            // enregistre l’étape pour le dendrogramme
            Set<String> left  = new LinkedHashSet<>(clusters.get(bi));
            Set<String> right = new LinkedHashSet<>(clusters.get(bj));
            merges.add(new MergeStep(left, right, best));

            // fusionne bi et bj
            Set<String> merged = new LinkedHashSet<>(left);
            merged.addAll(right);
            clusters.set(bi, merged);
            clusters.remove(bj);
        }

        // tri final lisible
        clusters.sort((c1, c2) -> {
            int bySize = Integer.compare(c2.size(), c1.size());
            if (bySize != 0) return bySize;
            return String.join(",", new TreeSet<>(c1)).compareTo(String.join(",", new TreeSet<>(c2)));
        });

        return new ClusteringResult(clusters, merges);
    }


    /** Couplage moyen entre deux clusters, paires absentes traitées comme 0. */
    private static double avgCoupling(Set<String> A, Set<String> B, Map<UnorderedPair<String>, Double> couples) {
        double sum = 0.0; int cnt = 0;
        for (String a : A)
            for (String b : B) {
                sum += couples.getOrDefault(new UnorderedPair<>(a, b), 0.0);
                cnt++;
            }
        return (cnt == 0) ? 0.0 : (sum / cnt);
    }

    public static final class UnorderedPair<T> {
        public final T a;
        public final T b;

        public UnorderedPair(T a, T b) {
            this.a = a;
            this.b = b;
        }

        public boolean contain(T a) {
            return a.equals(this.a) || a.equals(this.b);
        }

        public T getOther(T o) {
            return a.equals(o) ? b : a;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UnorderedPair<?> other)) return false;
            // symétrique : (A,B) == (B,A)
            return (Objects.equals(a, other.a) && Objects.equals(b, other.b)) ||
                    (Objects.equals(a, other.b) && Objects.equals(b, other.a));
        }

        @Override
        public int hashCode() {
            // ordre indifférent : somme + XOR
            return Objects.hashCode(a) ^ Objects.hashCode(b);
        }

        @Override
        public String toString() {
            return "(" + a + ", " + b + ")";
        }
    }

    /**
     * @param score couplage moyen au moment de la fusion
     */
    public record MergeStep(Set<String> left, Set<String> right, double score) {
    }

    /**
     * @param clusters partition finale
     * @param merges   steps ordonnés
     */
    public record ClusteringResult(List<Set<String>> clusters, List<MergeStep> merges) {
    }

}