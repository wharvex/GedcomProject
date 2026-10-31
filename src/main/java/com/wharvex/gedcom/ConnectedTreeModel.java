package com.wharvex.gedcom;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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
    private static final int ROUTE_LANE_GAP = 12;

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

    /** A family branch routed through a collision-free horizontal lane. */
    public static class BranchRoute {
        public final Family family;
        public final int anchorX;
        public final int anchorY;
        public final int busY;
        public final int routeX;
        public final int exitY;
        public final List<ChildConnection> children;

        BranchRoute(Family family, int anchorX, int anchorY, int busY, int routeX, int exitY,
                List<ChildConnection> children) {
            this.family = family;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.busY = busY;
            this.routeX = routeX;
            this.exitY = exitY;
            this.children = children;
        }
    }

    public static class ChildConnection {
        public final Person person;
        public final int x;

        ChildConnection(Person person, int x) {
            this.person = person;
            this.x = x;
        }
    }

    private static class BranchRoutePlan {
        final Family family;
        final int parentGeneration;
        final int childGeneration;
        final List<Person> children;
        final int anchorX;
        int routeX = -1;
        int exitLane;
        int lane;
        int minX;
        int maxX;
        final Map<Person, Integer> childPorts = new HashMap<>();

        BranchRoutePlan(Family family, int parentGeneration, int childGeneration, List<Person> children, int anchorX) {
            this.family = family;
            this.parentGeneration = parentGeneration;
            this.childGeneration = childGeneration;
            this.children = children;
            this.anchorX = anchorX;
            minX = anchorX;
            maxX = anchorX;
        }
    }

    record BranchSpan(int minX, int maxX) {
        boolean overlaps(BranchSpan other) {
            return minX <= other.maxX && other.minX <= maxX;
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
    private final List<BranchRoute> branchRoutes = new ArrayList<>();
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

    public List<BranchRoute> getBranchRoutes() {
        return branchRoutes;
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
            for (int i = 0; i < b.members.size(); i++) {
                Person p = b.members.get(i);
                p.x = bx + i * (BOX_W + SPOUSE_GAP);
                p.generation = b.generation;
            }
        }
        int layoutRight = (int) Math.round(maxX - minX) + PAD;
        List<BranchRoutePlan> plans = buildBranchRoutePlans(maxGen, layoutRight);
        int[] laneCounts = new int[maxGen];
        int[] exitLaneCounts = new int[maxGen];
        Map<Integer, List<BranchRoutePlan>> plansByGap = new HashMap<>();
        Map<Integer, List<BranchRoutePlan>> exitsByGap = new HashMap<>();
        for (BranchRoutePlan plan : plans) {
            plansByGap.computeIfAbsent(plan.childGeneration - 1, key -> new ArrayList<>()).add(plan);
            if (plan.routeX >= 0) {
                exitsByGap.computeIfAbsent(plan.parentGeneration, key -> new ArrayList<>()).add(plan);
            }
        }
        for (Map.Entry<Integer, List<BranchRoutePlan>> entry : plansByGap.entrySet()) {
            List<BranchRoutePlan> gapPlans = entry.getValue();
            List<Integer> lanes = assignBranchLanes(gapPlans.stream()
                    .map(plan -> new BranchSpan(plan.minX, plan.maxX)).toList());
            for (int i = 0; i < gapPlans.size(); i++) {
                BranchRoutePlan plan = gapPlans.get(i);
                plan.lane = lanes.get(i);
                laneCounts[entry.getKey()] = Math.max(laneCounts[entry.getKey()], plan.lane + 1);
            }
        }
        for (Map.Entry<Integer, List<BranchRoutePlan>> entry : exitsByGap.entrySet()) {
            List<BranchRoutePlan> gapPlans = entry.getValue();
            List<Integer> lanes = assignBranchLanes(gapPlans.stream()
                    .map(plan -> new BranchSpan(Math.min(plan.anchorX, plan.routeX),
                            Math.max(plan.anchorX, plan.routeX))).toList());
            for (int i = 0; i < gapPlans.size(); i++) {
                BranchRoutePlan plan = gapPlans.get(i);
                plan.exitLane = lanes.get(i);
                exitLaneCounts[entry.getKey()] = Math.max(exitLaneCounts[entry.getKey()], plan.exitLane + 1);
            }
        }
        int[] generationY = new int[maxGen + 1];
        generationY[0] = PAD;
        for (int generation = 1; generation <= maxGen; generation++) {
            generationY[generation] = generationY[generation - 1] + BOX_H + GAP_Y
                    + (laneCounts[generation - 1] + exitLaneCounts[generation - 1]) * ROUTE_LANE_GAP;
        }
        for (Person p : people) {
            p.y = generationY[p.generation];
        }
        buildBranchRoutes(plans, exitLaneCounts, generationY);
        int routeRight = plans.stream().mapToInt(plan -> plan.routeX).max().orElse(layoutRight);
        width = Math.max((int) Math.round(maxX - minX) + PAD * 2, routeRight + PAD);
        height = generationY[maxGen] + BOX_H + PAD;
    }

    private List<BranchRoutePlan> buildBranchRoutePlans(int maxGen, int layoutRight) {
        List<BranchRoutePlan> plans = new ArrayList<>();
        Map<Person, List<BranchRoutePlan>> incoming = new HashMap<>();
        int outerTrack = 0;
        for (Family family : families) {
            int parentGeneration = Math.max(family.husband == null ? 0 : family.husband.generation,
                    family.wife == null ? 0 : family.wife.generation);
            Map<Integer, List<Person>> childrenByGeneration = new TreeMap<>();
            for (Person child : family.children) {
                if (child.generation > parentGeneration && child.generation <= maxGen) {
                    childrenByGeneration.computeIfAbsent(child.generation, key -> new ArrayList<>()).add(child);
                }
            }
            for (Map.Entry<Integer, List<Person>> entry : childrenByGeneration.entrySet()) {
                BranchRoutePlan plan = new BranchRoutePlan(family, parentGeneration, entry.getKey(), entry.getValue(),
                        branchAnchorX(family));
                if (plan.childGeneration > parentGeneration + 1) {
                    plan.routeX = layoutRight + ++outerTrack * ROUTE_LANE_GAP;
                }
                plan.minX = plan.routeX >= 0 ? plan.routeX : plan.anchorX;
                plan.maxX = plan.minX;
                plans.add(plan);
                for (Person child : plan.children) {
                    incoming.computeIfAbsent(child, key -> new ArrayList<>()).add(plan);
                }
            }
        }
        for (Map.Entry<Person, List<BranchRoutePlan>> entry : incoming.entrySet()) {
            List<BranchRoutePlan> childPlans = entry.getValue();
            Set<Integer> used = new HashSet<>();
            for (int i = 0; i < childPlans.size(); i++) {
                BranchRoutePlan plan = childPlans.get(i);
                int desired = (i + 1) * BOX_W / (childPlans.size() + 1);
                int offset = childPortOffset(entry.getKey().x, desired, plans, used);
                used.add(offset);
                int portX = entry.getKey().x + offset;
                plan.childPorts.put(entry.getKey(), portX);
                plan.minX = Math.min(plan.minX, portX);
                plan.maxX = Math.max(plan.maxX, portX);
            }
        }
        return plans;
    }

    private void buildBranchRoutes(List<BranchRoutePlan> plans, int[] exitLaneCounts, int[] generationY) {
        for (BranchRoutePlan plan : plans) {
            int parentY = branchAnchorY(plan.family);
            int busY = generationY[plan.childGeneration - 1] + BOX_H + GAP_Y / 2
                    + exitLaneCounts[plan.childGeneration - 1] * ROUTE_LANE_GAP
                    + plan.lane * ROUTE_LANE_GAP;
            int exitY = plan.routeX < 0 ? 0 : generationY[plan.parentGeneration] + BOX_H + 4
                    + plan.exitLane * ROUTE_LANE_GAP;
            List<ChildConnection> children = plan.children.stream()
                    .map(child -> new ChildConnection(child, plan.childPorts.get(child))).toList();
            branchRoutes.add(new BranchRoute(plan.family, plan.anchorX, parentY, busY, plan.routeX, exitY, children));
        }
    }

    private static int childPortOffset(int boxX, int desired, List<BranchRoutePlan> plans, Set<Integer> used) {
        Set<Integer> sourcePorts = new HashSet<>();
        for (BranchRoutePlan plan : plans) {
            sourcePorts.add(plan.anchorX);
        }
        for (int distance = 0; distance < BOX_W; distance++) {
            int right = desired + distance;
            if (right > 0 && right < BOX_W && !used.contains(right) && !sourcePorts.contains(boxX + right)) {
                return right;
            }
            int left = desired - distance;
            if (left > 0 && left < BOX_W && !used.contains(left) && !sourcePorts.contains(boxX + left)) {
                return left;
            }
        }
        return desired;
    }

    static List<Integer> assignBranchLanes(List<BranchSpan> spans) {
        List<List<BranchSpan>> lanes = new ArrayList<>();
        List<Integer> assignments = new ArrayList<>();
        for (BranchSpan span : spans) {
            int lane = 0;
            while (lane < lanes.size() && lanes.get(lane).stream().anyMatch(span::overlaps)) {
                lane++;
            }
            if (lane == lanes.size()) {
                lanes.add(new ArrayList<>());
            }
            lanes.get(lane).add(span);
            assignments.add(lane);
        }
        return assignments;
    }

    private static int branchAnchorX(Family family) {
        Person h = family.husband;
        Person w = family.wife;
        if (h != null && w != null) {
            Person left = h.x <= w.x ? h : w;
            Person right = left == h ? w : h;
            if (left.y == right.y && right.x - left.x == BOX_W + SPOUSE_GAP) {
                return left.x + BOX_W + SPOUSE_GAP / 2;
            }
            return (h.x + w.x) / 2 + BOX_W / 2;
        }
        Person only = h != null ? h : w;
        return only.x + BOX_W / 2;
    }

    private static int branchAnchorY(Family family) {
        Person h = family.husband;
        Person w = family.wife;
        if (h != null && w != null) {
            Person left = h.x <= w.x ? h : w;
            Person right = left == h ? w : h;
            if (left.y == right.y && right.x - left.x == BOX_W + SPOUSE_GAP) {
                return left.y + BOX_H / 2;
            }
            return Math.max(h.y, w.y) + BOX_H + 8;
        }
        Person only = h != null ? h : w;
        return only.y + BOX_H;
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
