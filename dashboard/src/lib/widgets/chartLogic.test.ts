import { describe, it, expect } from 'vitest';
import { calculateChartPath, getMinMax, type ChartDataPoint } from './chartLogic';

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
