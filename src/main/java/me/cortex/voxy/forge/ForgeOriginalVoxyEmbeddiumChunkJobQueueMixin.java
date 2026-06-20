package me.cortex.voxy.forge;

import me.cortex.voxy.client.compat.SemaphoreBlockImpersonator;
import me.cortex.voxy.common.thread.MultiThreadPrioritySemaphore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.Semaphore;

@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.executor.ChunkJobQueue", remap = false)
public class ForgeOriginalVoxyEmbeddiumChunkJobQueueMixin {
    @Unique
    private MultiThreadPrioritySemaphore.Block voxy$semaphoreBlock;

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "(I)Ljava/util/concurrent/Semaphore;"))
    private Semaphore voxy$injectUnifiedPool(int permits) {
        MultiThreadPrioritySemaphore.Block block =
                ForgeVoxyInstance.INSTANCE.getOriginalVoxyModelPipeline().createEmbeddiumBuilderSemaphoreBlock();
        if (block != null) {
            this.voxy$semaphoreBlock = block;
            return new SemaphoreBlockImpersonator(block);
        }
        return new Semaphore(permits);
    }

    @Inject(method = "shutdown", at = @At("RETURN"))
    private void voxy$injectAtShutdown(CallbackInfoReturnable<?> ci) {
        if (this.voxy$semaphoreBlock != null) {
            this.voxy$semaphoreBlock.free();
            this.voxy$semaphoreBlock = null;
        }
    }
}
