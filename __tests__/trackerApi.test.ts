import { parsePayload, TrackerPayloadError } from '../src/native/trackerApi';

describe('native payload parsing', () => {
  it('accepts objects with required keys', () => {
    expect(parsePayload<{ a: number }>('{"a":1}', ['a']).a).toBe(1);
  });

  it('rejects malformed or incomplete payloads', () => {
    expect(() => parsePayload('not json')).toThrow(TrackerPayloadError);
    expect(() => parsePayload('42')).toThrow(TrackerPayloadError);
    expect(() => parsePayload('{"a":1}', ['b'])).toThrow('missing "b"');
  });
});
