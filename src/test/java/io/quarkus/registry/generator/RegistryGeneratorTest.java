package io.quarkus.registry.generator;

import java.io.IOException;
import java.nio.file.Path;

import io.quarkus.registry.catalog.ExtensionCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class RegistryGeneratorTest {

    @Test
    void should_generate_config_descriptor(@TempDir Path tempDir) throws IOException {
        Path path = new RegistryGenerator(tempDir).generate();
        assertThat(path.resolve("io/quarkus/registry/quarkus-registry-descriptor/1.0-SNAPSHOT/quarkus-registry-descriptor-1.0-SNAPSHOT.json")).isRegularFile();
        assertThat(path.resolve("io/quarkus/registry/quarkus-registry-descriptor/1.0-SNAPSHOT/quarkus-registry-descriptor-1.0-SNAPSHOT.json.sha1")).isRegularFile();
    }

    @Test
    void should_generate_platform_descriptor_and_maven_metadata(@TempDir Path tempDir) throws IOException {
        ExtensionCatalog extensionCatalog = MetadataExtractor.extractExtensionCatalog(MetadataExtractor.MAVEN_CENTRAL,
                                                                                      "io.quarkus.platform",
                                                                                      "quarkus-bom-quarkus-platform-descriptor",
                                                                                      "2.0.3.Final",
                                                                                      "2.0.3.Final");
        Path path = new RegistryGenerator(tempDir)
                .add("io.quarkus.platform", extensionCatalog)
                .generate();
        assertThat(path.resolve("io/quarkus/registry/quarkus-platforms/1.0-SNAPSHOT/quarkus-platforms-1.0-SNAPSHOT.json")).isRegularFile();
        assertThat(path.resolve("io/quarkus/registry/quarkus-platforms/1.0-SNAPSHOT/quarkus-platforms-1.0-SNAPSHOT.json.sha1")).isRegularFile();
        assertThat(path.resolve("io/quarkus/registry/quarkus-platforms/1.0-SNAPSHOT/maven-metadata.xml")).isRegularFile();
        assertThat(path.resolve("io/quarkus/registry/quarkus-platforms/1.0-SNAPSHOT/maven-metadata.xml.sha1")).isRegularFile();
    }

}