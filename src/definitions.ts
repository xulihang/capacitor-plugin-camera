export interface CameraPreviewPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
