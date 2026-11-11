package com.trust.sample.api.downstream;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
class DownstreamController {

    private final DownstreamClient downstreamClient;

    DownstreamController(DownstreamClient downstreamClient) {
        this.downstreamClient = downstreamClient;
    }

    /** The downstream API sees the same user, through a token issued to trust-sample-api. */
    @GetMapping("/downstream/on-behalf")
    @PreAuthorize("hasRole('SAMPLE_USER')")
    Map<String, Object> onBehalf() {
        return downstreamClient.dataOnBehalfOfTheUser();
    }

    /** The downstream API sees this application, whoever (if anyone) the caller is. */
    @GetMapping("/downstream/system")
    @PreAuthorize("hasRole('SAMPLE_USER')")
    Map<String, Object> system() {
        return downstreamClient.systemStatus();
    }
}
