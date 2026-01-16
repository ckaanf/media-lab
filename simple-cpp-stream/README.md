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
