package com.trust.sample.bff.relay;

import com.trust.restclient.error.ServiceClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * An API's answer about the user's own rights is passed back as is:
 * {@code 403} (role or UMA policy) and {@code 401} with an ACR challenge,
 * which the page turns into a step-up. Anything else is this BFF's upstream
 * failing: {@code 502}, details in the log only.
 */
@RestControllerAdvice
class DownstreamErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(DownstreamErrorHandler.class);

    @ExceptionHandler(ServiceClientResponseException.class)
    ResponseEntity<ProblemDetail> handle(ServiceClientResponseException exception) {
        int status = exception.getStatusCode().value();
        String challenge = exception.getResponseHeaders() == null
                ? null
                : exception.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE);

        if (status == HttpStatus.FORBIDDEN.value()) {
            return ResponseEntity.status(status).body(exception.getProblemDetail()
                    .orElseGet(() -> ProblemDetail.forStatus(HttpStatus.FORBIDDEN)));
        }
        if (status == HttpStatus.UNAUTHORIZED.value() && challenge != null
                && challenge.contains("insufficient_user_authentication")) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                    "A stronger authentication is required.");
            return ResponseEntity.status(status).header(HttpHeaders.WWW_AUTHENTICATE, challenge).body(problem);
        }

        log.warn("Downstream call failed: {} - {}", exception.getStatusCode(), exception.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "A downstream service failed.");
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(problem);
    }
}
