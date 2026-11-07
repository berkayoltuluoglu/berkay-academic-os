package org.example;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class GoogleClassroomOAuthServiceTest {
    private final GoogleClassroomOAuthService auth = new GoogleClassroomOAuthService();

    @Test void acceptsGooglesActualCanonicalReadOnlyPermissionNames() {
        assertDoesNotThrow(() -> auth.validateGrantedScopes(Set.of(
                "https://www.googleapis.com/auth/classroom.courses.readonly",
                "https://www.googleapis.com/auth/classroom.student-submissions.me.readonly",
                "https://www.googleapis.com/auth/userinfo.email", "openid")));
    }
    @Test void alsoAcceptsTheOriginallyRequestedScopes() {
        assertDoesNotThrow(() -> auth.validateGrantedScopes(Set.copyOf(GoogleClassroomOAuthService.CLASSROOM_SCOPES)));
    }
    @Test void partialConsentNamesTheMissingPermissionInsteadOfAcceptingIt() {
        var noAssignments = Set.of("https://www.googleapis.com/auth/classroom.courses.readonly", "email");
        var error = assertThrows(IllegalStateException.class, () -> auth.validateGrantedScopes(noAssignments));
        assertTrue(error.getMessage().contains("ödevlerini"));
        assertThrows(IllegalStateException.class, () -> auth.validateGrantedScopes(Set.of(
                "https://www.googleapis.com/auth/classroom.student-submissions.me.readonly", "email")));
        assertThrows(IllegalStateException.class, () -> auth.validateGrantedScopes(Set.of(
                "https://www.googleapis.com/auth/classroom.courses.readonly",
                "https://www.googleapis.com/auth/classroom.student-submissions.me.readonly", "openid")));
    }
}
