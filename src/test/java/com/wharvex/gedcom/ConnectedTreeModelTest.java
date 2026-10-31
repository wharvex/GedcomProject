package com.wharvex.gedcom;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectedTreeModelTest {

    @Test
    void placesSpousesSideBySideWithTheirParentsAboveAndChildBelow() throws IOException {
        String ged = String.join("\n",
                "0 HEAD", "0 @I1@ INDI", "1 NAME Al /A/", "0 @I2@ INDI", "1 NAME Bo /B/",
                "0 @I3@ INDI", "1 NAME Cy /C/", "0 @I4@ INDI", "1 NAME Di /D/",
                "0 @I5@ INDI", "1 NAME Ed /E/", "0 @I6@ INDI", "1 NAME Fay /F/",
                "0 @F1@ FAM", "1 HUSB @I1@", "1 WIFE @I2@", "1 CHIL @I3@",
                "0 @F2@ FAM", "1 HUSB @I5@", "1 WIFE @I6@", "1 CHIL @I4@",
                "0 @F3@ FAM", "1 HUSB @I3@", "1 WIFE @I4@", "0 TRLR", "");
        Path f = Files.createTempFile("conn", ".ged");
        try {
            Files.writeString(f, ged);
            ConnectedTreeModel m = new ConnectedTreeModel(new GedcomReader().read(f));
            ConnectedTreeModel.Person cy = byName(m, "Cy");
            ConnectedTreeModel.Person di = byName(m, "Di");
            assertEquals(cy.y, di.y);
            assertEquals(ConnectedTreeModel.BOX_W + ConnectedTreeModel.SPOUSE_GAP, di.x - cy.x);
            assertTrue(byName(m, "Al").y < cy.y);
            assertTrue(byName(m, "Ed").y < di.y);
            assertTrue(byName(m, "Al").x < byName(m, "Ed").x);
        } finally {
            Files.deleteIfExists(f);
        }
    }

    @Test
    void ordersChildrenWithTheirParentsAndKeepsTheirBoxesSeparated() throws IOException {
        String ged = String.join("\n",
                "0 HEAD", "0 @I1@ INDI", "1 NAME Al /A/", "0 @I2@ INDI", "1 NAME Bo /B/",
                "0 @I3@ INDI", "1 NAME Cy /C/", "0 @I4@ INDI", "1 NAME Di /D/",
                "0 @I5@ INDI", "1 NAME Ed /E/", "0 @I6@ INDI", "1 NAME Fay /F/",
                "0 @F1@ FAM", "1 HUSB @I1@", "1 WIFE @I2@", "1 CHIL @I5@",
                "0 @F2@ FAM", "1 HUSB @I3@", "1 WIFE @I4@", "1 CHIL @I6@", "0 TRLR", "");
        Path f = Files.createTempFile("conn-order", ".ged");
        try {
            Files.writeString(f, ged);
            ConnectedTreeModel m = new ConnectedTreeModel(new GedcomReader().read(f));
            ConnectedTreeModel.Person ed = byName(m, "Ed");
            ConnectedTreeModel.Person fay = byName(m, "Fay");

            assertTrue(ed.x < fay.x);
            assertTrue(fay.x - (ed.x + ConnectedTreeModel.BOX_W) >= ConnectedTreeModel.GAP_X);
        } finally {
            Files.deleteIfExists(f);
        }
    }

    @Test
    void familyConnectorColorsAreStableAndDistinct() {
        Color firstColor = FamilyTreeViewer.familyLineColor(0);

        assertEquals(firstColor, FamilyTreeViewer.familyLineColor(0));
        assertNotEquals(firstColor, FamilyTreeViewer.familyLineColor(1));
    }

    private static ConnectedTreeModel.Person byName(ConnectedTreeModel m, String first) {
        return m.getPeople().stream().filter(p -> p.name.startsWith(first)).findFirst().orElseThrow();
    }
}
