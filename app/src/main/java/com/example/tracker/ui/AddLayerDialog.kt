package com.example.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tracker.model.LayerType
import com.example.ui.theme.*

@Composable
fun AddLayerDialog(
    onDismiss: () -> Unit,
    onAddLayer: (type: LayerType, name: String, text: String, colorHex: Long) -> Unit
) {
    var selectedType by remember { mutableStateOf(LayerType.HUD_CALLOUT) }
    var layerName by remember { mutableStateOf("") }
    var textContent by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableStateOf(0xFF00F0FFL) }

    val colorOptions = listOf(
        0xFF00F0FFL, // Cyan
        0xFFF59E0BL, // Amber
        0xFFF43F5EL, // Crimson
        0xFF10B981L, // Emerald
        0xFF8B5CF6L, // Violet
        0xFF38BDF8L  // Sky Blue
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AeSlate900,
        title = {
            Text(
                text = "ATTACH VFX LAYER",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = CyanNeon
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Select Layer Type:", fontSize = 11.sp, color = AeSlate400)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LayerType.values().forEach { type ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedType == type) AeSlate700 else AeSlate850
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedType = type
                                    if (layerName.isEmpty()) layerName = type.title
                                    if (textContent.isEmpty()) textContent = type.defaultLabel
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedType == type,
                                    onClick = { selectedType = type },
                                    colors = RadioButtonDefaults.colors(selectedColor = CyanNeon)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(type.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(type.defaultLabel, fontSize = 10.sp, color = AeSlate400)
                                }
                            }
                        }
                    }
                }

                // Custom Label
                OutlinedTextField(
                    value = textContent,
                    onValueChange = { textContent = it },
                    label = { Text("Display Tag / Content", fontSize = 10.sp) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White),
                    modifier = Modifier.fillMaxWidth()
                )

                // Color selection
                Text("Accent Glow Color:", fontSize = 11.sp, color = AeSlate400)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(colorOptions) { hex ->
                        val isSel = selectedColorHex == hex
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .clickable { selectedColorHex = hex }
                                .border(
                                    width = if (isSel) 3.dp else 0.dp,
                                    color = if (isSel) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAddLayer(selectedType, layerName, textContent, selectedColorHex)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = AeSlate950),
                modifier = Modifier.testTag("confirm_add_layer_button")
            ) {
                Text("Attach Layer", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AeSlate400)
            }
        }
    )
}
