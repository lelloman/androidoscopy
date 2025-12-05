<script lang="ts">
    import type { TextWidget } from '../types/protocol';
    import { evaluateStringPath } from '../jsonpath';

    interface Props {
        widget: TextWidget;
        data: unknown;
    }

    let { widget, data }: Props = $props();

    let value = $derived(evaluateStringPath(data, widget.data_path, '-'));
</script>

<div class="text-widget">
    <span class="label">{widget.label}</span>
    <span class="value">{value}</span>
</div>

<style>
    .text-widget {
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
        font-size: 1rem;
        color: var(--text-color, #fff);
        word-break: break-word;
    }
</style>
