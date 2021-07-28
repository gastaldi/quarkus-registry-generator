package io.quarkus.registry.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.quarkus.maven.ArtifactCoords;
import io.quarkus.registry.catalog.Extension;
import io.quarkus.registry.catalog.ExtensionCatalog;
import io.quarkus.registry.catalog.Platform;
import io.quarkus.registry.catalog.PlatformRelease;
import io.quarkus.registry.catalog.json.JsonCatalogMapperHelper;
import io.quarkus.registry.catalog.json.JsonPlatform;
import io.quarkus.registry.catalog.json.JsonPlatformCatalog;
import io.quarkus.registry.catalog.json.JsonPlatformRelease;
import io.quarkus.registry.catalog.json.JsonPlatformReleaseVersion;
import io.quarkus.registry.catalog.json.JsonPlatformStream;
import io.quarkus.registry.config.RegistriesConfigLocator;
import io.quarkus.registry.config.json.RegistriesConfigMapperHelper;
import io.quarkus.registry.generator.internal.MetadataGenerator;
import org.mapstruct.factory.Mappers;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.writeString;

/**
 * Generates a static maven repository structure for a given set of {@link ExtensionCatalog} and {@link Extension} objects.
 */
public class RegistryGenerator {

    private static final RegistryMapper MAPPER = Mappers.getMapper(RegistryMapper.class);

    private final Path outputDir;

    private final Map<String, List<ExtensionCatalog>> catalogMap = new LinkedHashMap<>();

    private final List<Extension> extensionList = new ArrayList<>();


    public RegistryGenerator(Path outputDir) {
        this.outputDir = outputDir;
    }

    /**
     * Add an {@link ExtensionCatalog} object based on a platform release
     *
     * @param catalog the extension catalog
     * @return this instance, for method chaining purposes
     */
    public RegistryGenerator add(String platformKey, ExtensionCatalog catalog) {
        catalogMap.computeIfAbsent(platformKey, s -> new ArrayList<>()).add(catalog);
        return this;
    }

    /**
     * Add an {@link Extension} that is not part of any platform (eg. Quarkiverse)
     *
     * @param extension the extension to be included
     * @return this instance, for method chaining purposes
     */
    public RegistryGenerator add(Extension extension) {
        extensionList.add(extension);
        return this;
    }

    public Path generate() throws IOException {
        generateConfigMetadata();
        generatePlatformsMetadata();
        generateNonPlatformExtensionsMetadata();
        return outputDir;
    }

    /**
     * Just overwrite everything, contents should be the same
     */
    private void generateConfigMetadata() throws IOException {
        var descriptorDir = createDirectories(outputDir.resolve("io/quarkus/registry/quarkus-registry-descriptor/1.0-SNAPSHOT"));
        var contents = RegistriesConfigMapperHelper.jsonMapper().writeValueAsString(RegistriesConfigLocator.getDefaultRegistry());

        writeString(descriptorDir.resolve("quarkus-registry-descriptor-1.0-SNAPSHOT.json"), contents);
        writeString(descriptorDir.resolve("quarkus-registry-descriptor-1.0-SNAPSHOT.json.sha1"), HashUtil.sha1(contents));
    }

    private void generatePlatformsMetadata() throws IOException {
        var descriptorDir = createDirectories(outputDir.resolve("io/quarkus/registry/quarkus-platforms/1.0-SNAPSHOT"));
        Path platformPath = descriptorDir.resolve("quarkus-platforms-1.0-SNAPSHOT.json");
        JsonPlatformCatalog platformCatalog;
        if (Files.exists(platformPath)) {
            platformCatalog = JsonCatalogMapperHelper.deserialize(platformPath, JsonPlatformCatalog.class);
        } else {
            // Create from scratch
            platformCatalog = new JsonPlatformCatalog();

            JsonPlatform ioQuarkusPlatform = new JsonPlatform();
            ioQuarkusPlatform.setPlatformKey("io.quarkus.platform");
            ioQuarkusPlatform.setName("Quarkus Community Platform");
            platformCatalog.addPlatform(ioQuarkusPlatform);
        }
        //TODO: Process extension catalogs
        catalogMap.forEach((platformKey, catalogs) -> {
            Platform platform = platformCatalog.getPlatform(platformKey);
            if (platform != null) {
                for (ExtensionCatalog catalog : catalogs) {
                    Map<String, Object> platformReleaseMetadata = (Map<String, Object>) catalog.getMetadata().get("platform-release");
                    String streamId = (String) platformReleaseMetadata.get("stream");
                    String version = (String) platformReleaseMetadata.get("version");
                    List<String> memberBoms = (List<String>) platformReleaseMetadata.get("members");
                    JsonPlatformStream stream = (JsonPlatformStream) platform.getStream(streamId);
                    if (stream == null) {
                        stream = new JsonPlatformStream();
                        stream.setId(streamId);
                        ((JsonPlatform) platform).addStream(stream);
                    }
                    JsonPlatformRelease release = new JsonPlatformRelease();
                    release.setQuarkusCoreVersion(catalog.getQuarkusCoreVersion());
                    release.setUpstreamQuarkusCoreVersion(catalog.getUpstreamQuarkusCoreVersion());
                    release.setVersion(JsonPlatformReleaseVersion.fromString(version));
                    release.setMemberBoms(memberBoms.stream().map(ArtifactCoords::fromString).collect(Collectors.toList()));
                    stream.addRelease(release);
                }
            }
        });
        var contents = JsonCatalogMapperHelper.mapper().writeValueAsString(platformCatalog);
        writeString(descriptorDir.resolve("quarkus-platforms-1.0-SNAPSHOT.json"), contents);
        writeString(descriptorDir.resolve("quarkus-platforms-1.0-SNAPSHOT.json.sha1"), HashUtil.sha1(contents));

        // Generate maven-metadata.xml
        Set<String> quarkusVersions = platformCatalog.getPlatforms().stream()
                .flatMap(p -> p.getStreams().stream())
                .flatMap(s -> s.getReleases().stream())
                .map(PlatformRelease::getQuarkusCoreVersion)
                .collect(Collectors.toSet());
        String metadata = MetadataGenerator.generateMetadata(new ArtifactCoords("io.quarkus.registry", "quarkus-platforms", "1.0-SNAPSHOT"), quarkusVersions);

        writeString(descriptorDir.resolve("maven-metadata.xml"), metadata);
        writeString(descriptorDir.resolve("maven-metadata.xml.sha1"), HashUtil.sha1(metadata));
    }

    private void generateNonPlatformExtensionsMetadata() {
        //TODO: Process extensions
    }
}