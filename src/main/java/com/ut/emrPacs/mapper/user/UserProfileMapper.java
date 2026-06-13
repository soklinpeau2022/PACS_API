package com.ut.emrPacs.mapper.user;

import com.ut.emrPacs.model.dto.request.authentication.user.UserProfileUpdateRequest;
import com.ut.emrPacs.model.dto.response.authentication.user.UserProfileResponse;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis mapper interface for namespace {@code com.ut.emrPacs.mapper.user.UserProfileMapper}.
 * Each method maps to an XML statement with the same {@code id}.
 */
@Mapper
public interface UserProfileMapper {

    /**
     * MyBatis statement id: {@code getProfileByUserId}.
     */
    UserProfileResponse getProfileByUserId(@Param("userId") Long userId);

    /**
     * MyBatis statement id: {@code getPasswordByUserId}.
     */
    String getPasswordByUserId(@Param("userId") Long userId);

    /**
     * MyBatis statement id: {@code checkDuplicateUsernameEdit}.
     */
    Long checkDuplicateUsernameEdit(@Param("userId") Long userId, @Param("username") String username);

    /**
     * MyBatis statement id: {@code updateUserProfile}.
     */
    Boolean updateUserProfile(UserProfileUpdateRequest userProfileUpdateRequest);
}

