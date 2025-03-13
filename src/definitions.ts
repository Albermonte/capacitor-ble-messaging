export interface BLEMessagingPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
