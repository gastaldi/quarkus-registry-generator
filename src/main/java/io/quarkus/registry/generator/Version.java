package io.quarkus.registry.generator;

import java.util.Comparator;
import java.util.Objects;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

public class Version {

    /**
     * Order versions based on the qualifier. Final > CR1
     */
    public static final Comparator<String> QUALIFIER_REVERSED_COMPARATOR = (Comparator.comparing(DefaultArtifactVersion::new).reversed());

    /**
     * Converts a version to an appropriate stream key
     *
     * @param version
     * @return
     */
    public static String toStreamId(String version) {
        DefaultArtifactVersion dav = new DefaultArtifactVersion(version);
        return String.format("%s.%s", dav.getMajorVersion(), dav.getMinorVersion());
    }

    /**
     * Transforms the given version into a lexicographically sortable type
     * Eg. 1.2.3.Final -> 00001.00002.00003.Final
     *
     * @param version the version to be formatted
     * @return a version lexicographically sortable
     */
    public static String toSortable(String version) {
        DefaultArtifactVersion dav = new DefaultArtifactVersion(version);
        String qualifier = Objects.toString(dav.getQualifier(), "");
        // getQualifier does not work in some cases. Eg. 1.2.3.Final-redhat-00001
        if (!version.endsWith(qualifier)) {
            int idx = version.indexOf(qualifier);
            qualifier = version.substring(idx);
        }
        if (!qualifier.isEmpty()) {
            qualifier = "." + qualifier;
        }
        return String.format("%05d.%05d.%05d%s",
                             dav.getMajorVersion(),
                             dav.getMinorVersion(),
                             dav.getIncrementalVersion(),
                             qualifier);
    }
}
