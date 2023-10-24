package com.android.designcompose

import androidx.annotation.Keep
import androidx.annotation.VisibleForTesting
import com.android.designcompose.common.JniLoader

// HTTP Proxy configuration.
data class HttpProxyConfig(val proxySpec: String)

// Proxy configuration.
//
// Only HTTP proxy supported.
class ProxyConfig {
    var httpProxyConfig: HttpProxyConfig? = null
}

@Keep
internal class TextSize(
    var width: Float = 0F,
    var height: Float = 0F,
)


// Can't be an interface because interfaces don't allow external functions
internal object Jni {

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    external fun jniFetchDoc(
        docId: String,
        requestJson: String,
        proxyConfig: ProxyConfig
    ): ByteArray

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    external fun jniGetLayout(layoutId: Int): ByteArray?

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    external fun jniSetNodeSize(layoutId: Int, width: Int, height: Int): ByteArray?

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    external fun jniAddNode(
        layoutId: Int,
        parentLayoutId: Int,
        childIndex: Int,
        serializedView: ByteArray,
        serializedBaseView: ByteArray,
        computeLayout: Boolean
    ): ByteArray?

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    external fun jniAddTextNode(
        layoutId: Int,
        parentLayoutId: Int,
        childIndex: Int,
        serializedView: ByteArray,
        computeLayout: Boolean
    ): ByteArray?

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    external fun jniRemoveNode(layoutId: Int, computeLayout: Boolean): ByteArray?

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    external fun jniComputeLayout(): ByteArray?

    init{
        JniLoader.loadDefaultImpl()
    }
}
