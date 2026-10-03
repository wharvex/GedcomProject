package com.wharvex.gedcom;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Path;

public class GedcomViewer extends JFrame {
    private final JTree tree = new JTree(new DefaultTreeModel(new DefaultMutableTreeNode("No file loaded")));

    public GedcomViewer() {
        super("GEDCOM Viewer");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        JButton open = new JButton("Open...");
        open.addActionListener(e -> chooseFile());
        add(open, BorderLayout.NORTH);
        add(new JScrollPane(tree), BorderLayout.CENTER);
        setPreferredSize(new Dimension(800, 600));
        pack();
        setLocationRelativeTo(null);
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser(".");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            load(chooser.getSelectedFile().toPath());
        }
    }

    public void load(Path path) {
        try {
            GedcomDocument doc = new GedcomReader().read(path);
            DefaultMutableTreeNode root = new DefaultMutableTreeNode(path.getFileName().toString());
            for (GedcomNode n : doc.getRootNodes()) {
                root.add(toTreeNode(n));
            }
            tree.setModel(new DefaultTreeModel(root));
            for (int i = 0; i < tree.getRowCount() && i < 200; i++) {
                tree.expandRow(i);
            }
            setTitle("GEDCOM Viewer - " + path);
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Failed to parse file: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    static DefaultMutableTreeNode toTreeNode(GedcomNode node) {
        StringBuilder sb = new StringBuilder();
        if (node.getXrefId() != null) sb.append(node.getXrefId()).append(' ');
        sb.append(node.getTag());
        if (node.getValue() != null && !node.getValue().isEmpty()) sb.append(' ').append(node.getValue());
        DefaultMutableTreeNode t = new DefaultMutableTreeNode(sb.toString());
        for (GedcomNode c : node.getChildren()) {
            t.add(toTreeNode(c));
        }
        return t;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GedcomViewer v = new GedcomViewer();
            v.setVisible(true);
            if (args.length > 0) v.load(Path.of(args[0]));
        });
    }
}
