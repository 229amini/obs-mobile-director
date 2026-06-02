package com.mostafa229.obsmobiledirector.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun StreamTargetCard() {
    var host by remember { mutableStateOf("192.168.1.9") }
    var port by remember { mutableStateOf("9001") }
    var stabilizationEnabled by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("SRT target")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = host,
                onValueChange = { host = it },
                label = { Text("OBS PC IP") },
                singleLine = true
            )
            OutlinedTextField(
                modifier = Modifier.weight(0.55f),
                value = port,
                onValueChange = { port = it },
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Use hardware stabilization when supported")
            Switch(
                checked = stabilizationEnabled,
                onCheckedChange = { stabilizationEnabled = it }
            )
        }
    }
}

