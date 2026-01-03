#!/bin/bash
# Convenience script for running transcription-service tests

set -e  # Exit on error

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}===================================${NC}"
echo -e "${BLUE}Transcription Service Test Runner${NC}"
echo -e "${BLUE}===================================${NC}"
echo ""

# Check if we're in the right directory
if [ ! -f "main.py" ]; then
    echo -e "${RED}Error: Please run this script from the transcription-service directory${NC}"
    exit 1
fi

# Check if pytest is installed
if ! command -v pytest &> /dev/null; then
    echo -e "${RED}pytest is not installed. Installing test dependencies...${NC}"
    pip install -r requirements-dev.txt
fi

# Parse command line arguments
COVERAGE=false
VERBOSE=false
SPECIFIC_TEST=""
HTML_REPORT=false
MARKERS=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -c|--coverage)
            COVERAGE=true
            shift
            ;;
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        -h|--html)
            HTML_REPORT=true
            COVERAGE=true
            shift
            ;;
        -m|--markers)
            MARKERS="$2"
            shift 2
            ;;
        -t|--test)
            SPECIFIC_TEST="$2"
            shift 2
            ;;
        --help)
            echo "Usage: ./run_tests.sh [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  -c, --coverage     Run tests with coverage report"
            echo "  -v, --verbose      Run tests with verbose output"
            echo "  -h, --html         Generate HTML coverage report"
            echo "  -m, --markers      Run tests with specific markers (e.g., 'unit', 'integration')"
            echo "  -t, --test         Run specific test file or test"
            echo "  --help             Show this help message"
            echo ""
            echo "Examples:"
            echo "  ./run_tests.sh                           # Run all tests"
            echo "  ./run_tests.sh -c                        # Run with coverage"
            echo "  ./run_tests.sh -c -h                     # Run with HTML coverage report"
            echo "  ./run_tests.sh -v                        # Run with verbose output"
            echo "  ./run_tests.sh -t tests/test_main.py     # Run specific test file"
            echo "  ./run_tests.sh -m unit                   # Run only unit tests"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Build pytest command
PYTEST_CMD="pytest"

if [ "$VERBOSE" = true ]; then
    PYTEST_CMD="$PYTEST_CMD -vv"
fi

if [ "$COVERAGE" = true ]; then
    PYTEST_CMD="$PYTEST_CMD --cov=. --cov-report=term-missing"
    if [ "$HTML_REPORT" = true ]; then
        PYTEST_CMD="$PYTEST_CMD --cov-report=html"
    fi
fi

if [ ! -z "$MARKERS" ]; then
    PYTEST_CMD="$PYTEST_CMD -m $MARKERS"
fi

if [ ! -z "$SPECIFIC_TEST" ]; then
    PYTEST_CMD="$PYTEST_CMD $SPECIFIC_TEST"
fi

# Run tests
echo -e "${GREEN}Running tests...${NC}"
echo -e "${BLUE}Command: $PYTEST_CMD${NC}"
echo ""

if eval $PYTEST_CMD; then
    echo ""
    echo -e "${GREEN}===================================${NC}"
    echo -e "${GREEN}All tests passed!${NC}"
    echo -e "${GREEN}===================================${NC}"

    if [ "$HTML_REPORT" = true ]; then
        echo ""
        echo -e "${BLUE}HTML coverage report generated at: htmlcov/index.html${NC}"
        echo -e "${BLUE}Open it with: open htmlcov/index.html${NC}"
    fi

    exit 0
else
    echo ""
    echo -e "${RED}===================================${NC}"
    echo -e "${RED}Some tests failed!${NC}"
    echo -e "${RED}===================================${NC}"
    exit 1
fi
