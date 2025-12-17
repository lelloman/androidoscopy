<script lang="ts">
    import type { ChartWidget } from '../types/protocol';
    import { evaluateNumberPath } from '../jsonpath';
    import { formatValue } from '../format';
    import {
        calculateChartPathWithView,
        getStats,
        getPointAtX,
        zoom,
        pan,
        downloadCSV,
        downloadPNG,
        formatTimestamp,
        TIME_WINDOWS,
        filterByTimeWindow,
        type ChartDataPoint,
        type ViewWindow
    } from './chartLogic';
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
    let view = $state<ViewWindow>({ startIndex: 0, endIndex: maxPoints });
    let isZoomed = $state(false);
    let selectedTimeWindow = $state<number>(Infinity);

    // Interactive state
    let isDragging = $state(false);
    let dragStartX = $state(0);
    let hoverPoint = $state<ChartDataPoint | null>(null);
    let hoverX = $state(0);
    let showExportMenu = $state(false);
    let chartContainer: HTMLDivElement | null = $state(null);
    let canvasRef: HTMLCanvasElement | null = $state(null);

    let currentValue = $derived(evaluateNumberPath(data, widget.data_path, 0));
    let formattedValue = $derived(formatValue(currentValue, widget.format));

    $effect(() => {
        const now = Date.now();
        dataPoints = [
            ...dataPoints.slice(-(maxPoints - 1)),
            { value: currentValue, timestamp: now }
        ];
        // If not zoomed, keep the view at the end
        if (!isZoomed) {
            view = { startIndex: Math.max(0, dataPoints.length - maxPoints), endIndex: dataPoints.length };
        }
    });

    let filteredPoints = $derived(filterByTimeWindow(dataPoints, selectedTimeWindow));
    let stats = $derived(getStats(filteredPoints.slice(view.startIndex, view.endIndex + 1)));
    let pathD = $derived(calculateChartPathWithView(filteredPoints, view, 100, 100, 5));

    function handleWheel(e: WheelEvent) {
        e.preventDefault();
        if (!chartContainer) return;

        const rect = chartContainer.getBoundingClientRect();
        const centerRatio = (e.clientX - rect.left) / rect.width;
        const zoomIn = e.deltaY < 0;

        view = zoom(view, filteredPoints.length, zoomIn, centerRatio);
        isZoomed = view.startIndex > 0 || view.endIndex < filteredPoints.length;
    }

    function handleMouseDown(e: MouseEvent) {
        isDragging = true;
        dragStartX = e.clientX;
    }

    function handleMouseMove(e: MouseEvent) {
        if (!chartContainer) return;

        const rect = chartContainer.getBoundingClientRect();
        const x = e.clientX - rect.left;
        hoverX = x;
        hoverPoint = getPointAtX(filteredPoints, x, rect.width, view);

        if (isDragging) {
            const deltaRatio = (e.clientX - dragStartX) / rect.width;
            view = pan(view, filteredPoints.length, deltaRatio);
            dragStartX = e.clientX;
            isZoomed = true;
        }
    }

    function handleMouseUp() {
        isDragging = false;
    }

    function handleMouseLeave() {
        isDragging = false;
        hoverPoint = null;
    }

    function resetView() {
        view = { startIndex: Math.max(0, filteredPoints.length - maxPoints), endIndex: filteredPoints.length };
        isZoomed = false;
    }

    function handleExportCSV() {
        downloadCSV(filteredPoints, widget.label);
        showExportMenu = false;
    }

    function handleExportPNG() {
        if (!canvasRef || !chartContainer) {
            showExportMenu = false;
            return;
        }

        // Render chart to canvas
        const ctx = canvasRef.getContext('2d');
        if (!ctx) return;

        const width = chartContainer.clientWidth * 2;
        const height = chartContainer.clientHeight * 2;
        canvasRef.width = width;
        canvasRef.height = height;

        // Background
        ctx.fillStyle = '#111';
        ctx.fillRect(0, 0, width, height);

        // Draw grid lines
        ctx.strokeStyle = '#333';
        ctx.lineWidth = 1;
        for (let i = 0; i <= 4; i++) {
            const y = (height / 4) * i;
            ctx.beginPath();
            ctx.moveTo(0, y);
            ctx.lineTo(width, y);
            ctx.stroke();
        }

        // Draw chart line
        const viewedPoints = filteredPoints.slice(view.startIndex, view.endIndex + 1);
        if (viewedPoints.length >= 2) {
            const values = viewedPoints.map(p => p.value);
            const minValue = Math.min(...values);
            const maxValue = Math.max(...values);
            const range = maxValue - minValue || 1;
            const padding = 10;

            ctx.strokeStyle = chartColor;
            ctx.lineWidth = 3;
            ctx.beginPath();

            viewedPoints.forEach((p, i) => {
                const x = (i / (viewedPoints.length - 1)) * width;
                const y = height - padding - ((p.value - minValue) / range) * (height - 2 * padding);
                if (i === 0) {
                    ctx.moveTo(x, y);
                } else {
                    ctx.lineTo(x, y);
                }
            });
            ctx.stroke();
        }

        downloadPNG(canvasRef, widget.label);
        showExportMenu = false;
    }

    function setTimeWindow(seconds: number) {
        selectedTimeWindow = seconds;
        resetView();
    }
</script>

<svelte:window onmouseup={handleMouseUp} />

<div class="chart-widget">
    <div class="header">
        <span class="label">{widget.label}</span>
        <div class="header-right">
            <span class="value">{formattedValue}</span>
            {#if showExpandButton}
                <button
                    class="icon-button"
                    onclick={handleExpand}
                    aria-label="Open in fullscreen"
                    title="Open in fullscreen"
                >
                    ⛶
                </button>
            {/if}
        </div>
    </div>

    <div class="toolbar">
        <div class="time-windows">
            {#each TIME_WINDOWS as tw}
                <button
                    class="time-button"
                    class:active={selectedTimeWindow === tw.seconds}
                    onclick={() => setTimeWindow(tw.seconds)}
                >
                    {tw.label}
                </button>
            {/each}
        </div>
        <div class="toolbar-right">
            {#if isZoomed}
                <button class="icon-button reset" onclick={resetView} title="Reset zoom">
                    ⟲
                </button>
            {/if}
            <div class="export-dropdown">
                <button
                    class="icon-button"
                    onclick={() => showExportMenu = !showExportMenu}
                    title="Export"
                >
                    ↓
                </button>
                {#if showExportMenu}
                    <div class="export-menu">
                        <button class="export-option" onclick={handleExportPNG}>PNG Image</button>
                        <button class="export-option" onclick={handleExportCSV}>CSV Data</button>
                    </div>
                {/if}
            </div>
        </div>
    </div>

    <!-- svelte-ignore a11y_no_static_element_interactions -->
    <div
        class="chart-container"
        bind:this={chartContainer}
        onwheel={handleWheel}
        onmousedown={handleMouseDown}
        onmousemove={handleMouseMove}
        onmouseleave={handleMouseLeave}
        class:dragging={isDragging}
    >
        <svg viewBox="0 0 100 100" preserveAspectRatio="none">
            <!-- Grid lines -->
            <line x1="0" y1="25" x2="100" y2="25" stroke="#333" stroke-width="0.5" vector-effect="non-scaling-stroke" />
            <line x1="0" y1="50" x2="100" y2="50" stroke="#333" stroke-width="0.5" vector-effect="non-scaling-stroke" />
            <line x1="0" y1="75" x2="100" y2="75" stroke="#333" stroke-width="0.5" vector-effect="non-scaling-stroke" />

            <!-- Chart path -->
            <path
                d={pathD}
                fill="none"
                stroke={chartColor}
                stroke-width="2"
                vector-effect="non-scaling-stroke"
            />
        </svg>

        <!-- Tooltip -->
        {#if hoverPoint && chartContainer}
            <div
                class="tooltip"
                style:left="{Math.min(hoverX, chartContainer.clientWidth - 100)}px"
            >
                <div class="tooltip-value">{formatValue(hoverPoint.value, widget.format)}</div>
                <div class="tooltip-time">{formatTimestamp(hoverPoint.timestamp)}</div>
            </div>
            <div class="hover-line" style:left="{hoverX}px"></div>
        {/if}
    </div>

    <div class="stats-row">
        <div class="stat">
            <span class="stat-label">Min</span>
            <span class="stat-value">{formatValue(stats.min, widget.format)}</span>
        </div>
        <div class="stat">
            <span class="stat-label">Avg</span>
            <span class="stat-value">{formatValue(stats.avg, widget.format)}</span>
        </div>
        <div class="stat">
            <span class="stat-label">Max</span>
            <span class="stat-value">{formatValue(stats.max, widget.format)}</span>
        </div>
    </div>
</div>

<canvas bind:this={canvasRef} style="display: none;"></canvas>

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

    .toolbar {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 0.5rem;
        gap: 0.5rem;
    }

    .time-windows {
        display: flex;
        gap: 0.25rem;
    }

    .time-button {
        padding: 0.125rem 0.375rem;
        font-size: 0.625rem;
        border: 1px solid var(--border-color, #333);
        border-radius: 3px;
        background: transparent;
        color: var(--text-muted, #888);
        cursor: pointer;
        transition: all 0.15s;
    }

    .time-button:hover {
        color: var(--text-color, #fff);
        border-color: var(--text-muted, #888);
    }

    .time-button.active {
        background: var(--primary-color, #3b82f6);
        border-color: var(--primary-color, #3b82f6);
        color: white;
    }

    .toolbar-right {
        display: flex;
        gap: 0.25rem;
        align-items: center;
    }

    .icon-button {
        padding: 0.25rem 0.5rem;
        border: 1px solid var(--border-color, #333);
        border-radius: 4px;
        background: var(--surface-color, #1e1e1e);
        color: var(--text-muted, #888);
        font-size: 0.875rem;
        cursor: pointer;
        line-height: 1;
        transition: all 0.15s;
    }

    .icon-button:hover {
        color: var(--text-color, #fff);
        border-color: var(--text-muted, #888);
    }

    .icon-button.reset {
        color: var(--primary-color, #3b82f6);
    }

    .export-dropdown {
        position: relative;
    }

    .export-menu {
        position: absolute;
        top: 100%;
        right: 0;
        margin-top: 4px;
        background: var(--surface-color, #1e1e1e);
        border: 1px solid var(--border-color, #333);
        border-radius: 4px;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
        z-index: 100;
        min-width: 100px;
    }

    .export-option {
        display: block;
        width: 100%;
        padding: 0.5rem 0.75rem;
        border: none;
        background: transparent;
        color: var(--text-color, #fff);
        font-size: 0.75rem;
        text-align: left;
        cursor: pointer;
    }

    .export-option:hover {
        background: var(--surface-hover, #333);
    }

    .chart-container {
        position: relative;
        height: 80px;
        background: var(--chart-bg, #111);
        border-radius: 4px;
        overflow: hidden;
        cursor: crosshair;
    }

    .chart-container.dragging {
        cursor: grabbing;
    }

    .chart-container svg {
        width: 100%;
        height: 100%;
    }

    .tooltip {
        position: absolute;
        top: 4px;
        background: var(--surface-color, #2a2a2a);
        border: 1px solid var(--border-color, #444);
        border-radius: 4px;
        padding: 0.25rem 0.5rem;
        pointer-events: none;
        z-index: 10;
    }

    .tooltip-value {
        font-size: 0.75rem;
        font-weight: 600;
        color: var(--text-color, #fff);
    }

    .tooltip-time {
        font-size: 0.625rem;
        color: var(--text-muted, #888);
    }

    .hover-line {
        position: absolute;
        top: 0;
        bottom: 0;
        width: 1px;
        background: var(--text-muted, #666);
        pointer-events: none;
        opacity: 0.5;
    }

    .stats-row {
        display: flex;
        justify-content: space-between;
        margin-top: 0.5rem;
        padding-top: 0.5rem;
        border-top: 1px solid var(--border-color, #333);
    }

    .stat {
        display: flex;
        flex-direction: column;
        align-items: center;
    }

    .stat-label {
        font-size: 0.5rem;
        color: var(--text-muted, #666);
        text-transform: uppercase;
        letter-spacing: 0.05em;
    }

    .stat-value {
        font-size: 0.625rem;
        color: var(--text-color, #fff);
        font-variant-numeric: tabular-nums;
    }
</style>
