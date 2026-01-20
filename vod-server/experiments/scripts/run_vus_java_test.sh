#!/bin/bash
chmod +x drop_cache.sh

VUS=(100 300 500)

for USER in "${VUS[@]}"
do
    echo "========================================"
    echo "🚀 Starting Test with VUS: $USER"
    echo "========================================"

    ./drop_cache.sh

    echo "Starting Java server..."
    java -jar simple-spring-stream.jar &
    JAVA_PID=$!
    sleep 5

    TIMESTAMP=$(date +%Y%m%d_%H%M)
    RESULT_FILE="../results/java_range_${TIMESTAMP}_${USER}.txt"

    echo "Running k6 test (Saving to: $RESULT_FILE)..."
    VUS=$USER k6 run ../k6/vod_range.js | tee "$RESULT_FILE"

    echo "Stopping Java server (PID: $JAVA_PID)..."
    kill $JAVA_PID
    sleep 2
done

echo "✅ All tests completed!"