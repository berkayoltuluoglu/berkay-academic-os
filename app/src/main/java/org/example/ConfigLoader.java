package org.example;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigLoader {

    public AppConfig load() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        InputStream inputStream = getClass().getResourceAsStream("/config.json");

        if (inputStream == null) {
            throw new IllegalStateException("config.json bulunamadı");
        }

        try (inputStream) { return mapper.readValue(inputStream, AppConfig.class); }
    }

    public AppConfig load(Path root) throws Exception {
        return load(root, System.getProperty("os.name"));
    }

    AppConfig load(Path root, String operatingSystem) throws Exception {
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Path local = root.resolve("config/app-config.json");
        if (Files.exists(local)) {
            AppConfig config=mapper.readValue(FinanceStorageService.readLimited(local), AppConfig.class);
            // Existing profiles keep their custom configuration while gaining the new core screen.
            if(config.getModules()!=null && config.getModules().stream().noneMatch(m -> "sync".equals(m.getId()))) {
                var modules=new java.util.ArrayList<>(config.getModules());
                load().getModules().stream().filter(m -> "sync".equals(m.getId())).findFirst().ifPresent(modules::add);config.setModules(modules);
            }
            return config;
        }
        if (!Boolean.getBoolean("academic.os.packaged")) return load();
        try (InputStream input = getClass().getResourceAsStream("/config.json")) {
            var tree = mapper.readTree(input);
            ((com.fasterxml.jackson.databind.node.ObjectNode) tree.path("integrations").path("googleDrive")).put("enabled", false);
            for (var module : tree.path("modules")) {
                if ("documents".equals(module.path("id").asText())) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) module).put("target", "data/documents");
                    ((com.fasterxml.jackson.databind.node.ObjectNode) module.path("explorerOptions")).put("defaultRoot", "data/documents");
                    ((com.fasterxml.jackson.databind.node.ObjectNode) module).putArray("favorites")
                            .addObject().put("name", "Belgelerim").put("path", "data/documents");
                }
                if (operatingSystem.toLowerCase(java.util.Locale.ROOT).contains("win")
                        && ("scripts".equals(module.path("id").asText()) || "uart".equals(module.path("id").asText())))
                    ((com.fasterxml.jackson.databind.node.ObjectNode) module).put("enabled", false);
            }
            AtomicFileWriter.write(local, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(tree));
            return mapper.treeToValue(tree, AppConfig.class);
        }
    }
}
