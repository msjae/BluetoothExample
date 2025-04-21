package myHealth

import myHealth.HeartRateData
import myHealth.PpgData

/**
 * 센서 데이터 및 오류 변경 사항을 수신하는 옵저버 인터페이스 (Kotlin 버전).
 * TrackerDataNotifier를 통해 데이터 변경 알림을 받습니다.
 */
interface TrackerDataObserver {
    /**
     * 심박수 데이터가 변경되었을 때 호출됩니다.
     * @param hrData 수신된 심박수 데이터 객체.
     */
    fun onHeartRateTrackerDataChanged(hrData: HeartRateData) // 파라미터 타입 Kotlin 클래스로 변경

    /**
     * [추가] PPG 데이터가 변경되었을 때 호출됩니다.
     * @param ppgData 수신된 PPG 데이터 객체.
     */
    fun onPpgDataChanged(ppgData: PpgData) // PPG 메소드 추가

    /**
     * [제거] SpO2 데이터 변경 메소드는 사용하지 않으므로 제거합니다.
     */
    // fun onSpO2TrackerDataChanged(status: Int, spO2Value: Int)

    /**
     * 센서 트래커에서 오류가 발생했을 때 호출됩니다.
     * @param errorResourceId 오류를 설명하는 문자열 리소스 ID.
     * (오류 객체 자체를 전달하는 것이 더 유용할 수 있습니다. 예: fun onError(error: Throwable?))
     */
    fun onError(errorResourceId: Int) // 파라미터 타입 Int로 변경
}