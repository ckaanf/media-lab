plugins {
    java
    id("org.springframework.boot") version "4.0.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "org.example" // 하위 모듈과 맞춤
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}