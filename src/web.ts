import { WebPlugin } from '@capacitor/core';

import type { BLEMessagingPlugin } from './definitions';

export class BLEMessagingWeb extends WebPlugin implements BLEMessagingPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
