package com.loja.promotions.adapter.out.persistence;

import com.loja.promotions.application.dto.PageResult;
import com.loja.promotions.domain.model.Coupon;
import com.loja.promotions.domain.port.out.CouponRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Transactional
public class CouponRepositoryAdapter implements CouponRepositoryPort {

    @PersistenceContext(unitName = "ecommercePU")
    EntityManager em;

    @Override
    public Coupon save(Coupon coupon) {
        CouponJpaEntity merged = em.merge(CouponJpaMapper.toEntity(coupon));
        return CouponJpaMapper.toDomain(merged);
    }

    @Override
    public Optional<Coupon> findById(String id) {
        CouponJpaEntity entity = em.find(CouponJpaEntity.class, id);
        return Optional.ofNullable(entity).map(CouponJpaMapper::toDomain);
    }

    @Override
    public Optional<Coupon> findByCode(String code) {
        TypedQuery<CouponJpaEntity> query = em.createQuery(
                "SELECT c FROM CouponJpaEntity c WHERE c.code = :code", CouponJpaEntity.class);
        query.setParameter("code", code);
        List<CouponJpaEntity> result = query.setMaxResults(1).getResultList();
        return result.stream().findFirst().map(CouponJpaMapper::toDomain);
    }

    @Override
    public PageResult<Coupon> search(String codeFragment, Boolean active, int page, int pageSize) {
        List<String> predicates = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (codeFragment != null && !codeFragment.isBlank()) {
            predicates.add("c.code LIKE ?" + (params.size() + 1));
            params.add("%" + codeFragment.trim().toUpperCase() + "%");
        }
        if (active != null) {
            predicates.add("c.active = ?" + (params.size() + 1));
            params.add(active);
        }
        String whereClause = predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates);

        TypedQuery<Long> countQuery = em.createQuery(
                "SELECT COUNT(c) FROM CouponJpaEntity c" + whereClause, Long.class);
        for (int i = 0; i < params.size(); i++) {
            countQuery.setParameter(i + 1, params.get(i));
        }
        long total = countQuery.getSingleResult();

        TypedQuery<CouponJpaEntity> query = em.createQuery(
                "SELECT c FROM CouponJpaEntity c" + whereClause + " ORDER BY c.createdAt DESC",
                CouponJpaEntity.class);
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }
        List<CouponJpaEntity> entities = query
                .setFirstResult(page * pageSize)
                .setMaxResults(pageSize)
                .getResultList();

        List<Coupon> items = entities.stream().map(CouponJpaMapper::toDomain).toList();
        return new PageResult<>(items, total, page, pageSize);
    }
}
