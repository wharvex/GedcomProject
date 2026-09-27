# GEDCOM Project

## Initial Prompt

Create a lexer and parser for GEDCOM (.ged) files.

Use Java and Gradle.

Do not use any Java "Records". Any time you might want to use a Record, use a Class instead.

The lexer and parser should be made from scratch (i.e. do not use a pre-written lexer or parser library or something like that, although it is okay to use libraries in general).

The lexer should search through every character in the input file and create "tokens" for them in a queue (FIFO).

The parser should then look in that queue of tokens and create different kinds of "nodes" for tokens or groups of tokens that represent larger structures in the file.

The parser should use "recursive descent".

The lexer and parser should target version 5.5.1 of the GEDCOM spec.
