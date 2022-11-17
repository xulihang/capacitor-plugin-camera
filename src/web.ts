import { WebPlugin } from '@capacitor/core';

import type { CameraPreviewPlugin } from './definitions';

export class CameraPreviewWeb extends WebPlugin implements CameraPreviewPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
