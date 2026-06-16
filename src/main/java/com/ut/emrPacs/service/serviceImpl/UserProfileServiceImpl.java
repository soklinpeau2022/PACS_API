package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.helper.date.FormatDateHelper;
import com.ut.emrPacs.authentication.util.PasswordPolicy;
import com.ut.emrPacs.mapper.user.UserProfileMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.dto.request.authentication.user.UserProfileUpdateRequest;
import com.ut.emrPacs.model.dto.response.authentication.user.UserProfileResponse;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.UserProfileService;
import com.ut.emrPacs.service.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.net.UnknownHostException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;

@Service
public class UserProfileServiceImpl implements UserProfileService {

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private ActivityLogService activityLogService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    /** {@inheritDoc} */
        // Process flow: execute b crypt password encoder business logic and return operation result.
    @Override
    public ResponseMessage<BaseResult> getProfile(HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Query flow: load profile and return API response.
        LocalTime startDuration = LocalTime.now();
        try {
            Long userId = userService.getUserAuth().getId();
            UserProfileResponse profile = userProfileMapper.getProfileByUserId(userId);
            List<UserProfileResponse> profileList = profile != null ? List.of(profile) : List.of();

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert("/user-profile/user-profile-get", null, null, "User Profile", "User Profile (View)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", profileList, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert("/user-profile/user-profile-get", errorLine, error.toString(), "User Profile", "User Profile (View)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to load your profile. Please try again.", false));
        }
    }

    /** {@inheritDoc} */
    @Override
    public ResponseMessage<BaseResult> updateProfile(UserProfileUpdateRequest userProfileUpdateRequest, HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Update flow: validate request, apply changes to profile, and return operation result.
        LocalTime startDuration = LocalTime.now();
        try {
            Long userId = userService.getUserAuth().getId();

            if (userProfileUpdateRequest == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
            }

            UserProfileResponse currentProfile = userProfileMapper.getProfileByUserId(userId);
            String username = userProfileUpdateRequest.getUsername() != null ? userProfileUpdateRequest.getUsername().trim() : "";
            if (username.isEmpty() && currentProfile != null) {
                username = currentProfile.getUsername() != null ? currentProfile.getUsername().trim() : "";
            }

            String firstName = userProfileUpdateRequest.getFirstName() != null ? userProfileUpdateRequest.getFirstName().trim() : "";
            if (firstName.isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("First name is required.", false));
            }

            String lastName = userProfileUpdateRequest.getLastName() != null ? userProfileUpdateRequest.getLastName().trim() : "";
            if (lastName.isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Last name is required.", false));
            }

            if (!username.isEmpty()) {
                Long dup = userProfileMapper.checkDuplicateUsernameEdit(userId, username);
                if (dup != null && dup > 0) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("Duplicate Username", false));
                }
            }

            String oldPassword = userProfileUpdateRequest.getOldPassword() != null ? userProfileUpdateRequest.getOldPassword() : "";
            String newPassword = userProfileUpdateRequest.getNewPassword() != null ? userProfileUpdateRequest.getNewPassword() : "";
            String confirmPassword = userProfileUpdateRequest.getConfirmPassword() != null ? userProfileUpdateRequest.getConfirmPassword() : "";
            boolean passwordChangeRequested = !oldPassword.isEmpty() || !newPassword.isEmpty() || !confirmPassword.isEmpty();
            if (passwordChangeRequested) {
                if (oldPassword.isEmpty()) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("Old password is required.", false));
                }
                String currentHash = userProfileMapper.getPasswordByUserId(userId);
                if (currentHash == null || currentHash.isEmpty() || !encoder.matches(oldPassword, currentHash)) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid old password.", false));
                }

                if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("New password and confirm password are required.", false));
                }
                if (!newPassword.equals(confirmPassword)) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("Passwords do not match.", false));
                }

                String policyError = PasswordPolicy.validatePasswordChange(newPassword);
                if (policyError != null) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message(policyError, false));
                }
            }

            if (userProfileUpdateRequest.getDateOfBirth() != null && !userProfileUpdateRequest.getDateOfBirth().trim().isEmpty()) {
                String dob = userProfileUpdateRequest.getDateOfBirth().trim();
                if (dob.matches("^\\d{2}/\\d{2}/\\d{4}$")) {
                    DateTimeFormatter dmy = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
                    LocalDate parsed = LocalDate.parse(dob, dmy);
                    dob = parsed.format(DateTimeFormatter.ISO_LOCAL_DATE);
                } else if (dob.length() >= 10) {
                    dob = dob.substring(0, 10);
                }
                if (!FormatDateHelper.isValidIsoDate(dob)) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid date of birth.", false));
                }
                userProfileUpdateRequest.setDateOfBirth(dob);
            }

            userProfileUpdateRequest.setId(userId);
            userProfileUpdateRequest.setModifiedBy(userId);
            userProfileUpdateRequest.setUsername(username.isEmpty() ? null : username);
            userProfileUpdateRequest.setFirstName(firstName);
            userProfileUpdateRequest.setLastName(lastName);
            userProfileUpdateRequest.setPassword(passwordChangeRequested ? encoder.encode(newPassword) : null);

            // Update user profile
            Boolean result = userProfileMapper.updateUserProfile(userProfileUpdateRequest);
            if (Boolean.TRUE.equals(result)) {
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert("/user-profile/user-profile-update", null, null, "User Profile", "User Profile (Update)", "Update", 1, "Success", startDuration, endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
            }

            return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to complete the request. Please try again.", false));

        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert("/user-profile/user-profile-update", errorLine, error.toString(), "User Profile", "User Profile (Update)", "Update", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }
}
