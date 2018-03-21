import com.intellij.core.CoreApplicationEnvironment
import com.intellij.core.CoreJavaPsiImplementationHelper
import com.intellij.core.CoreProjectEnvironment
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.LanguageASTFactory
import com.intellij.lang.MetaLanguage
import com.intellij.lang.java.JavaLanguage
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
import com.intellij.psi.impl.source.tree.CoreJavaASTFactory
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.script.ScriptDefinitionProvider
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.lang.parser.GroovyParserDefinition
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
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

    project.registerService(JavaPsiImplementationHelper::class.java, CoreJavaPsiImplementationHelper(project))
    LanguageASTFactory.INSTANCE.addExplicitExtension(JavaLanguage.INSTANCE, CoreJavaASTFactory())

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

        createFile("src/main.kt").acceptChildren(object : KtTreeVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                println(function.name)
                super.visitNamedFunction(function)
            }
        })

        createFile("testData/test.groovy").acceptChildren(object : GroovyPsiElementVisitor(object : GroovyElementVisitor() {
            override fun visitMethod(method: GrMethod) {
                println(method.name)
                super.visitMethod(method)
            }
        }) {
            override fun visitElement(element: PsiElement?) {
                super.visitElement(element)
                element?.acceptChildren(this)
            }
        })

        createFile("testData/test.java").acceptChildren(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethod(method: PsiMethod?) {
                println(method?.name)
                super.visitMethod(method)
            }
        })
    }
}