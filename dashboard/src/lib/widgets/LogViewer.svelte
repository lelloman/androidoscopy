<script lang="ts">
    import type { LogEntry } from '../types/protocol';
    import { formatTime } from '../format';
    import { tick } from 'svelte';

    interface Props {
        logs: LogEntry[];
        defaultLevel?: string;
    }

    let { logs, defaultLevel = 'DEBUG' }: Props = $props();

    const LOG_LEVELS = ['VERBOSE', 'DEBUG', 'INFO', 'WARN', 'ERROR'];

    let levelFilter = $state(defaultLevel);
    let tagFilter = $state('');
    let searchFilter = $state('');
    let autoScroll = $state(true);
    let container: HTMLElement | null = $state(null);
    let expandedEntries = $state(new Set<number>());

    let filteredLogs = $derived(
        logs.filter(log => {
            const levelIdx = LOG_LEVELS.indexOf(log.level);
            const filterIdx = LOG_LEVELS.indexOf(levelFilter);
            if (levelIdx < filterIdx) return false;

            if (tagFilter && !log.tag?.toLowerCase().includes(tagFilter.toLowerCase())) {
                return false;
            }

            if (searchFilter && !log.message.toLowerCase().includes(searchFilter.toLowerCase())) {
                return false;
            }

            return true;
        })
    );

    function handleScroll() {
        if (container) {
            const atBottom = container.scrollHeight - container.scrollTop <= container.clientHeight + 50;
            autoScroll = atBottom;
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
    <div class="filters">
        <select bind:value={levelFilter}>
            {#each LOG_LEVELS as level}
                <option value={level}>{level}</option>
            {/each}
        </select>
        <input
            type="text"
            placeholder="Filter by tag..."
            bind:value={tagFilter}
        />
        <input
            type="text"
            placeholder="Search..."
            bind:value={searchFilter}
        />
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
</style>
