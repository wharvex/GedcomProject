# GEDCOM Project

This project provides a lexer, parser, viewer, and roots printer for GEDCOM 5.5.1 files.

## Build and Test

```bash
./gradlew test
```

On Windows PowerShell, use the Windows wrapper launcher instead:

```powershell
.\gradlew.bat test
```

## Lexer and Parser

### Usage Example

```java
GedcomReader reader = new GedcomReader();
GedcomDocument document = reader.read(Path.of("fam-tree-example.ged"));
```

## Viewer

### Usage Example (Raw)

```bash
./gradlew -q run --args="fam-tree-example.ged"
```

### Usage Example (Tree)

```bash
./gradlew -q run --args="--view tree fam-tree-example.ged"
```

### Usage Example (Maximal Connections)

```bash
./gradlew -q run --args="--view maximal fam-tree-example.ged"
```

This view connects each child to both recorded parents, producing a more unified
family tree than the paternal-line tree view.

## Roots Printer

### Usage Example

```bash
./gradlew -q run --args="--view roots fam-tree-example.ged"
```

In Windows PowerShell, replace `./gradlew` in these commands with
`.\gradlew.bat`. For example, to open the tree viewer:

```powershell
.\gradlew.bat -q run --args="--view tree fam-tree-example.ged"
```

### Context

**Roots are people who are not children in any family and who are not married into the tree.**

A person becomes a **root** (starting point for the tree layout) if:

1. **They are NOT a child** — eliminates anyone who appears as a CHIL record in a FAM.

2. **They are NOT married into the tree** — a person is "married in" if any of their spouses meets one of the following conditions (checked in this order):
   - The person is a child of their own spouse.
   - The spouse has children and the person doesn't.
   - The person doesn't have children, the spouse is not themselves a child, and the spouse's xref ID sorts lexicographically before theirs.
     - The "xref ID" part of this condition is essentially an arbitrary tie-breaker for childless couples, so only one member of the couple ends up as a root.
