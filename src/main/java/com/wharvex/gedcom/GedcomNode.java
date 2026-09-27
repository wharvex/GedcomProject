package com.wharvex.gedcom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GedcomNode {
    private final int level;
    private final String xrefId;
    private final String tag;
    private final String value;
    private final List<GedcomNode> children = new ArrayList<>();

    public GedcomNode(int level, String xrefId, String tag, String value) {
        this.level = level;
        this.xrefId = xrefId;
        this.tag = tag;
        this.value = value;
    }

    public int getLevel() {
        return level;
    }

    public String getXrefId() {
        return xrefId;
    }

    public String getTag() {
        return tag;
    }

    public String getValue() {
        return value;
    }

    public void addChild(GedcomNode child) {
        children.add(child);
    }

    public List<GedcomNode> getChildren() {
        return Collections.unmodifiableList(children);
    }
}
