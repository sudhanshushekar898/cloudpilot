package dev.cloudpilot.resources;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CloudResourceRepository extends JpaRepository<CloudResource, UUID> {

    List<CloudResource> findAllByOrderByNameAsc();

    Optional<CloudResource> findByName(String name);
}
