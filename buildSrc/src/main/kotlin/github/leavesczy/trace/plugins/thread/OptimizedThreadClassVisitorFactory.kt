package github.leavesczy.trace.plugins.thread

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import github.leavesczy.trace.utils.InitMethodName
import github.leavesczy.trace.utils.insertArgument
import github.leavesczy.trace.utils.nameWithDesc
import github.leavesczy.trace.utils.simpleClassName
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode

/**
 * @Author: leavesCZY
 * @Github: https://github.com/leavesCZY
 * @Desc:
 */
internal interface OptimizedThreadConfigParameters : InstrumentationParameters {
    @get:Input
    val config: Property<OptimizedThreadConfig>
}

internal abstract class OptimizedThreadClassVisitorFactory :
    AsmClassVisitorFactory<OptimizedThreadConfigParameters> {

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor {
        return OptimizedThreadClassVisitor(
            config = parameters.get().config.get(),
            nextClassVisitor = nextClassVisitor
        )
    }

    override fun isInstrumentable(classData: ClassData): Boolean {
        return true
    }

}

private class OptimizedThreadClassVisitor(
    private val config: OptimizedThreadConfig,
    private val nextClassVisitor: ClassVisitor
) : ClassNode(Opcodes.ASM5) {

    companion object {

        private const val executorsClass = "java/util/concurrent/Executors"

        private const val threadClass = "java/lang/Thread"

        private const val threadFactoryClass = "java/util/concurrent/ThreadFactory"

        private const val threadFactoryNewThreadMethodDesc =
            "newThread(Ljava/lang/Runnable;)Ljava/lang/Thread;"

    }

    override fun visitEnd() {
        super.visitEnd()
        methods.forEach { methodNode ->
            val instructions = methodNode.instructions
            if (instructions != null && instructions.size() > 0) {
                instructions.forEach { instruction ->
                    when (instruction.opcode) {
                        Opcodes.INVOKESTATIC -> {
                            val methodInsnNode = instruction as? MethodInsnNode
                            if (methodInsnNode?.owner == executorsClass) {
                                transformInvokeExecutorsInstruction(
                                    methodNode,
                                    instruction
                                )
                            }
                        }

                        Opcodes.NEW -> {
                            val typeInsnNode = instruction as? TypeInsnNode
                            if (typeInsnNode != null) {
                                if (typeInsnNode.desc == threadClass) {
                                    //如果是在 ThreadFactory 内初始化线程，则不处理
                                    if (!isThreadFactoryMethod(methodNode)) {
                                        transformNewThreadInstruction(
                                            methodNode,
                                            instruction
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        accept(nextClassVisitor)
    }

    private fun transformInvokeExecutorsInstruction(
        methodNode: MethodNode,
        methodInsnNode: MethodInsnNode
    ) {
        val pointMethod = config.executorsMethodNameList.find { it == methodInsnNode.name }
        if (pointMethod != null) {
            //将 Executors 替换为 OptimizedThreadPool
            methodInsnNode.owner = config.optimizedExecutorsClass
            //为调用 newFixedThreadPool 等方法的指令多插入一个 String 类型的方法入参参数声明
            methodInsnNode.insertArgument(String::class.java)
            //将 className 作为上述 String 参数的入参参数
//            methodNode.instructions.insertBefore(methodInsnNode, LdcInsnNode(simpleClassName))


            // 2. 拼接要传入的方法信息。这里示例为 "className#methodName(methodDesc)" 的形式。
            val fullMethodInfo = "$simpleClassName#${methodNode.name}${methodNode.desc}"
            //将 ClassName 作为构造参数传给 OptimizedThread
            methodNode.instructions.insertBefore(methodInsnNode, LdcInsnNode(fullMethodInfo))
        }
    }

    private fun transformNewThreadInstruction(methodNode: MethodNode, typeInsnNode: TypeInsnNode) {
        val instructions = methodNode.instructions
        val typeInsnNodeIndex = instructions.indexOf(typeInsnNode)
        //从 typeInsnNode 指令开始遍历，找到调用 Thread 构造函数的指令，然后对其进行替换
        for (index in typeInsnNodeIndex + 1 until instructions.size()) {
            val node = instructions[index]
            if (node is MethodInsnNode && node.isThreadInitMethod()) {
                //将 Thread 替换为 OptimizedThread
                typeInsnNode.desc = config.optimizedThreadClass
                node.owner = config.optimizedThreadClass
                //为调用 Thread 构造函数的指令多插入一个 String 类型的方法入参参数声明
                node.insertArgument(String::class.java)
                //将 ClassName 作为构造参数传给 OptimizedThread
//                instructions.insertBefore(node, LdcInsnNode(simpleClassName))


                //结果：null-OptimizedThreadActivity#onCreate$lambda$7(Lgithub/leavesczy/trace/thread/OptimizedThreadActivity;Landroid/view/View;)V-2
                // 2. 拼接要传入的方法信息。这里示例为 "className#methodName(methodDesc)" 的形式。
                val fullMethodInfo = "$simpleClassName#${methodNode.name}${methodNode.desc}"
                //将 ClassName 作为构造参数传给 OptimizedThread
                instructions.insertBefore(node, LdcInsnNode(fullMethodInfo))
                break
            }
        }
    }

    private fun MethodInsnNode.isThreadInitMethod(): Boolean {
        return this.owner == threadClass && this.name == InitMethodName
    }

    private fun ClassNode.isThreadFactoryMethod(methodNode: MethodNode): Boolean {
        return this.interfaces?.contains(threadFactoryClass) == true
                && methodNode.nameWithDesc == threadFactoryNewThreadMethodDesc
    }

}