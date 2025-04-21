package myHealth

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.NonNull // Kotlin에서는 주로 Nullability로 처리
import com.example.bluetoothexample.R
import com.samsung.android.service.health.tracking.ConnectionListener // 오타 수정: Listener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.data.PpgType
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.HealthTrackerType

/**
 * Samsung Health Tracking Service 연결 및 트래커 초기화를 관리하는 클래스 (Kotlin 버전).
 *
 * @property context 애플리케이션 컨텍스트.
 * @property connectionObserver 연결 상태 및 오류를 보고받을 옵저버.
 */
class ConnectionManager(
    private val context: Context, // Context 주입
    private val connectionObserver: ConnectionObserver // 생성자에서 옵저버 주입
) {

    // Log Tag
    private companion object {
        const val TAG = "ConnectionManagerKt"
    }

    // HealthTrackingService 인스턴스 (Nullable)
    private var healthTrackingService: HealthTrackingService? = null

    // SDK 연결 상태 리스너 (object expression 사용)
    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            Log.i(TAG, "Health Tracking Service connected successfully.")
            // 연결 성공 알림
            // R.string.ConnectedToHs 는 실제 리소스 ID로 가정
            connectionObserver.onConnectionResult(R.string.ConnectedToHs)

            // 연결 성공 후 각 트래커 지원 여부 확인 (healthTrackingService가 null이 아님을 보장)
            // isHeartRateAvailable, isPpgAvailable 등 호출
            if (!isHeartRateAvailable(healthTrackingService)) {
                Log.w(TAG, "Device does not support Heart Rate tracking.")
                // 지원하지 않음 알림 (필요 시)
                // connectionObserver.onConnectionResult(R.string.NoHrSupport)
            }
            if (!isPpgAvailable(healthTrackingService)) { // PPG 지원 여부 확인 추가
                Log.w(TAG, "Device does not support PPG tracking.")
                // 지원하지 않음 알림 (필요 시)
                // connectionObserver.onConnectionResult(R.string.NoPpgSupport)
            }
            // SpO2 관련 로직 제거됨
        }

        override fun onConnectionEnded() {
            // 일반적으로 disconnect() 호출 시 발생
            Log.i(TAG, "Health Tracking Service connection ended.")
            // 필요 시 연결 종료 상태 알림
        }

        override fun onConnectionFailed(e: HealthTrackerException?) { // Exception이 Nullable일 수 있음
            Log.e(TAG, "Health Tracking Service connection failed.", e)
            // 연결 실패 시 옵저버에게 오류 전달 (null 체크 추가)
            if (e != null) {
                connectionObserver.onError(e)
            } else {
                // 예외 없이 실패한 경우 처리 (예: 기본 오류 전달)
                // connectionObserver.onError(HealthTrackerException("Unknown connection failure", HealthTrackerException.UNKNOWN))
                Log.e(TAG,"Connection failed without specific exception.")
            }
        }
    }

    /**
     * Health Tracking Service에 연결을 시도합니다.
     */
    fun connect() {
        if (healthTrackingService == null) {
            Log.i(TAG, "Connecting to Health Tracking Service...")
            try {
                healthTrackingService = HealthTrackingService(connectionListener, context)
                healthTrackingService?.connectService() // 안전 호출(?.) 사용
            } catch (t: Throwable) { // 모든 종류의 오류(Throwable)를 잡음
                Log.e(TAG, "Failed to create or connect ConnectionManager: ${t.message}", t)
                // 잡힌 오류(t)를 그대로 onError 콜백으로 전달
                connectionObserver.onError(t) // <<< 수정된 부분
            }
        } else {
            Log.w(TAG, "Health Tracking Service already initialized or connecting.")
            // 이미 연결되어 있거나 시도 중인 경우의 로직 (예: 상태 반환)
        }
    }

    /**
     * Health Tracking Service와의 연결을 해제합니다.
     */
    fun disconnect() {
        if (healthTrackingService != null) {
            Log.i(TAG, "Disconnecting from Health Tracking Service...")
            try {
                healthTrackingService?.disconnectService() // 안전 호출(?.) 사용
            } catch (e: Exception) {
                Log.e(TAG, "Error during disconnectService", e)
            } finally {
                healthTrackingService = null // 참조 해제
            }
        }
    }

    // SpO2 초기화 함수 제거됨
    // fun initSpO2(spO2Listener: SpO2Listener): Boolean { ... }

    /**
     * Heart Rate 트래커를 초기화하고 리스너에 HealthTracker 인스턴스를 설정합니다.
     * @param heartRateListener HealthTracker를 설정할 리스너 객체.
     * @return 초기화 성공 여부.
     */
    fun initHeartRate(heartRateListener: HeartRateListener) {
        Log.d(TAG, "Initializing Heart Rate tracker...")
        // HEART_RATE_CONTINUOUS 타입의 트래커 가져오기
        val heartRateTracker: HealthTracker? = healthTrackingService?.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
        heartRateListener.setHealthTracker(heartRateTracker) // 리스너에 트래커 설정
        setHandlerForBaseListener(heartRateListener) // 핸들러 설정
    }

    /**
     * PPG 트래커를 초기화하고 리스너에 HealthTracker 인스턴스를 설정합니다.
     * @param ppgListener HealthTracker를 설정할 리스너 객체.
     */
    fun initPpg(ppgListener: PpgListener) {
        Log.d(TAG, "Initializing PPG tracker...")
        val ppgGreenTrackerType = HealthTrackerType.PPG_GREEN
        val ppgRedTrackerType = HealthTrackerType.PPG_RED
        val ppgIrTrackerType = HealthTrackerType.PPG_IR

        // getHealthTracker 호출
        val ppgGreenTracker: HealthTracker? = healthTrackingService?.getHealthTracker(ppgGreenTrackerType)
        val ppgRedTracker: HealthTracker? = healthTrackingService?.getHealthTracker(ppgRedTrackerType)
        val ppgIrTracker: HealthTracker? = healthTrackingService?.getHealthTracker(ppgIrTrackerType)

        ppgListener.setHealthTracker(ppgGreenTracker)
        setHandlerForBaseListener(ppgListener)
    }


    /**
     * BaseListener를 상속받은 리스너에 메인 Looper 핸들러를 설정합니다.
     * (리스너 내부에서 UI 스레드 작업 등이 필요할 경우 사용될 수 있음)
     */
    private fun setHandlerForBaseListener(baseListener: BaseListener) {
        // 필요 시 핸들러 전달 (BaseListener 구현에 따라 달라짐)
        baseListener.setHandler(Handler(Looper.getMainLooper()))
        Log.d(TAG, "Handler set for listener: ${baseListener::class.simpleName}")
    }

    // SpO2 지원 여부 확인 함수 제거됨
    // private fun isSpO2Available(...)

    /**
     * 현재 연결된 HealthTrackingService에서 Heart Rate (Continuous) 트래킹을 지원하는지 확인합니다.
     * @param healthTrackingService HealthTrackingService 인스턴스 (Nullable).
     * @return 지원하면 true, 아니면 false.
     */
    private fun isHeartRateAvailable(healthTrackingService: HealthTrackingService?): Boolean {
        return try {
            val availableTrackers = healthTrackingService?.getTrackingCapability()?.supportHealthTrackerTypes
            availableTrackers?.contains(HealthTrackerType.HEART_RATE_CONTINUOUS) == true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking HR capability", e)
            false
        }
    }

    /**
     * 현재 연결된 HealthTrackingService에서 PPG 트래킹을 지원하는지 확인합니다.
     * @param healthTrackingService HealthTrackingService 인스턴스 (Nullable).
     * @return 지원하면 true, 아니면 false.
     */
    private fun isPpgAvailable(healthTrackingService: HealthTrackingService?): Boolean {
        // ★★★ 중요: PPG 트래커 타입 확인 필요! ★★★
        val ppgTrackerType = HealthTrackerType.PPG_GREEN // initPpg 에서 사용한 타입과 동일하게 확인

        return try {
            val availableTrackers = healthTrackingService?.getTrackingCapability()?.supportHealthTrackerTypes
            availableTrackers?.contains(ppgTrackerType) == true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking PPG capability for type $ppgTrackerType", e)
            false
        }
    }
}

// --- 필요한 인터페이스 및 클래스 정의 (별도 파일 또는 이 파일 하단) ---

// interface ConnectionObserver { // 실제 메소드 시그니처 확인 필요
//     fun onConnectionResult(stringResourceId: Int)
//     fun onError(e: HealthTrackerException)
// }

// open class BaseListener { // 이전 정의된 내용
//     fun setHealthTracker(tracker: HealthTracker?) { /* ... */ }
//     fun setHandler(handler: Handler?) { /* ... */ }
//     // ... startTracker(), stopTracker() 등
// }
// class HeartRateListener : BaseListener() { /* ... */ }
// class PpgListener : BaseListener() { /* ... */ }

// 예시 R 클래스 (실제 프로젝트의 R 클래스 사용)
// object R { object string { const val ConnectedToHs = 1; /*...*/ }}