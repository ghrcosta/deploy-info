import { timeAgo, fmtTime } from './time-ago';

/** The instant the specs freeze the clock at (2025-06-01T12:00Z). */
const NOW = Date.UTC(2025, 5, 1, 12, 0, 0);

describe('time-ago', () => {
  beforeEach(() => {
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(NOW));
  });

  afterEach(() => {
    jasmine.clock().uninstall();
  });

  describe('timeAgo', () => {
    it('renders relative minutes below one hour', () => {
      expect(timeAgo(NOW - 5 * 60_000)).toBe('5m ago');
      expect(timeAgo(NOW - 59 * 60_000)).toBe('59m ago');
    });

    it('renders relative hours below one day', () => {
      expect(timeAgo(NOW - 3 * 3_600_000)).toBe('3h ago');
      expect(timeAgo(NOW - 23 * 3_600_000)).toBe('23h ago');
    });

    it('renders relative days beyond one day', () => {
      expect(timeAgo(NOW - 2 * 86_400_000)).toBe('2d ago');
    });

    it('rounds sub-minute and future timestamps up to one minute', () => {
      expect(timeAgo(NOW)).toBe('1m ago');
      expect(timeAgo(NOW + 60_000)).toBe('1m ago');
    });
  });

  describe('fmtTime', () => {
    it('renders an absolute UTC timestamp', () => {
      expect(fmtTime(Date.UTC(2025, 0, 1, 13, 45))).toBe('2025-01-01 13:45 UTC');
    });

    it('zero-pads month, day, hours and minutes', () => {
      expect(fmtTime(Date.UTC(2025, 8, 3, 7, 5))).toBe('2025-09-03 07:05 UTC');
    });
  });
});
