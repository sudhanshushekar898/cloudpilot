package dev.cloudpilot.knowledge;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RunbookRepository extends JpaRepository<Runbook, UUID> {

    Optional<Runbook> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Runbook> findAllByOrderByTitleAsc();

    @Query("select c.runbookId as runbookId, count(c) as chunks from RunbookChunk c group by c.runbookId")
    List<Map<String, Object>> countChunksByRunbook();
}
