# Testleri kullanıcının masaüstünü bölmeden çalıştırma

Varsayılan doğrulama pencere açmaz:

```sh
./gradlew :app:test :app:installDist --offline --no-daemon
```

JavaFX arayüz testleri test raporunda atlanan olarak görünür. Bir `DISPLAY` değişkeninin varlığı
tek başına bu testleri açmaz. Arayüz testleri geçici profil kullansa bile `new App(...)` ve `Stage.show()`
gerçek pencere oluşturur; bu nedenle kullanıcının açık masaüstünde kendiliğinden çalıştırılmamalı.

Linux'ta arayüz testlerini ayrı sanal ekranda, Xvfb kurulu olduğunda çalıştır:

```sh
xvfb-run -a ./gradlew :app:test :app:installDist -PguiTests=true --offline --no-daemon
```

`-PguiTests=true` pencere açan testleri açıkça seçer. Sanal ekran olmadan kullanıcının masaüstünde
bu seçeneği kullanma. Windows CI kendi test makinesinde bu seçeneği açıkça verir.
Gerçek masaüstünde görsel inceleme ayrıca kullanıcının isteğiyle yapılır.

Arayüz testleri bitince açtıkları pencereleri kapatır. Kullanıcının `./gradlew run` ile açtığı
uygulama test profili değildir; süreç adına bakarak kapatılmamalı. Bu değişiklik uygulama başlatma
ve bildirim davranışını değiştirmez.
