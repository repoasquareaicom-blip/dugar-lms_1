package dugar_lms_api.modules.accessmanagement.service;

import dugar_lms_api.modules.accessmanagement.dto.LoginRequest;
import dugar_lms_api.modules.accessmanagement.dto.LoginResponse;
import dugar_lms_api.modules.accessmanagement.dto.MenuResponse;
import dugar_lms_api.modules.accessmanagement.dto.UserInfoResponse;
import dugar_lms_api.modules.accessmanagement.model.Role;
import dugar_lms_api.modules.accessmanagement.model.User;
import dugar_lms_api.modules.accessmanagement.repository.RoleRepository;
import dugar_lms_api.modules.accessmanagement.repository.UserRoleRepository;
import dugar_lms_api.modules.accessmanagement.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final MenuService menuService;
    private final JwtService jwtService;
    private final PasswordService passwordService;

    public AuthService(
        UserRepository userRepository,
        RoleRepository roleRepository,
        UserRoleRepository userRoleRepository,
        MenuService menuService,
        JwtService jwtService,
        PasswordService passwordService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.menuService = menuService;
        this.jwtService = jwtService;
        this.passwordService = passwordService;
    }

    public LoginResponse login(LoginRequest request) {
        validateRequest(request);

        User user = loadUser(request.getUsername());
        verifyPassword(request.getPassword(), user.getPasswordHash());

        List<Role> roles = loadRoles(user);
        Role primaryRole = primaryRole(user, roles);
        List<MenuResponse> menus = loadMenus(roles);
        String token = generateJwt(user, primaryRole);

        updateLastLogin(user.getUserId());
        return buildResponse(user, primaryRole, roles, menus, token);
    }

    private void validateRequest(LoginRequest request) {
        if (request == null) {
            throw new BadCredentialsException("Invalid username or password");
        }

        String username = request.getUsername();
        String password = request.getPassword();

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BadCredentialsException("Invalid username or password");
        }
    }

    private User loadUser(String username) {
        return userRepository.findActiveByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private void verifyPassword(String rawPassword, String encodedPassword) {
        if (!passwordService.matches(rawPassword, encodedPassword)) {
            throw new BadCredentialsException("Invalid username or password");
        }
    }

    private List<Role> loadRoles(User user) {
        List<Role> roles = userRoleRepository.findActiveRolesByUserId(user.getUserId());
        if (!roles.isEmpty()) {
            return roles;
        }

        Role fallbackRole = roleRepository.findActiveById(user.getRoleId())
            .orElseThrow(() -> new BadCredentialsException("Role not found or inactive"));
        return List.of(fallbackRole);
    }

    private Role primaryRole(User user, List<Role> roles) {
        return roles.stream()
            .filter(role -> role.getRoleId().equals(user.getRoleId()))
            .findFirst()
            .orElse(roles.get(0));
    }

    private List<MenuResponse> loadMenus(List<Role> roles) {
        return menuService.getMenuTreeByRoleIds(roles.stream().map(Role::getRoleId).toList());
    }

    private String generateJwt(User user, Role role) {
        return jwtService.generateToken(
            user.getUserId(),
            user.getUsername(),
            role.getRoleId(),
            role.getRoleCode(),
            user.getUserGroup()
        );
    }

    private void updateLastLogin(Long userId) {
        userRepository.updateLastLogin(userId, LocalDateTime.now());
    }

    private LoginResponse buildResponse(User user, Role role, List<Role> roles, List<MenuResponse> menus, String token) {
        UserInfoResponse userInfo = UserInfoResponse.builder()
            .userId(user.getUserId())
            .username(user.getUsername())
            .fullName(user.getFullName())
            .emailId(user.getEmailId())
            .roleId(role.getRoleId())
            .roleName(role.getRoleName())
            .roleCode(role.getRoleCode())
            .userGroup(user.getUserGroup())
            .roleIds(roles.stream().map(Role::getRoleId).toList())
            .roleNames(roles.stream().map(Role::getRoleName).toList())
            .build();

        return LoginResponse.builder()
            .success(true)
            .message("Login successful")
            .token(token)
            .user(userInfo)
            .menus(menus)
            .build();
    }
}
