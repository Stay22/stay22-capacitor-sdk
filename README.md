# Stay22 SDK for Capacitor

This Capacitor 6 plugin wraps the Stay22 iOS and Android SDKs.

## Install

Install from GitHub. The package is not published to npm.

```bash
npm install github:Stay22/stay22-capacitor-sdk#1.0.0
npx cap sync
```

Pin the tag you want from [stay22-capacitor-sdk](https://github.com/Stay22/stay22-capacitor-sdk/releases).
The examples below describe this source tree. For a released package, use the README
at the tag you install; the published package may expose a different API.

This package ships the iOS SDK binary it wraps. Android resolves `com.stay22:sdk` from
Stay22's public GitHub Maven URL; declare that repository on the host app as shown below.

## Declare your partner ID natively

Do this rather than calling `initialize()` wherever you can.

`ios/App/App/Info.plist`:

```xml
<key>Stay22PartnerAID</key>
<string>your-partner-id</string>
<!-- Add this if your app requires an affirmative consent choice before Stay22 runs. -->
<key>Stay22RequiresConsent</key>
<true/>
```

`android/app/src/main/AndroidManifest.xml`, inside `<application>`:

```xml
<meta-data
    android:name="com.stay22.sdk.PartnerAID"
    android:value="your-partner-id" />
<!-- Add this if your app requires an affirmative consent choice before Stay22 runs. -->
<meta-data
    android:name="com.stay22.sdk.RequiresConsent"
    android:value="true" />
```

The plugin reads those keys while Capacitor sets up its bridge, before any of your
JavaScript runs. That includes a notification tap that cold-starts the app.

Use `Stay22.initialize({ aid })` only when the partner ID is known at runtime. On
Android that call can miss the first Activity resume.

The wrapped Android SDK needs API 24. A default Capacitor 6 app is API 22; set
`minSdkVersion` to 24 in `variables.gradle`.

On iOS the plugin needs a deployment target of 15.0. A default Capacitor 6 app is 13.0,
and `npx cap sync` stops with "required a higher minimum deployment target" until you
raise it. Set `platform :ios, '15.0'` in `ios/App/Podfile` and the App target's iOS
Deployment Target to 15.0 in Xcode, then run `npx cap sync ios` again.

The Android host also has to declare Stay22's Maven repository. In the top-level
`android/build.gradle`:

```groovy
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://raw.githubusercontent.com/Stay22/stay22-android-sdk/main/maven' }
    }
}
```

If repositories live in `settings.gradle` (`dependencyResolutionManagement`) instead, add
the same URL there. Templates that set `FAIL_ON_PROJECT_REPOS` will reject the
`allprojects` block above.

## App detection on iOS

The SDK checks which travel booking apps are on the device and uses that to pick the
booking experience. iOS only answers that check for URL schemes the host app has
declared, and the SDK treats any other app as unknown, so add all five to
`ios/App/App/Info.plist`:

```xml
<key>LSApplicationQueriesSchemes</key>
<array>
    <string>airbnb</string>
    <string>booking</string>
    <string>expda</string>
    <string>hotelsapp</string>
    <string>agoda</string>
</array>
```

| Scheme | App |
|---|---|
| `airbnb` | Airbnb |
| `booking` | Booking.com |
| `expda` | Expedia |
| `hotelsapp` | Hotels.com |
| `agoda` | Agoda |

Leave one out and the SDK cannot tell whether that app is installed. It logs what is missing at startup,
by app rather than by scheme, so a missing `expda` reads as `expedia` in the Xcode
console. Android needs nothing here: the `<queries>` block ships inside the Stay22
Android SDK and manifest merging folds it into your app.

## Use

If you declare the consent-required key above, the SDK ignores enabled values saved by
earlier versions until your app calls `setEnabled({ enabled: true })`. On each app
launch, pass your app's current consent choice to `setEnabled()` before sending travel
context. Call it again as soon as that choice changes, including when consent is
withdrawn. The SDK retains the explicit choice for notifications delivered while the
app is closed.

The SDK's built-in notification title and body follow the device's English or French
language at scheduling time. When your Stay22 account has notification text for the trip,
the SDK shows that instead.

```ts
import { Stay22 } from '@stay22/capacitor';

// Keep the native SDK in step with your app's consent choice.
await Stay22.setEnabled({ enabled: userHasOptedIn });

await Stay22.requestNotificationPermission();

// Illustrative app-chosen validity bounds, not SDK defaults.
const now = Date.now();
const request = {
  trip: { address: 'Paris, France' },
  timing: { type: 'after' as const, delayMs: 30 * 60 * 1000 },
  expiresAtMs: now + 12 * 60 * 60 * 1000,
  actionExpiresAtMs: now + 24 * 60 * 60 * 1000,
};
const result = await Stay22.ensureNotification(request);
```

Inspect `result.outcome` and `result.reason`, and save the receipt's reference when one
is returned. Submission is an acknowledgement; preparation can still reject the offer.
Retain the same request and deadlines when retrying the same occurrence.

Each request carries a complete trip; omitted fields do not inherit earlier values.
Use a saved reference with `updateNotification` to change timing, `replaceNotification`
to submit a different trip, or `cancelNotification` to cancel. Replacement revokes the
previous offer before preparing the new one, even if the new request is rejected. Retain
the original reference and arguments when retrying a command. An update supplies a complete
new timing intent: omitting `allowedWindows` removes your restriction, and omitted deadlines
require a valid cached account policy.

Read `getNotificationState()` or listen for `notificationChanged` to inspect later state.
A queued notification does not prove that the operating system displayed it at the
selected time.

## iOS notification delegate

On iOS, Capacitor owns `UNUserNotificationCenter.delegate`
and routes local notifications to a single handler. This plugin takes that delegate
and passes anything Stay22 did not schedule to whoever held it before, so
`@capacitor/local-notifications` keeps working alongside it. On Android, taps go
through the SDK's own activity, so `isNotificationHandlerInstalled()` is always true.

Plugin load order is not guaranteed. A plugin that claims the delegate after this one
replaces it. If either plugin's notifications stop working, load that plugin before
Stay22 and check `Stay22.isNotificationHandlerInstalled()`.

If you set `handleApplicationNotifications: false` in `capacitor.config.ts` to run your own
`UNUserNotificationCenter` delegate, this plugin's slot never receives anything. Forward taps
to `Stay22.handleNotificationResponse(_:)` and foreground presentations to
`Stay22.handleWillPresentNotification(_:)` from your own delegate instead.

## Events

```ts
Stay22.addListener('stay22Event', (event) => {
  console.log(event.type, event.description);
});
```

`type` is one of `notificationChanged`, `locationUpdated`, `notificationScheduled`,
`notificationShown`, `notificationClicked`, `notificationBlocked`, `notificationSkipped`,
`notificationCancelled`, `enabledChanged`, `travelContextCleared`, or `unknown`. Follow a
notification's lifecycle with `notificationChanged`; the SDK no longer emits
`locationUpdated`, `notificationScheduled`, `notificationShown`, `notificationSkipped`,
`notificationBlocked` or `notificationCancelled` for new notifications.
`description` is plain text. `notificationChanged` carries a receipt;
`notificationClicked` carries the Stay22 booking page `url` the SDK saved with the notification, even when the tap opens a booking app.

## Testing

```ts
const result = await Stay22.scheduleDiagnosticNotification({
  trip: { address: 'Paris, France' },
});
```

This is a per-call diagnostic. Use test controls only in internal builds.

## Support

See https://dev.stay22.com/docs/mobile-sdk, or email support@stay22.com.
