export interface ChartDataPoint {
    value: number;
    timestamp: number;
}

export interface ChartStats {
    min: number;
    max: number;
    avg: number;
}

export interface ViewWindow {
    startIndex: number;
    endIndex: number;
}

export function calculateChartPath(
    dataPoints: ChartDataPoint[],
    maxPoints: number,
    width: number = 100,
    height: number = 100,
    padding: number = 5
): string {
    if (dataPoints.length < 2) return '';

    const values = dataPoints.map(p => p.value);
    const minValue = Math.min(...values);
    const maxValue = Math.max(...values);
    const range = maxValue - minValue || 1;

    const points = dataPoints.map((p, i) => {
        const x = (i / (maxPoints - 1)) * width;
        const y = height - padding - ((p.value - minValue) / range) * (height - 2 * padding);
        return { x, y };
    });

    return points
        .map((p, i) => (i === 0 ? `M ${p.x},${p.y}` : `L ${p.x},${p.y}`))
        .join(' ');
}

export function calculateChartPathWithView(
    dataPoints: ChartDataPoint[],
    view: ViewWindow,
    width: number = 100,
    height: number = 100,
    padding: number = 5
): string {
    const viewedPoints = dataPoints.slice(view.startIndex, view.endIndex + 1);
    if (viewedPoints.length < 2) return '';

    const values = viewedPoints.map(p => p.value);
    const minValue = Math.min(...values);
    const maxValue = Math.max(...values);
    const range = maxValue - minValue || 1;

    const points = viewedPoints.map((p, i) => {
        const x = (i / (viewedPoints.length - 1)) * width;
        const y = height - padding - ((p.value - minValue) / range) * (height - 2 * padding);
        return { x, y };
    });

    return points
        .map((p, i) => (i === 0 ? `M ${p.x},${p.y}` : `L ${p.x},${p.y}`))
        .join(' ');
}

export function getMinMax(dataPoints: ChartDataPoint[]): { min: number; max: number } {
    if (dataPoints.length === 0) {
        return { min: 0, max: 100 };
    }
    const values = dataPoints.map(p => p.value);
    return {
        min: Math.min(...values),
        max: Math.max(...values)
    };
}

export function getStats(dataPoints: ChartDataPoint[]): ChartStats {
    if (dataPoints.length === 0) {
        return { min: 0, max: 0, avg: 0 };
    }
    const values = dataPoints.map(p => p.value);
    const sum = values.reduce((a, b) => a + b, 0);
    return {
        min: Math.min(...values),
        max: Math.max(...values),
        avg: sum / values.length
    };
}

export function getPointAtX(
    dataPoints: ChartDataPoint[],
    x: number,
    containerWidth: number,
    view: ViewWindow
): ChartDataPoint | null {
    const viewedPoints = dataPoints.slice(view.startIndex, view.endIndex + 1);
    if (viewedPoints.length === 0) return null;

    const ratio = x / containerWidth;
    const index = Math.round(ratio * (viewedPoints.length - 1));
    return viewedPoints[Math.max(0, Math.min(index, viewedPoints.length - 1))] ?? null;
}

export function zoom(
    currentView: ViewWindow,
    totalPoints: number,
    zoomIn: boolean,
    centerRatio: number = 0.5
): ViewWindow {
    const viewSize = currentView.endIndex - currentView.startIndex;
    const minViewSize = 5; // Minimum 5 points visible

    let newViewSize: number;
    if (zoomIn) {
        newViewSize = Math.max(minViewSize, Math.floor(viewSize * 0.8));
    } else {
        newViewSize = Math.min(totalPoints, Math.ceil(viewSize * 1.25));
    }

    const center = currentView.startIndex + viewSize * centerRatio;
    let newStart = Math.round(center - newViewSize * centerRatio);
    let newEnd = newStart + newViewSize;

    // Clamp to valid range
    if (newStart < 0) {
        newStart = 0;
        newEnd = Math.min(newViewSize, totalPoints);
    }
    if (newEnd > totalPoints) {
        newEnd = totalPoints;
        newStart = Math.max(0, totalPoints - newViewSize);
    }

    return { startIndex: newStart, endIndex: newEnd };
}

export function pan(
    currentView: ViewWindow,
    totalPoints: number,
    deltaRatio: number
): ViewWindow {
    const viewSize = currentView.endIndex - currentView.startIndex;
    const deltaPx = Math.round(viewSize * deltaRatio);

    let newStart = currentView.startIndex - deltaPx;
    let newEnd = currentView.endIndex - deltaPx;

    // Clamp to valid range
    if (newStart < 0) {
        newStart = 0;
        newEnd = viewSize;
    }
    if (newEnd > totalPoints) {
        newEnd = totalPoints;
        newStart = totalPoints - viewSize;
    }

    return { startIndex: Math.max(0, newStart), endIndex: Math.min(totalPoints, newEnd) };
}

export function exportToCSV(dataPoints: ChartDataPoint[], label: string): string {
    const header = 'timestamp,datetime,value';
    const rows = dataPoints.map(p => {
        const date = new Date(p.timestamp).toISOString();
        return `${p.timestamp},${date},${p.value}`;
    });
    return [header, ...rows].join('\n');
}

export function downloadCSV(dataPoints: ChartDataPoint[], label: string): void {
    const csv = exportToCSV(dataPoints, label);
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${label.toLowerCase().replace(/\s+/g, '_')}_chart_${new Date().toISOString().slice(0, 19).replace(/:/g, '-')}.csv`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}

export function downloadPNG(canvas: HTMLCanvasElement, label: string): void {
    const url = canvas.toDataURL('image/png');
    const a = document.createElement('a');
    a.href = url;
    a.download = `${label.toLowerCase().replace(/\s+/g, '_')}_chart_${new Date().toISOString().slice(0, 19).replace(/:/g, '-')}.png`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
}

export function formatTimestamp(timestamp: number): string {
    const date = new Date(timestamp);
    return date.toLocaleTimeString('en-US', {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    });
}

export const TIME_WINDOWS = [
    { label: '30s', seconds: 30 },
    { label: '1m', seconds: 60 },
    { label: '5m', seconds: 300 },
    { label: '15m', seconds: 900 },
    { label: 'All', seconds: Infinity }
] as const;

export function filterByTimeWindow(
    dataPoints: ChartDataPoint[],
    windowSeconds: number
): ChartDataPoint[] {
    if (windowSeconds === Infinity || dataPoints.length === 0) {
        return dataPoints;
    }
    const cutoff = Date.now() - windowSeconds * 1000;
    return dataPoints.filter(p => p.timestamp >= cutoff);
}
