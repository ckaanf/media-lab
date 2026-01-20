#!/bin/bash
chmod +x drop_cache.sh
./drop_cache.sh

echo "Starting Java server..."
java -jar simple-spring-stream.jar &
JAVA_PID=$!

sleep 5

echo "Running k6 test..."
k6 run ../k6/vod_range.js | tee ../results/java_range.txt

kill $JAVA_PID
