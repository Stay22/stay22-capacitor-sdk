import { registerPlugin } from '@capacitor/core';

import type { Stay22Plugin } from './definitions';

const Stay22 = registerPlugin<Stay22Plugin>('Stay22');

export * from './definitions';
export { Stay22 };
