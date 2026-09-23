package com.smartAiUniversityAssistant.seniorproject.feature.authentication.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.authentication.entity.AppUserRole;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto.RoleResponse;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AppUserRoleRepository extends JpaRepository<AppUserRole, Long> {
    @Query("select distinct new com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto.RoleResponse(r.roleCode, r.roleName) from AppUserRole ur join ur.role r where ur.user.id = :id and r.active = true order by r.roleCode")
    List<RoleResponse> findActiveRoles(@Param("id") Long id);
    List<AppUserRole> findByUserId(Long userId);
    @Query("select ur from AppUserRole ur join fetch ur.role r where ur.user.id in :ids order by ur.user.id, r.id")
    List<AppUserRole> findByUserIdInWithRole(@Param("ids") Collection<Long> ids);
}
