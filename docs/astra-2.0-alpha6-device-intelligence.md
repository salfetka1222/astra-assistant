# Astra Preview 2.0.0-alpha.6 — Device Intelligence (first layer)

Private Preview build: `com.astra.assistant.preview`, versionCode 33. No public release without owner approval.

## Implemented

- Read-only command `device.map`, triggered only by reviewed user phrases such as `изучи мой телефон` or `карта устройства`.
- `Инструменты → Карта устройства → Изучить телефон` provides the same on-demand snapshot without Termux.
- Snapshot: generic device manufacturer/model, Android version and API level, battery percentage, power-save mode, available RAM and space, whether Astra Accessibility is running, and whether the three already-approved Android system intents have handlers (camera, alarms and dialer).
- Every field is derived from Android system APIs and bounded to known data types, with `неизвестно` on unavailable data.
- No enumeration of installed apps, content provider access, contact lookup, files, identifiers, location, network scan or expanded permissions.
- No background scanning and no new storage for the map. A map requested in **chat** is saved as an ordinary local chat response, just like any other chat message. The report shown in **Tools** lives only in UI state; it is not separately persisted.
- Any device action still requires the existing one-time user confirmation. Reporting does not run those actions.

## Manual smoke test after a safe upgrade

1. Install the standalone signed alpha.6 APK over alpha.5; never uninstall or clear Preview data.
2. Go to **Инструменты → Карта устройства** and tap **Изучить телефон**. Observe Android version, battery, system handlers and Accessibility status. Tap **Обновить карту** to ensure it refreshes on demand.
3. Open **Чат** and send `изучи мой телефон`. Expect a read-only report immediately with no confirmation dialog, Termux required or Android app launch.
4. Send `изучи мой телефон и отправь данные`. It must not become a native action. The generic chat fallback may explain the limitation.
5. Confirm that the existing commands `открой камеру`, `открой будильник` and `назад` still require confirmation, and that chats/settings persist.
6. Turn off Accessibility in Android settings, then refresh the map and verify it accurately changes to `не активна`. Reenable it manually if desired.

## Important boundaries

Presence of an intent handler does not prove that the target activity actually opened or works. The map is capability discovery, not a device-wide app inventory, autonomous control, or visual inspection. If Android package visibility restrictions hide a handler, `не найдено` may be returned; this must not be interpreted as the absence of the physical hardware. Full Device Intelligence will be staged later after validation.
