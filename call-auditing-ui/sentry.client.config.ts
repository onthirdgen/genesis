import * as Sentry from '@sentry/nextjs';
import { env } from '@/lib/env';

Sentry.init({
  dsn: env.NEXT_PUBLIC_SENTRY_DSN,

  // Set tracesSampleRate to 1.0 to capture 100% of transactions for tracing.
  // We recommend adjusting this value in production to reduce volume and costs.
  tracesSampleRate: process.env.NODE_ENV === 'production' ? 0.1 : 1.0,

  // Setting this option to true will print useful information to the console while you're setting up Sentry.
  debug: false,

  // Replay settings
  replaysOnErrorSampleRate: 1.0, // Capture 100% of sessions with errors
  replaysSessionSampleRate: process.env.NODE_ENV === 'production' ? 0.1 : 0.5,

  integrations: [
    Sentry.replayIntegration({
      // Additional replay configuration goes in here, for example:
      maskAllText: true,
      blockAllMedia: true,
    }),
  ],

  // Environment
  environment: process.env.NODE_ENV,

  // Ignore specific errors
  ignoreErrors: [
    // Browser extensions
    /extensions\//i,
    /^Script error\.?$/,
    /^Javascript error: Script error\.? on line 0$/,
    // React hydration errors (non-critical)
    /Hydration failed because/i,
    /There was an error while hydrating/i,
  ],

  beforeSend(event, hint) {
    // Filter out errors from browser extensions
    if (event.exception?.values?.[0]?.stacktrace?.frames) {
      const frames = event.exception.values[0].stacktrace.frames;
      if (frames.some((frame) => frame.filename?.includes('extension://'))) {
        return null;
      }
    }
    return event;
  },
});
