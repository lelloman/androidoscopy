<script lang="ts">
    import type { DialogField } from '../types/protocol';

    interface Props {
        field: DialogField;
        value: unknown;
        onchange: (value: unknown) => void;
    }

    let { field, value, onchange }: Props = $props();

    function handleInput(e: Event) {
        const target = e.target as HTMLInputElement | HTMLSelectElement;
        if (field.type === 'number') {
            onchange(target.value === '' ? null : Number(target.value));
        } else if (field.type === 'checkbox') {
            onchange((target as HTMLInputElement).checked);
        } else {
            onchange(target.value);
        }
    }
</script>

<div class="field">
    <label for={field.key}>{field.label}</label>

    {#if field.type === 'text'}
        <input
            type="text"
            id={field.key}
            value={value ?? ''}
            oninput={handleInput}
        />
    {:else if field.type === 'number'}
        <input
            type="number"
            id={field.key}
            value={value ?? ''}
            min={field.min}
            max={field.max}
            oninput={handleInput}
        />
    {:else if field.type === 'select'}
        <select id={field.key} value={value ?? ''} onchange={handleInput}>
            {#each field.options as option}
                <option value={option.value}>{option.label}</option>
            {/each}
        </select>
    {:else if field.type === 'checkbox'}
        <label class="checkbox-wrapper">
            <input
                type="checkbox"
                id={field.key}
                checked={Boolean(value)}
                onchange={handleInput}
            />
            <span class="checkmark"></span>
        </label>
    {/if}
</div>

<style>
    .field {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
        margin-bottom: 1rem;
    }

    label {
        font-size: 0.875rem;
        font-weight: 500;
        color: var(--text-color, #fff);
    }

    input[type="text"],
    input[type="number"],
    select {
        padding: 0.625rem 0.75rem;
        border: 1px solid var(--border-color, #333);
        border-radius: 6px;
        background: var(--input-bg, #252525);
        color: var(--text-color, #fff);
        font-size: 0.875rem;
    }

    input[type="text"]:focus,
    input[type="number"]:focus,
    select:focus {
        outline: none;
        border-color: var(--primary-color, #3b82f6);
        box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2);
    }

    select {
        cursor: pointer;
    }

    .checkbox-wrapper {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        cursor: pointer;
    }

    input[type="checkbox"] {
        width: 18px;
        height: 18px;
        cursor: pointer;
    }
</style>
