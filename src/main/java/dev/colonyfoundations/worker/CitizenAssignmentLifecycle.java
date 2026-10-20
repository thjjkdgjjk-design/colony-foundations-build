package dev.colonyfoundations.worker;

import dev.colonyfoundations.block.ColonyClaimState;
import dev.colonyfoundations.block.ColonyHutBlockEntity;
import dev.colonyfoundations.block.WorkerRoleRules;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Reconciles villager command tags from the durable assignment directory when the entity
 * is loaded from disk. Released records clean up stale tags; active records restore missing
 * tags, so unloading the worker or hut chunk does not duplicate or strand a role.
 */
public final class CitizenAssignmentLifecycle {
    private static final String BUILDER_ROLE = "colony_foundations:builder";
    private static final String WORKER_ROLE = "colony_foundations:worker";

    private CitizenAssignmentLifecycle() {
    }

    public static void initialize() {
        ServerEntityEvents.ENTITY_LOAD.register(CitizenAssignmentLifecycle::onEntityLoad);
    }

    private static void onEntityLoad(Entity entity, ServerWorld world) {
        if (!(entity instanceof VillagerEntity citizen)) return;
        ColonyClaimState directory = ColonyClaimState.get(world);
        ColonyClaimState.CitizenAssignment assignment =
                directory.citizenAssignment(citizen.getUuid()).orElse(null);
        if (assignment == null) return;

        if (!assignment.isActive()) {
            removeAssignmentTags(citizen);
            if (BUILDER_ROLE.equals(assignment.roleId())) {
                citizen.removeCommandTag(WorkerRoleRules.BUILDER_TAG);
            }
            if (WORKER_ROLE.equals(assignment.roleId())) {
                citizen.removeCommandTag(ColonyHutBlockEntity.HUT_WORKER_TAG);
            }
            directory.forgetReleasedCitizenAssignment(citizen.getUuid());
            return;
        }

        BlockPos building = BlockPos.fromLong(assignment.buildingPosition());
        if (BUILDER_ROLE.equals(assignment.roleId())) {
            citizen.addCommandTag(WorkerRoleRules.BUILDER_TAG);
            String dimension = assignment.dimension().replace(':', '_').replace('/', '_');
            citizen.addCommandTag(WorkerRoleRules.TOWN_HALL_ASSIGNMENT_PREFIX
                    + Long.toUnsignedString(building.asLong(), 36) + "_" + dimension);
            return;
        }

        if (WORKER_ROLE.equals(assignment.roleId())) {
            citizen.addCommandTag(ColonyHutBlockEntity.HUT_WORKER_TAG);
            citizen.addCommandTag(ColonyHutBlockEntity.HUT_ASSIGNED_TAG_PREFIX
                    + Long.toUnsignedString(building.asLong(), 36));
            String jobPath = assignment.jobId().substring(assignment.jobId().indexOf(':') + 1);
            citizen.addCommandTag(WorkerRoleRules.JOB_TAG_PREFIX + jobPath);
        }
    }

    private static void removeAssignmentTags(VillagerEntity citizen) {
        List<String> tags = citizen.getCommandTags().stream()
                .filter(tag -> tag.startsWith(WorkerRoleRules.TOWN_HALL_ASSIGNMENT_PREFIX)
                        || tag.startsWith(WorkerRoleRules.HUT_ASSIGNMENT_PREFIX)
                        || tag.startsWith(WorkerRoleRules.JOB_TAG_PREFIX))
                .toList();
        tags.forEach(citizen::removeCommandTag);
    }
}
