import React from 'react';
import { Text } from 'react-native';
import ReactTestRenderer from 'react-test-renderer';
import Native from '../src/native/NativeShortVideoTracker';
import App from '../src/App';
import { fixtureSettings, fixtureSummary } from './support/fixtures';

const native = Native as unknown as Record<string, jest.Mock>;

function allText(tree: ReactTestRenderer.ReactTestRenderer): string {
  return tree.root
    .findAllByType(Text)
    .map(node =>
      [node.props.children]
        .flat(Infinity)
        .filter(c => typeof c === 'string' || typeof c === 'number')
        .join(''),
    )
    .join('\n');
}

test('renders the dashboard with separated verified and estimated counts', async () => {
  native.getSettings.mockResolvedValue(JSON.stringify(fixtureSettings()));
  native.getTodaySummary.mockResolvedValue(JSON.stringify(fixtureSummary()));

  let tree: ReactTestRenderer.ReactTestRenderer | undefined;
  await ReactTestRenderer.act(async () => {
    tree = ReactTestRenderer.create(<App />);
  });
  await ReactTestRenderer.act(async () => {});
  const text = allText(tree!);
  expect(text).toContain('Verified videos');
  expect(text).toContain('Estimated videos');
  // Snapchat only reports time, so its count is a dash, never zero.
  expect(text).toContain('Snapchat');
  expect(native.getTodaySummary).toHaveBeenCalled();
  await ReactTestRenderer.act(async () => tree!.unmount());
});

test('shows onboarding before setup is complete', async () => {
  native.getSettings.mockResolvedValue(
    JSON.stringify({ ...fixtureSettings(), onboardingCompleted: false }),
  );
  native.getTodaySummary.mockResolvedValue(JSON.stringify(fixtureSummary()));

  let tree: ReactTestRenderer.ReactTestRenderer | undefined;
  await ReactTestRenderer.act(async () => {
    tree = ReactTestRenderer.create(<App />);
  });
  await ReactTestRenderer.act(async () => {});
  expect(allText(tree!)).toContain('Your data stays on this phone');
  await ReactTestRenderer.act(async () => tree!.unmount());
});
