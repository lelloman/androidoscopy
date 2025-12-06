import { describe, it, expect } from 'vitest';
import { evaluateAlert, getAlertClass } from './alertLogic';
import type { AlertConfig } from '../types/protocol';

describe('evaluateAlert', () => {
    describe('when no alert config', () => {
        it('returns null', () => {
            expect(evaluateAlert(undefined, { value: 100 })).toBeNull();
        });
    });

    describe('with gt operator', () => {
        const alert: AlertConfig = {
            condition: { path: '$.value', operator: 'gt', value: 50 },
            severity: 'warning',
            message: 'Value too high'
        };

        it('triggers when value exceeds threshold', () => {
            const result = evaluateAlert(alert, { value: 75 });
            expect(result).toEqual({
                isTriggered: true,
                severity: 'warning',
                message: 'Value too high'
            });
        });

        it('does not trigger when value is below threshold', () => {
            const result = evaluateAlert(alert, { value: 25 });
            expect(result).toBeNull();
        });

        it('does not trigger when value equals threshold', () => {
            const result = evaluateAlert(alert, { value: 50 });
            expect(result).toBeNull();
        });
    });

    describe('with gte operator', () => {
        const alert: AlertConfig = {
            condition: { path: '$.level', operator: 'gte', value: 90 },
            severity: 'critical',
            message: 'Critical level reached'
        };

        it('triggers when value equals threshold', () => {
            const result = evaluateAlert(alert, { level: 90 });
            expect(result?.isTriggered).toBe(true);
            expect(result?.severity).toBe('critical');
        });

        it('triggers when value exceeds threshold', () => {
            const result = evaluateAlert(alert, { level: 95 });
            expect(result?.isTriggered).toBe(true);
        });

        it('does not trigger when value is below threshold', () => {
            const result = evaluateAlert(alert, { level: 89 });
            expect(result).toBeNull();
        });
    });

    describe('with lt operator', () => {
        const alert: AlertConfig = {
            condition: { path: '$.battery', operator: 'lt', value: 20 },
            severity: 'warning',
            message: 'Low battery'
        };

        it('triggers when value is below threshold', () => {
            const result = evaluateAlert(alert, { battery: 15 });
            expect(result?.isTriggered).toBe(true);
        });

        it('does not trigger when value is above threshold', () => {
            const result = evaluateAlert(alert, { battery: 50 });
            expect(result).toBeNull();
        });
    });

    describe('with eq operator', () => {
        const alert: AlertConfig = {
            condition: { path: '$.status', operator: 'eq', value: 'ERROR' },
            severity: 'critical',
            message: 'Error detected'
        };

        it('triggers when value matches', () => {
            const result = evaluateAlert(alert, { status: 'ERROR' });
            expect(result?.isTriggered).toBe(true);
        });

        it('does not trigger when value differs', () => {
            const result = evaluateAlert(alert, { status: 'OK' });
            expect(result).toBeNull();
        });
    });

    describe('severity levels', () => {
        it('returns info severity', () => {
            const alert: AlertConfig = {
                condition: { path: '$.x', operator: 'gt', value: 0 },
                severity: 'info',
                message: 'Info alert'
            };
            const result = evaluateAlert(alert, { x: 1 });
            expect(result?.severity).toBe('info');
        });

        it('returns warning severity', () => {
            const alert: AlertConfig = {
                condition: { path: '$.x', operator: 'gt', value: 0 },
                severity: 'warning',
                message: 'Warning alert'
            };
            const result = evaluateAlert(alert, { x: 1 });
            expect(result?.severity).toBe('warning');
        });

        it('returns critical severity', () => {
            const alert: AlertConfig = {
                condition: { path: '$.x', operator: 'gt', value: 0 },
                severity: 'critical',
                message: 'Critical alert'
            };
            const result = evaluateAlert(alert, { x: 1 });
            expect(result?.severity).toBe('critical');
        });
    });
});

describe('getAlertClass', () => {
    it('returns empty string when no alert state', () => {
        expect(getAlertClass(null)).toBe('');
    });

    it('returns alert-info class for info severity', () => {
        expect(getAlertClass({ isTriggered: true, severity: 'info', message: '' })).toBe('alert-info');
    });

    it('returns alert-warning class for warning severity', () => {
        expect(getAlertClass({ isTriggered: true, severity: 'warning', message: '' })).toBe('alert-warning');
    });

    it('returns alert-critical class for critical severity', () => {
        expect(getAlertClass({ isTriggered: true, severity: 'critical', message: '' })).toBe('alert-critical');
    });

    it('returns empty string when not triggered', () => {
        expect(getAlertClass({ isTriggered: false, severity: 'warning', message: '' })).toBe('');
    });
});
