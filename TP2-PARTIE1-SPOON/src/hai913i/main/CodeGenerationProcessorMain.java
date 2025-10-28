package hai913i.main;

import hai913i.tp2.gui.CouplingDashboardWindow;
import hai913i.tp2.gui.ProjectPathChooser;
import hai913i.tp2.spoon.model.ClassInfo;
import hai913i.tp2.spoon.processors.CodeGenerationProcessor;
import hai913i.tp2.spoon.visitors.AnalysisRepository;
import hai913i.tp2.spoon.visitors.ClassAnalysisProcessor;

import javax.swing.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public class CodeGenerationProcessorMain extends AbstractMain {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // 1) Sélection projet (fenêtre)
                String chosen = ProjectPathChooser.chooseProjectPath(null);
                if (chosen == null) {
                    System.out.println("Aucun projet sélectionné. Fin.");
                    return;
                }

                // 2) Exécution Spoon
                AnalysisRepository.clear();
                CodeGenerationProcessor runner = new CodeGenerationProcessor(chosen);

                runner.apply(new ClassAnalysisProcessor());

                // 3) Récup des classes
                final java.util.List<ClassInfo> classes = AnalysisRepository.getAll();

                // 4) Calculs IDENTIQUES à ton Main JDT
                DecimalFormat df = new DecimalFormat("#0.00000");

                HashSet<UnorderedPair<String>> couplesAlreadyCalculated = new HashSet<>();
                long tempResult = 0;

                for (ClassInfo classInfoA : classes) {
                    for (ClassInfo classInfoB : classes) {
                        if (!classInfoA.className.equals(classInfoB.className)) {
                            UnorderedPair<String> pair = new UnorderedPair<>(classInfoA.className, classInfoB.className);
                            if (couplesAlreadyCalculated.contains(pair)) continue;
                            couplesAlreadyCalculated.add(pair);

                            long nbCallAToB =
                                    classInfoA.methods.stream()
                                            .mapToLong(m -> m.methodCalls.stream()
                                                    .filter(mc -> mc.receiverType.equals(classInfoB.className))
                                                    .count())
                                            .sum();

                            long nbCallBToA =
                                    classInfoB.methods.stream()
                                            .mapToLong(m -> m.methodCalls.stream()
                                                    .filter(mc -> mc.receiverType.equals(classInfoA.className))
                                                    .count())
                                            .sum();

                            tempResult += nbCallAToB + nbCallBToA;
                        }
                    }
                }
                final long totCallAppBetweenBinaryClasses = tempResult;

                // 5) Matrice pondérée et couples (A,B) -> poids (mêmes formules)
                Map<String, Map<String, Double>> weighted = new HashMap<>();
                Map<UnorderedPair<String>, Double> couplesCalculated = new HashMap<>();
                couplesAlreadyCalculated.clear();

                for (ClassInfo classInfoA : classes) {
                    weighted.put(classInfoA.className, new HashMap<>());
                    for (ClassInfo classInfoB : classes) {
                        if (!classInfoA.className.equals(classInfoB.className)) {
                            UnorderedPair<String> pair = new UnorderedPair<>(classInfoA.className, classInfoB.className);
                            if (couplesAlreadyCalculated.contains(pair)) continue;
                            couplesAlreadyCalculated.add(pair);

                            long nbCallAToB =
                                    classInfoA.methods.stream()
                                            .mapToLong(m -> m.methodCalls.stream()
                                                    .filter(mc -> mc.receiverType.equals(classInfoB.className))
                                                    .count())
                                            .sum();

                            long nbCallBToA =
                                    classInfoB.methods.stream()
                                            .mapToLong(m -> m.methodCalls.stream()
                                                    .filter(mc -> mc.receiverType.equals(classInfoA.className))
                                                    .count())
                                            .sum();

                            double w = (totCallAppBetweenBinaryClasses == 0)
                                    ? 0.0
                                    : (double) (nbCallAToB + nbCallBToA) / totCallAppBetweenBinaryClasses;

                            weighted.get(classInfoA.className).put(classInfoB.className, w);
                            couplesCalculated.put(pair, w);
                        }
                    }
                }

                ClusteringResult res = clusterWithDendrogram(couplesCalculated);

                java.util.List<String> classChoices = classes.stream().map(ci -> ci.className).sorted().collect(Collectors.toList());

                CouplingDashboardWindow win = new CouplingDashboardWindow(
                        classChoices,
                        classes,
                        weighted,
                        totCallAppBetweenBinaryClasses,
                        res,
                        couplesCalculated
                );
                win.setVisible(true);

            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, e.toString(), "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    public static ClusteringResult clusterWithDendrogram(Map<UnorderedPair<String>, Double> couples) {
        if (couples == null || couples.isEmpty()) {
            return new ClusteringResult(java.util.List.of(), java.util.List.of());
        }

        // classes -> clusters initiaux
        Set<String> all = new TreeSet<>();
        for (var p : couples.keySet()) { all.add(p.a); all.add(p.b); }

        java.util.List<Set<String>> clusters = new ArrayList<>();
        for (String s : all) clusters.add(new LinkedHashSet<>(java.util.List.of(s)));

        java.util.List<MergeStep> merges = new ArrayList<>();

        while (clusters.size() > 1) {
            int bi = -1, bj = -1;
            double best = Double.NEGATIVE_INFINITY;

            for (int i = 0; i < clusters.size(); i++) {
                for (int j = i + 1; j < clusters.size(); j++) {
                    double s = avgCoupling(clusters.get(i), clusters.get(j), couples);
                    if (s > best) {
                        best = s; bi = i; bj = j;
                    } else if (s == best && bi >= 0) {
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

            if (bi < 0) break;

            Set<String> left  = new LinkedHashSet<>(clusters.get(bi));
            Set<String> right = new LinkedHashSet<>(clusters.get(bj));
            merges.add(new MergeStep(left, right, best));

            Set<String> merged = new LinkedHashSet<>(left);
            merged.addAll(right);
            clusters.set(bi, merged);
            clusters.remove(bj);
        }

        clusters.sort((c1, c2) -> {
            int bySize = Integer.compare(c2.size(), c1.size());
            if (bySize != 0) return bySize;
            return String.join(",", new TreeSet<>(c1)).compareTo(String.join(",", new TreeSet<>(c2)));
        });

        return new ClusteringResult(clusters, merges);
    }

    public record MergeStep(Set<String> left, Set<String> right, double score) {}
    public record ClusteringResult(java.util.List<Set<String>> clusters, java.util.List<MergeStep> merges) {}

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

        public UnorderedPair(T a, T b) { this.a = a; this.b = b; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UnorderedPair<?> other)) return false;
            return (Objects.equals(a, other.a) && Objects.equals(b, other.b)) ||
                    (Objects.equals(a, other.b) && Objects.equals(b, other.a));
        }
        @Override public int hashCode() { return Objects.hashCode(a) ^ Objects.hashCode(b); }
    }

}
