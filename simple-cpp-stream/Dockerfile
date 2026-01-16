# 1. 빌드 환경 (GCC 최신 버전)
FROM gcc:latest AS builder

WORKDIR /app

# 소스 코드 복사
COPY main.cpp .

# 컴파일 (최적화 옵션 포함)
RUN g++ -O3 -pthread main.cpp -o server

# 2. 실행 환경 (가벼운 Ubuntu/Debian)
FROM ubuntu:latest

WORKDIR /app

# 빌드된 실행 파일만 가져오기
COPY --from=builder /app/server .

# 영상 폴더 생성 (볼륨 마운트 포인트)
RUN mkdir -p videos

# 포트 개방
EXPOSE 8081

# 실행 (Videos 폴더는 외부에서 마운트할 예정)
CMD ["./server"]