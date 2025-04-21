package com.example.bluetoothexample.presentation

/**
 * 심박수 센서 상태를 나타내는 상수들을 정의하는 객체.
 * Java 클래스를 Kotlin object로 변환.
 */
object Status {
    // public static final int -> const val 로 변경
    const val STATUS_NONE = 0                 // 상태 없음
    const val STATUS_FIND_HR = 1              // 심박수 찾는 중
    const val STATUS_ATTACHED = -1            // 센서 부착됨 (음수 값의 의미는 SDK 문서 확인 필요)
    const val STATUS_DETECT_MOVE = -2         // 움직임 감지됨
    const val STATUS_DETACHED = -3            // 센서 떨어짐
    const val STATUS_LOW_RELIABILITY = -8     // 신뢰도 낮음
    const val STATUS_VERY_LOW_RELIABILITY = -10 // 신뢰도 매우 낮음
    const val STATUS_NO_DATA_FLUSH = -99      // 플러시 데이터 없음 (?)
}