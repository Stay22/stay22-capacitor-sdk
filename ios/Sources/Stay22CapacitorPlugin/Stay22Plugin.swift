import Capacitor
import os
import Stay22SDK
import UIKit
import UserNotifications

@objc(Stay22Plugin)
public class Stay22Plugin: CAPPlugin, CAPBridgedPlugin, Stay22EventListener {
    public let identifier = "Stay22Plugin"
    public let jsName = "Stay22"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "initialize", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isInitialized", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setEnabled", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isEnabled", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setCampaignId", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "hasNotificationPermission", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestNotificationPermission", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "ensureNotification", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "updateNotification", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "replaceNotification", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "cancelNotification", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getNotificationState", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "scheduleDiagnosticNotification", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "clearTravelContext", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isNotificationHandlerInstalled", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "addListener", returnType: CAPPluginReturnCallback),
        CAPPluginMethod(name: "removeAllListeners", returnType: CAPPluginReturnPromise)
    ]

    /// Info.plist key that starts the SDK during app launch.
    ///
    /// The native SDK reads no plist of its own; each wrapper does it, because each one
    /// owns the earliest launch hook available to it.
    private static let plistAidKey = "Stay22PartnerAID"

    /// Calls the SDK silently discards before `initialize(aid:)`, so the bridge rejects them
    /// instead of resolving a promise that did nothing. The same set the Flutter bridge
    /// rejects; `initialize`, `isInitialized`, the pre-init-safe setters and the diagnostics
    /// are deliberately absent.
    private static let requiresInitialization: Set<String> = [
        "hasNotificationPermission",
        "requestNotificationPermission",
        "clearTravelContext"
    ]

    /// Held strongly: `notificationRouter` keeps its handlers weakly.
    private var notificationHandler: Stay22NotificationHandler?

    // MARK: - Wiring

    override public func load() {
        // Capacitor loads plugins while its UIKit bridge starts on the main thread.
        // Preserve synchronous initialization and router installation for cold taps.
        MainActor.assumeIsolated { loadOnMain() }
    }

    @MainActor private func loadOnMain() {
        // Capacitor core has already assigned `UNUserNotificationCenter.delegate` to its own
        // router by the time load() runs, and that router never chains to a previous
        // delegate. With the SDK claiming the same slot, whoever ran last would hold it, so
        // whether a tap from a cold start opens a booking page would come down to start-up
        // order. Step the SDK out of the delegate business and drive it from the router's
        // own handler slot instead.
        Stay22.managesNotificationDelegate = false
        initializeFromPlist()
        installNotificationHandler()
        Stay22.advanced.setEventListener(self)

        // Plugin load order is not guaranteed, and a plugin that takes this slot after us
        // replaces us outright — Capacitor logs the override and carries on. One idempotent
        // check once the app is up closes that window; it re-chains whoever displaced us
        // rather than dropping them.
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(reinstallNotificationHandlerIfDisplaced),
            name: UIApplication.didBecomeActiveNotification,
            object: nil
        )
    }

    private func installNotificationHandler() {
        guard let router = bridge?.notificationRouter else { return }
        let handler = Stay22NotificationHandler(chainingTo: router.localNotificationHandler)
        notificationHandler = handler
        router.localNotificationHandler = handler
    }

    @objc private func reinstallNotificationHandlerIfDisplaced() {
        guard let router = bridge?.notificationRouter else { return }

        // Nothing installed yet — `bridge` was nil when load() ran. Both sides being nil
        // makes the identity check below false, so this case needs its own branch or the
        // handler is never installed at all.
        guard let handler = notificationHandler else {
            installNotificationHandler()
            return
        }

        guard router.localNotificationHandler !== handler else { return }

        // The displacer becomes our predecessor when we do not have one. We load before it
        // whenever plugin order puts us first, and without this the reclaim below would
        // leave us holding the slot with nothing behind us -- silently swallowing every
        // notification the app schedules through its own plugin. No-op when we already have
        // a predecessor, which is what keeps a third plugin from erasing it.
        let adopted = handler.adoptIfUnchained(router.localNotificationHandler)

        // Put the *same* handler back rather than building a new one around the displacer.
        // A plugin that took the slot without chaining is not carrying our predecessor, so
        // re-chaining it would drop the app's original handler permanently.
        router.localNotificationHandler = handler

        // `os.Logger`, not `CAPLog.print`: CAPLog writes to stdout, which neither the
        // unified log nor `xcodebuild`'s output captures, so the one message that explains
        // a partner's missing notifications was unreadable exactly when it was needed.
        // The SDK's own `os.log` lines do show up under
        // `xcrun simctl spawn <device> log show`.
        let log = Logger(subsystem: "com.stay22.capacitor", category: "notifications")
        // Literals, not concatenation: `Logger` takes an `OSLogMessage`, which a `String`
        // built with `+` cannot become.
        if adopted {
            log.notice("[Stay22] Another plugin took Capacitor's local-notification handler slot; Stay22 reclaimed it and chained to that plugin, so its own notifications keep working.")
        } else {
            log.error("[Stay22] Another plugin took Capacitor's local-notification handler slot; Stay22 reclaimed it but already had a predecessor, so that plugin's own notifications will not reach it. Load it before Stay22.")
        }
    }

    // MARK: - Lifecycle

    /// Starts the SDK from `Stay22PartnerAID` when the app declares one.
    ///
    /// `load()` runs while the bridge starts up, which is the earliest hook a Capacitor
    /// plugin gets. Starting from JavaScript is later still, and a notification tapped from
    /// a cold start is delivered before any JavaScript runs.
    @MainActor private func initializeFromPlist() {
        guard !Stay22.isInitialized else { return }
        guard let aid = (Bundle.main.object(forInfoDictionaryKey: Self.plistAidKey) as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines), !aid.isEmpty else { return }
        Stay22.initialize(aid: aid)
    }

    /// Fails a call the SDK would otherwise accept and silently discard.
    @MainActor private func rejectIfUninitialized(_ call: CAPPluginCall) -> Bool {
        guard Self.requiresInitialization.contains(call.methodName), !Stay22.isInitialized else {
            return false
        }
        call.reject(
            "Stay22 is not initialized. Declare Stay22PartnerAID in Info.plist, or call "
            + "Stay22.initialize({ aid }) first.",
            "not_initialized"
        )
        return true
    }

    // Capacitor invokes Objective-C selectors on its serial bridge queue; the
    // explicit actor hop is required even when the SDK is annotated @MainActor.
    @objc func initialize(_ call: CAPPluginCall) {
        Task { @MainActor in
            guard let aid = call.getString("aid")?.trimmingCharacters(in: .whitespacesAndNewlines),
                  !aid.isEmpty else {
                call.reject("initialize requires a non-empty aid")
                return
            }
            Stay22.initialize(aid: aid)
            call.resolve()
        }
    }

    @objc func isInitialized(_ call: CAPPluginCall) {
        Task { @MainActor in
            call.resolve(["value": Stay22.isInitialized])
        }
    }

    @objc func setEnabled(_ call: CAPPluginCall) {
        Task { @MainActor in
            guard let enabled = call.getBool("enabled") else {
                call.reject("setEnabled requires `enabled`")
                return
            }
            Stay22.isEnabled = enabled
            call.resolve()
        }
    }

    @objc func isEnabled(_ call: CAPPluginCall) {
        Task { @MainActor in
            call.resolve(["value": Stay22.isEnabled])
        }
    }

    // MARK: - Attribution

    @objc func setCampaignId(_ call: CAPPluginCall) {
        Task { @MainActor in
            // An absent key clears the campaign, matching the Flutter wrapper's nullable
            // parameter. Only a present-but-empty string is a mistake worth rejecting.
            let campaignId = call.getString("campaignId")
            if let campaignId, campaignId.isEmpty {
                call.reject("setCampaignId requires a non-empty `campaignId`, or none at all")
                return
            }
            Stay22.campaignId = campaignId
            call.resolve()
        }
    }

    // MARK: - Permission

    @objc func hasNotificationPermission(_ call: CAPPluginCall) {
        Task { @MainActor in
            guard !rejectIfUninitialized(call) else { return }
            call.resolve(["value": await Stay22.hasNotificationPermission()])
        }
    }

    @objc func requestNotificationPermission(_ call: CAPPluginCall) {
        Task { @MainActor in
            guard !rejectIfUninitialized(call) else { return }
            call.resolve(["value": await Stay22.requestNotificationPermission()])
        }
    }

    // MARK: - Travel context

    @objc func ensureNotification(_ call: CAPPluginCall) {
        Task { @MainActor in
            notificationCommand(call, "ensureNotification")
        }
    }

    @objc func updateNotification(_ call: CAPPluginCall) {
        Task { @MainActor in
            notificationCommand(call, "updateNotification")
        }
    }

    @objc func replaceNotification(_ call: CAPPluginCall) {
        Task { @MainActor in
            notificationCommand(call, "replaceNotification")
        }
    }

    @objc func cancelNotification(_ call: CAPPluginCall) {
        Task { @MainActor in
            notificationCommand(call, "cancelNotification")
        }
    }

    @objc func scheduleDiagnosticNotification(_ call: CAPPluginCall) {
        Task { @MainActor in
            notificationCommand(call, "scheduleDiagnosticNotification")
        }
    }

    @objc func getNotificationState(_ call: CAPPluginCall) {
        Task { @MainActor in
            call.resolve(NotificationBridgeCodec.map(await Stay22.notifications.current()))
        }
    }

    @MainActor private func notificationCommand(_ call: CAPPluginCall, _ method: String) {
        do {
            call.resolve(try NotificationBridgeCodec.command(method, call.options))
        } catch {
            call.reject(error.localizedDescription, "invalid_argument", error)
        }
    }

    @objc func clearTravelContext(_ call: CAPPluginCall) {
        Task { @MainActor in
            guard !rejectIfUninitialized(call) else { return }
            Stay22.clearTravelContext()
            call.resolve()
        }
    }

    // MARK: - Events

    // Capacitor invokes listener methods on its bridge queue, while Native events
    // arrive on main. Keep its mutable listener/retention dictionaries on main.
    override public func addListener(_ call: CAPPluginCall) {
        // The bridge saves this callback as soon as the selector returns.
        call.keepAlive = true
        Task { @MainActor in super.addListener(call) }
    }

    override public func removeListener(_ call: CAPPluginCall) {
        Task { @MainActor in super.removeListener(call) }
    }

    override public func removeAllListeners(_ call: CAPPluginCall) {
        Task { @MainActor in super.removeAllListeners(call) }
    }

    /// Forwards every SDK event to JavaScript. `Stay22.advanced` holds one listener slot,
    /// so this claims it in `load()` rather than on the first `addListener` call — the
    /// same reason the Flutter bridge buffers instead of subscribing lazily.
    public func onEvent(_ event: Stay22Event) {
        // Native startup can replay events before JavaScript subscribes.
        notifyListeners("stay22Event", data: Self.eventPayload(event), retainUntilConsumed: true)
    }

    private static func eventPayload(_ event: Stay22Event) -> [String: Any] {
        switch event {
        case .notificationChanged(let receipt):
            return ["type": "notificationChanged", "description": "Notification state changed", "receipt": NotificationBridgeCodec.map(receipt)]
        case .locationUpdated(let destination):
            return ["type": "locationUpdated", "description": "Location: \(destination)", "destination": destination]
        case .notificationScheduled(let destination, let delay):
            return [
                "type": "notificationScheduled",
                "description": "Scheduled: \(destination) in \(delay)s",
                "destination": destination,
                "delay": delay
            ]
        case .notificationShown(let destination):
            return ["type": "notificationShown", "description": "Shown: \(destination)", "destination": destination]
        case .notificationClicked(let destination, let url):
            return [
                "type": "notificationClicked",
                "description": "Clicked: \(destination)",
                "destination": destination,
                "url": url
            ]
        case .notificationBlocked(let reason):
            return ["type": "notificationBlocked", "description": "Blocked: \(reason)", "reason": reason]
        case .notificationSkipped(let reason):
            return ["type": "notificationSkipped", "description": "Skipped: \(reason)", "reason": reason]
        case .notificationCancelled(let reason):
            return ["type": "notificationCancelled", "description": "Cancelled: \(reason)", "reason": reason]
        case .enabledChanged(let isEnabled):
            return ["type": "enabledChanged", "description": "SDK enabled: \(isEnabled)", "isEnabled": isEnabled]
        case .travelContextCleared:
            return ["type": "travelContextCleared", "description": "Travel context cleared"]
        @unknown default:
            return ["type": "unknown", "description": "Unrecognised event"]
        }
    }

    // MARK: - Diagnostics

    @objc func isNotificationHandlerInstalled(_ call: CAPPluginCall) {
        Task { @MainActor in
            notificationHandlerState(call)
        }
    }

    @MainActor private func notificationHandlerState(_ call: CAPPluginCall) {
        guard let router = bridge?.notificationRouter, let handler = notificationHandler else {
            call.resolve(["value": false])
            return
        }
        // Occupying the slot is not enough. With `handleApplicationNotifications: false` the
        // router is not the notification-centre delegate at all and nothing ever consults
        // the slot, so reachability has to be part of the answer.
        let routerReceivesNotifications = UNUserNotificationCenter.current().delegate === router
        call.resolve(["value": routerReceivesNotifications && router.localNotificationHandler === handler])
    }
}
