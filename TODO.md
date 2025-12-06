# Androidoscopy - Extended Features

**Legend:**
- `[ ]` - Not started
- `[~]` - In progress
- `[x]` - Completed

---

## 1. Additional Data Providers

- [x] **BatteryDataProvider** - level, charging state, temperature, health
- [x] **StorageDataProvider** - app data size, cache size, available space
- [x] **ThreadDataProvider** - active count, thread list
- [x] **Write unit tests for new data providers**

## 2. Dashboard Templates

- [x] **batterySection()** - battery gauge, charging badge, temperature
- [x] **storageSection()** - storage gauge, cache size, available space
- [x] **threadSection()** - thread count, active threads list

## 3. Chart Widget

- [x] **Define chart widget schema** - time-series visualization
- [x] **Add chart() to SDK DSL** - RowBuilder method
- [x] **Implement Chart.svelte** - canvas-based line chart
- [x] **Add chart to Widget dispatcher**
- [x] **Write component tests for Chart**

## 4. Built-in Actions

- [x] **Force GC action** - built-in handler calling System.gc()
- [x] **Clear Caches action** - clear app cache directory
- [x] **Add actions to memorySection()** - GC button

## 5. Demo App

- [x] **Create MainActivity** - full SDK integration with all providers
- [x] **Add demo custom data** - counter, mock stats, action handlers
- [x] **Integrate Timber** - logging demo

---

*Last updated: 2025-12-06*
