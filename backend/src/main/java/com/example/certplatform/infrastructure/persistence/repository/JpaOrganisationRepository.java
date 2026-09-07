package com.example.certplatform.infrastructure.persistence.repository;

import com.example.certplatform.infrastructure.persistence.entity.OrganisationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaOrganisationRepository extends JpaRepository<OrganisationEntity, String> {
}
