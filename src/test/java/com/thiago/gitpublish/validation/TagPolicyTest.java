package com.thiago.gitpublish.validation;

import com.thiago.gitpublish.config.AppConfig;
import com.thiago.gitpublish.model.TagType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TagPolicyTest {

    private final TagPolicy policy = new TagPolicy();
    private final AppConfig config = new AppConfig("master", "develop", Map.of());

    @Test
    void classifiesRelease() {
        assertEquals(TagType.RELEASE, policy.classify("v1.2.3"));
    }

    @Test
    void classifiesReleaseCandidate() {
        assertEquals(
                TagType.RELEASE_CANDIDATE,
                policy.classify("v1.2.3-rc0.01")
        );
    }

    @Test
    void classifiesAlpha() {
        assertEquals(TagType.ALPHA, policy.classify("v1.2.3-alpha0.01"));
    }

    @Test
    void rejectsInvalidTag() {
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.classify("1.2.3")
        );
    }

    @Test
    void releaseRequiresMaster() {
        assertDoesNotThrow(
                () -> policy.validateBranch(TagType.RELEASE, "master", config)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validateBranch(TagType.RELEASE, "develop", config)
        );
    }

    @Test
    void releaseCandidateRequiresDevelop() {
        assertDoesNotThrow(
                () -> policy.validateBranch(
                        TagType.RELEASE_CANDIDATE,
                        "develop",
                        config
                )
        );
    }

    @Test
    void alphaRejectsProtectedBranches() {
        assertDoesNotThrow(
                () -> policy.validateBranch(
                        TagType.ALPHA,
                        "feature/ABC-123",
                        config
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validateBranch(TagType.ALPHA, "main", config)
        );
    }
}
