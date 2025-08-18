#!/bin/bash

# Kill any process running on port 8010
echo "Killing any process on port 8010..."
lsof -ti:8010 | xargs kill -9 2>/dev/null || echo "No process found on port 8010"

# Start Python HTTP server on port 8010
echo "Starting Python HTTP server on port 8010..."
python3 -m http.server 8010 &
SERVER_PID=$!

# Wait a moment for server to start
sleep 2

# Open index.html in default browser
echo "Opening index.html in default browser..."
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    open "http://localhost:8010/index.html"
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    # Linux
    xdg-open "http://localhost:8010/index.html" 2>/dev/null || sensible-browser "http://localhost:8010/index.html" 2>/dev/null || echo "Please open http://localhost:8010/index.html in your browser"
else
    # Windows or other
    echo "Please open http://localhost:8010/index.html in your browser"
fi

echo "Server started! Press Ctrl+C to stop."
echo "Server PID: $SERVER_PID"

# Wait for user to stop the server
trap "echo 'Stopping server...'; kill $SERVER_PID; exit" INT
wait $SERVER_PID
