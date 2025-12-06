<script lang="ts">
    import type { LogEntry, LogLevel } from '../types/protocol';
    import { formatTime } from '../format';
    import { tick } from 'svelte';
    import { filterLogs, isAtBottom, LOG_LEVELS, downloadLogs, type ExportFormat } from './logViewerLogic';

    interface Props {
        logs: LogEntry[];
        defaultLevel?: LogLevel;
    }

    let { logs, defaultLevel = 'DEBUG' }: Props = $props();

    let levelFilter = $state<LogLevel>(defaultLevel);
    let tagFilter = $state('');
    let searchFilter = $state('');
    let autoScroll = $state(true);
    let container: HTMLElement | null = $state(null);
    let expandedEntries = $state(new Set<number>());
    let showExportMenu = $state(false);

    const exportFormats: { value: ExportFormat; label: string }[] = [
        { value: 'text', label: 'Plain Text (.txt)' },
        { value: 'json', label: 'JSON (.json)' },
        { value: 'csv', label: 'CSV (.csv)' },
        { value: 'logcat', label: 'Logcat (.log)' },
    ];

    function handleExport(format: ExportFormat) {
        downloadLogs(filteredLogs, format);
        showExportMenu = false;
    }

    function toggleExportMenu() {
        showExportMenu = !showExportMenu;
    }

    function closeExportMenu() {
        showExportMenu = false;
    }

    let filteredLogs = $derived(
        filterLogs(logs, { levelFilter, tagFilter, searchFilter })
    );

    function handleScroll() {
        if (container) {
            autoScroll = isAtBottom(container.scrollHeight, container.scrollTop, container.clientHeight);
        }
    }

    function scrollToBottom() {
        autoScroll = true;
        if (container) {
            container.scrollTop = container.scrollHeight;
        }
    }

    function toggleExpanded(index: number) {
        if (expandedEntries.has(index)) {
            expandedEntries.delete(index);
        } else {
            expandedEntries.add(index);
        }
        expandedEntries = new Set(expandedEntries);
    }

    $effect(() => {
        if (autoScroll && container && filteredLogs.length > 0) {
            tick().then(() => {
                if (container) {
                    container.scrollTop = container.scrollHeight;
                }
            });
        }
    });
</script>

<div class="log-viewer">
    <div class="filters" role="search" aria-label="Log filters">
        <label class="visually-hidden" for="level-filter">Log level</label>
        <select id="level-filter" bind:value={levelFilter} aria-label="Filter by log level">
            {#each LOG_LEVELS as level}
                <option value={level}>{level}</option>
            {/each}
        </select>
        <label class="visually-hidden" for="tag-filter">Tag filter</label>
        <input
            id="tag-filter"
            type="text"
            placeholder="Filter by tag..."
            bind:value={tagFilter}
            aria-label="Filter by tag"
        />
        <label class="visually-hidden" for="search-filter">Search logs</label>
        <input
            id="search-filter"
            type="text"
            placeholder="Search..."
            bind:value={searchFilter}
            aria-label="Search log messages"
        />
        <div class="export-dropdown">
            <button
                class="export-button"
                onclick={toggleExportMenu}
                aria-label="Export logs"
                aria-expanded={showExportMenu}
            >
                Export ▾
            </button>
            {#if showExportMenu}
                <div class="export-menu" role="menu">
                    {#each exportFormats as format}
                        <button
                            class="export-option"
                            role="menuitem"
                            onclick={() => handleExport(format.value)}
                        >
                            {format.label}
                        </button>
                    {/each}
                </div>
            {/if}
        </div>
    </div>

    <div
        class="logs"
        bind:this={container}
        onscroll={handleScroll}
    >
        {#each filteredLogs as log, index}
            <div class="log-entry {log.level.toLowerCase()}">
                <span class="timestamp">[{formatTime(log.timestamp)}]</span>
                <span class="level">{log.level}</span>
                <span class="tag">{log.tag || '-'}</span>
                <span class="message">{log.message}</span>
                {#if log.throwable}
                    <button
                        class="toggle"
                        onclick={() => toggleExpanded(index)}
                    >
                        {expandedEntries.has(index) ? '▼' : '▶'}
                    </button>
                    {#if expandedEntries.has(index)}
                        <pre class="throwable">{log.throwable}</pre>
                    {/if}
                {/if}
            </div>
        {/each}
    </div>

    {#if !autoScroll}
        <button class="jump-to-bottom" onclick={scrollToBottom}>
            ↓ Jump to bottom
        </button>
    {/if}
</div>

<style>
    .log-viewer {
        display: flex;
        flex-direction: column;
        height: 400px;
        background: var(--surface-color, #1e1e1e);
        border-radius: 8px;
        overflow: hidden;
        position: relative;
    }

    .filters {
        display: flex;
        gap: 0.5rem;
        padding: 0.75rem;
        border-bottom: 1px solid var(--border-color, #333);
    }

    .filters select,
    .filters input {
        padding: 0.5rem;
        border: 1px solid var(--border-color, #333);
        border-radius: 4px;
        background: var(--input-bg, #252525);
        color: var(--text-color, #fff);
        font-size: 0.875rem;
    }

    .filters input {
        flex: 1;
    }

    .export-dropdown {
        position: relative;
    }

    .export-button {
        padding: 0.5rem 0.75rem;
        border: 1px solid var(--border-color, #333);
        border-radius: 4px;
        background: var(--input-bg, #252525);
        color: var(--text-color, #fff);
        font-size: 0.875rem;
        cursor: pointer;
        white-space: nowrap;
    }

    .export-button:hover {
        background: var(--surface-hover, #333);
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
        min-width: 150px;
    }

    .export-option {
        display: block;
        width: 100%;
        padding: 0.5rem 0.75rem;
        border: none;
        background: transparent;
        color: var(--text-color, #fff);
        font-size: 0.875rem;
        text-align: left;
        cursor: pointer;
    }

    .export-option:hover {
        background: var(--surface-hover, #333);
    }

    .export-option:first-child {
        border-radius: 4px 4px 0 0;
    }

    .export-option:last-child {
        border-radius: 0 0 4px 4px;
    }

    .logs {
        flex: 1;
        overflow-y: auto;
        font-family: 'JetBrains Mono', 'Fira Code', monospace;
        font-size: 0.8rem;
        line-height: 1.5;
    }

    .log-entry {
        padding: 0.25rem 0.75rem;
        border-bottom: 1px solid var(--border-color, #222);
        display: flex;
        flex-wrap: wrap;
        gap: 0.5rem;
        align-items: flex-start;
    }

    .log-entry:hover {
        background: rgba(255, 255, 255, 0.02);
    }

    .timestamp {
        color: var(--text-muted, #666);
        white-space: nowrap;
    }

    .level {
        font-weight: 600;
        width: 50px;
    }

    .tag {
        color: var(--info-color, #3b82f6);
        min-width: 60px;
        max-width: 100px;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    .message {
        flex: 1;
        word-break: break-word;
    }

    .log-entry.verbose .level { color: #6b7280; }
    .log-entry.debug .level { color: #8b5cf6; }
    .log-entry.info .level { color: #22c55e; }
    .log-entry.warn .level { color: #f59e0b; }
    .log-entry.error .level { color: #ef4444; }

    .toggle {
        background: none;
        border: none;
        color: var(--text-muted, #666);
        cursor: pointer;
        padding: 0 0.25rem;
    }

    .throwable {
        width: 100%;
        margin-top: 0.5rem;
        padding: 0.5rem;
        background: rgba(239, 68, 68, 0.1);
        border-radius: 4px;
        white-space: pre-wrap;
        font-size: 0.75rem;
        color: #ef4444;
    }

    .jump-to-bottom {
        position: absolute;
        bottom: 1rem;
        left: 50%;
        transform: translateX(-50%);
        padding: 0.5rem 1rem;
        background: var(--primary-color, #3b82f6);
        color: white;
        border: none;
        border-radius: 9999px;
        cursor: pointer;
        font-size: 0.875rem;
    }

    .jump-to-bottom:hover {
        opacity: 0.9;
    }

    .visually-hidden {
        position: absolute;
        width: 1px;
        height: 1px;
        padding: 0;
        margin: -1px;
        overflow: hidden;
        clip: rect(0, 0, 0, 0);
        white-space: nowrap;
        border: 0;
    }
</style>
