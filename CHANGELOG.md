# Changelog

All notable changes to the Stay22 Capacitor plugin are documented here.
This project adheres to [Semantic Versioning](https://semver.org).

## [Unreleased]

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
