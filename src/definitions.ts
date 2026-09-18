/**
 * The host app hands over explicit travel intent: where the user is going and when. The
 * SDK decides whether to schedule a single local notification offering accommodation
 * there, opening a Stay22 booking page when it is tapped.
 *
 * Declare your partner ID natively rather than calling {@link Stay22Plugin.initialize}
 * where you can: `Stay22PartnerAID` in `Info.plist`, or `com.stay22.sdk.PartnerAID` in
 * the Android manifest. The plugin reads it while Capacitor sets up its bridge, before
 * any of your JavaScript runs, which is what gives a notification tapped from a cold
 * start its best chance of opening its booking page.
 */
import type { PluginListenerHandle } from '@capacitor/core';

export interface TravelContext {
  address?: string;
  latitude?: number;
  longitude?: number;
  /** ISO date, `YYYY-MM-DD`. */
  checkinDate?: string;
  /** ISO date, `YYYY-MM-DD`. */
  checkoutDate?: string;
  hotelName?: string;
  adults?: number;
  children?: number;
}

/** An event the SDK reports as its state changes: scheduling, delivery, taps. */
export interface Stay22SDKEvent {
  type: string;
  description: string;
  /** Present on `notificationClicked`: the booking URL that was opened. */
  url?: string;
}

export interface Stay22Plugin {
  /**
   * Starts the SDK with your partner ID. A no-op once it is already running.
   *
   * Prefer the native declaration described above; use this only when the partner ID is
   * known at runtime. Resolving means "the SDK is running", not "ready to schedule".
   * Partner configuration still loads afterwards.
   */
  initialize(options: { aid: string }): Promise<void>;

  /** Whether native initialization has happened. Not a readiness signal. */
  isInitialized(): Promise<{ value: boolean }>;

  /**
   * Enables or disables the SDK. While disabled nothing is scheduled and any pending
   * notification is cleared.
   *
   * Persisted across launches and settable before {@link initialize}, which is what makes
   * it usable as your consent gate. Defaults to enabled.
   */
  setEnabled(options: { enabled: boolean }): Promise<void>;
  isEnabled(): Promise<{ value: boolean }>;

  /** Attribution `medium` on the booking link. Defaults to `pushnotif`. */
  setMedium(options: { medium: string }): Promise<void>;

  /** Attribution `campaign` on the booking link. Omit the value to clear it. */
  setCampaignId(options: { campaignId?: string }): Promise<void>;

  hasNotificationPermission(): Promise<{ value: boolean }>;

  /**
   * Shows the system prompt and reports what the user chose: the real answer, not the
   * state before the prompt appeared. Resolves with the current state when no prompt can
   * be shown.
   */
  requestNotificationPermission(): Promise<{ value: boolean }>;

  /** Replaces any previous travel context outright. */
  setTravelContext(options: TravelContext): Promise<void>;

  /** Clears the travel context and any pending notification. */
  clearTravelContext(): Promise<void>;

  /**
   * Whether Stay22 is installed in Capacitor's local-notification handler slot.
   *
   * On iOS, false means another plugin loaded after this one and took the slot without
   * chaining, so Stay22 notifications will open nothing. Check it if offers stop working.
   * On Android this is always true: taps go through the SDK's own activity, not a shared
   * Capacitor slot.
   */
  isNotificationHandlerInstalled(): Promise<{ value: boolean }>;

  /**
   * Runs the scheduling pipeline immediately, bypassing local gates and the server's
   * per-device cooldown, and shortens the delay to a few seconds. Writes no cooldown and
   * no server lock, so it stays repeatable. For integration testing only. Never call
   * this from a shipped build.
   */
  forceNotification(): Promise<void>;

  /** Fires for every SDK event: scheduling, delivery, taps and state changes. */
  addListener(
    eventName: 'stay22Event',
    listenerFunc: (event: Stay22SDKEvent) => void,
  ): Promise<PluginListenerHandle>;

  removeAllListeners(): Promise<void>;
}
