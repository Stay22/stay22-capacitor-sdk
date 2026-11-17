# Changelog

All notable changes to the Stay22 Capacitor plugin are documented here.
This project adheres to [Semantic Versioning](https://semver.org).

## [Unreleased]

## [1.0.0] - 2026-11-17

### Breaking changes
- Replace setTravelContext with a complete trip and timing request through `ensureNotification`; supply finite validity and inspect the returned result.
- Replace forceNotification() with `scheduleDiagnosticNotification` and an explicit trip for one isolated test. It returns `conflict` while a normal notification is still in progress.
- Remove `setMedium` calls; use `setCampaignId` for a campaign label.
- Listen for `notificationChanged` events to follow a notification. The SDK no longer emits `locationUpdated`, `notificationScheduled`, `notificationShown`, `notificationSkipped`, `notificationBlocked` or `notificationCancelled` for new notifications.
- Submit pending trips again after upgrading: the SDK retires notifications queued by an older version the next time it runs. On iOS, an older notification can still appear before then.

### Added
- Control a notification with revision-checked update, replacement and cancellation; persist its reference to retry a command after restart.
- Restrict timing with named-zone periods and a required guarantee, and inspect correlated receipts and separate OS observations.
- The load, shown-notification, failure and opt-out reports the SDK sends to Stay22 include your app's version and build number.

### Changed
- The plugin now wraps iOS and Android SDK 3.1.1.
- A notification now shows your account's text for the trip when Stay22 has one, and the built-in text otherwise.
- Once Stay22 turns on prepared booking links, a notification tap opens a booking link prepared during scheduling instead of waiting for a lookup. `notificationClicked` still carries the Stay22 link saved with the notification.

### Deprecated
- `clearTravelContext` still revokes both normal and diagnostic work; prefer cancellation with a specific reference.

### Removed
- The SDK no longer skips a trip because its destination matches the city Stay22 estimates from the user's network address, and no longer reports the `home_destination` skip reason.

### Fixed
- A notification's booking link now keeps the number of adults in the trip. Before, it could open the booking page with a different number of adults.
- An `aid` with capital letters no longer makes the SDK reject its partner configuration and schedule no notifications.
- When the SDK fails to report a tap on a prepared booking link or, on Android, a shown notification, it now retries instead of losing the report.
- On iOS, an app whose URL scheme your app does not list in `LSApplicationQueriesSchemes` is no longer treated as not installed.

## [0.2.0] - 2026-09-25

### Added
- Apps can require an affirmative consent choice before Stay22 starts by declaring the native consent-required key and calling `setEnabled()` when that choice changes.
- Built-in notifications now use French text on devices set to French. Partner-supplied notification text stays unchanged.

### Changed
- The plugin now wraps iOS and Android SDK 1.4.0. Android apps can use API 24 or newer.

### Fixed
- Disabling the SDK or clearing travel context now prevents a pending notification from
  appearing, including when the scheduling decision was already in progress.
- Android saves the consent choice before SDK initialization.

## [0.1.2] - 2026-09-21

### Fixed
- Your own local notifications could stop appearing. If this plugin loaded before
  `@capacitor/local-notifications`, Stay22 ended up holding Capacitor's single
  notification handler with nothing chained behind it, and every notification your app
  scheduled was delivered by iOS and then silently dropped — no banner, no
  `localNotificationReceived` event, and nothing in your logs. Plugin load order decided
  whether it happened, so it could look intermittent. Stay22 now chains to whichever
  plugin holds the slot, whatever order the two load in.
- The message explaining a handler-slot conflict now reaches the device log. It went
  through Capacitor's logger, which writes to stdout and appears in neither the unified
  log nor Xcode, so the one line that would have explained the above was unreadable.

## [0.1.1] - 2026-09-18

First usable release. 0.1.0 was withdrawn: its iOS half did not build, because it called
an SDK property that no published iOS SDK version had yet. 0.1.1 wraps iOS SDK 1.3.0,
which has it.

### Added
- Android Kotlin bridge over the published `com.stay22:sdk` Maven artifact, plus an
  official Capacitor Android sample (`npx cap add android`) that declares
  `com.stay22.sdk.PartnerAID`.
- First version of the plugin: lifecycle, consent, attribution, notification permission and
  travel context, on iOS.
- The partner ID is read from `Stay22PartnerAID` in `Info.plist` while Capacitor sets up its
  bridge, so the SDK is running before any of your JavaScript.
- Calls that the SDK would discard before it starts now reject with `not_initialized`
  instead of resolving as though they worked.
- Stay22 installs itself in Capacitor's local-notification handler slot and passes anything
  it did not schedule to the handler that held the slot before, so an app already running
  `@capacitor/local-notifications` keeps its own notifications.
- `Stay22.addListener('stay22Event', ...)` reports every SDK event: scheduling, delivery,
  taps and state changes.
- `Stay22.forceNotification()`, for testing the pipeline without waiting out the real delay.
