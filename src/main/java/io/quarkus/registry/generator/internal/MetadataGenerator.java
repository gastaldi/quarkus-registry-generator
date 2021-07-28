package io.quarkus.registry.generator.internal;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import io.quarkus.maven.ArtifactCoords;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Snapshot;
import org.apache.maven.artifact.repository.metadata.SnapshotVersion;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer;

public class MetadataGenerator {

    private static final MetadataXpp3Writer METADATA_WRITER = new MetadataXpp3Writer();

    private static final List<String> EMPTY_CLASSIFIER = Collections.singletonList("");

    public static String generateMetadata(ArtifactCoords artifact, Collection<String> quarkusVersions) {
        Metadata newMetadata = new Metadata();
        newMetadata.setGroupId(artifact.getGroupId());
        newMetadata.setArtifactId(artifact.getArtifactId());

        Versioning versioning = new Versioning();
        newMetadata.setVersioning(versioning);

        versioning.updateTimestamp();

        Snapshot snapshot = new Snapshot();
        versioning.setSnapshot(snapshot);
        snapshot.setTimestamp(versioning.getLastUpdated().substring(0, 8) + "." + versioning.getLastUpdated().substring(8));
        snapshot.setBuildNumber(1);

        final String baseVersion = artifact.getVersion().substring(0, artifact.getVersion().length() - "SNAPSHOT".length());
        addSnapshotVersion(versioning, snapshot, baseVersion, "pom", EMPTY_CLASSIFIER);
        addSnapshotVersion(versioning, snapshot, baseVersion, "json", EMPTY_CLASSIFIER);
        addSnapshotVersion(versioning, snapshot, baseVersion, "json", quarkusVersions);
        return writeMetadata(newMetadata);
    }

    private static void addSnapshotVersion(Versioning versioning, Snapshot snapshot, final String baseVersion,
                                           String extension, Collection<String> classifiers) {
        final String version = baseVersion + snapshot.getTimestamp() + "-" + snapshot.getBuildNumber();
        for (String classifier : classifiers) {
            final SnapshotVersion sv = new SnapshotVersion();
            sv.setExtension(extension);
            sv.setVersion(version);
            sv.setClassifier(classifier);
            sv.setUpdated(versioning.getLastUpdated());
            versioning.addSnapshotVersion(sv);
        }
    }

    private static String writeMetadata(Metadata metadata) {
        StringWriter sw = new StringWriter();
        try {
            METADATA_WRITER.write(sw, metadata);
        } catch (IOException e) {
            // Should never happen
        }
        return sw.toString();
    }
}
