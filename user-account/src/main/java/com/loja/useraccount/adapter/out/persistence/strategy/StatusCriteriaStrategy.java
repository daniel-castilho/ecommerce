package com.loja.useraccount.adapter.out.persistence.strategy;

import com.loja.useraccount.application.dto.UserSearchCriteria;
import jakarta.persistence.TypedQuery;

public class StatusCriteriaStrategy implements CriteriaStrategy {

    @Override
    public boolean supports(UserSearchCriteria criteria) {
        return criteria.status() != null;
    }

    @Override
    public String conditionFragment() {
        return "u.status = :status";
    }

    @Override
    public <T> void applyParameter(TypedQuery<T> query, UserSearchCriteria criteria) {
        query.setParameter("status", criteria.status());
    }
}
