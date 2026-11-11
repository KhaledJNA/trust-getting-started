package com.trust.sample.api.downstream;

import org.springframework.security.oauth2.client.annotation.ClientRegistrationId;
import org.springframework.web.service.annotation.GetExchange;

import java.util.Map;

/**
 * trust-sample-downstream-api, declared with Spring's HTTP interfaces; the
 * token sent is chosen per method (trust-rest-client-starter):
 * <ul>
 *   <li>{@link #dataOnBehalfOfTheUser()}: the caller's token is exchanged
 *   (RFC 8693) for a token issued to trust-sample-api - same user;</li>
 *   <li>{@link #systemStatus()}: this application's own token
 *   ({@code client_credentials}, scope {@code sample.system}) - no user.</li>
 * </ul>
 */
public interface DownstreamClient {

    @GetExchange("/data")
    @ClientRegistrationId("downstream-on-behalf")
    Map<String, Object> dataOnBehalfOfTheUser();

    @GetExchange("/system/status")
    @ClientRegistrationId("downstream-system")
    Map<String, Object> systemStatus();
}
