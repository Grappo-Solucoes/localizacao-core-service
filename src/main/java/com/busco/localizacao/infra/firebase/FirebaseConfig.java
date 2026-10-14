package com.busco.localizacao.infra.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;

@Configuration
public class FirebaseConfig {

    @Value("${FIREBASE_SERVICE_ACCOUNT_KEY}")
    private String serviceAccountKey;

    @Value("${firebase.database-url}")
    private String databaseUrl;

    @PostConstruct
    public void validateConfig() {
        if (serviceAccountKey == null || serviceAccountKey.isBlank()) {
            throw new IllegalStateException(
                    "Variável de ambiente FIREBASE_SERVICE_ACCOUNT_KEY não está definida. "
                            + "Configure a chave de service account do Firebase antes de iniciar."
            );
        }
    }

    @Bean
    public FirebaseApp firebaseApp() throws Exception {
        String base64EncodedServiceAccountKey = serviceAccountKey;
        InputStream credentialsStream =
                new ByteArrayInputStream(Base64.getDecoder().decode(base64EncodedServiceAccountKey));
        FirebaseOptions options =
                FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(credentialsStream))
                        .setDatabaseUrl(databaseUrl)
                        .build();

        if (FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.initializeApp(options);
        }
        return FirebaseApp.getInstance();
    }
}