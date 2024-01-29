/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.designcompose.testapp.validation.examples

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.designcompose.Meter
import com.android.designcompose.annotation.Design
import com.android.designcompose.annotation.DesignComponent
import com.android.designcompose.annotation.DesignDoc
import com.android.designcompose.annotation.DesignVariant

// TEST Basic Hello World example
@DesignDoc(id = "pxVlixodJqZL95zo2RzTHl")
interface HelloWorld {
    @DesignComponent(node = "#MainFrame") fun Main(@Design(node = "#Name") name: String)
}



// Logically grouped variants
enum class Panel {
    welcome,
    power,
    speedo,
    media,
    phone,
    startroute, // Map showing initial route with different stops
    nav,        // Map showing navigation
}

enum class App {
    har,
    android,
}

enum class AndroidState {
    on,
    off,
}

enum class PhoneCallState {
    incall,
    incoming,
}

enum class Panel1 {
    welcome,
    speedo,
    nav,
}

enum class Panel2 {
    startroute,
    phone,
    media,
    power,
    nav,
}

enum class Panel3 {
    compass,
    speedo,
    nav,
}

enum class CarView {
    parked,
    driving,
}

enum class ViewMode {
    normal,
    sport,
    charging,
    reverse,
}

enum class Notification {
    none,
    callanswer,
    callincoming,
    warning,
    emergency,
    autopilot,
    wiper,
}

val textSize = 20.sp

@Composable
@OptIn(ExperimentalMaterialApi::class)
internal inline fun <reified T : Enum<T>> LabelledDropDown(
    label: String,
    state: T,
    crossinline onStateChange: (T) -> Unit
) {
    var dropdownState by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = dropdownState,
        onExpandedChange = { dropdownState = !dropdownState },
        modifier = Modifier.width(200.dp),
    ) {
        TextField(
            value = state.toString(),
            readOnly = true,
            onValueChange = {},
            label = { Text(label, fontSize = textSize) },
            textStyle = TextStyle(fontSize = textSize),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownState) },
        )
        ExposedDropdownMenu(
            expanded = dropdownState,
            onDismissRequest = { dropdownState = false }
        ) {
            for (entry in enumValues<T>()) {
                DropdownMenuItem(
                    onClick = {
                        onStateChange(entry)
                        dropdownState = false
                    }
                ) {
                    Text(entry.toString(), fontSize = textSize)
                }
            }
        }
    }
}

@Composable
internal fun LabelledToggle(label: String, value: Boolean, onStateChange: (Boolean) -> Unit) {
    Row(
        Modifier.clickable(enabled = true) { onStateChange(!value) },
    ) {
        Text(label, fontSize = textSize)
        Switch(
            checked = value,
            onCheckedChange = { onStateChange(it) },
            modifier = Modifier.offset((3).dp)
        )
    }
}

@Composable
internal fun SliderControl(
    label: String,
    stateVar: Float,
    onValueChange: (Float) -> Unit,
    topValue: Float, // I should decide on Ints or Floats........
    minValue: Float = 0f
) {
    Row(modifier = Modifier.border(width = 2.dp, color = Color.Gray)) {
        Text(label, Modifier.width(120.dp), color = Color.Black, fontSize = textSize)
        androidx.compose.material.Slider(
            value = stateVar,
            modifier = Modifier.width(120.dp),
            valueRange = minValue..topValue,
            onValueChange = { onValueChange(it) }
        )
    }
}

// Galaxy E82Od5Wfu2xuqVLyOjQV7Z
// Future SB6ME0eT2ku8rFT7Y3eBX8
@DesignDoc(id = "E82Od5Wfu2xuqVLyOjQV7Z") // "4R7AzW9Ch1vfneEpIodko7")
interface Cluster {
    @DesignComponent(node = "#HAR-stage")
    fun HarMain(
        // Whether Android is on or off.
        @DesignVariant(property = "#heartbeat") androidState: AndroidState,
        // The current view mode
        @DesignVariant(property = "#view-mode") viewMode: ViewMode,
        @DesignVariant(property = "#panel-1") leftPanel: Panel1,
        @DesignVariant(property = "#panel-2") rightPanel: Panel2,
        @DesignVariant(property = "#panel-3") panel3: Panel3,
        @DesignVariant(property = "#carv-iew") carView: CarView,
        @DesignVariant(property = "#driving/notification") notification: Notification,
        @DesignVariant(property = "#phone/state") phoneState: PhoneCallState,
    )
    @DesignComponent(node = "#android-stage")
    fun AndroidMain(
        // The app (HAR or Android) that is rendering this. Must be set to "android"
        @DesignVariant(property = "#heartbeat") androidState: AndroidState,
        // The current view mode
        @DesignVariant(property = "#view-mode") viewMode: ViewMode,
        @DesignVariant(property = "#panel-1") leftPanel: Panel1,
        @DesignVariant(property = "#panel-2") rightPanel: Panel2,
        @DesignVariant(property = "#panel-3") panel3: Panel3,
        @DesignVariant(property = "#car-view") carView: CarView,
        @DesignVariant(property = "#driving/notification") notification: Notification,
        @DesignVariant(property = "#phone/state") phoneState: PhoneCallState,
        @Design(node = "#driving/rpm-gauge") rpmGauge: Meter,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HelloWorld() {
    val androidState = remember { mutableStateOf(AndroidState.on) }
    val viewMode = remember { mutableStateOf(ViewMode.normal) }
    val leftPanel = remember { mutableStateOf(Panel1.welcome) }
    val rightPanel = remember { mutableStateOf(Panel2.startroute) }
    val panel3 = remember { mutableStateOf(Panel3.compass) }
    val carView = remember { mutableStateOf(CarView.parked) }
    val notification = remember { mutableStateOf(Notification.none) }
    val phoneState = remember { mutableStateOf(PhoneCallState.incall)}

    if (androidState.value == AndroidState.on)
        ClusterDoc.AndroidMain(
            androidState = AndroidState.on,
            viewMode = viewMode.value,
            leftPanel = leftPanel.value,
            rightPanel = rightPanel.value,
            panel3 = panel3.value,
            carView = carView.value,
            notification = notification.value,
            phoneState = phoneState.value,
            rpmGauge = 75F,
        )

    ClusterDoc.HarMain(
        androidState = androidState.value,
        viewMode = viewMode.value,
        leftPanel = leftPanel.value,
        rightPanel = rightPanel.value,
        panel3 = panel3.value,
        carView = carView.value,
        notification = notification.value,
        phoneState = phoneState.value,
    )

    /* Manual Controls */
    Column(
        modifier = Modifier.offset(10.dp, 800.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabelledToggle("Android On", androidState.value == AndroidState.on) {
                androidState.value = if (it) AndroidState.on else AndroidState.off
            }
            LabelledDropDown(
                "ViewMode:",
                viewMode.value,
                onStateChange = { viewMode.value = it }
            )
            LabelledDropDown(
                "LeftPanel:",
                leftPanel.value,
                onStateChange = { leftPanel.value = it }
            )
            LabelledDropDown(
                "RightPanel:",
                rightPanel.value,
                onStateChange = { rightPanel.value = it }
            )
            LabelledDropDown(
                "Panel3:",
                panel3.value,
                onStateChange = { panel3.value = it }
            )
            LabelledDropDown(
                "CarView:",
                carView.value,
                onStateChange = { carView.value = it }
            )
            LabelledDropDown(
                "Notification:",
                notification.value,
                onStateChange = { notification.value = it }
            )
            LabelledDropDown(
                "Phone:",
                phoneState.value,
                onStateChange = { phoneState.value = it }
            )
        }
    }
    /*
    HelloWorldDoc.Main(
        name = "World",
        designComposeCallbacks =
            DesignComposeCallbacks(
                docReadyCallback = { id ->
                    Log.i("DesignCompose", "HelloWorld Ready: doc ID = $id")
                },
                newDocDataCallback = { docId, data ->
                    Log.i(
                        "DesignCompose",
                        "HelloWorld Updated doc ID $docId: ${data?.size ?: 0} bytes"
                    )
                },
            )
    )
    */
}
