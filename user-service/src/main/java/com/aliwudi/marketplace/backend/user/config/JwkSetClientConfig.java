package com.aliwudi.marketplace.backend.user.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value; // Still needed for @Value
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.nio.file.Files; // Still needed if you use file system paths
import java.nio.file.Paths; // Still needed if you use file system paths
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslContext; // Explicit Netty SslContext import

/**
 * Configuration for creating a specific WebClient instance to be used by Spring Security's
 * ReactiveJwtDecoder for fetching JWK Sets, applying a dedicated SSL truststore directly
 * to Netty's SslContextBuilder.
 *
 * Truststore path and password are now loaded from simplified application properties (e.g., 'truststore.path').
 */
@Configuration
public class JwkSetClientConfig {

    // Injected from application.properties or application.yml using simplified keys
    @Value("${truststore.path}")
    private String truststorePath;

    @Value("${truststore.password}")
    private String truststorePassword;

    /**
     * Defines a specific WebClient instance that uses the manually configured truststore.
     * This WebClient will be used exclusively for fetching JWK Sets.
     *
     * @return A WebClient configured with the manually loaded truststore for trusted connections.
     */
    @Bean("jwkSetWebClient")
    public WebClient jwkSetWebClient() {
        try {
            // 1. Load the KeyStore (truststore) manually using injected path and password
            KeyStore trustStore = KeyStore.getInstance("PKCS12");

            // Handle classpath vs. file system path intelligently
            InputStream is;
            if (truststorePath.startsWith("classpath:")) {
                is = getClass().getClassLoader().getResourceAsStream(truststorePath.substring("classpath:".length()));
                if (is == null) {
                    throw new RuntimeException("Truststore file not found on classpath: " + truststorePath);
                }
            } else {
                // Assuming it's a file system path if not 'classpath:'
                is = Files.newInputStream(Paths.get(truststorePath));
            }

            try (InputStream stream = is) { // Use a try-with-resources for the stream
                trustStore.load(stream, truststorePassword.toCharArray()); // Convert password String to char[]
            }


            // 2. Initialize a TrustManagerFactory with the loaded truststore
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);

            // 3. Extract the X509TrustManager from the factory
            X509TrustManager x509TrustManager = null;
            for (TrustManager tm : trustManagerFactory.getTrustManagers()) {
                if (tm instanceof X509TrustManager) {
                    x509TrustManager = (X509TrustManager) tm;
                    break;
                }
            }
            if (x509TrustManager == null) {
                throw new NoSuchAlgorithmException("No X509TrustManager found in TrustManagerFactory");
            }

            // 4. Build Netty's SslContext directly using SslContextBuilder and the X509TrustManager
            io.netty.handler.ssl.SslContext nettySslContext = SslContextBuilder.forClient()
                    .trustManager(x509TrustManager)
                    .build();

            // 5. Build an HttpClient that uses this Netty SslContext for secure connections.
            HttpClient httpClient = HttpClient.create()
                    .secure(sslContextSpec -> sslContextSpec.sslContext(nettySslContext));

            // 6. Create a ReactorClientHttpConnector from this customized HttpClient.
            ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);

            // 7. Build a WebClient instance using this specific connector.
            return WebClient.builder()
                    .clientConnector(connector)
                    .build();

        } catch (Exception e) {
            System.err.println("Error creating jwkSetWebClient with custom SSL trust: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to configure WebClient for JWK Set fetching due to SSL error", e);
        }
    }

    /**
     * Overrides Spring Security's auto-configured ReactiveJwtDecoder to use the
     * custom 'jwkSetWebClient' for fetching JWK Sets.
     * This ensures that the truststore configured is applied ONLY to the JWT decoding process.
     *
     * @param properties Spring Security OAuth2 Resource Server properties (contains jwk-set-uri).
     * @param jwkSetWebClient The custom WebClient bean specifically configured for JWK Set fetching.
     * @return A ReactiveJwtDecoder that uses the provided WebClient.
     */
    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(OAuth2ResourceServerProperties properties,
                                               @Qualifier("jwkSetWebClient") WebClient jwkSetWebClient) {
        String jwkSetUri = properties.getJwt().getJwkSetUri();
        if (jwkSetUri == null || jwkSetUri.isEmpty()) {
            throw new IllegalArgumentException("spring.security.oauth2.resourceserver.jwt.jwk-set-uri must be configured.");
        }

        NimbusReactiveJwtDecoder jwtDecoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri)
                .webClient(jwkSetWebClient)
                .build();

        return jwtDecoder;
    }
}
