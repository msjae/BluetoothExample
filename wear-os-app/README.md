# Wear OS PPG/HR 센서 데이터 전송 앱

## 1. 개요

이 애플리케이션은 삼성 갤럭시 워치(Wear OS)에서 실행되며, 내장된 PPG 및 심박수 센서 데이터를 측정하여 블루투스 클래식(SPP)을 통해 연결된 PC로 전송하는 역할을 합니다.

## 2. 개발 환경 및 요구사항

* **IDE:** Android Studio (최신 버전 권장)
* **언어:** Kotlin
* **UI:** Jetpack Compose for Wear OS
* **핵심 라이브러리:**
    * Samsung Health SDK (`libs` 폴더 내 파일 직접 추가 방식 사용)
    * Wear Compose Material
    * Kotlin Coroutines
    * Gson (JSON 변환)
    * Android Bluetooth API
* **대상 기기:** Samsung Galaxy Watch (Wear OS 기반, Samsung Health Platform 설치 및 업데이트 필요)
* **필수 권한:**
    * `android.permission.BODY_SENSORS` (신체 센서 접근)
    * `android.permission.BLUETOOTH_CONNECT` (Android 12 이상 블루투스 연결)
    * `android.permission.BLUETOOTH` (Android 11 이하)
    * `android.permission.BLUETOOTH_ADMIN` (Android 11 이하)
    * `android.permission.WAKE_LOCK` (백그라운드 작업 시 필요할 수 있음)

## 3. 설정

### 3.1. Samsung Health SDK 설정

1.  Samsung Developer 사이트 등 공식 경로를 통해 **Samsung Health SDK** (`.jar` 또는 `.aar` 파일)를 다운로드합니다. (Privileged SDK 접근 권한이 없는 경우 기능 제한 가능성 있음)
2.  다운로드한 SDK 파일을 이 프로젝트의 `app/libs` 폴더 안에 복사합니다.
3.  `app/build.gradle` (또는 `build.gradle.kts`) 파일의 `dependencies` 블록 안에 다음 의존성을 추가하고 Gradle Sync를 실행합니다. (파일 확장자에 맞게 수정)
    ```groovy
    // build.gradle (Groovy)
    implementation fileTree(dir: 'libs', include: ['*.jar', '*.aar'])
    ```kotlin
    // build.gradle.kts (Kotlin Script)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    ```

### 3.2. 대상 PC 블루투스 주소 설정

1.  `app/src/main/java/.../MainActivity.kt` 파일을 엽니다. (실제 경로 확인)
2.  파일 상단의 `companion object` 내부에 있는 `TARGET_PC_ADDRESS` 상수의 값을 **데이터를 수신할 PC의 실제 블루투스 MAC 주소**로 수정합니다.
    ```kotlin
    private companion object {
        // ...
        const val TARGET_PC_ADDRESS = "XX:XX:XX:XX:XX:XX" // <<< 이 부분을 실제 PC 주소로 변경!
        // ...
    }
    ```
## 4. 실행 및 사용법

1.  워치에서 설치된 앱을 실행합니다.
2.  앱에서 **[신체 센서]** 및 **[주변 기기]** 권한을 요청하면 **[허용]** 합니다.
3.  (PC에서 Python 서버가 실행 중인지 확인합니다.)
4.  **[PC 연결]** 버튼을 눌러 설정된 주소의 PC와 블루투스 연결을 시도합니다.
5.  연결 성공 후, **[측정 시작]** 버튼을 눌러 센서 데이터 측정을 시작합니다. 데이터는 화면에 표시되고 PC로 전송됩니다.
6.  **[측정 중지]** 버튼으로 측정을 멈출 수 있습니다.
7.  **[연결 끊기]** 버튼으로 PC와의 연결을 해제합니다.

## 5. 주요 코드 구조

* **`MainActivity.kt`:** 메인 화면 UI(Compose), 블루투스 연결 관리, Health SDK 연결 관리, 센서 데이터 옵저버 구현, 버튼 액션 처리.
* **`ConnectionManager.kt`:** Samsung Health Tracking Service 연결/해제, HealthTracker 객체 생성 및 리스너 초기화 담당.
* **`HeartRateListener.kt`, `PpgListener.kt`:** 각 센서 타입에 대한 데이터 수신 콜백 처리 및 `TrackerDataNotifier` 호출.
* **`BaseListener.kt`:** 리스너들의 공통 기능(트래커 설정, 시작/중지 등)을 위한 기본 클래스.
* **`HeartRateData.kt`, `PpgData.kt`:** 수신된 센서 데이터를 담는 데이터 클래스.
* **`Status.kt` (또는 `HeartRateStatus.kt`):** 센서 상태 코드를 정의하는 객체.
* **`ConnectionObserver.kt`, `TrackerDataObserver.kt`:** 콜백 인터페이스 정의.
* **`TrackerDataNotifier.kt`:** 데이터 변경 알림을 위한 Singleton 객체.

