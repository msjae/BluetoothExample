/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter and
 * https://github.com/android/wear-os-samples/tree/main/ComposeAdvanced to find the most up to date
 * changes to the libraries and their usages.
 */

package com.cookandroid.bluetoothexample // 패키지 이름은 본인 프로젝트에 맞게 수정하세요

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val TAG = "WearBluetoothClassic"
    private val TARGET_DEVICE_NAME = "MyBluetoothDeviceName" // 예시 이름, 실제 기기 이름으로 변경!
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // SPP UUID

    // --- Bluetooth 관련 변수 ---
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var connectionJob: Job? = null
    private var foundDevice: BluetoothDevice? = null

    // --- UI 상태 관리 ---
    private var connectionStatus by mutableStateOf("대기 중")
    private var isConnecting by mutableStateOf(false)
    private var isScanning by mutableStateOf(false)

    // --- 권한 요청 런처 ---
    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // ... (이전 코드와 동일) ...
            var allGranted = true
            permissions.entries.forEach {
                Log.d(TAG, "Permission ${it.key} granted: ${it.value}")
                if (!it.value) {
                    allGranted = false
                }
            }
            if (allGranted) {
                Log.d(TAG, "All required permissions granted.")
                checkBluetoothEnabled()
            } else {
                Log.e(TAG, "Required Bluetooth permissions were denied.")
                connectionStatus = "블루투스/위치 권한 필요"
                Toast.makeText(this, "앱 실행에 필요한 권한이 거부되었습니다.", Toast.LENGTH_LONG).show()
            }
        }

    // --- 블루투스 활성화 요청 런처 ---
    private val requestEnableBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // ... (이전 코드와 동일) ...
            if (result.resultCode == RESULT_OK) {
                Log.d(TAG, "Bluetooth enabled by user.")
            } else {
                Log.e(TAG, "Bluetooth was not enabled.")
                connectionStatus = "블루투스 비활성화됨"
                Toast.makeText(this, "블루투스 활성화가 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }

    // --- Bluetooth Discovery BroadcastReceiver ---
    private val bluetoothDiscoveryReceiver = object : BroadcastReceiver() {
        // ... (이전 코드와 동일, 내부 로직 변경 없음) ...
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    val deviceName = device?.name
                    val deviceAddress = device?.address

                    if (deviceName != null && deviceAddress != null) {
                        Log.d(TAG, "Device found: $deviceName [$deviceAddress]")
                        if (deviceName.equals(TARGET_DEVICE_NAME, ignoreCase = true)) {
                            Log.d(TAG, "Target device '$TARGET_DEVICE_NAME' found!")
                            foundDevice = device
                            connectionStatus = "기기 찾음, 연결 시도 중..."
                            isScanning = false
                            cancelBluetoothDiscovery() // 검색 중단 및 수신기 해제
                            initiateConnectionWithDevice(foundDevice!!)
                        }
                    } else {
                        Log.d(TAG, "Found device with null name or address.")
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Bluetooth discovery finished.")
                    if (isScanning) { // 기기 못 찾고 종료된 경우
                        connectionStatus = "검색 완료 (기기 못 찾음)"
                        Toast.makeText(context, "'$TARGET_DEVICE_NAME' 기기를 찾지 못했습니다.", Toast.LENGTH_LONG).show()
                        isScanning = false
                        unregisterDiscoveryReceiver() // 수신기 해제
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "블루투스를 지원하지 않는 기기입니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            WearApp() // Wear Compose UI 호출
        }

        checkAndRequestPermissions() // 앱 시작 시 권한 확인
    }

    // --- Compose UI 부분 (Wear Compose 적용) ---
    @Composable
    fun WearApp() {
        // *** Wear Compose 테마 적용 ***
        MaterialTheme {
            // ScalingLazyColumn: Wear OS에서 스크롤 및 화면 크기 최적화를 위한 권장 컨테이너
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally, // 아이템 수평 중앙 정렬
                verticalArrangement = Arrangement.spacedBy(
                    8.dp, Alignment.CenterVertically // 아이템 간 간격 및 수직 중앙 정렬
                )
            ) {
                // 상태 텍스트
                item {
                    Text(
                        text = "상태: $connectionStatus",
                        textAlign = TextAlign.Center, // 텍스트 중앙 정렬
                        modifier = Modifier.padding(horizontal = 10.dp) // 좌우 패딩
                    )
                }

                // 스캔 중 텍스트 (조건부 표시)
                if (isScanning) {
                    item {
                        Text(
                            text = "주변 기기 검색 중...",
                            textAlign = TextAlign.Center
                        )
                    }
                    // item { CircularProgressIndicator() } // 필요 시 로딩 인디케이터 추가
                }

                // 찾기 및 연결 버튼
                item {
                    Button(
                        onClick = { findAndConnectDevice() },
                        enabled = !isConnecting && !isScanning,
                        modifier = Modifier.fillMaxWidth(0.8f) // 버튼 너비 화면의 80%
                    ) {
                        Text(text = if (bluetoothSocket?.isConnected == true) "다시 연결/전송" else "찾기 및 연결")
                    }
                }

                // 연결 끊기 버튼
                item {
                    Button(
                        onClick = { disconnect() },
                        enabled = isConnecting || bluetoothSocket?.isConnected == true,
                        modifier = Modifier.fillMaxWidth(0.8f) // 버튼 너비 화면의 80%
                    ) {
                        Text("연결 끊기")
                    }
                }
            } // End of ScalingLazyColumn
        } // End of WearMaterialTheme
    }


    // --- 권한 확인 및 요청 관련 함수 ---
    private fun checkAndRequestPermissions() {
        // ... (이전 코드와 동일) ...
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
            requestMultiplePermissions.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.d(TAG, "All necessary permissions already granted.")
            checkBluetoothEnabled()
        }
    }

    private fun checkPermissions(): Boolean {
        // ... (이전 코드와 동일) ...
        val connectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
        val scanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Consider legacy behavior
        }
        val locationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return connectPermission && scanPermission && locationPermission
    }

    // --- 블루투스 활성화 확인 ---
    private fun checkBluetoothEnabled(): Boolean {
        // ... (이전 코드와 동일) ...
        if (bluetoothAdapter?.isEnabled == false) {
            Log.d(TAG, "Bluetooth is not enabled. Requesting user to enable.")
            connectionStatus = "블루투스 활성화 필요"
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if(ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED){
                    requestEnableBluetooth.launch(enableBtIntent)
                } else {
                    Log.e(TAG, "BLUETOOTH_CONNECT permission needed to request enable Bluetooth.")
                }
            } else {
                if(ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED){
                    requestEnableBluetooth.launch(enableBtIntent)
                } else {
                    Log.e(TAG, "BLUETOOTH permission needed to request enable Bluetooth.")
                }
            }
            return false
        }
        Log.d(TAG, "Bluetooth is enabled.")
        return true
    }

    // --- 기기 찾기 및 연결 시작 함수 ---
    private fun findAndConnectDevice() {
        // ... (이전 코드와 동일) ...
        if (!checkPermissions()) {
            Log.e(TAG, "Cannot proceed without required permissions.")
            connectionStatus = "권한 없음"
            checkAndRequestPermissions() // 권한 재요청
            return
        }
        if (!checkBluetoothEnabled()) {
            return
        }
        if (isConnecting || isScanning) return

        disconnect()
        foundDevice = null
        connectionStatus = "기기 찾는 중..."

        lifecycleScope.launch(Dispatchers.IO) {
            val pairedDevice = findDeviceInPairedList(TARGET_DEVICE_NAME)
            if (pairedDevice != null) {
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Device found in paired list.")
                    connectionStatus = "페어링된 기기 찾음, 연결 시도..."
                    foundDevice = pairedDevice
                    initiateConnectionWithDevice(foundDevice!!)
                }
            } else {
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Device not in paired list. Starting discovery...")
                    connectionStatus = "주변 기기 검색 시작..."
                    startBluetoothDiscovery()
                }
            }
        }
    }

    // --- 페어링된 기기 목록에서 이름으로 검색 ---
    @SuppressLint("MissingPermission")
    private fun findDeviceInPairedList(targetName: String): BluetoothDevice? {
        // ... (이전 코드와 동일) ...
        if (!checkPermissions()) return null
        return try {
            bluetoothAdapter?.bondedDevices?.find { device ->
                device.name?.equals(targetName, ignoreCase = true) == true
            }
        } catch (e: SecurityException) { null }
    }

    // --- 주변 기기 검색 시작 ---
    @SuppressLint("MissingPermission")
    private fun startBluetoothDiscovery() {
        // ... (이전 코드와 동일) ...
        if (!checkPermissions()) {
            connectionStatus = "권한 부족 (스캔/위치)"
            return
        }
        if (bluetoothAdapter?.isDiscovering == true) return

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        try {
            ContextCompat.registerReceiver(this, bluetoothDiscoveryReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            Log.d(TAG, "Discovery receiver registered.")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering receiver: ${e.message}", e)
            connectionStatus = "수신기 등록 오류"
            return
        }

        val discoveryStarted = bluetoothAdapter?.startDiscovery() ?: false
        if (discoveryStarted) {
            isScanning = true
            connectionStatus = "주변 기기 검색 중..."
        } else {
            Log.e(TAG, "Failed to start Bluetooth discovery.")
            connectionStatus = "검색 시작 실패"
            isScanning = false
            unregisterDiscoveryReceiver()
        }
    }

    // --- 주변 기기 검색 취소 ---
    @SuppressLint("MissingPermission")
    private fun cancelBluetoothDiscovery() {
        // ... (이전 코드와 동일) ...
        if (bluetoothAdapter?.isDiscovering == true) {
            if (!checkPermissions()) return // 권한 확인 필수
            try {
                bluetoothAdapter?.cancelDiscovery()
                Log.d(TAG, "Bluetooth discovery cancelled.")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException on cancelDiscovery.", e)
            }
        }
        isScanning = false
        unregisterDiscoveryReceiver()
    }

    // --- 수신기 등록 해제 ---
    private fun unregisterDiscoveryReceiver() {
        // ... (이전 코드와 동일) ...
        try {
            unregisterReceiver(bluetoothDiscoveryReceiver)
            Log.d(TAG, "Discovery receiver unregistered.")
        } catch (e: IllegalArgumentException) {
            // Ignore
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}", e)
        }
    }

    // --- 특정 기기와 연결 시도 ---
    @SuppressLint("MissingPermission")
    private fun initiateConnectionWithDevice(device: BluetoothDevice) {
        // ... (이전 코드와 동일, 내부 로직 변경 없음) ...
        if (!checkPermissions()) {
            connectionStatus = "권한 없음 (연결)"
            return
        }
        if (isConnecting) return
        isConnecting = true
        connectionStatus = "연결 시도 중..."
        connectionJob?.cancel()

        connectionJob = lifecycleScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Attempting to connect to ${device.name ?: "Unknown"} [${device.address}]")
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                bluetoothSocket?.connect()
                outputStream = bluetoothSocket?.outputStream
                Log.d(TAG, "Successfully connected to ${device.name ?: "Unknown"}")
                withContext(Dispatchers.Main) {
                    connectionStatus = "연결 성공"
                    isConnecting = false
                    Toast.makeText(this@MainActivity, "연결 성공!", Toast.LENGTH_SHORT).show()
                }
                sendData("Hello from Wear OS!")
            } catch (e: Exception) { // Catch generic exception for simplicity here
                Log.e(TAG, "Exception during connection: ${e.message}", e)
                val errorMsg = when(e){
                    is IOException -> "IO 오류"
                    is SecurityException -> "권한 오류"
                    else -> "알 수 없는 오류"
                }
                withContext(Dispatchers.Main) {
                    connectionStatus = "연결 실패: $errorMsg"
                    Toast.makeText(this@MainActivity, "연결 실패", Toast.LENGTH_SHORT).show()
                }
                closeSocket()
            } finally {
                withContext(Dispatchers.Main) { isConnecting = false }
            }
        }
    }

    // --- 데이터 전송 ---
    private fun sendData(message: String) {
        // ... (이전 코드와 동일) ...
        if (outputStream == null || bluetoothSocket?.isConnected != true) {
            Log.e(TAG, "Cannot send data: Not connected.")
            return
        }
        val msgBuffer: ByteArray = message.toByteArray()
        Log.d(TAG, "Sending data: $message")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                outputStream?.write(msgBuffer)
                outputStream?.flush()
                Log.d(TAG, "Data sent successfully.")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity,"데이터 전송 성공", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                Log.e(TAG, "IOException during sending data: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    connectionStatus = "전송 실패"
                }
                closeSocket()
            }
        }
    }

    // --- 연결 끊기 및 리소스 정리 ---
    private fun disconnect() {
        // ... (이전 코드와 동일) ...
        cancelBluetoothDiscovery()
        connectionJob?.cancel()
        closeSocket()
        foundDevice = null
        isConnecting = false
        isScanning = false
        // UI update needs to happen on main thread if called from background
        lifecycleScope.launch(Dispatchers.Main) {
            connectionStatus = "연결 끊김"
        }
        Log.d(TAG, "Disconnected.")
    }

    // --- 소켓 닫기 ---
    private fun closeSocket() {
        // ... (이전 코드와 동일) ...
        try {
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            // Ignore
        } finally {
            outputStream = null
            bluetoothSocket = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect() // 앱 종료 시 확실하게 정리
        Log.d(TAG, "onDestroy called, resources cleaned up.")
    }
}

// --- Jetpack Compose Previews ---

// 기본 상태 (앱 초기 실행 시) 미리보기
@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    // MainActivity의 WearApp 함수를 호출하여 미리보기를 생성합니다.
    // 하지만 실제 상태(connectionStatus 등)는 Activity의 것을 사용하므로,
    // Preview에서는 초기 상태만 보이거나, 상태를 고정하는 별도 Composable을 만드는 것이 좋습니다.
    // 여기서는 WearApp의 레이아웃 구조를 재현하여 고정된 값으로 보여주는 방식을 사용합니다.

    MaterialTheme {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            // --- 미리보기용 고정 상태 값 ---
            val previewStatus = "대기 중"
            val previewIsScanning = false
            val previewIsConnecting = false
            val previewIsConnected = false
            // ---

            item {
                Text(
                    text = "상태: $previewStatus",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }

            if (previewIsScanning) {
                item { Text(text = "주변 기기 검색 중...", textAlign = TextAlign.Center) }
            }

            item {
                Button(
                    onClick = { /* Preview에서는 동작 없음 */ },
                    enabled = !previewIsConnecting && !previewIsScanning,
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text(text = if (previewIsConnected) "다시 연결/전송" else "찾기 및 연결")
                }
            }

            item {
                Button(
                    onClick = { /* Preview에서는 동작 없음 */ },
                    enabled = previewIsConnecting || previewIsConnected,
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("연결 끊기")
                }
            }
        }
    }
}

// 검색 중 상태 미리보기
@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true)
@Composable
fun ScanningPreview() {
    MaterialTheme {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            // --- 미리보기용 고정 상태 값 ---
            val previewStatus = "주변 기기 검색 중..."
            val previewIsScanning = true
            // ---

            item {
                Text(
                    text = "상태: $previewStatus",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }

            item { Text(text = "주변 기기 검색 중...", textAlign = TextAlign.Center) }

            item {
                Button( // 검색 중에는 버튼 비활성화
                    onClick = { /* No action */ },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text(text = "찾기 및 연결")
                }
            }

            item {
                Button( // 검색 중에는 버튼 비활성화
                    onClick = { /* No action */ },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("연결 끊기")
                }
            }
        }
    }
}

// 연결 성공 상태 미리보기
@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true)
@Composable
fun ConnectedPreview() {
    MaterialTheme {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            // --- 미리보기용 고정 상태 값 ---
            val previewStatus = "연결 성공"
            val previewIsScanning = false
            val previewIsConnecting = false
            val previewIsConnected = true // 연결된 상태
            // ---

            item {
                Text(
                    text = "상태: $previewStatus",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }

            // 스캔 중 텍스트 없음

            item {
                Button(
                    onClick = { /* No action */ },
                    enabled = true, // 연결 후 활성화
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text(text = "다시 연결/전송") // 연결 후 텍스트 변경
                }
            }

            item {
                Button(
                    onClick = { /* No action */ },
                    enabled = true, // 연결 후 활성화
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("연결 끊기")
                }
            }
        }
    }
}