# 1. C++ 서버 이미지 빌드 (최초 1회)

```bash
cd v2-cpp-epoll
docker build -t media-server-cpp .
```

# 2. 서버 실행 (영상 폴더 연결)

# $(pwd)/../videos : 상위 폴더에 있는 videos를 컨테이너 내부 /app/videos로 연결

```bash
docker run -d \
-p 8081:8081 \
-v "$(pwd)/../videos":/app/videos \
--name cpp-server \
media-server-cpp

```

```plaintext
simple-cpp-stream/
├── CMakeLists.txt         # [1] 빌드 설정
├── videos/                # 영상 폴더
│   └── test.mp4
└── src/
    ├── main.cpp           # [2] 메인 (프로세스 관리 & 시그널)
    ├── core/
    │   ├── StreamContext.hpp     # [3] 상태 저장용 구조체
    │   ├── MediaController.hpp   # [4] 비즈니스 로직 (헤더)
    │   └── MediaController.cpp   # [5] 비즈니스 로직 (구현)
    ├── http/
    │   ├── HttpRequest.hpp       # [6] HTTP 파싱 (헤더)
    │   └── HttpRequest.cpp       # [7] HTTP 파싱 (구현)
    └── server/
        ├── HttpServer.hpp        # [8] 서버 엔진 (헤더)
        └── HttpServer.cpp        # [9] 서버 엔진 (구현)
```