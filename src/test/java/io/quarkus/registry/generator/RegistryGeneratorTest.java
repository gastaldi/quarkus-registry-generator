package io.quarkus.registry.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.quarkus.registry.catalog.Extension;
import io.quarkus.registry.catalog.ExtensionCatalog;
import io.quarkus.registry.catalog.PlatformRelease;
import io.quarkus.registry.catalog.PlatformStream;
import io.quarkus.registry.catalog.json.JsonCatalogMapperHelper;
import io.quarkus.registry.catalog.json.JsonPlatformCatalog;
import io.quarkus.registry.catalog.json.JsonPlatformReleaseVersion;
import io.quarkus.registry.config.RegistriesConfig;
import io.quarkus.registry.config.RegistryConfig;
import io.quarkus.registry.config.json.JsonRegistryConfig;
import io.quarkus.registry.config.json.RegistriesConfigMapperHelper;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.quarkus.registry.generator.MetadataExtractor.extractExtension;
import static io.quarkus.registry.generator.MetadataExtractor.extractExtensionCatalog;
import static org.assertj.core.api.Assertions.assertThat;

class RegistryGeneratorTest {

    @Test
    void should_use_custom_settings(@TempDir Path tempDir) throws Exception {
        Path path = new RegistryGenerator(tempDir)
                .withGroupId("lorem.ipsum.dolor")
                .withRegistryId("foo.bar")
                .withRegistryUrl("https://bar.foo.com")
                .withQuarkusVersionExpression("1.0.0")
                .withQuarkusVersionsExclusiveProvider(true)
                .withSupportsNonPlatforms(false)
                .generate();
        Path registryDescriptorRoot = path.resolve("lorem/ipsum/dolor/quarkus-registry-descriptor/1.0-SNAPSHOT/");
        assertThat(registryDescriptorRoot).exists();
        assertThat(registryDescriptorRoot.resolve("maven-metadata.xml")).exists();
        assertThat(registryDescriptorRoot.resolve("maven-metadata.xml.sha1")).exists();

        String version = getMetadataVersion(registryDescriptorRoot.resolve("maven-metadata.xml"));
        assertThat(registryDescriptorRoot.resolve(String.format("quarkus-registry-descriptor-%s.json", version))).exists();
        assertThat(registryDescriptorRoot.resolve(String.format("quarkus-registry-descriptor-%s.json.sha1", version))).exists();
        assertThat(registryDescriptorRoot.resolve("quarkus-registry-descriptor-1.0-SNAPSHOT.json")).exists();
        assertThat(registryDescriptorRoot.resolve("quarkus-registry-descriptor-1.0-SNAPSHOT.json.sha1")).exists();

        RegistryConfig registryConfig = RegistriesConfigMapperHelper.jsonMapper()
                .readValue(registryDescriptorRoot.resolve("quarkus-registry-descriptor-1.0-SNAPSHOT.json").toFile(),
                        JsonRegistryConfig.class);
        // id is marked with @JsonIgnore
        //assertThat(registryConfig.getId()).isEqualTo("foo.bar");
        assertThat(registryConfig.getNonPlatformExtensions()).isNull();
        assertThat(registryConfig.getQuarkusVersions().getRecognizedVersionsExpression()).isEqualTo("1.0.0");
        assertThat(registryConfig.getQuarkusVersions().isExclusiveProvider()).isTrue();
        assertThat(registryConfig.getMaven().getRepository().getUrl()).isEqualTo("https://bar.foo.com");
    }

    @Test
    void should_generate_config_descriptor(@TempDir Path tempDir) throws Exception {
        Path path = new RegistryGenerator(tempDir).generate();

        Path registryDescriptorRoot = path.resolve("io/quarkus/registry/quarkus-registry-descriptor/1.0-SNAPSHOT/");
        assertThat(registryDescriptorRoot).exists();
        assertThat(registryDescriptorRoot.resolve("maven-metadata.xml")).exists();
        assertThat(registryDescriptorRoot.resolve("maven-metadata.xml.sha1")).exists();

        String version = getMetadataVersion(registryDescriptorRoot.resolve("maven-metadata.xml"));
        assertThat(registryDescriptorRoot.resolve(String.format("quarkus-registry-descriptor-%s.json", version))).exists();
        assertThat(registryDescriptorRoot.resolve(String.format("quarkus-registry-descriptor-%s.json.sha1", version))).exists();
        assertThat(registryDescriptorRoot.resolve("quarkus-registry-descriptor-1.0-SNAPSHOT.json")).exists();
        assertThat(registryDescriptorRoot.resolve("quarkus-registry-descriptor-1.0-SNAPSHOT.json.sha1")).exists();
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
        assertThat(platformDescriptorRoot.resolve(String.format("quarkus-platforms-%s.json", version))).exists();
        assertThat(platformDescriptorRoot.resolve(String.format("quarkus-platforms-%s.json.sha1", version))).exists();
    }

    @Test
    void should_generate_non_platform_descriptor_and_maven_metadata(@TempDir Path tempDir) throws Exception {
        ExtensionCatalog extensionCatalog = extractExtensionCatalog(MetadataExtractor.MAVEN_CENTRAL,
                "io.quarkus.platform",
                "quarkus-bom-quarkus-platform-descriptor",
                "2.0.3.Final",
                "2.0.3.Final");
        Extension extension = extractExtension(MetadataExtractor.MAVEN_CENTRAL, "io.quarkiverse.prettytime",
                "quarkus-prettytime", "0.1.1");
        Path path = new RegistryGenerator(tempDir)
                .add(extensionCatalog)
                .add(extension)
                .generate();

        Path nonPlatformExtensionsRoot = path.resolve("io/quarkus/registry/quarkus-non-platform-extensions/1.0-SNAPSHOT");
        assertThat(nonPlatformExtensionsRoot).exists();
        assertThat(nonPlatformExtensionsRoot.resolve("maven-metadata.xml")).exists();
        assertThat(nonPlatformExtensionsRoot.resolve("maven-metadata.xml.sha1")).exists();

        String version = getMetadataVersion(nonPlatformExtensionsRoot.resolve("maven-metadata.xml"));
        assertThat(nonPlatformExtensionsRoot.resolve(
                String.format("quarkus-non-platform-extensions-%s-2.0.3.Final.json", version))).exists();
        assertThat(nonPlatformExtensionsRoot.resolve(
                String.format("quarkus-non-platform-extensions-%s-2.0.3.Final.json.sha1", version))).exists();
        assertThat(nonPlatformExtensionsRoot.resolve("quarkus-non-platform-extensions-1.0-SNAPSHOT-2.0.3.Final.json")).exists();
        assertThat(nonPlatformExtensionsRoot.resolve(
                "quarkus-non-platform-extensions-1.0-SNAPSHOT-2.0.3.Final.json.sha1")).exists();
    }

    @Test
    void should_order_platform_streams_and_releases(@TempDir Path tempDir) throws Exception {
        Path path = new RegistryGenerator(tempDir)
                .add(extractExtensionCatalog(MetadataExtractor.MAVEN_CENTRAL,
                        "io.quarkus.platform",
                        "quarkus-bom-quarkus-platform-descriptor",
                        "2.0.2.Final",
                        "2.0.2.Final"))
                .add(extractExtension(MetadataExtractor.MAVEN_CENTRAL, "io.quarkiverse.prettytime", "quarkus-prettytime",
                        "0.1.0"))
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
                .add(extractExtensionCatalog(MetadataExtractor.MAVEN_CENTRAL,
                        "io.quarkus.platform",
                        "quarkus-bom-quarkus-platform-descriptor",
                        "2.1.1.Final",
                        "2.1.1.Final"))
                .generate();
        Path platformDescriptorRoot = path.resolve("io/quarkus/registry/quarkus-platforms/1.0-SNAPSHOT");
        JsonPlatformCatalog platformCatalog = JsonCatalogMapperHelper.deserialize(
                platformDescriptorRoot.resolve("quarkus-platforms-1.0-SNAPSHOT.json"), JsonPlatformCatalog.class);
        assertThat(platformCatalog.getPlatforms()).hasSize(1);
        assertThat(platformCatalog.getRecommendedPlatform().getStreams())
                .extracting(PlatformStream::getId)
                .containsExactly("2.1", "2.0");
        assertThat(platformCatalog.getRecommendedPlatform().getStream("2.1").getRecommendedRelease())
                .extracting(PlatformRelease::getVersion).isEqualTo(JsonPlatformReleaseVersion.fromString("2.1.1.Final"));
        assertThat(platformCatalog.getRecommendedPlatform().getStream("2.0").getRecommendedRelease())
                .extracting(PlatformRelease::getVersion).isEqualTo(JsonPlatformReleaseVersion.fromString("2.0.3.Final"));
    }

    private String getMetadataVersion(Path metadataPath) throws IOException, XmlPullParserException {
        return new MetadataXpp3Reader().read(Files.newBufferedReader(metadataPath)).getVersioning().getSnapshotVersions().get(0)
                .getVersion();
    }

}