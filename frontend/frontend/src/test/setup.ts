import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// Component tests render into a shared jsdom document; without this a stale tree
// from the previous test would still answer queries in the next one.
afterEach(() => {
  cleanup();
});
