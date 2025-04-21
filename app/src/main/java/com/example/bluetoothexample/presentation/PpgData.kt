package com.example.bluetoothexample.presentation

/**
 * Raw PPG 센서 데이터를 담는 데이터 클래스.
 *
 * @property timestampNs 센서 이벤트 타임스탬프 (나노초). 각 측정값에 대한 타임스탬프 필요.
 * @property green 녹색광 PPG 센서 값.
 * @property ir 적외선(IR) PPG 센서 값.
 * @property red 적색광(Red) PPG 센서 값.
 * @property greenstatus 녹색광 PPG 센서 상태 또는 데이터 품질 정보.
 * @property redstatus 적색광 PPG 센서 상태 또는 데이터 품질 정보.
 */
data class PpgData(
    val timestampNs: Long,
    val green: Int = 0,     // 실제 데이터 타입이 Float 등일 수 있으므로 확인 필요
    val ir: Int = 0,        // 실제 데이터 타입이 Float 등일 수 있으므로 확인 필요
    val red: Int = 0,       // 실제 데이터 타입이 Float 등일 수 있으므로 확인 필요
    val greenStatus: Int = Status.STATUS_NONE,
    val redStatus: Int = Status.STATUS_NONE,
)