#!/bin/bash
chmod +x drop_cache.sh

VUS=(100 300 500)

for USER in "${VUS[@]}"
do
    echo "========================================"
    echo "🚀 Starting C++ Test with VUS: $USER"
    echo "========================================"

    ./drop_cache.sh

    pushd ../../cpp/simple-cpp-stream/ > /dev/null
    export VIDEO_PATH="../../../videos/"
    ./server &
    CPP_PID=$!
    popd > /dev/null

    sleep 3

    TIMESTAMP=$(date +%Y%m%d_%H%M)
    RESULT_FILE="../results/cpp_range_${TIMESTAMP}_${USER}.txt"

    echo "Running k6 test (Saving to: $RESULT_FILE)..."
    VUS=$USER k6 run ../k6/vod_range.js | tee "$RESULT_FILE"

    kill $CPP_PID
    sleep 2
done

echo "✅ All C++ tests completed!"