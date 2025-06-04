package io.github.birdywood.birdydrop.ui

import android.content.ClipData
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.birdywood.birdydrop.R
import io.github.birdywood.birdydrop.core.DeviceBluetooth
import io.github.birdywood.birdydrop.ApiBirdydrop
import io.github.birdywood.birdydrop.utils.ColorGenerator
import io.github.birdywood.birdydrop.utils.BirdydropUpdateListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

data class NearbyDevice(val name: MutableState<String>, val device: DeviceBluetooth)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BirdyDrop(api: ApiBirdydrop, receiveMessage: (String) -> Unit) {
    // This would typically be controlled by a ViewModel or some state outsie
    // For this example, we'll just simulate it being shown.

    val sheetState = if (LocalInspectionMode.current) {
        rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true
        )
    } else {
        rememberModalBottomSheetState(skipPartiallyExpanded = true)
    }
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) } // Start with it shown for preview

    var devicesList = remember { mutableStateOf(listOf<DeviceBluetooth>()) }
    var textToSend by remember { mutableStateOf("") }


    var showReceiveMessage by remember { mutableStateOf(false) }
    var objectReceived by remember { mutableStateOf(JSONObject()) }

    LaunchedEffect(Unit) {
        scope.launch {
            Log.d("BirdyDropSender", "LaunchedEffect")
            while (api.bDropServiceInstance == null) {
                delay(10)
            }

            api.boundSheet(object :
                BirdydropUpdateListener {

                override fun onNewDevice(devices: List<DeviceBluetooth>) {
                    Log.d("BirdyDropSender", devices.toString())
                    devicesList.value =
                        devices.sortedByDescending { it.lastSeen }.sortedBy { it.isConnected }
                            .filter { it.isConnected != DeviceBluetooth.IsConnected.NO }
                            .distinctBy { it.ip }
                }

                override fun sendMessage(msg: String) {
                    Log.d("BirdyDropSender", "Message: $msg")
                    textToSend = msg
                    if (!showReceiveMessage)
                        showBottomSheet = true
                }

                override fun onReceiveInfo(json: JSONObject) {
                    Log.d("BirdyDropSender", "Message: ${json.getString("msg")}")
                    objectReceived = json
                    showBottomSheet = false
                    showReceiveMessage = true
                }

            })
            api.bDropServiceInstance?.ble?.sendPing()
        }
    }
    val SLIDE_DURATION_MS = 500;

    val closingReceiver = {
        showReceiveMessage = false
    }
    AnimatedVisibility(
        visible = showReceiveMessage,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(SLIDE_DURATION_MS)
        ),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(SLIDE_DURATION_MS))
    ) {
        BirdyDropReceiver(
            nameDevice = objectReceived.getString("name"),
            textData = objectReceived.getString("msg"),
            onDismiss = { closingReceiver() },
            onDecline = { },
            onAccept = { receiveMessage(objectReceived.getString("msg")) }
        )
    }

    val closingSender = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                showBottomSheet = false
            }
        }
    }
    AnimatedVisibility(
        visible = showBottomSheet,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(SLIDE_DURATION_MS)
        ),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(SLIDE_DURATION_MS))
    ) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            // You can customize the drag handle if needed
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            // Content of the Bottom Sheet (Our AirDrop UI)
            BirdyDropSheetContent(
                devicesList.value.map { NearbyDevice(mutableStateOf(it.name), it) },
                api.bDropServiceInstance?.ble?.getLocalBluetoothName() ?: "",
                textToSend,
                onCancel = {
                    closingSender()
                },
                refresh = {
                    devicesList.value = listOf()
                    api.bDropServiceInstance?.ble?.sendPing()
                },
                onSend = { uid ->
                    closingSender()
                    api.bDropServiceInstance?.ble?.send(
                        uid,
                        textToSend.toByteArray()
                    )
                })
        }
    }
}

@Composable
fun BirdyDropSheetContent(
    devicesList: List<NearbyDevice>,
    nameDevice: String,
    textToSend: String = "",
    onCancel: () -> Unit,
    refresh: () -> Unit = {},
    onSend: (String) -> Unit
) {
// Le contenu de notre Bottom Sheet
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                bottom = WindowInsets.systemBars
                    .asPaddingValues()
                    .calculateBottomPadding()
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val selectedDevices = remember { mutableStateListOf<String>() }



        BackHandler() {
            selectedDevices.clear()
        }

        // L'icône de lien et le bouton de fermeture
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .offset(y = (-24).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.module_name),
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Bouton de fermeture en haut à droite
            IconButton(onClick = onCancel) {
                Icon(
                    painter = painterResource(R.drawable.rounded_close_24),
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        //Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Share with Friends",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        val clipboardManager = LocalClipboard.current
        val ctx = LocalContext.current

        // Champ de texte pour le lien
        val scope = rememberCoroutineScope()
        OutlinedTextField(
            value = textToSend,
            onValueChange = { /* Faire quelque chose avec le texte */ },
            readOnly = true,
            enabled = false,
            trailingIcon = {
                IconButton(onClick = {
                    scope.launch {
                        val clipData = ClipData.newPlainText("plain text", textToSend)
                        val clipEntry = ClipEntry(clipData)
                        clipboardManager.setClipEntry(clipEntry)
                        Toast.makeText(ctx, "Texte copié", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_content_copy_24),
                        contentDescription = "Copy link",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Section "Share to contacts"
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Nearby devices",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier


            )
            /*Button(onClick = { refresh() }){
                Text(text = "Refresh")
            }*/
            IconButton(onClick = { selectedDevices.clear();refresh() }) {
                Icon(
                    painter = painterResource(R.drawable.rounded_refresh_24),
                    contentDescription = "Refresh",
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Crossfade(targetState = devicesList.isEmpty(), animationSpec = tween(500)) { isEmpty ->
            if (isEmpty) {
                Text(
                    text = "Aucun appareil détecté pour le moment...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 96.dp), // Colonnes adaptatives
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 250.dp) // Hauteur max pour la grille
                        .padding(horizontal = 24.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), // Espace horizontal
                    verticalArrangement = Arrangement.spacedBy(8.dp) // Espace vertical
                ) {
                    items(devicesList, key = { it.name }) { device ->
                        NearbyDeviceItem(
                            name = device.name.value,
                            device = device.device,
                            isSelected = device.device.uid in selectedDevices,
                            onToggle = { isChecked ->
                                if (isChecked) {
                                    selectedDevices.add(device.device.uid)
                                } else {
                                    selectedDevices.remove(device.device.uid)
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Bouton "Send"
        Button(
            onClick = {
                for (uid in selectedDevices) {
                    onSend(uid)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            enabled = selectedDevices.size != 0
        ) {
            Text(
                text = "Send",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        //Spacer(modifier = Modifier.height(24.dp)) // Espace en bas du Bottom Sheet
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NearbyDeviceItem(
    name: String,
    device: DeviceBluetooth,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val deviceColor = ColorGenerator.getColorForName(name)
    val deviceBrush = ColorGenerator.generateDynamicGradientForName(name)
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val isOnline = device.isConnected == DeviceBluetooth.IsConnected.YES
    val backgroundColor = if (isOnline) deviceBrush else Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant
        )
    )

    Column(
        modifier = Modifier
            .size(96.dp) // Taille fixe pour le rond
            .clip(RoundedCornerShape(16.dp)) // Coins légèrement arrondis pour la pastille entière
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(enabled = isOnline) { onToggle(!isSelected) }
            .padding(4.dp), // Padding interne
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Le rond coloré
        Box(
            modifier = Modifier
                .size(56.dp) // Taille du rond
                .clip(CircleShape)
                .background(backgroundColor)
                .border(
                    width = 2.dp,
                    color = if (isOnline) deviceColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = 0.4f
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (device.isConnected == DeviceBluetooth.IsConnected.IN_PROGRESS) {
                LoadingIndicator(
                    modifier = Modifier
                        .padding(4.dp)
                        .fillMaxSize()
                        .align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // Optionnel: ajouter un icône ou la première lettre du nom du device ici
            else if (!isOnline) {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_close_24),
                    contentDescription = "Hors ligne",
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(32.dp)
                )
            }

        }
        Spacer(Modifier.height(4.dp))
        // Nom du device
        Text(
            text = name.split(" ")
                .first(), // Affiche juste le premier mot du nom pour la pastille
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        // Indicateur en ligne/hors ligne si nécessaire (peut être omis si la couleur parle d'elle-même)
        Text(
            text = if (isOnline) "En ligne" else if (device.isConnected == DeviceBluetooth.IsConnected.IN_PROGRESS) "Connexion..." else "Hors ligne",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}


@Preview(showBackground = true)
@Composable
fun BirdyDropSenderPreview() {
    MaterialTheme {
        // Simulate the bottom sheet being shown for the preview
        // In a real app, you'd have a parent Composable managing the sheet state
        Box(modifier = Modifier.fillMaxSize()) { // Wrap in a Box to simulate screen context
            BirdyDrop(
                ApiBirdydrop(LocalContext.current, object : BirdydropUpdateListener {})
            ) {}
        }
    }
}

@Preview(showBackground = true, name = "Sheet Content Only")
@Composable
fun BirdyDropSheetContentPreview() {
    MaterialTheme {
        BirdyDropSheetContent(
            devicesList = mutableListOf(),
            nameDevice = "Device Name",
            onCancel = {},
            onSend = {}
        )
    }
}