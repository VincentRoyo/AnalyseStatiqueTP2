package hai913i.tp2.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProjectPathChooser extends JDialog {

    public interface Callback {
        void onChosen(String projectPath);
        void onCancel();
    }

    private final JTextField pathField = new JTextField();
    private final DefaultListModel<File> listModel = new DefaultListModel<>();
    private final JList<File> list = new JList<>(listModel);
    private final JButton chooseBtn = new JButton("Valider");
    private final JButton browseBtn = new JButton("Parcourir…");
    private final JButton refreshBtn = new JButton("Actualiser");
    private final JButton cancelBtn = new JButton("Annuler");

    private final Callback callback;

    public ProjectPathChooser(Frame owner, Callback callback) {
        super(owner, "Choisir un projet Java", true);
        this.callback = callback;
        buildUI();
        scanDesktopForJavaProjects();
        pack();
        setMinimumSize(new Dimension(700, 420));
        setLocationRelativeTo(owner);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(root);

        // Header
        JLabel title = new JLabel("Sélectionnez un projet Java à analyser");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        root.add(title, BorderLayout.NORTH);

        // Center: list + path
        JPanel center = new JPanel(new GridBagLayout());
        root.add(center, BorderLayout.CENTER);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1; gbc.weighty = 1; gbc.fill = GridBagConstraints.BOTH;

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof File f) {
                    setText(f.getAbsolutePath());
                }
                return c;
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && !list.isSelectionEmpty()) {
                    File f = list.getSelectedValue();
                    pathField.setText(f.getAbsolutePath());
                    validateAndCloseIfOK();
                }
            }
        });
        list.addListSelectionListener(e -> {
            File f = list.getSelectedValue();
            if (f != null) {
                pathField.setText(f.getAbsolutePath());
            }
            updateValidateState();
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createTitledBorder("Projets détectés sur le Bureau"));
        center.add(scroll, gbc);

        // Path chooser row
        gbc.gridy++;
        gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel pathRow = new JPanel(new GridBagLayout());
        GridBagConstraints pgbc = new GridBagConstraints();
        pgbc.insets = new Insets(4, 4, 4, 4);
        pgbc.gridx = 0; pgbc.weightx = 0; pgbc.fill = GridBagConstraints.NONE;
        pathRow.add(new JLabel("Chemin du projet :"), pgbc);

        pgbc.gridx = 1; pgbc.weightx = 1; pgbc.fill = GridBagConstraints.HORIZONTAL;
        pathField.setToolTipText("Dossier du projet (doit contenir un sous-dossier src/)");
        pathField.getDocument().addDocumentListener((SimpleDocumentListener) e -> updateValidateState());
        pathRow.add(pathField, pgbc);

        pgbc.gridx = 2; pgbc.weightx = 0; pgbc.fill = GridBagConstraints.NONE;
        pathRow.add(browseBtn, pgbc);

        pgbc.gridx = 3;
        pathRow.add(refreshBtn, pgbc);

        center.add(pathRow, gbc);

        // Footer buttons
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(cancelBtn);
        south.add(chooseBtn);
        root.add(south, BorderLayout.SOUTH);

        // Actions
        browseBtn.addActionListener(e -> onBrowse());
        refreshBtn.addActionListener(e -> scanDesktopForJavaProjects());
        cancelBtn.addActionListener(e -> { if (callback != null) callback.onCancel(); dispose(); });
        chooseBtn.addActionListener(e -> validateAndCloseIfOK());

        getRootPane().setDefaultButton(chooseBtn);
        updateValidateState();

        // ESC closes
        root.registerKeyboardAction(e -> cancelBtn.doClick(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private void onBrowse() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choisir le dossier du projet Java");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        File pre = currentPathAsFile();
        if (pre != null && pre.exists()) chooser.setCurrentDirectory(pre);
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File dir = chooser.getSelectedFile();
            pathField.setText(dir.getAbsolutePath());
            updateValidateState();
        }
    }

    private void validateAndCloseIfOK() {
        String path = pathField.getText().trim();
        if (isJavaProject(path)) {
            if (callback != null) callback.onChosen(path);
            dispose();
        } else {
            JOptionPane.showMessageDialog(this,
                    "Le dossier sélectionné ne ressemble pas à un projet Java (pas de sous-dossier src/).",
                    "Projet invalide", JOptionPane.WARNING_MESSAGE);
        }
    }

    private File currentPathAsFile() {
        String p = pathField.getText().trim();
        if (p.isEmpty()) return null;
        return new File(p);
    }

    private void updateValidateState() {
        chooseBtn.setEnabled(isJavaProject(pathField.getText().trim()));
    }

    /** Détecte les projets sur le Bureau (gère Desktop et Bureau). */
    private void scanDesktopForJavaProjects() {
        listModel.clear();
        for (File desk : candidateDesktopFolders()) {
            if (desk != null && desk.exists() && desk.isDirectory()) {
                File[] children = desk.listFiles(File::isDirectory);
                if (children != null) {
                    Arrays.stream(children)
                            .filter(ProjectPathChooser::isJavaProject)
                            .forEach(listModel::addElement);
                }
            }
        }
    }

    /** Heuristiques robustes pour trouver le Bureau selon l’OS et la locale. */
    private static List<File> candidateDesktopFolders() {
        List<File> out = new ArrayList<>();

        File home = new File(System.getProperty("user.home", "."));
        // 1) Desktop (EN)
        out.add(new File(home, "Desktop"));
        // 2) Bureau (FR)
        out.add(new File(home, "Bureau"));
        // 3) FileSystemView (parfois pointe vers le Bureau)
        File fsvHome = FileSystemView.getFileSystemView().getHomeDirectory();
        if (fsvHome != null && fsvHome.exists() && fsvHome.isDirectory()) {
            out.add(fsvHome);
        }
        return out;
    }

    /** Un "projet Java" = dossier contenant un sous-dossier src/ */
    public static boolean isJavaProject(File dir) {
        return dir != null && dir.isDirectory() && new File(dir, "src").exists();
    }

    public static boolean isJavaProject(String projectPath) {
        File root = new File(projectPath);
        if (!root.exists() || !root.isDirectory()) return false;

        // Accept: src/, src/main/java/, ou src/java/
        return new File(root, "src").exists()
                || new File(root, "src/main/java").exists()
                || new File(root, "src/java").exists();
    }


    // Petit util pour écouter les changements de texte sans boilerplate
    @FunctionalInterface
    private interface SimpleDocumentListener extends javax.swing.event.DocumentListener {
        void update(javax.swing.event.DocumentEvent e);
        @Override default void insertUpdate(javax.swing.event.DocumentEvent e) { update(e); }
        @Override default void removeUpdate(javax.swing.event.DocumentEvent e) { update(e); }
        @Override default void changedUpdate(javax.swing.event.DocumentEvent e) { update(e); }
    }

    /** API simple : affiche la boîte et renvoie le chemin choisi (ou null si annulation) */
    public static String chooseProjectPath(Component parent) {
        final String[] result = { null };

        // crée une frame "cachée" si parent null
        Frame owner;
        if (parent != null) {
            owner = SwingUtilities.getWindowAncestor(parent) instanceof Frame f ? f : null;
        } else {
            owner = new JFrame(); // fenêtre invisible de secours
            owner.setUndecorated(true);
            owner.setLocationRelativeTo(null);
            owner.setVisible(true);
        }

        ProjectPathChooser dlg = new ProjectPathChooser(owner, new Callback() {
            @Override public void onChosen(String projectPath) { result[0] = projectPath; }
            @Override public void onCancel() { result[0] = null; }
        });

        dlg.setVisible(true);

        // ferme la frame de secours si on l’a créée
        if (parent == null && owner != null) owner.dispose();

        return result[0];
    }
}