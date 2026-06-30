package com.ut.emrPacs.mapper.user;

import com.ut.emrPacs.model.base.filter.UserListFilter;
import com.ut.emrPacs.model.base.filter.Filter;
import com.ut.emrPacs.model.dto.request.authentication.user.UserUpdateRequest;
import com.ut.emrPacs.model.dto.response.dropDown.DropDownModelResponse;
import com.ut.emrPacs.model.dto.response.authentication.user.UserResponse;
import com.ut.emrPacs.model.users.*;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis mapper interface for namespace {@code com.ut.emrPacs.mapper.user.UserMapper}.
 * Each method maps to an XML statement with the same {@code id}.
 */
@Mapper
public interface UserMapper {

    /**
     * MyBatis statement id: {@code getList}.
     */
    List<UserResponse> listUser(UserListFilter filter);

    /**
     * MyBatis statement id: {@code countList}.
     */
    Long countList(UserListFilter filter);

    /**
     * MyBatis statement id: {@code insert}.
     */
    Boolean createUser(User user);

    /**
     * MyBatis statement id: {@code update}.
     */
    Boolean updateUser(UserUpdateRequest userRequest);

    /**
     * MyBatis statement id: {@code getOneByUsername}.
     */
    List<User> getOneByUsername(String username);

    /**
     * MyBatis statement id: {@code getOneByUserId}.
     */
    List<UserResponse> getOneByUserId(Long userId);

    /**
     * MyBatis statement id: {@code editProfile}.
     */
    Boolean editProfile(User user);

    /**
     * MyBatis statement id: {@code checkDuplicate}.
     */
    Long checkDuplicate(String username);

    /**
     * MyBatis statement id: {@code checkDuplicateForUpdate}.
     */
    Long checkDuplicateForUpdate(@Param("username") String username, @Param("id") Long id);

    /**
     * MyBatis statement id: {@code checkDuplicateEmail}.
     */
    Long checkDuplicateEmail(String email);


    /**
     * MyBatis statement id: {@code delete}.
     */
    Boolean deleteUser(@Param("id") Long id, @Param("modifiedBy") Long modifiedBy);

    /**
     * MyBatis statement id: {@code getOneUserGroupList}.
     */
    List<UserGroupList> getOneUserGroupList( Long id);

    /**
     * MyBatis statement id: {@code getHospitalIdByUserId}.
     */
    List<UserHospital> getHospitalIdByUserId(Long id);

    /**
     * MyBatis statement id: {@code updatePassword}.
     */
    Boolean updatePassword(@Param("id") Long id, @Param("password") String password);

    /**
     * MyBatis statement id: {@code deleteUserHospital}.
     */
    Boolean deleteUserHospital(Long userId);

    /**
     * MyBatis statement id: {@code insertUserHospital}.
     */
    Boolean insertUserHospital(
            @Param("hospitalId") Long hospitalId,
            @Param("userId") Long userId,
            @Param("isDefault") Boolean isDefault
    );

    /**
     * MyBatis statement id: {@code findAllActiveHospitalIds}.
     */
    List<Long> findAllActiveHospitalIds();

    /**
     * MyBatis statement id: {@code countCrossHospitalScopeRoleByUserId}.
     */
    Long countCrossHospitalScopeRoleByUserId(@Param("userId") Long userId, @Param("roleCode") String roleCode);

    /**
     * MyBatis statement id: {@code countActiveUserInHospital}.
     */
    Long countActiveUserInHospital(@Param("userId") Long userId, @Param("hospitalId") Long hospitalId);

    /**
     * MyBatis statement id: {@code findPrimaryHospitalIdByUserId}.
     */
    Long findPrimaryHospitalIdByUserId(@Param("userId") Long userId);

    /**
     * MyBatis statement id: {@code getMemberDropdownByHospital}.
     */
    List<DropDownModelResponse> getMemberDropdownByHospital(@Param("hospitalId") Long hospitalId, @Param("filter") Filter filter);

}
