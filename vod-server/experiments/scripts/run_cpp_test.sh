#!/bin/bash
chmod +x drop_cache.sh
./drop_cache.sh

echo "Starting C++ server..."
./server &
CPP_PID=$!

sleep 3

echo "Running k6 test..."
k6 run ../k6/vod_range.js | tee ../results/cpp_range.txt

kill $CPP_PID
