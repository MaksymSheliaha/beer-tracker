package com.beertracker.repository;

import com.beertracker.model.ConsumptionEntry;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class ConsumptionRepository {

    private final Map<UUID, ConsumptionEntry> entriesById = new ConcurrentHashMap<>();
    private final List<ConsumptionEntry> appendOnlyLog = new CopyOnWriteArrayList<>();

    public ConsumptionEntry append(ConsumptionEntry entry) {
        entriesById.put(entry.id(), entry);
        appendOnlyLog.add(entry);
        return entry;
    }

    public Optional<ConsumptionEntry> findById(UUID id) {
        return Optional.ofNullable(entriesById.get(id));
    }

    public List<ConsumptionEntry> findAll() {
        return List.copyOf(appendOnlyLog);
    }
}
