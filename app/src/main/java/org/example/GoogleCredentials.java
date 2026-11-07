package org.example;

import java.io.IOException;
import java.nio.file.*;

/** Publisher desktop client settings are shared; access/refresh tokens always stay in the user profile. */
final class GoogleCredentials {
    static Path resolve(Path profile, String name) throws IOException {
        Path selected = profile.resolve("config/" + name);
        if (Files.exists(selected)) return selected;
        if (Boolean.getBoolean("academic.os.packaged")) {
            Path install = AppPaths.installationRoot();
            if (install != null && Files.isRegularFile(install.resolve("app/google-oauth-client.json")))
                return install.resolve("app/google-oauth-client.json");
        }
        throw new IOException("Google bağlantı ayarları eksik. Ayarlar ekranından Google istemci dosyasını seç.");
    }
}
