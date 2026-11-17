import { registerPlugin } from '@capacitor/core';

import type { Stay22Plugin } from './definitions';
import { copyNotificationInput } from './notification-input';

const native = registerPlugin<Stay22Plugin>('Stay22');

// Keep dispatch and listener ownership with Capacitor. Notification inputs alone
// need synchronous snapshots before Capacitor's deferred promise dispatch.
const Stay22: Stay22Plugin = {
  initialize: native.initialize,
  isInitialized: native.isInitialized,
  setEnabled: native.setEnabled,
  isEnabled: native.isEnabled,
  setCampaignId: native.setCampaignId,
  hasNotificationPermission: native.hasNotificationPermission,
  requestNotificationPermission: native.requestNotificationPermission,
  clearTravelContext: native.clearTravelContext,
  isNotificationHandlerInstalled: native.isNotificationHandlerInstalled,
  addListener: native.addListener,
  removeAllListeners: native.removeAllListeners,
  ensureNotification: async (request) => native.ensureNotification(copyNotificationInput(request)),
  updateNotification: async (options) => native.updateNotification(copyNotificationInput(options)),
  replaceNotification: async (options) => native.replaceNotification(copyNotificationInput(options)),
  cancelNotification: async (options) => native.cancelNotification(copyNotificationInput(options)),
  getNotificationState: () => native.getNotificationState(),
  scheduleDiagnosticNotification: async (options) => native.scheduleDiagnosticNotification(copyNotificationInput(options)),
};

export * from './definitions';
export { Stay22 };
