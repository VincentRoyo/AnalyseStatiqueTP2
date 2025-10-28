package hai913i.main;

import java.util.*;


public final class ModuleIdentifier {

    public record Module(Set<String> classes, double avgCoupling) {}

    // --- CONFIG ---
    private static final boolean isSimilarity = true; // si score = distance, passez à false

    // ===== API =====
    public static java.util.List<Module> identifyModules(
            CodeGenerationProcessorMain.ClusteringResult clustering,
            Map<CodeGenerationProcessorMain.UnorderedPair<String>, Double> couples,
            double cp
    ) {
        if (clustering == null || clustering.merges().isEmpty()) return List.of();

        Node root = buildTree(clustering);
        int M = root.leaves.size();
        int maxModules = Math.max(1, M / 2);

        // 1) Candidats = sous-arbres maximaux au-dessus du cut CP
        java.util.List<Node> candidates = new ArrayList<>();
        collectByCut(root, cp, candidates);

        // 2) Filtre "moyenne intra > CP" (toutes paires comptées, paires absentes = 0)
        java.util.List<Module> modules = new ArrayList<>();
        for (Node n : candidates) {
            double avg = avgIntra(n.leaves, couples);
            if (avg > cp) {
                modules.add(new Module(n.leaves, avg));
            }
        }

        if (modules.isEmpty()) return List.of();

        // 3) ≤ M/2 : garder les meilleurs
        modules.sort((a, b) -> {
            int c = Double.compare(b.avgCoupling, a.avgCoupling);
            if (c != 0) return c;
            c = Integer.compare(b.classes.size(), a.classes.size());
            if (c != 0) return c;
            return String.join(",", new TreeSet<>(a.classes))
                    .compareTo(String.join(",", new TreeSet<>(b.classes)));
        });

        if (modules.size() > maxModules) {
            modules = modules.subList(0, maxModules);
        }
        return modules;
    }

    // ===== Arbre du dendrogramme =====
    private static final class Node {
        final Set<String> leaves;
        final double height;   // score du merge (feuille: 0)
        final Node left, right;
        Node(Set<String> leaves, double height, Node left, Node right) {
            this.leaves = leaves; this.height = height; this.left = left; this.right = right;
        }
        boolean isLeaf() { return left == null && right == null; }
    }

    private static Node buildTree(CodeGenerationProcessorMain.ClusteringResult res) {
        var merges = res.merges();

        // Collecter toutes les feuilles mentionnées
        Set<String> allLeaves = new LinkedHashSet<>();
        for (var m : merges) { allLeaves.addAll(m.left()); allLeaves.addAll(m.right()); }

        Map<Set<String>, Node> nodes = new HashMap<>();
        for (String s : allLeaves) {
            nodes.put(Set.of(s), new Node(Set.of(s), 0.0, null, null));
        }

        for (var m : merges) {
            Node L = nodes.get(m.left());
            Node R = nodes.get(m.right());
            if (L == null || R == null) {
                L = (L != null) ? L : findBySet(nodes, m.left());
                R = (R != null) ? R : findBySet(nodes, m.right());
            }
            Set<String> mergedLeaves = new LinkedHashSet<>(m.left());
            mergedLeaves.addAll(m.right());
            Node parent = new Node(mergedLeaves, m.score(), L, R);
            nodes.put(mergedLeaves, parent);
        }

        return nodes.values().stream()
                .max(Comparator.comparingInt(n -> n.leaves.size()))
                .orElseThrow();
    }

    private static Node findBySet(Map<Set<String>, Node> nodes, Set<String> target) {
        for (var e : nodes.entrySet()) {
            if (e.getKey().equals(target)) return e.getValue();
        }
        return null;
    }

    // ===== Coupe du dendrogramme =====
    /**
     * Ajoute les sous-arbres "maximaux" au-dessus du seuil CP.
     * - Similarité: on garde un noeud si node.height >= CP; sinon on descend.
     * - Distance:   on garde un noeud si node.height <= CP; sinon on descend.
     * On s'arrête (on n'explore pas ses enfants) dès que le noeud est gardé (maximalité).
     */
    private static void collectByCut(Node n, double cp, java.util.List<Node> out) {
        if (n == null) return;
        if (n.isLeaf()) return; // singletons: pas utiles pour des modules > 1 classe

        boolean pass = isSimilarity ? (n.height >= cp) : (n.height <= cp);
        if (pass) {
            out.add(n);
            return; // maximal: ne pas descendre
        }
        // sinon, on coupe ici et on descend (ne jamais agréger à travers ce seuil)
        collectByCut(n.left,  cp, out);
        collectByCut(n.right, cp, out);
    }

    // ===== Moyenne intra (toutes paires, paires absentes = 0) =====
    private static double avgIntra(Set<String> S,
                                   Map<CodeGenerationProcessorMain.UnorderedPair<String>, Double> couples) {
        int n = S.size();
        if (n < 2) return 0.0;

        double sum = 0.0;
        int cnt = 0;

        java.util.List<String> list = new ArrayList<>(S);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                var key = new CodeGenerationProcessorMain.UnorderedPair<>(list.get(i), list.get(j));
                Double w = couples.get(key);
                sum += (w != null ? w : 0.0); // paire absente = 0
                cnt++;
            }
        }
        return sum / cnt;
    }
}
