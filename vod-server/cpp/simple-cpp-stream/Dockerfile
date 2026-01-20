# 1. 빌드 단계 (Builder Stage)
FROM ubuntu:22.04 AS builder

# 필수 패키지 설치 (C++, CMake, Make)
RUN apt-get update && apt-get install -y \
    build-essential \
    cmake \
    g++ \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# [중요] 소스 코드와 CMake 설정 파일 복사
# 이제 파일이 여러 개이므로 폴더째로 복사합니다.
COPY CMakeLists.txt .
COPY src ./src

# CMake로 빌드 시스템 생성 및 컴파일
RUN cmake . && make

# ---------------------------------------------------

# 2. 실행 단계 (Runtime Stage) - 가볍게 유지
FROM ubuntu:22.04

WORKDIR /app

# 빌드 단계에서 만든 실행 파일('server')만 가져오기
COPY --from=builder /app/server .

# 영상 저장소 폴더 생성
RUN mkdir -p videos

# 포트 노출
EXPOSE 8080

# 실행
CMD ["./server"]