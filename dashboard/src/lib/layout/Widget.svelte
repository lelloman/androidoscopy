<script lang="ts">
    import type { Widget } from '../types/protocol';
    import { evaluateCondition } from '../jsonpath';
    import NumberWidget from '../widgets/Number.svelte';
    import TextWidget from '../widgets/Text.svelte';
    import GaugeWidget from '../widgets/Gauge.svelte';
    import BadgeWidget from '../widgets/Badge.svelte';
    import ButtonWidget from '../widgets/Button.svelte';
    import TableWidget from '../widgets/Table.svelte';
    import ChartWidget from '../widgets/Chart.svelte';

    interface Props {
        widget: Widget;
        data: unknown;
        sessionId: string;
    }

    let { widget, data, sessionId }: Props = $props();

    let visible = $derived(
        !widget.visible_when || evaluateCondition(data, widget.visible_when)
    );
</script>

{#if visible}
    {#if widget.type === 'number'}
        <NumberWidget {widget} {data} />
    {:else if widget.type === 'text'}
        <TextWidget {widget} {data} />
    {:else if widget.type === 'gauge'}
        <GaugeWidget {widget} {data} />
    {:else if widget.type === 'badge'}
        <BadgeWidget {widget} {data} />
    {:else if widget.type === 'button'}
        <ButtonWidget {widget} {sessionId} />
    {:else if widget.type === 'table'}
        <TableWidget {widget} {data} {sessionId} />
    {:else if widget.type === 'chart'}
        <ChartWidget {widget} {data} />
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
