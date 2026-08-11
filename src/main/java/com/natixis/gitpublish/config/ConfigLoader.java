package com.natixis.gitpublish.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigLoader {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public AppConfig load(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException(
                    "Configuration file not found: %s%nCreate it or pass --config <file>."
                            .formatted(path.toAbsolutePath())
            );
        }

        try {
            return mapper.readValue(path.toFile(), AppConfig.class);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Could not read configuration file '%s': %s"
                            .formatted(path.toAbsolutePath(), e.getMessage()),
                    e
            );
        }
    }
}
