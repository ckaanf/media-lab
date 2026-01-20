cmake_minimum_required(VERSION 3.10)
project(SimpleCppStream)

set(CMAKE_CXX_STANDARD 17)

# 소스 파일 등록
set(SOURCES
        src/main.cpp
        src/http/HttpRequest.cpp
        src/core/MediaController.cpp
        src/server/HttpServer.cpp
)

# 실행 파일 생성
add_executable(server ${SOURCES})

# 헤더 파일 경로 설정 (src 폴더를 기준으로 include 할 수 있게 함)
target_include_directories(server PRIVATE src)