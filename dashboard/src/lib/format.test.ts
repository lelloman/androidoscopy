import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
    formatBytes,
    formatPercent,
    formatDuration,
    formatNumber,
    formatValue,
    formatTime,
    formatDate,
    formatDateTime,
    getRelativeTime
} from './format';

describe('formatBytes', () => {
    it('should format zero bytes', () => {
        expect(formatBytes(0)).toBe('0 B');
    });

    it('should format bytes', () => {
        expect(formatBytes(500)).toBe('500 B');
        expect(formatBytes(1)).toBe('1 B');
    });

    it('should format kilobytes', () => {
        expect(formatBytes(1024)).toBe('1.0 KB');
        expect(formatBytes(1536)).toBe('1.5 KB');
        expect(formatBytes(10240)).toBe('10.0 KB');
    });

    it('should format megabytes', () => {
        expect(formatBytes(1048576)).toBe('1.0 MB');
        expect(formatBytes(5242880)).toBe('5.0 MB');
    });

    it('should format gigabytes', () => {
        expect(formatBytes(1073741824)).toBe('1.0 GB');
        expect(formatBytes(2147483648)).toBe('2.0 GB');
    });

    it('should format terabytes', () => {
        expect(formatBytes(1099511627776)).toBe('1.0 TB');
    });

    it('should handle negative bytes', () => {
        expect(formatBytes(-1024)).toBe('-1.0 KB');
        expect(formatBytes(-1048576)).toBe('-1.0 MB');
    });
});

describe('formatPercent', () => {
    it('should format decimal values (0-1) as percentages', () => {
        expect(formatPercent(0.5)).toBe('50%');
        expect(formatPercent(0.25)).toBe('25%');
        expect(formatPercent(0.75)).toBe('75%');
        expect(formatPercent(1)).toBe('100%');
        expect(formatPercent(0)).toBe('0%');
    });

    it('should handle already percentage values (>1)', () => {
        expect(formatPercent(50)).toBe('50%');
        expect(formatPercent(100)).toBe('100%');
    });
});

describe('formatDuration', () => {
    it('should format milliseconds', () => {
        expect(formatDuration(500)).toBe('500ms');
        expect(formatDuration(999)).toBe('999ms');
    });

    it('should format seconds', () => {
        expect(formatDuration(1000)).toBe('1s');
        expect(formatDuration(30000)).toBe('30s');
        expect(formatDuration(59000)).toBe('59s');
    });

    it('should format minutes and seconds', () => {
        expect(formatDuration(60000)).toBe('1m 0s');
        expect(formatDuration(90000)).toBe('1m 30s');
        expect(formatDuration(3540000)).toBe('59m 0s');
    });

    it('should format hours and minutes', () => {
        expect(formatDuration(3600000)).toBe('1h 0m');
        expect(formatDuration(5400000)).toBe('1h 30m');
        expect(formatDuration(7200000)).toBe('2h 0m');
    });
});

describe('formatNumber', () => {
    it('should format numbers with locale', () => {
        // Note: This depends on locale, testing basic functionality
        expect(typeof formatNumber(1234567)).toBe('string');
        expect(formatNumber(0)).toBe('0');
    });
});

describe('formatValue', () => {
    it('should return dash for null/undefined', () => {
        expect(formatValue(null)).toBe('-');
        expect(formatValue(undefined)).toBe('-');
    });

    it('should format bytes when format is "bytes"', () => {
        expect(formatValue(1024, 'bytes')).toBe('1.0 KB');
        expect(formatValue(1048576, 'bytes')).toBe('1.0 MB');
    });

    it('should format percent when format is "percent"', () => {
        expect(formatValue(0.5, 'percent')).toBe('50%');
        expect(formatValue(0.75, 'percent')).toBe('75%');
    });

    it('should format duration when format is "duration"', () => {
        expect(formatValue(60000, 'duration')).toBe('1m 0s');
        expect(formatValue(3600000, 'duration')).toBe('1h 0m');
    });

    it('should format number when format is "number"', () => {
        expect(typeof formatValue(1234, 'number')).toBe('string');
    });

    it('should convert string numbers', () => {
        expect(formatValue('1024', 'bytes')).toBe('1.0 KB');
    });

    it('should return string representation for non-number values', () => {
        expect(formatValue('hello')).toBe('hello');
        expect(formatValue(true)).toBe('true');
    });
});

describe('formatTime', () => {
    it('should format ISO time string', () => {
        const result = formatTime('2024-12-05T14:30:45.123Z');
        // The exact output depends on locale/timezone, but should contain time parts
        expect(result).toMatch(/\d{2}:\d{2}:\d{2}/);
    });

    it('should handle invalid input gracefully', () => {
        // new Date('invalid') returns Invalid Date, not throwing
        const result = formatTime('invalid');
        expect(result).toMatch(/Invalid Date|invalid/);
    });
});

describe('formatDate', () => {
    it('should format ISO date string', () => {
        const result = formatDate('2024-12-05T14:30:45.123Z');
        // Should contain date parts
        expect(result).toMatch(/2024/);
    });

    it('should handle invalid input gracefully', () => {
        const result = formatDate('invalid');
        expect(result).toMatch(/Invalid Date|invalid/);
    });
});

describe('formatDateTime', () => {
    it('should format ISO datetime string', () => {
        const result = formatDateTime('2024-12-05T14:30:45.123Z');
        // Should contain both date and time
        expect(result).toMatch(/2024/);
        expect(result).toMatch(/\d{2}:\d{2}/);
    });

    it('should handle invalid input gracefully', () => {
        const result = formatDateTime('invalid');
        expect(result).toMatch(/Invalid Date|invalid/);
    });
});

describe('getRelativeTime', () => {
    beforeEach(() => {
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it('should return "just now" for recent times', () => {
        const now = new Date('2024-12-05T14:30:00.000Z');
        vi.setSystemTime(now);

        const recent = new Date('2024-12-05T14:29:30.000Z').toISOString();
        expect(getRelativeTime(recent)).toBe('just now');
    });

    it('should return minutes ago', () => {
        const now = new Date('2024-12-05T14:30:00.000Z');
        vi.setSystemTime(now);

        const minutesAgo = new Date('2024-12-05T14:25:00.000Z').toISOString();
        expect(getRelativeTime(minutesAgo)).toBe('5m ago');
    });

    it('should return hours ago', () => {
        const now = new Date('2024-12-05T14:30:00.000Z');
        vi.setSystemTime(now);

        const hoursAgo = new Date('2024-12-05T12:30:00.000Z').toISOString();
        expect(getRelativeTime(hoursAgo)).toBe('2h ago');
    });

    it('should return days ago', () => {
        const now = new Date('2024-12-05T14:30:00.000Z');
        vi.setSystemTime(now);

        const daysAgo = new Date('2024-12-02T14:30:00.000Z').toISOString();
        expect(getRelativeTime(daysAgo)).toBe('3d ago');
    });

    it('should return formatted date for old dates', () => {
        const now = new Date('2024-12-05T14:30:00.000Z');
        vi.setSystemTime(now);

        const oldDate = new Date('2024-11-15T14:30:00.000Z').toISOString();
        const result = getRelativeTime(oldDate);
        expect(result).toMatch(/2024/);
    });

    it('should handle invalid input gracefully', () => {
        const result = getRelativeTime('invalid');
        expect(result).toMatch(/Invalid Date|invalid/);
    });
});
