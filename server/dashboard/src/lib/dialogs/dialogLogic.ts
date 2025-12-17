import type { DialogField } from '../types/protocol';

/**
 * Gets the default value for a dialog field based on its type.
 */
export function getDefaultForType(field: DialogField): unknown {
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

/**
 * Initializes form values from dialog fields.
 */
export function initializeFormValues(fields: DialogField[]): Record<string, unknown> {
    const values: Record<string, unknown> = {};
    for (const field of fields) {
        values[field.key] = getDefaultForType(field);
    }
    return values;
}

/**
 * Converts input value to appropriate type based on field type.
 */
export function convertInputValue(
    value: string,
    fieldType: DialogField['type'],
    checked?: boolean
): unknown {
    switch (fieldType) {
        case 'number':
            return value === '' ? null : Number(value);
        case 'checkbox':
            return checked ?? false;
        default:
            return value;
    }
}
