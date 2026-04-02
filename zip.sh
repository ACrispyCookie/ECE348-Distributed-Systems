#!/bin/bash

# Configuration
GROUP=5
ASSIGNMENT=2
BASE_DIR="./project2/simple-chat-app/"  # Change this to wherever your project is
FOLDER_NAME="G${GROUP}_${ASSIGNMENT}"
ARCHIVE_NAME="${FOLDER_NAME}.tar.gz"
PRESENTATION="G${GROUP}_${ASSIGNMENT}_presentation.pdf"

# Folders to include (relative to BASE_DIR)
SOURCE_FOLDERS=(
    "src"
    "custom_tests"
    "build.gradle"
    "settings.gradle"
    "gradle"
    "gradlew"
    "gradlew.bat"
    ".gitignore"
)

# Create the submission folder
mkdir -p "$FOLDER_NAME"

# Copy source folders into it
for folder in "${SOURCE_FOLDERS[@]}"; do
    src="$BASE_DIR/$folder"
    if [ -e "$src" ]; then
        cp -r "$src" "$FOLDER_NAME/"
        echo "✅ Copied: $src"
    else
        echo "⚠️  Not found, skipping: $src"
    fi
done

# Create the tar.gz archive
tar -zcvf "$ARCHIVE_NAME" "$FOLDER_NAME"
echo "✅ Created archive: $ARCHIVE_NAME"

# Cleanup temporary folder
rm -rf "$FOLDER_NAME"
echo "🧹 Cleaned up temporary folder: $FOLDER_NAME"

# Handle presentation PDF
if [ -f "$PRESENTATION" ]; then
    echo "✅ Presentation found: $PRESENTATION"
else
    echo "⚠️  Presentation not found: $PRESENTATION (upload it manually)"
fi

echo ""
echo "--- Done! Files to submit: ---"
echo "  📦 $ARCHIVE_NAME"
[ -f "$PRESENTATION" ] && echo "  📄 $PRESENTATION"
