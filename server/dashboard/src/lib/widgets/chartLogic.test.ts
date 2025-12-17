import { describe, it, expect } from 'vitest';
import {
    calculateChartPath,
    calculateChartPathWithView,
    getMinMax,
    getStats,
    getPointAtX,
    zoom,
    pan,
    exportToCSV,
    formatTimestamp,
    filterByTimeWindow,
    type ChartDataPoint,
    type ViewWindow
} from './chartLogic';

describe('calculateChartPath', () => {
    it('returns empty string for less than 2 points', () => {
        expect(calculateChartPath([], 60)).toBe('');
        expect(calculateChartPath([{ value: 10, timestamp: 1 }], 60)).toBe('');
    });

    it('generates valid SVG path for 2 points', () => {
        const points: ChartDataPoint[] = [
            { value: 0, timestamp: 1 },
            { value: 100, timestamp: 2 }
        ];
        const path = calculateChartPath(points, 2, 100, 100, 5);

        expect(path).toMatch(/^M \d+,\d+ L \d+,\d+$/);
        expect(path).toContain('M 0,');
        expect(path).toContain('L 100,');
    });

    it('handles constant values', () => {
        const points: ChartDataPoint[] = [
            { value: 50, timestamp: 1 },
            { value: 50, timestamp: 2 },
            { value: 50, timestamp: 3 }
        ];
        const path = calculateChartPath(points, 3, 100, 100, 5);

        expect(path).toBeTruthy();
        // 3 points = 1 M command + 2 L commands, so 2 ' L ' separators = 3 segments
        expect(path.split(' L ').length).toBe(3);
    });

    it('scales values correctly within height', () => {
        const points: ChartDataPoint[] = [
            { value: 0, timestamp: 1 },
            { value: 100, timestamp: 2 }
        ];
        const path = calculateChartPath(points, 2, 100, 100, 5);

        // First point (value=0, min) should be at bottom (y = height - padding = 95)
        // Second point (value=100, max) should be at top (y = padding = 5)
        expect(path).toBe('M 0,95 L 100,5');
    });
});

describe('getMinMax', () => {
    it('returns default values for empty array', () => {
        const result = getMinMax([]);
        expect(result.min).toBe(0);
        expect(result.max).toBe(100);
    });

    it('returns correct min and max for single point', () => {
        const result = getMinMax([{ value: 42, timestamp: 1 }]);
        expect(result.min).toBe(42);
        expect(result.max).toBe(42);
    });

    it('returns correct min and max for multiple points', () => {
        const points: ChartDataPoint[] = [
            { value: 10, timestamp: 1 },
            { value: 50, timestamp: 2 },
            { value: 30, timestamp: 3 },
            { value: 80, timestamp: 4 }
        ];
        const result = getMinMax(points);
        expect(result.min).toBe(10);
        expect(result.max).toBe(80);
    });

    it('handles negative values', () => {
        const points: ChartDataPoint[] = [
            { value: -20, timestamp: 1 },
            { value: 10, timestamp: 2 }
        ];
        const result = getMinMax(points);
        expect(result.min).toBe(-20);
        expect(result.max).toBe(10);
    });
});

describe('getStats', () => {
    it('returns zeros for empty array', () => {
        const result = getStats([]);
        expect(result.min).toBe(0);
        expect(result.max).toBe(0);
        expect(result.avg).toBe(0);
    });

    it('calculates correct stats for single point', () => {
        const result = getStats([{ value: 42, timestamp: 1 }]);
        expect(result.min).toBe(42);
        expect(result.max).toBe(42);
        expect(result.avg).toBe(42);
    });

    it('calculates correct stats for multiple points', () => {
        const points: ChartDataPoint[] = [
            { value: 10, timestamp: 1 },
            { value: 20, timestamp: 2 },
            { value: 30, timestamp: 3 }
        ];
        const result = getStats(points);
        expect(result.min).toBe(10);
        expect(result.max).toBe(30);
        expect(result.avg).toBe(20);
    });
});

describe('calculateChartPathWithView', () => {
    it('returns empty string for less than 2 visible points', () => {
        const view: ViewWindow = { startIndex: 0, endIndex: 0 };
        expect(calculateChartPathWithView([{ value: 10, timestamp: 1 }], view)).toBe('');
    });

    it('uses only visible points within view window', () => {
        const points: ChartDataPoint[] = [
            { value: 0, timestamp: 1 },
            { value: 50, timestamp: 2 },
            { value: 100, timestamp: 3 },
            { value: 50, timestamp: 4 },
            { value: 0, timestamp: 5 }
        ];
        const view: ViewWindow = { startIndex: 1, endIndex: 3 };
        const path = calculateChartPathWithView(points, view, 100, 100, 5);

        expect(path).toBeTruthy();
        // Should have 3 points (indices 1, 2, 3)
        expect(path.split(' L ').length).toBe(3);
    });
});

describe('zoom', () => {
    it('zooms in by reducing view size', () => {
        const view: ViewWindow = { startIndex: 0, endIndex: 100 };
        const result = zoom(view, 100, true);
        expect(result.endIndex - result.startIndex).toBeLessThan(100);
    });

    it('zooms out by increasing view size', () => {
        const view: ViewWindow = { startIndex: 20, endIndex: 80 };
        const result = zoom(view, 100, false);
        expect(result.endIndex - result.startIndex).toBeGreaterThan(60);
    });

    it('respects minimum view size', () => {
        const view: ViewWindow = { startIndex: 45, endIndex: 55 };
        const result = zoom(view, 100, true);
        expect(result.endIndex - result.startIndex).toBeGreaterThanOrEqual(5);
    });

    it('does not exceed total points', () => {
        const view: ViewWindow = { startIndex: 0, endIndex: 90 };
        const result = zoom(view, 100, false);
        expect(result.endIndex).toBeLessThanOrEqual(100);
        expect(result.startIndex).toBeGreaterThanOrEqual(0);
    });
});

describe('pan', () => {
    it('pans left with positive delta', () => {
        const view: ViewWindow = { startIndex: 30, endIndex: 70 };
        const result = pan(view, 100, 0.1);
        expect(result.startIndex).toBeLessThan(30);
        expect(result.endIndex).toBeLessThan(70);
    });

    it('pans right with negative delta', () => {
        const view: ViewWindow = { startIndex: 30, endIndex: 70 };
        const result = pan(view, 100, -0.1);
        expect(result.startIndex).toBeGreaterThan(30);
        expect(result.endIndex).toBeGreaterThan(70);
    });

    it('clamps to left boundary', () => {
        const view: ViewWindow = { startIndex: 5, endIndex: 45 };
        const result = pan(view, 100, 0.5);
        expect(result.startIndex).toBe(0);
    });

    it('clamps to right boundary', () => {
        const view: ViewWindow = { startIndex: 55, endIndex: 95 };
        const result = pan(view, 100, -0.5);
        expect(result.endIndex).toBe(100);
    });
});

describe('getPointAtX', () => {
    const points: ChartDataPoint[] = [
        { value: 10, timestamp: 1000 },
        { value: 20, timestamp: 2000 },
        { value: 30, timestamp: 3000 },
        { value: 40, timestamp: 4000 }
    ];

    it('returns null for empty array', () => {
        const view: ViewWindow = { startIndex: 0, endIndex: 0 };
        expect(getPointAtX([], 50, 100, view)).toBeNull();
    });

    it('returns first point at x=0', () => {
        const view: ViewWindow = { startIndex: 0, endIndex: 4 };
        const result = getPointAtX(points, 0, 100, view);
        expect(result?.value).toBe(10);
    });

    it('returns last point at x=containerWidth', () => {
        const view: ViewWindow = { startIndex: 0, endIndex: 4 };
        const result = getPointAtX(points, 100, 100, view);
        expect(result?.value).toBe(40);
    });

    it('returns middle point at x=50%', () => {
        const view: ViewWindow = { startIndex: 0, endIndex: 4 };
        const result = getPointAtX(points, 50, 100, view);
        // With 4 points, 50% should be around index 1.5, rounded to 2
        expect(result?.value).toBe(30);
    });
});

describe('exportToCSV', () => {
    it('generates valid CSV with header', () => {
        const points: ChartDataPoint[] = [
            { value: 10, timestamp: 1000 },
            { value: 20, timestamp: 2000 }
        ];
        const csv = exportToCSV(points, 'Test');

        const lines = csv.split('\n');
        expect(lines[0]).toBe('timestamp,datetime,value');
        expect(lines.length).toBe(3); // header + 2 rows
    });

    it('includes correct values', () => {
        const points: ChartDataPoint[] = [
            { value: 42, timestamp: 1609459200000 } // 2021-01-01T00:00:00.000Z
        ];
        const csv = exportToCSV(points, 'Test');

        expect(csv).toContain('1609459200000');
        expect(csv).toContain('42');
    });
});

describe('formatTimestamp', () => {
    it('formats timestamp to time string', () => {
        const timestamp = new Date('2024-01-15T14:30:45').getTime();
        const result = formatTimestamp(timestamp);

        // Should contain hours, minutes, seconds
        expect(result).toMatch(/\d{1,2}:\d{2}:\d{2}/);
    });
});

describe('filterByTimeWindow', () => {
    it('returns all points for Infinity window', () => {
        const points: ChartDataPoint[] = [
            { value: 10, timestamp: 1000 },
            { value: 20, timestamp: 2000 }
        ];
        const result = filterByTimeWindow(points, Infinity);
        expect(result).toHaveLength(2);
    });

    it('returns empty array for empty input', () => {
        const result = filterByTimeWindow([], 60);
        expect(result).toHaveLength(0);
    });

    it('filters points outside time window', () => {
        const now = Date.now();
        const points: ChartDataPoint[] = [
            { value: 10, timestamp: now - 120000 }, // 2 minutes ago
            { value: 20, timestamp: now - 30000 },  // 30 seconds ago
            { value: 30, timestamp: now - 10000 }   // 10 seconds ago
        ];
        const result = filterByTimeWindow(points, 60); // 1 minute window
        expect(result).toHaveLength(2);
        expect(result[0].value).toBe(20);
        expect(result[1].value).toBe(30);
    });
});
