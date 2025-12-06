#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FAILED=0
PASSED=0
SKIPPED=0

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_header() {
    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

print_result() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✓ $2 passed${NC}"
        PASSED=$((PASSED + 1))
    else
        echo -e "${RED}✗ $2 failed${NC}"
        FAILED=$((FAILED + 1))
    fi
}

print_skip() {
    echo -e "${YELLOW}○ $1 skipped${NC}"
    SKIPPED=$((SKIPPED + 1))
}

# Parse arguments
RUN_E2E=true
RUN_ANDROID_INSTRUMENTED=true

for arg in "$@"; do
    case $arg in
        --skip-e2e)
            RUN_E2E=false
            ;;
        --skip-android-instrumented)
            RUN_ANDROID_INSTRUMENTED=false
            ;;
        --unit-only)
            RUN_E2E=false
            RUN_ANDROID_INSTRUMENTED=false
            ;;
        --help|-h)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --skip-e2e                   Skip E2E tests (dashboard Playwright + full-stack)"
            echo "  --skip-android-instrumented  Skip Android instrumented tests"
            echo "  --unit-only                  Run only unit tests (skip E2E and instrumented)"
            echo "  -h, --help                   Show this help message"
            echo ""
            echo "By default, runs ALL tests."
            exit 0
            ;;
    esac
done

echo -e "${BLUE}"
echo "╔═══════════════════════════════════════════════════════════╗"
echo "║                   ANDROIDOSCOPY TESTS                     ║"
echo "╚═══════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# Server tests
print_header "Server Tests (Rust)"
cd "$SCRIPT_DIR/server"
if cargo test; then
    print_result 0 "Server tests"
else
    print_result 1 "Server tests"
fi

# Dashboard unit tests
print_header "Dashboard Unit Tests (Vitest)"
cd "$SCRIPT_DIR/dashboard"
if npm test; then
    print_result 0 "Dashboard unit tests"
else
    print_result 1 "Dashboard unit tests"
fi

# Dashboard E2E tests
print_header "Dashboard E2E Tests (Playwright)"
if [ "$RUN_E2E" = true ]; then
    cd "$SCRIPT_DIR/dashboard"
    if npm run test:e2e; then
        print_result 0 "Dashboard E2E tests"
    else
        print_result 1 "Dashboard E2E tests"
    fi
else
    print_skip "Dashboard E2E tests (use --skip-e2e was set)"
fi

# Android SDK unit tests
print_header "Android SDK Unit Tests (JUnit)"
cd "$SCRIPT_DIR/android"
if ./gradlew :sdk:test; then
    print_result 0 "Android SDK unit tests"
else
    print_result 1 "Android SDK unit tests"
fi

# Android SDK instrumented tests
print_header "Android SDK Instrumented Tests (Espresso)"
if [ "$RUN_ANDROID_INSTRUMENTED" = true ]; then
    cd "$SCRIPT_DIR/android"
    if ./gradlew :sdk:connectedDebugAndroidTest; then
        print_result 0 "Android SDK instrumented tests"
    else
        print_result 1 "Android SDK instrumented tests"
    fi
else
    print_skip "Android instrumented tests (--skip-android-instrumented was set)"
fi

# Full-stack E2E tests
print_header "Full-Stack E2E Tests (Rust)"
if [ "$RUN_E2E" = true ]; then
    cd "$SCRIPT_DIR/e2e"
    if cargo test; then
        print_result 0 "Full-stack E2E tests"
    else
        print_result 1 "Full-stack E2E tests"
    fi
else
    print_skip "Full-stack E2E tests (--skip-e2e was set)"
fi

# Summary
echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}  SUMMARY${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "  ${GREEN}Passed:  $PASSED${NC}"
echo -e "  ${RED}Failed:  $FAILED${NC}"
echo -e "  ${YELLOW}Skipped: $SKIPPED${NC}"
echo ""

if [ $FAILED -gt 0 ]; then
    echo -e "${RED}Some tests failed!${NC}"
    exit 1
else
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
fi
