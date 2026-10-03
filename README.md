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

## GUI viewer

```bash
gradle -q run --args="fam-tree-example.ged"
```

Or run `com.wharvex.gedcom.GedcomViewer` and use the Open button.

### Family tree view

Pass `--view tree` to draw the file as a family tree chart instead of the raw
record tree (`--view raw`, the default):

```bash
gradle -q run --args="--view tree fam-tree-example.ged"
```
