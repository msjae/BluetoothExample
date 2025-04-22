package myHealth

import com.samsung.android.service.health.tracking.HealthTrackerException

/**
 * Health Tracking Service 연결 상태 및 오류를 수신하는 인터페이스 (Kotlin 버전).
 * ConnectionManager에서 발생하는 이벤트를 Activity 등에 알리는 데 사용됩니다.
 */
interface ConnectionObserver {
    /**
     * Health Tracking Service 연결 결과를 알립니다.
     * @param stringResourceId 연결 결과 상태를 나타내는 문자열 리소스 ID (예: R.string.ConnectedToHs).
     * (실제 구현에서는 리소스 ID 대신 상태 Enum 이나 Boolean 등을 사용하는 것이 더 명확할 수 있습니다.)
     */
    fun onConnectionResult(stringResourceId: Int) // void -> Unit (생략됨), int -> Int

    /**
     * Health Tracking Service 연결 중 오류가 발생했음을 알립니다.
     * @param e 발생한 오류 객체 (Nullable). HealthTrackerException 또는 다른 Throwable일 수 있음.
     */
    fun onError(e: Throwable?)
}
