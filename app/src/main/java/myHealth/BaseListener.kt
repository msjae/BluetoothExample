package myHealth

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.samsung.android.service.health.tracking.HealthTracker

/**
 * HealthTracker 이벤트 리스너의 기본 클래스 (Kotlin 버전).
 * HealthTracker 설정, 핸들러 관리, 트래커 시작/중지 기능을 제공합니다.
 * open 키워드를 사용하여 다른 리스너 클래스가 상속할 수 있도록 합니다.
 */
open class BaseListener {

    // Log Tag (Companion object 사용)
    private companion object {
        const val APP_TAG = "BaseListenerKt"
    }

    // Nullable 타입으로 선언하고 null로 초기화 (Setter를 통해 설정됨)
    private var handler: Handler? = null
    private var healthTracker: HealthTracker? = null
    private var isHandlerRunning: Boolean = false

    // protected 가시성으로 변경하여 하위 클래스에서 접근 가능하도록 함 (setTrackerEventListener 호출 주체 고려)
    protected var trackerEventListener: HealthTracker.TrackerEventListener? = null
        private set // 외부에서는 읽기만 가능하게 하고, 설정은 아래 setTrackerEventListener 함수 통해서만 가능

    // Setter 함수들 (Kotlin 스타일)
    fun setHealthTracker(tracker: HealthTracker?) {
        Log.i(APP_TAG, "setHealthTracker called with $tracker")
        this.healthTracker = tracker
    }

    fun setHandler(handler: Handler?) {
        this.handler = handler
    }

    // isHandlerRunning은 내부 상태 관리에 가까우므로 protected 또는 private으로 변경 고려 가능
    protected fun setHandlerRunning(handlerRunning: Boolean) {
        this.isHandlerRunning = handlerRunning
    }

    // trackerEventListener 설정 함수
    // 하위 클래스의 init 블록에서 호출되므로 protected 가 적절해 보임
    protected fun setTrackerEventListener(listener: HealthTracker.TrackerEventListener?) {
        this.trackerEventListener = listener
    }

    /**
     * 지정된 핸들러 스레드에서 HealthTracker의 이벤트 리스너를 설정하여 트래킹을 시작합니다.
     */
    fun startTracker() {
        Log.i(APP_TAG, "startTracker called")
        if (handler == null) {
            Log.w("BaseListenerKt", "Handler was null on startTracker, initializing now with MainLooper.")
            handler = Handler(Looper.getMainLooper())
        }
        // Nullable 프로퍼티 접근 시 안전 호출(?.) 사용
        Log.d(APP_TAG, "healthTracker: ${healthTracker?.toString()}")
        Log.d(APP_TAG, "trackerEventListener: ${trackerEventListener?.toString()}")

        // 핸들러가 null이 아니고, 핸들러가 아직 실행 중이지 않을 때
        if (!isHandlerRunning && handler != null) {
            // 핸들러 post (Kotlin 람다 사용) 및 안전 호출(?.)
            handler?.post {
                // healthTracker 와 trackerEventListener 가 null 이 아닐 때만 실행
                if (healthTracker != null && trackerEventListener != null) {
                    Log.d(APP_TAG, "Setting event listener on handler thread.")
                    // 실제 리스너 설정
                    healthTracker?.setEventListener(trackerEventListener) // 안전 호출(?.)
                    setHandlerRunning(true)
                } else if (healthTracker == null) {
                    Log.w(APP_TAG, "Cannot start tracker: healthTracker is null.")
                } else if (trackerEventListener == null) {
                    Log.w(APP_TAG, "Cannot start tracker: trackerEventListener is null.")
                } else {
                    Log.w(APP_TAG, "Cannot start tracker: healthTracker or trackerEventListener is null.")
                }
            }
        } else if (isHandlerRunning) {
            Log.w(APP_TAG, "Tracker already running.")
        } else {
            Log.e(APP_TAG, "Cannot start tracker: handler is null.")
        }
    }

    /**
     * HealthTracker의 이벤트 리스너를 해제하여 트래킹을 중지합니다.
     */
    fun stopTracker() {
        Log.i(APP_TAG, "stopTracker called")
        // Nullable 프로퍼티 접근 시 안전 호출(?.) 사용
        Log.d(APP_TAG, "healthTracker: ${healthTracker?.toString()}")
        Log.d(APP_TAG, "trackerEventListener: ${trackerEventListener?.toString()}")

        // 핸들러가 실행 중일 때만 중지 로직 수행
        if (isHandlerRunning) {
            // healthTracker가 null이 아닐 때 unsetEventListener 호출
            healthTracker?.unsetEventListener() // 안전 호출(?.)
            Log.i(APP_TAG, "Event listener unset.")
            setHandlerRunning(false)

            // 핸들러의 콜백 메시지 제거 (안전 호출 ?.)
            handler?.removeCallbacksAndMessages(null)
            Log.d(APP_TAG, "Handler callbacks removed.")
        } else {
            Log.w(APP_TAG, "Tracker not running or already stopped.")
        }
    }
}