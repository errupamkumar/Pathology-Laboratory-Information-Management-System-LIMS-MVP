/**
 * Shared kernel: base entity, audit trail, domain events, enums, exceptions, security
 * primitives and cross-module ports. Declared OPEN so every module may use its sub-packages.
 */
@ApplicationModule(displayName = "Common Shared Kernel", type = ApplicationModule.Type.OPEN)
package com.lims.common;

import org.springframework.modulith.ApplicationModule;
