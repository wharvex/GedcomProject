package com.wharvex.gedcom;

import java.util.Collections;
import java.util.List;

public class GedcomDocument {
    private final List<GedcomNode> rootNodes;

    public GedcomDocument(List<GedcomNode> rootNodes) {
        this.rootNodes = List.copyOf(rootNodes);
    }

    public List<GedcomNode> getRootNodes() {
        return Collections.unmodifiableList(rootNodes);
    }
}
