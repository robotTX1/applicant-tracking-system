package hu.robottx.ats.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import hu.robottx.ats.api.UsersApiDelegate;
import hu.robottx.ats.model.CurrentUserResponse;
import hu.robottx.ats.service.UserService;

@Component
@RequiredArgsConstructor
public class UsersApiDelegateImpl implements UsersApiDelegate {

    private final UserService userService;

    @Override
    public ResponseEntity<CurrentUserResponse> getCurrentUser() {
        return ResponseEntity.ok(userService.getCurrentUser());
    }

}
