package com.trust.sample.bff.relay;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PutExchange;

import java.util.Map;

/**
 * trust-sample-api. No {@code @ClientRegistrationId}: every call carries
 * the access token of the user's session (trust-rest-client-starter, BFF
 * mode), refreshed when needed.
 */
public interface ApiClient {

    @GetExchange("/me")
    Map<String, Object> me();

    @GetExchange("/reader")
    Map<String, Object> reader();

    @GetExchange("/sensitive")
    Map<String, Object> sensitive();

    @GetExchange("/documents/{id}")
    Map<String, Object> readDocument(@PathVariable String id);

    @PutExchange("/documents/{id}")
    Map<String, Object> writeDocument(@PathVariable String id);

    @GetExchange("/downstream/on-behalf")
    Map<String, Object> downstreamOnBehalf();

    @GetExchange("/downstream/system")
    Map<String, Object> downstreamSystem();
}
