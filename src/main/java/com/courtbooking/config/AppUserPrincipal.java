package com.courtbooking.config;

import com.courtbooking.model.AppUser;
import com.courtbooking.model.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class AppUserPrincipal implements UserDetails {

    private final AppUser appUser;
    // Read once here: the organisation is a lazy proxy, and asking a proxy for
    // its id doesn't need the session that loaded it.
    private final Long organisationId;

    public AppUserPrincipal(AppUser appUser) {
        this.appUser = appUser;
        this.organisationId = appUser.getOrganisation() != null ? appUser.getOrganisation().getId() : null;
    }

    public Long getId() {
        return appUser.getId();
    }

    public Role getRole() {
        return appUser.getRole();
    }

    // The organisation an ORG_ADMIN administers; null for everyone else
    public Long getOrganisationId() {
        return organisationId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + appUser.getRole().name()));
    }

    @Override
    public String getPassword() {
        return appUser.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return appUser.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
