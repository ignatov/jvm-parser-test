import com.intellij.core.CoreApplicationEnvironment
import com.intellij.core.CoreProjectEnvironment
import com.intellij.lang.MetaLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.local.CoreLocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.psi.SingleRootFileViewProvider
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.script.ScriptDefinitionProvider
import java.io.File

internal data class PsiSetup(
        val applicationEnvironment: CoreApplicationEnvironment,
        val projectEnvironment: CoreProjectEnvironment,
        val project: Project,
        val disposable: Disposable
)

internal fun setup(): PsiSetup {
    val disposable = Disposer.newDisposable()

    val applicationEnvironment = CoreApplicationEnvironment(disposable, false)

    val projectEnvironment = CoreProjectEnvironment(disposable, applicationEnvironment)

    CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), MetaLanguage.EP_NAME, MetaLanguage::class.java)

    applicationEnvironment.registerApplicationService(ScriptDefinitionProvider::class.java, NoopScriptDefinitionProvider())
    applicationEnvironment.registerFileType(KotlinFileType.INSTANCE, "kt")
    applicationEnvironment.registerParserDefinition(KotlinParserDefinition())


    val project = projectEnvironment.project
    return PsiSetup(applicationEnvironment, projectEnvironment, project, disposable)
}

internal inline fun <T> withPsiSetup(l: PsiSetup.() -> T): T {
    val setup = setup()
    val t = setup.l()
    Disposer.dispose(setup.disposable)
    return t
}

private class NoopScriptDefinitionProvider : ScriptDefinitionProvider {
    override fun isScript(fileName: String): Boolean {
        return false
    }

    override fun findScriptDefinition(fileName: String): KotlinScriptDefinition? {
        return null
    }
}

fun main(args: Array<String>) {
    withPsiSetup {
        val psiManager = PsiManager.getInstance(project)
        val vfm = VirtualFileManager.getInstance()

        val io = File("src/main.kt")
        val vFile = (vfm.getFileSystem(StandardFileSystems.FILE_PROTOCOL) as CoreLocalFileSystem).findFileByIoFile(io)
        val file = SingleRootFileViewProvider(psiManager, vFile!!).allFiles.filterIsInstance<KtFile>().first()

        file.acceptChildren(object : KtVisitorVoid() {
            override fun visitClass(klass: KtClass) {
                println(klass.name)
                super.visitClass(klass)
            }
        })
    }
}