package com.bakery.framework.service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bakery.framework.audit.BakeryRevisionEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Service tra cứu lịch sử thay đổi entity qua Hibernate Envers.
 *
 * <p>Tất cả logic history tập trung tại đây — các controller không cần biết Envers.
 */
@Service
@Transactional(readOnly = true)
public class EntityHistoryService {

    private static final int DEFAULT_LIMIT = 50;

    @PersistenceContext
    private EntityManager entityManager;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Lấy danh sách revision của một entity, bỏ qua revision ADD đầu tiên
     * (revision khởi tạo không mang thông tin thay đổi).
     */
    public <T> List<EntityRevision<T>> getHistory(Class<T> entityClass, Object id) {
        return getHistory(entityClass, id, 0, DEFAULT_LIMIT);
    }

    public <T> List<EntityRevision<T>> getHistory(Class<T> entityClass, Object id, int page, int size) {
        AuditReader reader = AuditReaderFactory.get(entityManager);

        Long total = (Long) reader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .addProjection(AuditEntity.revisionNumber().count())
                .add(AuditEntity.id().eq(id))
                .getSingleResult();

        if (total == null || total == 0) return Collections.emptyList();

        boolean hasInitialAdd = hasInitialAdd(reader, entityClass, id);
        long historyCount = total - (hasInitialAdd ? 1 : 0);
        if (historyCount <= 0) return Collections.emptyList();

        int offset = (page * size) + (hasInitialAdd ? 1 : 0);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = reader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.id().eq(id))
                .addOrder(AuditEntity.revisionNumber().desc())
                .setFirstResult(offset)
                .setMaxResults(size)
                .getResultList();

        int startVersion = (int) (historyCount - (long) (page * size));
        return toRevisions(entityClass, rows, startVersion);
    }

    /** Lấy snapshot của entity tại một revision cụ thể. */
    public <T> T getRevision(Class<T> entityClass, Object id, Number revision) {
        return AuditReaderFactory.get(entityManager).find(entityClass, id, revision);
    }

    /**
     * So sánh 2 revision, trả về list 2 phần tử [before, after].
     * FE dùng để render diff.
     */
    public <T> List<T> loadRevisionPair(Class<T> entityClass, Object id, Number revA, Number revB) {
        AuditReader reader = AuditReaderFactory.get(entityManager);
        T a = Objects.equals(revA, revB) ? reader.find(entityClass, id, revA) : reader.find(entityClass, id, revA);
        T b = Objects.equals(revA, revB) ? a : reader.find(entityClass, id, revB);
        List<T> result = new ArrayList<>(2);
        result.add(a);
        result.add(b);
        return result;
    }

    /**
     * Tính diff field-level giữa 2 revision.
     * Trả về list các field thay đổi kèm giá trị trước/sau.
     */
    public <T> List<FieldDiff> diff(Class<T> entityClass, Object id, Number revA, Number revB) {
        List<T> pair = loadRevisionPair(entityClass, id, revA, revB);
        T oldEntity = pair.get(0);
        T newEntity = pair.get(1);

        if (oldEntity == null && newEntity == null) return Collections.emptyList();

        Class<?> type = oldEntity != null ? oldEntity.getClass() : newEntity.getClass();
        List<FieldDiff> diffs = new ArrayList<>();

        for (Field field : collectFields(type)) {
            field.setAccessible(true);
            try {
                Object oldVal = oldEntity != null ? field.get(oldEntity) : null;
                Object newVal = newEntity != null ? field.get(newEntity) : null;
                if (!Objects.equals(oldVal, newVal)) {
                    diffs.add(new FieldDiff(field.getName(), oldVal, newVal));
                }
            } catch (IllegalAccessException e) {
                // skip inaccessible fields
            }
        }
        return diffs;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private <T> boolean hasInitialAdd(AuditReader reader, Class<T> entityClass, Object id) {
        @SuppressWarnings("unchecked")
        List<Object[]> oldest = reader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.id().eq(id))
                .addOrder(AuditEntity.revisionNumber().asc())
                .setFirstResult(0)
                .setMaxResults(1)
                .getResultList();
        if (oldest.isEmpty()) return false;
        RevisionType type = (RevisionType) oldest.getFirst()[2];
        return type == RevisionType.ADD;
    }

    private <T> List<EntityRevision<T>> toRevisions(Class<T> entityClass, List<Object[]> rows, int startVersion) {
        List<EntityRevision<T>> result = new ArrayList<>();
        int version = startVersion;
        for (Object[] row : rows) {
            T entity = entityClass.cast(row[0]);
            BakeryRevisionEntity rev = (BakeryRevisionEntity) row[1];
            RevisionType type = (RevisionType) row[2];
            result.add(new EntityRevision<>(
                    entity,
                    rev.getId(),
                    new Date(rev.getTimestamp()),
                    type.name(),
                    version--,
                    rev.getActor()));
        }
        return result;
    }

    /** Thu thập tất cả fields kể cả từ superclass (để diff BaseEntity fields). */
    private List<Field> collectFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            fields.addAll(List.of(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    /**
     * Một revision của entity gồm snapshot + metadata.
     */
    public record EntityRevision<T>(
            T entity,
            long revision,
            Date revisionDate,
            String revisionType,   // ADD | MOD | DEL
            int versionNumber,
            String actor) {}

    /**
     * Một field thay đổi giữa 2 revision.
     */
    public record FieldDiff(String field, Object before, Object after) {}
}
