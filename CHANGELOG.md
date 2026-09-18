# Changelog

All notable changes to the Stay22 Capacitor plugin are documented here.
This project adheres to [Semantic Versioning](https://semver.org).

## [Unreleased]

## [0.1.0] - 2026-09-17
### Added
- Android Kotlin bridge over the published `com.stay22:sdk` Maven artifact. Declare
  Stay22's public Maven repository on the host app as shown in the README.
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
