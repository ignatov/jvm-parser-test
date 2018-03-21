import com.intellij.core.CoreApplicationEnvironment
import com.intellij.core.CoreJavaPsiImplementationHelper
import com.intellij.core.CoreProjectEnvironment
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.MetaLanguage
import com.intellij.lang.java.JavaParserDefinition
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.local.CoreLocalFileSystem
import com.intellij.psi.*
import com.intellij.psi.impl.JavaPsiImplementationHelper
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.script.ScriptDefinitionProvider
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.lang.parser.GroovyParserDefinition
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrClassDefinition
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

    applicationEnvironment.registerFileType(GroovyFileType.GROOVY_FILE_TYPE, "groovy")
    applicationEnvironment.registerParserDefinition(GroovyParserDefinition())

    applicationEnvironment.registerFileType(JavaFileType.INSTANCE, "java")
    applicationEnvironment.registerParserDefinition(JavaParserDefinition())

    val project = projectEnvironment.project

    projectEnvironment.registerProjectComponent(JavaPsiImplementationHelper::class.java, CoreJavaPsiImplementationHelper(project))

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

        fun createFile(path: String): PsiFile {
            val vFile = (vfm.getFileSystem(StandardFileSystems.FILE_PROTOCOL) as CoreLocalFileSystem).findFileByIoFile(File(path))
            return SingleRootFileViewProvider(psiManager, vFile!!).allFiles.first()
        }

        createFile("src/main.kt").acceptChildren(object : KtVisitorVoid() {
            override fun visitClass(klass: KtClass) {
                println(klass.name)
                super.visitClass(klass)
            }
        })

        createFile("testData/test.groovy").acceptChildren(object : GroovyPsiElementVisitor(object : GroovyElementVisitor() {
            override fun visitClassDefinition(classDefinition: GrClassDefinition) {
                println(classDefinition.name)
                super.visitClassDefinition(classDefinition)
            }
        }){})

//        val createFile = createFile("testData/test.java")
//        createFile.acceptChildren(object : JavaElementVisitor() {
//            override fun visitClass(aClass: PsiClass?) {
//                println(aClass?.name)
//                super.visitClass(aClass)
//            }
//        })
    }
}