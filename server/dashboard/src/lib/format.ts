export function formatBytes(bytes: number): string {
    if (bytes === 0) return '0 B';
    if (bytes < 0) return '-' + formatBytes(-bytes);

    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    const value = bytes / Math.pow(1024, i);

    return `${value.toFixed(i > 0 ? 1 : 0)} ${units[i]}`;
}

export function formatPercent(value: number): string {
    // If value is already a decimal (0-1), multiply by 100
    const percent = value > 1 ? value : value * 100;
    return `${percent.toFixed(0)}%`;
}

export function formatDuration(ms: number): string {
    if (ms < 1000) return `${ms}ms`;

    const seconds = Math.floor(ms / 1000);
    if (seconds < 60) return `${seconds}s`;

    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    if (minutes < 60) return `${minutes}m ${remainingSeconds}s`;

    const hours = Math.floor(minutes / 60);
    const remainingMinutes = minutes % 60;
    return `${hours}h ${remainingMinutes}m`;
}

export function formatNumber(value: number): string {
    return value.toLocaleString();
}

export function formatValue(value: unknown, format?: string): string {
    if (value === undefined || value === null) return '-';

    const num = typeof value === 'number' ? value : Number(value);
    if (isNaN(num) && typeof value !== 'string') return String(value);

    switch (format) {
        case 'bytes':
            return formatBytes(num);
        case 'percent':
            return formatPercent(num);
        case 'duration':
            return formatDuration(num);
        case 'number':
            return formatNumber(num);
        default:
            return typeof value === 'number' ? formatNumber(value) : String(value);
    }
}

export function formatTime(isoString: string): string {
    try {
        const date = new Date(isoString);
        return date.toLocaleTimeString('en-US', {
            hour12: false,
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            fractionalSecondDigits: 3
        });
    } catch {
        return isoString;
    }
}

export function formatDate(isoString: string): string {
    try {
        const date = new Date(isoString);
        return date.toLocaleDateString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric'
        });
    } catch {
        return isoString;
    }
}

export function formatDateTime(isoString: string): string {
    try {
        const date = new Date(isoString);
        return date.toLocaleString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hour12: false
        });
    } catch {
        return isoString;
    }
}

export function getRelativeTime(isoString: string): string {
    try {
        const date = new Date(isoString);
        const now = new Date();
        const diffMs = now.getTime() - date.getTime();
        const diffSec = Math.floor(diffMs / 1000);
        const diffMin = Math.floor(diffSec / 60);
        const diffHour = Math.floor(diffMin / 60);
        const diffDay = Math.floor(diffHour / 24);

        if (diffSec < 60) return 'just now';
        if (diffMin < 60) return `${diffMin}m ago`;
        if (diffHour < 24) return `${diffHour}h ago`;
        if (diffDay < 7) return `${diffDay}d ago`;

        return formatDate(isoString);
    } catch {
        return isoString;
    }
}
