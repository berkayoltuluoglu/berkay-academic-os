package org.example;

import java.util.List;
import java.util.Set;

final class GoogleDriveOAuthService extends GoogleCalendarOAuthService {
    static final List<String> SCOPES=List.of("https://www.googleapis.com/auth/drive.file","https://www.googleapis.com/auth/userinfo.email");
    @Override protected List<String> requestedScopes(){return SCOPES;}
    @Override protected String tokenDirectory(){return "drive-files-v1";}
    @Override protected void validateGrantedScopes(Set<String> granted){
        if(!(granted.contains(SCOPES.getFirst())||granted.contains("https://www.googleapis.com/auth/drive"))
                || !(granted.contains(SCOPES.get(1))||granted.contains("email")))throw new IllegalStateException("Academic OS dosyaları ve bağlı hesabın e-postası için iki izni de onayla.");
    }
}
