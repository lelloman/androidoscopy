import { JSONPath } from 'jsonpath-plus';

/**
 * Evaluates a JSONPath expression against data.
 * If path doesn't start with $, returns it as a literal value.
 */
export function evaluatePath(data: unknown, path: string): unknown {
    if (!path) return undefined;

    // If not a JSONPath expression, return as literal
    if (!path.startsWith('$')) {
        return path;
    }

    try {
        const result = JSONPath({
            path,
            json: data as object,
            wrap: false
        });
        return result;
    } catch (e) {
        console.error('JSONPath evaluation error:', e);
        return undefined;
    }
}

/**
 * Evaluates a JSONPath expression and returns a number.
 */
export function evaluateNumberPath(data: unknown, path: string, defaultValue = 0): number {
    const result = evaluatePath(data, path);
    if (result === undefined || result === null) return defaultValue;
    const num = Number(result);
    return isNaN(num) ? defaultValue : num;
}

/**
 * Evaluates a JSONPath expression and returns a string.
 */
export function evaluateStringPath(data: unknown, path: string, defaultValue = ''): string {
    const result = evaluatePath(data, path);
    if (result === undefined || result === null) return defaultValue;
    return String(result);
}

/**
 * Evaluates a JSONPath expression and returns an array.
 */
export function evaluateArrayPath(data: unknown, path: string): unknown[] {
    const result = evaluatePath(data, path);
    if (Array.isArray(result)) return result;
    if (result === undefined || result === null) return [];
    return [result];
}

/**
 * Evaluates visibility condition.
 */
export function evaluateCondition(
    data: unknown,
    condition: { path: string; operator: string; value?: unknown }
): boolean {
    const value = evaluatePath(data, condition.path);

    switch (condition.operator) {
        case 'eq':
            return value === condition.value;
        case 'neq':
            return value !== condition.value;
        case 'gt':
            return typeof value === 'number' && typeof condition.value === 'number'
                ? value > condition.value
                : false;
        case 'gte':
            return typeof value === 'number' && typeof condition.value === 'number'
                ? value >= condition.value
                : false;
        case 'lt':
            return typeof value === 'number' && typeof condition.value === 'number'
                ? value < condition.value
                : false;
        case 'lte':
            return typeof value === 'number' && typeof condition.value === 'number'
                ? value <= condition.value
                : false;
        case 'exists':
            return value !== undefined && value !== null;
        default:
            return true;
    }
}
