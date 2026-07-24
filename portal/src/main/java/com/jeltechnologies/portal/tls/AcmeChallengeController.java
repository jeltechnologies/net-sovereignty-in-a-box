package com.jeltechnologies.portal.tls;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AcmeChallengeController {

    public static final String CHALLENGE_PATH_PREFIX = "/.well-known/acme-challenge/";

    private final AcmeChallengeStore challengeStore;

    public AcmeChallengeController(AcmeChallengeStore challengeStore) {
        this.challengeStore = challengeStore;
    }

    @GetMapping(CHALLENGE_PATH_PREFIX + "{token}")
    public ResponseEntity<String> respond(@PathVariable String token) {
        String keyAuthorization = challengeStore.get(token);
        if (keyAuthorization == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(keyAuthorization);
    }
}
