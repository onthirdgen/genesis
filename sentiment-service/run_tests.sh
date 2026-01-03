#!/bin/bash
# Test runner script for sentiment-service

set -e  # Exit on error

echo "=========================================="
echo "Sentiment Service - Test Runner"
echo "=========================================="
echo ""

# Check if we're in the correct directory
if [ ! -f "main.py" ]; then
    echo "Error: Please run this script from the sentiment-service directory"
    exit 1
fi

# Check if virtual environment exists
if [ ! -d "venv" ]; then
    echo "No virtual environment found. Creating one..."
    python3 -m venv venv
    echo "Virtual environment created."
fi

# Activate virtual environment
echo "Activating virtual environment..."
source venv/bin/activate

# Install/upgrade dependencies
echo ""
echo "Installing dependencies..."
pip install -q --upgrade pip
pip install -q -r requirements-test.txt
echo "Dependencies installed."

# Run tests based on arguments
echo ""
echo "=========================================="
echo "Running Tests"
echo "=========================================="
echo ""

if [ "$1" == "coverage" ]; then
    echo "Running tests with coverage..."
    pytest --cov=. --cov-report=term-missing --cov-report=html
    echo ""
    echo "Coverage report generated in htmlcov/"
    echo "Open htmlcov/index.html to view detailed report"

elif [ "$1" == "unit" ]; then
    echo "Running unit tests only..."
    pytest -m unit -v

elif [ "$1" == "integration" ]; then
    echo "Running integration tests only..."
    pytest -m integration -v

elif [ "$1" == "fast" ]; then
    echo "Running fast tests (excluding slow tests)..."
    pytest -m "not slow" -v

elif [ "$1" == "watch" ]; then
    echo "Running tests in watch mode..."
    echo "Note: Install pytest-watch with 'pip install pytest-watch'"
    ptw

elif [ -n "$1" ]; then
    # Run specific test file or pattern
    echo "Running: $@"
    pytest "$@"

else
    # Run all tests
    echo "Running all tests..."
    pytest -v
fi

echo ""
echo "=========================================="
echo "Test run complete!"
echo "=========================================="
