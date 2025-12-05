<script lang="ts">
    import type { Widget } from '../types/protocol';
    import { evaluateCondition } from '../jsonpath';
    import NumberWidget from '../widgets/Number.svelte';
    import GaugeWidget from '../widgets/Gauge.svelte';
    import BadgeWidget from '../widgets/Badge.svelte';

    interface Props {
        widget: Widget;
        data: unknown;
    }

    let { widget, data }: Props = $props();

    let visible = $derived(
        !widget.visible_when || evaluateCondition(data, widget.visible_when)
    );
</script>

{#if visible}
    {#if widget.type === 'number'}
        <NumberWidget {widget} {data} />
    {:else if widget.type === 'gauge'}
        <GaugeWidget {widget} {data} />
    {:else if widget.type === 'badge'}
        <BadgeWidget {widget} {data} />
    {:else if widget.type === 'text'}
        <NumberWidget widget={{ ...widget, type: 'number', format: 'text' } as any} {data} />
    {:else}
        <div class="unknown-widget">
            Unknown widget type: {widget.type}
        </div>
    {/if}
{/if}

<style>
    .unknown-widget {
        padding: 1rem;
        background: var(--surface-color, #1e1e1e);
        border-radius: 8px;
        color: var(--text-muted, #888);
        font-style: italic;
    }
</style>
