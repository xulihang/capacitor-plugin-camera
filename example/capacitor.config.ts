import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.tonyxlh.example.camera',
  appName: 'Camera',
  webDir: 'build',
  server: {
    androidScheme: 'https'
  }
};

export default config;
