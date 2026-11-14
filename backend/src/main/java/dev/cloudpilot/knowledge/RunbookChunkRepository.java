package dev.cloudpilot.knowledge;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RunbookChunkRepository extends JpaRepository<RunbookChunk, UUID> {
}
