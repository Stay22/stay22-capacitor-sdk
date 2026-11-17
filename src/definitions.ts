import type { PluginListenerHandle } from '@capacitor/core';
import type { NotificationRequest, NotificationUpdate, NotificationReference, NotificationResult, NotificationQuery, NotificationReceipt } from './notifications';
export type * from './notifications';

/** A complete trip snapshot. Native code owns validation and rejected replacement effects. */
export interface TravelContext {
  address?: string;
  latitude?: number;
  longitude?: number;
  checkinDate?: string;
  checkoutDate?: string;
  hotelName?: string;
  adults?: number;
  children?: number;
  /** Extra trip facts, forwarded without interpreting the keys. */
  context?: Record<string, string>;
}

/** Native event facts; description is a human-readable summary only. */
export type Stay22SDKEvent = { readonly description: string } & (
  | { readonly type: 'notificationChanged'; readonly receipt: NotificationReceipt }
  | { readonly type: 'locationUpdated' | 'notificationShown'; readonly destination: string }
  /** Delay is in seconds and retains native precision. */
  | { readonly type: 'notificationScheduled'; readonly destination: string; readonly delay: number }
  /** URL is the saved Stay22 link, even when a supplier opens. */
  | { readonly type: 'notificationClicked'; readonly destination: string; readonly url: string }
  | { readonly type: 'notificationBlocked' | 'notificationSkipped' | 'notificationCancelled'; readonly reason: string }
  | { readonly type: 'enabledChanged'; readonly isEnabled: boolean }
  | { readonly type: 'travelContextCleared' | 'unknown' }
);

export interface Stay22Plugin {
  /**
   * Initializes with your partner ID. A no-op once running, not a configuration-readiness signal.
   * Declare Stay22PartnerAID in Info.plist or com.stay22.sdk.PartnerAID in the manifest
   * when available, so the plugin can initialize before JavaScript runs on a cold start.
   */
  initialize(options: { aid: string }): Promise<void>;
  isInitialized(): Promise<{ value: boolean }>;
  /** Persisted consent gate; can be set before initialize. Opt-out revokes both modes. */
  setEnabled(options: { enabled: boolean }): Promise<void>;
  isEnabled(): Promise<{ value: boolean }>;
  /** Attribution campaign for subsequent requests. Omit to clear. */
  setCampaignId(options: { campaignId?: string }): Promise<void>;
  hasNotificationPermission(): Promise<{ value: boolean }>;
  requestNotificationPermission(): Promise<{ value: boolean }>;

  /** Acknowledges durable submission; inspect current state for server/OS resolution. */
  ensureNotification(request: NotificationRequest): Promise<NotificationResult>;
  /** Complete timing revision. Keep original reference and arguments when retrying. */
  updateNotification(options: NotificationUpdate): Promise<NotificationResult>;
  replaceNotification(options: { reference: NotificationReference; request: NotificationRequest }): Promise<NotificationResult>;
  cancelNotification(options: { reference: NotificationReference }): Promise<NotificationResult>;
  /** Read-only; unavailable differs from an available query with no retained offers. */
  getNotificationState(): Promise<NotificationQuery>;
  /** One bounded test of a complete trip, with no persistent force flag or timing options. */
  scheduleDiagnosticNotification(options: { trip: TravelContext }): Promise<NotificationResult>;
  /** @deprecated Revokes both modes. Prefer cancellation with a specific reference. */
  clearTravelContext(): Promise<void>;

  /**
   * On iOS, whether this plugin owns Capacitor's notification handler slot. False means
   * another plugin displaced it. Android taps use the SDK Activity and return true.
   */
  isNotificationHandlerInstalled(): Promise<{ value: boolean }>;
  addListener(eventName: 'stay22Event', listenerFunc: (event: Stay22SDKEvent) => void): Promise<PluginListenerHandle>;
  removeAllListeners(): Promise<void>;
}
