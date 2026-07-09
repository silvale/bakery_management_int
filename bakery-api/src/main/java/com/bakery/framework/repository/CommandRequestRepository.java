package com.bakery.framework.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bakery.framework.entity.CommandAction;
import com.bakery.framework.entity.CommandRequest;

@Repository
public interface CommandRequestRepository extends JpaRepository<CommandRequest, UUID> {

    List<CommandRequest> findByEntityIdOrderByCreatedAtDesc(UUID entityId);

    List<CommandRequest> findByEntityNameAndEntityIdOrderByCreatedAtDesc(String entityName, UUID entityId);

    List<CommandRequest> findByEntityNameAndActionOrderByCreatedAtDesc(String entityName, CommandAction action);
}
