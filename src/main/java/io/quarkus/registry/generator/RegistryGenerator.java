package io.quarkus.registry.generator;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import io.quarkus.maven.ArtifactCoords;
import io.quarkus.registry.Constants;
import io.quarkus.registry.catalog.Extension;
import io.quarkus.registry.catalog.ExtensionCatalog;
import io.quarkus.registry.catalog.PlatformRelease;
import io.quarkus.registry.catalog.PlatformStream;
import io.quarkus.registry.catalog.json.JsonCatalogMapperHelper;
import io.quarkus.registry.catalog.json.JsonExtensionCatalog;
import io.quarkus.registry.catalog.json.JsonPlatform;
import io.quarkus.registry.catalog.json.JsonPlatformCatalog;
import io.quarkus.registry.catalog.json.JsonPlatformRelease;
import io.quarkus.registry.catalog.json.JsonPlatformReleaseVersion;
import io.quarkus.registry.catalog.json.JsonPlatformStream;
import io.quarkus.registry.config.json.JsonRegistryConfig;
import io.quarkus.registry.config.json.JsonRegistryDescriptorConfig;
import io.quarkus.registry.config.json.JsonRegistryMavenConfig;
import io.quarkus.registry.config.json.JsonRegistryMavenRepoConfig;
import io.quarkus.registry.config.json.JsonRegistryNonPlatformExtensionsConfig;
import io.quarkus.registry.config.json.JsonRegistryPlatformsConfig;
import io.quarkus.registry.config.json.JsonRegistryQuarkusVersionsConfig;
import io.quarkus.registry.config.json.RegistriesConfigMapperHelper;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.sonatype.nexus.repository.metadata.model.RepositoryMetadata;

import static io.quarkus.registry.generator.HashUtil.sha1;
import static io.quarkus.registry.generator.MetadataGenerator.generateMetadata;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.writeString;
import static java.util.stream.Collectors.toList;

/**
 * Generates a static maven repository structure for a given set of {@link ExtensionCatalog} and {@link Extension} objects.
 */
public class RegistryGenerator implements Closeable {

    private final Path outputDir;

    private final Map<String, List<ExtensionCatalog>> catalogMap = new LinkedHashMap<>();

    private final List<Extension> extensionList = new ArrayList<>();

    private static final String SHA1_EXTENSION = ".sha1";

    private String groupId = Constants.DEFAULT_REGISTRY_GROUP_ID;
    private String registryId = Constants.DEFAULT_REGISTRY_ID;
    private String registryUrl = Constants.DEFAULT_REGISTRY_MAVEN_REPO_URL;
    private boolean supportsNonPlatforms = true;
    private boolean quarkusVersionsExclusiveProvider;
    private String quarkusVersionExpression;

    private final Date now = new Date();

    public RegistryGenerator(Path outputDir) {
        this.outputDir = outputDir;
    }

    public RegistryGenerator add(ExtensionCatalog catalog) {
        Map<String, Object> metadata = (Map<String, Object>) catalog.getMetadata().get("platform-release");
        String platformKey = (String) metadata.get("platform-key");
        return add(platformKey, catalog);
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

    /**
     * Use this group ID for all generated metadata.
     *
     * @param groupId the group ID to be used
     * @return this instance, for method chaining purposes
     */
    public RegistryGenerator withGroupId(String groupId) {
        this.groupId = groupId;
        return this;
    }

    /**
     * Use this registry ID for all generated metadata.
     *
     * @param registryId the group ID to be used
     * @return this instance, for method chaining purposes
     */
    public RegistryGenerator withRegistryId(String registryId) {
        this.registryId = registryId;
        return this;
    }

    public RegistryGenerator withRegistryUrl(String registryUrl) {
        this.registryUrl = registryUrl;
        return this;
    }

    public RegistryGenerator withSupportsNonPlatforms(boolean supportsNonPlatforms) {
        this.supportsNonPlatforms = supportsNonPlatforms;
        return this;
    }

    public RegistryGenerator withQuarkusVersionExpression(String quarkusVersionExpression) {
        this.quarkusVersionExpression = quarkusVersionExpression;
        return this;
    }

    public RegistryGenerator withQuarkusVersionsExclusiveProvider(boolean quarkusVersionsExclusiveProvider) {
        this.quarkusVersionsExclusiveProvider = quarkusVersionsExclusiveProvider;
        return this;
    }

    /**
     * Perform the generation on the given data
     *
     * @return the {@link Path} of the output directory
     * @throws IOException if some IO error occurs
     */
    public Path generate() throws IOException {
        generateRepositoryMetadata();
        generateConfig();
        generatePlatforms();
        generateNonPlatformExtensions();
        return outputDir;
    }

    @Override
    public void close() throws IOException {
        generate();
    }

    private void generateRepositoryMetadata() throws IOException {
        var descriptorDir = createDirectories(outputDir.resolve(".meta"));
        // Create prefixes.txt
        writeString(descriptorDir.resolve("prefixes.txt"), "## repository-prefixes/2.0" + System.lineSeparator()
                + "/" + groupId.replace('.', '/'));
        // Create repository-metadata.xml and repository-metadata.sha1
        RepositoryMetadata repositoryMetadata = new RepositoryMetadata();
        repositoryMetadata.setVersion(RepositoryMetadata.MODEL_VERSION);
        repositoryMetadata.setId(registryId);
        repositoryMetadata.setUrl(registryUrl);
        repositoryMetadata.setLayout(RepositoryMetadata.LAYOUT_MAVEN2);
        repositoryMetadata.setPolicy(RepositoryMetadata.POLICY_SNAPSHOT);
        var metadataString = MetadataGenerator.toString(repositoryMetadata);
        writeString(descriptorDir.resolve("repository-metadata.xml"), metadataString);
        writeString(descriptorDir.resolve("repository-metadata.xml.sha1"), sha1(metadataString));
    }

    /**
     * Just overwrite everything, contents should be the same. Must produce the following files:
     *
     * io/quarkus/registry/quarkus-registry-descriptor/1.0-SNAPSHOT/maven-metadata.xml
     * io/quarkus/registry/quarkus-registry-descriptor/1.0-SNAPSHOT/maven-metadata.xml.sha1
     * io/quarkus/registry/quarkus-registry-descriptor/1.0-SNAPSHOT/quarkus-registry-descriptor-1.0-20210803.135921-1.json
     * io/quarkus/registry/quarkus-registry-descriptor/1.0-SNAPSHOT/quarkus-registry-descriptor-1.0-20210803.135921-1.json.sha1
     *
     * @throws IOException if some IO exception occurs
     */
    private void generateConfig() throws IOException {
        var descriptorDir = createDirectories(
                outputDir.resolve(groupId.replace('.', '/') + "/quarkus-registry-descriptor/1.0-SNAPSHOT"));
        final JsonRegistryConfig qer = new JsonRegistryConfig();
        qer.setId(registryId);

        final JsonRegistryDescriptorConfig descriptor = new JsonRegistryDescriptorConfig();
        qer.setDescriptor(descriptor);
        descriptor.setArtifact(
                new ArtifactCoords(groupId,
                        Constants.DEFAULT_REGISTRY_DESCRIPTOR_ARTIFACT_ID, null,
                        Constants.JSON, Constants.DEFAULT_REGISTRY_ARTIFACT_VERSION));

        final JsonRegistryMavenConfig registryMavenConfig = new JsonRegistryMavenConfig();
        qer.setMaven(registryMavenConfig);

        final JsonRegistryPlatformsConfig platformsConfig = new JsonRegistryPlatformsConfig();
        qer.setPlatforms(platformsConfig);
        platformsConfig.setArtifact(new ArtifactCoords(groupId,
                Constants.DEFAULT_REGISTRY_PLATFORMS_CATALOG_ARTIFACT_ID, null, Constants.JSON,
                Constants.DEFAULT_REGISTRY_ARTIFACT_VERSION));

        if (supportsNonPlatforms) {
            final JsonRegistryNonPlatformExtensionsConfig nonPlatformExtensionsConfig = new JsonRegistryNonPlatformExtensionsConfig();
            qer.setNonPlatformExtensions(nonPlatformExtensionsConfig);
            nonPlatformExtensionsConfig.setArtifact(new ArtifactCoords(groupId,
                    Constants.DEFAULT_REGISTRY_NON_PLATFORM_EXTENSIONS_CATALOG_ARTIFACT_ID, null, Constants.JSON,
                    Constants.DEFAULT_REGISTRY_ARTIFACT_VERSION));
        }
        if (quarkusVersionExpression != null) {
            final JsonRegistryQuarkusVersionsConfig quarkusVersionsConfig = new JsonRegistryQuarkusVersionsConfig();
            quarkusVersionsConfig.setRecognizedVersionsExpression(quarkusVersionExpression);
            quarkusVersionsConfig.setExclusiveProvider(quarkusVersionsExclusiveProvider);
            qer.setQuarkusVersions(quarkusVersionsConfig);
        }
        final JsonRegistryMavenRepoConfig mavenRepo = new JsonRegistryMavenRepoConfig();
        registryMavenConfig.setRepository(mavenRepo);
        mavenRepo.setId(registryId);
        mavenRepo.setUrl(registryUrl);

        var contents = RegistriesConfigMapperHelper.jsonMapper().writeValueAsString(qer);

        // Generate metadata
        Metadata metadata = generateMetadata(new ArtifactCoords(groupId, "quarkus-registry-descriptor", "1.0-SNAPSHOT"), now,
                Collections.emptyList());
        var metadataString = MetadataGenerator.toString(metadata);
        writeString(descriptorDir.resolve("maven-metadata.xml"), metadataString);
        writeString(descriptorDir.resolve("maven-metadata.xml.sha1"), sha1(metadataString));

        String timestampedJsonFile = String.format("quarkus-registry-descriptor-%s.json",
                metadata.getVersioning().getSnapshotVersions().get(0).getVersion());
        writeString(descriptorDir.resolve(timestampedJsonFile), contents);
        writeString(descriptorDir.resolve(timestampedJsonFile + SHA1_EXTENSION), sha1(contents));

        copy(descriptorDir.resolve(timestampedJsonFile), descriptorDir.resolve("quarkus-registry-descriptor-1.0-SNAPSHOT.json"),
                StandardCopyOption.REPLACE_EXISTING);
        copy(descriptorDir.resolve(timestampedJsonFile + SHA1_EXTENSION),
                descriptorDir.resolve("quarkus-registry-descriptor-1.0-SNAPSHOT.json" + SHA1_EXTENSION),
                StandardCopyOption.REPLACE_EXISTING);

    }

    /**
     * Must produce the following files:
     *
     * io/quarkus/registry/quarkus-platforms/1.0-SNAPSHOT/maven-metadata.xml
     * io/quarkus/registry/quarkus-platforms/1.0-SNAPSHOT/maven-metadata.xml.sha1
     * io/quarkus/registry/quarkus-platforms/1.0-SNAPSHOT/quarkus-platforms-1.0-20210803.135923-1.json
     * io/quarkus/registry/quarkus-platforms/1.0-SNAPSHOT/quarkus-platforms-1.0-20210803.135923-1.json.sha1
     *
     * @throws IOException if some IO exception occurs
     */
    private void generatePlatforms() throws IOException {
        var descriptorDir = createDirectories(outputDir.resolve(groupId.replace('.', '/') + "/quarkus-platforms/1.0-SNAPSHOT"));

        final JsonPlatformCatalog platformCatalog = new JsonPlatformCatalog();

        // Sort by release (Final > CR) and then by stream id
        Comparator<PlatformRelease> compareRelease = ((o1, o2) -> Version.VERSION_COMPARATOR
                .compare(o1.getVersion().toString(), o2.getVersion().toString()));

        catalogMap.forEach((platformKey, catalogs) -> {
            // Create a new platform because they are immutable
            JsonPlatform jsonPlatform = new JsonPlatform();
            jsonPlatform.setPlatformKey(platformKey);

            Map<String, JsonPlatformStream> streams = new TreeMap<>(Comparator.comparing(Version::toSortable).reversed());
            for (ExtensionCatalog catalog : catalogs) {
                Map<String, Object> platformReleaseMetadata = (Map<String, Object>) catalog.getMetadata()
                        .get("platform-release");
                String streamId = (String) platformReleaseMetadata.get("stream");
                String version = (String) platformReleaseMetadata.get("version");
                List<String> memberBoms = (List<String>) platformReleaseMetadata.get("members");
                JsonPlatformStream stream = streams.computeIfAbsent(streamId, key -> {
                    JsonPlatformStream newStream = new JsonPlatformStream();
                    newStream.setId(key);
                    return newStream;
                });
                JsonPlatformRelease release = new JsonPlatformRelease();
                release.setQuarkusCoreVersion(catalog.getQuarkusCoreVersion());
                release.setUpstreamQuarkusCoreVersion(catalog.getUpstreamQuarkusCoreVersion());
                release.setVersion(JsonPlatformReleaseVersion.fromString(version));
                release.setMemberBoms(memberBoms.stream().map(ArtifactCoords::fromString).collect(toList()));

                if (stream.getReleases().isEmpty()) {
                    stream.addRelease(release);
                } else {
                    // Order all releases
                    List<PlatformRelease> sortedReleases = new ArrayList<>();
                    sortedReleases.add(release);
                    sortedReleases.addAll(stream.getReleases());
                    sortedReleases.sort(compareRelease);
                    stream.setReleases(List.of(sortedReleases.get(0)));
                }
            }
            List<PlatformStream> orderedStreams = new ArrayList<>(streams.values());
            orderedStreams.sort((p1, p2) -> Version.QUALIFIER_REVERSED_COMPARATOR.compare(
                    p1.getRecommendedRelease().getVersion().toString(), p2.getRecommendedRelease().getVersion().toString()));
            jsonPlatform.setStreams(orderedStreams);
            platformCatalog.addPlatform(jsonPlatform);
        });

        // Generate maven-metadata.xml
        Set<String> quarkusVersions = platformCatalog.getPlatforms().stream()
                .flatMap(p -> p.getStreams().stream())
                .flatMap(s -> s.getReleases().stream())
                .map(PlatformRelease::getQuarkusCoreVersion)
                .collect(Collectors.toSet());

        Metadata metadata = generateMetadata(new ArtifactCoords(groupId, "quarkus-platforms", "1.0-SNAPSHOT"), now,
                quarkusVersions);
        var metadataString = MetadataGenerator.toString(metadata);
        writeString(descriptorDir.resolve("maven-metadata.xml"), metadataString);
        writeString(descriptorDir.resolve("maven-metadata.xml.sha1"), sha1(metadataString));

        var timestampedJsonFile = String.format("quarkus-platforms-%s.json",
                metadata.getVersioning().getSnapshotVersions().get(0).getVersion());
        var contents = JsonCatalogMapperHelper.mapper().writeValueAsString(platformCatalog);
        writeString(descriptorDir.resolve(timestampedJsonFile), contents);
        writeString(descriptorDir.resolve(timestampedJsonFile + SHA1_EXTENSION), sha1(contents));

        copy(descriptorDir.resolve(timestampedJsonFile), descriptorDir.resolve("quarkus-platforms-1.0-SNAPSHOT.json"),
                StandardCopyOption.REPLACE_EXISTING);
        copy(descriptorDir.resolve(timestampedJsonFile + SHA1_EXTENSION),
                descriptorDir.resolve("quarkus-platforms-1.0-SNAPSHOT.json" + SHA1_EXTENSION),
                StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Must produce the following files:
     *
     * io/quarkus/registry/quarkus-non-platform-extensions/1.0-SNAPSHOT/maven-metadata.xml
     * io/quarkus/registry/quarkus-non-platform-extensions/1.0-SNAPSHOT/maven-metadata.xml.sha1
     * io/quarkus/registry/quarkus-non-platform-extensions/1.0-SNAPSHOT/quarkus-non-platform-extensions-1.0-20210803.135924-1-2.1.0.Final.json
     * io/quarkus/registry/quarkus-non-platform-extensions/1.0-SNAPSHOT/quarkus-non-platform-extensions-1.0-20210803.135924-1-2.1.0.Final.json.sha1
     * io/quarkus/registry/quarkus-non-platform-extensions/1.0-SNAPSHOT/quarkus-non-platform-extensions-1.0-20210803.135924-1-2.0.3.Final.json
     * io/quarkus/registry/quarkus-non-platform-extensions/1.0-SNAPSHOT/quarkus-non-platform-extensions-1.0-20210803.135924-1-2.0.3.Final.json.sha1
     *
     * @throws IOException
     */
    private void generateNonPlatformExtensions() throws IOException {
        var descriptorDir = createDirectories(
                outputDir.resolve(groupId.replace('.', '/') + "/quarkus-non-platform-extensions/1.0-SNAPSHOT"));
        // Generate metadata
        Metadata metadata = generateMetadata(new ArtifactCoords(groupId,
                        "quarkus-non-platform-extensions",
                        "1.0-SNAPSHOT"),
                now,
                Collections.emptyList());
        String metadataString = MetadataGenerator.toString(metadata);
        writeString(descriptorDir.resolve("maven-metadata.xml"), metadataString);
        writeString(descriptorDir.resolve("maven-metadata.xml.sha1"), sha1(metadataString));

        Collection<String> quarkusVersions = getQuarkusVersions();
        // Generate a JSON per Quarkus version
        for (String quarkusVersion : quarkusVersions) {
            JsonExtensionCatalog jsonExtensionCatalog = new JsonExtensionCatalog();
            jsonExtensionCatalog.setId(new ArtifactCoords(groupId,
                    "quarkus-non-platform-extensions",
                    quarkusVersion,
                    "json",
                    "1.0-SNAPSHOT").toString());
            jsonExtensionCatalog.setBom(ArtifactCoords.pom("io.quarkus.platform", "quarkus-bom", quarkusVersion));
            extensionList.forEach(jsonExtensionCatalog::addExtension);
            var contents = JsonCatalogMapperHelper.mapper().writeValueAsString(jsonExtensionCatalog);
            var timestampedJsonFile = String.format("quarkus-non-platform-extensions-%s-%s.json",
                    metadata.getVersioning().getSnapshotVersions().get(0).getVersion(), quarkusVersion);
            writeString(descriptorDir.resolve(timestampedJsonFile), contents);
            writeString(descriptorDir.resolve(timestampedJsonFile + SHA1_EXTENSION), sha1(contents));
            copy(descriptorDir.resolve(timestampedJsonFile), descriptorDir.resolve(
                            String.format("quarkus-non-platform-extensions-1.0-SNAPSHOT-%s.json", quarkusVersion)),
                    StandardCopyOption.REPLACE_EXISTING);
            copy(descriptorDir.resolve(timestampedJsonFile + SHA1_EXTENSION), descriptorDir.resolve(
                            String.format("quarkus-non-platform-extensions-1.0-SNAPSHOT-%s.json" + SHA1_EXTENSION, quarkusVersion)),
                    StandardCopyOption.REPLACE_EXISTING);

        }
    }

    private Collection<String> getQuarkusVersions() {
        return catalogMap.values().stream()
                .flatMap(Collection::stream)
                .map(ExtensionCatalog::getQuarkusCoreVersion)
                .sorted(Version.QUALIFIER_REVERSED_COMPARATOR)
                .collect(Collectors.toList());
    }
}