package hai913i.tp2.gui;

import hai913i.main.CodeGenerationProcessorMain;
import hai913i.main.ModuleIdentifier;
import hai913i.tp2.spoon.model.ClassInfo;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;


public class CouplingDashboardWindow extends JFrame {

    private final java.util.List<String> classChoices;
    private final java.util.List<ClassInfo> classes;
    private final Map<String, Map<String, Double>> weight;
    private final long totCallAppBetweenBinaryClasses;
    private final CodeGenerationProcessorMain.ClusteringResult clustering;

    // <<< AJOUT : couples non ordonnés -> poids
    private final Map<CodeGenerationProcessorMain.UnorderedPair<String>, Double> couples;

    public CouplingDashboardWindow(
            java.util.List<String> classChoices,
            java.util.List<ClassInfo> classes,
            Map<String, Map<String, Double>> weight,
            long totCallAppBetweenBinaryClasses,
            CodeGenerationProcessorMain.ClusteringResult clustering,
            Map<CodeGenerationProcessorMain.UnorderedPair<String>, Double> couples // <<< AJOUT
    ) {
        super("Analyse de Couplage — Spoon");
        this.classChoices = classChoices;
        this.classes = classes;
        this.weight = weight;
        this.totCallAppBetweenBinaryClasses = totCallAppBetweenBinaryClasses;
        this.clustering = clustering;
        this.couples = couples; // <<<

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1200, 800));
        setLocationByPlatform(true);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Couplage", buildCouplingTab());
        tabs.addTab("Graphe", new GraphPanel(classes, weight));
        tabs.addTab("Dendrogramme", new DendrogramPanel(clustering));
        tabs.addTab("Modules", new ModulesPanel()); // <<< NOUVEL ONGLET

        setContentPane(tabs);
        pack();
    }

    // ---------------------- Onglet 1: Couplage ----------------------
    private JPanel buildCouplingTab() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JComboBox<String> a = new JComboBox<>(classChoices.toArray(new String[0]));
        JComboBox<String> b = new JComboBox<>(classChoices.toArray(new String[0]));
        JButton calc = new JButton("Calculer");
        JTextArea out = new JTextArea(14, 80);
        out.setEditable(false);
        out.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        top.add(new JLabel("Classe A :"));
        top.add(a);
        top.add(Box.createHorizontalStrut(12));
        top.add(new JLabel("Classe B :"));
        top.add(b);
        top.add(Box.createHorizontalStrut(12));
        top.add(calc);

        root.add(top, BorderLayout.NORTH);
        root.add(new JScrollPane(out), BorderLayout.CENTER);

        DecimalFormat df = new DecimalFormat("#0.00000");

        calc.addActionListener(e -> {
            String sa = (String) a.getSelectedItem();
            String sb = (String) b.getSelectedItem();
            if (Objects.equals(sa, sb)) {
                out.setText("Sélectionnez deux classes différentes.");
                return;
            }
            Optional<ClassInfo> ca = classes.stream().filter(c -> c.className.equalsIgnoreCase(sa)).findFirst();
            Optional<ClassInfo> cb = classes.stream().filter(c -> c.className.equalsIgnoreCase(sb)).findFirst();

            if (ca.isEmpty() || cb.isEmpty()) {
                out.setText("Class not found : " + (ca.isPresent() ? sb : sa));
                return;
            }

            long nbCallAToB =
                    ca.get().methods.stream()
                            .mapToLong(m -> m.methodCalls.stream()
                                    .filter(mc -> mc.receiverType.equals(sb))
                                    .count())
                            .sum();

            long nbCallBToA =
                    cb.get().methods.stream()
                            .mapToLong(m -> m.methodCalls.stream()
                                    .filter(mc -> mc.receiverType.equals(sa))
                                    .count())
                            .sum();

            double couplage = (totCallAppBetweenBinaryClasses == 0)
                    ? 0.0
                    : (double) (nbCallAToB + nbCallBToA) / totCallAppBetweenBinaryClasses;

            StringBuilder sbuf = new StringBuilder();
            sbuf.append("Nombre de call classe ").append(sa).append(" -> ").append(sb).append(" : ").append(nbCallAToB).append("\n");
            sbuf.append("Nombre de call classe ").append(sb).append(" -> ").append(sa).append(" : ").append(nbCallBToA).append("\n");
            sbuf.append("Nombre de call tot app entre deux classes : ").append(totCallAppBetweenBinaryClasses).append("\n");
            sbuf.append("Couplage entre ").append(sa).append(" et ").append(sb).append(" => ").append(df.format(couplage)).append("\n");

            out.setText(sbuf.toString());
        });

        return root;
    }


    // ---------------------- Onglet 2: Graphe (pondéré) ----------------------
    private static class GraphPanel extends JPanel {
        private final java.util.List<ClassInfo> classes;
        private final Map<String, Map<String, Double>> weight;
        private final JSlider thresholdSlider = new JSlider(0, 100, 0);
        private final JCheckBox showLabels = new JCheckBox("Labels", false);
        private final DecimalFormat df = new DecimalFormat("#0.000");
        private final Map<String, Point2D.Double> pos = new HashMap<>();
        private double zoom = 1.0, offsetX = 0, offsetY = 0;
        private Point dragOrigin;

        GraphPanel(java.util.List<ClassInfo> classes, Map<String, Map<String, Double>> weight) {
            super(new BorderLayout(8, 8));
            this.classes = classes;
            this.weight = weight;

            JPanel top = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            top.add(new JLabel("Seuil:"));
            thresholdSlider.setToolTipText("Seuil d’affichage des arêtes (% du max)");
            thresholdSlider.setPreferredSize(new Dimension(220, 40));
            top.add(thresholdSlider);
            top.add(showLabels);
            add(top, BorderLayout.NORTH);

            Canvas canvas = new Canvas();
            add(new JScrollPane(canvas), BorderLayout.CENTER);

            thresholdSlider.addChangeListener(e -> canvas.repaint());
            showLabels.addActionListener(e -> canvas.repaint());
        }

        private class Canvas extends JPanel {
            Canvas() {
                setPreferredSize(new Dimension(1600, 1200));
                setBackground(Color.white);
                addMouseWheelListener(e -> {
                    double factor = (e.getPreciseWheelRotation() < 0) ? 1.1 : 1.0 / 1.1;
                    Point p = e.getPoint();
                    zoomAround(p, factor);
                });
                addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        dragOrigin = e.getPoint();
                    }
                });
                addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                    @Override
                    public void mouseDragged(java.awt.event.MouseEvent e) {
                        if (dragOrigin != null) {
                            offsetX += (e.getX() - dragOrigin.x) / zoom;
                            offsetY += (e.getY() - dragOrigin.y) / zoom;
                            dragOrigin = e.getPoint();
                            repaint();
                        }
                    }
                });
            }

            private void zoomAround(Point p, double factor) {
                double px = (p.x / zoom) - offsetX;
                double py = (p.y / zoom) - offsetY;
                zoom *= factor;
                offsetX = (p.x / zoom) - px;
                offsetY = (p.y / zoom) - py;
                repaint();
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.translate(offsetX * zoom, offsetY * zoom);
                g2.scale(zoom, zoom);

                java.util.List<String> nodes = classes.stream().map(ci -> ci.className).sorted().collect(Collectors.toList());
                placeOnCircle(nodes, 480);

                // max poids
                double maxW = 0.0;
                for (var a : weight.keySet())
                    for (var e : weight.get(a).entrySet())
                        maxW = Math.max(maxW, e.getValue());
                double threshold = (thresholdSlider.getValue() / 100.0) * maxW;

                // edges
                for (String a : nodes) {
                    Map<String, Double> row = weight.getOrDefault(a, Map.of());
                    for (String b : nodes) {
                        if (a.equals(b)) continue;
                        double w = row.getOrDefault(b, 0.0);
                        double w2 = weight.getOrDefault(b, Map.of()).getOrDefault(a, 0.0);
                        double ww = w + w2;
                        if (ww <= threshold || ww <= 0) continue;

                        Point2D pa = pos.get(a), pb = pos.get(b);
                        float stroke = (float) (1.0 + 10.0 * (ww / (maxW > 0 ? maxW : 1.0)));
                        g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.setColor(new Color(30, 144, 255, 140));
                        g2.draw(new Line2D.Double(pa, pb));

                        if (showLabels.isSelected()) {
                            String label = df.format(ww);
                            drawLabel(g2, label, mid(pa, pb));
                        }
                    }
                }

                // nodes
                for (String n : nodes) drawNode(g2, n, pos.get(n));

                g2.dispose();
            }

            private void placeOnCircle(java.util.List<String> nodes, double radius) {
                pos.clear();
                double cx = getPreferredSize().width / 2.0;
                double cy = getPreferredSize().height / 2.0;
                int N = nodes.size();
                for (int i = 0; i < N; i++) {
                    double ang = 2 * Math.PI * i / Math.max(1, N);
                    pos.put(nodes.get(i), new Point2D.Double(cx + radius * Math.cos(ang), cy + radius * Math.sin(ang)));
                }
            }

            private void drawNode(Graphics2D g2, String name, Point2D p) {
                int r = 16;
                Shape s = new Ellipse2D.Double(p.getX() - r, p.getY() - r, 2 * r, 2 * r);
                g2.setColor(new Color(60, 60, 60));
                g2.fill(s);
                g2.setColor(Color.white);
                g2.setStroke(new BasicStroke(2f));
                g2.draw(s);
                FontMetrics fm = g2.getFontMetrics();
                int w = fm.stringWidth(name);
                g2.setColor(Color.black);
                g2.drawString(name, (float) (p.getX() - w / 2.0), (float) (p.getY() - r - 6));
            }

            private void drawLabel(Graphics2D g2, String txt, Point2D c) {
                FontMetrics fm = g2.getFontMetrics();
                int w = fm.stringWidth(txt);
                int h = fm.getAscent();
                int pad = 4;
                Shape bg = new RoundRectangle2D.Double(c.getX() - w / 2.0 - pad, c.getY() - h - pad,
                        w + 2 * pad, h + 2 * pad, 8, 8);
                g2.setColor(new Color(255, 255, 210));
                g2.fill(bg);
                g2.setColor(new Color(120, 120, 80));
                g2.draw(bg);
                g2.setColor(Color.black);
                g2.drawString(txt, (float) (c.getX() - w / 2.0), (float) (c.getY() - 4));
            }

            private Point2D mid(Point2D a, Point2D b) {
                return new Point2D.Double((a.getX() + b.getX()) / 2.0, (a.getY() + b.getY()) / 2.0);
            }
        }
    }

    // ---------------------- Onglet 3: Dendrogramme ----------------------
    private static class DendrogramPanel extends JPanel {
        private final CodeGenerationProcessorMain.ClusteringResult res;

        // UI
        private final JCheckBox uniformLevels = new JCheckBox("Niveaux uniformes (branches égales)", true);
        private final JCheckBox alphabeticalLeaves = new JCheckBox("Feuilles triées alphabétiquement", false);
        private final DecimalFormat df = new DecimalFormat("#0.000");

        // structure interne
        private static final class Node {
            final Set<String> leaves;   // feuilles couvertes
            final double heightScore;   // score de merge (feuille: 0)
            final Node left, right;     // null pour feuille
            int depth;                  // profondeur (calculée)
            Node(Set<String> leaves, double heightScore, Node left, Node right) {
                this.leaves = leaves; this.heightScore = heightScore; this.left = left; this.right = right;
            }
            boolean isLeaf() { return left == null && right == null; }
        }

        private Node root;
        private java.util.List<String> leafOrder = List.of(); // ordre d'affichage gauche→droite
        private Map<String, Integer> xLeaf;                    // X des feuilles
        private int maxDepth;                                  // profondeur max (pour échelle uniforme)

        DendrogramPanel(CodeGenerationProcessorMain.ClusteringResult res) {
            this.res = res;
            setLayout(new BorderLayout());
            setBackground(Color.white);

            // barre d'options
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            top.add(uniformLevels);
            top.add(alphabeticalLeaves);
            JButton refresh = new JButton("Mettre à jour");
            top.add(refresh);
            add(top, BorderLayout.NORTH);

            // zone dessin
            Canvas canvas = new Canvas();
            add(new JScrollPane(canvas), BorderLayout.CENTER);

            // build initial
            rebuildTree();
            refresh.addActionListener(e -> { canvas.repaint(); });
        }

        private void rebuildTree() {
            var merges = res.merges();
            if (merges.isEmpty()) { root = null; leafOrder = List.of(); return; }

            // 1) feuilles observées
            Set<String> allLeaves = new LinkedHashSet<>();
            for (var m : merges) { allLeaves.addAll(m.left()); allLeaves.addAll(m.right()); }

            Map<Set<String>, Node> nodes = new HashMap<>();
            for (String s : allLeaves) {
                nodes.put(Set.of(s), new Node(Set.of(s), 0.0, null, null));
            }

            // 2) rejouer les merges dans l'ordre fourni par le clustering (agglomératif)
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

            // 3) racine = noeud avec max de feuilles
            root = nodes.values().stream().max(Comparator.comparingInt(n -> n.leaves.size())).orElse(null);

            // 4) profondeur (pour échelle uniforme)
            computeDepth(root);
            // 5) ordre des feuilles induit par l'arbre (in-order)
            var order = new ArrayList<String>();
            collectLeavesInOrder(root, order);
            leafOrder = order;
        }

        private static Node findBySet(Map<Set<String>, Node> nodes, Set<String> target) {
            for (var e : nodes.entrySet()) {
                if (e.getKey().equals(target)) return e.getValue();
            }
            return null;
        }

        private int computeDepth(Node n) {
            if (n == null) return -1;
            if (n.isLeaf()) {
                n.depth = 0;
                maxDepth = Math.max(maxDepth, 0);
                return 0;
            }
            int dl = computeDepth(n.left);
            int dr = computeDepth(n.right);
            n.depth = Math.max(dl, dr) + 1;     // parent = 1 + max(profondeur enfants)
            maxDepth = Math.max(maxDepth, n.depth);
            return n.depth;
        }


        private static void collectLeavesInOrder(Node n, java.util.List<String> out) {
            if (n == null) return;
            if (n.isLeaf()) {
                out.add(n.leaves.iterator().next());
                return;
            }
            collectLeavesInOrder(n.left, out);
            collectLeavesInOrder(n.right, out);
        }

        private class Canvas extends JPanel {
            Canvas() {
                setPreferredSize(new Dimension(1600, 1000));
                setBackground(Color.white);
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (root == null || leafOrder.isEmpty()) {
                    g2.setColor(Color.darkGray);
                    g2.drawString("Aucun merge (jeu de données vide ou une seule classe).", 20, 30);
                    g2.dispose();
                    return;
                }

                // marges et surface de tracé
                int leftMargin = 140, rightMargin = 140, topMargin = 40, bottomMargin = 80;
                int W = getWidth(), H = getHeight();
                int plotLeft = leftMargin, plotRight = W - rightMargin;
                int plotTop = topMargin, plotBottom = H - bottomMargin;
                int plotWidth = plotRight - plotLeft, plotHeight = plotBottom - plotTop;

                // X des feuilles
                java.util.List<String> leaves = new ArrayList<>(leafOrder);
                if (alphabeticalLeaves.isSelected()) {
                    leaves.sort(String::compareTo);
                }
                xLeaf = new HashMap<>();
                for (int i = 0; i < leaves.size(); i++) {
                    double u = (i + 0.5) / Math.max(1, leaves.size());
                    xLeaf.put(leaves.get(i), plotLeft + (int) Math.round(u * plotWidth));
                }

                // Y mapping : soit uniforme par niveaux, soit par score
                double minScore = 0.0;
                double maxScore = Math.max(root.heightScore, 1e-12);
                java.util.function.DoubleFunction<Integer> yScore = s -> {
                    double t = (s - minScore) / (maxScore - minScore);
                    return plotBottom - (int) Math.round(t * plotHeight);
                };
                java.util.function.IntFunction<Integer> yDepth = d -> {
                    double t = (double) d / Math.max(1, maxDepth);
                    return plotBottom - (int) Math.round(t * plotHeight);
                };

                // grille
                g2.setColor(new Color(235,235,235));
                if (uniformLevels.isSelected()) {
                    for (int d = 0; d <= maxDepth; d++) {
                        int y = yDepth.apply(d);
                        g2.drawLine(plotLeft, y, plotRight, y);
                    }
                } else {
                    for (int i = 0; i <= 5; i++) {
                        double s = minScore + i * (maxScore - minScore) / 5.0;
                        int y = yScore.apply(s);
                        g2.drawLine(plotLeft, y, plotRight, y);
                        g2.setColor(Color.DARK_GRAY);
                        g2.drawString(df.format(s), 8, y + 4);
                        g2.setColor(new Color(235,235,235));
                    }
                }

                // tracé récursif
                g2.setStroke(new BasicStroke(2.2f));
                g2.setColor(new Color(60, 120, 200));
                drawNode(g2, root, yDepth, yScore);

                // feuilles (ticks + labels)
                g2.setColor(Color.BLACK);
                int y0 = uniformLevels.isSelected() ? yDepth.apply(0) : yScore.apply(minScore);
                for (String leaf : leaves) {
                    int x = xLeaf.get(leaf);
                    g2.drawLine(x, y0, x, y0 + 6);
                    drawCenterString(g2, leaf, x, y0 + 20);
                }

                g2.dispose();
            }

            private void drawNode(Graphics2D g2,
                                  Node n,
                                  java.util.function.IntFunction<Integer> yDepth,
                                  java.util.function.DoubleFunction<Integer> yScore) {
                if (n == null || n.isLeaf()) return;

                int yMerge = uniformLevels.isSelected() ? yDepth.apply(n.depth) : yScore.apply(n.heightScore);

                // gauche
                int xL = xOfCluster(n.left);
                int yL = uniformLevels.isSelected() ? yDepth.apply(n.left.depth) : yScore.apply(n.left.heightScore);
                g2.drawLine(xL, yL, xL, yMerge);

                // droite
                int xR = xOfCluster(n.right);
                int yR = uniformLevels.isSelected() ? yDepth.apply(n.right.depth) : yScore.apply(n.right.heightScore);
                g2.drawLine(xR, yR, xR, yMerge);

                // barre horizontale du merge (longueur visuelle = distance entre sous-clusters)
                g2.drawLine(Math.min(xL, xR), yMerge, Math.max(xL, xR), yMerge);

                // label score (utile même en mode uniforme)
                drawCenterString(g2, df.format(n.heightScore), (xL + xR) / 2, yMerge - 6);

                // récursion
                drawNode(g2, n.left, yDepth, yScore);
                drawNode(g2, n.right, yDepth, yScore);
            }

            private int xOfCluster(Node n) {
                if (n.isLeaf()) return xLeaf.get(n.leaves.iterator().next());
                String leftMost = leftmost(n);
                String rightMost = rightmost(n);
                return (xLeaf.get(leftMost) + xLeaf.get(rightMost)) / 2;
            }

            private String leftmost(Node n) {
                return n.isLeaf() ? n.leaves.iterator().next() : leftmost(n.left);
            }
            private String rightmost(Node n) {
                return n.isLeaf() ? n.leaves.iterator().next() : rightmost(n.right);
            }

            private void drawCenterString(Graphics2D g2, String s, int cx, int cy) {
                FontMetrics fm = g2.getFontMetrics();
                int w = fm.stringWidth(s);
                g2.drawString(s, cx - w / 2, cy);
            }
        }
    }

    // ---------------------- Onglet 4: Modules ----------------------
    private class ModulesPanel extends JPanel {
        private final JSpinner cpSpinner;
        private final JButton runBtn = new JButton("Calculer");
        private final JLabel info = new JLabel("—");
        private final ModulesTableModel model = new ModulesTableModel();
        private final JTable table = new JTable(model);

        ModulesPanel() {
            super(new BorderLayout(8,8));
            setBorder(new EmptyBorder(10,10,10,10));

            // Top controls
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            cpSpinner = new JSpinner(new SpinnerNumberModel(0.05, 0.0, 1.0, 0.005));
            ((JSpinner.DefaultEditor)cpSpinner.getEditor()).getTextField().setColumns(6);
            top.add(new JLabel("CP (moyenne intra-module) >"));
            top.add(cpSpinner);
            top.add(runBtn);
            top.add(Box.createHorizontalStrut(16));
            top.add(info);

            add(top, BorderLayout.NORTH);

            table.setFillsViewportHeight(true);
            table.setAutoCreateRowSorter(true);
            table.setRowHeight(22);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
            table.getColumnModel().getColumn(0).setPreferredWidth(40);   // #
            table.getColumnModel().getColumn(1).setPreferredWidth(60);   // Taille
            table.getColumnModel().getColumn(2).setPreferredWidth(90);   // Avg
            table.getColumnModel().getColumn(3).setPreferredWidth(900);  // Classes
            add(new JScrollPane(table), BorderLayout.CENTER);

            runBtn.addActionListener(e -> run());
            run(); // premier calcul avec la valeur par défaut
        }

        private void run() {
            double cp = ((Number) cpSpinner.getValue()).doubleValue();
            var modules = ModuleIdentifier.identifyModules(clustering, couples, cp);
            model.setData(modules);
            int M = clustering.clusters().isEmpty()
                    ? 0
                    : clustering.clusters().stream().mapToInt(Set::size).max().orElse(0);
            info.setText(String.format("Modules: %d (limite M/2 = %d)  —  CP=%.3f",
                    modules.size(), Math.max(1, M/2), cp));
        }
    }

    private static class ModulesTableModel extends AbstractTableModel {
        private final String[] cols = {"#", "Taille", "Avg", "Classes"};
        private final DecimalFormat df = new DecimalFormat("#0.000");
        private java.util.List<ModuleIdentifier.Module> data = List.of();

        public void setData(java.util.List<ModuleIdentifier.Module> d) {
            this.data = new ArrayList<>(d);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }

        @Override
        public Object getValueAt(int row, int col) {
            var m = data.get(row);
            return switch (col) {
                case 0 -> row + 1;
                case 1 -> m.classes().size();
                case 2 -> df.format(m.avgCoupling());
                case 3 -> String.join(", ", new TreeSet<>(m.classes()));
                default -> "";
            };
        }

        @Override public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 0,1 -> Integer.class;
                case 2 -> String.class;
                default -> String.class;
            };
        }
    }
}
