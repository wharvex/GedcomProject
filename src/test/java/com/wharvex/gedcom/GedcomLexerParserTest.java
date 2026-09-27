package com.wharvex.gedcom;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GedcomLexerParserTest {

    @Test
    void lexerProducesFifoTokensForEachGedcomLine() {
        GedcomLexer lexer = new GedcomLexer();

        Queue<GedcomToken> queue = lexer.tokenize("0 @I1@ INDI\n1 NAME Jane /Doe/\n");
        List<GedcomToken> tokens = new ArrayList<>(queue);

        assertEquals(GedcomTokenType.LEVEL, tokens.get(0).getType());
        assertEquals("0", tokens.get(0).getText());
        assertEquals(GedcomTokenType.XREF_ID, tokens.get(1).getType());
        assertEquals("@I1@", tokens.get(1).getText());
        assertEquals(GedcomTokenType.TAG, tokens.get(2).getType());
        assertEquals("INDI", tokens.get(2).getText());
        assertEquals(GedcomTokenType.NEWLINE, tokens.get(3).getType());

        assertEquals(GedcomTokenType.LEVEL, tokens.get(4).getType());
        assertEquals("1", tokens.get(4).getText());
        assertEquals(GedcomTokenType.TAG, tokens.get(5).getType());
        assertEquals("NAME", tokens.get(5).getText());
        assertEquals(GedcomTokenType.VALUE, tokens.get(6).getType());
        assertEquals("Jane /Doe/", tokens.get(6).getText());
    }

    @Test
    void parserBuildsHierarchyWithRecursiveDescent() {
        GedcomLexer lexer = new GedcomLexer();
        GedcomParser parser = new GedcomParser();

        Queue<GedcomToken> queue = lexer.tokenize("0 HEAD\n1 GEDC\n2 VERS 5.5.1\n0 TRLR\n");
        GedcomDocument document = parser.parse(queue);

        assertEquals(2, document.getRootNodes().size());

        GedcomNode head = document.getRootNodes().get(0);
        assertEquals(0, head.getLevel());
        assertEquals("HEAD", head.getTag());
        assertNull(head.getXrefId());
        assertEquals(1, head.getChildren().size());

        GedcomNode gedc = head.getChildren().get(0);
        assertEquals("GEDC", gedc.getTag());
        assertEquals(1, gedc.getLevel());
        assertEquals(1, gedc.getChildren().size());
        assertEquals("VERS", gedc.getChildren().get(0).getTag());
        assertEquals("5.5.1", gedc.getChildren().get(0).getValue());

        assertEquals("TRLR", document.getRootNodes().get(1).getTag());
    }

    @Test
    void readerParsesRepositoryExampleGedcomFile() throws IOException {
        GedcomReader reader = new GedcomReader();

        GedcomDocument document = reader.read(Path.of("fam-tree-example.ged"));

        assertNotNull(document);
        assertTrue(document.getRootNodes().size() > 2);
        assertEquals("HEAD", document.getRootNodes().get(0).getTag());
        assertEquals("TRLR", document.getRootNodes().get(document.getRootNodes().size() - 1).getTag());
    }
}
