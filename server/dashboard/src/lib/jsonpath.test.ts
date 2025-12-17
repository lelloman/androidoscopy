import { describe, it, expect } from 'vitest';
import {
    evaluatePath,
    evaluateNumberPath,
    evaluateStringPath,
    evaluateArrayPath,
    evaluateCondition
} from './jsonpath';

describe('evaluatePath', () => {
    const testData = {
        memory: {
            heap_used_bytes: 1000000,
            heap_max_bytes: 5000000,
            pressure_level: 'LOW'
        },
        items: [
            { id: 1, name: 'Item 1' },
            { id: 2, name: 'Item 2' },
            { id: 3, name: 'Item 3' }
        ],
        count: 42,
        active: true,
        nested: {
            deep: {
                value: 'deep value'
            }
        }
    };

    it('should return literal values for non-JSONPath strings', () => {
        expect(evaluatePath(testData, 'literal string')).toBe('literal string');
        expect(evaluatePath(testData, 'hello')).toBe('hello');
    });

    it('should return undefined for empty path', () => {
        expect(evaluatePath(testData, '')).toBe(undefined);
    });

    it('should evaluate simple root path', () => {
        expect(evaluatePath(testData, '$.count')).toBe(42);
        expect(evaluatePath(testData, '$.active')).toBe(true);
    });

    it('should evaluate nested paths', () => {
        expect(evaluatePath(testData, '$.memory.heap_used_bytes')).toBe(1000000);
        expect(evaluatePath(testData, '$.memory.pressure_level')).toBe('LOW');
        expect(evaluatePath(testData, '$.nested.deep.value')).toBe('deep value');
    });

    it('should evaluate array access by index', () => {
        expect(evaluatePath(testData, '$.items[0].name')).toBe('Item 1');
        expect(evaluatePath(testData, '$.items[1].id')).toBe(2);
        expect(evaluatePath(testData, '$.items[2].name')).toBe('Item 3');
    });

    it('should return undefined for non-existent paths', () => {
        expect(evaluatePath(testData, '$.nonexistent')).toBe(undefined);
        expect(evaluatePath(testData, '$.memory.nonexistent')).toBe(undefined);
    });

    it('should handle array as result', () => {
        const items = evaluatePath(testData, '$.items');
        expect(Array.isArray(items)).toBe(true);
        expect((items as unknown[]).length).toBe(3);
    });

    it('should handle object as result', () => {
        const memory = evaluatePath(testData, '$.memory');
        expect(typeof memory).toBe('object');
        expect((memory as Record<string, unknown>).heap_used_bytes).toBe(1000000);
    });
});

describe('evaluateNumberPath', () => {
    const testData = {
        count: 42,
        price: 99.99,
        stringNum: '123',
        text: 'hello',
        nullValue: null
    };

    it('should return number values', () => {
        expect(evaluateNumberPath(testData, '$.count')).toBe(42);
        expect(evaluateNumberPath(testData, '$.price')).toBe(99.99);
    });

    it('should convert string numbers', () => {
        expect(evaluateNumberPath(testData, '$.stringNum')).toBe(123);
    });

    it('should return default for non-number values', () => {
        expect(evaluateNumberPath(testData, '$.text')).toBe(0);
        expect(evaluateNumberPath(testData, '$.text', 100)).toBe(100);
    });

    it('should return default for null/undefined', () => {
        expect(evaluateNumberPath(testData, '$.nullValue')).toBe(0);
        expect(evaluateNumberPath(testData, '$.nonexistent')).toBe(0);
        expect(evaluateNumberPath(testData, '$.nonexistent', -1)).toBe(-1);
    });
});

describe('evaluateStringPath', () => {
    const testData = {
        name: 'Test Name',
        count: 42,
        active: true,
        nullValue: null
    };

    it('should return string values', () => {
        expect(evaluateStringPath(testData, '$.name')).toBe('Test Name');
    });

    it('should convert non-string values to strings', () => {
        expect(evaluateStringPath(testData, '$.count')).toBe('42');
        expect(evaluateStringPath(testData, '$.active')).toBe('true');
    });

    it('should return default for null/undefined', () => {
        expect(evaluateStringPath(testData, '$.nullValue')).toBe('');
        expect(evaluateStringPath(testData, '$.nonexistent')).toBe('');
        expect(evaluateStringPath(testData, '$.nonexistent', 'default')).toBe('default');
    });
});

describe('evaluateArrayPath', () => {
    const testData = {
        items: [1, 2, 3],
        single: 'one',
        nullValue: null
    };

    it('should return array values', () => {
        const result = evaluateArrayPath(testData, '$.items');
        expect(result).toEqual([1, 2, 3]);
    });

    it('should wrap single values in array', () => {
        const result = evaluateArrayPath(testData, '$.single');
        expect(result).toEqual(['one']);
    });

    it('should return empty array for null/undefined', () => {
        expect(evaluateArrayPath(testData, '$.nullValue')).toEqual([]);
        expect(evaluateArrayPath(testData, '$.nonexistent')).toEqual([]);
    });
});

describe('evaluateCondition', () => {
    const testData = {
        count: 10,
        name: 'test',
        active: true,
        nullValue: null
    };

    describe('eq operator', () => {
        it('should return true for equal values', () => {
            expect(evaluateCondition(testData, { path: '$.count', operator: 'eq', value: 10 })).toBe(true);
            expect(evaluateCondition(testData, { path: '$.name', operator: 'eq', value: 'test' })).toBe(true);
        });

        it('should return false for unequal values', () => {
            expect(evaluateCondition(testData, { path: '$.count', operator: 'eq', value: 20 })).toBe(false);
            expect(evaluateCondition(testData, { path: '$.name', operator: 'eq', value: 'other' })).toBe(false);
        });
    });

    describe('neq operator', () => {
        it('should return true for unequal values', () => {
            expect(evaluateCondition(testData, { path: '$.count', operator: 'neq', value: 20 })).toBe(true);
        });

        it('should return false for equal values', () => {
            expect(evaluateCondition(testData, { path: '$.count', operator: 'neq', value: 10 })).toBe(false);
        });
    });

    describe('gt operator', () => {
        it('should return true when value is greater', () => {
            expect(evaluateCondition(testData, { path: '$.count', operator: 'gt', value: 5 })).toBe(true);
        });

        it('should return false when value is less or equal', () => {
            expect(evaluateCondition(testData, { path: '$.count', operator: 'gt', value: 10 })).toBe(false);
            expect(evaluateCondition(testData, { path: '$.count', operator: 'gt', value: 15 })).toBe(false);
        });

        it('should return false for non-numeric comparisons', () => {
            expect(evaluateCondition(testData, { path: '$.name', operator: 'gt', value: 5 })).toBe(false);
        });
    });

    describe('gte operator', () => {
        it('should return true when value is greater or equal', () => {
            expect(evaluateCondition(testData, { path: '$.count', operator: 'gte', value: 5 })).toBe(true);
            expect(evaluateCondition(testData, { path: '$.count', operator: 'gte', value: 10 })).toBe(true);
        });

        it('should return false when value is less', () => {
            expect(evaluateCondition(testData, { path: '$.count', operator: 'gte', value: 15 })).toBe(false);
        });
    });

    describe('lt operator', () => {
        it('should return true when value is less', () => {
            expect(evaluateCondition(testData, { path: '$.count', operator: 'lt', value: 15 })).toBe(true);
        });

        it('should return false when value is greater or equal', () => {
            expect(evaluateCondition(testData, { path: '$.count', operator: 'lt', value: 10 })).toBe(false);
            expect(evaluateCondition(testData, { path: '$.count', operator: 'lt', value: 5 })).toBe(false);
        });
    });

    describe('lte operator', () => {
        it('should return true when value is less or equal', () => {
            expect(evaluateCondition(testData, { path: '$.count', operator: 'lte', value: 15 })).toBe(true);
            expect(evaluateCondition(testData, { path: '$.count', operator: 'lte', value: 10 })).toBe(true);
        });

        it('should return false when value is greater', () => {
            expect(evaluateCondition(testData, { path: '$.count', operator: 'lte', value: 5 })).toBe(false);
        });
    });

    describe('exists operator', () => {
        it('should return true for existing values', () => {
            expect(evaluateCondition(testData, { path: '$.count', operator: 'exists' })).toBe(true);
            expect(evaluateCondition(testData, { path: '$.name', operator: 'exists' })).toBe(true);
            expect(evaluateCondition(testData, { path: '$.active', operator: 'exists' })).toBe(true);
        });

        it('should return false for null/undefined values', () => {
            expect(evaluateCondition(testData, { path: '$.nullValue', operator: 'exists' })).toBe(false);
            expect(evaluateCondition(testData, { path: '$.nonexistent', operator: 'exists' })).toBe(false);
        });
    });

    describe('unknown operator', () => {
        it('should return true for unknown operators', () => {
            expect(evaluateCondition(testData, { path: '$.count', operator: 'unknown', value: 10 })).toBe(true);
        });
    });
});
