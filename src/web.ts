import { PluginListenerHandle, WebPlugin } from '@capacitor/core';

import type { BLEMessagingPlugin } from './definitions';

export class BLEMessagingWeb extends WebPlugin implements BLEMessagingPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }

  async initializePeripheral(): Promise<void> {
    throw this.unimplemented('Not implemented on web.');
  }

  async initializeCentral(): Promise<void> {
    throw this.unimplemented('Not implemented on web.');
  }

  async startAdvertising(): Promise<void> {
    throw this.unimplemented('Not implemented on web.');
  }

  async stopAdvertising(): Promise<void> {
    throw this.unimplemented('Not implemented on web.');
  }

  async startScan(): Promise<void> {
    throw this.unimplemented('Not implemented on web.');
  }

  async stopScan(): Promise<void> {
    throw this.unimplemented('Not implemented on web.');
  }

  async sendMessage(): Promise<void> {
    throw this.unimplemented('Not implemented on web.');
  }

  async connectToDevice(): Promise<void> {
    throw this.unimplemented('Not implemented on web.');
  }

  async disconnectFromDevice(): Promise<void> {
    throw this.unimplemented('Not implemented on web.');
  }

  async isAdvertising(): Promise<{ isAdvertising: boolean }> {
    throw this.unimplemented('Not implemented on web.');
  }

  async isScanning(): Promise<{ isScanning: boolean }> {
    throw this.unimplemented('Not implemented on web.');
  }
  
  async addListener(): Promise<PluginListenerHandle> {
    throw this.unimplemented('Not implemented on web.');
  }

  async cleanup(): Promise<void> {
    throw this.unimplemented('Not implemented on web.');
  }
}
