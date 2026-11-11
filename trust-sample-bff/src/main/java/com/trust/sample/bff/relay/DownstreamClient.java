package com.trust.sample.bff.relay;

import org.springframework.web.service.annotation.GetExchange;

import java.util.Map;

/**
 * trust-sample-downstream-api, called directly with the session's token:
 * that API validates it by introspection (opaque-token mode), not locally.
 */
public interface DownstreamClient {

    @GetExchange("/data")
    Map<String, Object> data();
}
