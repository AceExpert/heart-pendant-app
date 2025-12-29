import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
    start(): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>(
  'NativeBluetooth',
);