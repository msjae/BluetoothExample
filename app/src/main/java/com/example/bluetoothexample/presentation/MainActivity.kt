package com.example.bluetoothexample.presentation

import android.Manifest
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

// 데이터 클래스 제거
// data class DiscoveredDeviceInfo(...)

class MainActivity : ComponentActivity() {

    private val TAG = "WearBluetoothDirect"
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // SPP UUID
    // [추가] 연결할 PC의 MAC 주소 상수
    private val TARGET_PC_ADDRESS = "8C:88:4B:26:8A:36"

    // --- Bluetooth 관련 변수 ---
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var connectionJob: Job? = null

    // --- UI 상태 관리 ---
    private var connectionStatus by mutableStateOf("대기 중")
    private var isConnecting by mutableStateOf(false)
    // private var isScanning by mutableStateOf(false) // 제거
    // private var discoveredDevices = mutableStateListOf<DiscoveredDeviceInfo>() // 제거

    // --- BroadcastReceiver 관련 변수 제거 ---
    // private var isReceiverRegistered = false

    // --- 권한 요청 런처 ---
    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allGranted = true
            permissions.entries.forEach {
                Log.d(TAG, "Permission ${it.key} granted: ${it.value}")
                if (!it.value) { allGranted = false; Log.w(TAG, "Permission denied: ${it.key}") }
            }
            if (allGranted) {
                Log.d(TAG, "Required permissions granted.")
                checkBluetoothEnabled()
            } else {
                Log.e(TAG, "Required permissions were denied.")
                updateStatus("권한 필요")
                Toast.makeText(this, "앱 실행에 필요한 권한이 거부되었습니다.", Toast.LENGTH_LONG).show()
            }
        }

    // --- 블루투스 활성화 요청 런처 ---
    private val requestEnableBluetooth =
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

    // --- BroadcastReceiver 제거 ---
    // private val bluetoothDiscoveryReceiver = ...

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "블루투스를 지원하지 않는 기기입니다.", Toast.LENGTH_LONG).show()
            finish(); return
        }

        setContent {
            WearApp()
        }

        checkAndRequestPermissions()
    }

    // --- UI 상태 업데이트 편의 함수 ---
    private fun updateStatus(status: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            connectionStatus = status
        }
    }

    // --- Compose UI 부분 (단순화) ---
    @Composable
    fun WearApp() {
        MaterialTheme {
            Scaffold(
                // vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) } // 필요 시 비네팅 추가
            ) {
                Column( // ScalingLazyColumn 대신 Column 사용 (아이템 수가 적으므로)
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp), // 전체 패딩
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center // 수직 중앙 정렬
                ) {
                    // 1. 상태 텍스트
                    Text(
                        text = connectionStatus,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp), // 버튼과의 간격
                        style = MaterialTheme.typography.title3
                    )

                    // 2. 연결/해제 버튼 영역
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        // 연결 버튼
                        Button(
                            onClick = { connectToTargetPC() },
                            // 연결 중이거나 이미 연결된 상태면 비활성화
                            enabled = !isConnecting && bluetoothSocket?.isConnected != true,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("PC 연결", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        // 연결 끊기 버튼
                        Button(
                            onClick = { disconnect() },
                            // 연결 중이거나 연결된 상태일 때 활성화
                            enabled = isConnecting || bluetoothSocket?.isConnected == true,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("연결 끊기", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    // 검색 관련 UI 제거
                } // End of Column
            } // End of Scaffold
        } // End of MaterialTheme
    }

    // DeviceChipItem 제거

    // --- 권한 확인 및 요청 관련 함수 (수정) ---
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 연결 권한 (필수)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            // 스캔 권한 (직접 연결에는 필수 아님, 필요 시 추가)
            // if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            //    permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            // }
        } else {
            // 이전 버전 권한 (필수)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
            // 위치 권한 (직접 연결에는 필수 아님)
            // if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //     permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            // }
        }

        // 필요한 권한이 있다면 요청
        if (permissionsToRequest.isNotEmpty()) {
            Log.i(TAG, "Requesting necessary permissions: ${permissionsToRequest.joinToString()}")
            requestMultiplePermissions.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.d(TAG, "Necessary permissions already granted.")
            checkBluetoothEnabled() // 권한 있으면 BT 활성화 확인
        }
    }

    // --- 현재 필요한 모든 권한이 있는지 확인하는 함수 (수정) ---
    private fun checkPermissions(): Boolean {
        val connectPermissionGranted: Boolean

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            connectPermissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            connectPermissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }

        // 스캔, 위치 권한 체크 제거 (현재 로직 기준)

        if (!connectPermissionGranted) {
            Log.w(TAG, "Permission check FAILED: Missing Connect Permission")
        }
        return connectPermissionGranted
    }


    // --- 블루투스 활성화 확인 및 요청 (이전과 동일) ---
    @SuppressLint("MissingPermission")
    private fun checkBluetoothEnabled(): Boolean {
        // Adapter null 체크 추가
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth adapter is null in checkBluetoothEnabled.")
            updateStatus("블루투스 오류")
            return false
        }
        if (bluetoothAdapter?.isEnabled == false) {
            Log.i(TAG, "Bluetooth is not enabled. Requesting user to enable.")
            updateStatus("블루투스 활성화 필요")

            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            // 활성화 요청에 필요한 권한 확인 (CONNECT 또는 BLUETOOTH)
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
            }
            if (hasPermission) {
                try { requestEnableBluetooth.launch(enableBtIntent) }
                catch (e: SecurityException) { Log.e(TAG, "SecurityException on enable BT", e); updateStatus("권한 오류") }
                catch (e: Exception) { Log.e(TAG, "Exception on enable BT", e); updateStatus("오류 발생") }
            } else {
                Log.e(TAG, "Cannot request BT enable without permission.")
                checkAndRequestPermissions() // 권한 재요청
            }
            return false
        }
        Log.d(TAG, "Bluetooth is enabled.")
        return true
    }

    // --- 기기 검색 시작 함수 제거 ---
    // private fun startScan() { ... }

    // --- 주변 기기 검색 시작 함수 제거 ---
    // private fun startBluetoothDiscovery() { ... }

    // --- 주변 기기 검색 취소 함수 제거 ---
    // private fun cancelBluetoothDiscovery() { ... }

    // --- BroadcastReceiver 등록 해제 함수 제거 ---
    // private fun unregisterDiscoveryReceiver() { ... }


    // --- [추가] 지정된 PC 주소로 연결 시도 ---
    @SuppressLint("MissingPermission")
    private fun connectToTargetPC() {
        // 0. 상태 확인
        if (isConnecting) {
            Log.w(TAG, "Already connecting.")
            return
        }
        if (bluetoothSocket?.isConnected == true) {
            Log.w(TAG, "Already connected.")
            updateStatus("이미 연결됨") // 사용자에게 알림
            return
        }

        // 1. 권한 확인
        if (!checkPermissions()) {
            Log.e(TAG, "Cannot connect without CONNECT permission.")
            updateStatus("권한 없음")
            checkAndRequestPermissions() // 권한 요청
            return
        }

        // 2. 블루투스 활성화 확인
        if (!checkBluetoothEnabled()) {
            // 활성화 요청은 checkBluetoothEnabled 내부에서 처리
            return
        }

        // 3. BluetoothDevice 객체 가져오기
        val targetDevice: BluetoothDevice? = try {
            // CONNECT (S+) 또는 BLUETOOTH (S-) 권한 필요
            bluetoothAdapter?.getRemoteDevice(TARGET_PC_ADDRESS)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException getting remote device $TARGET_PC_ADDRESS", e)
            updateStatus("권한 오류 (기기)")
            null
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid Bluetooth address: $TARGET_PC_ADDRESS", e)
            updateStatus("잘못된 PC 주소")
            null
        } catch (e: Exception){
            Log.e(TAG, "Error getting remote device $TARGET_PC_ADDRESS", e)
            updateStatus("기기 접근 오류")
            null
        }

        // 4. 기기 객체 얻었으면 연결 시도
        if (targetDevice != null) {
            Log.i(TAG, "Target PC device found: ${targetDevice.name ?: TARGET_PC_ADDRESS}")
            initiateConnectionWithDevice(targetDevice)
        } else {
            Log.e(TAG, "Failed to get BluetoothDevice for $TARGET_PC_ADDRESS")
            // 상태 업데이트는 try-catch 블록 내부에서 이미 처리됨
        }
    }


    // --- 특정 기기와 연결 시도 (거의 동일, 상태 업데이트 추가) ---
    @SuppressLint("MissingPermission") // 함수 초입에서 checkPermissions() 호출됨
    private fun initiateConnectionWithDevice(device: BluetoothDevice) {
        // 이미 checkPermissions()를 통과했지만, 방어적으로 추가 가능
        if (!checkPermissions()) { Log.e(TAG,"Perm check fail in initiateConnection"); return }
        if (isConnecting) { Log.w(TAG, "Already connecting"); return }

        val deviceName = try { device.name } catch (e: SecurityException) { device.address } ?: "Unknown"
        updateStatus("연결 시도 중: $deviceName")
        isConnecting = true // 메인 스레드에서 isConnecting 업데이트

        connectionJob?.cancel()
        connectionJob = lifecycleScope.launch(Dispatchers.IO) {
            Log.i(TAG, "Attempting to connect to $deviceName [${device.address}]")
            var tempSocket: BluetoothSocket? = null
            try {
                // CONNECT 권한 필요
                tempSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                tempSocket?.connect() // Blocking call - CONNECT 권한 필요

                bluetoothSocket = tempSocket
                outputStream = tempSocket?.outputStream
                Log.i(TAG, "Successfully connected to $deviceName")

                withContext(Dispatchers.Main) {
                    connectionStatus = "연결 성공: $deviceName"; isConnecting = false
                    Toast.makeText(this@MainActivity, "PC 연결 성공!", Toast.LENGTH_SHORT).show()
                }
                // TODO: 여기에 실제 생체 데이터 전송 로직 시작 또는 활성화 코드 추가
                sendData("Hello from Wear OS! Connected to $deviceName at ${System.currentTimeMillis()}")

            } catch (e: Exception) { // IOException, SecurityException 등
                Log.e(TAG, "Connection failed to $deviceName: ${e.message}", e)
                val errorMsg = when(e){
                    is IOException -> "IO 오류 (${e.message})"
                    is SecurityException -> "권한 오류 (연결)"
                    else -> "연결 실패 (${e.javaClass.simpleName})"
                }
                withContext(Dispatchers.Main) {
                    connectionStatus = "연결 실패: $errorMsg"; isConnecting = false
                    Toast.makeText(this@MainActivity, "PC 연결 실패: $errorMsg", Toast.LENGTH_LONG).show()
                }
                closeSocket() // 실패 시 소켓 정리
            }
            // finally 블록 제거됨, isConnecting = false 는 성공/실패 경로에서 처리
        }
    }

    // --- 데이터 전송 함수 ---
    // 이 함수는 이제 외부(예: 센서 데이터 콜백)에서 호출되어야 합니다.
    fun sendData(message: String) {
        if (outputStream == null || bluetoothSocket?.isConnected != true) {
            // Log.w(TAG, "Cannot send data: Not connected.") // 너무 자주 로깅될 수 있음
            // 연결이 끊겼을 때의 처리 (예: UI 업데이트, 재연결 시도 등)
            if (connectionStatus.startsWith("연결 성공")) { // 연결 상태였는데 끊긴 경우
                updateStatus("연결 끊김 (전송 시도)")
                // 필요 시 자동으로 재연결 시도 로직 추가
                // connectToTargetPC()
            }
            return
        }
        // 데이터 전송은 IO 스레드에서
        lifecycleScope.launch(Dispatchers.IO) {
            val msgBuffer: ByteArray = message.toByteArray() // UTF-8 기본 인코딩
            Log.d(TAG, "Sending data (${msgBuffer.size} bytes): \"$message\"")
            try {
                // CONNECT 권한 필요 (소켓 유효성으로 갈음)
                outputStream?.write(msgBuffer)
                outputStream?.flush()
                // Log.i(TAG, "Data sent successfully.") // 성공 로깅은 너무 많을 수 있음
                // 성공 시 UI 피드백은 필요에 따라 추가 (예: 마지막 전송 시간 표시)
            } catch (e: IOException) {
                Log.e(TAG, "IOException during sending data: ${e.message}")
                // UI 업데이트 (메인 스레드) - 실패 알림
                withContext(Dispatchers.Main) {
                    // 데이터 전송 실패 시 상태 업데이트 또는 Toast
                    // connectionStatus = "데이터 전송 실패" // 상태를 계속 덮어쓸 수 있으므로 주의
                    // Toast.makeText(this@MainActivity, "데이터 전송 중 오류", Toast.LENGTH_SHORT).show()
                }
                // ★ 중요: 데이터 전송 실패 시 연결 상태 확인 및 소켓 닫기 고려
                closeSocket() // 연결 끊김으로 간주하고 정리
                updateStatus("연결 끊김 (전송 오류)")
            }
        }
    }

    // --- 연결 끊기 및 리소스 정리 ---
    private fun disconnect() {
        // cancelBluetoothDiscovery() 제거
        connectionJob?.cancel()
        closeSocket()

        // 상태 변수 초기화 (메인 스레드)
        lifecycleScope.launch(Dispatchers.Main) {
            if (isConnecting || bluetoothSocket?.isConnected == true || !connectionStatus.contains("끊김")) {
                connectionStatus = "연결 끊김"
            }
            isConnecting = false
            // isScanning = false 제거
        }
        Log.i(TAG, "Disconnected.")
    }

    // --- 소켓 및 스트림 닫기 (이전과 동일) ---
    private fun closeSocket() {
        if (bluetoothSocket == null && outputStream == null) return // 이미 닫혔으면 중복 실행 방지
        Log.d(TAG,"Closing BT socket and stream.")
        try { outputStream?.close() } catch (e: IOException) { Log.w(TAG,"IOE closing OS: ${e.message}")} finally { outputStream = null }
        try { bluetoothSocket?.close() } catch (e: IOException) { Log.w(TAG,"IOE closing BS: ${e.message}")} finally { bluetoothSocket = null }
    }

    override fun onDestroy() {
        super.onDestroy(); Log.d(TAG, "onDestroy called."); disconnect()
    }
}


// --- Jetpack Compose Previews (단순화된 UI 반영) ---

@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true, name = "Initial State")
@Composable
fun PreviewInitialDirect() {
    MaterialTheme {
        Scaffold {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("대기 중", textAlign = TextAlign.Center, style = MaterialTheme.typography.title3, modifier = Modifier.padding(bottom = 16.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    Button(onClick={}, modifier=Modifier.weight(1f)){Text("PC 연결")}
                    Button(onClick={}, enabled=false, modifier=Modifier.weight(1f)){Text("연결 끊기")}
                }
            }
        }
    }
}

@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true, name = "Connecting State")
@Composable
fun PreviewConnectingDirect() {
    MaterialTheme {
        Scaffold {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("연결 시도 중: TargetPC...", textAlign = TextAlign.Center, style = MaterialTheme.typography.title3, modifier = Modifier.padding(bottom = 16.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    Button(onClick={}, enabled=false, modifier=Modifier.weight(1f)){Text("PC 연결")}
                    Button(onClick={}, enabled=true, modifier=Modifier.weight(1f)){Text("연결 끊기")} // 연결 시도 중에도 끊기 가능
                }
                CircularProgressIndicator(modifier = Modifier.padding(top=8.dp)) // 연결 중 표시
            }
        }
    }
}


@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true, name = "Connected State")
@Composable
fun PreviewConnectedDirect() {
    MaterialTheme {
        Scaffold {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("연결 성공: TargetPC", textAlign = TextAlign.Center, style = MaterialTheme.typography.title3, modifier = Modifier.padding(bottom = 16.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    Button(onClick={}, enabled=false, modifier=Modifier.weight(1f)){Text("PC 연결")} // 이미 연결됨
                    Button(onClick={}, enabled=true, modifier=Modifier.weight(1f)){Text("연결 끊기")}
                }
                // TODO: 여기에 데이터 전송 시작 버튼 또는 상태 표시 추가 가능
            }
        }
    }
}