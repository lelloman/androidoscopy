import { describe, it, expect } from 'vitest';
import { filterLogs, isAtBottom, LOG_LEVELS, type LogFilterOptions } from './logViewerLogic';
import type { LogEntry, LogLevel } from '../types/protocol';

function createLog(
    level: LogEntry['level'],
    message: string,
    tag?: string,
    throwable?: string
): LogEntry {
    return {
        timestamp: '2024-01-01T12:00:00.000Z',
        level,
        message,
        tag,
        throwable,
    };
}

describe('filterLogs', () => {
    const sampleLogs: LogEntry[] = [
        createLog('VERBOSE', 'Verbose message', 'NetworkTag'),
        createLog('DEBUG', 'Debug message', 'UITag'),
        createLog('INFO', 'Info message', 'AppTag'),
        createLog('WARN', 'Warning message', 'NetworkTag'),
        createLog('ERROR', 'Error message', 'UITag'),
    ];

    describe('level filtering', () => {
        it('shows all logs when filter is VERBOSE', () => {
            const options: LogFilterOptions = { levelFilter: 'VERBOSE', tagFilter: '', searchFilter: '' };
            const result = filterLogs(sampleLogs, options);
            expect(result).toHaveLength(5);
        });

        it('filters out VERBOSE when filter is DEBUG', () => {
            const options: LogFilterOptions = { levelFilter: 'DEBUG', tagFilter: '', searchFilter: '' };
            const result = filterLogs(sampleLogs, options);
            expect(result).toHaveLength(4);
            expect(result.every(log => log.level !== 'VERBOSE')).toBe(true);
        });

        it('filters out VERBOSE and DEBUG when filter is INFO', () => {
            const options: LogFilterOptions = { levelFilter: 'INFO', tagFilter: '', searchFilter: '' };
            const result = filterLogs(sampleLogs, options);
            expect(result).toHaveLength(3);
            expect(result.map(log => log.level)).toEqual(['INFO', 'WARN', 'ERROR']);
        });

        it('shows only WARN and ERROR when filter is WARN', () => {
            const options: LogFilterOptions = { levelFilter: 'WARN', tagFilter: '', searchFilter: '' };
            const result = filterLogs(sampleLogs, options);
            expect(result).toHaveLength(2);
            expect(result.map(log => log.level)).toEqual(['WARN', 'ERROR']);
        });

        it('shows only ERROR when filter is ERROR', () => {
            const options: LogFilterOptions = { levelFilter: 'ERROR', tagFilter: '', searchFilter: '' };
            const result = filterLogs(sampleLogs, options);
            expect(result).toHaveLength(1);
            expect(result[0].level).toBe('ERROR');
        });
    });

    describe('tag filtering', () => {
        it('filters by exact tag match (case insensitive)', () => {
            const options: LogFilterOptions = { levelFilter: 'VERBOSE', tagFilter: 'uitag', searchFilter: '' };
            const result = filterLogs(sampleLogs, options);
            expect(result).toHaveLength(2);
            expect(result.every(log => log.tag === 'UITag')).toBe(true);
        });

        it('filters by partial tag match', () => {
            const options: LogFilterOptions = { levelFilter: 'VERBOSE', tagFilter: 'Network', searchFilter: '' };
            const result = filterLogs(sampleLogs, options);
            expect(result).toHaveLength(2);
            expect(result.every(log => log.tag?.includes('Network'))).toBe(true);
        });

        it('excludes logs without tags when tag filter is set', () => {
            const logsWithNoTag = [
                createLog('INFO', 'Message with tag', 'Tag'),
                createLog('INFO', 'Message without tag'),
            ];
            const options: LogFilterOptions = { levelFilter: 'VERBOSE', tagFilter: 'Tag', searchFilter: '' };
            const result = filterLogs(logsWithNoTag, options);
            expect(result).toHaveLength(1);
            expect(result[0].tag).toBe('Tag');
        });

        it('returns empty when no tags match', () => {
            const options: LogFilterOptions = { levelFilter: 'VERBOSE', tagFilter: 'NonExistent', searchFilter: '' };
            const result = filterLogs(sampleLogs, options);
            expect(result).toHaveLength(0);
        });
    });

    describe('search filtering', () => {
        it('filters by message content (case insensitive)', () => {
            const options: LogFilterOptions = { levelFilter: 'VERBOSE', tagFilter: '', searchFilter: 'error' };
            const result = filterLogs(sampleLogs, options);
            expect(result).toHaveLength(1);
            expect(result[0].message).toBe('Error message');
        });

        it('filters by partial message match', () => {
            const options: LogFilterOptions = { levelFilter: 'VERBOSE', tagFilter: '', searchFilter: 'message' };
            const result = filterLogs(sampleLogs, options);
            expect(result).toHaveLength(5); // All logs contain "message"
        });

        it('returns empty when no messages match', () => {
            const options: LogFilterOptions = { levelFilter: 'VERBOSE', tagFilter: '', searchFilter: 'xyz123' };
            const result = filterLogs(sampleLogs, options);
            expect(result).toHaveLength(0);
        });
    });

    describe('combined filtering', () => {
        it('applies level and tag filters together', () => {
            const options: LogFilterOptions = { levelFilter: 'INFO', tagFilter: 'NetworkTag', searchFilter: '' };
            const result = filterLogs(sampleLogs, options);
            expect(result).toHaveLength(1);
            expect(result[0].level).toBe('WARN');
            expect(result[0].tag).toBe('NetworkTag');
        });

        it('applies level and search filters together', () => {
            const options: LogFilterOptions = { levelFilter: 'WARN', tagFilter: '', searchFilter: 'error' };
            const result = filterLogs(sampleLogs, options);
            expect(result).toHaveLength(1);
            expect(result[0].level).toBe('ERROR');
        });

        it('applies tag and search filters together', () => {
            const options: LogFilterOptions = { levelFilter: 'VERBOSE', tagFilter: 'UI', searchFilter: 'error' };
            const result = filterLogs(sampleLogs, options);
            expect(result).toHaveLength(1);
            expect(result[0].tag).toBe('UITag');
            expect(result[0].message).toBe('Error message');
        });

        it('applies all three filters together', () => {
            const options: LogFilterOptions = { levelFilter: 'DEBUG', tagFilter: 'UI', searchFilter: 'Debug' };
            const result = filterLogs(sampleLogs, options);
            expect(result).toHaveLength(1);
            expect(result[0]).toEqual(sampleLogs[1]); // Debug message with UITag
        });

        it('returns empty when combined filters exclude all', () => {
            const options: LogFilterOptions = { levelFilter: 'ERROR', tagFilter: 'Network', searchFilter: '' };
            const result = filterLogs(sampleLogs, options);
            expect(result).toHaveLength(0);
        });
    });

    describe('edge cases', () => {
        it('handles empty log array', () => {
            const options: LogFilterOptions = { levelFilter: 'VERBOSE', tagFilter: '', searchFilter: '' };
            const result = filterLogs([], options);
            expect(result).toHaveLength(0);
        });

        it('handles empty tag filter', () => {
            const options: LogFilterOptions = { levelFilter: 'VERBOSE', tagFilter: '', searchFilter: '' };
            const result = filterLogs(sampleLogs, options);
            expect(result).toHaveLength(5);
        });

        it('handles empty search filter', () => {
            const options: LogFilterOptions = { levelFilter: 'VERBOSE', tagFilter: '', searchFilter: '' };
            const result = filterLogs(sampleLogs, options);
            expect(result).toHaveLength(5);
        });

        it('handles logs with throwable property', () => {
            const logsWithThrowable = [
                createLog('ERROR', 'Error with throwable', 'Tag', 'java.lang.NullPointerException'),
                createLog('ERROR', 'Error without throwable', 'Tag'),
            ];
            const options: LogFilterOptions = { levelFilter: 'VERBOSE', tagFilter: '', searchFilter: '' };
            const result = filterLogs(logsWithThrowable, options);
            expect(result).toHaveLength(2);
        });

        it('preserves original order of logs', () => {
            const options: LogFilterOptions = { levelFilter: 'VERBOSE', tagFilter: '', searchFilter: '' };
            const result = filterLogs(sampleLogs, options);
            expect(result).toEqual(sampleLogs);
        });

        it('does not mutate original array', () => {
            const original = [...sampleLogs];
            const options: LogFilterOptions = { levelFilter: 'ERROR', tagFilter: '', searchFilter: '' };
            filterLogs(sampleLogs, options);
            expect(sampleLogs).toEqual(original);
        });
    });
});

describe('isAtBottom', () => {
    it('returns true when scrolled to bottom', () => {
        expect(isAtBottom(1000, 900, 100)).toBe(true);
    });

    it('returns true when exactly at bottom', () => {
        expect(isAtBottom(1000, 900, 100)).toBe(true);
    });

    it('returns true when within threshold of bottom', () => {
        expect(isAtBottom(1000, 860, 100, 50)).toBe(true);
    });

    it('returns false when scrolled above threshold', () => {
        expect(isAtBottom(1000, 800, 100, 50)).toBe(false);
    });

    it('returns false when scrolled to top', () => {
        expect(isAtBottom(1000, 0, 100)).toBe(false);
    });

    it('returns true for small container that fits all content', () => {
        expect(isAtBottom(100, 0, 100)).toBe(true);
    });

    it('uses default threshold of 50', () => {
        expect(isAtBottom(1000, 855, 100)).toBe(true); // 1000 - 855 = 145 <= 100 + 50
        expect(isAtBottom(1000, 849, 100)).toBe(false); // 1000 - 849 = 151 > 100 + 50
    });

    it('handles custom threshold', () => {
        expect(isAtBottom(1000, 800, 100, 100)).toBe(true);
        expect(isAtBottom(1000, 799, 100, 100)).toBe(false);
    });
});

describe('LOG_LEVELS', () => {
    it('has levels in correct order from least to most severe', () => {
        expect(LOG_LEVELS).toEqual(['VERBOSE', 'DEBUG', 'INFO', 'WARN', 'ERROR']);
    });

    it('has 5 log levels', () => {
        expect(LOG_LEVELS).toHaveLength(5);
    });
});
