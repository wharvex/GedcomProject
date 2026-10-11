package com.wharvex.gedcom;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FamilyTreeViewerSearchTest {

    @Test
    void findsCaseInsensitiveNonOverlappingOccurrences() {
        assertEquals(List.of(0, 5), FamilyTreeViewer.findOccurrences("Jane jane", "JANE"));
    }

    @Test
    void returnsNoOccurrencesForEmptyOrAbsentQuery() {
        assertEquals(List.of(), FamilyTreeViewer.findOccurrences("Jane", ""));
        assertEquals(List.of(), FamilyTreeViewer.findOccurrences("Jane", "Doe"));
    }
}
