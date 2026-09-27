package com.wharvex.gedcom;

public class GedcomToken {
    private final GedcomTokenType type;
    private final String text;
    private final int line;

    public GedcomToken(GedcomTokenType type, String text, int line) {
        this.type = type;
        this.text = text;
        this.line = line;
    }

    public GedcomTokenType getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public int getLine() {
        return line;
    }
}
