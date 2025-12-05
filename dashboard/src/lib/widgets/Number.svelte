<script lang="ts">
    import type { NumberWidget } from '../types/protocol';
    import { evaluatePath } from '../jsonpath';
    import { formatValue } from '../format';

    interface Props {
        widget: NumberWidget;
        data: unknown;
    }

    let { widget, data }: Props = $props();

    let value = $derived(evaluatePath(data, widget.data_path));
    let formatted = $derived(formatValue(value, widget.format));
</script>

<div class="number-widget">
    <span class="label">{widget.label}</span>
    <span class="value">{formatted}</span>
</div>

<style>
    .number-widget {
        display: flex;
        flex-direction: column;
        padding: 1rem;
        background: var(--surface-color, #1e1e1e);
        border-radius: 8px;
        min-width: 120px;
    }

    .label {
        font-size: 0.75rem;
        color: var(--text-muted, #888);
        text-transform: uppercase;
        letter-spacing: 0.05em;
        margin-bottom: 0.25rem;
    }

    .value {
        font-size: 1.5rem;
        font-weight: 600;
        color: var(--text-color, #fff);
        font-variant-numeric: tabular-nums;
    }
</style>
