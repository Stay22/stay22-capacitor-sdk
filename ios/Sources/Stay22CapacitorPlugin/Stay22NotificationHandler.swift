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
    /// Captured once and never reassigned. Rebuilding the chain against whatever occupies
    /// the slot later would drop this handler and everything behind it: a third plugin that
    /// takes the slot without chaining does not carry our predecessor, so re-chaining *it*
    /// would silently remove the app's original handler for good.
    private let previous: NotificationHandlerProtocol?

    init(chainingTo previous: NotificationHandlerProtocol?) {
        self.previous = previous
        super.init()
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
