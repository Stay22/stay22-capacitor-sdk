import Capacitor
import Stay22SDK
import UserNotifications

/// Receives local notifications from Capacitor's router, handles the ones Stay22 scheduled,
/// and passes every other one to whoever held the slot before this plugin loaded.
///
/// Capacitor gives local notifications a single handler slot and its router holds that
/// reference weakly, so the plugin owns this object and this object owns the chain.
final class Stay22NotificationHandler: NSObject, NotificationHandlerProtocol {
    /// The handler installed before us — `@capacitor/local-notifications`, in practice.
    ///
    /// Strong on purpose. The router holds every handler weakly, so a weak reference here
    /// would go nil the moment the other plugin's own reference did, and that app would
    /// stop receiving its own notifications with nothing to show for it.
    ///
    /// Never *re*assigned once it holds someone. Rebuilding the chain against whatever
    /// occupies the slot later would drop this handler and everything behind it: a third
    /// plugin that takes the slot without chaining does not carry our predecessor, so
    /// re-chaining *it* would silently remove the app's original handler for good.
    ///
    /// Filling it when it is still nil is the opposite case and is safe -- see
    /// `adoptIfUnchained`.
    private var previous: NotificationHandlerProtocol?

    init(chainingTo previous: NotificationHandlerProtocol?) {
        self.previous = previous
        super.init()
    }

    /// Take `candidate` as our predecessor, but only if we do not have one yet.
    ///
    /// Plugin load order decides whether we have one at all. Load after
    /// `@capacitor/local-notifications` and `init` captures it. Load *before* it and we
    /// capture nil, that plugin then takes the slot from us, and the plugin puts us back on
    /// the next `didBecomeActive` -- leaving us holding the slot with nothing behind us.
    /// From then on `willPresent` returns `[]` for every notification Stay22 did not
    /// schedule, which iOS obeys: the notification is delivered, the app says present
    /// nothing, and it never appears. The app's own notifications stop working and nothing
    /// says so.
    ///
    /// Measured 2026-09-20 in `NotificationJourneyUITests`, which failed about half the
    /// time on exactly this: `Received response 0 for willPresentNotification` with
    /// `authorizationStatus: Authorized` in the simulator log.
    ///
    /// The guard above still holds: refusing to *replace* a predecessor is what stops a
    /// third plugin from erasing the app's original handler. Filling an empty slot takes
    /// nothing away.
    /// Returns whether it took one, so the caller can say which case it logged.
    @discardableResult
    func adoptIfUnchained(_ candidate: NotificationHandlerProtocol?) -> Bool {
        guard previous == nil, let candidate, candidate !== self else { return false }
        previous = candidate
        return true
    }

    func willPresent(notification: UNNotification) -> UNNotificationPresentationOptions {
        guard Stay22.handleWillPresentNotification(notification) else {
            return previous?.willPresent(notification: notification) ?? []
        }
        // `.list` is what keeps the offer in Notification Centre once the banner times
        // out; without it a user who misses the banner loses the offer entirely.
        return [.banner, .sound, .list]
    }

    func didReceive(response: UNNotificationResponse) {
        // Return rather than also forwarding, unlike the SDK's own delegate, which
        // forwards everything. Capacitor's contract is one handler per slot: passing a
        // Stay22 notification down would make the app's local-notifications plugin raise a
        // JavaScript event for a notification it never scheduled.
        guard !Stay22.handleNotificationResponse(response) else { return }
        previous?.didReceive(response: response)
    }
}
