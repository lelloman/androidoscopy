import { describe, it, expect } from 'vitest';
import { getThresholdStyle } from './threshold';

describe('getThresholdStyle', () => {
    describe('with default thresholds', () => {
        it('should return "success" for low ratios', () => {
            expect(getThresholdStyle(0)).toBe('success');
            expect(getThresholdStyle(0.25)).toBe('success');
            expect(getThresholdStyle(0.5)).toBe('success');
            expect(getThresholdStyle(0.74)).toBe('success');
        });

        it('should return "warning" for medium ratios', () => {
            expect(getThresholdStyle(0.75)).toBe('warning');
            expect(getThresholdStyle(0.8)).toBe('warning');
            expect(getThresholdStyle(0.89)).toBe('warning');
        });

        it('should return "danger" for high ratios', () => {
            expect(getThresholdStyle(0.9)).toBe('danger');
            expect(getThresholdStyle(0.95)).toBe('danger');
            expect(getThresholdStyle(1)).toBe('danger');
        });

        it('should handle undefined thresholds', () => {
            expect(getThresholdStyle(0.5, undefined)).toBe('success');
            expect(getThresholdStyle(0.8, undefined)).toBe('warning');
            expect(getThresholdStyle(0.95, undefined)).toBe('danger');
        });

        it('should handle empty thresholds array', () => {
            expect(getThresholdStyle(0.5, [])).toBe('success');
            expect(getThresholdStyle(0.8, [])).toBe('warning');
            expect(getThresholdStyle(0.95, [])).toBe('danger');
        });
    });

    describe('with custom thresholds', () => {
        it('should use custom thresholds', () => {
            const thresholds = [
                { value: 0.5, style: 'warning' as const },
                { value: 0.8, style: 'danger' as const },
            ];

            expect(getThresholdStyle(0.3, thresholds)).toBe('success');
            expect(getThresholdStyle(0.5, thresholds)).toBe('warning');
            expect(getThresholdStyle(0.7, thresholds)).toBe('warning');
            expect(getThresholdStyle(0.8, thresholds)).toBe('danger');
            expect(getThresholdStyle(1, thresholds)).toBe('danger');
        });

        it('should sort thresholds correctly', () => {
            // Thresholds in non-sorted order
            const thresholds = [
                { value: 0.8, style: 'danger' as const },
                { value: 0.3, style: 'success' as const },
                { value: 0.5, style: 'warning' as const },
            ];

            expect(getThresholdStyle(0.2, thresholds)).toBe('success');
            expect(getThresholdStyle(0.35, thresholds)).toBe('success');
            expect(getThresholdStyle(0.6, thresholds)).toBe('warning');
            expect(getThresholdStyle(0.9, thresholds)).toBe('danger');
        });

        it('should handle single threshold', () => {
            const thresholds = [{ value: 0.5, style: 'warning' as const }];

            expect(getThresholdStyle(0.3, thresholds)).toBe('success');
            expect(getThresholdStyle(0.5, thresholds)).toBe('warning');
            expect(getThresholdStyle(0.9, thresholds)).toBe('warning');
        });
    });

    describe('edge cases', () => {
        it('should handle ratios greater than 1', () => {
            expect(getThresholdStyle(1.5)).toBe('danger');
            expect(getThresholdStyle(2)).toBe('danger');
        });

        it('should handle negative ratios', () => {
            expect(getThresholdStyle(-0.1)).toBe('success');
            expect(getThresholdStyle(-1)).toBe('success');
        });

        it('should handle exact threshold boundaries', () => {
            const thresholds = [
                { value: 0.5, style: 'warning' as const },
            ];

            // At exactly the threshold value, should match
            expect(getThresholdStyle(0.5, thresholds)).toBe('warning');
            // Just below should not match
            expect(getThresholdStyle(0.499, thresholds)).toBe('success');
        });
    });
});
