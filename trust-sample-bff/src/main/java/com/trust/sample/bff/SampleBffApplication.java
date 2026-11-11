package com.trust.sample.bff;

import com.trust.sample.bff.relay.ApiClient;
import com.trust.sample.bff.relay.DownstreamClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.service.registry.ImportHttpServices;

@SpringBootApplication
@EnableAsync
@ImportHttpServices(types = ApiClient.class, group = "api")
@ImportHttpServices(types = DownstreamClient.class, group = "downstream")
public class SampleBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleBffApplication.class, args);
    }

    /**
     * The session's token is relayed by trust-rest-client-starter, through
     * this manager. Trust's default one only knows {@code client_credentials}
     * and token exchange; a BFF also needs {@code refresh_token}, since the
     * realm's access tokens live 5 minutes while the session lives 30.
     */
    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrations, OAuth2AuthorizedClientRepository authorizedClients) {

        DefaultOAuth2AuthorizedClientManager manager =
                new DefaultOAuth2AuthorizedClientManager(clientRegistrations, authorizedClients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken()
                .build());
        return manager;
    }
}
