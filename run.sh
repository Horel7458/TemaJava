#!/bin/bash

# Script pentru compilare și rulare aplicație RTSP Recorder

echo "================================================"
echo "RTSP Recorder Application"
echo "================================================"
echo ""

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed!"
    echo "Please install Maven first."
    exit 1
fi

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed!"
    echo "Please install Java 17 or higher."
    exit 1
fi

# Clean and compile
echo "Compiling the application..."
mvn clean package

if [ $? -ne 0 ]; then
    echo ""
    echo "Error: Compilation failed!"
    exit 1
fi

echo ""
echo "Compilation successful!"
echo ""
echo "================================================"
echo "Starting the application..."
echo "================================================"
echo ""
echo "Controls:"
echo "  Press 1: Record 3 videos (5 seconds each)"
echo "  Press 2: Playback videos in reverse order"
echo "  Press ESC: Exit application"
echo ""
echo "Note: Change RTSP_URL in VideoController.java to your stream URL"
echo ""

# Run the application with Maven (needed for JavaFX)
mvn javafx:run
