package com.android.designcompose.testapp.validation.benchmarks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.TabRowDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.designcompose.testapp.validation.TestButton
import com.android.designcompose.testapp.validation.TestButtons
import com.android.designcompose.testapp.validation.TestContent
import com.android.designcompose.testapp.validation.examples.BattleshipDoc
import com.android.designcompose.testapp.validation.examples.BattleshipTest
import com.android.designcompose.testapp.validation.examples.EXAMPLES
import com.android.designcompose.testapp.validation.examples.HelloWorld
import com.android.designcompose.testapp.validation.examples.HelloWorldDoc

class Battleship : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val index = remember { mutableStateOf(0) }
            Row {
                BattleshipButtons(index)
                TabRowDefaults.Divider(color = Color.Black, modifier = Modifier.fillMaxHeight().width(1.dp))
                BattleshipContent(index.value)
            }
        }
    }
}

val BattleshipList: ArrayList<Triple<String, @Composable () -> Unit, String?>> =
    arrayListOf(
        Triple("Battleship", { BattleshipTest() }, BattleshipDoc.javaClass.name),
        Triple("Hello", { HelloWorld() }, HelloWorldDoc.javaClass.name),
    )

// Draw the content for the current test
@Composable
fun BattleshipContent(index: Int) {
    Box { BattleshipList[index].second() }
}
// Draw all the buttons on the left side of the screen, one for each test
@Composable
fun BattleshipButtons(index: MutableState<Int>) {

    Column(Modifier.width(110.dp).verticalScroll(rememberScrollState())) {
        var count = 0
        BattleshipList.forEach {
            TestButton(it.first, index, count)
            TabRowDefaults.Divider(color = Color.Black, modifier = Modifier.height(1.dp))
            ++count
        }
    }
}