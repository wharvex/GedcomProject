package com.wharvex.gedcom;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectedTreeModelTest {
    private record VerticalSegment(int x, int y1, int y2, String source) {
    }

    private record BranchStem(ConnectedTreeModel.Family family, int x, int y) {
    }

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

    @Test
    void routesNonOverlappingFamilyBranchesOnSharedLane() throws IOException {
        String ged = String.join("\n",
                "0 HEAD", "0 @I1@ INDI", "1 NAME Al /A/", "0 @I2@ INDI", "1 NAME Bo /B/",
                "0 @I3@ INDI", "1 NAME Cy /C/", "0 @I4@ INDI", "1 NAME Di /D/",
                "0 @I5@ INDI", "1 NAME Ed /E/",
                "0 @F1@ FAM", "1 HUSB @I1@", "1 WIFE @I2@", "1 CHIL @I5@",
                "0 @F2@ FAM", "1 HUSB @I3@", "1 WIFE @I4@", "1 CHIL @I5@", "0 TRLR", "");
        Path f = Files.createTempFile("conn-routes", ".ged");
        try {
            Files.writeString(f, ged);
            ConnectedTreeModel model = new ConnectedTreeModel(new GedcomReader().read(f));
            List<ConnectedTreeModel.BranchRoute> routes = model.getBranchRoutes();

            assertEquals(2, routes.size());
            assertNotEquals(routes.get(0).children.get(0).x, routes.get(1).children.get(0).x);
            assertTrue(routes.stream().allMatch(route -> route.busY < route.children.get(0).person.y));
            int firstMinX = Math.min(routes.get(0).anchorX, routes.get(0).children.get(0).x);
            int firstMaxX = Math.max(routes.get(0).anchorX, routes.get(0).children.get(0).x);
            int secondMinX = Math.min(routes.get(1).anchorX, routes.get(1).children.get(0).x);
            int secondMaxX = Math.max(routes.get(1).anchorX, routes.get(1).children.get(0).x);
            assertTrue(firstMaxX < secondMinX || secondMaxX < firstMinX);
            assertEquals(routes.get(0).busY, routes.get(1).busY);
        } finally {
            Files.deleteIfExists(f);
        }
    }

    @Test
    void assignsOverlappingBranchSpansToDifferentLanes() {
        List<Integer> lanes = ConnectedTreeModel.assignBranchLanes(List.of(
                new ConnectedTreeModel.BranchSpan(0, 100),
                new ConnectedTreeModel.BranchSpan(50, 150),
                new ConnectedTreeModel.BranchSpan(200, 300)));

        assertEquals(List.of(0, 1, 0), lanes);
    }

    @Test
    void sampleFamilyTreeHasNoOverlappingBranchSegments() throws IOException {
        ConnectedTreeModel model = new ConnectedTreeModel(
                new GedcomReader().read(Path.of("fam-tree-example.ged")));
        List<ConnectedTreeModel.BranchRoute> routes = model.getBranchRoutes();
        List<VerticalSegment> verticalSegments = new java.util.ArrayList<>();

        for (int i = 0; i < routes.size(); i++) {
            ConnectedTreeModel.BranchRoute first = routes.get(i);
            if (first.routeX >= 0) {
                verticalSegments.add(new VerticalSegment(first.routeX, first.exitY, first.busY, "route " + i + " rail"));
            }
            for (ConnectedTreeModel.ChildConnection child : first.children) {
                verticalSegments.add(new VerticalSegment(child.x, first.busY, child.person.y,
                        "route " + i + " child " + child.person.id));
            }
            int firstBusX = first.routeX >= 0 ? first.routeX : first.anchorX;
            int firstMinX = first.children.stream().mapToInt(child -> child.x).min().orElse(firstBusX);
            int firstMaxX = first.children.stream().mapToInt(child -> child.x).max().orElse(firstBusX);
            firstMinX = Math.min(firstMinX, firstBusX);
            firstMaxX = Math.max(firstMaxX, firstBusX);
            for (int j = i + 1; j < routes.size(); j++) {
                ConnectedTreeModel.BranchRoute second = routes.get(j);
                if (first.busY == second.busY) {
                    int secondBusX = second.routeX >= 0 ? second.routeX : second.anchorX;
                    int secondMinX = second.children.stream().mapToInt(child -> child.x).min().orElse(secondBusX);
                    int secondMaxX = second.children.stream().mapToInt(child -> child.x).max().orElse(secondBusX);
                    secondMinX = Math.min(secondMinX, secondBusX);
                    secondMaxX = Math.max(secondMaxX, secondBusX);
                    assertTrue(firstMaxX < secondMinX || secondMaxX < firstMinX);
                }
                if (first.routeX >= 0 && second.routeX >= 0 && first.exitY == second.exitY) {
                    int firstExitMin = Math.min(first.anchorX, first.routeX);
                    int firstExitMax = Math.max(first.anchorX, first.routeX);
                    int secondExitMin = Math.min(second.anchorX, second.routeX);
                    int secondExitMax = Math.max(second.anchorX, second.routeX);
                    assertTrue(firstExitMax < secondExitMin || secondExitMax < firstExitMin);
                }
            }
        }
        Map<BranchStem, Integer> stemEnds = new LinkedHashMap<>();
        for (ConnectedTreeModel.BranchRoute route : routes) {
            BranchStem stem = new BranchStem(route.family, route.anchorX, route.anchorY);
            stemEnds.merge(stem, route.routeX >= 0 ? route.exitY : route.busY, Math::max);
        }
        for (Map.Entry<BranchStem, Integer> entry : stemEnds.entrySet()) {
            BranchStem stem = entry.getKey();
            verticalSegments.add(new VerticalSegment(stem.x(), stem.y(), entry.getValue(), "family parent stem"));
        }
        for (int i = 0; i < verticalSegments.size(); i++) {
            VerticalSegment first = verticalSegments.get(i);
            for (int j = i + 1; j < verticalSegments.size(); j++) {
                VerticalSegment second = verticalSegments.get(j);
                boolean overlap = first.x == second.x
                        && Math.max(Math.min(first.y1, first.y2), Math.min(second.y1, second.y2))
                        < Math.min(Math.max(first.y1, first.y2), Math.max(second.y1, second.y2));
                assertTrue(!overlap, "Overlapping vertical routes at x=" + first.x + ": "
                        + first.source + " " + first.y1 + "-" + first.y2 + " vs "
                        + second.source + " " + second.y1 + "-" + second.y2);
            }
        }
    }

    private static ConnectedTreeModel.Person byName(ConnectedTreeModel m, String first) {
        return m.getPeople().stream().filter(p -> p.name.startsWith(first)).findFirst().orElseThrow();
    }
}
