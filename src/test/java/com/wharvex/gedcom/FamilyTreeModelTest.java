package com.wharvex.gedcom;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FamilyTreeModelTest {
    private GedcomDocument document() {
        String gedcom = """
                0 @I1@ INDI
                1 NAME Parent /One/
                0 @I2@ INDI
                1 NAME Parent /Two/
                0 @I3@ INDI
                1 NAME Child /One/
                0 @F1@ FAM
                1 HUSB @I1@
                1 WIFE @I2@
                1 CHIL @I3@
                """;
        return new GedcomParser().parse(new GedcomLexer().tokenize(gedcom));
    }

    @Test
    void defaultModeKeepsChildrenOnTheHusbandLine() {
        FamilyTreeModel model = new FamilyTreeModel(document());

        assertEquals(List.of("@I1@"), rootIds(model));
        assertEquals(List.of("@I3@"), childIds(findPerson(model.getRoots().get(0), "@I1@")));
    }

    @Test
    void maximalConnectionsIncludesBothParentsAndUsesOneRootForTheCouple() {
        FamilyTreeModel model = new FamilyTreeModel(document(), true);

        assertEquals(List.of("@I1@"), rootIds(model));
        assertEquals(List.of("@I3@"), childIds(findPerson(model.getRoots().get(0), "@I1@")));
        assertEquals(List.of("@I3@"), childIds(findPerson(model.getRoots().get(0), "@I2@")));
    }

    private static List<String> rootIds(FamilyTreeModel model) {
        return model.getRoots().stream().map(person -> person.id).toList();
    }

    private static List<String> childIds(FamilyTreeModel.Person person) {
        return person.children.stream().map(child -> child.id).toList();
    }

    private static FamilyTreeModel.Person findPerson(FamilyTreeModel.Person root, String id) {
        if (root.id.equals(id))
            return root;
        for (FamilyTreeModel.Person spouse : root.spouses) {
            if (spouse.id.equals(id))
                return spouse;
        }
        throw new AssertionError("Person not found: " + id);
    }
}
