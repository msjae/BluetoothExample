package myHealth

import android.util.Log
import com.google.gson.Gson

import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.ValueKey
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Raw PPG 데이터 수신 및 처리를 위한 리스너.
 * BaseListener를 상속한다고 가정합니다.
 */
class PpgListener : BaseListener() {

    private companion object {
        const val APP_TAG = "PpgListenerKt"
    }

    init {
        val trackerEventListener = object : HealthTracker.TrackerEventListener {
            // onDataReceived는 DataPoint 리스트를 받음
            override fun onDataReceived(list: List<DataPoint>) {
                // 리스트의 각 DataPoint를 처리
                list.forEach { dataPoint ->
                    readValuesFromDataPoint(dataPoint)
                }
            }

            override fun onFlushCompleted() {
                Log.i(APP_TAG, " onFlushCompleted called")
            }

            override fun onError(trackerError: HealthTracker.TrackerError?) {
                Log.e(APP_TAG, " onError called: $trackerError")
                setHandlerRunning(false) // BaseListener의 함수라고 가정

                when (trackerError) {
                    HealthTracker.TrackerError.PERMISSION_ERROR -> Log.e(APP_TAG,"SDK Permission Error")
                    HealthTracker.TrackerError.SDK_POLICY_ERROR -> Log.e(APP_TAG,"SDK Policy Error")
                    else -> Log.w(APP_TAG, "Unknown error: $trackerError")
                }
            }
        }
        setTrackerEventListener(trackerEventListener)
    }

    /**
     * DataPoint에서 PPG 관련 값들을 읽어옵니다.
     * 각 DataPoint는 단일 시점의 PPG 측정값 세트를 나타낸다고 가정합니다.
     */
    fun readValuesFromDataPoint(dataPoint: DataPoint) {
        try {
            // 1. 타임스탬프 추출
            val timestampNs: Long = dataPoint.getTimestamp()

            // 2. 각 PPG 채널 값 추출
            // getValue가 null을 반환할 수 있으므로 Nullable 타입(Int?)으로 받고 확인
            val green = dataPoint.getValue(ValueKey.PpgGreenSet.PPG_GREEN) as? Int

            // 3. 상태 값 추출 (여기서는 Green 채널 상태만 사용 - 필요시 수정)
            // 상태값이 항상 제공되는지, Nullable인지 확인 필요
            val greenStatus: Int = dataPoint.getValue(ValueKey.PpgGreenSet.STATUS) ?: Status.STATUS_NONE
            // val irStatus: Int = dataPoint.getValue(ValueKey.PpgSet.IR_STATUS) ?: Status.STATUS_NONE
            Log.d(APP_TAG, "PPG Values: Green: $green, Green Status: $greenStatus")
            // 4. 필수 값들이 null이 아닌지 확인 (타임스탬프는 보통 null이 아님)
            if (green == null) {
                Log.w(APP_TAG, "Received DataPoint with null PPG value(s). Skipping. Timestamp: $timestampNs")
                return // 필수 값 중 하나라도 없으면 이 DataPoint 처리 중단
            }

            // 5. PpgData 객체 생성 (Non-null 값 사용)
            val ppgData = PpgData(
                timestampNs = timestampNs,
                green = green, // Non-null 보장됨
                greenStatus = greenStatus, // status는 Nullable일 수 있음
            )

            // 6. 데이터 처리/전송
            TrackerDataNotifier.notifyPpgTrackerObservers(ppgData)

            Log.d(APP_TAG, "Processed PPG Data: $ppgData") // 생성된 데이터 로그 출력

        } catch (e: Exception) {
            // getValue 에서 오류 발생 가능성 (예: 키 부재, 타입 불일치 등)
            // 또는 timestamp 속성 접근 오류 등
            Log.e(APP_TAG, "Error reading PPG values from DataPoint: ${e.message}", e)
        }
    }
}