package com.wharvex.gedcom;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Queue;

public class GedcomReader {
    private final GedcomLexer lexer;
    private final GedcomParser parser;

    public GedcomReader() {
        this.lexer = new GedcomLexer();
        this.parser = new GedcomParser();
    }

    public GedcomDocument read(Path path) throws IOException {
        Queue<GedcomToken> tokens = lexer.tokenize(path);
        return parser.parse(tokens);
    }

    public GedcomDocument read(Path path, Charset charset) throws IOException {
        Queue<GedcomToken> tokens = lexer.tokenize(path, charset);
        return parser.parse(tokens);
    }
}
