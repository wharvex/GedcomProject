package com.wharvex.gedcom;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;

public class GedcomLexer {

    public Queue<GedcomToken> tokenize(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        Charset detectedCharset = detectCharset(bytes);
        return tokenize(new String(bytes, detectedCharset));
    }

    public Queue<GedcomToken> tokenize(Path path, Charset charset) throws IOException {
        return tokenize(Files.readString(path, charset));
    }

    public Queue<GedcomToken> tokenize(String input) {
        Queue<GedcomToken> tokens = new ArrayDeque<>();
        StringBuilder currentLine = new StringBuilder();
        int lineNumber = 1;

        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (current == '\r') {
                continue;
            }

            if (current == '\n') {
                tokenizeLine(currentLine.toString(), lineNumber, tokens);
                tokens.add(new GedcomToken(GedcomTokenType.NEWLINE, "\\n", lineNumber));
                currentLine.setLength(0);
                lineNumber++;
            } else {
                currentLine.append(current);
            }
        }

        if (!currentLine.isEmpty()) {
            tokenizeLine(currentLine.toString(), lineNumber, tokens);
            tokens.add(new GedcomToken(GedcomTokenType.NEWLINE, "\\n", lineNumber));
        }

        tokens.add(new GedcomToken(GedcomTokenType.EOF, "", lineNumber));
        return tokens;
    }

    private void tokenizeLine(String line, int lineNumber, Queue<GedcomToken> tokens) {
        if (line.isBlank()) {
            return;
        }

        int cursor = 0;
        int levelStart = cursor;

        while (cursor < line.length() && Character.isDigit(line.charAt(cursor))) {
            cursor++;
        }

        if (cursor == levelStart) {
            throw new IllegalArgumentException("Missing level at line " + lineNumber);
        }

        tokens.add(new GedcomToken(GedcomTokenType.LEVEL, line.substring(levelStart, cursor), lineNumber));
        cursor = skipSpaces(line, cursor);

        String firstWord = readWord(line, cursor);
        if (firstWord.isEmpty()) {
            throw new IllegalArgumentException("Missing tag/xref at line " + lineNumber);
        }

        cursor += firstWord.length();
        if (isXref(firstWord)) {
            tokens.add(new GedcomToken(GedcomTokenType.XREF_ID, firstWord, lineNumber));
            cursor = skipSpaces(line, cursor);

            String tag = readWord(line, cursor);
            if (tag.isEmpty()) {
                throw new IllegalArgumentException("Missing tag at line " + lineNumber);
            }
            tokens.add(new GedcomToken(GedcomTokenType.TAG, tag, lineNumber));
            cursor += tag.length();
        } else {
            tokens.add(new GedcomToken(GedcomTokenType.TAG, firstWord, lineNumber));
        }

        cursor = skipSpaces(line, cursor);
        if (cursor < line.length()) {
            tokens.add(new GedcomToken(GedcomTokenType.VALUE, line.substring(cursor), lineNumber));
        }
    }

    private int skipSpaces(String line, int cursor) {
        while (cursor < line.length() && line.charAt(cursor) == ' ') {
            cursor++;
        }
        return cursor;
    }

    private String readWord(String line, int cursor) {
        int start = cursor;
        while (cursor < line.length() && line.charAt(cursor) != ' ') {
            cursor++;
        }
        return line.substring(start, cursor);
    }

    private boolean isXref(String text) {
        return text.length() >= 3 && text.charAt(0) == '@' && text.charAt(text.length() - 1) == '@';
    }

    private Charset detectCharset(byte[] bytes) {
        String probe = new String(bytes, StandardCharsets.ISO_8859_1);
        String[] lines = probe.split("\\R", 200);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("1 CHAR ")) {
                String declared = trimmed.substring("1 CHAR ".length()).trim().toUpperCase();
                if (declared.startsWith("UTF-8") || declared.startsWith("UTF8")) {
                    return StandardCharsets.UTF_8;
                }
                if (declared.startsWith("ASCII")) {
                    return StandardCharsets.US_ASCII;
                }
                if (declared.startsWith("ANSEL") || declared.startsWith("ANSI")) {
                    return StandardCharsets.ISO_8859_1;
                }
                if (declared.startsWith("UNICODE")) {
                    return StandardCharsets.UTF_8;
                }
            }
        }
        return StandardCharsets.UTF_8;
    }
}
