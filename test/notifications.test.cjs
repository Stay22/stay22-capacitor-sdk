const { test, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const methods = ['ensureNotification', 'updateNotification', 'replaceNotification', 'cancelNotification',
  'scheduleDiagnosticNotification', 'getNotificationState'];
const calls = [];
let response;
// Exercise Capacitor's actual deferred dispatch, where caller-owned objects used
// to remain mutable until a later microtask. Native business outcomes are fixtures.
globalThis.androidBridge = {};
globalThis.Capacitor = {
  PluginHeaders: [{ name: 'Stay22', methods: methods.map(name => ({ name, rtype: 'promise' })) }],
  nativePromise: async (plugin, method, args) => { calls.push({ plugin, method, args }); return response; },
};
const { Stay22 } = require('../dist/plugin.cjs.js');
beforeEach(() => {
  calls.length = 0;
  response = { outcome: 'rejected', receipt: null, previousEffect: 'uncertain', reason: 'not_initialized',
    capabilities: { windowGuarantees: ['targetSelection'], supportedHorizonMs: null } };
});

test('a complete request is copied before Capacitor defers the native call', async () => {
  const request = { trip: { address: 'Paris', context: { purpose: 'concert' } },
    timing: { type: 'after', delayMs: 1000 }, allowedWindows: { timeZone: 'Europe/Paris',
      periods: [{ weekdays: [1, 2], startMinute: 780, endMinute: 1080 }], requiredWindowGuarantee: 'prePostCheck' },
    expiresAtMs: 2000, actionExpiresAtMs: 3000 };
  const original = JSON.parse(JSON.stringify(request));
  const pending = Stay22.ensureNotification(request);
  request.trip.context.purpose = 'changed';
  request.allowedWindows.periods[0].weekdays.push(3);
  request.timing.delayMs = 5;
  assert.deepEqual(await pending, response);
  assert.deepEqual(calls, [{ plugin: 'Stay22', method: 'ensureNotification', args: original }]);
});

test('a reconstructed reference and original arguments reach each command without querying', async () => {
  const reference = JSON.parse('{"id":" original ","revision":3,"mode":"diagnostic"}');
  await Stay22.updateNotification({ reference, timing: { type: 'at', atMs: 5000 } });
  await Stay22.replaceNotification({ reference, request: { trip: { latitude: 91, checkinDate: '2026-02-30' } } });
  await Stay22.cancelNotification({ reference });
  assert.deepEqual(calls.map(call => call.method), ['updateNotification', 'replaceNotification', 'cancelNotification']);
  for (const call of calls) assert.deepEqual(call.args.reference, reference);
  assert.equal(calls[1].args.request.trip.latitude, 91);
});

test('non-JSON values cannot silently erase a constraint before native validation', async () => {
  for (const atMs of [NaN, Infinity, -Infinity, 1n, () => 5, Symbol('invalid')]) {
    await assert.rejects(Stay22.ensureNotification({ trip: { address: 'Paris' }, timing: { type: 'at', atMs } }));
  }
  assert.equal(calls.length, 0);
});

test('diagnostic and queries preserve their native meanings', async () => {
  await Stay22.scheduleDiagnosticNotification({ trip: { address: 'Paris', adults: 2 } });
  assert.deepEqual(calls[0].args, { trip: { address: 'Paris', adults: 2 } });
  response = { status: 'available', normal: null, diagnostic: null, native: { status: 'unavailable', reason: 'os_query_failed' } };
  assert.deepEqual(await Stay22.getNotificationState(), response);
  response = { status: 'unavailable', reason: 'storage_unavailable' };
  assert.deepEqual(await Stay22.getNotificationState(), response);
  assert.equal(calls.filter(call => call.method === 'getNotificationState').length, 2);
});
