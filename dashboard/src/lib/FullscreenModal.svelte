<script lang="ts">
    import { fullscreenWidget, closeFullscreen } from './stores/fullscreen';
    import type { LogEntry, ChartWidget as ChartWidgetType, TableWidget as TableWidgetType, NetworkRequestViewerWidget as NetworkRequestViewerWidgetType, SharedPreferencesViewerWidget as SharedPreferencesViewerWidgetType } from './types/protocol';
    import LogViewer from './widgets/LogViewer.svelte';
    import Chart from './widgets/Chart.svelte';
    import Table from './widgets/Table.svelte';
    import NetworkRequestViewer from './widgets/NetworkRequestViewer.svelte';
    import SharedPreferencesViewer from './widgets/SharedPreferencesViewer.svelte';

    function handleKeydown(event: KeyboardEvent) {
        if (event.key === 'Escape' && $fullscreenWidget.isOpen) {
            closeFullscreen();
        }
    }

    function handleBackdropClick(event: MouseEvent) {
        if (event.target === event.currentTarget) {
            closeFullscreen();
        }
    }
</script>

<svelte:window onkeydown={handleKeydown} />

{#if $fullscreenWidget.isOpen}
    <!-- svelte-ignore a11y_click_events_have_key_events a11y_interactive_supports_focus -->
    <div
        class="fullscreen-backdrop"
        onclick={handleBackdropClick}
        role="dialog"
        aria-modal="true"
        aria-label="Fullscreen widget view"
    >
        <div class="fullscreen-content">
            <button
                class="close-button"
                onclick={closeFullscreen}
                aria-label="Close fullscreen"
            >
                ×
            </button>

            {#if $fullscreenWidget.widgetType === 'logviewer'}
                <div class="widget-container logviewer-fullscreen">
                    <LogViewer
                        logs={$fullscreenWidget.props.logs as LogEntry[]}
                        defaultLevel={$fullscreenWidget.props.defaultLevel as import('./types/protocol').LogLevel}
                        showExpandButton={false}
                    />
                </div>
            {:else if $fullscreenWidget.widgetType === 'chart'}
                <div class="widget-container chart-fullscreen">
                    <Chart
                        widget={$fullscreenWidget.props.widget as ChartWidgetType}
                        data={$fullscreenWidget.props.data}
                        showExpandButton={false}
                    />
                </div>
            {:else if $fullscreenWidget.widgetType === 'table'}
                <div class="widget-container table-fullscreen">
                    <Table
                        widget={$fullscreenWidget.props.widget as TableWidgetType}
                        data={$fullscreenWidget.props.data}
                        sessionId={$fullscreenWidget.props.sessionId as string}
                        showExpandButton={false}
                    />
                </div>
            {:else if $fullscreenWidget.widgetType === 'network_request_viewer'}
                <div class="widget-container network-fullscreen">
                    <NetworkRequestViewer
                        widget={$fullscreenWidget.props.widget as NetworkRequestViewerWidgetType}
                        data={$fullscreenWidget.props.data}
                        sessionId={$fullscreenWidget.props.sessionId as string}
                        showExpandButton={false}
                    />
                </div>
            {:else if $fullscreenWidget.widgetType === 'shared_preferences_viewer'}
                <div class="widget-container prefs-fullscreen">
                    <SharedPreferencesViewer
                        widget={$fullscreenWidget.props.widget as SharedPreferencesViewerWidgetType}
                        data={$fullscreenWidget.props.data}
                        sessionId={$fullscreenWidget.props.sessionId as string}
                        showExpandButton={false}
                    />
                </div>
            {/if}
        </div>
    </div>
{/if}

<style>
    .fullscreen-backdrop {
        position: fixed;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background: rgba(0, 0, 0, 0.85);
        z-index: 2000;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 2rem;
        animation: fadeIn 0.2s ease;
    }

    @keyframes fadeIn {
        from { opacity: 0; }
        to { opacity: 1; }
    }

    .fullscreen-content {
        position: relative;
        width: 100%;
        height: 100%;
        max-width: 100%;
        max-height: 100%;
        display: flex;
        flex-direction: column;
    }

    .close-button {
        position: absolute;
        top: -1rem;
        right: -1rem;
        width: 2.5rem;
        height: 2.5rem;
        border-radius: 50%;
        border: none;
        background: var(--surface-color, #2a2a2a);
        color: var(--text-color, #fff);
        font-size: 1.5rem;
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 10;
        transition: background 0.2s, transform 0.2s;
    }

    .close-button:hover {
        background: var(--danger-color, #ef4444);
        transform: scale(1.1);
    }

    .widget-container {
        flex: 1;
        min-height: 0;
        overflow: hidden;
        border-radius: 8px;
    }

    .logviewer-fullscreen :global(.log-viewer) {
        height: 100%;
    }

    .chart-fullscreen :global(.chart-widget) {
        height: 100%;
    }

    .chart-fullscreen :global(.chart-container) {
        flex: 1;
        height: auto;
    }

    .table-fullscreen :global(.table-widget) {
        height: 100%;
        display: flex;
        flex-direction: column;
    }

    .table-fullscreen :global(.table-container) {
        flex: 1;
        overflow: auto;
    }

    .network-fullscreen :global(.network-viewer) {
        height: 100%;
    }

    .network-fullscreen :global(.request-list) {
        flex: 1;
    }

    .network-fullscreen :global(.request-detail) {
        max-height: 40%;
    }

    .prefs-fullscreen :global(.prefs-viewer) {
        height: 100%;
    }
</style>
