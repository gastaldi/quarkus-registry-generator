package io.quarkus.registry.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.quarkus.registry.catalog.Extension;
import io.quarkus.registry.catalog.ExtensionCatalog;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.quarkus.registry.generator.MetadataExtractor.extractExtension;
import static io.quarkus.registry.generator.MetadataExtractor.extractExtensionCatalog;
import static org.assertj.core.api.Assertions.assertThat;

class RegistryGeneratorTest {

    @Test
    void should_generate_config_descriptor(@TempDir Path tempDir) throws Exception {
        Path path = new RegistryGenerator(tempDir).generate();

        Path registryDescriptorRoot = path.resolve("io/quarkus/registry/quarkus-registry-descriptor/1.0-SNAPSHOT/");
        assertThat(registryDescriptorRoot).exists();
        assertThat(registryDescriptorRoot.resolve("maven-metadata.xml")).exists();
        assertThat(registryDescriptorRoot.resolve("maven-metadata.xml.sha1")).exists();

        String version = getMetadataVersion(registryDescriptorRoot.resolve("maven-metadata.xml"));
        assertThat(registryDescriptorRoot.resolve(String.format("quarkus-registry-descriptor-%s-1.0-SNAPSHOT.json", version))).exists();
        assertThat(registryDescriptorRoot.resolve(String.format("quarkus-registry-descriptor-%s-1.0-SNAPSHOT.json.sha1", version))).exists();
    }

    @Test
    void should_generate_platform_descriptor_and_maven_metadata(@TempDir Path tempDir) throws Exception {
        ExtensionCatalog extensionCatalog = extractExtensionCatalog(MetadataExtractor.MAVEN_CENTRAL,
                                                                    "io.quarkus.platform",
                                                                    "quarkus-bom-quarkus-platform-descriptor",
                                                                    "2.0.3.Final",
                                                                    "2.0.3.Final");
        Path path = new RegistryGenerator(tempDir)
                .add(extensionCatalog)
                .generate();
        Path platformDescriptorRoot = path.resolve("io/quarkus/registry/quarkus-platforms/1.0-SNAPSHOT");
        assertThat(platformDescriptorRoot).exists();
        assertThat(platformDescriptorRoot.resolve("maven-metadata.xml")).exists();
        assertThat(platformDescriptorRoot.resolve("maven-metadata.xml.sha1")).exists();

        String version = getMetadataVersion(platformDescriptorRoot.resolve("maven-metadata.xml"));
        assertThat(platformDescriptorRoot.resolve(String.format("quarkus-platforms-%s-1.0-SNAPSHOT.json", version))).exists();
        assertThat(platformDescriptorRoot.resolve(String.format("quarkus-platforms-%s-1.0-SNAPSHOT.json.sha1", version))).exists();
    }

    @Test
    void should_generate_non_platform_descriptor_and_maven_metadata(@TempDir Path tempDir) throws Exception {
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

        Path nonPlatformExtensionsRoot = path.resolve("io/quarkus/registry/quarkus-non-platform-extensions/1.0-SNAPSHOT");
        assertThat(nonPlatformExtensionsRoot).exists();
        assertThat(nonPlatformExtensionsRoot.resolve("maven-metadata.xml")).exists();
        assertThat(nonPlatformExtensionsRoot.resolve("maven-metadata.xml.sha1")).exists();

        String version = getMetadataVersion(nonPlatformExtensionsRoot.resolve("maven-metadata.xml"));
        assertThat(nonPlatformExtensionsRoot.resolve(String.format("quarkus-non-platform-extensions-%s-2.0.3.Final.json", version))).exists();
        assertThat(nonPlatformExtensionsRoot.resolve(String.format("quarkus-non-platform-extensions-%s-2.0.3.Final.json.sha1", version))).exists();
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
//        fail("Missing assertions");
    }


    private String getMetadataVersion(Path metadataPath) throws IOException, XmlPullParserException {
        return new MetadataXpp3Reader().read(Files.newBufferedReader(metadataPath)).getVersioning().getSnapshotVersions().get(0).getVersion();
    }

}