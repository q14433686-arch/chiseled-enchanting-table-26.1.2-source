package enchantment_fusion.smoke;

import java.io.InputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Static bytecode check only. This Java process does not start Minecraft or a world. */
public final class ClientSoulBladeHookCheck {
    private ClientSoulBladeHookCheck() {}
    public static void main(String[] args) throws Exception {
        try (InputStream input = ClientSoulBladeHookCheck.class.getClassLoader().getResourceAsStream("net/minecraft/client/Minecraft.class")) {
            if (input == null) throw new AssertionError("Target client class not found on check classpath");
            ClassNode node = new ClassNode(); new ClassReader(input).accept(node, ClassReader.SKIP_FRAMES);
            int count = 0;
            for (var method : node.methods) if (method.name.equals("startAttack") && method.desc.equals("()Z")) {
                for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                    && call.owner.equals("net/minecraft/client/player/LocalPlayer") && call.name.equals("resetAttackStrengthTicker")
                    && call.desc.equals("()V")) count++;
            }
            if (count != 1) throw new AssertionError("Expected one client miss-branch reset call, found " + count);
            System.out.println("Client soul-blade miss-branch hook: one matching invocation");
        }
    }
}
