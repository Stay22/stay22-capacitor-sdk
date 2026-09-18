# Stay22 SDK for Capacitor integration prompt

Give this file to a coding assistant with your Stay22 partner ID (`aid`).

````text
Integrate the Stay22 SDK for Capacitor into this Capacitor app.

Fetch Stay22's current docs. Do not rely on remembered Stay22 APIs.

1. https://dev.stay22.com/llms.txt (Mobile SDK section)
2. https://dev.stay22.com/docs/mobile-sdk.md
3. Append `.md` to any other page URL. Start with
   https://dev.stay22.com/docs/mobile-sdk/quick-start.md
4. After you know the version you installed, read CHANGELOG.md in
   Stay22/stay22-capacitor-sdk for that version.
5. Do not invent configuration flags, suppression ranges, or cookie
   behavior. If a page and the installed SDK disagree, that version's
   changelog wins.
6. Install only as the Stay22/stay22-capacitor-sdk README says.
   Pin a released git tag from that README. The package is not
   published to npm.
7. For `UNUserNotificationCenter.delegate`, follow the
   Stay22/stay22-capacitor-sdk README, not the Stay22 iOS SDK pages.

Partner ID (`aid`): <AID>
````
