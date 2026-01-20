#!/bin/bash
chmod +x drop_cache.sh

CHUNKS=(65536 262144 1048576)

for CHUNK in "${CHUNKS[@]}"
do
    echo "========================================"
    echo "🚀 Starting C++ Test with CHUNK_SIZE: $CHUNK"
    echo "========================================"

    ./drop_cache.sh

    pushd ../../cpp/simple-cpp-stream/ > /dev/null
    export VIDEO_PATH="../../../videos/"
    ./server &
    CPP_PID=$!
    popd > /dev/null

    sleep 3

    TIMESTAMP=$(date +%Y%m%d_%H%M)
    RESULT_FILE="../results/cpp_range_${TIMESTAMP}_${CHUNK}.txt"

    echo "Running k6 test (Saving to: $RESULT_FILE)..."
    CHUNK_SIZE=$CHUNK k6 run ../k6/vod_range.js | tee "$RESULT_FILE"

    kill $CPP_PID
    sleep 2
done

echo "✅ All C++ tests completed!"