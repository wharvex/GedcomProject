package com.wharvex.gedcom;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Graphical viewer that draws the GEDCOM file as a descendant family tree chart. */
public class FamilyTreeViewer extends JFrame {
    private final ChartPanel chart = new ChartPanel();

    public FamilyTreeViewer() {
        super("GEDCOM Family Tree");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        JButton open = new JButton("Open...");
        open.addActionListener(e -> chooseFile());
        add(open, BorderLayout.NORTH);
        add(new JScrollPane(chart), BorderLayout.CENTER);
        setPreferredSize(new Dimension(1000, 700));
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
            chart.setModel(new FamilyTreeModel(new GedcomReader().read(path)));
            setTitle("GEDCOM Family Tree - " + path);
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Failed to parse file: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static class ChartPanel extends JPanel {
        private static final int BOX_W = 160;
        private static final int LINE_H = 16;
        private static final int GAP_X = 20;
        private static final int GAP_Y = 40;
        private static final int PAD = 20;

        private FamilyTreeModel model;

        void setModel(FamilyTreeModel model) {
            this.model = model;
            int w = PAD;
            int h = PAD;
            Set<String> seen = new HashSet<>();
            for (FamilyTreeModel.Person r : model.getRoots()) {
                w += width(r, new HashSet<>(seen)) + GAP_X;
                h = Math.max(h, PAD * 2 + height(r, 0, new HashSet<>()));
            }
            setPreferredSize(new Dimension(w + PAD, h));
            revalidate();
            repaint();
        }

        private static int boxHeight(FamilyTreeModel.Person p) {
            int lines = 2;
            for (FamilyTreeModel.Person s : p.spouses) lines += 2;
            return lines * LINE_H + 6;
        }

        private static int width(FamilyTreeModel.Person p, Set<String> seen) {
            if (!seen.add(p.id)) return BOX_W;
            int sum = 0;
            for (FamilyTreeModel.Person c : p.children) {
                if (seen.contains(c.id)) continue;
                sum += width(c, seen) + GAP_X;
            }
            return Math.max(BOX_W, sum - GAP_X);
        }

        private static int height(FamilyTreeModel.Person p, int depthGuard, Set<String> seen) {
            if (!seen.add(p.id)) return 0;
            int max = 0;
            for (FamilyTreeModel.Person c : p.children) {
                max = Math.max(max, height(c, depthGuard + 1, seen));
            }
            return boxHeight(p) + (max > 0 ? GAP_Y + max : 0);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (model == null) {
                g.drawString("No file loaded", PAD, PAD);
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int x = PAD;
            Set<String> seen = new HashSet<>();
            for (FamilyTreeModel.Person r : model.getRoots()) {
                if (seen.contains(r.id)) continue;
                int w = width(r, new HashSet<>(seen));
                draw(g2, r, x, PAD, w, seen);
                x += w + GAP_X;
            }
            g2.dispose();
        }

        /** Draws p centered in [x, x+w); returns bottom-center attach point is implicit. */
        private void draw(Graphics2D g, FamilyTreeModel.Person p, int x, int y, int w, Set<String> seen) {
            seen.add(p.id);
            int bh = boxHeight(p);
            int bx = x + (w - BOX_W) / 2;
            g.setColor(new Color(235, 243, 255));
            g.fillRoundRect(bx, y, BOX_W, bh, 10, 10);
            g.setColor(Color.DARK_GRAY);
            g.drawRoundRect(bx, y, BOX_W, bh, 10, 10);
            int ty = y + LINE_H;
            ty = text(g, p, bx, ty);
            for (FamilyTreeModel.Person s : p.spouses) {
                g.drawLine(bx + 6, ty - LINE_H + 6, bx + BOX_W - 6, ty - LINE_H + 6);
                ty = text(g, s, bx, ty + 4);
            }
            int childY = y + bh + GAP_Y;
            int cx = x;
            int px = bx + BOX_W / 2;
            int totalW = 0;
            for (FamilyTreeModel.Person c : p.children) {
                if (seen.contains(c.id)) continue;
                int cw = width(c, new HashSet<>(seen));
                g.setColor(Color.DARK_GRAY);
                int childCenter = cx + cw / 2;
                g.drawLine(px, y + bh, px, y + bh + GAP_Y / 2);
                g.drawLine(px, y + bh + GAP_Y / 2, childCenter, y + bh + GAP_Y / 2);
                g.drawLine(childCenter, y + bh + GAP_Y / 2, childCenter, childY);
                draw(g, c, cx, childY, cw, seen);
                cx += cw + GAP_X;
                totalW += cw;
            }
        }

        private int text(Graphics2D g, FamilyTreeModel.Person p, int bx, int ty) {
            g.setColor(Color.BLACK);
            g.drawString(clip(g, p.name), bx + 6, ty);
            ty += LINE_H;
            g.setColor(Color.GRAY);
            g.drawString(clip(g, p.life), bx + 6, ty);
            return ty + LINE_H;
        }

        private String clip(Graphics2D g, String s) {
            FontMetrics fm = g.getFontMetrics();
            while (s.length() > 1 && fm.stringWidth(s) > BOX_W - 12) s = s.substring(0, s.length() - 1);
            return s;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FamilyTreeViewer v = new FamilyTreeViewer();
            v.setVisible(true);
            if (args.length > 0) v.load(Path.of(args[0]));
        });
    }
}
