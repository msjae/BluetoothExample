# Wear OS 센서 데이터 수신 서버 (Python)

## 1. 개요

이 Python 스크립트는 블루투스 클래식(SPP)을 사용하여 Wear OS 앱(`wear-os-app/` 참조)으로부터 전송되는 실시간 PPG 및 심박수(HR) 데이터를 수신하고 처리하는 서버 역할을 합니다.

## 2. 기능

* Wear OS 앱으로부터의 블루투스 SPP 연결 요청 수락.
* 수신된 데이터를 UTF-8 문자열로 디코딩.
* 수신된 문자열에서 JSON 객체 파싱.
* 파싱된 센서 데이터(타임스탬프, 센서 타입, 값)를 콘솔에 출력.
* 수신된 데이터를 **`sensor_data.csv`** 파일에 행 단위로 저장 (기본 설정).

## 3. 요구사항 및 설치

* **Python 3:** Python 3.x 버전이 설치되어 있어야 합니다.
* **PyBluez 라이브러리:** 블루투스 통신을 위해 필요합니다.
    ```bash
    pip install PyBluez
    ```

* **기타 표준 라이브러리:** `json`, `threading`, `re`, `csv`, `os`, `datetime`, `queue`, `sys`, `time`, `numpy`

## 4. 실행 방법

1.  PC의 **블루투스 기능이 켜져 있고, 다른 기기에서 검색 가능(discoverable) 상태**인지 확인합니다. (필수!)
2.  터미널 또는 명령 프롬프트를 열고 이 스크립트가 있는 폴더(`python-server/`)로 이동합니다.
3.  다음 명령어를 입력하여 서버를 실행합니다.
    ```bash
    python server_csv.py
    ```
4.  콘솔에 "Waiting for connection on RFCOMM channel..." 메시지가 나타나면 서버가 워치 앱의 연결을 기다리고 있는 상태입니다.
5.  (워치 앱에서 [PC 연결] 버튼을 누릅니다.)
6.  워치 앱이 연결되면 "Accepted connection from..." 메시지가 표시되고, 데이터가 수신될 때마다 콘솔에 로그가 출력되고 `sensor_data.csv` 파일에 데이터가 기록됩니다. 실시간 그래프 기능이 활성화된 경우 별도의 창이 나타납니다.
7.  서버를 종료하려면 콘솔 창에서 `Ctrl + C`를 누릅니다.

## 5. CSV 파일 형식

* 서버 스크립트와 동일한 폴더에 `sensor_data.csv` 파일이 생성됩니다.
* 파일이 없으면 새로 생성되고 헤더 행이 먼저 기록됩니다.
* 이후 수신되는 각 센서 데이터는 새 행으로 추가됩니다.
* **열 구성:**
    1.  `Timestamp`: 워치에서 전송된 타임스탬프 값 (단위는 전송된 JSON 데이터 확인 필요 - 보통 나노초 또는 밀리초).
    2.  `SensorType`: JSON 데이터의 "sensor" 키 값 (예: "HeartRateData", "PpgData", 또는 다른 지정된 타입). 없으면 "Unknown".
    3.  `Value`: JSON 데이터의 "value" 키 값 (HR의 경우 심박수) 또는 "green" 키 값 (PPG의 경우 Green 채널 값). 다른 값을 저장하려면 코드 수정 필요.

## 6. 코드 주요 부분 설명

* **`extract_json_objects(text)`:** 수신된 데이터 스트림에서 개별 JSON 객체를 분리합니다.
* **`write_to_csv(data_row)`:** CSV 파일에 스레드 안전하게 데이터를 기록합니다.
* **`handle_client(client_sock, client_info)`:** 각 클라이언트 연결을 처리하는 스레드 함수. 데이터 수신, 디코딩, JSON 파싱, CSV 저장, (옵션) AI 처리 로직 포함.
* **`start_bluetooth_server()`:** 블루투스 RFCOMM 서버 소켓 설정, 서비스 광고, 클라이언트 연결 수락 및 `handle_client` 스레드 시작 로직 포함.
* **`if __name__ == "__main__":`:** 메인 실행 블록. 블루투스 서버 스레드 시작.

