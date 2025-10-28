package org.example.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MainWindow extends JFrame {

    private final JTabbedPane tabs = new JTabbedPane(JTabbedPane.LEFT);

    // Champs du 1er onglet exposés au niveau de la classe
    private JComboBox<String> inputA;
    private JComboBox<String> inputB;
    private JButton calcBtn;
    private JTextArea resultArea;

    // --- Nouvel onglet Call Graph ---
    private GraphPanel graphPanel;
    private JButton fitBtn;
    private JButton zoomInBtn;
    private JButton zoomOutBtn;

    private DendrogramPanel dendroPanel;
    private JButton dendroFitBtn;
    private JButton dendroZoomInBtn;
    private JButton dendroZoomOutBtn;

    // Callback injecté depuis le main
    private BiConsumer<String, String> onCalculate;

    // --- Stockage des merges pour l’outil Modules ---
    private List<MergeStep> lastMerges = List.of();
    private Map<String, Map<String, Double>> lastWeights = Map.of(); // NEW


    public MainWindow(String title) {
        super(title);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(960, 600));
        setLocationByPlatform(true);

        setJMenuBar(buildMenuBar());
        add(buildToolbar(), BorderLayout.NORTH);

        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabs.addTab("Calcul",    buildCalcTab());
        tabs.addTab("Call Graph",buildCallGraphTab());
        tabs.addTab("Dendrogramme", buildDendrogramTab());
        add(tabs, BorderLayout.CENTER);

        pack();
    }

    /** Injecte la logique métier du bouton "Calculer". */
    public void setOnCalculate(BiConsumer<String, String> onCalculate) {
        this.onCalculate = onCalculate;
        updateCalcButtonEnabled();
    }

    /** Injecte la liste des classes (noms simples ou qualifiés, à toi de choisir). */
    public void setClassChoices(Collection<String> classNames) {
        Objects.requireNonNull(classNames, "classNames");
        DefaultComboBoxModel<String> modelA = new DefaultComboBoxModel<>();
        DefaultComboBoxModel<String> modelB = new DefaultComboBoxModel<>();
        for (String s : classNames) {
            modelA.addElement(s);
            modelB.addElement(s);
        }
        inputA.setModel(modelA);
        inputB.setModel(modelB);

        if (modelA.getSize() > 0) inputA.setSelectedIndex(0);
        if (modelB.getSize() > 0) inputB.setSelectedIndex(modelB.getSize() > 1 ? 1 : 0);

        updateCalcButtonEnabled();
    }

    public void showResult(String text) {
        resultArea.setText(text == null ? "" : text);
    }

    public void setDendrogram(java.util.List<MergeStep> merges) {
        this.lastMerges = (merges == null) ? java.util.List.of() : merges;
        if (dendroPanel != null) dendroPanel.setDendrogram(merges);
    }


    // Type simple pour pousser les fusions (si tu n’as pas déjà ClusteringResult.MergeStep)
    public static class MergeStep {
        public final java.util.Set<String> left, right;
        public final double score;
        public MergeStep(java.util.Set<String> left, java.util.Set<String> right, double score) {
            this.left = left; this.right = right; this.score = score;
        }
    }


    private static Map<String, Map<String, Double>> normalizeUndirected(
            Map<String, ? extends Map<String, ? extends Number>> wadj) {

        Map<String, Map<String, Double>> out = new LinkedHashMap<>();
        Set<String> all = new LinkedHashSet<>();

        // Collecte de toutes les clés (A et B)
        for (var e : wadj.entrySet()) {
            all.add(e.getKey());
            if (e.getValue() != null)
                all.addAll(e.getValue().keySet());
        }

        // Initialisation des lignes
        for (String a : all)
            out.put(a, new LinkedHashMap<>());

        // Normalisation symétrique (A,B) = (B,A) = somme positive
        for (String a : all) {
            // ici on cast explicitement, car getOrDefault perd le type
            Map<String, ? extends Number> row =
                    wadj.containsKey(a) ? wadj.get(a) : Collections.emptyMap();

            for (String b : all) {
                double ab = 0.0, ba = 0.0;

                Number nab = (row != null) ? row.get(b) : null;
                if (nab != null) ab = nab.doubleValue();

                Map<String, ? extends Number> rowB =
                        wadj.containsKey(b) ? wadj.get(b) : Collections.emptyMap();
                Number nba = (rowB != null) ? rowB.get(a) : null;
                if (nba != null) ba = nba.doubleValue();

                double w = Math.max(0.0, ab) + Math.max(0.0, ba);
                if (w > 0.0) {
                    out.get(a).put(b, w);
                    out.get(b).put(a, w);
                }
            }
        }

        return out;
    }


    private JComponent buildDendrogramTab() {
        JPanel root = new JPanel(new BorderLayout());
        JToolBar tb = new JToolBar(); tb.setFloatable(false);
        dendroFitBtn = new JButton("Fit");
        dendroZoomInBtn = new JButton("+");
        dendroZoomOutBtn = new JButton("–");

        JButton modulesBtn = new JButton("Modules…");
        tb.addSeparator();
        tb.add(modulesBtn);

        modulesBtn.addActionListener(e -> {
            if (lastMerges == null || lastMerges.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Aucun dendrogramme disponible. Calculez d’abord le clustering.",
                        "Modules", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (lastWeights == null || lastWeights.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Aucune matrice de couplage pair-à-pair disponible (lastWeights vide).\n" +
                                "Appelez setWeightedCoupling(...) avant.",
                        "Modules", JOptionPane.WARNING_MESSAGE);
                return;
            }
            new ModuleExplorerDialog(this, lastMerges, lastWeights).setVisible(true); // NEW
        });


        tb.add(new JLabel("Dendrogramme  "));
        tb.add(dendroFitBtn); tb.add(dendroZoomInBtn); tb.add(dendroZoomOutBtn);

        dendroPanel = new DendrogramPanel();
        dendroFitBtn.addActionListener(e -> dendroPanel.fitToView());
        dendroZoomInBtn.addActionListener(e -> dendroPanel.zoomBy(1.15));
        dendroZoomOutBtn.addActionListener(e -> dendroPanel.zoomBy(1.0/1.15));

        root.add(tb, BorderLayout.NORTH);
        root.add(dendroPanel, BorderLayout.CENTER);
        return root;
    }

    // -----------------------------------------------------------------------------
// Détection des modules par coupe horizontale du dendrogramme au seuil CP.
// - On coupe toute arête parent->enfant dont le score ne passe pas le seuil.
// - Les sous-arbres maximaux au-dessus du cut sont candidats.
// - On filtre: moyenne intra (toutes paires, absentes=0) > CP.
// - On limite à ≤ M/2 modules (M = nb de classes).
// Hypothèse par défaut: score = similarité (plus grand = plus proche).
// Si votre score est une distance (plus petit = plus proche), mettre isSimilarity=false.
// -----------------------------------------------------------------------------
    public static List<Set<String>> identifyCoupledGroups(
            List<MergeStep> merges,
            Map<String, Map<String, Double>> weights,
            double cp
    ) {
        final boolean isSimilarity = true; // ← passe à false si ton score est une distance

        if (merges == null || merges.isEmpty()) return List.of();

        // 1) Construire l’arbre (dendrogramme) en rejouant les merges
        _Node root = _buildTreeFromMerges(merges);

        // 2) Sous-arbres maximaux au-dessus du "cut" CP
        List<_Node> candidates = new ArrayList<>();
        _collectByCut(root, cp, isSimilarity, candidates);

        // 3) Filtre avgIntra > CP (toutes paires; paires absentes = 0)
        List<_ModuleRec> kept = new ArrayList<>();
        for (_Node n : candidates) {
            if (n.leaves.size() < 2) continue;
            double avg = _avgIntra(n.leaves, weights);
            if (avg > cp) kept.add(new _ModuleRec(n.leaves, avg));
        }
        if (kept.isEmpty()) return List.of();

        // 4) ≤ M/2 modules
        int M = root.leaves.size();
        int maxModules = Math.max(1, M / 2);

        kept.sort((a, b) -> {
            int c = Double.compare(b.avg, a.avg);
            if (c != 0) return c;
            c = Integer.compare(b.classes.size(), a.classes.size());
            if (c != 0) return c;
            return String.join(",", new TreeSet<>(a.classes))
                    .compareTo(String.join(",", new TreeSet<>(b.classes)));
        });
        if (kept.size() > maxModules) kept = kept.subList(0, maxModules);

        // 5) Sortie: sets de noms de classes
        List<Set<String>> out = new ArrayList<>(kept.size());
        for (_ModuleRec m : kept) out.add(m.classes);
        return out;
    }

// ======== Implémentation interne (privée à MainWindow) ========

    private static final class _ModuleRec {
        final Set<String> classes; final double avg;
        _ModuleRec(Set<String> c, double a) { classes = c; avg = a; }
    }

    private static final class _Node {
        final Set<String> leaves;
        final double height;   // score du merge qui crée ce nœud (feuille: 0)
        final _Node left, right;
        _Node(Set<String> leaves, double height, _Node left, _Node right) {
            this.leaves = leaves; this.height = height; this.left = left; this.right = right;
        }
        boolean isLeaf() { return left == null && right == null; }
    }

    private static _Node _buildTreeFromMerges(List<MergeStep> merges) {
        // feuilles observées
        Set<String> all = new LinkedHashSet<>();
        for (MergeStep m : merges) { all.addAll(m.left); all.addAll(m.right); }

        Map<Set<String>, _Node> nodes = new HashMap<>();
        for (String s : all) nodes.put(Set.of(s), new _Node(Set.of(s), 0.0, null, null));

        for (MergeStep m : merges) {
            _Node L = nodes.get(m.left);
            _Node R = nodes.get(m.right);
            if (L == null || R == null) {
                // fallback si les Set ne sont pas la même instance
                for (var e : nodes.entrySet()) {
                    if (L == null && e.getKey().equals(m.left))  L = e.getValue();
                    if (R == null && e.getKey().equals(m.right)) R = e.getValue();
                }
            }
            Set<String> merged = new LinkedHashSet<>(m.left);
            merged.addAll(m.right);
            _Node parent = new _Node(merged, m.score, L, R);
            nodes.put(merged, parent);
        }

        return nodes.values().stream()
                .max(Comparator.comparingInt(n -> n.leaves.size()))
                .orElseThrow();
    }

    private static void _collectByCut(_Node n, double cp, boolean isSimilarity, List<_Node> out) {
        if (n == null || n.isLeaf()) return;
        boolean pass = isSimilarity ? (n.height >= cp) : (n.height <= cp);
        if (pass) { out.add(n); return; }   // sous-arbre maximal au-dessus du cut
        _collectByCut(n.left,  cp, isSimilarity, out);
        _collectByCut(n.right, cp, isSimilarity, out);
    }

    private static double _avgIntra(Set<String> S, Map<String, Map<String, Double>> weights) {
        int n = S.size();
        if (n < 2) return 0.0;
        List<String> list = new ArrayList<>(S);
        double sum = 0.0; int cnt = 0;
        for (int i = 0; i < n; i++) {
            String a = list.get(i);
            for (int j = i + 1; j < n; j++) {
                String b = list.get(j);
                sum += _symWeight(a, b, weights);  // paires absentes = 0
                cnt++;
            }
        }
        return sum / cnt;
    }

    private static double _symWeight(String a, String b, Map<String, Map<String, Double>> W) {
        // somme des 2 sens (comme tu fais ailleurs). Change en max/avg si besoin.
        double ab = W.getOrDefault(a, Map.of()).getOrDefault(b, 0.0);
        double ba = W.getOrDefault(b, Map.of()).getOrDefault(a, 0.0);
        return Math.max(0.0, ab) + Math.max(0.0, ba);
    }

    /** Graphe pondéré : Map<Classe, Map<Classe, Poids>> ; poids >= 0. */
    public void setWeightedCoupling(Map<String, ? extends Map<String, ? extends Number>> weightedAdjacency,
                                    boolean undirectedCombine) {
        if (graphPanel != null) {
            graphPanel.setWeightedGraph(weightedAdjacency, undirectedCombine);
        }
        this.lastWeights = normalizeUndirected(weightedAdjacency);
    }


    public void setClusters(java.util.List<java.util.Set<String>> clusters) {
        if (graphPanel != null) graphPanel.setClusters(clusters);
    }

    public void showClusters(boolean show) {
        if (graphPanel != null) graphPanel.setShowClusters(show);
    }


    /** Optionnel : met en évidence un nœud par son nom (couleur différente). */
    public void highlightNode(String name) {
        if (graphPanel != null) {
            graphPanel.setHighlight(name);
        }
    }

    // --- UI interne (onglet "Calcul") ---
    private JComponent buildCalcTab() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel row = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(0, 0, 0, 8);
        gc.gridy = 0; gc.fill = GridBagConstraints.HORIZONTAL; gc.weighty = 0;

        inputA = new JComboBox<>();
        inputA.setEditable(false); // passe à true si tu veux saisie libre
        inputB = new JComboBox<>();
        inputB.setEditable(false);

        calcBtn = new JButton("Calculer");
        resultArea = new JTextArea(6, 20);
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);

        // A
        gc.gridx = 0; gc.weightx = 0.5;
        row.add(labeledField("Classe A", inputA), gc);

        // B
        gc.gridx = 1; gc.weightx = 0.5;
        row.add(labeledField("Classe B", inputB), gc);

        // Bouton
        gc.gridx = 2; gc.weightx = 0;
        row.add(calcBtn, gc);

        // Enter => clic "Calculer"
        getRootPane().setDefaultButton(calcBtn);

        calcBtn.addActionListener(e -> {
            if (onCalculate != null && inputA.getSelectedItem() != null && inputB.getSelectedItem() != null) {
                String a = Objects.toString(inputA.getSelectedItem(), "");
                String b = Objects.toString(inputB.getSelectedItem(), "");
                onCalculate.accept(a, b);
            }
        });

        // (Dé)activer le bouton si sélection incomplète
        inputA.addActionListener(e1 -> updateCalcButtonEnabled());
        inputB.addActionListener(e2 -> updateCalcButtonEnabled());

        root.add(row, BorderLayout.NORTH);
        root.add(new JScrollPane(resultArea,
                        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER),
                BorderLayout.CENTER);
        return root;
    }

    private void updateCalcButtonEnabled() {
        boolean ok = onCalculate != null
                && inputA != null && inputA.getSelectedItem() != null
                && inputB != null && inputB.getSelectedItem() != null;
        if (calcBtn != null) calcBtn.setEnabled(ok);
    }

    // --- UI Call Graph ---
    private JComponent buildCallGraphTab() {
        JPanel root = new JPanel(new BorderLayout());

        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        fitBtn = new JButton("Fit");
        zoomInBtn = new JButton("+");
        zoomOutBtn = new JButton("–");
        // nouveau bouton
        JToggleButton clustersBtn = new JToggleButton("Clusters"); // off par défaut

        tb.add(new JLabel("Graphe des appels  "));
        tb.add(fitBtn);
        tb.add(zoomInBtn);
        tb.add(zoomOutBtn);
        tb.addSeparator();
        tb.add(clustersBtn); // ← bouton toggle

        graphPanel = new GraphPanel();
        fitBtn.addActionListener(e -> graphPanel.fitToView());
        zoomInBtn.addActionListener(e -> graphPanel.zoomBy(1.15));
        zoomOutBtn.addActionListener(e -> graphPanel.zoomBy(1.0/1.15));

        // toggle coloration clusters
        clustersBtn.addActionListener(e -> graphPanel.setShowClusters(clustersBtn.isSelected()));

        root.add(tb, BorderLayout.NORTH);
        root.add(graphPanel, BorderLayout.CENTER);
        return root;
    }


    // --- UI interne générique ---

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("Fichier");
        JMenuItem exit = new JMenuItem("Quitter");
        exit.addActionListener(e -> dispose());
        file.add(exit);
        bar.add(file);
        return bar;
    }

    private JToolBar buildToolbar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.add(new JLabel("Prêt"));
        return tb;
    }

    private static JComponent labeledField(String label, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(4, 2));
        JLabel l = new JLabel(label);
        l.setLabelFor(field);
        p.add(l, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    private static JComponent placeholder(String text) {
        JPanel p = new JPanel(new GridBagLayout());
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.ITALIC, l.getFont().getSize() + 1f));
        l.setForeground(new Color(90, 90, 90));
        p.add(l);
        return p;
    }

    // ====================================================================================
    //                              GraphPanel (Java2D)
    // ====================================================================================
    /**
     * Panneau de rendu du graphe :
     *  - Entrée : adjacency list (Map<node, Collection<neighbors>>)
     *  - Layout : circulaire par défaut ; si DAG détecté => couches (topological-like)
     *  - Interactions : pan (drag), zoom (molette), Fit
     */
    static class GraphPanel extends JComponent {
        private Map<String, Set<String>> adj = new LinkedHashMap<>();
        private List<Node> nodes = new ArrayList<>();
        private List<Edge> edges = new ArrayList<>();

        private double zoom = 1.0;
        private double offsetX = 0.0, offsetY = 0.0; // pan
        private Point lastDrag = null;
        private boolean undirected = false; // mode combiné non orienté

        private boolean showClusters = false;
        private java.util.Map<String, Integer> clusterIndex = new java.util.HashMap<>();
        private int clusterCount = 0;


        private String highlight = null;

        private double wMin = 0.0, wMax = 1.0;  // pour normalisation du rendu
        private boolean showEdgeLabels = true;  // option : afficher les poids

        private static final int NODE_PAD_X = 12;
        private static final int NODE_PAD_Y = 7;
        private static final int LAYER_VSPACE = 90;   // espacement vertical (layout couches)
        private static final int LAYER_HSPACE = 120;  // espacement horizontal (layout couches)
        private static final int CIRCLE_RADIUS = 220; // layout cercle

        private static final int FD_MAX_ITERS_BASE = 400; // itérations de base (adaptées au nombre de nœuds)
        private static final double FD_COOLING = 0.95;    // facteur de refroidissement
        private static final double FD_NODE_MARGIN = 28;  // marge pour éviter les chevauchements de boîtes
        private static final double CURVE_BASE = 18;      // courbure de base des arêtes


        GraphPanel() {
            setOpaque(true);
            setBackground(Color.white);

            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    lastDrag = e.getPoint();
                }
                @Override public void mouseReleased(MouseEvent e) {
                    lastDrag = null;
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseDragged(MouseEvent e) {
                    if (lastDrag != null) {
                        Point p = e.getPoint();
                        offsetX += (p.x - lastDrag.x);
                        offsetY += (p.y - lastDrag.y);
                        lastDrag = p;
                        repaint();
                    }
                }
            });
            addMouseWheelListener(e -> {
                // zoom autour du pointeur
                double factor = (e.getWheelRotation() < 0) ? 1.15 : (1.0/1.15);
                zoomAround(e.getPoint(), factor);
            });

            // Fit à la première peinture si vide
            addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) {
                    // optionnel : on peut recomposer le layout si on veut réagir au resize
                    // ici on ne relayout pas automatiquement pour ne pas casser le pan de l’utilisateur
                }
            });
        }

        private static String undirectedKey(String a, String b) {
            return (a.compareTo(b) <= 0) ? (a + "§" + b) : (b + "§" + a);
        }

        void setClusters(java.util.List<java.util.Set<String>> clusters) {
            clusterIndex.clear();
            if (clusters != null) {
                int idx = 0;
                for (java.util.Set<String> set : clusters) {
                    for (String name : set) {
                        if (name != null) clusterIndex.put(name, idx);
                    }
                    idx++;
                }
                clusterCount = idx;
            } else {
                clusterCount = 0;
            }
            repaint();
        }

        /** Active/désactive l’affichage coloré des clusters. */
        void setShowClusters(boolean show) {
            this.showClusters = show;
            repaint();
        }

        /** Couleur stable par cluster (palette HSB pastel). */
        private Color clusterColor(int idx) {
            if (clusterCount <= 0) return new java.awt.Color(245,245,250);
            float h = (idx % 12) / 12.0f;  // 12 teintes avant de boucler
            float s = 0.35f;
            float b = 1.00f;
            return Color.getHSBColor(h, s, b);
        }


        void setWeightedGraph(Map<String, ? extends Map<String, ? extends Number>> wadj,
                              boolean undirectedCombine) {
            this.undirected = undirectedCombine;

            // 1) Normaliser : Map<String, Map<String, Double>> wmap
            Map<String, Map<String, Double>> wmap = new LinkedHashMap<>();
            Set<String> all = new LinkedHashSet<>();

            for (var e : wadj.entrySet()) {
                String a = Objects.toString(e.getKey(), "");
                Map<String, Double> row = new LinkedHashMap<>();
                if (e.getValue() != null) {
                    for (var f : e.getValue().entrySet()) {
                        String b = Objects.toString(f.getKey(), "");
                        double w = (f.getValue() == null) ? 0.0 : Math.max(0.0, f.getValue().doubleValue());
                        row.merge(b, w, Double::sum); // agglomère doublons éventuels
                        all.add(b);
                    }
                }
                wmap.put(a, row);
                all.add(a);
            }
            // clés pour feuilles
            for (String n : all) wmap.putIfAbsent(n, new LinkedHashMap<>());

            // 2) Construire les nodes (stables, triés pour pairs i<j en non orienté)
            nodes.clear();
            Map<String, Node> byName = new LinkedHashMap<>();
            List<String> names = new ArrayList<>(all);
            Collections.sort(names);
            for (String n : names) {
                Node node = new Node(n);
                byName.put(n, node);
                nodes.add(node);
            }

            // 3) Construire les edges + mettre à jour wMin/wMax
            edges.clear();
            wMin = Double.POSITIVE_INFINITY;
            wMax = 0.0;

            if (undirected) {
                // Une seule arête par paire (a,b) avec a<b ; poids = w(a→b)+w(b→a)
                for (int i = 0; i < names.size(); i++) {
                    String a = names.get(i);
                    for (int j = i + 1; j < names.size(); j++) {
                        String b = names.get(j);
                        double wab = wmap.getOrDefault(a, Map.of()).getOrDefault(b, 0.0);
                        double wba = wmap.getOrDefault(b, Map.of()).getOrDefault(a, 0.0);
                        double w = wab + wba;
                        if (w > 0.0) {
                            Edge e = new Edge(byName.get(a), byName.get(b));
                            e.directed = false; // non orienté => pas de flèche
                            e.weight = w;
                            edges.add(e);
                            wMin = Math.min(wMin, w);
                            wMax = Math.max(wMax, w);
                        }
                    }
                }
                // adj pour le layout : on peut ne rien relier (force-directed n'en dépend pas),
                // mais on met des sets vides pour cohérence
                adj = new LinkedHashMap<>();
                for (String n : names) adj.put(n, new LinkedHashSet<>());
            } else {
                // Dirigé : arête pour chaque (a→b) avec w>0
                adj = new LinkedHashMap<>();
                for (String a : names) {
                    Set<String> outs = new LinkedHashSet<>(wmap.get(a).keySet());
                    adj.put(a, outs);
                    for (var en : wmap.get(a).entrySet()) {
                        String b = en.getKey();
                        double w = en.getValue();
                        if (w > 0.0) {
                            Edge e = new Edge(byName.get(a), byName.get(b));
                            e.directed = true;
                            e.weight = w;
                            edges.add(e);
                            wMin = Math.min(wMin, w);
                            wMax = Math.max(wMax, w);
                        }
                    }
                }
            }

            if (!Double.isFinite(wMin)) wMin = 0.0;
            if (wMax <= wMin) wMax = wMin + 1.0;

            // 4) Layout + Fit
            doLayoutAuto();
            fitToView();
        }

        private static String fmtWeight(double w) {
            if (Double.isNaN(w)) return "NaN";
            if (Double.isInfinite(w)) return (w > 0 ? "+Inf" : "-Inf");
            // 6 chiffres significatifs, Locale.US pour le point décimal
            return String.format(java.util.Locale.US, "%.6g", w);
        }



        void setGraph(Map<String, ? extends Collection<String>> adjacency) {
            // normalise l’adjacence et sécurise les nœuds isolés
            LinkedHashMap<String, Set<String>> normalized = new LinkedHashMap<>();
            Set<String> allNodes = new LinkedHashSet<>();
            for (Map.Entry<String, ? extends Collection<String>> en : adjacency.entrySet()) {
                String from = Objects.toString(en.getKey(), "");
                Set<String> to = en.getValue() == null ? Set.of()
                        : en.getValue().stream().filter(Objects::nonNull).map(Object::toString).collect(Collectors.toCollection(LinkedHashSet::new));
                normalized.put(from, to);
                allNodes.add(from);
                allNodes.addAll(to);
            }
            // assure l’existence des clés pour les feuilles
            for (String n : allNodes) normalized.putIfAbsent(n, new LinkedHashSet<>());
            this.adj = normalized;

            buildModel();
            doLayoutAuto();
            fitToView(); // fit initial
        }

        void setHighlight(String name) {
            this.highlight = name;
            repaint();
        }

        void fitToView() {
            if (nodes.isEmpty()) return;
            Rectangle bounds = getGraphBounds();
            if (bounds.width <= 0 || bounds.height <= 0) return;

            Insets in = getInsets();
            int vw = getWidth() - in.left - in.right;
            int vh = getHeight() - in.top - in.bottom;
            if (vw <= 0 || vh <= 0) return;

            // marge
            int margin = 40;
            double sx = (vw - margin) / (double) bounds.width;
            double sy = (vh - margin) / (double) bounds.height;
            zoom = Math.max(0.1, Math.min(sx, sy));

            // centre
            double cx = bounds.getCenterX();
            double cy = bounds.getCenterY();
            double viewCx = in.left + vw / 2.0;
            double viewCy = in.top + vh / 2.0;

            // offset pour centrer après zoom
            offsetX = viewCx - cx * zoom;
            offsetY = viewCy - cy * zoom;

            repaint();
        }

        void zoomBy(double factor) {
            Point center = new Point(getWidth() / 2, getHeight() / 2);
            zoomAround(center, factor);
        }

        private void zoomAround(Point pivot, double factor) {
            factor = Math.max(0.1, Math.min(5.0, zoom * factor)) / zoom;
            // on ajuste offset pour zoomer autour du pivot (écran)
            offsetX = pivot.x - (pivot.x - offsetX) * factor;
            offsetY = pivot.y - (pivot.y - offsetY) * factor;
            zoom *= factor;
            repaint();
        }

        private void buildModel() {
            nodes.clear();
            edges.clear();
            Map<String, Node> byName = new LinkedHashMap<>();
            for (String n : adj.keySet()) {
                Node node = new Node(n);
                byName.put(n, node);
                nodes.add(node);
            }
            for (Map.Entry<String, Set<String>> en : adj.entrySet()) {
                Node from = byName.get(en.getKey());
                for (String toName : en.getValue()) {
                    Node to = byName.get(toName);
                    if (to != null) {
                        edges.add(new Edge(from, to));
                    }
                }
            }
        }

        private void doLayoutAuto() {
            if (nodes.isEmpty()) return;

            // Heuristique :
            // - si DAG et dirigé => layout en couches (lisible pour graphes d’appels)
            // - sinon => force-directed (réduit naturellement les croisements)
            boolean dag = isDag();
            boolean useLayered = dag && edges.stream().anyMatch(e -> e.directed);
            if (useLayered) {
                layoutLayers();
            } else {
                layoutForceDirected();
            }
        }

        private void layoutForceDirected() {
            int n = nodes.size();
            if (n == 0) return;

            // Aire de travail (taille composant ou fallback)
            double W = Math.max(getWidth(), 800);
            double H = Math.max(getHeight(), 600);
            double area = W * H;

            // Distance idéale entre nœuds
            double k = Math.sqrt(area / (n + 1.0));

            // Initialisation : cercle + petite gigue
            double R = Math.min(W, H) * 0.33;
            double cx = W / 2.0, cy = H / 2.0;
            Random rnd = new Random(42);
            for (int i = 0; i < n; i++) {
                double a = 2 * Math.PI * i / n;
                nodes.get(i).pos.setLocation(cx + R * Math.cos(a) + rnd.nextGaussian()*5,
                        cy + R * Math.sin(a) + rnd.nextGaussian()*5);
                nodes.get(i).vx = nodes.get(i).vy = 0;
            }

            // Adjacence pour attractions (pondérées)
            Map<Node, Map<Node, Double>> neigh = new LinkedHashMap<>();
            for (Node u : nodes) neigh.put(u, new LinkedHashMap<>());
            for (Edge e : edges) {
                double w = Math.max(0.0, e.weight);
                if (undirected || !e.directed) {
                    neigh.get(e.from).merge(e.to, w, Double::sum);
                    neigh.get(e.to).merge(e.from, w, Double::sum);
                } else {
                    // en dirigé, on reste symétrique pour la géométrie (sinon les nœuds “fuient”)
                    neigh.get(e.from).merge(e.to, w, Double::sum);
                    neigh.get(e.to).merge(e.from, w, Double::sum);
                }
            }

            // Itérations
            int iters = FD_MAX_ITERS_BASE + (int) Math.min(300, 6L * n);
            double temp = Math.max(W, H) / 5.0;

            for (int it = 0; it < iters; it++) {
                // forces de répulsion (toutes paires)
                for (int i = 0; i < n; i++) {
                    Node v = nodes.get(i);
                    double fx = 0, fy = 0;
                    for (int j = 0; j < n; j++) if (j != i) {
                        Node u = nodes.get(j);
                        double dx = v.pos.x - u.pos.x;
                        double dy = v.pos.y - u.pos.y;
                        double dist = Math.hypot(dx, dy) + 1e-6;
                        double force = (k * k) / dist; // répulsion
                        fx += (dx / dist) * force;
                        fy += (dy / dist) * force;
                    }
                    // stocke dans vx/vy temporairement
                    v.vx = fx; v.vy = fy;
                }

                // forces d’attraction (voisins)
                for (Node v : nodes) {
                    for (var en : neigh.get(v).entrySet()) {
                        Node u = en.getKey();
                        double w = 1.0 + Math.sqrt(en.getValue()); // plus le poids est grand, plus ça attire
                        double dx = v.pos.x - u.pos.x;
                        double dy = v.pos.y - u.pos.y;
                        double dist = Math.hypot(dx, dy) + 1e-6;
                        double force = (dist * dist) / k; // attraction
                        double fx = (dx / dist) * force * w * 0.5;
                        double fy = (dy / dist) * force * w * 0.5;
                        // appliquer en sens opposé pour u
                        v.vx -= fx; v.vy -= fy;
                        u.vx += fx; u.vy += fy;
                    }
                }

                // évite chevauchement des boîtes (petit push si trop proches)
                FontMetrics fm = getFontMetrics(getFont());
                for (int i = 0; i < n; i++) {
                    Node a = nodes.get(i);
                    Dimension da = measureNode(a.name, fm);
                    for (int j = i+1; j < n; j++) {
                        Node b = nodes.get(j);
                        Dimension db = measureNode(b.name, fm);
                        double dx = b.pos.x - a.pos.x;
                        double dy = b.pos.y - a.pos.y;
                        double dist = Math.hypot(dx, dy) + 1e-6;
                        double minDist = 0.5 * (Math.max(da.width, da.height) + Math.max(db.width, db.height)) + FD_NODE_MARGIN;
                        if (dist < minDist) {
                            double push = (minDist - dist) * 0.8;
                            double ux = dx / dist, uy = dy / dist;
                            a.vx -= ux * push; a.vy -= uy * push;
                            b.vx += ux * push; b.vy += uy * push;
                        }
                    }
                }

                // intégration + refroidissement
                for (Node v : nodes) {
                    double disp = Math.hypot(v.vx, v.vy);
                    if (disp > 0) {
                        double step = Math.min(disp, temp);
                        v.pos.x += (v.vx / disp) * step;
                        v.pos.y += (v.vy / disp) * step;
                    }
                }
                temp *= FD_COOLING;
            }

            // recentrage
            double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
            for (Node v : nodes) {
                minX = Math.min(minX, v.pos.x);
                minY = Math.min(minY, v.pos.y);
                maxX = Math.max(maxX, v.pos.x);
                maxY = Math.max(maxY, v.pos.y);
            }
            double gx = (minX + maxX) / 2.0;
            double gy = (minY + maxY) / 2.0;
            for (Node v : nodes) {
                v.pos.x += (W / 2.0 - gx);
                v.pos.y += (H / 2.0 - gy);
            }

            FontMetrics fm = getFontMetrics(getFont());
            resolveOverlaps(fm);
        }



        private boolean isDag() {
            Map<String, Integer> indeg = new HashMap<>();
            for (String n : adj.keySet()) indeg.put(n, 0);
            for (var en : adj.entrySet()) {
                for (String v : en.getValue()) indeg.put(v, indeg.getOrDefault(v, 0) + 1);
            }
            Deque<String> dq = new ArrayDeque<>();
            for (var en : indeg.entrySet()) if (en.getValue() == 0) dq.add(en.getKey());
            int seen = 0;
            Map<String, Set<String>> tmp = adj.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> new LinkedHashSet<>(e.getValue())));
            while (!dq.isEmpty()) {
                String u = dq.removeFirst();
                seen++;
                for (String v : new ArrayList<>(tmp.getOrDefault(u, Set.of()))) {
                    Set<String> out = tmp.get(u);
                    if (out != null) out.remove(v);
                    indeg.put(v, indeg.get(v) - 1);
                    if (indeg.get(v) == 0) dq.add(v);
                }
            }
            return seen == adj.size();
        }

        private void layoutCircle() {
            int n = nodes.size();
            if (n == 0) return;

            // rayon basé sur la taille du composant (fallback si 0)
            int w = Math.max(getWidth(), 600);
            int h = Math.max(getHeight(), 400);
            int r = Math.max(140, Math.min(w, h) / 2 - 60);

            double cx = w / 2.0;
            double cy = h / 2.0;

            for (int i = 0; i < n; i++) {
                double a = 2 * Math.PI * i / n;
                double x = cx + r * Math.cos(a);
                double y = cy + r * Math.sin(a);
                nodes.get(i).pos.setLocation(x, y);
            }
        }

        private void layoutLayers() {
            // topological-ish : calcule niveaux par BFS sur indegree==0
            Map<String, Integer> indeg = new HashMap<>();
            for (String n : adj.keySet()) indeg.put(n, 0);
            for (var en : adj.entrySet()) {
                for (String v : en.getValue()) indeg.put(v, indeg.getOrDefault(v, 0) + 1);
            }
            Deque<String> dq = new ArrayDeque<>();
            for (var en : indeg.entrySet()) if (en.getValue() == 0) dq.add(en.getKey());

            Map<String, Integer> level = new HashMap<>();
            while (!dq.isEmpty()) {
                String u = dq.removeFirst();
                int lu = level.getOrDefault(u, 0);
                for (String v : adj.getOrDefault(u, Set.of())) {
                    if (!level.containsKey(v) || level.get(v) < lu + 1) {
                        level.put(v, lu + 1);
                    }
                    indeg.put(v, indeg.get(v) - 1);
                    if (indeg.get(v) == 0) dq.add(v);
                }
                level.putIfAbsent(u, lu);
            }
            // si certains nœuds n’ont pas été atteints (isolés), on les met niveau 0
            for (String n : adj.keySet()) level.putIfAbsent(n, 0);

            // regroupe par niveau
            Map<Integer, List<Node>> perLevel = new TreeMap<>();
            Map<String, Node> byName = nodes.stream().collect(Collectors.toMap(n -> n.name, n -> n));
            for (var en : level.entrySet()) {
                perLevel.computeIfAbsent(en.getValue(), k -> new ArrayList<>()).add(byName.get(en.getKey()));
            }

            // positionnement : lignes horizontales, X réparti
            int baseY = 80;
            int y = baseY;
            int w = Math.max(getWidth(), 800);

            for (var en : perLevel.entrySet()) {
                List<Node> layer = en.getValue();
                int count = layer.size();
                int totalWidth = Math.max(1, (count - 1)) * LAYER_HSPACE;
                int startX = (w - totalWidth) / 2;

                for (int i = 0; i < count; i++) {
                    Node n = layer.get(i);
                    double x = startX + i * LAYER_HSPACE;
                    n.pos.setLocation(x, y);
                }
                y += LAYER_VSPACE;
            }
        }

        private Rectangle getGraphBounds() {
            if (nodes.isEmpty()) return new Rectangle(0,0,0,0);
            FontMetrics fm = getFontMetrics(getFont());
            Rectangle2D bounds = null;
            for (Node n : nodes) {
                Dimension d = measureNode(n.name, fm);
                Rectangle2D r = new Rectangle2D.Double(
                        n.pos.x - d.width / 2.0,
                        n.pos.y - d.height / 2.0,
                        d.width, d.height
                );
                bounds = (bounds == null) ? r : bounds.createUnion(r);
            }
            if (bounds == null) return new Rectangle(0,0,0,0);
            return bounds.getBounds();
        }

        private Dimension measureNode(String text, FontMetrics fm) {
            int tw = fm.stringWidth(text);
            int th = fm.getHeight();
            return new Dimension(tw + 2 * NODE_PAD_X, th + 2 * NODE_PAD_Y);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());

                AffineTransform oldTx = g2.getTransform();
                g2.translate(offsetX, offsetY);
                g2.scale(zoom, zoom);

                FontMetrics fm = g2.getFontMetrics();

                // dessine edges d’abord
                g2.setStroke(new BasicStroke(1.2f));
                g2.setColor(new Color(90, 90, 90));
                for (Edge e : edges) {
                    drawEdge(g2, e, fm);
                }

                // dessine nodes au-dessus
                for (Node n : nodes) {
                    boolean hl = (highlight != null && highlight.equals(n.name));
                    drawNode(g2, n, fm, hl);
                }

                g2.setTransform(oldTx);

                // overlay info
                g2.setColor(new Color(0,0,0,120));
                g2.drawString("Pan: drag | Zoom: wheel | Fit: bouton", 10, getHeight() - 10);
            } finally {
                g2.dispose();
            }
        }

        private void drawNode(Graphics2D g2, Node n, FontMetrics fm, boolean highlight) {
            Dimension d = measureNode(n.name, fm);
            double x = n.pos.x - d.width / 2.0;
            double y = n.pos.y - d.height / 2.0;
            Shape shape = new RoundRectangle(new Rectangle2D.Double(x, y, d.width, d.height), 12);

            // remplissage
            if (highlight) {
                g2.setPaint(new Color(255, 235, 180));
            } else if (showClusters) {
                Integer cid = clusterIndex.get(n.name);
                if (cid != null) {
                    g2.setPaint(clusterColor(cid));
                } else {
                    g2.setPaint(new Color(245, 245, 250));
                }
            } else {
                g2.setPaint(new Color(245, 245, 250));
            }
            g2.fill(shape);

            // contour
            g2.setColor(new Color(60, 60, 80));
            g2.setStroke(new BasicStroke(1.4f));
            g2.draw(shape);

            // texte
            g2.setColor(new Color(30, 30, 30));
            int tx = (int) (x + (d.width - fm.stringWidth(n.name)) / 2.0);
            int ty = (int) (y + (d.height - fm.getHeight()) / 2.0 + fm.getAscent());
            g2.drawString(n.name, tx, ty);
        }

        private void drawEdge(Graphics2D g2, Edge e, FontMetrics fm) {
            Dimension dFrom = measureNode(e.from.name, fm);
            Dimension dTo   = measureNode(e.to.name, fm);

            Point2D.Double p1 = new Point2D.Double(e.from.pos.x, e.from.pos.y);
            Point2D.Double p2 = new Point2D.Double(e.to.pos.x,   e.to.pos.y);

            // direction
            double dx = p2.x - p1.x, dy = p2.y - p1.y;
            double len = Math.hypot(dx, dy);
            if (len < 1e-6) return;
            double ux = dx / len, uy = dy / len;
            double ox = -uy, oy = ux; // vecteur perpendiculaire

            // recule pour toucher le bord des boîtes
            double rFrom = Math.min(dFrom.width, dFrom.height) / 2.0;
            double rTo   = Math.min(dTo.width,   dTo.height) / 2.0;
            Point2D.Double a = new Point2D.Double(p1.x + ux * rFrom, p1.y + uy * rFrom);
            Point2D.Double b = new Point2D.Double(p2.x - ux * (rTo + 10), p2.y - uy * (rTo + 10));

            // courbure per-paire pour casser les superpositions
            String key = undirectedKey(e.from.name, e.to.name);
            int hash = key.hashCode();
            double sign = (e.directed && e.from.name.compareTo(e.to.name) < 0) ? +1 : -1;
            // amplitude : base + dépend de la force du lien (poids normalisé)
            double norm = (e.weight - wMin) / (wMax - wMin + 1e-9);
            norm = Math.sqrt(Math.max(0.0, Math.min(1.0, norm)));
            double amp = CURVE_BASE * (0.6 + 0.8 * norm);
            // petit décalage stable par hash pour disperser les arêtes concurrentes
            amp *= (1.0 + ( (hash & 0xFF) - 128) / 512.0);
            // pour non orienté, signe stable par hash ; pour dirigé, signe par sens
            double s = undirected ? ((hash & 1) == 0 ? +1 : -1) : sign;

            double cx = (a.x + b.x)/2.0 + ox * amp * s;
            double cy = (a.y + b.y)/2.0 + oy * amp * s;

            // épaisseur selon le poids
            double strokeW = 0.8 + 3.2 * norm;
            Stroke old = g2.getStroke();
            g2.setStroke(new BasicStroke((float) strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(90, 90, 90));

            // courbe quadratique
            Path2D path = new Path2D.Double();
            path.moveTo(a.x, a.y);
            path.quadTo(cx, cy, b.x, b.y);
            g2.draw(path);

            // flèche seulement si dirigé : pointe orientée vers la tangente finale
            if (e.directed) {
                // tangente au point d'arrivée : vecteur (b - ctrl)
                double tx = b.x - cx, ty = b.y - cy;
                double tlen = Math.hypot(tx, ty) + 1e-6;
                tx /= tlen; ty /= tlen;
                drawArrowHead(g2, b, new Point2D.Double(tx, ty), 8 + 4*norm, 8 + 4*norm);
            }
            g2.setStroke(old);

            // étiquette
            if (showEdgeLabels && e.weight > 0) {
                String label = fmtWeight(e.weight);
                drawEdgeLabel(g2, label, (int) cx, (int) cy, fm);
            }

        }


        private void drawEdgeLabel(Graphics2D g2, String text, int x, int y, FontMetrics fm) {
            int tw = fm.stringWidth(text);
            int th = fm.getAscent();
            int pad = 3;
            int bx = x - tw/2 - pad;
            int by = y - th/2 - pad;
            int bw = tw + 2*pad;
            int bh = th + 2*pad;

            Color old = g2.getColor();
            g2.setColor(new Color(255,255,255,220));
            g2.fillRoundRect(bx, by, bw, bh, 8, 8);
            g2.setColor(new Color(60,60,60));
            g2.drawRoundRect(bx, by, bw, bh, 8, 8);
            g2.setColor(new Color(30,30,30));
            g2.drawString(text, x - tw/2, y + th/2 - 1);
            g2.setColor(old);
        }



        private void drawArrowHead(Graphics2D g2, Point2D.Double tip, Point2D.Double dir, double w, double h) {
            // dir est normalisée
            double ox = -dir.y;
            double oy =  dir.x;
            Path2D path = new Path2D.Double();
            path.moveTo(tip.x, tip.y);
            path.lineTo(tip.x - dir.x * h + ox * w / 2.0, tip.y - dir.y * h + oy * w / 2.0);
            path.lineTo(tip.x - dir.x * h - ox * w / 2.0, tip.y - dir.y * h - oy * w / 2.0);
            path.closePath();
            g2.fill(path);
        }

        // Résout les intersections de boîtes (labels compris) par petits déplacements.
        private void resolveOverlaps(FontMetrics fm) {
            final int MAX_ITERS = 250;          // augmente si graphe très dense
            final double EPS = 1e-6;
            final double STEP_CAP = 12.0;       // déplacement max par itération (évite les sauts)
            final double PAD = 6.0;             // espace cible entre boîtes (en plus de FD_NODE_MARGIN)

            for (int it = 0; it < MAX_ITERS; it++) {
                int overlaps = 0;

                for (int i = 0; i < nodes.size(); i++) {
                    Node a = nodes.get(i);
                    Rectangle2D ra = getNodeRect(a, fm, PAD);

                    for (int j = i + 1; j < nodes.size(); j++) {
                        Node b = nodes.get(j);
                        Rectangle2D rb = getNodeRect(b, fm, PAD);

                        if (!ra.intersects(rb)) continue;
                        overlaps++;

                        // vecteur de séparation (axe le plus pénétré)
                        double axc = ra.getCenterX(), ayc = ra.getCenterY();
                        double bxc = rb.getCenterX(), byc = rb.getCenterY();

                        double dx = bxc - axc;
                        double dy = byc - ayc;

                        if (Math.abs(dx) < EPS && Math.abs(dy) < EPS) {
                            // centres identiques : pousse aléa faible pour briser la symétrie
                            dx = 0.01; dy = -0.013;
                        }

                        // profondeur d'intersection sur X et Y
                        double ix = Math.min(ra.getMaxX(), rb.getMaxX()) - Math.max(ra.getMinX(), rb.getMinX());
                        double iy = Math.min(ra.getMaxY(), rb.getMaxY()) - Math.max(ra.getMinY(), rb.getMinY());

                        // on pousse selon l’axe de plus petite correction
                        double mx, my;
                        if (ix < iy) {
                            mx = (dx >= 0 ? +ix/2.0 : -ix/2.0);
                            my = 0.0;
                        } else {
                            mx = 0.0;
                            my = (dy >= 0 ? +iy/2.0 : -iy/2.0);
                        }

                        // cap le déplacement
                        mx = Math.max(-STEP_CAP, Math.min(STEP_CAP, mx));
                        my = Math.max(-STEP_CAP, Math.min(STEP_CAP, my));

                        // déplace à l’opposé chacun de moitié
                        a.pos.x -= mx; a.pos.y -= my;
                        b.pos.x += mx; b.pos.y += my;

                        // MAJ rectangles pour éviter re-comparer avec anciens bounds
                        ra = getNodeRect(a, fm, PAD);
                        rb = getNodeRect(b, fm, PAD);
                    }
                }
                if (overlaps == 0) break;
            }

            // recentre tout le nuage après les déplacements
            double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
            for (Node v : nodes) {
                minX = Math.min(minX, v.pos.x);
                minY = Math.min(minY, v.pos.y);
                maxX = Math.max(maxX, v.pos.x);
                maxY = Math.max(maxY, v.pos.y);
            }
            double gx = (minX + maxX) / 2.0;
            double gy = (minY + maxY) / 2.0;
            double W = Math.max(getWidth(), 800), H = Math.max(getHeight(), 600);
            for (Node v : nodes) {
                v.pos.x += (W / 2.0 - gx);
                v.pos.y += (H / 2.0 - gy);
            }
        }


        // --------------------------------- helpers ---------------------------------

        private static class Node {
            final String name;
            final Point2D.Double pos = new Point2D.Double();
            double vx = 0, vy = 0; // ← pour le layout force-directed
            Node(String name) { this.name = name; }
        }

        private Rectangle2D getNodeRect(Node n, FontMetrics fm, double pad) {
            Dimension d = measureNode(n.name, fm);
            double x = n.pos.x - d.width  / 2.0 - pad;
            double y = n.pos.y - d.height / 2.0 - pad;
            return new Rectangle2D.Double(x, y, d.width + 2*pad, d.height + 2*pad);
        }



        private static class Edge {
            final Node from, to;
            double weight = 0.0;
            boolean directed = true; // ← new
            Edge(Node f, Node t) { this.from = f; this.to = t; }
        }



        private static class RoundRectangle extends Path2D.Double {
            RoundRectangle(Rectangle2D r, double arc) {
                double x = r.getX(), y = r.getY(), w = r.getWidth(), h = r.getHeight(), a = arc;
                moveTo(x + a, y);
                lineTo(x + w - a, y);
                quadTo(x + w, y, x + w, y + a);
                lineTo(x + w, y + h - a);
                quadTo(x + w, y + h, x + w - a, y + h);
                lineTo(x + a, y + h);
                quadTo(x, y + h, x, y + h - a);
                lineTo(x, y + a);
                quadTo(x, y, x + a, y);
                closePath();
            }
        }
    }

    /** Fenêtre d’exploration des modules (groupes de classes couplées). */
    static class ModuleExplorerDialog extends JDialog {
        private final List<MergeStep> merges;
        private final Map<String, Map<String, Double>> weights; // NEW
        private final JTextField thresholdField = new JTextField("0.35", 10);
        private final JPanel resultPanel = new JPanel(new GridBagLayout());

        ModuleExplorerDialog(JFrame owner, List<MergeStep> merges, Map<String, Map<String, Double>> weights) {
            super(owner, "Identifier les modules", true);
            this.merges = merges;
            this.weights = weights;

            setMinimumSize(new Dimension(720, 520));
            setLocationRelativeTo(owner);

            JPanel content = new JPanel(new BorderLayout(12, 12));
            content.setBorder(new EmptyBorder(12, 12, 12, 12));

            // Explication + saisie seuil CP
            JPanel top = new JPanel(new GridBagLayout());
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets = new Insets(4, 4, 4, 4); gc.gridy = 0; gc.anchor = GridBagConstraints.WEST; gc.fill = GridBagConstraints.HORIZONTAL;

            JLabel expl = new JLabel(
                    "<html><b>Seuil CP</b> : la moyenne du couplage de <i>tous</i> les couples de classes d’un module " +
                            "doit être ≥ CP. Le nombre de modules est limité à M/2 (M = nombre de classes).<br>" +
                            "Valeurs typiques : 0.2 à 0.6 selon la densité de couplage.</html>"
            );
            gc.gridx = 0; gc.gridwidth = 3; gc.weightx = 1.0;
            top.add(expl, gc);

            gc.gridy++; gc.gridwidth = 1; gc.weightx = 0.0;
            top.add(new JLabel("CP (0..1) :"), gc);
            gc.gridx = 1; gc.weightx = 0.3;
            top.add(thresholdField, gc);
            JButton runBtn = new JButton("Calculer");
            gc.gridx = 2; gc.weightx = 0.0;
            top.add(runBtn, gc);

            // Contenu résultats
            JScrollPane scroll = new JScrollPane(resultPanel,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

            content.add(top, BorderLayout.NORTH);
            content.add(scroll, BorderLayout.CENTER);

            setContentPane(content);

            runBtn.addActionListener(e -> computeAndRender());
            // Calcul initial avec valeur par défaut
            SwingUtilities.invokeLater(this::computeAndRender);
            pack();
        }

        private void computeAndRender() {
            double cp;
            try {
                cp = Double.parseDouble(thresholdField.getText().trim());
                if (cp < 0.0) cp = 0.0;
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "CP doit être un réel.",
                        "Seuil invalide", JOptionPane.ERROR_MESSAGE);
                return;
            }

            List<Set<String>> modules = MainWindow.identifyCoupledGroups(merges, weights, cp);

            // rendu
            resultPanel.removeAll();
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridx = 0; gc.gridy = 0; gc.insets = new Insets(6, 6, 6, 6);
            gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1.0;

            if (modules.isEmpty()) {
                JLabel none = new JLabel("Aucun module ne satisfait CP.");
                none.setForeground(new Color(120, 0, 0));
                resultPanel.add(none, gc);
            } else {
                int idx = 1;
                for (Set<String> mod : modules) {
                    JPanel card = moduleCard(idx++, mod);
                    resultPanel.add(card, gc);
                    gc.gridy++;
                }
            }

            // glue vertical
            gc.weighty = 1.0; gc.fill = GridBagConstraints.BOTH;
            resultPanel.add(Box.createVerticalGlue(), gc);

            resultPanel.revalidate();
            resultPanel.repaint();
        }

        private JPanel moduleCard(int index, Set<String> classes) {
            JPanel p = new JPanel(new BorderLayout());
            p.setBorder(BorderFactory.createTitledBorder("Module " + index + "  (" + classes.size() + " classes)"));

            // Liste visuelle
            DefaultListModel<String> model = new DefaultListModel<>();
            java.util.List<String> list = new ArrayList<>(classes);
            Collections.sort(list);
            list.forEach(model::addElement);

            JList<String> jl = new JList<>(model);
            jl.setVisibleRowCount(Math.min(10, model.size()));
            JScrollPane sp = new JScrollPane(jl,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

            // actions : copier
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton copyBtn = new JButton("Copier");
            copyBtn.addActionListener(e -> {
                String text = String.join("\n", list);
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new java.awt.datatransfer.StringSelection(text), null);
            });
            actions.add(copyBtn);

            p.add(sp, BorderLayout.CENTER);
            p.add(actions, BorderLayout.SOUTH);
            return p;
        }
    }


    // ====================================================================================
//                              DendrogramPanel
// ====================================================================================
    static class DendrogramPanel extends JComponent {
        // Modèle
        private List<MainWindow.MergeStep> merges = List.of();

        // Arbre reconstruit
        private Node root;
        private List<Node> leaves = new ArrayList<>();

        // On force un Y discret par niveaux (feuilles→racine)
        private boolean levelMode = true; // toujours vrai selon ta demande
        private int treeMaxDepth = 1;     // nb de niveaux [1..]


        // Vue
        private double zoom = 1.0;
        private double offsetX = 0.0, offsetY = 0.0;
        private Point lastDrag;


        private static final int V_MARGIN  = 40;   // marge haut/bas
        private static final int LABEL_PAD = 4;    // padding fond du label
        private static final int LEAF_GAP  = 18;   // gap minimum entre libellés (px)

        // Échelle verticale
        private enum YScale { LINEAR, LOG }
        private YScale yScale = YScale.LINEAR;
        private double minScore = 0.0; // min > 0 pour log si possible
        private double maxScore = 1.0;

        private boolean invertY = true; // false = feuilles en bas, racine en haut

        // Seuils d'affichage
        private static final double LABEL_ZOOM_MIN = 0.55; // cacher labels si zoom < seuil
        private static final double LABEL_TILT_ZOOM = 0.75; // incliner légèrement les labels si zoom bas


        DendrogramPanel() {
            setOpaque(true);
            setBackground(Color.white);

            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { lastDrag = e.getPoint(); }
                @Override public void mouseReleased(MouseEvent e) { lastDrag = null; }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseDragged(MouseEvent e) {
                    if (lastDrag != null) {
                        Point p = e.getPoint();
                        offsetX += (p.x - lastDrag.x);
                        offsetY += (p.y - lastDrag.y);
                        lastDrag = p;
                        repaint();
                    }
                }
            });
            addMouseWheelListener(e -> {
                double factor = (e.getWheelRotation() < 0) ? 1.15 : (1.0/1.15);
                zoomAround(e.getPoint(), factor);
            });
        }

        void setDendrogram(List<MainWindow.MergeStep> merges) {
            this.merges = (merges == null) ? List.of() : merges;
            rebuildTree();
            autoLayout();
            fitToView();
        }

        // Calcule level pour chaque nœud: feuille=0, parent=1+max(level enfants)
        private int assignLevels(Node n) {
            if (n == null) return 0;
            if (n.isLeaf()) {
                n.y = 0.0;      // y normalisé (sera re-mappé en pixels à la peinture)
                return 0;
            }
            int l = assignLevels(n.left);
            int r = (n.right != null) ? assignLevels(n.right) : l;
            int myLevel = Math.max(l, r) + 1;
            // y normalisé par niveau (0..1), feuilles en bas, racine en haut
            n.y = (double) myLevel; // provisoire : valeur de niveau (entier)
            return myLevel;
        }

        // Normalise tous les y en [0..1] avec treeMaxDepth
        private void normalizeLevels(Node n) {
            if (n == null) return;
            n.y = (treeMaxDepth == 0) ? 0.0 : (n.y / (double) treeMaxDepth);
            normalizeLevels(n.left);
            if (n.right != null) normalizeLevels(n.right);
        }

        // (optionnel) utilitaire si besoin ailleurs
        private int depthOf(Node n) {
            if (n == null || n.isLeaf()) return 0;
            return 1 + Math.max(depthOf(n.left), depthOf(n.right));
        }

        // --- nouveau : parcours in-order pour les feuilles
        private void collectLeavesInOrder(Node n, List<Node> out) {
            if (n == null) return;
            if (n.isLeaf()) { out.add(n); return; }
            collectLeavesInOrder(n.left, out);
            collectLeavesInOrder(n.right, out);
        }

        // --- utilitaire : inversion des Y après normalisation par niveaux
        private void invertAllY(Node n) {
            if (n == null) return;
            n.y = 1.0 - n.y;
            invertAllY(n.left);
            invertAllY(n.right);
        }

        // ------------------------ Build & Layout ------------------------

        private static final class Node {
            final Set<String> members; // ensemble de classes couvertes par ce sous-arbre
            Node left, right;
            double score;  // hauteur (couplage) du merge créant ce nœud; 0 pour feuille
            // position (monde)
            double x, y;
            // feuille ?
            boolean isLeaf() { return left == null && right == null; }
            Node(Set<String> members, double score) { this.members = members; this.score = score; }
        }

        /*
        private void rebuildTree() {
            // 1) Collecte des symboles (feuilles)
            Set<String> all = new TreeSet<>();
            for (var m : merges) { all.addAll(m.left); all.addAll(m.right); }
            if (all.isEmpty()) { root = null; leaves = new ArrayList<>(); maxScore = 1; return; }

            // DSU: parent et représentant -> Node
            Map<String, String> parent = new HashMap<>();
            Map<String, Node>   repNode = new HashMap<>();
            for (String s : all) {
                parent.put(s, s);
                repNode.put(s, new Node(Set.of(s), 0.0)); // feuilles à 0 (normal)
            }

            // merges triés par score croissant (classique pour un dendrogramme)
            List<MainWindow.MergeStep> ordered = new ArrayList<>(merges);
            ordered.sort(Comparator.comparingDouble(m -> m.score));
            maxScore = Math.max(1e-9, ordered.isEmpty() ? 1.0 : ordered.get(ordered.size()-1).score);
            chooseYScale(ordered);

            // DSU helpers
            java.util.function.Function<String,String> find = new java.util.function.Function<>() {
                @Override public String apply(String x) {
                    String p = parent.get(x);
                    if (!p.equals(x)) parent.put(x, apply(p));
                    return parent.get(x);
                }
            };
            java.util.function.BiFunction<String,String,String> unite = (ra, rb) -> {
                parent.put(rb, ra);            // ra devient le nouveau rep
                return ra;
            };

            // util: fusionne une liste de représentants en un seul Node à hauteur m.score
            java.util.function.BiFunction<java.util.List<String>, Double, String> foldSide = (reps, score) -> {
                // reps : représentants DISTINCTS (id DSU)
                if (reps.isEmpty()) return null;
                String rep = reps.get(0);
                Node left = repNode.get(rep);
                for (int i = 1; i < reps.size(); i++) {
                    String r = reps.get(i);
                    Node right = repNode.get(r);
                    Node tmp = new Node(union(left.members, right.members), score); // <-- score m
                    tmp.left = left; tmp.right = right;
                    String newRep = unite.apply(rep, r);
                    repNode.remove(rep); repNode.remove(r);
                    repNode.put(newRep, tmp);
                    rep = newRep; left = tmp;
                }
                return rep; // représentant de ce côté après agglomération
            };

            // 2) Rejoue toutes les fusions
            for (var m : ordered) {
                if (m.left.isEmpty() || m.right.isEmpty()) continue;

                // représentants DISTINCTS de chaque côté au moment de m
                LinkedHashSet<String> leftRepsSet  = new LinkedHashSet<>();
                for (String s : m.left)  leftRepsSet.add(find.apply(s));
                LinkedHashSet<String> rightRepsSet = new LinkedHashSet<>();
                for (String s : m.right) rightRepsSet.add(find.apply(s));

                // agglomération côté gauche et côté droit, à la HAUTEUR m.score
                String repL = foldSide.apply(new ArrayList<>(leftRepsSet),  m.score);
                String repR = foldSide.apply(new ArrayList<>(rightRepsSet), m.score);
                if (repL == null || repR == null) continue;
                if (repL.equals(repR)) continue; // déjà fusionnés par étapes précédentes

                // fusion finale de l’étape m, à la HAUTEUR m.score
                Node ln = repNode.get(repL);
                Node rn = repNode.get(repR);
                Node parentNode = new Node(union(ln.members, rn.members), m.score);
                parentNode.left = ln; parentNode.right = rn;

                String newRep = unite.apply(repL, repR);
                repNode.remove(repL); repNode.remove(repR);
                repNode.put(newRep, parentNode);
            }

            // 3) Racine
            if (repNode.size() == 1) {
                root = repNode.values().iterator().next();
            } else {
                // fallback : pseudo-racine en peigne (rare si merges complets)
                List<Node> parts = new ArrayList<>(repNode.values());
                Node cur = parts.get(0);
                for (int i = 1; i < parts.size(); i++) {
                    Node p = new Node(union(cur.members, parts.get(i).members), maxScore);
                    p.left = cur; p.right = parts.get(i);
                    cur = p;
                }
                root = cur;
            }

            // 4) feuilles dans l’ordre gauche→droite de l’arbre (pas de tri alphabétique)
            leaves = new ArrayList<>();
            collectLeavesInOrder(root, leaves);

            // (optionnel) sanity-check: afficher les scores internes
            // assertInternalScores();
        }

         */

        private void rebuildTree() {
            // 1) Collecte des feuilles
            Set<String> all = new TreeSet<>();
            for (var m : merges) { all.addAll(m.left); all.addAll(m.right); }
            if (all.isEmpty()) { root = null; leaves = new ArrayList<>(); maxScore = 1; return; }

            // DSU: parent et représentant -> Node
            Map<String, String> parent = new HashMap<>();
            Map<String, Node>   repNode = new HashMap<>();
            for (String s : all) {
                parent.put(s, s);
                repNode.put(s, new Node(Set.of(s), 0.0)); // feuilles: score=0 (normal)
            }

            // 2) PAS de tri: on respecte l'ordre fourni par l'algo
            List<MainWindow.MergeStep> ordered = new ArrayList<>(merges);

            // maxScore pour l’échelle verticale
            maxScore = 1e-9;
            for (var m : ordered) maxScore = Math.max(maxScore, Math.max(0.0, m.score));
            chooseYScale(ordered);

            // DSU helpers
            java.util.function.Function<String,String> find = new java.util.function.Function<>() {
                @Override public String apply(String x) {
                    String p = parent.get(x);
                    if (!p.equals(x)) parent.put(x, apply(p));
                    return parent.get(x);
                }
            };
            java.util.function.BiFunction<String,String,String> unite = (ra, rb) -> {
                parent.put(rb, ra);            // ra devient représentant
                return ra;
            };

            // Agglomération d’un côté à hauteur m.score (propagation du score GARANTIE)
            java.util.function.BiFunction<java.util.List<String>, Double, String> foldSide = (repList, score) -> {
                if (repList.isEmpty()) return null;
                String rep = repList.get(0);
                Node cur = repNode.get(rep);
                for (int i = 1; i < repList.size(); i++) {
                    String r = repList.get(i);
                    Node other = repNode.get(r);
                    Node tmp = new Node(union(cur.members, other.members), score); // <-- m.score !
                    tmp.left = cur; tmp.right = other;
                    String newRep = unite.apply(rep, r);
                    repNode.remove(rep); repNode.remove(r);
                    repNode.put(newRep, tmp);
                    rep = newRep; cur = tmp;
                }
                return rep;
            };

            // 3) Rejoue toutes les fusions DANS L’ORDRE D’ENTRÉE
            for (var m : ordered) {
                if (m.left.isEmpty() || m.right.isEmpty()) continue;

                // représentants DISTINCTS de chaque côté au moment de cette étape
                LinkedHashSet<String> L = new LinkedHashSet<>();
                for (String s : m.left)  L.add(find.apply(s));
                LinkedHashSet<String> R = new LinkedHashSet<>();
                for (String s : m.right) R.add(find.apply(s));

                String repL = foldSide.apply(new ArrayList<>(L), m.score);
                String repR = foldSide.apply(new ArrayList<>(R), m.score);
                if (repL == null || repR == null) continue;
                if (repL.equals(repR)) continue; // déjà fusionnés par une étape précédente

                Node ln = repNode.get(repL);
                Node rn = repNode.get(repR);

                // Fusion finale à HAUTEUR m.score (pas de 0, pas de max(...))
                Node parentNode = new Node(union(ln.members, rn.members), m.score);
                parentNode.left = ln; parentNode.right = rn;

                String newRep = unite.apply(repL, repR);
                repNode.remove(repL); repNode.remove(repR);
                repNode.put(newRep, parentNode);
            }

            // 4) Racine
            if (repNode.size() == 1) {
                root = repNode.values().iterator().next();
            } else {
                // fallback (rare si merges complets) : on assemble au maxScore
                List<Node> parts = new ArrayList<>(repNode.values());
                parts.sort(Comparator.comparingInt(n -> -n.members.size()));
                Node cur = parts.get(0);
                for (int i = 1; i < parts.size(); i++) {
                    Node p = new Node(union(cur.members, parts.get(i).members), maxScore);
                    p.left = cur; p.right = parts.get(i);
                    cur = p;
                }
                root = cur;
            }

            // 5) Feuilles dans l’ordre gauche→droite (pas de tri alpha)
            leaves = new ArrayList<>();
            collectLeavesInOrder(root, leaves);

            // (optionnel) sanity-check :
            // assertInternalScores();
        }


        // Clé canonique: TreeSet (ordre stable) et Set.copyOf pour immuabilité
        private static Set<String> canon(Set<String> s) {
            return Collections.unmodifiableSet(new TreeSet<>(s));
        }

        // Si on doit "recréer" un sous-arbre (non attendu), on fait un peigne à m.score
        private Node buildCombFromLeaves(Set<String> members, double score) {
            List<String> list = new ArrayList<>(members);
            list.sort(String::compareTo);
            if (list.isEmpty()) return null;
            Node cur = new Node(Set.of(list.get(0)), 0.0);
            for (int i = 1; i < list.size(); i++) {
                Node leaf = new Node(Set.of(list.get(i)), 0.0);
                Node p = new Node(canon(union(cur.members, leaf.members)), score); // hauteur = m.score
                p.left = cur; p.right = leaf;
                cur = p;
            }
            return cur;
        }

        private void collectLeaves(Node n, List<Node> out) {
            if (n == null) return;
            if (n.isLeaf()) { out.add(n); return; }
            collectLeaves(n.left, out);
            collectLeaves(n.right, out);
        }






        private static Node pickNodeCovering(Map<Set<String>, Node> bySet, Set<String> target) {
            // trouve un nœud dont l'ensemble == target
            Node exact = bySet.get(target);
            if (exact != null) return exact;
            // sinon tente un nœud dont les membres == target (via equals de Set)
            for (var e : bySet.entrySet()) {
                if (e.getKey().equals(target)) return e.getValue();
            }
            return null;
        }

        private Node buildSubtreeFromLeaves(Map<Set<String>, Node> bySet, Set<String> members) {
            // construit un « peigne » artificiel à partir des feuilles demandées
            List<Node> list = new ArrayList<>();
            for (String s : members) list.add(new Node(Set.of(s), 0.0));
            if (list.isEmpty()) return null;
            Node cur = list.get(0);
            for (int i = 1; i < list.size(); i++) {
                Node p = new Node(union(cur.members, list.get(i).members), 0.0);
                p.left = cur; p.right = list.get(i);
                cur = p;
            }
            bySet.put(cur.members, cur);
            return cur;
        }

        private static Set<String> union(Set<String> a, Set<String> b) {
            Set<String> u = new TreeSet<>(a); u.addAll(b); return u;
        }

        private void autoLayout() {
            if (root == null) return;

            FontMetrics fm = getFontMetrics(getFont());
            double x = 0.0;
            for (Node lf : leaves) {
                String label = shortName(lf.members.iterator().next());
                int w = fm.stringWidth(label);
                lf.x = x + w / 2.0;
                x += w + 18; // gap constant
            }

            int deepest = assignLevels(root);        // niveaux entiers (feuille=0)
            treeMaxDepth = Math.max(1, deepest);
            normalizeLevels(root);                   // 0..1 (feuille=0, racine=1)
            invertAllY(root);                        // => feuille=1 (bas), racine=0 (haut)
            assignX(root);                           // barycentre des enfants
        }



        private void assignY(Node n) {
            if (n == null) return;
            if (n.isLeaf()) { n.y = invertY ? 1.0 : 0.0; return; }
            assignY(n.left);
            assignY(n.right);
            double t = scaleY(n.score); // ∈ [0..1]
            n.y = invertY ? (1.0 - t) : t;
        }



        private double assignX(Node n) {
            if (n.isLeaf()) return n.x;
            double xl = assignX(n.left);
            double xr = (n.right != null) ? assignX(n.right) : xl;
            n.x = (xl + xr) / 2.0;
            return n.x;
        }

        // ------------------------ View ops ------------------------

        void fitToView() {
            if (root == null) { repaint(); return; }
            Rectangle2D bounds = getTreeBounds(); // basé sur feuilles.x
            Insets in = getInsets();
            int vw = Math.max(1, getWidth() - in.left - in.right);
            int vh = Math.max(1, getHeight() - in.top - in.bottom);

            // Hauteur en pixels : V_MARGIN haut + (échelle Y 0..1) * (vh - 2*V_MARGIN) + V_MARGIN bas
            double hWorld = Math.max(1.0, (vh - 2.0 * V_MARGIN)); // on travaille déjà en pixels virtuels
            double wWorld = Math.max(1.0, bounds.getWidth());

            double sx = vw / wWorld;
            double sy = 1.0; // l’échelle Y est déjà dans le mapping scaleY → [0..1] * (vh - 2*V_MARGIN)
            double s = Math.max(0.1, Math.min(sx, sy));
            zoom = s;

            // centre horizontalement sur le milieu des feuilles, verticalement au milieu de [V_MARGIN..vh-V_MARGIN]
            double viewCx = in.left + vw / 2.0;
            double viewCy = in.top + vh / 2.0;

            double cx = bounds.getCenterX();
            double cy = (V_MARGIN + (vh - 2.0 * V_MARGIN) / 2.0);

            offsetX = viewCx - cx * zoom;
            offsetY = viewCy - cy * zoom;
            repaint();
        }


        void zoomBy(double factor) {
            Point c = new Point(getWidth()/2, getHeight()/2);
            zoomAround(c, factor);
        }

        private void zoomAround(Point pivot, double factor) {
            factor = Math.max(0.1, Math.min(5.0, zoom * factor)) / zoom;
            offsetX = pivot.x - (pivot.x - offsetX) * factor;
            offsetY = pivot.y - (pivot.y - offsetY) * factor;
            zoom *= factor;
            repaint();
        }

        private Rectangle2D getTreeBounds() {
            // borne X d’après les feuilles et un padding
            double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
            for (Node lf : leaves) {
                minX = Math.min(minX, lf.x);
                maxX = Math.max(maxX, lf.x);
            }
            if (!Double.isFinite(minX)) { minX = 0; maxX = 0; }
            double pad = 40;
            return new Rectangle2D.Double(minX - pad, 0, (maxX - minX) + 2*pad, 1);
        }


        // ------------------------ Rendering ------------------------

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRect(0,0,getWidth(),getHeight());

                AffineTransform old = g2.getTransform();
                g2.translate(offsetX, offsetY);
                g2.scale(zoom, zoom);

                if (root != null) {
                    int H = getHeight();
                    double y0 = V_MARGIN;
                    double yH = H - V_MARGIN;
                    double pxPerUnit = Math.max(1.0, yH - y0);

                    drawNode(g2, root, y0, pxPerUnit);
                    drawLeafLabels(g2, y0, pxPerUnit);
                }

                g2.setTransform(old);
                g2.setColor(new Color(0,0,0,120));
                g2.drawString("Pan: drag | Zoom: wheel | Fit: bouton", 10, getHeight() - 10);
            } finally {
                g2.dispose();
            }
        }

        private void drawNode(Graphics2D g2, Node n, double y0, double pxPerUnit) {
            if (n == null || n.isLeaf()) return;

            double y = y0 + n.y * pxPerUnit;

            g2.setStroke(new BasicStroke(1.6f));
            g2.setColor(new Color(90,90,90));

            double xl = n.left.x;
            double yl = y0 + n.left.y * pxPerUnit;
            g2.draw(new java.awt.geom.Line2D.Double(xl, yl, xl, y));

            double xr = (n.right != null ? n.right.x : n.left.x);
            double yr = (n.right != null ? (y0 + n.right.y * pxPerUnit) : yl);
            if (n.right != null) {
                g2.draw(new java.awt.geom.Line2D.Double(xr, yr, xr, y));
            }
            g2.draw(new java.awt.geom.Line2D.Double(xl, y, xr, y));

            // score discret (facultatif)
            String lbl = fmtWeight(n.score);
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(lbl);
            g2.setColor(new Color(255,255,255,220));
            g2.fillRoundRect((int)((xl+xr)/2 - tw/2 - 3), (int)(y - fm.getAscent() - 6), tw+6, fm.getAscent()+4, 8,8);
            g2.setColor(new Color(60,60,60));
            g2.drawString(lbl, (int)((xl+xr)/2 - tw/2), (int)(y - 6));

            drawNode(g2, n.left, y0, pxPerUnit);
            if (n.right != null) drawNode(g2, n.right, y0, pxPerUnit);
        }


        private void drawLeafLabels(Graphics2D g2, double y0, double pxPerUnit) {
            double z = this.zoom;

            // cacher labels si zoom trop faible (LOD)
            if (z < LABEL_ZOOM_MIN) return;

            FontMetrics fm = g2.getFontMetrics();
            for (Node lf : leaves) {
                String full = lf.members.iterator().next();
                String name = shortName(full);

                int tw = fm.stringWidth(name);
                int th = fm.getAscent();

                double x = lf.x;
                double y = y0 + lf.y * pxPerUnit + th + 6;

                // petit fond
                g2.setColor(new Color(255,255,255,220));

                if (z < LABEL_TILT_ZOOM) {
                    // inclinaison légère (lisibilité en dense)
                    AffineTransform old = g2.getTransform();
                    g2.translate(x, y);
                    g2.rotate(-Math.toRadians(25));

                    g2.fillRoundRect(-tw/2 - LABEL_PAD, -th - LABEL_PAD + 3, tw + 2*LABEL_PAD, th + 2*LABEL_PAD, 8,8);
                    g2.setColor(new Color(30,30,30));
                    g2.drawString(name, -tw/2, 0);

                    g2.setTransform(old);
                } else {
                    g2.fillRoundRect((int)(x - tw/2 - LABEL_PAD), (int)(y - th - LABEL_PAD + 3),
                            tw + 2*LABEL_PAD, th + 2*LABEL_PAD, 8,8);
                    g2.setColor(new Color(30,30,30));
                    g2.drawString(name, (int)(x - tw/2), (int)(y));
                }
            }
        }


        private static String fmtWeight(double w) {
            if (Double.isNaN(w)) return "NaN";
            if (Double.isInfinite(w)) return (w > 0 ? "+Inf" : "-Inf");
            // 6 chiffres significatifs, Locale.US pour le point décimal
            return String.format(java.util.Locale.US, "%.6g", w);
        }


        private static String shortName(String fqcn) {
            if (fqcn == null) return "";
            int i = fqcn.lastIndexOf('.');
            return (i >= 0 && i + 1 < fqcn.length()) ? fqcn.substring(i + 1) : fqcn;
        }

        private double scaleY(double score) {
            if (maxScore <= 0) return 0;
            if (yScale == YScale.LOG) {
                // LOG sur [minScore..maxScore], avec garde-fous
                double lo = Math.max(1e-12, minScore);
                double hi = Math.max(lo * (1.0 + 1e-9), maxScore);
                double s  = Math.max(lo, score);
                return Math.log1p(s - lo) / Math.log1p(hi - lo);
            } else {
                // LINEAR sur [0..maxScore]
                double s = Math.max(0.0, score);
                return s / maxScore;
            }
        }

        /** Calcule la dispersion et choisit LINEAR/LOG automatiquement. */
        private void chooseYScale(java.util.List<MainWindow.MergeStep> ordered) {
            // min>0 (hors 0) et max
            double minPos = Double.POSITIVE_INFINITY, maxVal = 0.0;
            for (var m : ordered) {
                double v = Math.max(0.0, m.score);
                if (v > 0.0) minPos = Math.min(minPos, v);
                maxVal = Math.max(maxVal, v);
            }
            minScore = (minPos == Double.POSITIVE_INFINITY) ? 0.0 : minPos;
            maxScore = Math.max(1e-9, maxVal);

            // heuristique : si max/min > 50, passe en LOG
            if (minScore > 0.0 && (maxScore / minScore) > 50.0) {
                yScale = YScale.LOG;
            } else {
                yScale = YScale.LINEAR;
            }
        }

    }

}
