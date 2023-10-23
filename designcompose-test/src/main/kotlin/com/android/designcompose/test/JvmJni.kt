import com.android.designcompose.JniInterface

internal object JvmJni: JniInterface() {

    init{
        println(javaClass.getResource("libjni.so")?.readText())


    }
}