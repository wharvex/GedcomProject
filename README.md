# GEDCOM Project

This project provides a lexer and recursive-descent parser for GEDCOM 5.5.1 files.

## Build and test

```bash
gradle test
```

## Usage example

```java
GedcomReader reader = new GedcomReader();
GedcomDocument document = reader.read(Path.of("fam-tree-example.ged"));
```
