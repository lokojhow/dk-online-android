import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.dktelecom.dkonline',
  appName: 'DK Online',
  webDir: 'www',
  android: {
    allowMixedContent: false,
    backgroundColor: '#020817'
  },
  server: {
    androidScheme: 'https'
  }
};

export default config;
