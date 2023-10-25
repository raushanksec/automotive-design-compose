package com.android.designcompose
import com.android.designcompose.DesignSwitcherDoc

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziRule
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@Composable
fun DesignSwitcherDeadbeef() {
    DesignSwitcher(doc = null, currentDocId = "DEADBEEF", branchHash = null, setDocId = {})
}

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = RobolectricDeviceQualifiers.MediumTablet, sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TestDesignSwitcher {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val roborazziRule =
        RoborazziRule(
            composeRule = composeTestRule,
            // Specify the node to capture for the last image
            captureRoot = composeTestRule.onRoot(),
            options =
                RoborazziRule.Options(
                    outputDirectoryPath = "src/testDebug/roborazzi",
                    // Always capture the last image of the test
                    captureType = RoborazziRule.CaptureType.LastImage()
                )
        )

    @Test
    fun testInitialLoad() {
        with(composeTestRule) {
            setContent { DesignSwitcherDeadbeef() }
            onNode(SemanticsMatcher.expectValue(docClassSemanticsKey, DesignSwitcherDoc.javaClass.name))
                .assert(
                    SemanticsMatcher.expectValue(
                        docRenderStatusSemanticsKey,
                        DocRenderStatus.Rendered
                    )
                )
        }
    }
}
