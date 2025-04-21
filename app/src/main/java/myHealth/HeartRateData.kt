package myHealth


/**
 * 심박수(HR), 심박간격(IBI), IBI 품질 정보를 담는 데이터 클래스.
 */
data class HeartRateData(
    // 기본값을 가진 프로퍼티 (val: 읽기 전용)
    val status: Int = Status.STATUS_NONE,
    val hr: Int = 0,
    val ibi: Int = 0, // R-R interval 또는 beat-to-beat interval
    val qIbi: Int = 1 // IBI 품질 (기본값 1?)
) {
    // Java의 static final 상수들을 Kotlin의 companion object 안에 const val로 정의
    companion object {
        const val IBI_QUALITY_SHIFT = 15
        const val IBI_MASK = 0x1          // 이 값은 getHrIbi() 에서 사용되지 않는 것 같아 보입니다. 원본 코드 확인 필요.
        const val IBI_QUALITY_MASK = 0x7FFF // 이 값도 getHrIbi() 에서 사용되지 않는 것 같아 보입니다. 원본 코드 확인 필요.
    }

    /**
     * IBI 품질과 IBI 값을 결합하여 반환합니다.
     * (qIbi를 15비트 왼쪽으로 시프트하고 ibi 값과 OR 연산)
     * @return 결합된 IBI 값 (품질 포함)
     */
    fun getHrIbi(): Int {
        // 비트 연산자는 Kotlin에서도 동일하게 사용 가능
        return (qIbi shl IBI_QUALITY_SHIFT) or ibi // << 를 shl (shift left), | 를 or 로 변경 (가독성)
    }
}
