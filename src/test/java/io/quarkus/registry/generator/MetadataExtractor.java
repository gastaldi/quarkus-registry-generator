package io.quarkus.registry.generator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Objects;

import io.quarkus.registry.catalog.Extension;
import io.quarkus.registry.catalog.ExtensionCatalog;
import io.quarkus.registry.catalog.json.JsonCatalogMapperHelper;
import io.quarkus.registry.catalog.json.JsonExtension;
import io.quarkus.registry.catalog.json.JsonExtensionCatalog;
import io.quarkus.registry.config.json.RegistriesConfigMapperHelper;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public class MetadataExtractor {

    public static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";

    public static ExtensionCatalog extractExtensionCatalog(String repository, String groupId, String artifactId, String version, String classifier) throws IOException {
        byte[] bytes = readCatalog(repository, groupId, artifactId, version, classifier);
        return JsonCatalogMapperHelper.deserialize(new ByteArrayInputStream(bytes), JsonExtensionCatalog.class);
    }

    public static Extension extractExtension(String repository, String groupId, String artifactId, String version) throws IOException {
        byte[] bytes = readExtension(repository, groupId, artifactId, version);
        return RegistriesConfigMapperHelper.yamlMapper().readValue(new ByteArrayInputStream(bytes), JsonExtension.class);
    }

    private static byte[] readCatalog(String repository, String groupId, String artifactId, String version, String classifier) throws IOException {
        URI platformJson;
        if (classifier == null) {
            platformJson = URI.create(MessageFormat.format("{0}{1}/{2}/{3}/{2}-{3}.json",
                                                           Objects.toString(repository, MAVEN_CENTRAL),
                                                           groupId.replace('.', '/'),
                                                           artifactId,
                                                           version));
        } else {
//            https://repo1.maven.org/maven2/io/quarkus/quarkus-bom-quarkus-platform-descriptor/1.13.0.Final/quarkus-bom-quarkus-platform-descriptor-1.13.0.Final-1.13.0.Final.json
            platformJson = URI.create(MessageFormat.format("{0}{1}/{2}/{3}/{2}-{4}-{3}.json",
                                                           Objects.toString(repository, MAVEN_CENTRAL),
                                                           groupId.replace('.', '/'),
                                                           artifactId,
                                                           version,
                                                           classifier));
        }
        try (CloseableHttpClient httpClient = createHttpClient();
             InputStream is = httpClient.execute(new HttpGet(platformJson)).getEntity().getContent()) {
            return is.readAllBytes();
        }
    }

    private static byte[] readExtension(String repository, String groupId, String artifactId, String version) throws IOException {
        URL extensionJarURL = new URL(MessageFormat.format("jar:{0}{1}/{2}/{3}/{2}-{3}.jar!/META-INF/quarkus-extension.yaml",
                                                           Objects.toString(repository, MAVEN_CENTRAL),
                                                           groupId.replace('.', '/'),
                                                           artifactId,
                                                           version));
        try (InputStream is = extensionJarURL.openStream()) {
            return is.readAllBytes();
        }
    }

    private static CloseableHttpClient createHttpClient() {
        return HttpClients.custom()
                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .build();
    }
}