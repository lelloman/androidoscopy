<script lang="ts">
    import type { ChartWidget } from '../types/protocol';
    import { evaluateNumberPath } from '../jsonpath';
    import { formatValue } from '../format';
    import { calculateChartPath, getMinMax, type ChartDataPoint } from './chartLogic';
    import { openFullscreen } from '../stores/fullscreen';

    interface Props {
        widget: ChartWidget;
        data: unknown;
        showExpandButton?: boolean;
    }

    let { widget, data, showExpandButton = true }: Props = $props();

    function handleExpand() {
        openFullscreen('chart', { widget, data });
    }

    let maxPoints = $derived(widget.max_points ?? 60);
    let chartColor = $derived(widget.color ?? '#3b82f6');

    let dataPoints: ChartDataPoint[] = $state([]);

    let currentValue = $derived(evaluateNumberPath(data, widget.data_path, 0));
    let formattedValue = $derived(formatValue(currentValue, widget.format));

    $effect(() => {
        const now = Date.now();
        dataPoints = [
            ...dataPoints.slice(-(maxPoints - 1)),
            { value: currentValue, timestamp: now }
        ];
    });

    let minMax = $derived(getMinMax(dataPoints));
    let pathD = $derived(calculateChartPath(dataPoints, maxPoints));
</script>

<div class="chart-widget">
    <div class="header">
        <span class="label">{widget.label}</span>
        <div class="header-right">
            <span class="value">{formattedValue}</span>
            {#if showExpandButton}
                <button
                    class="expand-button"
                    onclick={handleExpand}
                    aria-label="Open in fullscreen"
                    title="Open in fullscreen"
                >
                    ⛶
                </button>
            {/if}
        </div>
    </div>
    <div class="chart-container">
        <svg viewBox="0 0 100 100" preserveAspectRatio="none">
            <path
                d={pathD}
                fill="none"
                stroke={chartColor}
                stroke-width="2"
                vector-effect="non-scaling-stroke"
            />
        </svg>
    </div>
    <div class="range">
        <span>{formatValue(minMax.min, widget.format)}</span>
        <span>{formatValue(minMax.max, widget.format)}</span>
    </div>
</div>

<style>
    .chart-widget {
        display: flex;
        flex-direction: column;
        padding: 1rem;
        background: var(--surface-color, #1e1e1e);
        border-radius: 8px;
        min-width: 200px;
    }

    .header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 0.5rem;
    }

    .header-right {
        display: flex;
        align-items: center;
        gap: 0.5rem;
    }

    .expand-button {
        padding: 0.25rem 0.5rem;
        border: 1px solid var(--border-color, #333);
        border-radius: 4px;
        background: var(--surface-color, #1e1e1e);
        color: var(--text-muted, #888);
        font-size: 0.875rem;
        cursor: pointer;
        line-height: 1;
        opacity: 0.6;
        transition: opacity 0.2s;
    }

    .expand-button:hover {
        opacity: 1;
        color: var(--text-color, #fff);
    }

    .label {
        font-size: 0.75rem;
        color: var(--text-muted, #888);
        text-transform: uppercase;
        letter-spacing: 0.05em;
    }

    .value {
        font-size: 1.25rem;
        font-weight: 600;
        color: var(--text-color, #fff);
        font-variant-numeric: tabular-nums;
    }

    .chart-container {
        height: 80px;
        background: var(--chart-bg, #111);
        border-radius: 4px;
        overflow: hidden;
    }

    .chart-container svg {
        width: 100%;
        height: 100%;
    }

    .range {
        display: flex;
        justify-content: space-between;
        margin-top: 0.25rem;
        font-size: 0.625rem;
        color: var(--text-muted, #666);
        font-variant-numeric: tabular-nums;
    }
</style>
