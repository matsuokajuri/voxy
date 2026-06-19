package me.cortex.voxy.forge;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Formal owner skeleton command registration split out of ForgeVoxyCommands.
 *
 * <p>These K1-K4 owner commands are still skeleton/status surfaces. They do not
 * imply formal renderer readiness.</p>
 */
final class ForgeVoxyFormalOwnerCommands {
    private ForgeVoxyFormalOwnerCommands() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("formal_terrain_renderer_owner_enable")
                        .executes(ctx -> formalTerrainRendererOwnerEnable(ctx.getSource())))
                .then(Commands.literal("formal_terrain_renderer_owner_status")
                        .executes(ctx -> formalTerrainRendererOwnerStatus(ctx.getSource())))
                .then(Commands.literal("formal_terrain_renderer_owner_check")
                        .executes(ctx -> formalTerrainRendererOwnerCheck(ctx.getSource())))
                .then(Commands.literal("formal_terrain_renderer_owner_audit")
                        .executes(ctx -> formalTerrainRendererOwnerAudit(ctx.getSource())))
                .then(Commands.literal("formal_terrain_renderer_owner_dump")
                        .executes(ctx -> formalTerrainRendererOwnerDump(ctx.getSource())))
                .then(Commands.literal("formal_terrain_renderer_owner_clear")
                        .executes(ctx -> formalTerrainRendererOwnerClear(ctx.getSource())))
                .then(Commands.literal("qa_k1_formal_terrain_renderer_owner")
                        .executes(ctx -> qaK1FormalTerrainRendererOwner(ctx.getSource())))
                .then(Commands.literal("formal_mdic_viewport_owner_enable")
                        .executes(ctx -> formalMdicViewportOwnerEnable(ctx.getSource())))
                .then(Commands.literal("formal_mdic_viewport_owner_status")
                        .executes(ctx -> formalMdicViewportOwnerStatus(ctx.getSource())))
                .then(Commands.literal("formal_mdic_viewport_owner_check")
                        .executes(ctx -> formalMdicViewportOwnerCheck(ctx.getSource())))
                .then(Commands.literal("formal_mdic_viewport_owner_audit")
                        .executes(ctx -> formalMdicViewportOwnerAudit(ctx.getSource())))
                .then(Commands.literal("formal_mdic_viewport_owner_dump")
                        .executes(ctx -> formalMdicViewportOwnerDump(ctx.getSource())))
                .then(Commands.literal("formal_mdic_viewport_owner_clear")
                        .executes(ctx -> formalMdicViewportOwnerClear(ctx.getSource())))
                .then(Commands.literal("qa_k2_formal_mdic_viewport_owner")
                        .executes(ctx -> qaK2FormalMdicViewportOwner(ctx.getSource())))
                .then(Commands.literal("formal_command_generation_owner_enable")
                        .executes(ctx -> formalCommandGenerationOwnerEnable(ctx.getSource())))
                .then(Commands.literal("formal_command_generation_owner_status")
                        .executes(ctx -> formalCommandGenerationOwnerStatus(ctx.getSource())))
                .then(Commands.literal("formal_command_generation_owner_check")
                        .executes(ctx -> formalCommandGenerationOwnerCheck(ctx.getSource())))
                .then(Commands.literal("formal_command_generation_owner_audit")
                        .executes(ctx -> formalCommandGenerationOwnerAudit(ctx.getSource())))
                .then(Commands.literal("formal_command_generation_owner_dump")
                        .executes(ctx -> formalCommandGenerationOwnerDump(ctx.getSource())))
                .then(Commands.literal("formal_command_generation_owner_clear")
                        .executes(ctx -> formalCommandGenerationOwnerClear(ctx.getSource())))
                .then(Commands.literal("qa_k3_formal_command_generation_owner")
                        .executes(ctx -> qaK3FormalCommandGenerationOwner(ctx.getSource())))
                .then(Commands.literal("formal_visibility_owner_enable")
                        .executes(ctx -> formalVisibilityOwnerEnable(ctx.getSource())))
                .then(Commands.literal("formal_visibility_owner_status")
                        .executes(ctx -> formalVisibilityOwnerStatus(ctx.getSource())))
                .then(Commands.literal("formal_visibility_owner_check")
                        .executes(ctx -> formalVisibilityOwnerCheck(ctx.getSource())))
                .then(Commands.literal("formal_visibility_owner_audit")
                        .executes(ctx -> formalVisibilityOwnerAudit(ctx.getSource())))
                .then(Commands.literal("formal_visibility_owner_dump")
                        .executes(ctx -> formalVisibilityOwnerDump(ctx.getSource())))
                .then(Commands.literal("formal_visibility_owner_clear")
                        .executes(ctx -> formalVisibilityOwnerClear(ctx.getSource())))
                .then(Commands.literal("qa_k4_formal_visibility_owner")
                        .executes(ctx -> qaK4FormalVisibilityOwner(ctx.getSource())));
    }

    static int formalTerrainRendererOwnerEnable(CommandSourceStack source) {
        ForgeFormalTerrainRendererStats status = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().enable("command-enable");
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("k1-formal-terrain-renderer-owner-enable");
        String message = "Voxy K1 formal terrain renderer owner enable: "
                + formatFormalTerrainRendererOwnerStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " No live terrain draw, MDICSectionRenderer call, VoxyRenderSystem call, or formal MDIC draw was started.";
        source.sendSuccess(() -> Component.literal(message), false);
        return status.formalTerrainRendererOwnerReady()
                && status.noDraw()
                && !status.formalTerrainRendererReady()
                && !status.actualRendererDrawEnabled()
                && !rendererStatus.formalRendererReady()
                && !rendererStatus.actualDrawEnabled() ? 1 : 0;
    }

    static int formalTerrainRendererOwnerStatus(CommandSourceStack source) {
        ForgeFormalTerrainRendererStats status = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy K1 formal terrain renderer owner status: " + formatFormalTerrainRendererOwnerStatus(status)), false);
        return status.formalTerrainRendererOwnerReady() || status.stale() ? 1 : 0;
    }

    static int formalTerrainRendererOwnerCheck(CommandSourceStack source) {
        ForgeFormalTerrainRendererStats status = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().check("command-check");
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("k1-formal-terrain-renderer-owner-check");
        source.sendSuccess(() -> Component.literal("Voxy K1 formal terrain renderer owner check: "
                + formatFormalTerrainRendererOwnerStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)), false);
        return status.formalTerrainRendererOwnerReady() && status.noDraw() ? 1 : 0;
    }

    static int formalTerrainRendererOwnerAudit(CommandSourceStack source) {
        ForgeFormalTerrainRendererAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().audit();
        ForgeFormalTerrainRendererStats status = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().createStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("k1-formal-terrain-renderer-owner-audit");
        source.sendSuccess(() -> Component.literal("Voxy K1 formal terrain renderer owner audit: "
                + formatFormalTerrainRendererOwnerAudit(audit)
                + " "
                + formatFormalTerrainRendererOwnerStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)), false);
        return audit.success() ? 1 : 0;
    }

    static int formalTerrainRendererOwnerDump(CommandSourceStack source) {
        ForgeFormalTerrainRendererStats status = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().check("command-dump");
        String blockers = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().dumpBlockers();
        source.sendSuccess(() -> Component.literal("Voxy K1 formal terrain renderer owner dump: blockers=" + blockers + " " + formatFormalTerrainRendererOwnerStatus(status)), false);
        return status.blockerCount() > 0 ? 1 : 0;
    }

    static int formalTerrainRendererOwnerClear(CommandSourceStack source) {
        ForgeFormalTerrainRendererStats status = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().clear("command-clear");
        source.sendSuccess(() -> Component.literal("Voxy K1 formal terrain renderer owner clear: "
                + formatFormalTerrainRendererOwnerStatus(status)
                + " Only K1 owner lifecycle/status was cleared; GL geometry heap, MDIC command buffers, debug renderers, J-stage previews, and formal ModelStore resources were left unchanged."), false);
        return 1;
    }

    static int qaK1FormalTerrainRendererOwner(CommandSourceStack source) {
        ForgeFormalTerrainRendererStats enableStatus = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().enable("qa-k1-formal-terrain-renderer-owner");
        ForgeFormalTerrainRendererStats checkStatus = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().check("qa-k1-formal-terrain-renderer-owner");
        ForgeFormalTerrainRendererAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().audit();
        ForgeFormalTerrainRendererStats status = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().createStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("qa-k1-formal-terrain-renderer-owner");
        String message = "Voxy QA K1 formal terrain renderer owner no-draw skeleton: "
                + formatFormalTerrainRendererOwnerStatus(status)
                + " "
                + formatFormalTerrainRendererOwnerAudit(audit)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " K1 references formal model/shader/J5 readiness, defines viewport/command/visibility/draw placeholders as missing, keeps J-stage previews separated, and starts no live terrain draw.";
        VoxyForge.LOGGER.info(message);
        source.sendSuccess(() -> Component.literal(message), false);
        return enableStatus.formalTerrainRendererOwnerReady()
                && checkStatus.k0VerdictReadyForK1()
                && audit.success()
                && status.formalTerrainRendererOwnerReady()
                && status.formalTerrainRendererLifecycleReady()
                && !status.formalTerrainRendererReady()
                && !status.actualRendererDrawEnabled()
                && status.noDraw()
                && status.k0VerdictReadyForK1()
                && status.originalVoxyAlignmentPreserved()
                && status.debugRendererIsolationOk()
                && status.previewSystemsSeparated()
                && !status.sampleSetUsedAsFormalSource()
                && status.p0BlockerCount() >= 1
                && !rendererStatus.formalRendererReady()
                && !rendererStatus.actualDrawEnabled() ? 1 : 0;
    }

    static int formalMdicViewportOwnerEnable(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().enable("k2-formal-mdic-viewport-owner-enable");
        ForgeFormalMdicViewportStats status = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().enable("command-enable");
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("k2-formal-mdic-viewport-owner-enable");
        source.sendSuccess(() -> Component.literal("Voxy K2 formal MDIC viewport owner enable: "
                + formatFormalMdicViewportOwnerStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " No formal draw, cmdgen, glMultiDrawElementsIndirectCountARB, MDICSectionRenderer, or VoxyRenderSystem call was started."), false);
        return status.formalViewportOwnerReady()
                && status.formalCommandBufferOwnerReady()
                && status.formalVisibilityOwnerReady()
                && status.noDraw()
                && !status.actualRendererDrawEnabled()
                && !rendererStatus.formalRendererReady()
                && !rendererStatus.actualDrawEnabled() ? 1 : 0;
    }

    static int formalMdicViewportOwnerStatus(CommandSourceStack source) {
        ForgeFormalMdicViewportStats status = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy K2 formal MDIC viewport owner status: " + formatFormalMdicViewportOwnerStatus(status)), false);
        return status.formalViewportOwnerReady() || status.stale() ? 1 : 0;
    }

    static int formalMdicViewportOwnerCheck(CommandSourceStack source) {
        ForgeFormalMdicViewportStats status = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().check("command-check");
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("k2-formal-mdic-viewport-owner-check");
        source.sendSuccess(() -> Component.literal("Voxy K2 formal MDIC viewport owner check: "
                + formatFormalMdicViewportOwnerStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)), false);
        return status.formalViewportOwnerReady()
                && status.formalCommandBufferOwnerReady()
                && status.formalVisibilityOwnerReady()
                && status.noDraw() ? 1 : 0;
    }

    static int formalMdicViewportOwnerAudit(CommandSourceStack source) {
        ForgeFormalMdicViewportAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().audit();
        ForgeFormalMdicViewportStats status = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().createStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("k2-formal-mdic-viewport-owner-audit");
        source.sendSuccess(() -> Component.literal("Voxy K2 formal MDIC viewport owner audit: "
                + formatFormalMdicViewportOwnerAudit(audit)
                + " "
                + formatFormalMdicViewportOwnerStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)), false);
        return audit.success() ? 1 : 0;
    }

    static int formalMdicViewportOwnerDump(CommandSourceStack source) {
        ForgeFormalMdicViewportStats status = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().check("command-dump");
        String blockers = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().dumpBlockers();
        source.sendSuccess(() -> Component.literal("Voxy K2 formal MDIC viewport owner dump: blockers=" + blockers + " " + formatFormalMdicViewportOwnerStatus(status)), false);
        return status.blockerCount() > 0 ? 1 : 0;
    }

    static int formalMdicViewportOwnerClear(CommandSourceStack source) {
        ForgeFormalMdicViewportStats status = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().clear("command-clear");
        source.sendSuccess(() -> Component.literal("Voxy K2 formal MDIC viewport owner clear: "
                + formatFormalMdicViewportOwnerStatus(status)
                + " Only K2 owner lifecycle/status was cleared; debug MDIC command buffers, GL geometry heap, J-stage previews, and formal ModelStore resources were left unchanged."), false);
        return 1;
    }

    static int qaK2FormalMdicViewportOwner(CommandSourceStack source) {
        ForgeFormalTerrainRendererStats terrainStatus = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().enable("qa-k2-formal-mdic-viewport-owner");
        ForgeFormalMdicViewportStats enableStatus = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().enable("qa-k2-formal-mdic-viewport-owner");
        ForgeFormalMdicViewportStats checkStatus = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().check("qa-k2-formal-mdic-viewport-owner");
        ForgeFormalMdicViewportAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().audit();
        ForgeFormalMdicViewportStats status = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().createStatusSnapshot();
        ForgeFormalTerrainRendererStats terrainOwnerStatus = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().check("qa-k2-formal-mdic-viewport-owner");
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("qa-k2-formal-mdic-viewport-owner");
        String message = "Voxy QA K2 formal MDIC viewport / command / visibility owner no-draw skeleton: "
                + formatFormalMdicViewportOwnerStatus(status)
                + " "
                + formatFormalMdicViewportOwnerAudit(audit)
                + " "
                + formatFormalTerrainRendererOwnerStatus(terrainOwnerStatus)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " K2 creates formal logical owners for viewport, draw command, draw count, visibility, render-list/indirect lookup, and position scratch resources; debug MDIC command buffers are not used as formal resources and no draw/cmdgen call is issued.";
        VoxyForge.LOGGER.info(message);
        source.sendSuccess(() -> Component.literal(message), false);
        return terrainStatus.formalTerrainRendererOwnerReady()
                && enableStatus.formalViewportOwnerReady()
                && checkStatus.formalCommandBufferOwnerReady()
                && status.formalDrawCommandBufferOwnerReady()
                && status.formalDrawCountBufferOwnerReady()
                && status.formalVisibilityOwnerReady()
                && status.formalRenderListOwnerReady()
                && status.formalPositionScratchOwnerReady()
                && !status.debugMdicCommandBuffersUsedAsFormal()
                && status.debugRendererIsolationOk()
                && !status.formalDrawPipelineReady()
                && !status.formalRendererReady()
                && !status.actualRendererDrawEnabled()
                && status.noDraw()
                && status.p0BlockerCount() >= 1
                && audit.success()
                && !rendererStatus.formalRendererReady()
                && !rendererStatus.actualDrawEnabled() ? 1 : 0;
    }

    static int formalCommandGenerationOwnerEnable(CommandSourceStack source) {
        ForgeFormalCommandGenerationStats status = ForgeVoxyInstance.INSTANCE.getFormalCommandGenerationOwner().enable("command-enable");
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("k3-formal-command-generation-owner-enable");
        source.sendSuccess(() -> Component.literal("Voxy K3 formal command generation owner enable: "
                + formatFormalCommandGenerationOwnerStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " No cmdgen dispatch, draw call, MDICSectionRenderer, or VoxyRenderSystem call was started."), false);
        return status.formalCommandGenerationOwnerReady()
                && status.formalCommandGenerationContractReady()
                && !status.formalDrawPipelineReady()
                && !status.formalRendererReady()
                && !status.actualRendererDrawEnabled() ? 1 : 0;
    }

    static int formalCommandGenerationOwnerStatus(CommandSourceStack source) {
        ForgeFormalCommandGenerationStats status = ForgeVoxyInstance.INSTANCE.getFormalCommandGenerationOwner().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy K3 formal command generation owner status: " + formatFormalCommandGenerationOwnerStatus(status)), false);
        return status.formalCommandGenerationOwnerReady() || status.stale() ? 1 : 0;
    }

    static int formalCommandGenerationOwnerCheck(CommandSourceStack source) {
        ForgeFormalCommandGenerationStats status = ForgeVoxyInstance.INSTANCE.getFormalCommandGenerationOwner().check("command-check");
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("k3-formal-command-generation-owner-check");
        source.sendSuccess(() -> Component.literal("Voxy K3 formal command generation owner check: "
                + formatFormalCommandGenerationOwnerStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)), false);
        return status.formalCommandGenerationOwnerReady()
                && status.formalCommandGenerationContractReady()
                && status.formalDrawCommandLayoutReady()
                && status.formalDrawCountLayoutReady()
                && !status.formalDrawPipelineReady()
                && !status.actualRendererDrawEnabled() ? 1 : 0;
    }

    static int formalCommandGenerationOwnerAudit(CommandSourceStack source) {
        ForgeFormalCommandGenerationAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalCommandGenerationOwner().audit();
        ForgeFormalCommandGenerationStats status = ForgeVoxyInstance.INSTANCE.getFormalCommandGenerationOwner().createStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("k3-formal-command-generation-owner-audit");
        source.sendSuccess(() -> Component.literal("Voxy K3 formal command generation owner audit: "
                + formatFormalCommandGenerationOwnerAudit(audit)
                + " "
                + formatFormalCommandGenerationOwnerStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)), false);
        return audit.success() ? 1 : 0;
    }

    static int formalCommandGenerationOwnerDump(CommandSourceStack source) {
        ForgeFormalCommandGenerationStats status = ForgeVoxyInstance.INSTANCE.getFormalCommandGenerationOwner().check("command-dump");
        String blockers = ForgeVoxyInstance.INSTANCE.getFormalCommandGenerationOwner().dumpBlockers();
        source.sendSuccess(() -> Component.literal("Voxy K3 formal command generation owner dump: blockers=" + blockers + " " + formatFormalCommandGenerationOwnerStatus(status)), false);
        return status.blockerCount() > 0 ? 1 : 0;
    }

    static int formalCommandGenerationOwnerClear(CommandSourceStack source) {
        ForgeFormalCommandGenerationStats status = ForgeVoxyInstance.INSTANCE.getFormalCommandGenerationOwner().clear("command-clear");
        source.sendSuccess(() -> Component.literal("Voxy K3 formal command generation owner clear: "
                + formatFormalCommandGenerationOwnerStatus(status)
                + " Only K3 owner lifecycle/status was cleared; K2 viewport resources, debug command buffers, GL geometry heap, previews, and formal model resources were left unchanged."), false);
        return 1;
    }

    static int qaK3FormalCommandGenerationOwner(CommandSourceStack source) {
        ForgeFormalTerrainRendererStats terrainStatus = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().enable("qa-k3-formal-command-generation-owner");
        ForgeFormalMdicViewportStats viewportEnableStatus = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().enable("qa-k3-formal-command-generation-owner");
        ForgeFormalCommandGenerationStats enableStatus = ForgeVoxyInstance.INSTANCE.getFormalCommandGenerationOwner().enable("qa-k3-formal-command-generation-owner");
        ForgeFormalCommandGenerationStats checkStatus = ForgeVoxyInstance.INSTANCE.getFormalCommandGenerationOwner().check("qa-k3-formal-command-generation-owner");
        ForgeFormalCommandGenerationAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalCommandGenerationOwner().audit();
        ForgeFormalCommandGenerationStats status = ForgeVoxyInstance.INSTANCE.getFormalCommandGenerationOwner().createStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("qa-k3-formal-command-generation-owner");
        String message = "Voxy QA K3 formal command generation ownership skeleton: "
                + formatFormalCommandGenerationOwnerStatus(status)
                + " "
                + formatFormalCommandGenerationOwnerAudit(audit)
                + " "
                + formatFormalMdicViewportOwnerStatus(viewportEnableStatus)
                + " "
                + formatFormalTerrainRendererOwnerStatus(terrainStatus)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " No cmdgen compute dispatch, glMultiDrawElementsIndirectCountARB, MDICSectionRenderer, VoxyRenderSystem, terrain draw, visible LoD draw, or debug renderer replacement was performed.";
        VoxyForge.LOGGER.info(message);
        source.sendSuccess(() -> Component.literal(message), false);
        return terrainStatus.formalTerrainRendererOwnerReady()
                && viewportEnableStatus.formalViewportOwnerReady()
                && enableStatus.formalCommandGenerationOwnerReady()
                && checkStatus.formalCommandGenerationContractReady()
                && status.formalDrawCommandLayoutReady()
                && status.formalDrawCountLayoutReady()
                && status.debugRendererIsolationOk()
                && !status.debugMdicCommandBuffersUsedAsFormal()
                && !status.cmdgenComputeShaderRun()
                && !status.glMultiDrawElementsIndirectCountCalled()
                && !status.formalDrawPipelineReady()
                && !status.formalRendererReady()
                && !status.actualRendererDrawEnabled()
                && status.noDraw()
                && status.p0BlockerCount() >= 1
                && audit.success()
                && !rendererStatus.formalRendererReady()
                && !rendererStatus.actualDrawEnabled() ? 1 : 0;
    }

    static int formalVisibilityOwnerEnable(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().enable("k4-formal-visibility-owner-enable");
        ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().enable("k4-formal-visibility-owner-enable");
        ForgeVoxyInstance.INSTANCE.getFormalCommandGenerationOwner().enable("k4-formal-visibility-owner-enable");
        ForgeFormalVisibilityStats status = ForgeVoxyInstance.INSTANCE.getFormalVisibilityOwner().enable("command-enable");
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("k4-formal-visibility-owner-enable");
        source.sendSuccess(() -> Component.literal("Voxy K4 formal visibility owner enable: "
                + formatFormalVisibilityOwnerStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " No traversal implementation, cmdgen dispatch, draw call, MDICSectionRenderer, or VoxyRenderSystem call was started."), false);
        return status.formalVisibilityOwnerReady()
                && status.formalRenderListOwnerReady()
                && status.formalIndirectLookupOwnerReady()
                && !status.formalDrawPipelineReady()
                && !status.formalRendererReady()
                && !status.actualRendererDrawEnabled() ? 1 : 0;
    }

    static int formalVisibilityOwnerStatus(CommandSourceStack source) {
        ForgeFormalVisibilityStats status = ForgeVoxyInstance.INSTANCE.getFormalVisibilityOwner().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy K4 formal visibility owner status: " + formatFormalVisibilityOwnerStatus(status)), false);
        return status.formalVisibilityOwnerReady() || status.stale() ? 1 : 0;
    }

    static int formalVisibilityOwnerCheck(CommandSourceStack source) {
        ForgeFormalVisibilityStats status = ForgeVoxyInstance.INSTANCE.getFormalVisibilityOwner().check("command-check");
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("k4-formal-visibility-owner-check");
        source.sendSuccess(() -> Component.literal("Voxy K4 formal visibility owner check: "
                + formatFormalVisibilityOwnerStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)), false);
        return status.formalVisibilityOwnerReady()
                && status.formalVisibilityContractReady()
                && status.formalRenderListContractReady()
                && !status.formalVisibilityTraversalImplemented()
                && !status.cmdgenComputeShaderRun()
                && !status.glMultiDrawElementsIndirectCountCalled()
                && !status.actualRendererDrawEnabled() ? 1 : 0;
    }

    static int formalVisibilityOwnerAudit(CommandSourceStack source) {
        ForgeFormalVisibilityAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalVisibilityOwner().audit();
        ForgeFormalVisibilityStats status = ForgeVoxyInstance.INSTANCE.getFormalVisibilityOwner().createStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("k4-formal-visibility-owner-audit");
        source.sendSuccess(() -> Component.literal("Voxy K4 formal visibility owner audit: "
                + formatFormalVisibilityOwnerAudit(audit)
                + " "
                + formatFormalVisibilityOwnerStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)), false);
        return audit.success() ? 1 : 0;
    }

    static int formalVisibilityOwnerDump(CommandSourceStack source) {
        ForgeFormalVisibilityStats status = ForgeVoxyInstance.INSTANCE.getFormalVisibilityOwner().check("command-dump");
        String blockers = ForgeVoxyInstance.INSTANCE.getFormalVisibilityOwner().dumpBlockers();
        source.sendSuccess(() -> Component.literal("Voxy K4 formal visibility owner dump: blockers=" + blockers + " " + formatFormalVisibilityOwnerStatus(status)), false);
        return status.blockerCount() > 0 ? 1 : 0;
    }

    static int formalVisibilityOwnerClear(CommandSourceStack source) {
        ForgeFormalVisibilityStats status = ForgeVoxyInstance.INSTANCE.getFormalVisibilityOwner().clear("command-clear");
        source.sendSuccess(() -> Component.literal("Voxy K4 formal visibility owner clear: "
                + formatFormalVisibilityOwnerStatus(status)
                + " Only K4 owner lifecycle/status was cleared; K1/K2/K3 owners, debug planners, debug renderers, GL geometry heap, previews, and formal model resources were left unchanged."), false);
        return 1;
    }

    static int qaK4FormalVisibilityOwner(CommandSourceStack source) {
        ForgeFormalTerrainRendererStats terrainStatus = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().enable("qa-k4-formal-visibility-owner");
        ForgeFormalMdicViewportStats viewportStatus = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().enable("qa-k4-formal-visibility-owner");
        ForgeFormalCommandGenerationStats commandStatus = ForgeVoxyInstance.INSTANCE.getFormalCommandGenerationOwner().enable("qa-k4-formal-visibility-owner");
        ForgeFormalVisibilityStats enableStatus = ForgeVoxyInstance.INSTANCE.getFormalVisibilityOwner().enable("qa-k4-formal-visibility-owner");
        ForgeFormalVisibilityStats checkStatus = ForgeVoxyInstance.INSTANCE.getFormalVisibilityOwner().check("qa-k4-formal-visibility-owner");
        ForgeFormalVisibilityAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalVisibilityOwner().audit();
        ForgeFormalVisibilityStats status = ForgeVoxyInstance.INSTANCE.getFormalVisibilityOwner().createStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("qa-k4-formal-visibility-owner");
        String message = "Voxy QA K4 formal visibility/render-list ownership skeleton: "
                + formatFormalVisibilityOwnerStatus(status)
                + " "
                + formatFormalVisibilityOwnerAudit(audit)
                + " "
                + formatFormalCommandGenerationOwnerStatus(commandStatus)
                + " "
                + formatFormalMdicViewportOwnerStatus(viewportStatus)
                + " "
                + formatFormalTerrainRendererOwnerStatus(terrainStatus)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " No formal traversal implementation, cmdgen compute dispatch, glMultiDrawElementsIndirectCountARB, MDICSectionRenderer, VoxyRenderSystem, terrain draw, visible LoD draw, or debug planner promotion was performed.";
        VoxyForge.LOGGER.info(message);
        source.sendSuccess(() -> Component.literal(message), false);
        return terrainStatus.formalTerrainRendererOwnerReady()
                && viewportStatus.formalViewportOwnerReady()
                && commandStatus.formalCommandGenerationOwnerReady()
                && enableStatus.formalVisibilityOwnerReady()
                && checkStatus.formalVisibilityContractReady()
                && status.formalRenderListContractReady()
                && status.formalIndirectLookupOwnerReady()
                && !status.debugPlannerUsedAsFormal()
                && !status.formalVisibilityTraversalImplemented()
                && !status.cmdgenComputeShaderRun()
                && !status.glMultiDrawElementsIndirectCountCalled()
                && !status.formalDrawPipelineReady()
                && !status.formalRendererReady()
                && !status.actualRendererDrawEnabled()
                && status.noDraw()
                && status.p0BlockerCount() >= 1
                && audit.success()
                && !rendererStatus.formalRendererReady()
                && !rendererStatus.actualDrawEnabled() ? 1 : 0;
    }

    private static String formatFormalTerrainRendererOwnerStatus(ForgeFormalTerrainRendererStats status) {
        return ForgeVoxyCommands.formatFormalTerrainRendererOwnerStatus(status);
    }

    private static String formatFormalTerrainRendererOwnerAudit(ForgeFormalTerrainRendererAuditResult audit) {
        return ForgeVoxyCommands.formatFormalTerrainRendererOwnerAudit(audit);
    }

    private static String formatFormalMdicViewportOwnerStatus(ForgeFormalMdicViewportStats status) {
        return ForgeVoxyCommands.formatFormalMdicViewportOwnerStatus(status);
    }

    private static String formatFormalMdicViewportOwnerAudit(ForgeFormalMdicViewportAuditResult audit) {
        return ForgeVoxyCommands.formatFormalMdicViewportOwnerAudit(audit);
    }

    private static String formatFormalCommandGenerationOwnerStatus(ForgeFormalCommandGenerationStats status) {
        return ForgeVoxyCommands.formatFormalCommandGenerationOwnerStatus(status);
    }

    private static String formatFormalCommandGenerationOwnerAudit(ForgeFormalCommandGenerationAuditResult audit) {
        return ForgeVoxyCommands.formatFormalCommandGenerationOwnerAudit(audit);
    }

    private static String formatFormalVisibilityOwnerStatus(ForgeFormalVisibilityStats status) {
        return ForgeVoxyCommands.formatFormalVisibilityOwnerStatus(status);
    }

    private static String formatFormalVisibilityOwnerAudit(ForgeFormalVisibilityAuditResult audit) {
        return ForgeVoxyCommands.formatFormalVisibilityOwnerAudit(audit);
    }

    private static String formatFormalRendererStatus(ForgeFormalRendererStats status) {
        return ForgeVoxyCommands.formatFormalRendererStatus(status);
    }

}
