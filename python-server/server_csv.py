import bluetooth
import json
import threading
import re
import csv # CSV 모듈 임포트
import os # 파일 존재 확인을 위한 os 모듈 임포트
from datetime import datetime # 선택 사항: 고유 파일 이름 생성용

# --- CSV 설정 ---
CSV_FILENAME_BASE = "sensor_data" # CSV 파일 기본 이름
# 선택 사항: 실행 시마다 타임스탬프로 고유 파일 이름 생성
# timestamp_str = datetime.now().strftime("%Y%m%d_%H%M%S")
# CSV_FILENAME = f"{CSV_FILENAME_BASE}_{timestamp_str}.csv"
CSV_FILENAME = f"{CSV_FILENAME_BASE}.csv" # 고정 파일 이름 사용

CSV_HEADER = ["Timestamp", "SensorType", "Value"] # CSV 헤더
csv_lock = threading.Lock() # CSV 파일 쓰기 스레드 동기화를 위한 Lock
# --- CSV 설정 끝 ---


# Optional: AI 모델 로딩
# ai_model = load_my_ai_model()

def extract_json_objects(text):
    """
    여러 JSON 객체가 붙어 있을 경우 각각 분리하는 함수 (개선)
    정확한 JSON 객체 경계를 찾으려고 시도합니다.
    """
    objects = []
    brace_level = 0
    current_object = ""
    in_string = False
    escape = False

    for char in text:
        current_object += char
        if char == '"' and not escape:
            in_string = not in_string
        elif char == '\\' and in_string:
            escape = True
            continue
        elif not in_string:
            if char == '{':
                if brace_level == 0:
                    current_object = "{" # 새 객체 시작 시 초기화
                brace_level += 1
            elif char == '}':
                if brace_level > 0: # 유효한 괄호 쌍인지 확인
                    brace_level -= 1
                    if brace_level == 0:
                        # 유효한 JSON인지 간단히 확인
                        if current_object.strip().startswith('{') and current_object.strip().endswith('}'):
                             objects.append(current_object.strip())
                        current_object = "" # 다음 객체를 위해 초기화

        escape = False

    # 완료되지 않은 부분은 남은 버퍼로 반환
    remaining_buffer = current_object if brace_level > 0 and current_object.strip().startswith('{') else ""
    return objects, remaining_buffer

def write_to_csv(data_row):
    """스레드 안전하게 CSV 파일에 데이터 행을 추가합니다."""
    with csv_lock: # 파일 접근 전 Lock 획득
        file_exists = os.path.isfile(CSV_FILENAME)
        try:
            # 추가 모드('a')로 파일 열기. 없으면 생성.
            # newline='' 은 Windows에서 추가 빈 줄 방지.
            with open(CSV_FILENAME, mode='a', newline='', encoding='utf-8') as csvfile:
                writer = csv.writer(csvfile)
                # 파일이 새로 생성되었거나 비어있을 때만 헤더 작성
                if not file_exists or os.path.getsize(CSV_FILENAME) == 0:
                    writer.writerow(CSV_HEADER)
                    print(f"[*] Created CSV file '{CSV_FILENAME}' and wrote header.")
                writer.writerow(data_row)
                # print(f"[*] Saved data to CSV: {data_row}") # 선택 사항: CSV 쓰기 로깅
        except IOError as e:
            print(f"[!] Error writing to CSV file '{CSV_FILENAME}': {e}")
        except Exception as e:
            print(f"[!] Unexpected error during CSV write: {e}")


def handle_client(client_sock, client_info):
    print(f"[+] Accepted connection from {client_info}") # 이모티콘 제거
    buffer = ""  # 누적 버퍼
    try:
        while True:
            try:
                data_bytes = client_sock.recv(1024)
                if not data_bytes:
                    print(f"[*] Client {client_info} disconnected.") # 이모티콘 제거
                    break
            except bluetooth.BluetoothError as bt_err:
                print(f"[!] Bluetooth Error during recv from {client_info}: {bt_err}")
                break

            try:
                data_str = data_bytes.decode('utf-8')
                buffer += data_str
                # print(f"[<] Received raw from {client_info}, buffer size: {len(buffer)}") # 로그 너무 많을 수 있음

                # 버퍼에서 완전한 JSON 객체들 추출
                json_chunks, remaining_buffer = extract_json_objects(buffer)
                buffer = remaining_buffer # 다음 처리를 위해 남은 부분 저장

                for chunk in json_chunks:
                    try:
                        sensor_data = json.loads(chunk)
                        # print(f"[+] Parsed data from {client_info}: {sensor_data}") # 로그 너무 많을 수 있음

                        # 필요한 데이터 추출
                        sensor_type = sensor_data.get("sensor", "Unknown") # 없으면 기본값
                        # 값과 타임스탬프 키 처리
                        value = sensor_data.get("value")
                        if value is None: value = sensor_data.get("green") # 'value' 없으면 'green' 시도

                        timestamp = sensor_data.get("timestamp")
                        if timestamp is None: timestamp = sensor_data.get("timestampNs") # 'timestamp' 없으면 'timestampNs' 시도

                        if value is not None and timestamp is not None:
                            print(f"[>] Data from {client_info}: Type={sensor_type}, Value={value}, Timestamp={timestamp}") # 이모티콘 제거

                            # --- CSV 저장 ---
                            data_row = [timestamp, sensor_type, value]
                            write_to_csv(data_row)
                            # -------------------

                            # Optional: AI 처리
                            # print(f"[>] Feeding to AI (placeholder): value={value}, timestamp={timestamp}")
                            # process_and_feed_to_ai(sensor_type, value, timestamp, ai_model)
                        else:
                            print(f"[!] Incomplete data from {client_info}: {sensor_data} (Missing value or timestamp)") # 이모티콘 제거

                    except json.JSONDecodeError as e:
                        print(f"[!] JSON Decode Error (chunk) from {client_info}: {e} - Chunk: '{chunk[:100]}...'") # 이모티콘 제거
                        # 잘못된 청크 처리 (여기서는 그냥 넘어감)
                    except Exception as e:
                        print(f"[!] Error processing chunk from {client_info}: {e} - Chunk: {chunk[:100]}...") # 이모티콘 제거

            except UnicodeDecodeError as e:
                print(f"[!] Unicode Decode Error from {client_info}: {e} - Raw bytes: {data_bytes[:50]}...") # 이모티콘 제거
                buffer = "" # 디코드 오류 시 버퍼 비우기 (선택)

    except bluetooth.BluetoothError as e:
        print(f"[!] Bluetooth Error with {client_info}: {e}") # 이모티콘 제거
    except Exception as e:
        print(f"[!] Unexpected Error with {client_info}: {e}") # 이모티콘 제거
    finally:
        print(f"[-] Closing connection with {client_info}") # 이모티콘 제거
        client_sock.close()

def start_server():
    server_sock = None # 소켓 초기화
    try:
        server_sock = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
        server_sock.bind(("", bluetooth.PORT_ANY))
        server_sock.listen(1)

        port = server_sock.getsockname()[1]
        uuid = "00001101-0000-1000-8000-00805F9B34FB"

        print("[*] Setting up Bluetooth service...") # 이모티콘 제거
        bluetooth.advertise_service(
            server_sock,
            "BioDataSPPServer",
            service_id=uuid,
            service_classes=[uuid, bluetooth.SERIAL_PORT_CLASS],
            profiles=[bluetooth.SERIAL_PORT_PROFILE]
        )

        print(f"[*] Waiting for connection on RFCOMM channel {port}...") # 이모티콘 제거
        print(f"[*] Data will be saved to: {CSV_FILENAME}") # 이모티콘 제거

        while True:
            try:
                client_sock, client_info = server_sock.accept()
                client_thread = threading.Thread(target=handle_client, args=(client_sock, client_info), daemon=True)
                client_thread.start()
            except bluetooth.BluetoothError as e:
                print(f"[!] Error accepting connection: {e}")
                break # Accept 오류 시 서버 종료
            except Exception as e:
                 print(f"[!] Unexpected error during accept: {e}")
                 time.sleep(1)

    except OSError as e:
        print(f"[!] OS Error (Maybe Bluetooth is off or permissions issue?): {e}") # 이모티콘 제거
    except bluetooth.BluetoothError as e:
        print(f"[!] Bluetooth Setup Error: {e}") # 이모티콘 제거
    except KeyboardInterrupt:
        print("\n[!] Server shutting down by user request (Ctrl+C).") # 이모티콘 제거
    except Exception as e:
        print(f"[!] Unexpected Server error: {e}") # 이모티콘 제거
    finally:
        print("[-] Stopping advertise service and closing server socket.") # 이모티콘 제거
        if server_sock:
            try:
                bluetooth.stop_advertise(server_sock)
            except Exception as e:
                print(f"[!] Error stopping advertise service: {e}") # 이모티콘 제거
            try:
                server_sock.close()
            except Exception as e:
                print(f"[!] Error closing server socket: {e}") # 이모티콘 제거
        print("[*] Server shutdown complete.") # 이모티콘 제거


if __name__ == "__main__":
    start_server()

