import { describe, it, expect } from 'vitest';
import {
    getDefaultForType,
    initializeFormValues,
    convertInputValue,
} from './dialogLogic';
import type { DialogField } from '../types/protocol';

describe('getDefaultForType', () => {
    describe('text field', () => {
        it('returns empty string when no default', () => {
            const field: DialogField = { type: 'text', key: 'name', label: 'Name' };
            expect(getDefaultForType(field)).toBe('');
        });

        it('returns custom default when provided', () => {
            const field: DialogField = { type: 'text', key: 'name', label: 'Name', default: 'John' };
            expect(getDefaultForType(field)).toBe('John');
        });
    });

    describe('number field', () => {
        it('returns null when no default', () => {
            const field: DialogField = { type: 'number', key: 'age', label: 'Age' };
            expect(getDefaultForType(field)).toBeNull();
        });

        it('returns custom default when provided', () => {
            const field: DialogField = { type: 'number', key: 'age', label: 'Age', default: 25 };
            expect(getDefaultForType(field)).toBe(25);
        });

        it('returns zero default when provided', () => {
            const field: DialogField = { type: 'number', key: 'count', label: 'Count', default: 0 };
            expect(getDefaultForType(field)).toBe(0);
        });
    });

    describe('select field', () => {
        it('returns first option value', () => {
            const field: DialogField = {
                type: 'select',
                key: 'color',
                label: 'Color',
                options: [
                    { value: 'red', label: 'Red' },
                    { value: 'blue', label: 'Blue' },
                ],
            };
            expect(getDefaultForType(field)).toBe('red');
        });

        it('returns empty string when no options', () => {
            const field: DialogField = {
                type: 'select',
                key: 'color',
                label: 'Color',
                options: [],
            };
            expect(getDefaultForType(field)).toBe('');
        });
    });

    describe('checkbox field', () => {
        it('returns false when no default', () => {
            const field: DialogField = { type: 'checkbox', key: 'agree', label: 'Agree' };
            expect(getDefaultForType(field)).toBe(false);
        });

        it('returns true default when provided', () => {
            const field: DialogField = { type: 'checkbox', key: 'agree', label: 'Agree', default: true };
            expect(getDefaultForType(field)).toBe(true);
        });

        it('returns false default when provided explicitly', () => {
            const field: DialogField = { type: 'checkbox', key: 'agree', label: 'Agree', default: false };
            expect(getDefaultForType(field)).toBe(false);
        });
    });
});

describe('initializeFormValues', () => {
    it('returns empty object for no fields', () => {
        expect(initializeFormValues([])).toEqual({});
    });

    it('initializes single field', () => {
        const fields: DialogField[] = [
            { type: 'text', key: 'name', label: 'Name' },
        ];
        expect(initializeFormValues(fields)).toEqual({ name: '' });
    });

    it('initializes multiple fields', () => {
        const fields: DialogField[] = [
            { type: 'text', key: 'name', label: 'Name' },
            { type: 'number', key: 'age', label: 'Age' },
            { type: 'checkbox', key: 'agree', label: 'Agree' },
        ];
        expect(initializeFormValues(fields)).toEqual({
            name: '',
            age: null,
            agree: false,
        });
    });

    it('uses field defaults', () => {
        const fields: DialogField[] = [
            { type: 'text', key: 'name', label: 'Name', default: 'John' },
            { type: 'number', key: 'age', label: 'Age', default: 25 },
            { type: 'checkbox', key: 'agree', label: 'Agree', default: true },
        ];
        expect(initializeFormValues(fields)).toEqual({
            name: 'John',
            age: 25,
            agree: true,
        });
    });

    it('uses first option for select field', () => {
        const fields: DialogField[] = [
            {
                type: 'select',
                key: 'color',
                label: 'Color',
                options: [
                    { value: 'red', label: 'Red' },
                    { value: 'blue', label: 'Blue' },
                ],
            },
        ];
        expect(initializeFormValues(fields)).toEqual({ color: 'red' });
    });
});

describe('convertInputValue', () => {
    describe('text type', () => {
        it('returns the value as-is', () => {
            expect(convertInputValue('hello', 'text')).toBe('hello');
        });

        it('returns empty string as-is', () => {
            expect(convertInputValue('', 'text')).toBe('');
        });
    });

    describe('select type', () => {
        it('returns the value as-is', () => {
            expect(convertInputValue('option1', 'select')).toBe('option1');
        });
    });

    describe('number type', () => {
        it('converts string to number', () => {
            expect(convertInputValue('42', 'number')).toBe(42);
        });

        it('converts decimal string to number', () => {
            expect(convertInputValue('3.14', 'number')).toBe(3.14);
        });

        it('converts negative string to number', () => {
            expect(convertInputValue('-5', 'number')).toBe(-5);
        });

        it('returns null for empty string', () => {
            expect(convertInputValue('', 'number')).toBeNull();
        });

        it('returns NaN for invalid number string', () => {
            expect(convertInputValue('abc', 'number')).toBeNaN();
        });
    });

    describe('checkbox type', () => {
        it('returns true when checked is true', () => {
            expect(convertInputValue('', 'checkbox', true)).toBe(true);
        });

        it('returns false when checked is false', () => {
            expect(convertInputValue('', 'checkbox', false)).toBe(false);
        });

        it('returns false when checked is undefined', () => {
            expect(convertInputValue('', 'checkbox')).toBe(false);
        });
    });
});
