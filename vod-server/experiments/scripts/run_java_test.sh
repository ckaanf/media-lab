#!/bin/bash
chmod +x drop_cache.sh

# 테스트하고 싶은 청크 사이즈 리스트 (64KB, 256KB, 1MB)
CHUNKS=(65536 262144 1048576)

for CHUNK in "${CHUNKS[@]}"
do
    echo "========================================"
    echo "🚀 Starting Test with CHUNK_SIZE: $CHUNK"
    echo "========================================"

    # 1. 캐시 비우기
    ./drop_cache.sh

    # 2. 서버 실행
    echo "Starting Java server..."
    java -jar simple-spring-stream.jar &
    JAVA_PID=$!
    sleep 5

    # 3. 파일명 생성 (예: java_range_20240120_1530_65536.txt)
    TIMESTAMP=$(date +%Y%m%d_%H%M)
    RESULT_FILE="../results/java_range_${TIMESTAMP}_${CHUNK}.txt"

    # 4. k6 실행 (CHUNK_SIZE 환경변수 주입)
    echo "Running k6 test (Saving to: $RESULT_FILE)..."
    CHUNK_SIZE=$CHUNK k6 run ../k6/vod_range.js | tee "$RESULT_FILE"

    # 5. 서버 종료
    echo "Stopping Java server (PID: $JAVA_PID)..."
    kill $JAVA_PID
    sleep 2
done

echo "✅ All tests completed!"