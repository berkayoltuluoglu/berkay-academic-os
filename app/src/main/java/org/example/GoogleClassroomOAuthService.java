package org.example;

import java.util.List;
import java.util.Set;

final class GoogleClassroomOAuthService extends GoogleCalendarOAuthService {
    static final List<String> CLASSROOM_SCOPES = List.of(
            "https://www.googleapis.com/auth/classroom.courses.readonly",
            "https://www.googleapis.com/auth/classroom.coursework.me.readonly",
            "https://www.googleapis.com/auth/userinfo.email");
    @Override protected List<String> requestedScopes() { return CLASSROOM_SCOPES; }
    @Override protected String tokenDirectory() { return "classroom-read-v1"; }
    @Override protected void validateGrantedScopes(Set<String> granted) {
        var missing = new java.util.ArrayList<String>();
        if (!granted.contains(CLASSROOM_SCOPES.get(0))) missing.add("dersleri okuma");
        // Google canonicalizes coursework.me.readonly to this equivalent read-only scope in token responses.
        if (!granted.contains(CLASSROOM_SCOPES.get(1))
                && !granted.contains("https://www.googleapis.com/auth/classroom.student-submissions.me.readonly"))
            missing.add("kendi ödevlerini okuma");
        if (!granted.contains(CLASSROOM_SCOPES.get(2)) && !granted.contains("email"))
            missing.add("bağlı e-posta hesabını okuma");
        if (!missing.isEmpty()) throw new IllegalStateException("Classroom için şu izinleri onayla: " + String.join(", ", missing) + ".");
    }
}
