var capacitorStay22 = (function (exports, core) {
    'use strict';

    // Capacitor defers dispatch. Snapshot at the public call so later caller mutations
    // cannot change the trip, revision or timing before native code receives it.
    function copyNotificationInput(input) {
        const encoded = JSON.stringify(input, (_key, value) => {
            if ((typeof value === 'number' && !Number.isFinite(value)) ||
                typeof value === 'function' || typeof value === 'symbol' || typeof value === 'bigint') {
                throw new TypeError('Notification input must contain JSON values and finite numbers.');
            }
            return value;
        });
        if (encoded === undefined)
            throw new TypeError('Notification input is required.');
        return JSON.parse(encoded);
    }

    const native = core.registerPlugin('Stay22');
    // Keep dispatch and listener ownership with Capacitor. Notification inputs alone
    // need synchronous snapshots before Capacitor's deferred promise dispatch.
    const Stay22 = {
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

    exports.Stay22 = Stay22;

    return exports;

})({}, capacitorExports);
//# sourceMappingURL=plugin.js.map
