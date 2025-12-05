import type { Threshold } from '../types/protocol';

/**
 * Get style based on threshold configuration.
 * @param ratio Value ratio (0-1)
 * @param thresholds Optional threshold configuration
 * @returns Style class ('success', 'warning', or 'danger')
 */
export function getThresholdStyle(ratio: number, thresholds?: Threshold[]): string {
    if (!thresholds || thresholds.length === 0) {
        // Default thresholds
        if (ratio >= 0.9) return 'danger';
        if (ratio >= 0.75) return 'warning';
        return 'success';
    }

    // Sort thresholds by value descending
    const sorted = [...thresholds].sort((a, b) => b.value - a.value);
    for (const threshold of sorted) {
        if (ratio >= threshold.value) {
            return threshold.style;
        }
    }
    return 'success';
}
