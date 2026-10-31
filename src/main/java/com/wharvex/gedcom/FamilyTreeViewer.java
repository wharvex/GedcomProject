package com.wharvex.gedcom;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

/** Graphical viewer that draws the GEDCOM file as a descendant family tree chart. */
public class FamilyTreeViewer extends JFrame {
    private record BranchStem(ConnectedTreeModel.Family family, int x, int y) {
    }

    private final ChartPanel chart = new ChartPanel();
    private final JTextField searchField = new JTextField(20);
    private final JLabel searchStatus = new JLabel(" ");

    private final boolean connected;

    public FamilyTreeViewer() {
        this(false);
    }

    public FamilyTreeViewer(boolean connected) {
        super(connected ? "GEDCOM Connected Family Tree" : "GEDCOM Family Tree");
        this.connected = connected;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        JButton open = new JButton("Open...");
        open.addActionListener(e -> chooseFile());
        JButton previous = new JButton("Previous");
        previous.addActionListener(e -> chart.moveMatch(-1));
        JButton next = new JButton("Next");
        next.addActionListener(e -> chart.moveMatch(1));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(open);
        toolbar.add(new JLabel("Search:"));
        toolbar.add(searchField);
        toolbar.add(previous);
        toolbar.add(next);
        toolbar.add(searchStatus);
        add(toolbar, BorderLayout.NORTH);
        add(new JScrollPane(chart), BorderLayout.CENTER);
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void changed() {
                chart.setSearchQuery(searchField.getText());
            }

            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                changed();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                changed();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                changed();
            }
        });
        chart.setMatchCountListener(count -> searchStatus.setText(
                count == 0 ? (searchField.getText().isEmpty() ? " " : "No matches")
                        : (chart.getActiveMatchIndex() + 1) + " of " + count));
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
            GedcomDocument doc = new GedcomReader().read(path);
            if (connected) {
                chart.setConnectedModel(new ConnectedTreeModel(doc));
            } else {
                chart.setModel(new FamilyTreeModel(doc));
            }
            setTitle((connected ? "GEDCOM Connected Family Tree - " : "GEDCOM Family Tree - ") + path);
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Failed to parse file: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    static List<Integer> findOccurrences(String text, String query) {
        List<Integer> matches = new ArrayList<>();
        if (query.isEmpty()) return matches;
        int from = 0;
        while (from <= text.length() - query.length()) {
            int match = -1;
            for (int i = from; i <= text.length() - query.length(); i++) {
                if (text.regionMatches(true, i, query, 0, query.length())) {
                    match = i;
                    break;
                }
            }
            if (match < 0) break;
            matches.add(match);
            from = match + query.length();
        }
        return matches;
    }

    static Color familyLineColor(int familyIndex) {
        double hue = (familyIndex * 0.618033988749895) % 1.0;
        if (hue < 0) hue++;
        return Color.getHSBColor((float) hue, 0.72f, 0.58f);
    }

    private static class ChartPanel extends JPanel {
        private static final int BOX_W = 160;
        private static final int LINE_H = 16;
        private static final int GAP_X = 20;
        private static final int GAP_Y = 40;
        private static final int PAD = 20;

        private FamilyTreeModel model;
        private ConnectedTreeModel connectedModel;
        private String searchQuery = "";
        private int activeMatchIndex;
        private final List<Rectangle> searchMatches = new ArrayList<>();
        private boolean searchNeedsRefresh;
        private int searchGeneration;
        private IntConsumer matchCountListener = count -> {
        };

        void setMatchCountListener(IntConsumer listener) {
            matchCountListener = listener;
        }

        int getActiveMatchIndex() {
            return activeMatchIndex;
        }

        void setSearchQuery(String query) {
            if (searchQuery.equals(query)) return;
            searchQuery = query;
            activeMatchIndex = 0;
            searchMatches.clear();
            searchNeedsRefresh = true;
            searchGeneration++;
            repaint();
        }

        void moveMatch(int direction) {
            if (searchMatches.isEmpty()) return;
            activeMatchIndex = Math.floorMod(activeMatchIndex + direction, searchMatches.size());
            repaint();
            scrollToActiveMatch();
            matchCountListener.accept(searchMatches.size());
        }

        void setConnectedModel(ConnectedTreeModel model) {
            this.model = null;
            this.connectedModel = model;
            activeMatchIndex = 0;
            searchMatches.clear();
            searchNeedsRefresh = true;
            searchGeneration++;
            setPreferredSize(new Dimension(model.getWidth(), model.getHeight()));
            revalidate();
            repaint();
        }

        void setModel(FamilyTreeModel model) {
            this.model = model;
            this.connectedModel = null;
            activeMatchIndex = 0;
            searchMatches.clear();
            searchNeedsRefresh = true;
            searchGeneration++;
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
            searchMatches.clear();
            if (connectedModel != null) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                drawConnected(g2);
                g2.dispose();
                updateSearchStatusAfterPaint();
                return;
            }
            if (model == null) {
                g.drawString("No file loaded", PAD, PAD);
                updateSearchStatusAfterPaint();
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
            updateSearchStatusAfterPaint();
        }

        private void updateSearchStatusAfterPaint() {
            if (!searchNeedsRefresh) return;
            searchNeedsRefresh = false;
            if (activeMatchIndex >= searchMatches.size()) activeMatchIndex = 0;
            int count = searchMatches.size();
            int generation = searchGeneration;
            Rectangle activeMatch = count == 0 ? null : new Rectangle(searchMatches.get(activeMatchIndex));
            SwingUtilities.invokeLater(() -> {
                if (generation != searchGeneration) return;
                matchCountListener.accept(count);
                if (activeMatch != null) scrollRectToVisible(activeMatch);
            });
        }

        private void scrollToActiveMatch() {
            if (activeMatchIndex < searchMatches.size()) {
                Rectangle match = searchMatches.get(activeMatchIndex);
                scrollRectToVisible(new Rectangle(match.x, match.y, match.width, match.height));
            }
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

        private void drawConnected(Graphics2D g) {
            final int boxW = ConnectedTreeModel.BOX_W;
            final int boxH = ConnectedTreeModel.BOX_H;
            List<ConnectedTreeModel.Family> families = connectedModel.getFamilies();
            for (int familyIndex = 0; familyIndex < families.size(); familyIndex++) {
                ConnectedTreeModel.Family f = families.get(familyIndex);
                g.setColor(familyLineColor(familyIndex));
                ConnectedTreeModel.Person h = f.husband;
                ConnectedTreeModel.Person w = f.wife;
                if (h != null && w != null) {
                    ConnectedTreeModel.Person left = h.x <= w.x ? h : w;
                    ConnectedTreeModel.Person right = left == h ? w : h;
                    if (left.y == right.y && right.x - left.x == boxW + ConnectedTreeModel.SPOUSE_GAP) {
                        int anchorY = left.y + boxH / 2;
                        g.drawLine(left.x + boxW, anchorY, right.x, anchorY);
                    } else {
                        int anchorY = Math.max(h.y, w.y) + boxH + 8;
                        g.drawLine(h.x + boxW / 2, h.y + boxH, h.x + boxW / 2, anchorY);
                        g.drawLine(w.x + boxW / 2, w.y + boxH, w.x + boxW / 2, anchorY);
                        g.drawLine(h.x + boxW / 2, anchorY, w.x + boxW / 2, anchorY);
                    }
                }
            }
            Map<BranchStem, Integer> stemEnds = new LinkedHashMap<>();
            for (ConnectedTreeModel.BranchRoute route : connectedModel.getBranchRoutes()) {
                BranchStem stem = new BranchStem(route.family, route.anchorX, route.anchorY);
                int endY = route.routeX >= 0 ? route.exitY : route.busY;
                stemEnds.merge(stem, endY, Math::max);
            }
            for (Map.Entry<BranchStem, Integer> entry : stemEnds.entrySet()) {
                BranchStem stem = entry.getKey();
                g.setColor(familyLineColor(families.indexOf(stem.family())));
                g.drawLine(stem.x(), stem.y(), stem.x(), entry.getValue());
            }
            for (ConnectedTreeModel.BranchRoute route : connectedModel.getBranchRoutes()) {
                g.setColor(familyLineColor(families.indexOf(route.family)));
                int busStartX = route.routeX >= 0 ? route.routeX : route.anchorX;
                int minX = busStartX;
                int maxX = busStartX;
                for (ConnectedTreeModel.ChildConnection child : route.children) {
                    minX = Math.min(minX, child.x);
                    maxX = Math.max(maxX, child.x);
                }
                if (route.routeX >= 0) {
                    g.drawLine(route.anchorX, route.exitY, route.routeX, route.exitY);
                    g.drawLine(route.routeX, route.exitY, route.routeX, route.busY);
                }
                g.drawLine(minX, route.busY, maxX, route.busY);
                for (ConnectedTreeModel.ChildConnection child : route.children) {
                    g.drawLine(child.x, route.busY, child.x, child.person.y);
                }
            }
            for (ConnectedTreeModel.Person p : connectedModel.getPeople()) {
                g.setColor(new Color(235, 243, 255));
                g.fillRoundRect(p.x, p.y, boxW, boxH, 10, 10);
                g.setColor(Color.DARK_GRAY);
                g.drawRoundRect(p.x, p.y, boxW, boxH, 10, 10);
                drawSearchableText(g, clip(g, p.name), p.x + 6, p.y + LINE_H, Color.BLACK);
                drawSearchableText(g, clip(g, p.life), p.x + 6, p.y + 2 * LINE_H, Color.GRAY);
            }
        }

        private int text(Graphics2D g, FamilyTreeModel.Person p, int bx, int ty) {
            drawSearchableText(g, clip(g, p.name), bx + 6, ty, Color.BLACK);
            ty += LINE_H;
            drawSearchableText(g, clip(g, p.life), bx + 6, ty, Color.GRAY);
            return ty + LINE_H;
        }

        private void drawSearchableText(Graphics2D g, String text, int x, int baseline, Color color) {
            if (searchQuery.isEmpty()) {
                g.setColor(color);
                g.drawString(text, x, baseline);
                return;
            }

            List<Integer> occurrences = findOccurrences(text, searchQuery);
            FontMetrics metrics = g.getFontMetrics();
            int cursor = 0;
            for (int start : occurrences) {
                int end = start + searchQuery.length();
                g.setColor(color);
                g.drawString(text.substring(cursor, start),
                        x + metrics.stringWidth(text.substring(0, cursor)), baseline);
                int matchX = x + metrics.stringWidth(text.substring(0, start));
                int matchWidth = metrics.stringWidth(text.substring(start, end));
                Rectangle bounds = new Rectangle(matchX, baseline - metrics.getAscent(),
                        matchWidth, metrics.getHeight());
                boolean active = searchMatches.size() == activeMatchIndex;
                g.setColor(active ? new Color(255, 170, 70) : new Color(255, 240, 120));
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
                g.setColor(Color.BLACK);
                g.drawString(text.substring(start, end), matchX, baseline);
                searchMatches.add(bounds);
                cursor = end;
            }
            g.setColor(color);
            g.drawString(text.substring(cursor), x + metrics.stringWidth(text.substring(0, cursor)), baseline);
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
