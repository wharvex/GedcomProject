package com.wharvex.gedcom;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class GedcomParser {
    private List<GedcomToken> tokens;
    private int cursor;

    public GedcomDocument parse(Queue<GedcomToken> tokenQueue) {
        this.tokens = new ArrayList<>(tokenQueue);
        this.cursor = 0;

        List<GedcomNode> roots = new ArrayList<>();
        while (!isAtEnd()) {
            if (check(GedcomTokenType.NEWLINE)) {
                advance();
                continue;
            }

            roots.add(parseNode());
        }

        return new GedcomDocument(roots);
    }

    private GedcomNode parseNode() {
        GedcomToken levelToken = consume(GedcomTokenType.LEVEL, "Expected line level");
        int level = Integer.parseInt(levelToken.getText());

        String xrefId = null;
        if (match(GedcomTokenType.XREF_ID)) {
            xrefId = previous().getText();
        }

        GedcomToken tagToken = consume(GedcomTokenType.TAG, "Expected tag");

        String value = null;
        if (match(GedcomTokenType.VALUE)) {
            value = previous().getText();
        }

        if (match(GedcomTokenType.NEWLINE)) {
            // line consumed
        } else if (!check(GedcomTokenType.EOF)) {
            throw error(peek(), "Expected end of line");
        }

        GedcomNode node = new GedcomNode(level, xrefId, tagToken.getText(), value);

        while (check(GedcomTokenType.LEVEL) && nextLevel() > level) {
            node.addChild(parseNode());
        }

        return node;
    }

    private int nextLevel() {
        return Integer.parseInt(peek().getText());
    }

    private boolean match(GedcomTokenType type) {
        if (check(type)) {
            advance();
            return true;
        }
        return false;
    }

    private GedcomToken consume(GedcomTokenType type, String message) {
        if (check(type)) {
            return advance();
        }
        throw error(peek(), message);
    }

    private IllegalArgumentException error(GedcomToken token, String message) {
        return new IllegalArgumentException(message + " at line " + token.getLine());
    }

    private boolean check(GedcomTokenType type) {
        if (isAtEnd()) {
            return type == GedcomTokenType.EOF;
        }
        return peek().getType() == type;
    }

    private GedcomToken advance() {
        if (!isAtEnd()) {
            cursor++;
        }
        return previous();
    }

    private boolean isAtEnd() {
        return peek().getType() == GedcomTokenType.EOF;
    }

    private GedcomToken peek() {
        return tokens.get(cursor);
    }

    private GedcomToken previous() {
        return tokens.get(cursor - 1);
    }
}
