package io.quarkus.registry.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.quarkus.registry.catalog.Extension;
import io.quarkus.registry.catalog.ExtensionCatalog;
import io.quarkus.registry.catalog.json.JsonCatalogMapperHelper;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.quarkus.registry.generator.MetadataExtractor.extractExtension;
import static io.quarkus.registry.generator.MetadataExtractor.extractExtensionCatalog;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class RegistryGeneratorTest {

    @Test
    void should_generate_config_descriptor(@TempDir Path tempDir) throws IOException {
        Path path = new RegistryGenerator(tempDir).generate();
        assertThat(path.resolve("io/quarkus/registry/quarkus-registry-descriptor/1.0-SNAPSHOT/quarkus-registry-descriptor-1.0-SNAPSHOT.json")).isRegularFile();
        assertThat(path.resolve("io/quarkus/registry/quarkus-registry-descriptor/1.0-SNAPSHOT/quarkus-registry-descriptor-1.0-SNAPSHOT.json.sha1")).isRegularFile();
    }

    @Test
    void should_generate_platform_descriptor_and_maven_metadata(@TempDir Path tempDir) throws IOException {
        ExtensionCatalog extensionCatalog = extractExtensionCatalog(MetadataExtractor.MAVEN_CENTRAL,
                                                                    "io.quarkus.platform",
                                                                    "quarkus-bom-quarkus-platform-descriptor",
                                                                    "2.0.3.Final",
                                                                    "2.0.3.Final");
        Path path = new RegistryGenerator(tempDir)
                .add(extensionCatalog)
                .generate();
        assertThat(path.resolve("io/quarkus/registry/quarkus-platforms/1.0-SNAPSHOT/quarkus-platforms-1.0-SNAPSHOT.json")).isRegularFile();
        assertThat(path.resolve("io/quarkus/registry/quarkus-platforms/1.0-SNAPSHOT/quarkus-platforms-1.0-SNAPSHOT.json.sha1")).isRegularFile();
        assertThat(path.resolve("io/quarkus/registry/quarkus-platforms/1.0-SNAPSHOT/maven-metadata.xml")).isRegularFile();
        assertThat(path.resolve("io/quarkus/registry/quarkus-platforms/1.0-SNAPSHOT/maven-metadata.xml.sha1")).isRegularFile();
    }

    @Test
    void should_generate_non_platform_descriptor_and_maven_metadata(@TempDir Path tempDir) throws IOException {
        ExtensionCatalog extensionCatalog = extractExtensionCatalog(MetadataExtractor.MAVEN_CENTRAL,
                                                                    "io.quarkus.platform",
                                                                    "quarkus-bom-quarkus-platform-descriptor",
                                                                    "2.0.3.Final",
                                                                    "2.0.3.Final");
        Extension extension = extractExtension(MetadataExtractor.MAVEN_CENTRAL, "io.quarkiverse.prettytime", "quarkus-prettytime", "0.1.1");
        Path path = new RegistryGenerator(tempDir)
                .add(extensionCatalog)
                .add(extension)
                .generate();
        assertThat(path.resolve("io/quarkus/registry/quarkus-non-platform-extensions/1.0-SNAPSHOT/maven-metadata.xml")).isRegularFile();
        assertThat(path.resolve("io/quarkus/registry/quarkus-non-platform-extensions/1.0-SNAPSHOT/maven-metadata.xml.sha1")).isRegularFile();
        assertThat(path.resolve("io/quarkus/registry/quarkus-non-platform-extensions/1.0-SNAPSHOT/quarkus-non-platform-extensions-1.0-SNAPSHOT-2.0.3.Final.json")).isRegularFile();
        assertThat(path.resolve("io/quarkus/registry/quarkus-non-platform-extensions/1.0-SNAPSHOT/quarkus-non-platform-extensions-1.0-SNAPSHOT-2.0.3.Final.json.sha1")).isRegularFile();
    }

    @Test
    void should_generate_platform_descriptor_and_maven_metadata_incremental(@TempDir Path tempDir) throws Exception {
        Path path = new RegistryGenerator(tempDir)
                .add(extractExtensionCatalog(MetadataExtractor.MAVEN_CENTRAL,
                                             "io.quarkus.platform",
                                             "quarkus-bom-quarkus-platform-descriptor",
                                             "2.0.2.Final",
                                             "2.0.2.Final"))
                .add(extractExtension(MetadataExtractor.MAVEN_CENTRAL, "io.quarkiverse.prettytime", "quarkus-prettytime", "0.1.0"))
                .generate();
        // Perform incremental change
        path = new RegistryGenerator(path)
                .add(extractExtensionCatalog(MetadataExtractor.MAVEN_CENTRAL,
                                             "io.quarkus.platform",
                                             "quarkus-bom-quarkus-platform-descriptor",
                                             "2.0.3.Final",
                                             "2.0.3.Final"))
                .add(extractExtensionCatalog(MetadataExtractor.MAVEN_CENTRAL,
                                             "io.quarkus.platform",
                                             "quarkus-bom-quarkus-platform-descriptor",
                                             "2.1.0.CR1",
                                             "2.1.0.CR1"))
                .add(extractExtension(MetadataExtractor.MAVEN_CENTRAL, "io.quarkiverse.prettytime", "quarkus-prettytime", "0.1.0"))
                .generate();
        fail("Missing assertions");
    }

}