package com.lims.server.admin;

import com.lims.common.audit.AuditLog;
import com.lims.common.audit.AuditLogRepository;
import com.lims.common.security.Roles;
import com.lims.common.web.ApiPaths;
import com.lims.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.V1 + "/admin")
@PreAuthorize(Roles.IS_ADMIN)
@Tag(name = "Administration", description = "Staff account provisioning (RBAC) and audit trail review. ADMIN only.")
public class AdminController {

    private final UserAdminService userAdminService;
    private final AuditLogRepository auditLogRepository;

    public AdminController(UserAdminService userAdminService, AuditLogRepository auditLogRepository) {
        this.userAdminService = userAdminService;
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/users")
    @Operation(summary = "List staff accounts")
    public ResponseEntity<PageResponse<UserAdminDtos.UserDto>> listUsers(
            @ParameterObject @PageableDefault(size = 50, sort = "username") Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(userAdminService.listUsers(pageable)));
    }

    @PostMapping("/users")
    @Operation(summary = "Create a staff account with one or more roles")
    public ResponseEntity<UserAdminDtos.UserDto> createUser(@Valid @RequestBody UserAdminDtos.CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userAdminService.createUser(request));
    }

    @PutMapping("/users/{id}/roles")
    @Operation(summary = "Replace a user's roles")
    public ResponseEntity<UserAdminDtos.UserDto> updateRoles(@PathVariable UUID id,
                                                             @Valid @RequestBody UserAdminDtos.UpdateRolesRequest request) {
        return ResponseEntity.ok(userAdminService.updateRoles(id, request.roles()));
    }

    @PatchMapping("/users/{id}/status")
    @Operation(summary = "Enable or disable a user (takes effect on their next request)")
    public ResponseEntity<UserAdminDtos.UserDto> updateStatus(@PathVariable UUID id,
                                                              @Valid @RequestBody UserAdminDtos.UpdateStatusRequest request) {
        return ResponseEntity.ok(userAdminService.setEnabled(id, request.enabled()));
    }

    @PostMapping("/users/{id}/unlock")
    @Operation(summary = "Clear a brute-force lockout")
    public ResponseEntity<UserAdminDtos.UserDto> unlock(@PathVariable UUID id) {
        return ResponseEntity.ok(userAdminService.unlock(id));
    }

    @PostMapping("/users/{id}/reset-password")
    @Operation(summary = "Set a new password for a user")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID id,
                                              @Valid @RequestBody UserAdminDtos.ResetPasswordRequest request) {
        userAdminService.resetPassword(id, request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit-logs")
    @Transactional(readOnly = true)
    @Operation(summary = "Review the immutable audit trail (newest first)")
    public ResponseEntity<PageResponse<UserAdminDtos.AuditLogDto>> auditLogs(
            @RequestParam(required = false) @Size(max = 100) String entityName,
            @RequestParam(required = false) @Size(max = 100) String entityId,
            @ParameterObject @PageableDefault(size = 50, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AuditLog> page;
        if (entityName != null && entityId != null) {
            page = auditLogRepository.findByEntityNameAndEntityId(entityName, entityId, pageable);
        } else if (entityName != null) {
            page = auditLogRepository.findByEntityName(entityName, pageable);
        } else {
            page = auditLogRepository.findAll(pageable);
        }
        return ResponseEntity.ok(PageResponse.from(page, log -> new UserAdminDtos.AuditLogDto(
                log.getId(), log.getEntityName(), log.getEntityId(), log.getActionType().name(),
                log.getChangedBy(), log.getNewData(), log.getIpAddress(), log.getTimestamp())));
    }
}
