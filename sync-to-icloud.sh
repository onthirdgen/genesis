#!/bin/bash

# ==============================================================================
# Sync genesis directory to iCloud with .gitignore exclusions
# ==============================================================================
# This script syncs /Users/jon/AI/genesis to iCloud, excluding files matching
# .gitignore patterns. Only copies files that have changed (different size or
# modification time).
# ==============================================================================

set -e  # Exit on error

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Source and destination directories (absolute paths)
SOURCE_DIR="/Users/jon/AI/genesis"
DEST_DIR="/Users/jon/Library/Mobile Documents/com~apple~CloudDocs/AI/genesis"
GITIGNORE_FILE="$SOURCE_DIR/.gitignore"

echo -e "${BLUE}==============================================================================${NC}"
echo -e "${BLUE}Genesis to iCloud Sync Script${NC}"
echo -e "${BLUE}==============================================================================${NC}"
echo ""
echo -e "${YELLOW}Source:${NC}      $SOURCE_DIR"
echo -e "${YELLOW}Destination:${NC} $DEST_DIR"
echo ""

# Prompt for delay
echo -e "${YELLOW}Enter delay in minutes before starting sync (0 for immediate):${NC} "
read -r DELAY_MINUTES

# Validate input is a non-negative number
if ! [[ "$DELAY_MINUTES" =~ ^[0-9]+$ ]]; then
    echo -e "${YELLOW}Error: Invalid input. Please enter a non-negative number.${NC}"
    exit 1
fi

# Apply delay if specified
if [ "$DELAY_MINUTES" -gt 0 ]; then
    DELAY_SECONDS=$((DELAY_MINUTES * 60))
    echo ""
    echo -e "${YELLOW}Waiting ${DELAY_MINUTES} minute(s) before starting sync...${NC}"
    echo -e "${YELLOW}Press Ctrl+C to cancel${NC}"
    sleep "$DELAY_SECONDS"
    echo -e "${GREEN}✓${NC} Delay complete, starting sync now"
    echo ""
fi

# Verify source directory exists
if [ ! -d "$SOURCE_DIR" ]; then
    echo -e "${YELLOW}Error: Source directory does not exist: $SOURCE_DIR${NC}"
    exit 1
fi

# Create destination directory if it doesn't exist
if [ ! -d "$DEST_DIR" ]; then
    echo -e "${YELLOW}Creating destination directory...${NC}"
    mkdir -p "$DEST_DIR"
fi

# Build exclude file for rsync from .gitignore
TEMP_EXCLUDE_FILE=$(mktemp)
trap "rm -f $TEMP_EXCLUDE_FILE" EXIT

# Always exclude .git directory
echo ".git/" > "$TEMP_EXCLUDE_FILE"
echo ".git" >> "$TEMP_EXCLUDE_FILE"

# Include ./claude and ./private directories (even if in .gitignore)
echo "+ /claude/" >> "$TEMP_EXCLUDE_FILE"
echo "+ /claude/**" >> "$TEMP_EXCLUDE_FILE"
echo "+ /private/" >> "$TEMP_EXCLUDE_FILE"
echo "+ /private/**" >> "$TEMP_EXCLUDE_FILE"

# Parse .gitignore if it exists
if [ -f "$GITIGNORE_FILE" ]; then
    echo -e "${YELLOW}Reading .gitignore exclusions...${NC}"

    while IFS= read -r line; do
        # Skip empty lines and comments
        [[ -z "$line" || "$line" =~ ^[[:space:]]*#.*$ ]] && continue

        # Trim whitespace
        line=$(echo "$line" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
        [[ -z "$line" ]] && continue

        # Handle negation patterns (lines starting with !)
        if [[ "$line" =~ ^\! ]]; then
            # rsync uses + for include patterns
            pattern="${line#!}"
            echo "+ $pattern" >> "$TEMP_EXCLUDE_FILE"
        else
            # Add exclude pattern
            # Remove leading slash if present (rsync handles paths relative to source)
            pattern="${line#/}"
            echo "$pattern" >> "$TEMP_EXCLUDE_FILE"
        fi
    done < "$GITIGNORE_FILE"

    echo -e "${GREEN}✓${NC} Loaded exclusions from .gitignore"
else
    echo -e "${YELLOW}Warning: .gitignore not found at $GITIGNORE_FILE${NC}"
fi

echo ""
echo -e "${YELLOW}Starting sync (delta copy only)...${NC}"
echo ""

# Run rsync with:
# -a: archive mode (preserve permissions, timestamps, etc.)
# -v: verbose
# -h: human-readable sizes
# --progress: show progress for each file
# --exclude-from: read exclude patterns from file
# --delete: delete files in dest that don't exist in source (optional - commented out for safety)
# By default, rsync only copies files with different size or modification time

rsync -avh \
    --progress \
    --exclude-from="$TEMP_EXCLUDE_FILE" \
    --stats \
    "$SOURCE_DIR/" \
    "$DEST_DIR/"

# Note: removed --delete flag for safety - uncomment if you want exact mirror
# Add --delete after --stats if you want to remove files from destination that don't exist in source

echo ""
echo -e "${GREEN}==============================================================================${NC}"
echo -e "${GREEN}✓ Sync complete!${NC}"
echo -e "${GREEN}==============================================================================${NC}"
