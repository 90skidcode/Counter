/* global jest */
jest.mock(
  'react-native-safe-area-context',
  () => require('react-native-safe-area-context/jest/mock').default,
);

// The Kotlin TurboModule does not exist under Jest. Every method rejects unless a test
// provides an implementation, so tests never silently depend on fake data.
jest.mock('./src/native/NativeShortVideoTracker', () => {
  const handler = {
    get: (target, prop) => {
      if (prop === '__esModule') {
        return false;
      }
      if (!(prop in target)) {
        target[prop] = jest.fn(() =>
          Promise.reject(new Error(`unmocked native call ${String(prop)}`)),
        );
      }
      return target[prop];
    },
  };
  return { __esModule: true, default: new Proxy({}, handler) };
});
