<script lang="ts">
    import type { Section, LogEntry, NetworkRequestViewerWidget, SharedPreferencesViewerWidget, SqliteViewerWidget, PermissionsViewerWidget } from '../types/protocol';
    import Widget from './Widget.svelte';
    import LogViewer from '../widgets/LogViewer.svelte';
    import NetworkRequestViewer from '../widgets/NetworkRequestViewer.svelte';
    import SharedPreferencesViewer from '../widgets/SharedPreferencesViewer.svelte';
    import SqliteViewer from '../widgets/SqliteViewer.svelte';
    import PermissionsViewer from '../widgets/PermissionsViewer.svelte';

    interface Props {
        section: Section;
        data: unknown;
        logs?: LogEntry[];
        sessionId: string;
    }

    let { section, data, logs = [], sessionId }: Props = $props();

    let collapsed = $state(section.collapsed_default ?? false);

    // Use full_width from schema, or auto-detect for certain widget types
    let isFullWidth = $derived(
        section.full_width ||
        section.layout === 'stack' ||
        section.layout === 'flow' ||
        section.widget?.type === 'log_viewer' ||
        section.widget?.type === 'network_request_viewer' ||
        section.widget?.type === 'shared_preferences_viewer' ||
        section.widget?.type === 'sqlite_viewer' ||
        section.widgets?.some(w => w.type === 'log_viewer' || w.type === 'table' || w.type === 'network_request_viewer' || w.type === 'shared_preferences_viewer' || w.type === 'sqlite_viewer')
    );

    function toggleCollapse() {
        if (section.collapsible) {
            collapsed = !collapsed;
        }
    }
</script>

<div class="section" class:section-full-width={isFullWidth}>
    <button
        class="header"
        onclick={toggleCollapse}
        disabled={!section.collapsible}
    >
        <h3>{section.title}</h3>
        {#if section.collapsible}
            <span class="toggle">{collapsed ? '▶' : '▼'}</span>
        {/if}
    </button>

    {#if !collapsed}
        <div
            class="content layout-{section.layout || 'row'}"
            style:--columns={section.columns}
        >
            {#if section.widget}
                {#if section.widget.type === 'log_viewer'}
                    <LogViewer {logs} defaultLevel={section.widget.default_level} />
                {:else if section.widget.type === 'network_request_viewer'}
                    <NetworkRequestViewer widget={section.widget as NetworkRequestViewerWidget} {data} {sessionId} />
                {:else if section.widget.type === 'shared_preferences_viewer'}
                    <SharedPreferencesViewer widget={section.widget as SharedPreferencesViewerWidget} {data} {sessionId} />
                {:else if section.widget.type === 'sqlite_viewer'}
                    <SqliteViewer widget={section.widget as SqliteViewerWidget} {data} {sessionId} />
                {:else if section.widget.type === 'permissions_viewer'}
                    <PermissionsViewer widget={section.widget as PermissionsViewerWidget} {data} {sessionId} />
                {:else}
                    <Widget widget={section.widget} {data} {sessionId} />
                {/if}
            {:else if section.widgets}
                {#each section.widgets as widget}
                    {#if widget.type === 'log_viewer'}
                        <LogViewer {logs} defaultLevel={widget.default_level} />
                    {:else if widget.type === 'network_request_viewer'}
                        <NetworkRequestViewer widget={widget as NetworkRequestViewerWidget} {data} {sessionId} />
                    {:else if widget.type === 'shared_preferences_viewer'}
                        <SharedPreferencesViewer widget={widget as SharedPreferencesViewerWidget} {data} {sessionId} />
                    {:else if widget.type === 'sqlite_viewer'}
                        <SqliteViewer widget={widget as SqliteViewerWidget} {data} {sessionId} />
                    {:else if widget.type === 'permissions_viewer'}
                        <PermissionsViewer widget={widget as PermissionsViewerWidget} {data} {sessionId} />
                    {:else}
                        <Widget {widget} {data} {sessionId} />
                    {/if}
                {/each}
            {/if}
        </div>
    {/if}
</div>

<style>
    .section {
        background: var(--card-bg, #171717);
        border-radius: 12px;
        overflow: hidden;
    }

    .header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        width: 100%;
        padding: 1rem;
        background: none;
        border: none;
        text-align: left;
        cursor: default;
        color: var(--text-color, #fff);
    }

    .header:not(:disabled) {
        cursor: pointer;
    }

    .header:not(:disabled):hover {
        background: rgba(255, 255, 255, 0.02);
    }

    .header h3 {
        margin: 0;
        font-size: 0.875rem;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.05em;
    }

    .toggle {
        color: var(--text-muted, #666);
    }

    .content {
        padding: 0 1rem 1rem;
    }

    .content.layout-row {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
        gap: 1rem;
    }

    .content.layout-grid {
        display: grid;
        grid-template-columns: repeat(var(--columns, 2), 1fr);
        gap: 1rem;
    }

    .content.layout-stack {
        display: flex;
        flex-direction: column;
        gap: 1rem;
    }

    .content.layout-flow {
        display: flex;
        flex-wrap: wrap;
        gap: 0.5rem;
    }

    @media (min-width: 1200px) {
        .content.layout-row {
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
        }
    }
</style>
