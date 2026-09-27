# GEDCOM Project

## Initial Prompt

I want to create a lexer and parser for gedcom files. I want to use Java and Gradle. I do not want the program to use any Java "Records".

The lexer and parser should be made from scratch. The lexer should search through every character in the input file and create "tokens" for them in a queue (FIFO).

The parser should then look in that queue of tokens and create different kinds of "nodes" for tokens or groups of tokens that represent larger structures in the file.

The parser should use "recursive descent".
