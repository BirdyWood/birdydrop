package io.github.birdywood.birdydrop.ui

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.birdywood.birdydrop.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirdyDropReceiver(
    nameDevice: String = "John Doe",
    textData: String = "Un texte",
    onDismiss: () -> Unit, // Renamed for clarity in bottom sheet context
    onDecline: () -> Unit,
    onAccept: () -> Unit
) {
    val sheetState = if (LocalInspectionMode.current){
        rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)
    }else {
        rememberModalBottomSheetState(skipPartiallyExpanded = true)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {

        Column(
            modifier = Modifier.padding(24.dp, 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Optional: Add a drag handle if you want the standard one
            // Box(modifier = Modifier.padding(bottom = 16.dp)) {
            //     BottomSheetDefaults.DragHandle()
            // }

            Text(
                text = nameDevice,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "souhaite partager un texte avec vous.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Image Preview
            /*Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min=50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Text(textData)
            }*/
            val clipboardManager = LocalClipboard.current
            val ctx = LocalContext.current
            val scope = rememberCoroutineScope()
            OutlinedTextField(
                value = textData,
                onValueChange = { /* Faire quelque chose avec le texte */ },
                readOnly = true,
                enabled = false,
                trailingIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            val clipData = ClipData.newPlainText("plain text", textData)
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
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        onDecline()
                        onDismiss() // Dismiss after action
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Refuser")
                }
                Button(
                    onClick = {
                        onAccept()
                        onDismiss() // Dismiss after action
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Accepter")
                }
            }
            Spacer(
                modifier = Modifier.height(
                    WindowInsets.systemBars
                        .asPaddingValues()
                        .calculateBottomPadding()
                )
            ) // Add some padding at the very bottom
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun BirdyDropReceiverPreview() {
    var showSheet by remember { mutableStateOf(true) }
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) { // Simulate a screen background
            /*Image(
                Icons.Outlined.Email,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )*/
            if (showSheet) {
                BirdyDropReceiver(
                    onDismiss = { showSheet = false },
                    onDecline = { /*TODO*/ },
                    onAccept = { /*TODO*/ }
                )
            }
        }
    }
}