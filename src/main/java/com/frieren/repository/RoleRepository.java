package com.frieren.repository;

import com.frieren.entity.Role;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class RoleRepository implements PanacheRepositoryBase<Role, Short> {

    public List<Role> findRolesForOrganization(UUID orgId) {
        return find("organization.id is null or organization.id = ?1", orgId).list();
    }

    public Optional<Role> findByNameInOrgOrGlobal(String name, UUID orgId) {
        return find("name = ?1 and (organization.id is null or organization.id = ?2)", name, orgId).firstResultOptional();
    }
}
