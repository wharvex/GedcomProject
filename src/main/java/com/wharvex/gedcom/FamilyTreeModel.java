package com.wharvex.gedcom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds a descendant family tree (individuals, spouses, children) from a GEDCOM document. */
public class FamilyTreeModel {
    /** A person together with the spouses and children of their marriages. */
    public static class Person {
        public final String id;
        public final String name;
        public final String life;
        public final List<Person> spouses = new ArrayList<>();
        public final List<Person> children = new ArrayList<>();

        Person(String id, String name, String life) {
            this.id = id;
            this.name = name;
            this.life = life;
        }
    }

    private final List<Person> roots = new ArrayList<>();

    public FamilyTreeModel(GedcomDocument doc) {
        Map<String, Person> people = new LinkedHashMap<>();
        Map<String, GedcomNode> families = new LinkedHashMap<>();
        for (GedcomNode n : doc.getRootNodes()) {
            if ("INDI".equals(n.getTag()) && n.getXrefId() != null) {
                people.put(n.getXrefId(), new Person(n.getXrefId(), nameOf(n), lifeOf(n)));
            } else if ("FAM".equals(n.getTag()) && n.getXrefId() != null) {
                families.put(n.getXrefId(), n);
            }
        }
        Map<String, Boolean> isChild = new HashMap<>();
        for (GedcomNode fam : families.values()) {
            Person husb = null;
            Person wife = null;
            List<Person> kids = new ArrayList<>();
            for (GedcomNode c : fam.getChildren()) {
                Person p = people.get(c.getValue());
                if (p == null) continue;
                switch (c.getTag()) {
                    case "HUSB" -> husb = p;
                    case "WIFE" -> wife = p;
                    case "CHIL" -> kids.add(p);
                    default -> { }
                }
            }
            if (husb != null && wife != null) {
                husb.spouses.add(wife);
                wife.spouses.add(husb);
            }
            Person parent = husb != null ? husb : wife;
            if (parent != null) {
                for (Person k : kids) {
                    parent.children.add(k);
                    isChild.put(k.id, true);
                }
            }
        }
        for (Person p : people.values()) {
            if (isChild.containsKey(p.id)) continue;
            // skip spouses who marry into the tree; they are shown alongside their partner
            boolean marriedIn = false;
            for (Person s : p.spouses) {
                if (s.children.contains(p) || (!s.children.isEmpty() && p.children.isEmpty())
                        || (s.id.compareTo(p.id) < 0 && !isChild.containsKey(s.id) && p.children.isEmpty())) {
                    marriedIn = true;
                }
            }
            if (!marriedIn) roots.add(p);
        }
    }

    public List<Person> getRoots() {
        return roots;
    }

    private static String nameOf(GedcomNode indi) {
        for (GedcomNode c : indi.getChildren()) {
            if ("NAME".equals(c.getTag()) && c.getValue() != null) {
                return c.getValue().replace("/", "").trim();
            }
        }
        return "(unknown)";
    }

    private static String lifeOf(GedcomNode indi) {
        String b = year(indi, "BIRT");
        String d = year(indi, "DEAT");
        if (b.isEmpty() && d.isEmpty()) return "";
        return b + " - " + d;
    }

    private static String year(GedcomNode indi, String tag) {
        for (GedcomNode c : indi.getChildren()) {
            if (tag.equals(c.getTag())) {
                for (GedcomNode g : c.getChildren()) {
                    if ("DATE".equals(g.getTag()) && g.getValue() != null) return g.getValue();
                }
            }
        }
        return "";
    }
}
