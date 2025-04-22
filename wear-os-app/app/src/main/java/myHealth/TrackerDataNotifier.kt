package myHealth // 패키지 이름 유지 (실제 프로젝트에 맞게 조정)

// 필요한 데이터 클래스 및 옵저버 인터페이스 임포트 (실제 경로 확인 필요)
import android.util.Log
import myHealth.HeartRateData
import myHealth.PpgData
import myHealth.TrackerDataObserver
import java.util.Collections // 스레드 안전성을 위해 추가 (선택 사항)

/**
 * 센서 데이터 및 오류 알림을 관리하는 Singleton 객체 (Kotlin 버전).
 * 등록된 TrackerDataObserver들에게 데이터 변경 및 오류를 브로드캐스트합니다.
 */
object TrackerDataNotifier { // public class TrackerDataNotifier -> object TrackerDataNotifier (Singleton)

    // 옵저버 목록 (스레드 안전성을 위해 synchronizedList 사용 고려)
    // private val observers = mutableListOf<TrackerDataObserver>() // 기본 MutableList
    private val observers = Collections.synchronizedList(mutableListOf<TrackerDataObserver>()) // 스레드 안전 버전

    // getInstance() 메소드 불필요 -> object 이름으로 바로 접근 (TrackerDataNotifier.addObserver(...))

    /**
     * 데이터 변경 알림을 받을 옵저버를 등록합니다.
     * @param observer 등록할 TrackerDataObserver 객체.
     */
    fun addObserver(observer: TrackerDataObserver) {
        // 이미 등록되어 있는지 확인 (선택 사항)
        if (!observers.contains(observer)) {
            observers.add(observer)
        }
    }

    /**
     * 등록된 옵저버를 제거합니다.
     * @param observer 제거할 TrackerDataObserver 객체.
     */
    fun removeObserver(observer: TrackerDataObserver) {
        observers.remove(observer)
    }

    /**
     * 등록된 모든 옵저버에게 심박수 데이터 변경을 알립니다.
     * @param hrData 전달할 HeartRateData 객체.
     */
    fun notifyHeartRateTrackerObservers(hrData: HeartRateData) {
        // ConcurrentModificationException 방지를 위해 리스트 복사본 사용 권장
        observers.toList().forEach { observer -> // toList() 로 복사본 생성 후 순회
            try {
                observer.onHeartRateTrackerDataChanged(hrData)
            } catch (e: Exception) {
                // 개별 옵저버 오류 처리 (선택 사항)
                Log.e("TrackerDataNotifier", "Error notifying HR observer: ${e.message}", e)
            }
        }
    }

    /**
     * [추가] 등록된 모든 옵저버에게 PPG 데이터 변경을 알립니다.
     * @param ppgData 전달할 PpgData 객체.
     */
    fun notifyPpgTrackerObservers(ppgData: PpgData) {
        // ConcurrentModificationException 방지를 위해 리스트 복사본 사용 권장
        observers.toList().forEach { observer -> // toList() 로 복사본 생성 후 순회
            try {
                observer.onPpgDataChanged(ppgData)
            } catch (e: Exception) {
                Log.e("TrackerDataNotifier", "Error notifying PPG observer: ${e.message}", e)
            }
        }
    }

    /**
     * 등록된 모든 옵저버에게 오류 발생을 알립니다.
     * @param errorResourceId 오류를 나타내는 문자열 리소스 ID.
     */
    fun notifyError(errorResourceId: Int) {
        // ConcurrentModificationException 방지를 위해 리스트 복사본 사용 권장
        observers.toList().forEach { observer -> // toList() 로 복사본 생성 후 순회
            try {
                observer.onError(errorResourceId)
            } catch (e: Exception) {
                Log.e("TrackerDataNotifier", "Error notifying error observer: ${e.message}", e)
            }
        }
    }
}
