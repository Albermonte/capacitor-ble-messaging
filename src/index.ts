import { registerPlugin } from '@capacitor/core';

import type { BLEMessagingPlugin } from './definitions';

const BLEMessaging = registerPlugin<BLEMessagingPlugin>('BLEMessaging', {
  web: () => import('./web').then(m => new m.BLEMessagingWeb()),
});

export * from './definitions';
export { BLEMessaging };
