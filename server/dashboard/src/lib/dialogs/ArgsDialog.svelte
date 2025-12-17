<script lang="ts">
    import type { ArgsDialog as ArgsDialogType, DialogField as DialogFieldType } from '../types/protocol';
    import DialogField from './DialogField.svelte';

    interface Props {
        dialog: ArgsDialogType;
        open: boolean;
        onsubmit: (values: Record<string, unknown>) => void;
        oncancel: () => void;
    }

    let { dialog, open, onsubmit, oncancel }: Props = $props();

    let values: Record<string, unknown> = $state({});

    function getDefaultForType(field: DialogFieldType): unknown {
        switch (field.type) {
            case 'text':
                return field.default ?? '';
            case 'number':
                return field.default ?? null;
            case 'select':
                return field.options[0]?.value ?? '';
            case 'checkbox':
                return field.default ?? false;
            default:
                return '';
        }
    }

    function initValues() {
        const newValues: Record<string, unknown> = {};
        for (const field of dialog.fields) {
            newValues[field.key] = getDefaultForType(field);
        }
        values = newValues;
    }

    function handleSubmit(e: Event) {
        e.preventDefault();
        onsubmit(values);
    }

    function handleOverlayClick(e: MouseEvent) {
        if (e.target === e.currentTarget) {
            oncancel();
        }
    }

    function handleKeydown(e: KeyboardEvent) {
        if (e.key === 'Escape') {
            oncancel();
        }
    }

    function updateValue(key: string, value: unknown) {
        values[key] = value;
    }

    $effect(() => {
        if (open) {
            initValues();
        }
    });
</script>

<svelte:window onkeydown={handleKeydown} />

{#if open}
    <div class="dialog-overlay" onclick={handleOverlayClick} role="presentation">
        <div class="dialog" role="dialog" aria-labelledby="dialog-title">
            <h2 id="dialog-title">{dialog.title}</h2>

            <form onsubmit={handleSubmit}>
                {#each dialog.fields as field}
                    <DialogField
                        {field}
                        value={values[field.key]}
                        onchange={(v) => updateValue(field.key, v)}
                    />
                {/each}

                <div class="actions">
                    <button type="button" class="cancel" onclick={oncancel}>
                        Cancel
                    </button>
                    <button type="submit" class="confirm">
                        Confirm
                    </button>
                </div>
            </form>
        </div>
    </div>
{/if}

<style>
    .dialog-overlay {
        position: fixed;
        inset: 0;
        background: rgba(0, 0, 0, 0.5);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 1000;
        padding: 1rem;
    }

    .dialog {
        background: var(--card-bg, #1a1a1a);
        border-radius: 12px;
        padding: 1.5rem;
        max-width: 400px;
        width: 100%;
        box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
    }

    h2 {
        margin: 0 0 1.5rem;
        font-size: 1.125rem;
        font-weight: 600;
        color: var(--text-color, #fff);
    }

    form {
        display: flex;
        flex-direction: column;
    }

    .actions {
        display: flex;
        justify-content: flex-end;
        gap: 0.75rem;
        margin-top: 0.5rem;
    }

    .actions button {
        padding: 0.5rem 1rem;
        border: none;
        border-radius: 6px;
        font-size: 0.875rem;
        font-weight: 500;
        cursor: pointer;
        transition: background 0.2s;
    }

    .cancel {
        background: var(--surface-color, #333);
        color: var(--text-color, #fff);
    }

    .cancel:hover {
        background: #404040;
    }

    .confirm {
        background: var(--primary-color, #3b82f6);
        color: white;
    }

    .confirm:hover {
        background: #2563eb;
    }
</style>
