package com.wharvex.gedcom;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a maximally interconnected family graph from a GEDCOM document and lays it out by generation. Spouses are
 * placed side by side in marriage blocks, children are connected to both recorded parents, and each spouse's own
 * parents are placed above that spouse.
 */
public class ConnectedTreeModel {
    public static final int BOX_W = 160;
    public static final int BOX_H = 44;
    public static final int SPOUSE_GAP = 24;
    public static final int GAP_X = 30;
    public static final int GAP_Y = 60;
    public static final int PAD = 20;
    private static final int ORDERING_PASSES = 12;

    /** A family (FAM record) with its recorded parents and children. */
    public static class Family {
        public final Person husband;
        public final Person wife;
        public final List<Person> children = new ArrayList<>();

        Family(Person husband, Person wife) {
            this.husband = husband;
            this.wife = wife;
        }
    }

    /** A person together with the rectangle on which they are drawn. */
    public static class Person {
        public final String id;
        public final String name;
        public final String life;
        public int x;
        public int y;
        int generation;
        Block block;

        Person(String id, String name, String life) {
            this.id = id;
            this.name = name;
            this.life = life;
        }
    }

    /** People joined by marriage; drawn together in one row. */
    static class Block {
        final List<Person> members = new ArrayList<>();
        int generation;
        double x;
        int width;
        final List<Block> above = new ArrayList<>();
        final List<Block> below = new ArrayList<>();

        double center() {
            return x + width / 2.0;
        }
    }

    private final List<Person> people = new ArrayList<>();
    private final List<Family> families = new ArrayList<>();
    private int width = PAD * 2;
    private int height = PAD * 2;

    public ConnectedTreeModel(GedcomDocument doc) {
        Map<String, Person> byId = new LinkedHashMap<>();
        List<GedcomNode> famNodes = new ArrayList<>();
        for (GedcomNode n : doc.getRootNodes()) {
            if ("INDI".equals(n.getTag()) && n.getXrefId() != null) {
                byId.put(n.getXrefId(), new Person(n.getXrefId(), FamilyTreeModel.nameOf(n),
                        FamilyTreeModel.lifeOf(n)));
            } else if ("FAM".equals(n.getTag()) && n.getXrefId() != null) {
                famNodes.add(n);
            }
        }
        people.addAll(byId.values());
        for (GedcomNode fam : famNodes) {
            Person husb = null;
            Person wife = null;
            List<Person> kids = new ArrayList<>();
            for (GedcomNode c : fam.getChildren()) {
                Person p = byId.get(c.getValue());
                if (p == null)
                    continue;
                switch (c.getTag()) {
                case "HUSB" -> husb = p;
                case "WIFE" -> wife = p;
                case "CHIL" -> kids.add(p);
                default -> {
                }
                }
            }
            if (husb == null && wife == null)
                continue;
            Family f = new Family(husb, wife);
            for (Person k : kids) {
                if (k != husb && k != wife && !f.children.contains(k))
                    f.children.add(k);
            }
            families.add(f);
        }
        layout();
    }

    public List<Person> getPeople() {
        return people;
    }

    public List<Family> getFamilies() {
        return families;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    private void layout() {
        List<Block> blocks = buildBlocks();
        assignGenerations(blocks);
        linkBlocks(blocks);
        int maxGen = 0;
        for (Block b : blocks)
            maxGen = Math.max(maxGen, b.generation);
        List<List<Block>> layers = new ArrayList<>();
        for (int g = 0; g <= maxGen; g++)
            layers.add(new ArrayList<>());
        for (Block b : blocks)
            layers.get(b.generation).add(b);
        for (List<Block> layer : layers)
            place(layer, null);
        for (int pass = 0; pass < ORDERING_PASSES; pass++) {
            for (int g = 1; g <= maxGen; g++)
                place(layers.get(g), true);
            for (int g = maxGen - 1; g >= 0; g--)
                place(layers.get(g), false);
        }
        double minX = Double.MAX_VALUE;
        double maxX = 0;
        for (Block b : blocks) {
            minX = Math.min(minX, b.x);
            maxX = Math.max(maxX, b.x + b.width);
        }
        if (blocks.isEmpty())
            return;
        for (Block b : blocks) {
            int bx = (int) Math.round(b.x - minX) + PAD;
            int by = PAD + b.generation * (BOX_H + GAP_Y);
            for (int i = 0; i < b.members.size(); i++) {
                Person p = b.members.get(i);
                p.x = bx + i * (BOX_W + SPOUSE_GAP);
                p.y = by;
            }
        }
        width = (int) Math.round(maxX - minX) + PAD * 2;
        height = PAD * 2 + (maxGen + 1) * BOX_H + maxGen * GAP_Y;
    }

    private List<Block> buildBlocks() {
        Map<Person, List<Person>> spouses = new HashMap<>();
        for (Family f : families) {
            if (f.husband != null && f.wife != null && f.husband != f.wife) {
                spouses.computeIfAbsent(f.husband, k -> new ArrayList<>()).add(f.wife);
                spouses.computeIfAbsent(f.wife, k -> new ArrayList<>()).add(f.husband);
            }
        }
        List<Block> blocks = new ArrayList<>();
        for (Person p : people) {
            if (p.block != null)
                continue;
            Block b = new Block();
            Deque<Person> queue = new ArrayDeque<>();
            queue.add(p);
            p.block = b;
            while (!queue.isEmpty()) {
                Person cur = queue.poll();
                b.members.add(cur);
                for (Person s : spouses.getOrDefault(cur, List.of())) {
                    if (s.block == null) {
                        s.block = b;
                        queue.add(s);
                    }
                }
            }
            if (b.members.size() == 2) {
                for (Family f : families) {
                    if (f.husband != null && f.wife != null && b.members.contains(f.husband)
                            && b.members.contains(f.wife)) {
                        b.members.set(0, f.husband);
                        b.members.set(1, f.wife);
                        break;
                    }
                }
            }
            b.width = b.members.size() * BOX_W + (b.members.size() - 1) * SPOUSE_GAP;
            blocks.add(b);
        }
        return blocks;
    }

    private void assignGenerations(List<Block> blocks) {
        // the iteration cap keeps malformed (cyclic) data from looping forever
        for (int i = 0; i <= blocks.size(); i++) {
            boolean changed = false;
            for (Family f : families) {
                int parentGen = Math.max(f.husband == null ? 0 : f.husband.block.generation,
                        f.wife == null ? 0 : f.wife.block.generation);
                for (Person c : f.children) {
                    if (c.block.generation < parentGen + 1) {
                        c.block.generation = parentGen + 1;
                        changed = true;
                    }
                }
            }
            if (!changed)
                break;
        }
    }

    private void linkBlocks(List<Block> blocks) {
        for (Family f : families) {
            Person parent = f.husband != null ? f.husband : f.wife;
            for (Person c : f.children) {
                if (c.block.generation <= parent.block.generation)
                    continue;
                for (Person par : new Person[] { f.husband, f.wife }) {
                    if (par != null && !par.block.below.contains(c.block)) {
                        par.block.below.add(c.block);
                        c.block.above.add(par.block);
                    }
                }
            }
        }
    }

    /**
     * Orders the layer by the average position of neighbours (above if {@code useAbove}, below if false, either when
     * null) and places the blocks left to right as close to that position as possible without overlapping.
     */
    private static void place(List<Block> layer, Boolean useAbove) {
        if (useAbove == null) {
            double x = 0;
            for (Block b : layer) {
                b.x = x;
                x += b.width + GAP_X;
            }
            return;
        }
        Map<Block, Double> desired = new HashMap<>();
        for (Block b : layer) {
            List<Block> nb = useAbove ? b.above : b.below;
            double sum = 0;
            for (Block n : nb)
                sum += n.center();
            desired.put(b, nb.isEmpty() ? b.center() : sum / nb.size());
        }
        layer.sort(Comparator.comparingDouble(desired::get));
        double minX = -Double.MAX_VALUE;
        for (Block b : layer) {
            b.x = Math.max(minX, desired.get(b) - b.width / 2.0);
            minX = b.x + b.width + GAP_X;
        }
    }
}
