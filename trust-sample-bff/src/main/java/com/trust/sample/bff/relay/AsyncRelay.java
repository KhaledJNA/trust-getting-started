package com.trust.sample.bff.relay;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The same relay, from another thread: the user's security context (and
 * the request, needed to find the session's token) are carried over by
 * trust-security-web's task decorator, with no code here.
 */
@Service
class AsyncRelay {

    private final ApiClient api;

    AsyncRelay(ApiClient api) {
        this.api = api;
    }

    @Async
    CompletableFuture<Map<String, Object>> me() {
        Map<String, Object> body = new java.util.LinkedHashMap<>(api.me());
        body.put("thread", Thread.currentThread().getName());
        return CompletableFuture.completedFuture(body);
    }
}
