# Daggerfall Online for Android

An Android app for [Daggerfall Online](https://daggerfalljs.dev/) by Lattymoy (MIT): The Elder Scrolls II: Daggerfall,
online and single-player. Made with the developer's knowledge; the icon is Daggerfall Online's own, used at the developer's request.

- **Online** — the live game from daggerfalljs.dev in a full-screen WebView, always up to date.
- **Offline** — a phone-tuned build of the open-source edition stored on the phone: heavy optional visuals off by
  default (`patches/phone-build.patch`), no internet needed. Downloaded once from this repository's releases.

Daggerfall's own data (ARENA2, Bethesda's freeware release) is never bundled: the app downloads Daggerfall Unity's
`DaggerfallGameFiles.zip` or reads your own ARENA2 folder, and answers the game's `./arena2/<NAME>` requests locally.

The Elder Scrolls and Daggerfall are trademarks of ZeniMax Media Inc. This is an unofficial project.

## Build

```sh
python3 make_icon.py && python3 build.py          # Daggerfall.apk (Android SDK build-tools 35, JAVA_HOME)

# the offline build: the open-source edition with the patch, built for phones
git clone https://github.com/Lattymoy/daggerfall-js-source && cd daggerfall-js-source
git apply ../patches/phone-build.patch && npm ci && VITE_PHONE=1 npm run build   # -> dist/, zipped as daggerfall-offline-web.zip
```

---

Приложение Daggerfall Online для Android. Онлайн — живая игра с сайта; офлайн — облегчённая для телефона версия без
интернета. Файлы самой игры (ARENA2) приложение скачивает бесплатно само. Telegram: [@st_mobile_port](https://t.me/st_mobile_port).
