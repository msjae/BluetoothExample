package com.example.bluetoothexample.presentation // 실제 패키지 이름으로 변경하세요

import android.Manifest // Manifest 임포트 추가
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // Compose Color 사용
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.*
import com.example.bluetoothexample.R
import com.google.gson.Gson // JSON 변환 예시용 (build.gradle에 추가 필요: implementation 'com.google.code.gson:gson:2.10.1')
import com.samsung.android.service.health.tracking.HealthTrackerException // 예외 처리용

import myHealth.ConnectionManager
import myHealth.ConnectionObserver
import myHealth.Status
import myHealth.PpgData
import myHealth.PpgListener
import myHealth.TrackerDataNotifier // 가정
import myHealth.TrackerDataObserver
import myHealth.HeartRateData
import myHealth.HeartRateListener
// import com.samsung.health.multisensortracking.R // R 클래스 임포트 (실제 프로젝트의 R 사용)


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.lang.IllegalArgumentException // IllegalArgumentException 임포트

/**
 * Wear OS 액티비티:
 * 1. Samsung Health Tracking Service에 연결 (ConnectionManager 사용 가정).
 * 2. HR 및 PPG 센서 리스너 등록 및 데이터 수신 (HeartRateListener, PpgListener 사용 가정).
 * 3. 지정된 PC와 블루투스(SPP) 연결 관리.
 * 4. 수신된 센서 데이터를 UI에 표시.
 * 5. 수신된 센서 데이터를 JSON으로 변환하여 블루투스로 PC에 전송.
 */
class MainActivity : ComponentActivity() {

    // --- 상수 정의 ---
    private companion object {
        const val TAG = "WearSensorBt"
        val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val TARGET_PC_ADDRESS = "8C:88:4B:26:8A:36"
        val timestampFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    }

    // --- Bluetooth 관련 변수 ---
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var bluetoothConnectionJob: Job? = null

    // --- Health SDK 관련 변수 ---
    private var connectionManager: ConnectionManager? = null
    private var heartRateListener: HeartRateListener? = null
    private var ppgListener: PpgListener? = null
    private var healthServiceConnected by mutableStateOf(false)

    // --- UI 상태 관리 변수 ---
    private var btConnectionStatus by mutableStateOf("대기 중")
    private var isConnectingBluetooth by mutableStateOf(false)
    private var latestHrData by mutableStateOf<HeartRateData?>(null)
    private var latestPpgData by mutableStateOf<PpgData?>(null)
    private var allPermissionsGranted by mutableStateOf(false)
    // --- New State Variable ---
    private var isTrackingSensors by mutableStateOf(false) // 센서 트래킹 활성 상태

    private val gson = Gson()

    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = getRequiredPermissions().all { permissions.getOrDefault(it, false) }
            if (allGranted) {
                Log.d(TAG, "All required permissions granted.")
                allPermissionsGranted = true
                initializeAfterPermissions()
            } else {
                Log.e(TAG, "Required permissions were denied.")
                allPermissionsGranted = false
                updateStatus("권한 필요")
                Toast.makeText(this, "앱 실행에 필요한 권한이 거부되었습니다.", Toast.LENGTH_LONG).show()
                // finish()
            }
        }

    private val requestEnableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Log.d(TAG, "Bluetooth enabled by user.")
                updateStatus("대기 중 (블루투스 켜짐)")
            } else {
                Log.e(TAG, "Bluetooth was not enabled by user.")
                updateStatus("블루투스 비활성화됨")
                Toast.makeText(this, "블루투스 활성화가 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }

    private val healthConnectionObserver = object : ConnectionObserver {
        override fun onConnectionResult(stringResourceId: Int) {
            val isConnected = (stringResourceId == R.string.ConnectedToHs) // 실제 리소스 ID 사용
            healthServiceConnected = isConnected
            if (isConnected) {
                Log.i(TAG, "Successfully connected to Health Tracking Service.")
                runOnUiThread { Toast.makeText(applicationContext, "헬스 서비스 연결 성공", Toast.LENGTH_SHORT).show() }
                // 리스너 초기화 (이제 여기서 트래킹 시작 안함)
                initializeListenersAndTrackers()
                TrackerDataNotifier.addObserver(sensorDataObserver)
                updateStatus("헬스 서비스 연결됨") // 상태 업데이트
            } else {
                Log.w(TAG, "Failed to connect to Health Tracking Service.")
                runOnUiThread { Toast.makeText(applicationContext, "헬스 서비스 연결 실패", Toast.LENGTH_SHORT).show() }
                updateStatus("헬스 서비스 연결 실패")
                // 연결 실패 시 센서 트래킹 상태도 비활성화
                isTrackingSensors = false
            }
        }

        override fun onError(e: Throwable?) {
            runOnUiThread {
                Log.e(TAG, "Health Tracking Service connection error: ${e?.message}", e)
                healthServiceConnected = false
                isTrackingSensors = false // 오류 발생 시 센서 트래킹 중지 상태로
                updateStatus("헬스 서비스 오류")

                if (e is HealthTrackerException) {
                    if (e.hasResolution()) {
                        e.resolve(this@MainActivity)
                    } else {
                        val errorMsg = when (e.errorCode) { /* ... */ else -> "헬스 서비스 오류 발생" }
                        Toast.makeText(applicationContext, errorMsg, Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(applicationContext, "초기화 중 오류 발생: ${e?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val sensorDataObserver = object : TrackerDataObserver {
        override fun onHeartRateTrackerDataChanged(hrData: HeartRateData) {
            // 트래킹 중일 때만 데이터 처리
            if (!isTrackingSensors) return
            latestHrData = hrData
            Log.d(TAG, "HR data received: $hrData")
            val jsonData = convertToJson(hrData)
            sendDataViaBluetooth(jsonData)
        }

        override fun onPpgDataChanged(ppgData: PpgData) {
            // 트래킹 중일 때만 데이터 처리
            if (!isTrackingSensors) return
            latestPpgData = ppgData
            Log.d(TAG, "PPG data received: $ppgData")
            val jsonData = convertToJson(ppgData)
            sendDataViaBluetooth(jsonData)
        }

        override fun onError(errorResourceId: Int) {
            Log.e(TAG, "Sensor error occurred. Resource ID: $errorResourceId")
            runOnUiThread {
                Toast.makeText(applicationContext, "센서 오류 발생 (코드: $errorResourceId)", Toast.LENGTH_LONG).show()
            }
            // 센서 오류 발생 시 트래킹 중지 및 상태 업데이트
            stopSensorTracking() // 내부적으로 isTrackingSensors = false 설정됨
            updateStatus("센서 오류 발생")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "블루투스를 지원하지 않는 기기입니다.", Toast.LENGTH_LONG).show()
            finish(); return
        }

        setContent {
            WearApp(
                btStatus = btConnectionStatus,
                isConnecting = isConnectingBluetooth,
                isConnected = bluetoothSocket?.isConnected == true,
                hrData = latestHrData,
                ppgData = latestPpgData,
                onConnectClick = { connectToTargetPC() },
                onDisconnectClick = { disconnectBluetooth() },
                // --- Pass new state and callbacks ---
                isTracking = isTrackingSensors,
                onStartTrackingClick = { startSensorTracking() },
                onStopTrackingClick = { stopSensorTracking() },
                healthServiceConnected = healthServiceConnected // 헬스 서비스 연결 상태 전달
            )
        }

        checkAndRequestPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called. Cleaning up resources.")
        // 리스너 중지 (앱 종료 시 확실히 중지)
        stopSensorTracking() // 상태 업데이트 및 리스너 stopTracker() 호출
        TrackerDataNotifier.removeObserver(sensorDataObserver)
        connectionManager?.disconnect()
        disconnectBluetooth()
    }

    private fun updateStatus(status: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            btConnectionStatus = status
        }
    }

    // --- Jetpack Compose UI 정의 (수정됨) ---
    @Composable
    fun WearApp(
        btStatus: String,
        isConnecting: Boolean,
        isConnected: Boolean,
        hrData: HeartRateData?,
        ppgData: PpgData?,
        onConnectClick: () -> Unit,
        onDisconnectClick: () -> Unit,
        // --- New parameters ---
        isTracking: Boolean,
        onStartTrackingClick: () -> Unit,
        onStopTrackingClick: () -> Unit,
        healthServiceConnected: Boolean // 헬스 서비스 상태 받기
    ) {
        val blackBackgroundColors = MaterialTheme.colors.copy(
            background = Color.Black, onBackground = Color.White,
            surface = Color.Black, onSurface = Color.White,
            primary = Color(0xFFBB86FC), onPrimary = Color.Black
        )
        MaterialTheme (colors = blackBackgroundColors){
            Scaffold(
                timeText = { TimeText(modifier = Modifier.padding(top = 5.dp)) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. 블루투스 상태 및 버튼
                    Text(
                        text = "BT: $btStatus",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp),
                        style = MaterialTheme.typography.title3,
                        color = if (isConnected) Color(0xFF4CAF50) else LocalContentColor.current
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        Button(onClick = onConnectClick, enabled = !isConnecting && !isConnected, modifier = Modifier.weight(1f)) {
                            Text("PC 연결", maxLines = 1)
                        }
                        Button(onClick = onDisconnectClick, enabled = isConnecting || isConnected, modifier = Modifier.weight(1f)) {
                            Text("연결 끊기", maxLines = 1)
                        }
                    }
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(bottom=8.dp).size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }

                    // --- 센서 트래킹 상태 및 버튼 추가 ---
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sensors: ${if (!healthServiceConnected) "서비스 연결 안됨" else if (isTracking) "측정 중" else "중지됨"}",
                        style = MaterialTheme.typography.caption1,
                        color = if (isTracking) Color.Green else LocalContentColor.current
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        Button(
                            onClick = onStartTrackingClick,
                            // 헬스 서비스 연결되고, 현재 트래킹 중이 아닐 때만 활성화
                            enabled = healthServiceConnected && !isTracking,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("측정 시작", maxLines = 1)
                        }
                        Button(
                            onClick = onStopTrackingClick,
                            // 현재 트래킹 중일 때만 활성화
                            enabled = isTracking,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("측정 중지", maxLines = 1)
                        }
                    }
                    // --- 끝: 센서 트래킹 상태 및 버튼 추가 ---


                    Spacer(modifier = Modifier.padding(vertical = 8.dp)) // Use Divider instead of Spacer for visual separation

                    // 2. HR 데이터 표시
                    Text("Heart Rate", style = MaterialTheme.typography.caption1, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (hrData != null && isTracking) { // isTracking 조건 추가
                        Text("Status: ${hrData.status} (${getHrStatusString(hrData.status)})")
                        Text("HR: ${hrData.hr} bpm")
                        Text("IBI: ${hrData.ibi} ms (Q: ${hrData.qIbi})")
                    } else {
                        Text("Status: N/A")
                        Text("HR: --- bpm")
                        Text("IBI: --- ms (Q: -)")
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. PPG 데이터 표시
                    Text("Raw PPG", style = MaterialTheme.typography.caption1, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (ppgData != null && isTracking) { // isTracking 조건 추가
                        val tsFormatted = try {
                            timestampFormatter.format(Date(TimeUnit.NANOSECONDS.toMillis(ppgData.timestampNs)))
                        } catch (e: Exception) { "--:--:--.---" }
                        Text("Time: $tsFormatted")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Text("G: ${ppgData.green}")
                        }
                        Text("greenStatus: ${ppgData.greenStatus ?: "N/A"} (${getPpgStatusString(ppgData.greenStatus)})")
                    } else {
                        Text("Time: --:--:--.---")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Text("G: ---")
                            Text("IR: ---")
                            Text("R: ---")
                        }
                        Text("Status: N/A")
                    }
                }
            }
        }
    }

    // --- 권한 관련 함수 (동일) ---
    private fun getRequiredPermissions(): List<String> { /* ... 기존과 동일 ... */
        val permissions = mutableListOf(
            Manifest.permission.BODY_SENSORS // 센서 권한
        )
        // 블루투스 관련 권한
        permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        return permissions
    }
    private fun checkAndRequestPermissions() { /* ... 기존과 동일 ... */
        val permissionsToRequest = getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.i(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.d(TAG, "All required permissions already granted.")
            allPermissionsGranted = true
            initializeAfterPermissions()
        }
    }
    private fun initializeAfterPermissions() { /* ... 기존과 동일 ... */
        if (!allPermissionsGranted) {
            Log.w(TAG, "Cannot initialize, permissions not granted.")
            return
        }
        Log.d(TAG, "Permissions granted, proceeding with initialization...")
        val btEnabled = checkBluetoothEnabled() // Check BT status first
        createAndConnectSdk() // Try connecting to SDK regardless of BT
    }
    private fun checkPermissions(): Boolean { /* ... 기존과 동일 ... */
        val allGranted = getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!allGranted) {
            Log.w(TAG, "Runtime permission check FAILED.")
        }
        return allGranted
    }

    // --- 블루투스 활성화 확인 (동일) ---
    @SuppressLint("MissingPermission")
    private fun checkBluetoothEnabled(): Boolean { /* ... 기존과 동일 ... */
        val hasBtPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (!hasBtPermission) {
            Log.e(TAG, "Missing Bluetooth permission for checking/enabling.")
            return false
        }
        if (bluetoothAdapter == null) { Log.e(TAG, "BT adapter null"); updateStatus("블루투스 오류"); return false }

        if (bluetoothAdapter?.isEnabled == false) {
            Log.i(TAG, "Bluetooth is not enabled. Requesting.")
            updateStatus("블루투스 활성화 필요")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            try {
                requestEnableBluetoothLauncher.launch(enableBtIntent)
            } catch (e: SecurityException) { Log.e(TAG, "SecEx on enable BT", e); updateStatus("권한 오류") }
            catch (e: Exception) { Log.e(TAG, "Ex on enable BT", e); updateStatus("오류 발생") }
            return false
        }
        Log.d(TAG, "Bluetooth is enabled.")
        return true
    }

    // --- Health SDK 연결 시도 (동일) ---
    private fun createAndConnectSdk() { /* ... 기존과 동일 ... */
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Cannot connect SDK without BODY_SENSORS permission.")
            updateStatus("센서 권한 필요")
            return
        }
        if (healthServiceConnected || connectionManager != null) {
            Log.d(TAG, "ConnectionManager already exists or connected. Skipping.")
            return
        }

        Log.i(TAG, "Creating ConnectionManager and connecting to Health Tracking Service...")
        try {
            connectionManager = ConnectionManager(this, healthConnectionObserver) // 가정
            connectionManager?.connect()
            Log.d(TAG, "connectionManager?.connect() called.")
            updateStatus("헬스 서비스 연결 중...")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to create or connect ConnectionManager: ${t.message}", t)
            updateStatus("헬스 서비스 연결 불가")
        }
    }

    // --- 센서 리스너 초기화 (수정됨: 자동 시작 제거) ---
    private fun initializeListenersAndTrackers() {
        if (!healthServiceConnected || connectionManager == null) {
            Log.e(TAG, "Cannot initialize listeners: Health Service not connected or ConnectionManager is null.")
            return
        }
        Log.i(TAG, "Initializing listeners...")
        // 리스너 객체 생성 또는 가져오기 (ConnectionManager가 제공한다고 가정)
//        heartRateListener = HeartRateListener()
//        ppgListener = PpgListener() // 실제 구현에 맞게 수정

        heartRateListener = HeartRateListener(/* 필요한 인자 전달 */)
        connectionManager?.initHeartRate(heartRateListener!!) // 실제 초기화 메서드 호출 (가정)
        ppgListener = PpgListener(/* args */)
        connectionManager?.initPpg(ppgListener!!)

        Log.i(TAG, "Listeners initialized. Ready to start tracking.")
        // 여기서 startTracker() 호출 제거됨
    }

    // --- New: 센서 트래킹 시작 함수 ---
    private fun startSensorTracking() {
        Log.d(TAG, "startSensorTracking called.")
        if (isTrackingSensors) {
            Log.w(TAG, "Sensors already tracking.")
            return
        }
        if (!healthServiceConnected || connectionManager == null) {
            Log.e(TAG, "Cannot start tracking: Health Service not connected or ConnectionManager is null.")
            Toast.makeText(this, "헬스 서비스 연결 필요", Toast.LENGTH_SHORT).show()
            return
        }
        // 센서 권한 재확인 (방어적 코드)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot start tracking: BODY_SENSORS permission missing.")
            Toast.makeText(this, "센서 권한 필요", Toast.LENGTH_SHORT).show()
            checkAndRequestPermissions() // 다시 권한 요청
            return
        }

        Log.i(TAG, "Starting sensor tracking...")
        try {
            heartRateListener?.startTracker()
            ppgListener?.startTracker()
            isTrackingSensors = true // 상태 업데이트
            Log.i(TAG, "Sensor tracking status set to: $isTrackingSensors")
            // 데이터 초기화
            latestHrData = null
            latestPpgData = null
            updateStatus("센서 측정 시작됨") // 상태 메시지 업데이트
            Toast.makeText(this, "센서 측정 시작", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting trackers: ${e.message}", e)
            isTrackingSensors = false // 시작 실패 시 상태 복원
            Toast.makeText(this, "센서 시작 오류", Toast.LENGTH_SHORT).show()
            updateStatus("센서 시작 오류")
        }
    }

    // --- New: 센서 트래킹 중지 함수 ---
    private fun stopSensorTracking() {
        Log.d(TAG, "stopSensorTracking called.")
        if (!isTrackingSensors) {
            // Log.d(TAG, "Sensors already stopped.") // 너무 자주 로깅될 수 있음
            return
        }
        Log.i(TAG, "Stopping sensor tracking...")
        try {
            heartRateListener?.stopTracker()
            ppgListener?.stopTracker()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping trackers: ${e.message}", e)
            // 오류가 발생해도 상태는 중지된 것으로 간주
        } finally {
            isTrackingSensors = false // 상태 업데이트
            // UI 클리어를 위해 null로 설정 (선택 사항)
            // latestHrData = null
            // latestPpgData = null
            updateStatus("센서 측정 중지됨") // 상태 메시지 업데이트
            // Toast.makeText(this, "센서 측정 중지", Toast.LENGTH_SHORT).show() // 중지는 조용히
        }
    }


    // --- 블루투스 연결 관련 함수들 (동일) ---
    @SuppressLint("MissingPermission")
    private fun connectToTargetPC() { /* ... 기존과 동일 ... */
        if (isConnectingBluetooth || bluetoothSocket?.isConnected == true) { Log.w(TAG, "BT Already connecting or connected."); return }
        if (!checkPermissions()) { checkAndRequestPermissions(); return }
        if (!checkBluetoothEnabled()) { return }

        val targetDevice: BluetoothDevice? = try {
            bluetoothAdapter?.getRemoteDevice(TARGET_PC_ADDRESS)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting remote device $TARGET_PC_ADDRESS", e)
            updateStatus(when(e) {
                is SecurityException -> "권한 오류 (기기)"
                is IllegalArgumentException -> "잘못된 PC 주소"
                else -> "기기 접근 오류"
            })
            null
        }
        targetDevice?.let { initiateConnectionWithDevice(it) }
            ?: Log.e(TAG, "Target device object is null, cannot connect.")
    }
    @SuppressLint("MissingPermission")
    private fun initiateConnectionWithDevice(device: BluetoothDevice) { /* ... 기존과 동일 ... */
        if (!checkPermissions()) { Log.e(TAG,"BT Perm check fail in initiateConnection"); return }
        if (isConnectingBluetooth) { Log.w(TAG, "Already attempting BT connection"); return }

        val deviceName = try { device.name ?: device.address } catch (e: SecurityException) { device.address }
        updateStatus("BT 연결 시도 중: $deviceName")
        isConnectingBluetooth = true

        bluetoothConnectionJob?.cancel()
        bluetoothConnectionJob = lifecycleScope.launch(Dispatchers.IO) {
            var tempSocket: BluetoothSocket? = null
            try {
                tempSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                tempSocket?.connect()

                bluetoothSocket = tempSocket
                outputStream = tempSocket?.outputStream
                Log.i(TAG, "BT Successfully connected to $deviceName")

                withContext(Dispatchers.Main) {
                    btConnectionStatus = "BT 연결 성공: $deviceName"; isConnectingBluetooth = false
                    Toast.makeText(this@MainActivity, "PC 연결 성공!", Toast.LENGTH_SHORT).show()
                }
                sendDataViaBluetooth(convertToJson(mapOf("status" to "connected", "device" to Build.MODEL)))

            } catch (e: Exception) {
                Log.e(TAG, "BT Connection failed to $deviceName: ${e.message}", e)
                val errorMsg = when(e){
                    is IOException -> "BT IO 오류"
                    is SecurityException -> "BT 권한 오류 (연결)"
                    else -> "BT 연결 실패"
                }
                withContext(Dispatchers.Main) {
                    btConnectionStatus = errorMsg; isConnectingBluetooth = false
                    Toast.makeText(this@MainActivity, "PC 연결 실패: $errorMsg", Toast.LENGTH_LONG).show()
                }
                closeBluetoothSocket()
            }
        }
    }

    // --- 데이터 전송 함수 (동일) ---
    fun sendDataViaBluetooth(jsonData: String) { /* ... 기존과 동일 ... */
        if (outputStream == null || bluetoothSocket?.isConnected != true) {
            if (btConnectionStatus.startsWith("BT 연결 성공")) {
                updateStatus("BT 연결 끊김 (전송 시도)")
            }
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val msgBuffer: ByteArray = jsonData.toByteArray(Charsets.UTF_8)
            try {
                outputStream?.write(msgBuffer)
                outputStream?.flush()
            } catch (e: IOException) {
                Log.e(TAG, "IOException during sending BT data: ${e.message}")
                withContext(Dispatchers.Main) {
                    // Toast.makeText(this@MainActivity, "데이터 전송 오류", Toast.LENGTH_SHORT).show()
                }
                closeBluetoothSocket()
                updateStatus("BT 연결 끊김 (전송 오류)")
            }
        }
    }

    // --- JSON 변환 함수 (동일) ---
    private fun convertToJson(data: Any): String { /* ... 기존과 동일 ... */
        return try {
            gson.toJson(data)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting data to JSON for BT: ${e.message}")
            "{\"error\":\"json conversion failed\", \"data_type\":\"${data::class.simpleName}\"}"
        }
    }

    // --- 블루투스 연결 해제 (동일) ---
    private fun disconnectBluetooth() { /* ... 기존과 동일 ... */
        bluetoothConnectionJob?.cancel()
        closeBluetoothSocket()
        updateStatus("BT 연결 끊김")
        isConnectingBluetooth = false
        Log.i(TAG, "Bluetooth Disconnected.")
    }

    // --- 블루투스 소켓 닫기 (동일) ---
    private fun closeBluetoothSocket() { /* ... 기존과 동일 ... */
        if (bluetoothSocket == null && outputStream == null) return
        Log.d(TAG,"Closing BT socket and stream.")
        try { outputStream?.close() } catch (e: IOException) { Log.w(TAG,"IOE closing OS: ${e.message}")} finally { outputStream = null }
        try { bluetoothSocket?.close() } catch (e: IOException) { Log.w(TAG,"IOE closing BS: ${e.message}")} finally { bluetoothSocket = null }
        updateStatus("BT 연결 없음")
        isConnectingBluetooth = false
    }

    // --- 상태 코드 -> 문자열 변환 함수 (동일) ---
    private fun getHrStatusString(status: Int?): String { /* ... 기존과 동일 ... */
        return when (status) {
            Status.STATUS_FIND_HR -> "Finding HR"
            Status.STATUS_ATTACHED -> "Attached"
            Status.STATUS_DETECT_MOVE -> "Moving"
            Status.STATUS_DETACHED -> "Detached"
            Status.STATUS_LOW_RELIABILITY -> "Low Reliability"
            Status.STATUS_VERY_LOW_RELIABILITY -> "Very Low Reliability"
            Status.STATUS_NO_DATA_FLUSH -> "No Data Flush"
            Status.STATUS_NONE -> "None"
            else -> "Unknown ($status)"
        }
    }
    private fun getPpgStatusString(status: Int?): String { /* ... 기존과 동일 ... */
        return when (status) {
            0 -> "Good" // 예시
            -1 -> "Noisy" // 예시
            -3 -> "Detached" // 예시
            null -> "N/A"
            else -> "Code $status"
        }
    }

} // End of MainActivity


// --- 필요한 데이터 클래스 및 객체 정의 (이전과 동일) ---
// data class HeartRateData(...)
// data class PpgData(...)
// object Status { ... } // 실제 값 사용

// --- 가정하는 인터페이스 및 클래스 (이전과 동일, 실제 SDK 구현 필요) ---
// interface TrackerDataObserver { ... }
// class TrackerDataNotifier { ... }
// interface ConnectionObserver { ... }
// class ConnectionManager(context: Context, observer: ConnectionObserver) { ... }
// open class BaseListener { ... }
// class HeartRateListener : BaseListener() { ... }
// class PpgListener : BaseListener() { ... }


// --- Jetpack Compose Previews (수정됨) ---

@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true, name = "Preview Initial")
@Composable
fun PreviewInitial() {
    MaterialTheme {
        WearAppPreview(
            btStatus = "대기 중",
            isConnecting = false,
            isConnected = false,
            hrData = null,
            ppgData = null,
            isTracking = false,      // 초기 상태: 트래킹 중 아님
            healthServiceConnected = false // 초기 상태: 서비스 연결 안됨
        )
    }
}

@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true, name = "Preview Connected & Tracking")
@Composable
fun PreviewConnectedTracking() {
    MaterialTheme {
        WearAppPreview(
            btStatus = "BT 연결 성공: PC",
            isConnecting = false,
            isConnected = true,
            hrData = HeartRateData(status = Status.STATUS_FIND_HR, hr = 75, ibi = 800, qIbi = 0),
            ppgData = PpgData(timestampNs = System.nanoTime(), green = 15000, greenStatus = 0),
            isTracking = true,       // 트래킹 중 상태
            healthServiceConnected = true // 서비스 연결됨 상태
        )
    }
}

@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true, name = "Preview Connected & Stopped")
@Composable
fun PreviewConnectedStopped() {
    MaterialTheme {
        WearAppPreview(
            btStatus = "BT 연결 성공: PC",
            isConnecting = false,
            isConnected = true,
            hrData = null, // 중지 시 데이터 없음 (또는 이전 데이터 표시 가능)
            ppgData = null,
            isTracking = false,      // 트래킹 중지 상태
            healthServiceConnected = true // 서비스 연결됨 상태
        )
    }
}


// Preview 전용 Composable (MainActivity의 WearApp 로직과 유사하게 만듦, 수정됨)
@Composable
fun WearAppPreview(
    btStatus: String,
    isConnecting: Boolean,
    isConnected: Boolean,
    hrData: HeartRateData?,
    ppgData: PpgData?,
    // --- New parameters for Preview ---
    isTracking: Boolean,
    healthServiceConnected: Boolean
) {
    val previewTimestampFormatter = remember {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    }
    val blackBackgroundColors = MaterialTheme.colors.copy(
        background = Color.Black, onBackground = Color.White,
        surface = Color.Black, onSurface = Color.White,
        primary = Color(0xFFBB86FC), onPrimary = Color.Black
    )

    MaterialTheme(colors = blackBackgroundColors) {
        Scaffold(
            timeText = { TimeText(modifier = Modifier.padding(top = 5.dp)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // BT Section (unchanged)
                Text(
                    text = "BT: $btStatus", textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.title3,
                    color = if (isConnected) Color(0xFF4CAF50) else LocalContentColor.current
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    Button(onClick = {}, enabled = !isConnecting && !isConnected, modifier = Modifier.weight(1f)) { Text("PC 연결", maxLines = 1) }
                    Button(onClick = {}, enabled = isConnecting || isConnected, modifier = Modifier.weight(1f)) { Text("연결 끊기", maxLines = 1) }
                }
                if (isConnecting) { CircularProgressIndicator(modifier = Modifier.padding(bottom=8.dp).size(24.dp), strokeWidth = 2.dp) }

                // --- Sensor Tracking Section (Added to Preview) ---
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sensors: ${if (!healthServiceConnected) "서비스 연결 안됨" else if (isTracking) "측정 중" else "중지됨"}",
                    style = MaterialTheme.typography.caption1,
                    color = if (isTracking) Color.Green else LocalContentColor.current
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    Button(onClick = {}, enabled = healthServiceConnected && !isTracking, modifier = Modifier.weight(1f)) {
                        Text("측정 시작", maxLines = 1)
                    }
                    Button(onClick = {}, enabled = isTracking, modifier = Modifier.weight(1f)) {
                        Text("측정 중지", maxLines = 1)
                    }
                }
                // --- End Sensor Tracking Section ---

                Spacer(modifier = Modifier.padding(vertical = 8.dp))

                // HR Section (add isTracking check)
                Text("Heart Rate", style = MaterialTheme.typography.caption1, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                if (hrData != null && isTracking) { // Check isTracking
                    Text("Status: ${hrData.status} (${MainActivity().getHrStatusString(hrData.status)})") // Use helper
                    Text("HR: ${hrData.hr} bpm")
                    Text("IBI: ${hrData.ibi} ms (Q: ${hrData.qIbi})")
                } else {
                    Text("Status: N/A"); Text("HR: --- bpm"); Text("IBI: --- ms (Q: -)")
                }
                Spacer(modifier = Modifier.height(12.dp))

                // PPG Section (add isTracking check)
                Text("Raw PPG", style = MaterialTheme.typography.caption1, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                if (ppgData != null && isTracking) { // Check isTracking
                    val tsFormatted = try {
                        previewTimestampFormatter.format(Date(TimeUnit.NANOSECONDS.toMillis(ppgData.timestampNs)))
                    } catch (e: Exception) { "--:--:--.---" }
                    Text("Time: $tsFormatted")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Text("G: ${ppgData.green}")
                    }
                    Text("Status: ${ppgData.greenStatus ?: "N/A"} (${MainActivity().getPpgStatusString(ppgData.greenStatus)})") // Use helper
                } else {
                    Text("Time: --:--:--.---")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Text("G: ---"); Text("IR: ---"); Text("R: ---")
                    }
                    Text("Status: N/A")
                }
            }
        }
    }
}

// MainActivity 내의 private 함수를 Preview에서 사용하기 위한 임시 public 함수 (기존과 동일)
// 실제로는 ViewModel 등을 사용하여 상태 및 로직 분리 권장
fun MainActivity.getHrStatusString(status: Int?): String { return this.getHrStatusString(status)}
fun MainActivity.getPpgStatusString(status: Int?): String { return this.getPpgStatusString(status) }