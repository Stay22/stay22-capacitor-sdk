import type { TravelContext } from './definitions';

export type NotificationMode = 'normal' | 'diagnostic';
export type NotificationWindowGuarantee = 'targetSelection' | 'prePostCheck' | 'visiblePresentation';
export type NotificationTiming =
  | { readonly type: 'accountDefault' }
  | { readonly type: 'after'; readonly delayMs: number }
  | { readonly type: 'notBefore'; readonly atMs: number }
  | { readonly type: 'at'; readonly atMs: number };

/** ISO weekdays Monday=1..Sunday=7; omitted means every day. End is exclusive. */
export interface NotificationLocalWindow {
  readonly weekdays?: readonly number[];
  readonly startMinute: number;
  readonly endMinute: number;
}
/** Empty periods permit no notification. Unsupported guarantees reject rather than weaken. */
export interface NotificationAllowedWindows {
  readonly timeZone: string;
  readonly periods: readonly NotificationLocalWindow[];
  readonly requiredWindowGuarantee: NotificationWindowGuarantee;
}
/** One complete trip; omitted fields never inherit an earlier notification's trip. */
export interface NotificationRequest {
  readonly trip: TravelContext;
  readonly timing?: NotificationTiming;
  readonly allowedWindows?: NotificationAllowedWindows;
  readonly expiresAtMs?: number;
  readonly actionExpiresAtMs?: number;
}
/** Persist this with original command arguments. Never rebase a retry onto a newer query. */
export interface NotificationReference {
  readonly id: string;
  readonly revision: number;
  readonly mode: NotificationMode;
}
export interface NotificationUpdate {
  readonly reference: NotificationReference;
  /** Complete new timing intent; omitted windows remove the caller restriction. */
  readonly timing: NotificationTiming;
  readonly allowedWindows?: NotificationAllowedWindows;
  readonly expiresAtMs?: number;
  readonly actionExpiresAtMs?: number;
}
export interface NotificationCapabilities {
  readonly windowGuarantees: readonly NotificationWindowGuarantee[];
  readonly supportedHorizonMs: number | null;
}
export type NotificationExpirySource = 'explicitDeadline' | 'accountPolicy' | 'diagnosticPolicy';
export interface NotificationRequestedTiming {
  readonly timing: NotificationTiming;
  readonly submittedAtMs: number;
  readonly expiresAtMs: number;
  readonly actionExpiresAtMs: number;
  readonly executionExpirySource: NotificationExpirySource;
  readonly actionExpirySource: NotificationExpirySource;
  readonly allowedWindows: NotificationAllowedWindows | null;
}
export interface NotificationAcceptedTiming {
  readonly targetAtMs: number;
  readonly intervalStartAtMs: number;
  readonly intervalEndAtMs: number;
  readonly guarantee: NotificationWindowGuarantee;
}
export type NotificationRequestStatus = 'preparing' | 'awaitingAuthorization' | 'applied' | 'rejected' | 'cancelled' | 'expired';
export type NotificationExecutionPhase = 'queueing' | 'queued' | 'posting' | 'completed' | 'revoking' | 'revoked' | 'blocked' | 'expired';
export type NotificationEvidence = 'none' | 'osAccepted' | 'androidPosted' | 'iosObserved' | 'unknown';
export type NotificationPreviousEffect = 'none' | 'unchanged' | 'revoked' | 'cancellationRequested' | 'uncertain';
export interface NotificationExecution {
  readonly revision: number;
  readonly requested: NotificationRequestedTiming;
  readonly acceptedTiming: NotificationAcceptedTiming;
  readonly actionExpiresAtMs: number;
  readonly phase: NotificationExecutionPhase;
  readonly evidence: NotificationEvidence;
  readonly observedAtMs: number | null;
  readonly capabilities: NotificationCapabilities;
  readonly reason: string | null;
}
/** SDK facts at one observation, not a live mutable scheduling object. */
export interface NotificationReceipt {
  readonly id: string;
  readonly revision: number;
  readonly mode: NotificationMode;
  readonly reference: NotificationReference;
  readonly requestStatus: NotificationRequestStatus;
  readonly requested: NotificationRequestedTiming | null;
  readonly execution: NotificationExecution | null;
  readonly previousEffect: NotificationPreviousEffect;
  readonly capabilities: NotificationCapabilities;
  readonly reason: string | null;
}
export interface NotificationResult {
  readonly outcome: 'submitted' | 'kept' | 'replaced' | 'cancelled' | 'conflict' | 'rejected';
  readonly receipt: NotificationReceipt | null;
  readonly previousEffect: NotificationPreviousEffect;
  readonly reason: string | null;
  readonly capabilities: NotificationCapabilities;
}
export interface NotificationNativeEntry {
  readonly identifier: string;
  readonly attemptId: string | null;
  readonly mode: NotificationMode | null;
  readonly kind: 'work' | 'pendingNotification' | 'deliveredNotification';
  /** Raw OS state; this does not establish visible presentation. */
  readonly status: string;
  readonly deliveredAtMs: number | null;
}
export type NotificationNativeQuery =
  | { readonly status: 'available'; readonly observedAtMs: number; readonly entries: readonly NotificationNativeEntry[] }
  | { readonly status: 'unavailable'; readonly reason: string };
/** Receipts and OS observations are independent reads, without recovery or reporting effects. */
export type NotificationQuery =
  | { readonly status: 'available'; readonly normal: NotificationReceipt | null;
      readonly diagnostic: NotificationReceipt | null; readonly native: NotificationNativeQuery }
  | { readonly status: 'unavailable'; readonly reason: string };
