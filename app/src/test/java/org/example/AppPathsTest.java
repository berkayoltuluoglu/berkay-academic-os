package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AppPathsTest {
    @TempDir Path directory;
    @Test void installedProfilesUseUserWritableLocationsOnEachPlatform() {
        assertEquals(directory.resolve("Local/BerkayAcademicOS"), AppPaths.defaultProfile("Windows 11", Map.of("LOCALAPPDATA", directory.resolve("Local").toString()), directory));
        assertEquals(directory.resolve(".local/share/berkay-academic-os"), AppPaths.defaultProfile("Linux", Map.of(), directory));
        assertEquals(directory.resolve("Library/Application Support/BerkayAcademicOS"), AppPaths.defaultProfile("Mac OS X", Map.of(), directory));
    }
    @Test void explicitProfileOverrideDoesNotDependOnTheWorkingDirectory() {
        String previous = System.getProperty("academic.os.home");
        try { System.setProperty("academic.os.home", directory.toString()); assertEquals(directory.toAbsolutePath(), AppPaths.root()); }
        finally { if (previous == null) System.clearProperty("academic.os.home"); else System.setProperty("academic.os.home", previous); }
    }
    @Test void packagedConfigUsesLocalDocumentsAndContainsNoPersonalMountPaths() throws Exception {
        String previous = System.getProperty("academic.os.packaged");
        try {
            System.setProperty("academic.os.packaged", "true"); var config = new ConfigLoader().load(directory, "Windows 11");
            var documents = config.getModules().stream().filter(module -> "documents".equals(module.getId())).findFirst().orElseThrow();
            assertEquals("data/documents", documents.getTarget()); assertFalse(Files.readString(directory.resolve("config/app-config.json")).contains("/home/berkay/"));
            assertTrue(config.getModules().stream().filter(m -> Set.of("scripts", "uart").contains(m.getId())).noneMatch(ModuleItem::isEnabled));
        } finally { if (previous == null) System.clearProperty("academic.os.packaged"); else System.setProperty("academic.os.packaged", previous); }
    }

    @Test void packagedLinuxProfileKeepsItsSupportedTools() throws Exception {
        String previous = System.getProperty("academic.os.packaged");
        try {
            System.setProperty("academic.os.packaged", "true");
            var config = new ConfigLoader().load(directory, "Linux");
            var tools = config.getModules().stream().filter(m -> Set.of("scripts", "uart").contains(m.getId())).toList();
            assertEquals(2, tools.size());
            assertTrue(tools.stream().allMatch(ModuleItem::isEnabled));
            assertFalse(Files.readString(directory.resolve("config/app-config.json")).contains("/home/berkay/"));
        } finally { if (previous == null) System.clearProperty("academic.os.packaged"); else System.setProperty("academic.os.packaged", previous); }
    }
}
