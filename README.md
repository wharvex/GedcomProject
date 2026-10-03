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

## Roots explanation

**Roots are people who are not children in any family and who are not married into the tree.**

A person becomes a **root** (starting point for the tree layout) if:

1. **They are NOT a child** — checked via `isChild.containsKey(p.id)`. This eliminates anyone who appears as a CHIL record in a FAM.

2. **They are NOT married into the tree** — this is the trickier part. A person is marked as "married in" if any of their spouses meet one of these conditions:
   - The spouse already has children (meaning they're an established parent in the tree)
   - The spouse has children and the person doesn't (asymmetric: the spouse is rooted, this person married them)
   - Both are non-children AND the spouse's xref ID sorts earlier alphabetically AND the person has no children (tie-breaker: prefer the alphabetically-first spouse as the root)

**In practical terms:**
- If you have a couple where one is a founder/ancestor and the other married in, only the original founder becomes a root.
- The spouse who married into that family line isn't shown as a separate root—they appear alongside their partner in the tree.
- This prevents duplicate root nodes for married couples and keeps the visual hierarchy clean (one ancestral line, not two parallel ones).

