package com.figlms.identity.internal;

import com.figlms.ApiException;
import com.figlms.identity.api.UnknownUserException;
import com.figlms.identity.api.UserAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Accounts and login: {@code /api/identity/...}.
 *
 * <ul>
 *   <li>{@code POST /login} {@code {username, password}}: opens a session. Every failure (wrong password, unknown,
 *       suspended or locked account) answers the same 401 {@code INVALID_LOGIN}, so the answer does not tell which
 *       usernames exist. Moodle shows "Invalid login, please try again" in the same cases.</li>
 *   <li>{@code POST /logout}, {@code GET /me}, {@code PUT /me/password}.</li>
 *   <li>{@code GET /password-policy}: the rules, for the sign-up form (no login needed).</li>
 *   <li>Site administrator only: {@code POST /users}, {@code GET /users/{username}}, {@code PUT /users/{username}/suspended}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/identity")
class AccountController {

    record LoginRequest(String username, String password) {
    }

    record LoginResponse(String username, String fullName, boolean siteAdmin, long sessionTimeoutSeconds) {
    }

    record CreateUserRequest(String username, String password, String firstname, String lastname, String email) {
    }

    record PasswordChange(String currentPassword, String newPassword) {
    }

    record SuspendRequest(boolean suspended) {
    }

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository sessionContexts;
    private final AccountService service;
    private final AccountRepository accounts;
    private final PasswordPolicy policy;
    private final IdentityProperties properties;

    AccountController(AuthenticationManager authenticationManager, SecurityContextRepository sessionContexts,
            AccountService service, AccountRepository accounts, PasswordPolicy policy, IdentityProperties properties) {
        this.authenticationManager = authenticationManager;
        this.sessionContexts = sessionContexts;
        this.service = service;
        this.accounts = accounts;
        this.policy = policy;
        this.properties = properties;
    }

    @PostMapping("/login")
    LoginResponse login(@RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
        } catch (AuthenticationException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_LOGIN", "Invalid login, please try again");
        }
        // Session fixation protection: a session opened before the login gets a new id, otherwise a new one starts.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        sessionContexts.saveContext(context, request, response);
        HttpSession session = request.getSession();
        session.setMaxInactiveInterval((int) properties.sessionTimeout().toSeconds());
        UserAccount account = accounts.findByUsername(authentication.getName()).orElseThrow();
        return new LoginResponse(account.username(), account.fullName(), account.siteAdmin(),
                session.getMaxInactiveInterval());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request, HttpServletResponse response, Authentication caller) {
        new SecurityContextLogoutHandler().logout(request, response, caller);
    }

    @GetMapping("/me")
    UserAccount me(Authentication caller) {
        return accounts.findByUsername(caller.getName()).orElseThrow(() -> new UnknownUserException(caller.getName()));
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@RequestBody PasswordChange body, Authentication caller) {
        service.changePassword(caller.getName(), body.currentPassword(), body.newPassword());
    }

    @GetMapping("/password-policy")
    IdentityProperties.Password passwordPolicy() {
        return policy.rules();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    UserAccount create(@RequestBody CreateUserRequest body, Authentication caller) {
        requireSiteAdmin(caller);
        return service.create(body.username(), body.password(), body.firstname(), body.lastname(), body.email());
    }

    @GetMapping("/users/{username}")
    UserAccount get(@PathVariable String username, Authentication caller) {
        if (!caller.getName().equals(username)) {
            requireSiteAdmin(caller);
        }
        return accounts.findByUsername(username).orElseThrow(() -> new UnknownUserException(username));
    }

    @PutMapping("/users/{username}/suspended")
    UserAccount suspend(@PathVariable String username, @RequestBody SuspendRequest body, Authentication caller) {
        requireSiteAdmin(caller);
        return service.setSuspended(username, body.suspended());
    }

    private static void requireSiteAdmin(Authentication caller) {
        boolean admin = caller.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!admin) {
            throw ApiException.forbidden("FORBIDDEN", "Only the site administrator may manage accounts");
        }
    }
}
