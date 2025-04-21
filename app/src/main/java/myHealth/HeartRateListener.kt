package myHealth

import android.util.Log
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.ValueKey

/**
 * Heart Rate 데이터 수신 및 처리를 위한 리스너 (Kotlin 버전).
 * BaseListener를 상속한다고 가정합니다.
 */
class HeartRateListener : BaseListener() { // BaseListener 상속

    // Log Tag 정의 (Companion object 사용)
    private companion object {
        const val APP_TAG = "HeartRateListenerKt" // 코틀린 파일임을 구분하기 위해 이름 변경 (선택 사항)
    }

    // 초기화 블록: 리스너 생성 및 설정
    init {
        val trackerEventListener = object : HealthTracker.TrackerEventListener {
            // NonNull 어노테이션 대신 Kotlin의 Non-nullable 타입 사용 (List<DataPoint>)
            // SDK에서 list가 null을 반환할 수도 있다면 List<DataPoint>? 로 변경 필요
            override fun onDataReceived(list: List<DataPoint>) {
                // forEach 람다 사용 (Java의 for-each 루프와 동일)
                list.forEach { dataPoint ->
                    readValuesFromDataPoint(dataPoint)
                }
            }

            override fun onFlushCompleted() {
                Log.i(APP_TAG, " onFlushCompleted called")
                // 필요한 로직 추가 가능
            }

            override fun onError(trackerError: HealthTracker.TrackerError?) { // trackerError가 null일 수 있는지 SDK 확인 필요 (? 추가)
                Log.e(APP_TAG, " onError called: $trackerError")
                setHandlerRunning(false) // BaseListener의 함수라고 가정

                // when 표현식 사용 (Java의 if-else if 또는 switch 와 유사)
                when (trackerError) {
                    HealthTracker.TrackerError.PERMISSION_ERROR -> {
                        // TrackerDataNotifier.getInstance().notifyError(R.string.NoPermission) // R 임포트 필요
                        Log.e(APP_TAG,"SDK Permission Error") // 임시 로그
                    }
                    HealthTracker.TrackerError.SDK_POLICY_ERROR -> {
                        // TrackerDataNotifier.getInstance().notifyError(R.string.SdkPolicyError) // R 임포트 필요
                        Log.e(APP_TAG,"SDK Policy Error") // 임시 로그
                    }
                    // 필요한 다른 에러 케이스 추가
                    else -> {
                        Log.w(APP_TAG, "Unknown error: $trackerError")
                    }
                }
            }
        }
        // BaseListener의 setTrackerEventListener 함수 호출 (상속받았다고 가정)
        setTrackerEventListener(trackerEventListener)
    }

    /**
     * DataPoint에서 심박수 관련 값들을 읽어옵니다.
     */
    fun readValuesFromDataPoint(dataPoint: DataPoint) {
        try {
            // ValueKey를 사용하여 각 데이터 추출 (Null 가능성 고려)
            // dataPoint.getValue()가 null을 반환할 수 있으므로 안전 호출 ?. 또는 !! 사용 필요 (SDK 문서 확인)
            // 여기서는 Non-null이라고 가정하고 진행 (실제로는 null 처리 필요할 수 있음)
            val status: Int = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE_STATUS) ?: Status.STATUS_NONE
            val hr: Int = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE) ?: 0

            // IBI 리스트와 상태 리스트 가져오기 (Null 또는 빈 리스트 가능성 처리)
            val hrIbiList: List<Int>? = dataPoint.getValue(ValueKey.HeartRateSet.IBI_LIST)
            val hrIbiStatusList: List<Int>? = dataPoint.getValue(ValueKey.HeartRateSet.IBI_STATUS_LIST)

            // 리스트가 비어있지 않은 경우 마지막 값 사용, 아니면 기본값 사용
            val lastIbi: Int = hrIbiList?.lastOrNull() ?: 0 // 마지막 IBI 값 또는 0
            val lastQIbi: Int = hrIbiStatusList?.lastOrNull() ?: 1 // 마지막 IBI 상태 또는 1

            // 추출한 값으로 HeartRateData 객체 생성 (Kotlin data class 사용)
            val hrData = HeartRateData(
                status = status,
                hr = hr,
                ibi = lastIbi,
                qIbi = lastQIbi
            )

            // 데이터 변경 알림 (TrackerDataNotifier는 Java Singleton으로 가정)
            // TrackerDataNotifier.getInstance().notifyHeartRateTrackerObservers(hrData)
            Log.d(APP_TAG, "Processed HR Data: $hrData") // 생성된 데이터 로그 출력
            TrackerDataNotifier.notifyHeartRateTrackerObservers(hrData)

            // 원본 데이터 포인트 로깅
            // Log.d(APP_TAG, "Original DataPoint: ${dataPoint.toString()}")

        } catch (e: Exception) {
            // getValue 에서 오류 발생 가능성 (예: 키 부재, 타입 불일치 등)
            Log.e(APP_TAG, "Error reading values from DataPoint: ${e.message}", e)
        }
    }
}