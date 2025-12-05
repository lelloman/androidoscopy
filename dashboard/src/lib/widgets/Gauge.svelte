<script lang="ts">
    import type { GaugeWidget, Threshold } from '../types/protocol';
    import { evaluateNumberPath } from '../jsonpath';
    import { formatValue } from '../format';

    interface Props {
        widget: GaugeWidget;
        data: unknown;
    }

    let { widget, data }: Props = $props();

    let value = $derived(evaluateNumberPath(data, widget.value_path, 0));
    let max = $derived(evaluateNumberPath(data, widget.max_path, 100));
    let percentage = $derived(max > 0 ? (value / max) * 100 : 0);
    let formattedValue = $derived(formatValue(value, widget.format));
    let formattedMax = $derived(formatValue(max, widget.format));

    let style = $derived(getThresholdStyle(percentage / 100, widget.thresholds));

    function getThresholdStyle(ratio: number, thresholds?: Threshold[]): string {
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
</script>

<div class="gauge-widget">
    <div class="header">
        <span class="label">{widget.label}</span>
        <span class="value">{formattedValue} / {formattedMax}</span>
    </div>
    <div class="bar">
        <div
            class="fill {style}"
            style="width: {Math.min(percentage, 100)}%"
        ></div>
    </div>
</div>

<style>
    .gauge-widget {
        display: flex;
        flex-direction: column;
        padding: 1rem;
        background: var(--surface-color, #1e1e1e);
        border-radius: 8px;
        min-width: 180px;
    }

    .header {
        display: flex;
        justify-content: space-between;
        align-items: baseline;
        margin-bottom: 0.5rem;
    }

    .label {
        font-size: 0.75rem;
        color: var(--text-muted, #888);
        text-transform: uppercase;
        letter-spacing: 0.05em;
    }

    .value {
        font-size: 0.875rem;
        color: var(--text-color, #fff);
        font-variant-numeric: tabular-nums;
    }

    .bar {
        height: 8px;
        background: var(--bar-bg, #333);
        border-radius: 4px;
        overflow: hidden;
    }

    .fill {
        height: 100%;
        border-radius: 4px;
        transition: width 0.3s ease;
    }

    .fill.success {
        background: var(--success-color, #22c55e);
    }

    .fill.warning {
        background: var(--warning-color, #f59e0b);
    }

    .fill.danger {
        background: var(--danger-color, #ef4444);
    }
</style>
