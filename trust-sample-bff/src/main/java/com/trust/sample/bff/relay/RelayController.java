package com.trust.sample.bff.relay;

import com.trust.security.core.acr.RequiresAcr;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * What the single-page application calls. Every route needs a logged-in
 * session (the starter's default); the decisions themselves are taken by
 * the APIs, on the relayed token.
 */
@RestController
@RequestMapping("/api")
class RelayController {

    private final ApiClient api;
    private final DownstreamClient downstream;
    private final AsyncRelay asyncRelay;

    RelayController(ApiClient api, DownstreamClient downstream, AsyncRelay asyncRelay) {
        this.api = api;
        this.downstream = downstream;
        this.asyncRelay = asyncRelay;
    }

    @GetMapping("/me")
    Map<String, Object> me() {
        return api.me();
    }

    @GetMapping("/reader")
    Map<String, Object> reader() {
        return api.reader();
    }

    /** trust-sample-api requires acr=strong: its challenge is passed back to the browser. */
    @GetMapping("/sensitive")
    Map<String, Object> sensitive() {
        return api.sensitive();
    }

    @GetMapping("/documents/{id}")
    Map<String, Object> readDocument(@PathVariable String id) {
        return api.readDocument(id);
    }

    @PutMapping("/documents/{id}")
    Map<String, Object> writeDocument(@PathVariable String id) {
        return api.writeDocument(id);
    }

    @GetMapping("/downstream/on-behalf")
    Map<String, Object> downstreamOnBehalf() {
        return api.downstreamOnBehalf();
    }

    @GetMapping("/downstream/system")
    Map<String, Object> downstreamSystem() {
        return api.downstreamSystem();
    }

    @GetMapping("/downstream/direct")
    Map<String, Object> downstreamDirect() {
        return downstream.data();
    }

    @GetMapping("/async/me")
    CompletableFuture<Map<String, Object>> asyncMe() {
        return asyncRelay.me();
    }

    /** Step-up decided by the BFF itself, on the session's ID token. */
    @GetMapping("/local/strong")
    @RequiresAcr("strong")
    Map<String, Object> localStrong() {
        return Map.of("message", "BFF endpoint: session authenticated with acr=strong");
    }
}
