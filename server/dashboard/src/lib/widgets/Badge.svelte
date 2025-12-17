<script lang="ts">
    import type { BadgeWidget } from '../types/protocol';
    import { evaluateStringPath } from '../jsonpath';

    interface Props {
        widget: BadgeWidget;
        data: unknown;
    }

    let { widget, data }: Props = $props();

    let value = $derived(evaluateStringPath(data, widget.data_path, '-'));
    let style = $derived(widget.variants[value] || 'muted');
</script>

<div class="badge-widget">
    <span class="label">{widget.label}</span>
    <span class="badge {style}">{value}</span>
</div>

<style>
    .badge-widget {
        display: flex;
        flex-direction: column;
        padding: 1rem;
        background: var(--surface-color, #1e1e1e);
        border-radius: 8px;
        min-width: 100px;
    }

    .label {
        font-size: 0.75rem;
        color: var(--text-muted, #888);
        text-transform: uppercase;
        letter-spacing: 0.05em;
        margin-bottom: 0.5rem;
    }

    .badge {
        display: inline-block;
        padding: 0.25rem 0.75rem;
        border-radius: 9999px;
        font-size: 0.875rem;
        font-weight: 500;
        text-align: center;
    }

    .badge.success {
        background: rgba(34, 197, 94, 0.2);
        color: #22c55e;
    }

    .badge.warning {
        background: rgba(245, 158, 11, 0.2);
        color: #f59e0b;
    }

    .badge.danger {
        background: rgba(239, 68, 68, 0.2);
        color: #ef4444;
    }

    .badge.info {
        background: rgba(59, 130, 246, 0.2);
        color: #3b82f6;
    }

    .badge.muted {
        background: rgba(107, 114, 128, 0.2);
        color: #6b7280;
    }
</style>
