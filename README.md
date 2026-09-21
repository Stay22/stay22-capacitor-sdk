# Stay22 SDK for Capacitor

This Capacitor 6 plugin wraps the Stay22 iOS and Android SDKs.

## Install

Install from GitHub. The package is not published to npm.

```bash
npm install github:Stay22/stay22-capacitor-sdk#0.1.2
npx cap sync
```

Pin the tag you want from [stay22-capacitor-sdk](https://github.com/Stay22/stay22-capacitor-sdk/releases).

This package ships the iOS SDK binary it wraps. Android resolves `com.stay22:sdk` from
Stay22's public GitHub Maven URL; declare that repository on the host app as shown below.

## Declare your partner ID natively

Do this rather than calling `initialize()` wherever you can.

`ios/App/App/Info.plist`:

```xml
<key>Stay22PartnerAID</key>
<string>your-partner-id</string>
```

`android/app/src/main/AndroidManifest.xml`, inside `<application>`:

```xml
<meta-data
    android:name="com.stay22.sdk.PartnerAID"
    android:value="your-partner-id" />
```

The plugin reads those keys while Capacitor sets up its bridge, before any of your
JavaScript runs. That includes a notification tap that cold-starts the app.

Use `Stay22.initialize({ aid })` only when the partner ID is known at runtime. On
Android that call can miss the first Activity resume.

The wrapped Android SDK needs API 26. A default Capacitor 6 app is API 22; set
`minSdkVersion` to 26 in `variables.gradle`.

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
declared, and returns `false` for any it has not, so add all five to
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

Leave one out and the SDK treats that app as absent. It logs what is missing at startup,
by app rather than by scheme, so a missing `expda` reads as `expedia` in the Xcode
console. Android needs nothing here: the `<queries>` block ships inside the Stay22
Android SDK and manifest merging folds it into your app.

## Use

```ts
import { Stay22 } from '@stay22/capacitor';

// Your consent gate. Persisted, and safe to set before the SDK starts.
await Stay22.setEnabled({ enabled: userHasOptedIn });

await Stay22.requestNotificationPermission();

await Stay22.setTravelContext({
  address: 'Paris, France',
  checkinDate: '2026-03-20',
  checkoutDate: '2026-03-25',
});
```

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

`type` is one of `locationUpdated`, `notificationScheduled`, `notificationShown`,
`notificationClicked`, `notificationBlocked`, `notificationSkipped`,
`notificationCancelled`, `enabledChanged`, `travelContextCleared`.
`description` is plain text. `notificationClicked` also carries the opened `url`.

## Testing

```ts
await Stay22.forceNotification();
```

Debug helper that displays a notification without the usual schedule. Leave this
call out of the binary you send to users.

## Support

See https://dev.stay22.com/docs/mobile-sdk, or email support@stay22.com.
