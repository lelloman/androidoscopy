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

- [ ] **batterySection()** - battery gauge, charging badge, temperature
- [ ] **storageSection()** - storage gauge, cache size, available space
- [ ] **threadSection()** - thread count, active threads list

## 3. Chart Widget

- [ ] **Define chart widget schema** - time-series visualization
- [ ] **Add chart() to SDK DSL** - RowBuilder method
- [ ] **Implement Chart.svelte** - canvas-based line chart
- [ ] **Add chart to Widget dispatcher**
- [ ] **Write component tests for Chart**

## 4. Built-in Actions

- [ ] **Force GC action** - built-in handler calling System.gc()
- [ ] **Clear Caches action** - clear app cache directory
- [ ] **Add actions to memorySection()** - GC button

## 5. Demo App

- [ ] **Create MainActivity** - full SDK integration with all providers
- [ ] **Add demo custom data** - counter, mock stats, action handlers
- [ ] **Integrate Timber** - logging demo

---

*Last updated: 2025-12-06*
