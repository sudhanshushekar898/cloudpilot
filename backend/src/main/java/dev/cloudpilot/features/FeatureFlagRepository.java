package dev.cloudpilot.features;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface FeatureFlagRepository extends JpaRepository<FeatureFlag, String> {

    List<FeatureFlag> findAllByOrderByNameAsc();
}
