// Capacitor defers dispatch. Snapshot at the public call so later caller mutations
// cannot change the trip, revision or timing before native code receives it.
export function copyNotificationInput<T>(input: T): T {
  const encoded = JSON.stringify(input, (_key, value: unknown) => {
    if ((typeof value === 'number' && !Number.isFinite(value)) ||
        typeof value === 'function' || typeof value === 'symbol' || typeof value === 'bigint') {
      throw new TypeError('Notification input must contain JSON values and finite numbers.');
    }
    return value;
  });
  if (encoded === undefined) throw new TypeError('Notification input is required.');
  return JSON.parse(encoded) as T;
}
