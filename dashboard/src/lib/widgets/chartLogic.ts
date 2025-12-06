export interface ChartDataPoint {
    value: number;
    timestamp: number;
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
