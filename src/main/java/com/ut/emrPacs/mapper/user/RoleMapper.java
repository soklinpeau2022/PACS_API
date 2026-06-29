package com.ut.emrPacs.mapper.user;

import com.ut.emrPacs.model.dto.response.authentication.module.ModuleType;
import com.ut.emrPacs.model.role.Role;
import com.ut.emrPacs.model.role.RoleUser;
import com.ut.emrPacs.model.base.filter.RoleListFilter;
import com.ut.emrPacs.model.dto.response.authentication.role.RoleResponse;
import com.ut.emrPacs.model.dto.response.authentication.role.UserGroupSummaryResponse;
import com.ut.emrPacs.model.users.UserList;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis mapper interface for namespace {@code com.ut.emrPacs.mapper.user.RoleMapper}.
 * Each method maps to an XML statement with the same {@code id}.
 */
@Mapper
public interface RoleMapper {
    /**
     * MyBatis statement id: {@code getList}.
     */
    List<RoleResponse> listRole(@Param("filter") RoleListFilter filter, @Param("hospitalId") Long hospitalId);

    /**
     * MyBatis statement id: {@code countList}.
     */
    Long countList(@Param("filter") RoleListFilter filter, @Param("hospitalId") Long hospitalId);

    /**
     * MyBatis statement id: {@code getOne}.
     */
    List<RoleResponse> getRoleById(@Param("id") Long id, @Param("hospitalId") Long hospitalId);

    /**
     * MyBatis statement id: {@code getRoleByIdAnyHospital}.
     */
    List<RoleResponse> getRoleByIdAnyHospital(@Param("id") Long id);

    /**
     * MyBatis statement id: {@code countRoleByIdAndCode}.
     */
    Long countRoleByIdAndCode(@Param("id") Long id, @Param("code") String code);

    /**
     * MyBatis statement id: {@code getUser}.
     */
    List<RoleUser> getUser(@Param("id") Long id, @Param("hospitalId") Long hospitalId);

    /**
     * MyBatis statement id: {@code getUserAllHospitals}.
     */
    List<RoleUser> getUserAllHospitals(@Param("id") Long id);

    /**
     * MyBatis statement id: {@code listUserGroups}.
     */
    List<RoleResponse> listUserGroups(@Param("filter") RoleListFilter filter,
                                      @Param("actorHospitalId") Long actorHospitalId,
                                      @Param("isSuperAdmin") boolean isSuperAdmin,
                                      @Param("groupType") String groupType,
                                      @Param("includeScopeGroup") boolean includeScopeGroup);

    /**
     * MyBatis statement id: {@code countUserGroups}.
     */
    Long countUserGroups(@Param("filter") RoleListFilter filter,
                         @Param("actorHospitalId") Long actorHospitalId,
                         @Param("isSuperAdmin") boolean isSuperAdmin,
                         @Param("groupType") String groupType,
                         @Param("includeScopeGroup") boolean includeScopeGroup);

    /**
     * MyBatis statement id: {@code summarizeUserGroups}.
     */
    UserGroupSummaryResponse summarizeUserGroups(@Param("filter") RoleListFilter filter,
                                                 @Param("visibleHospitalId") Long visibleHospitalId,
                                                 @Param("includeScopeGroup") boolean includeScopeGroup);

    /**
     * MyBatis statement id: {@code checkDuplicate}.
     */
    Long checkDuplicate(@Param("name") String name, @Param("id") Long id, @Param("hospitalId") Long hospitalId);

    /**
     * MyBatis statement id: {@code insert}.
     */
    Boolean createRole(Role role);

    /**
     * MyBatis statement id: {@code update}.
     */
    Boolean updateRole(Role role);

    /**
     * MyBatis statement id: {@code delete}.
     */
    Boolean deleteRole(@Param("id") Long id, @Param("hospitalId") Long hospitalId);

    /**
     * MyBatis statement id: {@code deleteRoleByIdForSuperAdmin}.
     */
    Boolean deleteRoleByIdForSuperAdmin(@Param("id") Long id);

    /**
     * MyBatis statement id: {@code deleteRolePermissions}.
     */
    int deleteRolePermissions(@Param("id") Long id, @Param("hospitalId") Long hospitalId);

    /**
     * MyBatis statement id: {@code getModule}.
     */
    List<ModuleType> getModule(@Param("userId") Long userId, @Param("hospitalId") Long hospitalId);



    /**
     * MyBatis statement id: {@code insertRoleUser}.
     */
    Boolean insertRoleUser(@Param("roleId") Long roleId, @Param("userId") Long userId);

    /**
     * MyBatis statement id: {@code listActiveRoleIds}.
     */
    List<Long> listActiveRoleIds();

    /**
     * MyBatis statement id: {@code countActiveUsersByIds}.
     */
    Long countActiveUsersByIds(@Param("userIds") List<Long> userIds);

    /**
     * MyBatis statement id: {@code countActiveUsersByIdsInHospital}.
     */
    Long countActiveUsersByIdsInHospital(@Param("userIds") List<Long> userIds, @Param("hospitalId") Long hospitalId);

    /**
     * MyBatis statement id: {@code deleteRoleUser}.
     */
    Boolean deleteRoleUser(Long userId);

    /**
     * MyBatis statement id: {@code deleteUserRoleByRoleId}.
     */
    Boolean deleteUserRoleByRoleId(Long roleId);

    /**
     * MyBatis statement id: {@code getUserByGroupName}.
     */
    List<UserList> getUserByGroupName(String name);

}
